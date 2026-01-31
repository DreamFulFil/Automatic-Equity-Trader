import os
from unittest.mock import MagicMock

import pytest

from app.services.quarterly_report_service import (
    QuarterlyReportDownloadError,
    download_quarterly_financial_report,
)


def _make_response(content: bytes, headers=None, chunks=None):
    response = MagicMock()
    response.content = content
    response.headers = headers or {"Content-Type": "application/pdf"}
    response.raise_for_status = MagicMock()
    response.iter_content = MagicMock(return_value=chunks or [b"report", b"data"])
    return response


def _sample_html():
    return (
        "<a href='javascript:readfile2(\"F\",\"2330\",\"2023Q1_2330.pdf\");'>"
        "2023Q1_2330.pdf</a>"
    ).encode("utf-8")


def test_download_quarterly_report_success(tmp_path):
    session = MagicMock()
    session.post.side_effect = [
        _make_response(_sample_html()),
        _make_response(b"", chunks=[b"abc", b"def"]),
    ]

    result = download_quarterly_financial_report(
        ticker="2330",
        report_year=2023,
        report_quarter=1,
        report_type="F01",
        destination_dir=str(tmp_path),
        session=session,
    )

    assert result.status == "success"
    assert result.ticker == "2330"
    assert result.report_year == 2023
    assert result.report_quarter == 1
    assert result.roc_year == 112
    assert os.path.exists(result.file_path)
    assert result.size_bytes == 6

    first_call = session.post.call_args_list[0]
    assert first_call.kwargs["data"]["season"] == "1"
    assert first_call.kwargs["data"]["dtype"] == "F01"


def test_download_quarterly_report_invalid_ticker():
    with pytest.raises(QuarterlyReportDownloadError):
        download_quarterly_financial_report(ticker="AAPL", report_year=2023, report_quarter=1)


def test_download_quarterly_report_missing_result(tmp_path):
    session = MagicMock()
    session.post.side_effect = [
        _make_response(b"<html>No results</html>"),
    ]

    with pytest.raises(QuarterlyReportDownloadError):
        download_quarterly_financial_report(
            ticker="2330",
            report_year=2023,
            report_quarter=1,
            destination_dir=str(tmp_path),
            session=session,
        )
