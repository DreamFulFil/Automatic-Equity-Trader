from types import SimpleNamespace

import pytest

import app.services.annual_report_rag_service as rag_service


def test_index_annual_report_rag_success(tmp_path, monkeypatch):
    report_path = tmp_path / "report.txt"
    report_path.write_text("Annual report content", encoding="utf-8")

    mock_report = SimpleNamespace(
        ticker="2330",
        report_year=2023,
        roc_year=112,
        file_path=str(report_path),
    )

    def fake_download(**_):
        return mock_report

    class DummyVectorstore:
        def __init__(self):
            self.saved = None

        def save_local(self, path):
            self.saved = path

    class DummyFAISS:
        @staticmethod
        def from_documents(docs, embeddings):
            return DummyVectorstore()

    monkeypatch.setattr(rag_service, "download_shareholders_annual_report", fake_download)
    monkeypatch.setattr(rag_service, "_load_documents", lambda _: [SimpleNamespace(page_content="doc", metadata={})])
    monkeypatch.setattr(rag_service, "_chunk_documents", lambda docs: docs)
    monkeypatch.setattr(rag_service, "_build_embeddings", lambda model: "embeddings")
    monkeypatch.setattr(rag_service, "FAISS", DummyFAISS)

    result = rag_service.index_annual_report_rag(
        ticker="2330",
        report_year=2023,
        report_type="F04",
        faiss_dir=str(tmp_path / "faiss"),
        force=True,
    )

    assert result.status == "ok"
    assert result.ticker == "2330"
    assert result.chunk_count == 1
    assert "faiss" in result.index_path


def test_query_annual_report_rag_uses_embeddings(monkeypatch):
    index_info = rag_service.AnnualReportRagIndexResponse(
        status="ok",
        ticker="2330",
        report_year=2023,
        roc_year=112,
        report_type="F04",
        file_path="/tmp/report.txt",
        index_path="/tmp/index",
        chunk_count=1,
        embedding_model="BAAI/bge-m3",
        timestamp="2026-01-01T00:00:00",
    )

    class DummyEmbeddings:
        def embed_query(self, query):
            assert query == "What is revenue?"
            return [1.0, 2.0, 3.0]

    captured = {}

    class DummyVectorstore:
        def similarity_search_by_vector(self, vector, k=4):
            captured["vector"] = vector
            captured["k"] = k
            return [SimpleNamespace(page_content="Revenue is X.", metadata={"source": "file"})]

    class DummyFAISS:
        @staticmethod
        def load_local(path, embeddings, allow_dangerous_deserialization=False):
            assert path == "/tmp/index"
            assert allow_dangerous_deserialization is True
            return DummyVectorstore()

    class DummyLLM:
        def invoke(self, prompt):
            assert "Revenue is X" in prompt
            return "Revenue is X."

    monkeypatch.setattr(rag_service, "index_annual_report_rag", lambda **_: index_info)
    monkeypatch.setattr(rag_service, "_build_embeddings", lambda model: DummyEmbeddings())
    monkeypatch.setattr(rag_service, "FAISS", DummyFAISS)
    monkeypatch.setattr(rag_service, "_llm_from_env", lambda model: DummyLLM())

    result = rag_service.query_annual_report_rag(
        ticker="2330",
        question="What is revenue?",
        report_year=2023,
        report_type="F04",
        top_k=2,
        force=False,
    )

    assert result.status == "ok"
    assert result.answer == "Revenue is X."
    assert captured["vector"] == [1.0, 2.0, 3.0]
    assert captured["k"] == 2
    assert result.sources == ["file"]
