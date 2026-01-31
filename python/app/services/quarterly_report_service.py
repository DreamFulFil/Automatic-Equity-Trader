import os
from dataclasses import dataclass
from datetime import datetime
from typing import Dict, Optional, Tuple

import requests
from pydantic import BaseModel, Field, StrictBool, StrictInt, StrictStr, field_validator

from app.services.annual_report_service import (
    _decode_twse_response,
    _gregorian_year,
    _load_cached_metadata,
    _roc_year,
    _twse_headers,
    _write_metadata,
)

TWSE_REPORTS_URL = "https://doc.twse.com.tw/server-java/t57sb01"
TWSE_QUARTERLY_REPORT_TYPES = {
    "F01": "Consolidated financial statements",
    "F02": "Individual financial statements",
    "F03": "Reviewed financial statements",
}


class QuarterlyReportRequest(BaseModel):
    ticker: StrictStr = Field(..., min_length=1, max_length=20)
    report_year: Optional[StrictInt] = Field(default=None, ge=1, le=2100)
    report_quarter: StrictInt = Field(..., ge=1, le=4)
    report_type: StrictStr = Field(default="F01")
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
        if normalized not in TWSE_QUARTERLY_REPORT_TYPES:
            raise ValueError("Unsupported report type")
        return normalized


class QuarterlyReportResponse(BaseModel):
    status: StrictStr
    ticker: StrictStr
    report_year: Optional[StrictInt]
    report_quarter: StrictInt
    roc_year: Optional[StrictInt]
    report_type: StrictStr
    source: StrictStr
    url: StrictStr
    file_path: StrictStr
    content_type: Optional[StrictStr]
    size_bytes: StrictInt
    timestamp: StrictStr


@dataclass
class QuarterlyReportDownloadError(Exception):
    message: str
    status_code: int = 400


def _project_root() -> str:
    script_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.abspath(os.path.join(script_dir, "..", ".."))


def _reports_base_dir(destination_dir: Optional[str]) -> str:
    if destination_dir:
        return destination_dir
    return os.path.join(_project_root(), "reports", "financial", "quarterly")


def _parse_twse_result(html: str) -> Tuple[str, str, str]:
    import re

    match = re.search(
        r"readfile2?\(\"(?P<kind>[^\"]+)\",\"(?P<co_id>[^\"]+)\",\"(?P<filename>[^\"]+)\"\)",
        html,
    )
    if not match:
        raise QuarterlyReportDownloadError("Quarterly report not found in TWSE response", status_code=404)

    return (
        match.group("kind"),
        match.group("co_id"),
        match.group("filename"),
    )


def _normalize_co_id(ticker: str) -> str:
    normalized = ticker.strip().upper()
    if normalized.endswith(".TW"):
        normalized = normalized[:-3]
    normalized = normalized.replace("-", "")
    if not normalized.isdigit():
        raise QuarterlyReportDownloadError("Ticker must be a numeric TWSE code", status_code=400)
    return normalized


def download_quarterly_financial_report(
    ticker: str,
    report_year: Optional[int],
    report_quarter: int,
    report_type: str = "F01",
    force: bool = False,
    destination_dir: Optional[str] = None,
    session: Optional[requests.Session] = None,
) -> QuarterlyReportResponse:
    normalized_ticker = _normalize_co_id(ticker)
    base_dir = _reports_base_dir(destination_dir)
    ticker_dir = os.path.join(base_dir, normalized_ticker)
    latest_cache_path = os.path.join(ticker_dir, "latest.json")
    year_cache_path = os.path.join(ticker_dir, f"report-{report_year}-Q{report_quarter}.json") if report_year else None

    if not force:
        cached = None
        if report_year and year_cache_path:
            cached = _load_cached_metadata(year_cache_path)
        if not cached:
            cached = _load_cached_metadata(latest_cache_path)
        if cached and os.path.exists(cached.get("file_path", "")):
            return QuarterlyReportResponse(**cached)

    session = session or requests.Session()
    requested_roc_year = _roc_year(report_year)
    if requested_roc_year is None:
        requested_roc_year = _roc_year(datetime.now().year)
    report_type = report_type.strip().upper()
    if report_type not in TWSE_QUARTERLY_REPORT_TYPES:
        raise QuarterlyReportDownloadError("Unsupported report type", status_code=400)

    payload = {
        "step": "1",
        "co_id": normalized_ticker,
        "year": str(requested_roc_year),
        "season": str(report_quarter),
        "mtype": "F",
        "dtype": report_type,
        "seamon": "",
    }

    try:
        response = session.post(TWSE_REPORTS_URL, headers=_twse_headers(), data=payload, timeout=30)
        response.raise_for_status()
    except requests.RequestException as exc:
        raise QuarterlyReportDownloadError(f"Failed to query TWSE: {exc}", status_code=502) from exc

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
        raise QuarterlyReportDownloadError(f"Failed to download quarterly report: {exc}", status_code=502) from exc

    ext = os.path.splitext(filename)[1] or ".pdf"
    report_year_value = _gregorian_year(report_year) or _gregorian_year(requested_roc_year)
    safe_year = report_year_value or "latest"
    file_name = f"{normalized_ticker}-{safe_year}-Q{report_quarter}{ext}"
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
        "status": "success",
        "ticker": normalized_ticker,
        "report_year": report_year_value,
        "report_quarter": report_quarter,
        "roc_year": requested_roc_year,
        "report_type": report_type,
        "source": "TWSE",
        "url": TWSE_REPORTS_URL,
        "file_path": file_path,
        "content_type": report_response.headers.get("Content-Type"),
        "size_bytes": size_bytes,
        "timestamp": datetime.now().isoformat(),
    }

    if report_year_value:
        _write_metadata(os.path.join(ticker_dir, f"report-{report_year_value}-Q{report_quarter}.json"), metadata)
    _write_metadata(latest_cache_path, metadata)

    return QuarterlyReportResponse(**metadata)
