from dataclasses import dataclass
from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field, StrictBool, StrictInt, StrictStr, field_validator

from app.services.annual_report_summary_service import (
    _chunk_documents,
    _load_documents,
    _llm_from_env,
    DEFAULT_SUMMARY_MODEL,
    load_summarize_chain,
)
from app.services.quarterly_report_service import (
    QuarterlyReportDownloadError,
    TWSE_QUARTERLY_REPORT_TYPES,
    download_quarterly_financial_report,
)


class QuarterlyReportSummaryRequest(BaseModel):
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
        return normalized

    @field_validator("report_type")
    @classmethod
    def validate_report_type(cls, value: str) -> str:
        normalized = value.strip().upper()
        if normalized not in TWSE_QUARTERLY_REPORT_TYPES:
            raise ValueError("Unsupported report type")
        return normalized


class QuarterlyReportSummaryResponse(BaseModel):
    status: StrictStr
    ticker: StrictStr
    report_year: Optional[StrictInt]
    report_quarter: StrictInt
    roc_year: Optional[StrictInt]
    report_type: StrictStr
    summary: StrictStr
    file_path: StrictStr
    model: StrictStr
    chunk_count: StrictInt
    timestamp: StrictStr


@dataclass
class QuarterlyReportSummaryError(Exception):
    message: str
    status_code: int = 400


def summarize_quarterly_report(
    ticker: str,
    report_year: Optional[int],
    report_quarter: int,
    report_type: str = "F01",
    force: bool = False,
    llm_model: str = DEFAULT_SUMMARY_MODEL,
) -> QuarterlyReportSummaryResponse:
    try:
        report_info = download_quarterly_financial_report(
            ticker=ticker,
            report_year=report_year,
            report_quarter=report_quarter,
            report_type=report_type,
            force=force,
        )
    except QuarterlyReportDownloadError as exc:
        raise QuarterlyReportSummaryError(exc.message, exc.status_code) from exc

    documents = _load_documents(report_info.file_path)
    chunks = _chunk_documents(documents)

    if load_summarize_chain is None:
        raise QuarterlyReportSummaryError("Summarize chain is not available", status_code=500)

    llm = _llm_from_env(llm_model)
    chain = load_summarize_chain(llm, chain_type="map_reduce")
    summary = chain.run(chunks)

    return QuarterlyReportSummaryResponse(
        status="ok",
        ticker=report_info.ticker,
        report_year=report_info.report_year,
        report_quarter=report_info.report_quarter,
        roc_year=report_info.roc_year,
        report_type=report_type,
        summary=str(summary).strip(),
        file_path=report_info.file_path,
        model=llm_model,
        chunk_count=len(chunks),
        timestamp=datetime.now().isoformat(),
    )
