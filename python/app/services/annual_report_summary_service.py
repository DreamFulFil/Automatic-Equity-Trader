import os
from dataclasses import dataclass
from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel, Field, StrictBool, StrictInt, StrictStr, field_validator

from app.services.annual_report_service import (
    AnnualReportDownloadError,
    TWSE_ANNUAL_REPORT_TYPES,
    download_shareholders_annual_report,
)

try:
    from langchain_community.document_loaders import Docx2txtLoader
    from langchain_community.document_loaders import PyPDFLoader
    from langchain_community.llms import Ollama
    from langchain.chains.summarize import load_summarize_chain
    from langchain_core.documents import Document
    from langchain.text_splitter import RecursiveCharacterTextSplitter
except ImportError:  # pragma: no cover - exercised via unit tests with mocks
    Docx2txtLoader = None
    PyPDFLoader = None
    Ollama = None
    load_summarize_chain = None
    Document = None
    RecursiveCharacterTextSplitter = None


DEFAULT_SUMMARY_MODEL = "llama3.1"


class AnnualReportSummaryRequest(BaseModel):
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
        return normalized

    @field_validator("report_type")
    @classmethod
    def validate_report_type(cls, value: str) -> str:
        normalized = value.strip().upper()
        if normalized not in TWSE_ANNUAL_REPORT_TYPES:
            raise ValueError("Unsupported report type")
        return normalized


class AnnualReportSummaryResponse(BaseModel):
    status: StrictStr
    ticker: StrictStr
    report_year: Optional[StrictInt]
    roc_year: Optional[StrictInt]
    report_type: StrictStr
    summary: StrictStr
    file_path: StrictStr
    model: StrictStr
    chunk_count: StrictInt
    timestamp: StrictStr


@dataclass
class AnnualReportSummaryError(Exception):
    message: str
    status_code: int = 400


def _load_documents(file_path: str) -> List[Document]:
    if Document is None:
        raise AnnualReportSummaryError("LangChain dependencies are not installed", status_code=500)

    _, ext = os.path.splitext(file_path)
    ext = ext.lower()
    if ext == ".pdf":
        if PyPDFLoader is None:
            raise AnnualReportSummaryError("PyPDFLoader not available", status_code=500)
        loader = PyPDFLoader(file_path)
        return loader.load()
    if ext == ".docx":
        if Docx2txtLoader is None:
            raise AnnualReportSummaryError("Docx2txtLoader not available", status_code=500)
        loader = Docx2txtLoader(file_path)
        return loader.load()

    with open(file_path, "r", encoding="utf-8", errors="ignore") as handle:
        content = handle.read()
    return [Document(page_content=content, metadata={"source": file_path})]


def _chunk_documents(documents: List[Document]) -> List[Document]:
    if RecursiveCharacterTextSplitter is None:
        raise AnnualReportSummaryError("Text splitter is not available", status_code=500)
    splitter = RecursiveCharacterTextSplitter(chunk_size=1500, chunk_overlap=200)
    return splitter.split_documents(documents)


def _llm_from_env(model_name: str):
    if Ollama is None:
        raise AnnualReportSummaryError("Ollama client is not available", status_code=500)
    base_url = os.environ.get("OLLAMA_URL", "http://localhost:11434")
    return Ollama(base_url=base_url, model=model_name)


def summarize_annual_report(
    ticker: str,
    report_year: Optional[int] = None,
    report_type: str = "F04",
    force: bool = False,
    llm_model: str = DEFAULT_SUMMARY_MODEL,
) -> AnnualReportSummaryResponse:
    report_info = download_shareholders_annual_report(
        ticker=ticker,
        report_year=report_year,
        report_type=report_type,
        force=force,
    )

    documents = _load_documents(report_info.file_path)
    chunks = _chunk_documents(documents)

    if load_summarize_chain is None:
        raise AnnualReportSummaryError("Summarize chain is not available", status_code=500)

    llm = _llm_from_env(llm_model)
    chain = load_summarize_chain(llm, chain_type="map_reduce")
    summary = chain.run(chunks)

    return AnnualReportSummaryResponse(
        status="ok",
        ticker=report_info.ticker,
        report_year=report_info.report_year,
        roc_year=report_info.roc_year,
        report_type=report_type,
        summary=str(summary).strip(),
        file_path=report_info.file_path,
        model=llm_model,
        chunk_count=len(chunks),
        timestamp=datetime.now().isoformat(),
    )