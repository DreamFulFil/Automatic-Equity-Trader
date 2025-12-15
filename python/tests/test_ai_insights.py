"""
Tests for AI Insights Service
"""

import pytest
from unittest.mock import Mock, patch
from app.services.ai_insights_service import AIInsightsService
from app.services.ollama_service import OllamaService


@pytest.fixture
def mock_ollama():
    """Mock Ollama service"""
    ollama = Mock(spec=OllamaService)
    ollama.generate.return_value = {
        "response": "This is a test AI response"
    }
    return ollama


@pytest.fixture
def ai_insights(mock_ollama):
    """Create AI insights service with mocked Ollama"""
    return AIInsightsService(mock_ollama)


class TestStrategyPerformanceAnalysis:
    """Tests for strategy performance analysis"""
    
    def test_analyze_empty_performances(self, ai_insights):
        """Should handle empty performance list"""
        result = ai_insights.analyze_strategy_performance([])
        
        assert result['summary'] == "No performance data available yet"
        assert result['risk_level'] == "UNKNOWN"
    
    def test_analyze_strategy_performance(self, ai_insights, mock_ollama):
        """Should analyze strategies and rank by Sharpe ratio"""
        performances = [
            {'strategy_name': 'RSI', 'sharpe_ratio': 1.5, 'total_return_pct': 10},
            {'strategy_name': 'MACD', 'sharpe_ratio': 0.8, 'total_return_pct': 5},
            {'strategy_name': 'BB', 'sharpe_ratio': 1.2, 'total_return_pct': 8}
        ]
        
        result = ai_insights.analyze_strategy_performance(performances)
        
        assert result['best_strategy'] == 'RSI'
        assert len(result['top_3_strategies']) == 3
        assert result['top_3_strategies'][0] == 'RSI'
        assert mock_ollama.generate.called
    
    def test_risk_level_assessment(self, ai_insights):
        """Should correctly assess risk levels"""
        # High risk: low Sharpe, high drawdown
        high_risk = {'sharpe_ratio': 0.3, 'max_drawdown_pct': -18}
        assert ai_insights._assess_risk_level(high_risk) == "HIGH"
        
        # Medium risk
        medium_risk = {'sharpe_ratio': 0.8, 'max_drawdown_pct': -11}
        assert ai_insights._assess_risk_level(medium_risk) == "MEDIUM"
        
        # Low risk
        low_risk = {'sharpe_ratio': 1.5, 'max_drawdown_pct': -5}
        assert ai_insights._assess_risk_level(low_risk) == "LOW"


class TestStockPerformanceAnalysis:
    """Tests for stock performance analysis"""
    
    def test_analyze_empty_stock_data(self, ai_insights):
        """Should handle empty stock data"""
        result = ai_insights.analyze_stock_performance([])
        
        assert result['summary'] == "No stock performance data available"
    
    def test_analyze_stock_performance(self, ai_insights, mock_ollama):
        """Should identify best strategy for each stock"""
        stock_data = [
            {'symbol': '2330.TW', 'strategy_name': 'RSI', 'sharpe_ratio': 1.5},
            {'symbol': '2330.TW', 'strategy_name': 'MACD', 'sharpe_ratio': 0.8},
            {'symbol': '2454.TW', 'strategy_name': 'RSI', 'sharpe_ratio': 1.2},
        ]
        
        result = ai_insights.analyze_stock_performance(stock_data)
        
        assert '2330.TW' in result['best_matches']
        assert result['best_matches']['2330.TW']['strategy'] == 'RSI'
        assert result['best_matches']['2330.TW']['sharpe'] == 1.5
        assert mock_ollama.generate.called


class TestPositionSizing:
    """Tests for position sizing recommendations"""
    
    def test_position_sizing_advice(self, ai_insights, mock_ollama):
        """Should generate safe position sizing advice"""
        result = ai_insights.generate_position_sizing_advice(
            capital=80000,
            stock_price=1100,
            risk_level="LOW",
            equity=100000
        )
        
        assert 'suggested_shares' in result
        assert 'position_value' in result
        assert result['risk_percentage'] <= 15  # Should never exceed 15%
        assert mock_ollama.generate.called
    
    def test_position_sizing_respects_max_risk(self, ai_insights):
        """Should never suggest positions > 10% of equity"""
        result = ai_insights.generate_position_sizing_advice(
            capital=80000,
            stock_price=1000,
            risk_level="LOW",
            equity=80000
        )
        
        # Max 10% of 80k = 8000 / 1000 = 8 shares
        assert result['suggested_shares'] <= 8
        assert result['risk_percentage'] <= 10


class TestRiskAnalysis:
    """Tests for risk metric analysis"""
    
    def test_low_risk_assessment(self, ai_insights):
        """Should identify low-risk scenarios"""
        metrics = {
            'max_drawdown_pct': -5,
            'volatility': 1.5,
            'sharpe_ratio': 1.8
        }
        
        result = ai_insights.analyze_risk_metrics(metrics)
        
        assert result['risk_level'] == "LOW"
        assert len(result['warnings']) == 0
        assert not result['action_required']
    
    def test_high_risk_assessment(self, ai_insights):
        """Should identify high-risk scenarios"""
        metrics = {
            'max_drawdown_pct': -18,
            'volatility': 4.5,
            'sharpe_ratio': 0.3
        }
        
        result = ai_insights.analyze_risk_metrics(metrics)
        
        assert result['risk_level'] == "HIGH"
        assert len(result['warnings']) >= 2
        assert result['action_required']
    
    def test_risk_warnings(self, ai_insights):
        """Should generate appropriate warnings"""
        metrics = {
            'max_drawdown_pct': -16,  # Should warn
            'volatility': 2.0,  # OK
            'sharpe_ratio': 0.4  # Should warn
        }
        
        result = ai_insights.analyze_risk_metrics(metrics)
        
        assert any('drawdown' in w.lower() for w in result['warnings'])
        assert any('sharpe' in w.lower() for w in result['warnings'])


class TestDailyReports:
    """Tests for daily report analysis"""
    
    def test_positive_sentiment(self, ai_insights):
        """Should detect positive performance"""
        report_data = {
            'daily_return_pct': 2.5,
            'win_rate_pct': 65,
            'total_trades': 5
        }
        
        sentiment = ai_insights._determine_sentiment(report_data)
        assert sentiment == "POSITIVE"
    
    def test_negative_sentiment(self, ai_insights):
        """Should detect concerning performance"""
        report_data = {
            'daily_return_pct': -3.0,
            'win_rate_pct': 35,
            'total_trades': 5
        }
        
        sentiment = ai_insights._determine_sentiment(report_data)
        assert sentiment == "NEGATIVE"
    
    def test_needs_attention(self, ai_insights):
        """Should flag reports needing user attention"""
        report_data = {
            'daily_return_pct': -3.5,
            'max_drawdown_pct': -16
        }
        
        assert ai_insights._needs_attention(report_data)
        
        # Normal scenario
        normal_report = {
            'daily_return_pct': 1.0,
            'max_drawdown_pct': -5
        }
        
        assert not ai_insights._needs_attention(normal_report)


class TestStrategyExplanations:
    """Tests for strategy switch explanations"""
    
    def test_explain_strategy_switch(self, ai_insights, mock_ollama):
        """Should generate beginner-friendly explanations"""
        explanation = ai_insights.explain_strategy_switch(
            current_strategy="RSI",
            recommended_strategy="MACD",
            reason="Better Sharpe ratio over 7 days"
        )
        
        assert explanation is not None
        assert mock_ollama.generate.called
        
        # Verify prompt contains key information
        call_args = mock_ollama.generate.call_args[0][0]
        assert "RSI" in call_args
        assert "MACD" in call_args


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
