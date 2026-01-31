import json
import os
from dataclasses import dataclass
from datetime import datetime
from typing import Dict, List, Optional

from pydantic import BaseModel, Field, StrictBool, StrictInt, StrictStr, field_validator

from app.services.annual_report_service import (
    AnnualReportDownloadError,
    TWSE_ANNUAL_REPORT_TYPES,
    download_shareholders_annual_report,
)

try:
    from langchain_community.document_loaders import PyPDFLoader
    from langchain_community.document_loaders import Docx2txtLoader
    from langchain_community.embeddings import HuggingFaceEmbeddings
    from langchain_community.llms import Ollama
    from langchain_community.vectorstores import FAISS
    from langchain_core.documents import Document
    from langchain.text_splitter import RecursiveCharacterTextSplitter
except ImportError:  # pragma: no cover - exercised via unit tests with mocks
    PyPDFLoader = None
    Docx2txtLoader = None
    HuggingFaceEmbeddings = None
    Ollama = None
    FAISS = None
    Document = None
    RecursiveCharacterTextSplitter = None


DEFAULT_EMBEDDING_MODEL = "BAAI/bge-m3"
DEFAULT_LLM_MODEL = "llama3.1"


class AnnualReportRagIndexRequest(BaseModel):
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


class AnnualReportRagQueryRequest(BaseModel):
    ticker: StrictStr = Field(..., min_length=1, max_length=20)
    question: StrictStr = Field(..., min_length=1, max_length=2000)
    report_year: Optional[StrictInt] = Field(default=None, ge=1, le=2100)
    report_type: StrictStr = Field(default="F04")
    top_k: StrictInt = Field(default=4, ge=1, le=20)
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


class AnnualReportRagIndexResponse(BaseModel):
    status: StrictStr
    ticker: StrictStr
    report_year: Optional[StrictInt]
    roc_year: Optional[StrictInt]
    report_type: StrictStr
    file_path: StrictStr
    index_path: StrictStr
    chunk_count: StrictInt
    embedding_model: StrictStr
    timestamp: StrictStr


class AnnualReportRagQueryResponse(BaseModel):
    status: StrictStr
    ticker: StrictStr
    report_year: Optional[StrictInt]
    roc_year: Optional[StrictInt]
    report_type: StrictStr
    answer: StrictStr
    sources: List[StrictStr]
    file_path: StrictStr
    index_path: StrictStr
    llm_model: StrictStr
    embedding_model: StrictStr
    timestamp: StrictStr


@dataclass
class AnnualReportRagError(Exception):
    message: str
    status_code: int = 400


def _project_root() -> str:
    script_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.abspath(os.path.join(script_dir, "..", ".."))


def _faiss_base_dir() -> str:
    return os.path.join(_project_root(), "reports", "shareholders", "faiss")


def _load_documents(file_path: str) -> List[Document]:
    if Document is None:
        raise AnnualReportRagError("LangChain dependencies are not installed", status_code=500)

    _, ext = os.path.splitext(file_path)
    ext = ext.lower()
    if ext == ".pdf":
        if PyPDFLoader is None:
            raise AnnualReportRagError("PyPDFLoader not available", status_code=500)
        loader = PyPDFLoader(file_path)
        return loader.load()
    if ext == ".docx":
        if Docx2txtLoader is None:
            raise AnnualReportRagError("Docx2txtLoader not available", status_code=500)
        loader = Docx2txtLoader(file_path)
        return loader.load()

    with open(file_path, "r", encoding="utf-8", errors="ignore") as handle:
        content = handle.read()
    return [Document(page_content=content, metadata={"source": file_path})]


def _build_embeddings(model_name: str):
    if HuggingFaceEmbeddings is None:
        raise AnnualReportRagError("Embedding dependencies are not installed", status_code=500)
    return HuggingFaceEmbeddings(
        model_name=model_name,
        model_kwargs={"device": "cpu"},
        encode_kwargs={"normalize_embeddings": True},
    )


def _chunk_documents(documents: List[Document]) -> List[Document]:
    if RecursiveCharacterTextSplitter is None:
        raise AnnualReportRagError("Text splitter is not available", status_code=500)
    splitter = RecursiveCharacterTextSplitter(chunk_size=1200, chunk_overlap=200)
    return splitter.split_documents(documents)


def _llm_from_env(model_name: str):
    if Ollama is None:
        raise AnnualReportRagError("Ollama client is not available", status_code=500)
    base_url = os.environ.get("OLLAMA_URL", "http://localhost:11434")
    return Ollama(base_url=base_url, model=model_name)


def _write_index_metadata(index_dir: str, metadata: Dict[str, str]) -> None:
    os.makedirs(index_dir, exist_ok=True)
    metadata_path = os.path.join(index_dir, "index.json")
    with open(metadata_path, "w", encoding="utf-8") as handle:
        json.dump(metadata, handle, indent=2)


def _load_index_metadata(index_dir: str) -> Optional[Dict[str, str]]:
    metadata_path = os.path.join(index_dir, "index.json")
    if not os.path.exists(metadata_path):
        return None
    try:
        with open(metadata_path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except Exception:
        return None


def index_annual_report_rag(
    ticker: str,
    report_year: Optional[int] = None,
    report_type: str = "F04",
    force: bool = False,
    embedding_model: str = DEFAULT_EMBEDDING_MODEL,
    faiss_dir: Optional[str] = None,
) -> AnnualReportRagIndexResponse:
    report_info = download_shareholders_annual_report(
        ticker=ticker,
        report_year=report_year,
        report_type=report_type,
        force=force,
    )

    base_dir = faiss_dir or _faiss_base_dir()
    index_dir = os.path.join(
        base_dir,
        report_info.ticker,
        str(report_info.report_year or "latest"),
        report_type,
    )

    if not force:
        cached = _load_index_metadata(index_dir)
        if cached and os.path.exists(index_dir):
            return AnnualReportRagIndexResponse(**cached)

    documents = _load_documents(report_info.file_path)
    chunks = _chunk_documents(documents)
    embeddings = _build_embeddings(embedding_model)

    if FAISS is None:
        raise AnnualReportRagError("FAISS store not available", status_code=500)
    vectorstore = FAISS.from_documents(chunks, embeddings)
    vectorstore.save_local(index_dir)

    metadata = {
        "status": "ok",
        "ticker": report_info.ticker,
        "report_year": report_info.report_year,
        "roc_year": report_info.roc_year,
        "report_type": report_type,
        "file_path": report_info.file_path,
        "index_path": index_dir,
        "chunk_count": len(chunks),
        "embedding_model": embedding_model,
        "timestamp": datetime.now().isoformat(),
    }
    _write_index_metadata(index_dir, metadata)
    return AnnualReportRagIndexResponse(**metadata)


def query_annual_report_rag(
    ticker: str,
    question: str,
    report_year: Optional[int] = None,
    report_type: str = "F04",
    top_k: int = 4,
    force: bool = False,
    embedding_model: str = DEFAULT_EMBEDDING_MODEL,
    llm_model: str = DEFAULT_LLM_MODEL,
    faiss_dir: Optional[str] = None,
) -> AnnualReportRagQueryResponse:
    index_info = index_annual_report_rag(
        ticker=ticker,
        report_year=report_year,
        report_type=report_type,
        force=force,
        embedding_model=embedding_model,
        faiss_dir=faiss_dir,
    )

    if FAISS is None:
        raise AnnualReportRagError("FAISS store not available", status_code=500)

    embeddings = _build_embeddings(embedding_model)
    vectorstore = FAISS.load_local(
        index_info.index_path,
        embeddings,
        allow_dangerous_deserialization=True,
    )

    query_vector = embeddings.embed_query(question)
    if hasattr(vectorstore, "similarity_search_by_vector"):
        docs = vectorstore.similarity_search_by_vector(query_vector, k=top_k)
    else:
        docs = vectorstore.similarity_search(question, k=top_k)

    context_blocks = [doc.page_content for doc in docs]
    sources = [doc.metadata.get("source", "") for doc in docs]
    context = "\n\n".join(context_blocks)

    llm = _llm_from_env(llm_model)
    prompt = (
        "You are a helpful analyst. Use the provided annual report context to answer the question. "
        "If the answer is not in the context, say you cannot find it.\n\n"
        f"Context:\n{context}\n\nQuestion: {question}\nAnswer:"
    )
    answer = llm.invoke(prompt)

    return AnnualReportRagQueryResponse(
        status="ok",
        ticker=index_info.ticker,
        report_year=index_info.report_year,
        roc_year=index_info.roc_year,
        report_type=index_info.report_type,
        answer=str(answer).strip(),
        sources=sources,
        file_path=index_info.file_path,
        index_path=index_info.index_path,
        llm_model=llm_model,
        embedding_model=embedding_model,
        timestamp=datetime.now().isoformat(),
    )