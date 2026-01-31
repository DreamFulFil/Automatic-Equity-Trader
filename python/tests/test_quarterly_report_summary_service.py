from types import SimpleNamespace

import app.services.quarterly_report_summary_service as summary_service


def test_summarize_quarterly_report_success(tmp_path, monkeypatch):
    report_path = tmp_path / "report.txt"
    report_path.write_text("Quarterly report content", encoding="utf-8")

    mock_report = SimpleNamespace(
        ticker="2330",
        report_year=2023,
        report_quarter=1,
        roc_year=112,
        file_path=str(report_path),
    )

    def fake_download(**_):
        return mock_report

    class DummyLLM:
        def __init__(self):
            self.calls = []

    class DummyChain:
        def __init__(self):
            self.runs = []

        def run(self, docs):
            self.runs.append(docs)
            return "Summary output"

    def fake_load_chain(llm, chain_type="map_reduce"):
        assert chain_type == "map_reduce"
        return DummyChain()

    monkeypatch.setattr(summary_service, "download_quarterly_financial_report", fake_download)
    monkeypatch.setattr(summary_service, "_load_documents", lambda _: [SimpleNamespace(page_content="doc", metadata={})])
    monkeypatch.setattr(summary_service, "_chunk_documents", lambda docs: docs)
    monkeypatch.setattr(summary_service, "_llm_from_env", lambda model: DummyLLM())
    monkeypatch.setattr(summary_service, "load_summarize_chain", fake_load_chain)

    result = summary_service.summarize_quarterly_report(
        ticker="2330",
        report_year=2023,
        report_quarter=1,
        report_type="F01",
        force=False,
    )

    assert result.status == "ok"
    assert result.summary == "Summary output"
    assert result.chunk_count == 1
