import json
import os
import re
from dataclasses import dataclass
from datetime import datetime
from typing import Dict, Optional, Tuple

import requests
from pydantic import BaseModel, Field, StrictBool, StrictInt, StrictStr, field_validator

TWSE_REPORTS_URL = "https://doc.twse.com.tw/server-java/t57sb01"
TWSE_ANNUAL_REPORT_TYPES = {
    "F04": "股東會年報",
    "F11": "股東會年報(股東會後修訂本)",
    "FE4": "英文版-股東會年報",
    "FE6": "英文版-股東會年報(股東會後修訂本)",
}


class AnnualReportRequest(BaseModel):
    ticker: StrictStr = Field(..., min_length=1, max_length=20)
    report_year: Optional[StrictInt] = Field(default=None, ge=1, le=2100)
    report_type: StrictStr = Field(default="F04")
    force: StrictBool = False

    @field_validator("ticker")
    @classmethod
    def validate_ticker(cls, value: str) -> str:
        normalized = value.strip().upper()
        if not normalized:
            raise ValueError("Ticker cannot be empty")
        if any(sep in normalized for sep in ("/", "\\")):
            raise ValueError("Ticker contains invalid characters")
        return normalized

    @field_validator("report_type")
    @classmethod
    def validate_report_type(cls, value: str) -> str:
        normalized = value.strip().upper()
        if normalized not in TWSE_ANNUAL_REPORT_TYPES:
            raise ValueError("Unsupported report type")
        return normalized


class AnnualReportResponse(BaseModel):
    status: StrictStr
    ticker: StrictStr
    report_year: Optional[StrictInt]
    roc_year: Optional[StrictInt]
    filing_date: Optional[StrictStr]
    report_date: Optional[StrictStr]
    source: StrictStr
    url: StrictStr
    file_path: StrictStr
    content_type: Optional[StrictStr]
    size_bytes: StrictInt
    timestamp: StrictStr


@dataclass
class AnnualReportDownloadError(Exception):
    message: str
    status_code: int = 400


def _project_root() -> str:
    script_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.abspath(os.path.join(script_dir, "..", ".."))


def _reports_base_dir(destination_dir: Optional[str]) -> str:
    if destination_dir:
        return destination_dir
    return os.path.join(_project_root(), "reports", "shareholders")


def _twse_headers() -> Dict[str, str]:
    user_agent = os.environ.get("TWSE_USER_AGENT", "Automatic-Equity-Trader/1.0 (support@example.com)")
    return {
        "User-Agent": user_agent,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Encoding": "gzip, deflate",
        "Connection": "keep-alive",
    }

def _normalize_co_id(ticker: str) -> str:
    normalized = ticker.strip().upper()
    if normalized.endswith(".TW"):
        normalized = normalized[:-3]
    normalized = normalized.replace("-", "")
    if not normalized.isdigit():
        raise AnnualReportDownloadError("Ticker must be a numeric TWSE code", status_code=400)
    return normalized


def _load_cached_metadata(cache_path: str) -> Optional[Dict[str, str]]:
    if not os.path.exists(cache_path):
        return None
    try:
        with open(cache_path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except Exception:
        return None


def _write_metadata(cache_path: str, metadata: Dict[str, str]) -> None:
    os.makedirs(os.path.dirname(cache_path), exist_ok=True)
    with open(cache_path, "w", encoding="utf-8") as handle:
        json.dump(metadata, handle, indent=2)

def _decode_twse_response(content: bytes) -> str:
    for encoding in ("big5", "cp950", "utf-8"):
        try:
            return content.decode(encoding)
        except UnicodeDecodeError:
            continue
    return content.decode("utf-8", errors="ignore")


def _roc_year(year: Optional[int]) -> Optional[int]:
    if year is None:
        return None
    return year - 1911 if year > 1911 else year


def _gregorian_year(year: Optional[int]) -> Optional[int]:
    if year is None:
        return None
    return year + 1911 if year < 1911 else year


def _parse_twse_result(html: str) -> Tuple[str, str, str]:
    match = re.search(
        r"readfile2?\(\"(?P<kind>[^\"]+)\",\"(?P<co_id>[^\"]+)\",\"(?P<filename>[^\"]+)\"\)",
        html,
    )
    if not match:
        raise AnnualReportDownloadError("Annual report not found in TWSE response", status_code=404)

    return (
        match.group("kind"),
        match.group("co_id"),
        match.group("filename"),
    )


def download_shareholders_annual_report(
    ticker: str,
    report_year: Optional[int] = None,
    report_type: str = "F04",
    force: bool = False,
    destination_dir: Optional[str] = None,
    session: Optional[requests.Session] = None,
) -> AnnualReportResponse:
    normalized_ticker = _normalize_co_id(ticker)
    base_dir = _reports_base_dir(destination_dir)
    ticker_dir = os.path.join(base_dir, normalized_ticker)
    latest_cache_path = os.path.join(ticker_dir, "latest.json")
    year_cache_path = os.path.join(ticker_dir, f"report-{report_year}.json") if report_year else None

    if not force:
        cached = None
        if report_year and year_cache_path:
            cached = _load_cached_metadata(year_cache_path)
        if not cached:
            cached = _load_cached_metadata(latest_cache_path)
        if cached and os.path.exists(cached.get("file_path", "")):
            return AnnualReportResponse(**cached)

    session = session or requests.Session()
    requested_roc_year = _roc_year(report_year)
    if requested_roc_year is None:
        requested_roc_year = _roc_year(datetime.now().year)
    report_type = report_type.strip().upper()
    if report_type not in TWSE_ANNUAL_REPORT_TYPES:
        raise AnnualReportDownloadError("Unsupported report type", status_code=400)

    payload = {
        "step": "1",
        "co_id": normalized_ticker,
        "year": str(requested_roc_year),
        "mtype": "F",
        "dtype": report_type,
        "seamon": "",
    }

    try:
        response = session.post(TWSE_REPORTS_URL, headers=_twse_headers(), data=payload, timeout=30)
        response.raise_for_status()
    except requests.RequestException as exc:
        raise AnnualReportDownloadError(f"Failed to query TWSE: {exc}", status_code=502) from exc

    html = _decode_twse_response(response.content)
    kind, co_id, filename = _parse_twse_result(html)

    download_payload = {
        "step": "9",
        "kind": kind,
        "co_id": co_id,
        "filename": filename,
    }

    try:
        report_response = session.post(
            TWSE_REPORTS_URL,
            headers=_twse_headers(),
            data=download_payload,
            timeout=60,
            stream=True,
        )
        report_response.raise_for_status()
    except requests.RequestException as exc:
        raise AnnualReportDownloadError(f"Failed to download TWSE report: {exc}", status_code=502) from exc

    ext = os.path.splitext(filename)[1] or ".pdf"
    report_year_value = _gregorian_year(report_year) or _gregorian_year(requested_roc_year)
    safe_year = report_year_value or "latest"
    file_name = f"{normalized_ticker}-{safe_year}{ext}"
    os.makedirs(ticker_dir, exist_ok=True)
    file_path = os.path.join(ticker_dir, file_name)
    temp_path = f"{file_path}.part"

    size_bytes = 0
    with open(temp_path, "wb") as handle:
        for chunk in report_response.iter_content(chunk_size=8192):
            if chunk:
                handle.write(chunk)
                size_bytes += len(chunk)

    os.replace(temp_path, file_path)

    metadata = {
        "status": "ok",
        "ticker": normalized_ticker,
        "report_year": report_year_value,
        "roc_year": requested_roc_year,
        "filing_date": None,
        "report_date": None,
        "source": "TWSE",
        "url": TWSE_REPORTS_URL,
        "file_path": file_path,
        "content_type": report_response.headers.get("Content-Type"),
        "size_bytes": size_bytes,
        "timestamp": datetime.now().isoformat(),
    }

    if report_year_value:
        _write_metadata(os.path.join(ticker_dir, f"report-{report_year_value}.json"), metadata)
    _write_metadata(latest_cache_path, metadata)

    return AnnualReportResponse(**metadata)