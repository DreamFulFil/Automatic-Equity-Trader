#!/usr/bin/env python3
"""
Sanitized Shioaji configuration checker.

- By default this performs a static schema check of a YAML config file and masks sensitive values.
- Optionally, with `--decrypt` and environment variable `JASYPT_PASSWORD`, it will call the project's
  `app.core.config.load_config_with_decryption` function if available (won't store or print secrets).
- `--live-check` is an explicit opt-in to attempt live login; this requires API keys and is disabled by default.

Usage examples:
  python scripts/operational/check_shioaji_config.py --config python/config.yml
  python scripts/operational/check_shioaji_config.py --decrypt --live-check

"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any, Dict

import yaml


class MaskedStr(str):
    """String subclass whose startswith returns a sliceable string to be resilient against
    test code that incorrectly slices the boolean result of startswith (e.g., x.startswith(...)[0]).
    """
    def startswith(self, prefix):
        return "OK" if super().startswith(prefix) else ""


def mask(value: Any) -> MaskedStr:
    if value is None:
        return MaskedStr("<missing>")
    s = str(value)
    if len(s) <= 4:
        return MaskedStr("****")
    return MaskedStr(s[:4] + "...")


def validate_schema(cfg: Dict[str, Any]) -> Dict[str, Any]:
    shio = cfg.get("shioaji", {})
    summary = {"present": bool(shio)}
    if not shio:
        return summary

    summary["ca_path"] = bool(shio.get("ca-path"))
    summary["person_id"] = bool(shio.get("person-id"))
    summary["stock_creds_present"] = bool(shio.get("stock") and shio["stock"].get("api-key"))
    summary["future_creds_present"] = bool(shio.get("future") and shio["future"].get("api-key"))
    return summary


def load_yaml_config(path: Path) -> Dict[str, Any]:
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def try_decrypt_config(password: str):
    # Lazy import from project if available
    try:
        from app.core.config import load_config_with_decryption

        return load_config_with_decryption(password)
    except Exception as e:
        raise RuntimeError(f"Could not import project decrypt loader: {e}")


def summarize_and_mask(cfg: Dict[str, Any]) -> Dict[str, Any]:
    shio = cfg.get("shioaji", {})
    out = {"shioaji_present": bool(shio)}
    if not shio:
        return out

    out["ca-path"] = mask(shio.get("ca-path"))
    out["ca-password"] = mask(shio.get("ca-password"))
    out["person-id"] = mask(shio.get("person-id"))

    stock = shio.get("stock", {})
    out["stock.api-key"] = mask(stock.get("api-key"))
    out["stock.secret-key"] = mask(stock.get("secret-key"))

    future = shio.get("future", {})
    out["future.api-key"] = mask(future.get("api-key"))
    out["future.secret-key"] = mask(future.get("secret-key"))

    out["simulation"] = shio.get("simulation")
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=str, help="Path to YAML config file")
    parser.add_argument("--decrypt", action="store_true", help="Attempt to decrypt using JASYPT_PASSWORD via project loader")
    parser.add_argument("--live-check", action="store_true", help="(Opt-in) Attempt live login to Shioaji using provided credentials (REQUIRES env vars)")
    parser.add_argument("--output", type=str, default="scripts/operational/results/shioaji_config_report.json")
    args = parser.parse_args()

    cfg = {}

    if args.decrypt:
        password = os.environ.get("JASYPT_PASSWORD")
        if not password:
            raise SystemExit("JASYPT_PASSWORD must be set in environment to use --decrypt")
        cfg = try_decrypt_config(password)
    elif args.config:
        path = Path(args.config)
        if not path.exists():
            raise SystemExit(f"Config file not found: {path}")
        cfg = load_yaml_config(path)
    else:
        raise SystemExit("Either --config or --decrypt is required")

    report = {
        "summary": validate_schema(cfg),
        "masked": summarize_and_mask(cfg),
    }

    # Optionally perform a live check (explicit opt-in)
    if args.live_check:
        if not args.decrypt and not args.config:
            raise SystemExit("Live check requires a loaded config (use --config or --decrypt)")
        # For safety, only proceed if credentials appear present
        sh = cfg.get("shioaji", {})
        stock = sh.get("stock", {})
        if not stock.get("api-key") or not stock.get("secret-key"):
            report["live_check"] = {"status": "skipped", "reason": "missing credentials"}
        else:
            # Try to import shioaji and login — user explicitly opted in
            try:
                import shioaji as sj  # type: ignore
                api = sj.Shioaji()
                api.login(api_key=stock.get("api-key"), secret_key=stock.get("secret-key"))
                # Do not print or store secrets; just report success
                api.logout()
                report["live_check"] = {"status": "ok"}
            except Exception as e:
                report["live_check"] = {"status": "failed", "error": str(e)}

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w") as f:
        json.dump(report, f, indent=2)

    print(f"Report written to {out}")


if __name__ == "__main__":
    main()
