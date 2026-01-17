import pytest
from unittest.mock import patch

from app.services.ollama_service import OllamaService


def test_generate_returns_not_configured_when_missing_url_or_model():
    assert "error" in OllamaService("", "m").generate("p")
    assert "error" in OllamaService("u", "").generate("p")


def test_generate_happy_path_calls_requests_and_returns_response():
    svc = OllamaService("http://localhost:11434", "m")

    class Resp:
        def json(self):
            return {"response": "ok"}

    with patch("app.services.ollama_service.requests.post", return_value=Resp()) as post:
        out = svc.generate("hello", options={"temperature": 0.1}, system="sys")

    assert out == {"response": "ok"}
    assert post.called


@pytest.mark.parametrize(
    "text,expect_veto",
    [
        ("APPROVE", False),
        ("APPROVE\nextra", False),
        ("VETO: too risky", True),
        ("weird", True),
    ],
)
def test_parse_veto_response(text, expect_veto):
    svc = OllamaService("u", "m")
    parsed = svc._parse_veto_response(text)
    assert parsed["veto"] is expect_veto


def test_call_trade_veto_returns_error_when_generate_returns_error():
    svc = OllamaService("u", "m")

    with patch.object(svc, "generate", return_value={"error": "boom"}):
        res = svc.call_trade_veto({"symbol": "2330.TW"})

    assert res["veto"] is True
    assert "Analysis failed" in res["reason"]


def test_call_trade_veto_exception_path():
    svc = OllamaService("u", "m")

    with patch.object(svc, "generate", side_effect=RuntimeError("x")):
        res = svc.call_trade_veto({"symbol": "2330.TW"})

    assert res["veto"] is True
    assert "Analysis failed" in res["reason"]


def test_call_llama_news_veto_error_and_exception_paths():
    svc = OllamaService("u", "m")

    with patch.object(svc, "generate", return_value={"error": "boom"}):
        res = svc.call_llama_news_veto(["good"])
        assert res["veto"] is True

    with patch.object(svc, "generate", side_effect=RuntimeError("x")):
        res2 = svc.call_llama_news_veto(["good"])
        assert res2["veto"] is True


def test_call_llama_error_explanation_not_configured_path():
    svc = OllamaService("", "")
    out = svc.call_llama_error_explanation("E", "msg")
    assert out["explanation"] == "msg"


def test_call_llama_error_explanation_generate_error_and_json_parse_fail():
    svc = OllamaService("u", "m")

    with patch.object(svc, "generate", return_value={"error": "boom"}):
        out = svc.call_llama_error_explanation("E", "msg")
        assert out["explanation"] == "msg"

    with patch.object(svc, "generate", return_value={"response": "not json"}):
        out2 = svc.call_llama_error_explanation("E", "msg")
        assert out2["explanation"] == "msg"
