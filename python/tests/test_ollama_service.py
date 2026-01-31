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


# =====================================================================
# Phase 5: Risk Scoring Tests
# =====================================================================


class TestLocalRiskScoring:
    """Tests for local risk scoring calculation (no LLM required)"""

    def test_calculate_local_risk_score_low_risk(self):
        """Low risk proposal should have low score"""
        svc = OllamaService("u", "m")
        proposal = {
            "symbol": "2330",
            "direction": "LONG",
            "shares": 50,
            "daily_pnl": 500,
            "weekly_pnl": 2000,
            "drawdown_percent": 0.5,
            "trades_today": 1,
            "win_streak": 2,
            "loss_streak": 0,
            "volatility_level": "normal",
            "news_headlines": [],
            "strategy_days_active": 30,
        }
        result = svc._calculate_local_risk_score(proposal)
        
        assert result["veto"] is False
        assert result["risk_score"] < 70

    def test_calculate_local_risk_score_high_drawdown(self):
        """High drawdown should increase risk score"""
        svc = OllamaService("u", "m")
        proposal = {
            "symbol": "2330",
            "drawdown_percent": 5.0,  # Above 3% danger threshold
            "shares": 50,
            "trades_today": 1,
            "loss_streak": 0,
            "volatility_level": "normal",
        }
        result = svc._calculate_local_risk_score(proposal)
        
        assert result["breakdown"]["drawdown"] > 100  # Over danger threshold

    def test_calculate_local_risk_score_negative_news(self):
        """Negative news should increase news risk"""
        svc = OllamaService("u", "m")
        proposal = {
            "symbol": "2330",
            "news_headlines": ["股價下跌", "利空消息"],
            "shares": 50,
            "trades_today": 1,
        }
        result = svc._calculate_local_risk_score(proposal)
        
        assert result["breakdown"]["news"] >= 50  # 2 negative * 25 each

    def test_calculate_local_risk_score_extreme_volatility(self):
        """Extreme volatility should maximize volatility risk"""
        svc = OllamaService("u", "m")
        proposal = {
            "symbol": "2330",
            "volatility_level": "extreme",
            "shares": 50,
            "trades_today": 1,
        }
        result = svc._calculate_local_risk_score(proposal)
        
        assert result["breakdown"]["volatility"] == 100

    def test_calculate_local_risk_score_losing_streak(self):
        """Losing streak should increase streak risk"""
        svc = OllamaService("u", "m")
        proposal = {
            "symbol": "2330",
            "loss_streak": 3,  # At danger threshold
            "win_streak": 0,
            "shares": 50,
            "trades_today": 1,
        }
        result = svc._calculate_local_risk_score(proposal)
        
        assert result["breakdown"]["streak"] == 100


class TestSignalConfidenceAdjustment:
    """Tests for signal confidence adjustment in risk scoring"""

    def test_high_confidence_reduces_effective_score(self):
        """High confidence should reduce effective risk score by 20%"""
        svc = OllamaService("u", "m")
        
        proposal_low = {
            "symbol": "2330",
            "shares": 100,
            "trades_today": 2,
            "loss_streak": 1,
            "volatility_level": "normal",
        }
        
        proposal_high = dict(proposal_low)
        proposal_high["signal_confidence"] = 0.95
        
        result_low = svc._calculate_local_risk_score(proposal_low)
        result_high = svc._calculate_local_risk_score(proposal_high)
        
        # High confidence should have lower effective score
        assert result_high["risk_score"] < result_low["risk_score"]
        assert result_high["confidence_adjustment"] == 0.8

    def test_low_confidence_increases_effective_score(self):
        """Low confidence should increase effective risk score by 20%"""
        svc = OllamaService("u", "m")
        proposal = {
            "symbol": "2330",
            "shares": 50,
            "trades_today": 1,
            "volatility_level": "normal",
            "signal_confidence": 0.3,  # Low confidence
        }
        result = svc._calculate_local_risk_score(proposal)
        
        assert result["confidence_adjustment"] == 1.2
        assert result["threshold"] == 60.0  # Stricter threshold

    def test_high_confidence_uses_relaxed_threshold(self):
        """High confidence should use 80 as veto threshold"""
        svc = OllamaService("u", "m")
        proposal = {
            "symbol": "2330",
            "shares": 50,
            "trades_today": 1,
            "volatility_level": "normal",
            "signal_confidence": 0.92,
        }
        result = svc._calculate_local_risk_score(proposal)
        
        assert result["threshold"] == 80.0


class TestRiskScoreResponseParsing:
    """Tests for parsing risk score JSON responses"""

    def test_parse_risk_score_response_valid_json(self):
        """Valid JSON response should parse correctly"""
        svc = OllamaService("u", "m")
        json_response = '''{"drawdown_risk": 20, "news_risk": 30, "volatility_risk": 25, 
                           "streak_risk": 15, "size_risk": 20, "total_score": 22.5, 
                           "recommendation": "APPROVE", "reason": "Low risk"}'''
        
        result = svc._parse_risk_score_response(json_response)
        
        assert result["veto"] is False
        assert result["breakdown"]["drawdown"] == 20
        assert result["breakdown"]["news"] == 30

    def test_parse_risk_score_response_fallback_to_legacy(self):
        """Invalid JSON should fall back to legacy parsing"""
        svc = OllamaService("u", "m")
        legacy_response = "VETO: too risky"
        
        result = svc._parse_risk_score_response(legacy_response)
        
        assert result["veto"] is True
        assert result["risk_score"] == 100.0

    def test_parse_risk_score_response_with_confidence(self):
        """Confidence should adjust the score"""
        svc = OllamaService("u", "m")
        json_response = '''{"drawdown_risk": 50, "news_risk": 50, "volatility_risk": 50, 
                           "streak_risk": 50, "size_risk": 50, "total_score": 50, 
                           "recommendation": "APPROVE", "reason": "Moderate risk"}'''
        
        result = svc._parse_risk_score_response(json_response, signal_confidence=0.95)
        
        # High confidence should reduce score by 20%
        assert result["risk_score"] == 50 * 0.8  # 40


class TestCallTradeRiskScore:
    """Tests for call_trade_risk_score method"""

    def test_call_trade_risk_score_no_llm(self):
        """Should use local calculation when use_llm=False"""
        svc = OllamaService("u", "m")
        proposal = {
            "symbol": "2330",
            "shares": 50,
            "trades_today": 1,
            "volatility_level": "normal",
        }
        
        result = svc.call_trade_risk_score(proposal, use_llm=False)
        
        assert "veto" in result
        assert "risk_score" in result
        assert "breakdown" in result

    def test_call_trade_risk_score_fallback_on_error(self):
        """Should fall back to local calculation on LLM error"""
        svc = OllamaService("http://localhost:11434", "m")
        
        with patch.object(svc, "generate", return_value={"error": "boom"}):
            result = svc.call_trade_risk_score({"symbol": "2330"})
        
        # Should have local calculation result, not error
        assert "veto" in result
        assert "risk_score" in result


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
