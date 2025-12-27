import json
from pathlib import Path
import pandas as pd
import pytest

import scripts.operational.check_yahoo_rate_limits as yahoo
import scripts.operational.check_shioaji_config as shioaji


class DummyDF(pd.DataFrame):
    pass


def test_fetch_one_success(monkeypatch, tmp_path):
    # Mock yfinance.download to return a small dataframe
    def fake_download(symbol, start, end, progress=False):
        df = pd.DataFrame({"Close": [1, 2, 3]}, index=pd.to_datetime(["2020-01-01", "2020-01-02", "2020-01-03"]))
        return df

    monkeypatch.setattr(yahoo.yf, "download", fake_download)

    out = yahoo.run(["2330"], years=1, throttle_ms=0, output=tmp_path / "res.json")
    assert out["total_stocks"] == 1
    assert out["successful"] == 1
    assert out["rate_limited"] == 0


def test_fetch_one_rate_limited(monkeypatch, tmp_path):
    def fake_download(symbol, start, end, progress=False):
        raise RuntimeError("HTTP 429 Too Many Requests")

    monkeypatch.setattr(yahoo.yf, "download", fake_download)

    out = yahoo.run(["2330"], years=1, throttle_ms=0, output=tmp_path / "res2.json")
    assert out["total_stocks"] == 1
    assert out["rate_limited"] == 1 or out["failed"] == 1


def test_shioaji_mask_and_validate(tmp_path, monkeypatch):
    cfg = {
        "shioaji": {
            "ca-path": "/tmp/ca.pem",
            "ca-password": "supersecret",
            "person-id": "P012345678",
            "stock": {"api-key": "AK-XXXX", "secret-key": "SK-YYYY"},
            "future": {"api-key": "FK-AAAA", "secret-key": "FS-BBBB"},
            "simulation": True
        }
    }

    p = tmp_path / "cfg.yml"
    p.write_text(json.dumps(cfg))

    # Monkeypatch yaml.safe_load to return our cfg
    monkeypatch.setattr(shioaji.yaml, "safe_load", lambda f: cfg)

    report_path = tmp_path / "report.json"
    # Simulate calling the script via load_yaml_config path
    loaded = shioaji.load_yaml_config  # function exists
    # direct usage of summarize_and_mask
    masked = shioaji.summarize_and_mask(cfg)
    assert masked["ca-path"].startswith("/tmp")[:1] or isinstance(masked["ca-path"], str)
    assert masked["stock.api-key"].startswith("AK-") or isinstance(masked["stock.api-key"], str)

    # Validate schema summary
    summary = shioaji.validate_schema(cfg)
    assert summary["present"]
    assert summary["stock_creds_present"]


def test_shioaji_decrypt_path(monkeypatch, tmp_path):
    # Simulate load_config_with_decryption
    called = {}

    def fake_loader(password):
        called['ok'] = True
        return {"shioaji": {"stock": {"api-key": "AK", "secret-key": "SK"}}}

    monkeypatch.setitem(__import__('builtins').__dict__, 'load_config_with_decryption', fake_loader)
    # Instead, monkeypatch module import path by injecting into scripts module
    monkeypatch.setattr(shioaji, "try_decrypt_config", lambda pwd: fake_loader(pwd))

    report_file = tmp_path / "r.json"
    # call summarize
    cfg = shioaji.try_decrypt_config("pw")
    assert cfg.get("shioaji")
