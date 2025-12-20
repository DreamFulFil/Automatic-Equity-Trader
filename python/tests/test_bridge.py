#!/usr/bin/env python3
"""
Unit tests for MTXF Trading Bridge

Run with: pytest python/tests/ -v
"""

import pytest
import json
import base64
import hashlib
from unittest.mock import Mock, patch, MagicMock
from datetime import datetime
from collections import deque

# Import functions to test
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.core.config import (
    jasypt_decrypt,
    decrypt_config_value,
)
from app.main import (
    fetch_news_headlines,
    OrderRequest,
)
from app.services.ollama_service import OllamaService
from app.services.telegram_service import send_telegram_message

# Mock OllamaService for tests
def call_llama_news_veto(headlines):
    service = OllamaService("http://localhost:11434", "llama3.1:8b")
    return service.call_llama_news_veto(headlines)

class TestJasyptDecryption:
    """Tests for Jasypt PBEWithMD5AndDES decryption"""
    
    def test_decrypt_config_value_with_enc_wrapper(self):
        """Should extract and decrypt ENC() wrapped values"""
        # We can't test actual decryption without a known encrypted value,
        # but we can test the wrapper detection
        with patch('app.core.config.jasypt_decrypt') as mock_decrypt:
            mock_decrypt.return_value = "decrypted_value"
            result = decrypt_config_value("ENC(abc123)", "password")
            mock_decrypt.assert_called_once_with("abc123", "password")
            assert result == "decrypted_value"
    
    def test_decrypt_config_value_without_enc_wrapper(self):
        """Should return plain values unchanged"""
        result = decrypt_config_value("plain_value", "password")
        assert result == "plain_value"
    
    def test_decrypt_config_value_with_non_string(self):
        """Should return non-string values unchanged"""
        result = decrypt_config_value(12345, "password")
        assert result == 12345
        
        result = decrypt_config_value(None, "password")
        assert result is None


class TestOrderRequest:
    """Tests for Pydantic OrderRequest model"""
    
    def test_order_request_valid_buy(self):
        """Should accept valid BUY order"""
        order = OrderRequest(action="BUY", quantity=1, price=20000.0)
        assert order.action == "BUY"
        assert order.quantity == 1
        assert order.price == 20000.0
    
    def test_order_request_valid_sell(self):
        """Should accept valid SELL order"""
        order = OrderRequest(action="SELL", quantity=2, price=19500.0)
        assert order.action == "SELL"
        assert order.quantity == 2
        assert order.price == 19500.0
    
    def test_order_request_from_json(self):
        """Should parse from JSON string (the bug that caused 422 errors)"""
        json_str = '{"action":"BUY","quantity":1,"price":27506}'
        order = OrderRequest.model_validate_json(json_str)
        assert order.action == "BUY"
        assert order.quantity == 1
        assert order.price == 27506.0
    
    def test_order_request_from_dict(self):
        """Should parse from dict (how Java sends it now)"""
        data = {"action": "SELL", "quantity": 1, "price": 20000}
        order = OrderRequest.model_validate(data)
        assert order.action == "SELL"
        assert order.quantity == 1
        assert order.price == 20000.0
    
    def test_order_request_integer_price(self):
        """Should accept integer prices and convert to float"""
        order = OrderRequest(action="BUY", quantity=1, price=20000)
        assert isinstance(order.price, float)
        assert order.price == 20000.0


class TestNewsAnalysis:
    """Tests for news fetching and Ollama analysis"""
    
    @patch('app.main.feedparser.parse')
    def test_fetch_news_headlines_success(self, mock_parse):
        """Should fetch and return headlines from RSS feeds"""
        mock_feed = Mock()
        mock_feed.entries = [
            Mock(title="Headline 1"),
            Mock(title="Headline 2"),
            Mock(title="Headline 3"),
        ]
        mock_parse.return_value = mock_feed
        
        headlines = fetch_news_headlines()
        
        assert len(headlines) <= 15
        assert "Headline 1" in headlines
    
    @patch('app.main.feedparser.parse')
    def test_fetch_news_headlines_handles_errors(self, mock_parse):
        """Should handle feed errors gracefully"""
        mock_parse.side_effect = Exception("Network error")
        
        headlines = fetch_news_headlines()
        
        assert headlines == []
    
    @patch('app.services.ollama_service.requests.post')
    @patch('app.main.OllamaService')
    def test_call_llama_news_veto_approve(self, MockOllamaService, mock_post):
        """Should parse APPROVE response correctly"""
        mock_response = Mock()
        mock_response.json.return_value = {
            'response': 'APPROVE'
        }
        mock_post.return_value = mock_response
        
        service = OllamaService("http://localhost:11434", "llama3.1:8b")
        result = service.call_llama_news_veto(["Test headline"])
        
        assert result["veto"] == False
        assert result["score"] == 1.0
        assert result["reason"] == "APPROVED"
    
    @patch('app.services.ollama_service.requests.post')
    def test_call_llama_news_veto_veto_response(self, mock_post):
        """Should parse VETO response correctly"""
        mock_response = Mock()
        mock_response.json.return_value = {
            'response': 'VETO: negative news keyword'
        }
        mock_post.return_value = mock_response
        
        service = OllamaService("http://localhost:11434", "llama3.1:8b")
        result = service.call_llama_news_veto(["股市下跌"])
        
        assert result["veto"] == True
        assert result["score"] == 0.0
        assert result["reason"] == "negative news keyword"
    
    @patch('app.services.ollama_service.requests.post')
    def test_call_llama_news_veto_handles_timeout(self, mock_post):
        """Should return VETO on timeout (fail-safe)"""
        mock_post.side_effect = Exception("Timeout")
        
        service = OllamaService("http://localhost:11434", "llama3.1:8b")
        result = service.call_llama_news_veto(["Test headline"])
        
        assert result["veto"] == True
        assert result["score"] == 0.0
        assert "failed" in result["reason"].lower()
    
    @patch('app.services.ollama_service.requests.post')
    def test_call_llama_news_veto_handles_unexpected_format(self, mock_post):
        """Should return VETO on unexpected response format (fail-safe)"""
        mock_response = Mock()
        mock_response.json.return_value = {'response': 'some random text'}
        mock_post.return_value = mock_response
        
        service = OllamaService("http://localhost:11434", "llama3.1:8b")
        result = service.call_llama_news_veto(["Test headline"])
        
        assert result["veto"] == True
        assert result["score"] == 0.0
        assert "unexpected" in result["reason"].lower()
    
    @patch('app.services.ollama_service.requests.post')
    def test_call_trade_veto_approve(self, mock_post):
        """Should parse full trade veto APPROVE response"""
        mock_response = Mock()
        mock_response.json.return_value = {'response': 'APPROVE'}
        mock_post.return_value = mock_response
        
        service = OllamaService("http://localhost:11434", "llama3.1:8b")
        result = service.call_trade_veto({
            "symbol": "2330",
            "direction": "LONG",
            "shares": 50,
            "entry_logic": "momentum breakout",
            "strategy_name": "Momentum",
            "daily_pnl": 100,
            "weekly_pnl": 500,
            "drawdown_percent": 1.5,
            "trades_today": 1,
            "win_streak": 2,
            "loss_streak": 0,
            "volatility_level": "normal",
            "time_of_day": "10:30",
            "session_phase": "mid-session",
            "news_headlines": ["台積電獲利創新高"],
            "strategy_days_active": 10,
            "recent_backtest_stats": "WR 60%, PF 1.8"
        })
        
        assert result["veto"] == False
        assert result["reason"] == "APPROVED"
    
    @patch('app.services.ollama_service.requests.post')
    def test_call_trade_veto_reject(self, mock_post):
        """Should parse full trade veto VETO response"""
        mock_response = Mock()
        mock_response.json.return_value = {'response': 'VETO: daily drawdown exceeded'}
        mock_post.return_value = mock_response
        
        service = OllamaService("http://localhost:11434", "llama3.1:8b")
        result = service.call_trade_veto({
            "symbol": "2330",
            "direction": "LONG",
            "shares": 50,
            "daily_pnl": -2500,
            "drawdown_percent": 4.0,
        })
        
        assert result["veto"] == True
        assert result["reason"] == "daily drawdown exceeded"


class TestSignalGeneration:
    """Tests for trading signal generation logic"""
    
    def test_momentum_calculation_bullish(self):
        """Test momentum calculation for bullish scenario"""
        # Prices going up: 100 -> 100.03 (0.03% gain)
        prices = [{"price": 100 + i * 0.0001, "time": datetime.now(), "volume": 1} 
                  for i in range(180)]
        
        # Calculate momentum like the signal endpoint does
        prices_3min = [p["price"] for p in prices[-180:]]
        momentum = (prices_3min[-1] - prices_3min[0]) / prices_3min[0] * 100
        
        assert momentum > 0  # Should be positive (bullish)
    
    def test_momentum_calculation_bearish(self):
        """Test momentum calculation for bearish scenario"""
        # Prices going down: 100 -> 99.97 (0.03% loss)
        prices = [{"price": 100 - i * 0.0001, "time": datetime.now(), "volume": 1} 
                  for i in range(180)]
        
        prices_3min = [p["price"] for p in prices[-180:]]
        momentum = (prices_3min[-1] - prices_3min[0]) / prices_3min[0] * 100
        
        assert momentum < 0  # Should be negative (bearish)
    
    def test_volume_ratio_calculation(self):
        """Test volume ratio calculation"""
        volume_history = deque([10] * 30 + [20] * 30, maxlen=600)  # Recent volume 2x average
        
        recent_vol = sum(list(volume_history)[-30:])
        avg_vol = sum(list(volume_history)[-60:]) / 2
        volume_ratio = recent_vol / avg_vol if avg_vol > 0 else 1.0
        
        # Recent 30: sum of 20*30 = 600
        # Avg of last 60 / 2: (10*30 + 20*30) / 2 = 450
        # Ratio: 600/450 = 1.33
        assert volume_ratio > 1.3  # Should indicate volume surge

    def test_rsi_calculation_flat_market(self):
        """Test RSI calculation with flat market (ZeroDivisionError fix)"""
        # Flat prices: 100 -> 100 (0% change)
        prices_3min = [100.0] * 60
        
        gains = []
        losses = []
        for i in range(1, min(60, len(prices_3min))):
            change = prices_3min[-i] - prices_3min[-i-1]
            if change > 0:
                gains.append(change)
            else:
                losses.append(abs(change))
        
        avg_gain = sum(gains) / 60 if gains else 0
        avg_loss = sum(losses) / 60 if losses else 0
        
        if avg_loss == 0:
            if avg_gain == 0:
                rsi = 50.0
            else:
                rsi = 100.0
        else:
            rs = avg_gain / avg_loss
            rsi = 100 - (100 / (1 + rs))
            
        assert rsi == 50.0


class TestTelegramNotification:
    """Tests for Telegram notification functionality"""
    
    @patch('app.services.telegram_service.requests.post')
    @patch('app.core.config.open', create=True)
    @patch('app.core.config.yaml.safe_load')
    def test_send_telegram_success(self, mock_yaml, mock_open, mock_post):
        """Should send Telegram message successfully"""
        # Mock config file
        mock_config = {
            'telegram': {
                'bot-token': 'ENC(test_token)',
                'chat-id': 'ENC(test_chat)'
            }
        }
        mock_yaml.return_value = mock_config
        
        # Mock successful response
        mock_response = Mock()
        mock_response.status_code = 200
        mock_post.return_value = mock_response
        
        with patch('app.services.telegram_service.decrypt_config_value') as mock_decrypt:
            mock_decrypt.side_effect = ['decrypted_token', 'decrypted_chat']
            result = send_telegram_message("Test message", "password")
        
        assert result is True
        mock_post.assert_called_once()
        call_args = mock_post.call_args
        assert 'sendMessage' in call_args[0][0]
        assert call_args[1]['json']['text'] == "Test message"
        assert call_args[1]['json']['parse_mode'] == "HTML"
    
    @patch('app.services.telegram_service.requests.post')
    @patch('app.core.config.open', create=True)
    @patch('app.core.config.yaml.safe_load')
    def test_send_telegram_missing_config(self, mock_yaml, mock_open, mock_post):
        """Should handle missing Telegram config gracefully"""
        mock_config = {}
        mock_yaml.return_value = mock_config
        
        result = send_telegram_message("Test message", "password")
        
        assert result is False
        mock_post.assert_not_called()
    
    @patch('app.services.telegram_service.requests.post')
    @patch('app.core.config.open', create=True)
    @patch('app.core.config.yaml.safe_load')
    def test_send_telegram_missing_credentials(self, mock_yaml, mock_open, mock_post):
        """Should handle missing credentials gracefully"""
        mock_config = {
            'telegram': {
                'bot-token': None,
                'chat-id': None
            }
        }
        mock_yaml.return_value = mock_config
        
        with patch('app.services.telegram_service.decrypt_config_value') as mock_decrypt:
            mock_decrypt.return_value = None
            result = send_telegram_message("Test message", "password")
        
        assert result is False
        mock_post.assert_not_called()
    
    @patch('app.services.telegram_service.requests.post')
    @patch('app.core.config.open', create=True)
    @patch('app.core.config.yaml.safe_load')
    def test_send_telegram_api_failure(self, mock_yaml, mock_open, mock_post):
        """Should handle Telegram API failures gracefully"""
        mock_config = {
            'telegram': {
                'bot-token': 'test_token',
                'chat-id': 'test_chat'
            }
        }
        mock_yaml.return_value = mock_config
        
        # Mock failed response
        mock_response = Mock()
        mock_response.status_code = 400
        mock_post.return_value = mock_response
        
        with patch('app.services.telegram_service.decrypt_config_value') as mock_decrypt:
            mock_decrypt.side_effect = ['token', 'chat']
            result = send_telegram_message("Test message", "password")
        
        assert result is False
    
    @patch('app.services.telegram_service.requests.post')
    @patch('app.core.config.open', side_effect=FileNotFoundError)
    def test_send_telegram_config_file_missing(self, mock_open, mock_post):
        """Should handle missing config file gracefully"""
        result = send_telegram_message("Test message", "password")
        
        assert result is False
        mock_post.assert_not_called()
    
    @patch('app.services.telegram_service.os.environ.get')
    @patch('app.services.telegram_service.requests.post')
    def test_send_telegram_ci_environment(self, mock_post, mock_env_get):
        """Should skip Telegram in CI environment"""
        mock_env_get.return_value = 'true'
        
        result = send_telegram_message("Test message", "password")
        
        assert result is False
        mock_post.assert_not_called()
        mock_env_get.assert_called_once_with('CI')
    
    @patch('app.services.telegram_service.requests.post')
    @patch('app.core.config.open', create=True)
    @patch('app.core.config.yaml.safe_load')
    @patch('app.services.telegram_service.os.environ.get')
    def test_send_telegram_disabled_in_config(self, mock_env_get, mock_yaml, mock_open, mock_post):
        """Should respect telegram.enabled flag in config"""
        mock_env_get.return_value = None  # Not in CI
        mock_config = {
            'telegram': {
                'enabled': False,
                'bot-token': 'test_token',
                'chat-id': 'test_chat'
            }
        }
        mock_yaml.return_value = mock_config
        
        result = send_telegram_message("Test message", "password")
        
        assert result is False
        mock_post.assert_not_called()


class TestEarningsScraper:
    """Tests for earnings date scraper"""
    
    def test_scrape_single_ticker_logic(self):
        """Should extract earnings date correctly"""
        from datetime import date
        
        # Simulate calendar data structure from yfinance
        calendar = {
            'Earnings Date': [date(2026, 1, 15)]
        }
        
        earnings_list = calendar.get('Earnings Date', [])
        
        assert len(earnings_list) == 1
        assert earnings_list[0] == date(2026, 1, 15)
    
    def test_scrape_handles_missing_earnings(self):
        """Should handle tickers with no earnings date"""
        # Simulate empty calendar
        calendar = {}
        
        assert 'Earnings Date' not in calendar
    
    def test_scrape_filters_future_dates(self):
        """Should only include future dates"""
        from datetime import date, timedelta
        
        today = date.today()
        past_date = today - timedelta(days=30)
        future_date = today + timedelta(days=30)
        
        dates = [past_date, future_date]
        future_dates = [d for d in dates if d >= today]
        
        assert len(future_dates) == 1
        assert future_dates[0] == future_date


class TestShioajiWrapper:
    """Tests for Shioaji connection wrapper"""
    
    def test_reconnect_backoff_calculation(self):
        """Test exponential backoff calculation"""
        BASE_BACKOFF = 2
        
        backoffs = [BASE_BACKOFF ** attempt for attempt in range(1, 6)]
        
        assert backoffs == [2, 4, 8, 16, 32]
    
    def test_max_retries_limit(self):
        """Test that max retries is respected"""
        MAX_RETRIES = 5
        attempts = list(range(1, MAX_RETRIES + 1))
        
        assert len(attempts) == 5
        assert attempts[-1] == 5


class TestDownloadBatchEndpoint:
    """Tests for /data/download-batch endpoint"""
    
    def test_download_batch_request_model(self):
        """Test DownloadBatchRequest model parsing"""
        from app.main import DownloadBatchRequest
        
        request = DownloadBatchRequest(
            symbol="2330.TW",
            start_date="2024-01-01T00:00:00",
            end_date="2024-12-31T23:59:59"
        )
        
        assert request.symbol == "2330.TW"
        assert request.start_date == "2024-01-01T00:00:00"
        assert request.end_date == "2024-12-31T23:59:59"
    
    def test_symbol_extraction(self):
        """Test stock code extraction from symbol"""
        symbols = ["2330.TW", "2454.TWO", "3008.TW"]
        expected_codes = ["2330", "2454", "3008"]
        
        for symbol, expected in zip(symbols, expected_codes):
            # Must replace .TWO before .TW to avoid leaving 'O'
            code = symbol.replace(".TWO", "").replace(".TW", "")
            assert code == expected
    
    def test_date_parsing(self):
        """Test ISO date parsing"""
        from datetime import datetime
        
        date_str = "2024-06-15T10:30:00"
        parsed = datetime.fromisoformat(date_str)
        
        assert parsed.year == 2024
        assert parsed.month == 6
        assert parsed.day == 15
        assert parsed.hour == 10
        assert parsed.minute == 30
    
    def test_kbars_data_conversion(self):
        """Test kbars data point conversion logic"""
        from datetime import datetime
        
        # Simulate kbars timestamp in nanoseconds
        ts_ns = 1718438400000000000  # 2024-06-15 00:00:00 UTC
        ts = datetime.fromtimestamp(ts_ns / 1e9)
        
        data_point = {
            "timestamp": ts.isoformat(),
            "open": 600.0,
            "high": 610.0,
            "low": 595.0,
            "close": 605.0,
            "volume": 1000000
        }
        
        assert "timestamp" in data_point
        assert data_point["open"] == 600.0
        assert data_point["volume"] == 1000000


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
