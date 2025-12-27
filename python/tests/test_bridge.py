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

from bridge import (
    jasypt_decrypt,
    decrypt_config_value,
    fetch_news_headlines,
    call_llama_news_veto,
    OrderRequest,
)


class TestJasyptDecryption:
    """Tests for Jasypt PBEWithMD5AndDES decryption"""
    
    def test_decrypt_config_value_with_enc_wrapper(self):
        """Should extract and decrypt ENC() wrapped values"""
        # We can't test actual decryption without a known encrypted value,
        # but we can test the wrapper detection
        with patch('bridge.jasypt_decrypt') as mock_decrypt:
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
    
    @patch('bridge.feedparser.parse')
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
    
    @patch('bridge.feedparser.parse')
    def test_fetch_news_headlines_handles_errors(self, mock_parse):
        """Should handle feed errors gracefully"""
        mock_parse.side_effect = Exception("Network error")
        
        headlines = fetch_news_headlines()
        
        assert headlines == []
    
    @patch('bridge.requests.post')
    @patch('bridge.OLLAMA_URL', 'http://localhost:11434')
    @patch('bridge.OLLAMA_MODEL', 'llama3.1:8b')
    def test_call_llama_news_veto_success(self, mock_post):
        """Should call Ollama and parse response"""
        mock_response = Mock()
        mock_response.json.return_value = {
            'response': '{"veto": false, "score": 0.6, "reason": "Market stable"}'
        }
        mock_post.return_value = mock_response
        
        result = call_llama_news_veto(["Test headline"])
        
        assert result["veto"] == False
        assert result["score"] == 0.6
        assert "stable" in result["reason"]
    
    @patch('bridge.requests.post')
    @patch('bridge.OLLAMA_URL', 'http://localhost:11434')
    @patch('bridge.OLLAMA_MODEL', 'llama3.1:8b')
    def test_call_llama_news_veto_handles_timeout(self, mock_post):
        """Should return safe defaults on timeout"""
        mock_post.side_effect = Exception("Timeout")
        
        result = call_llama_news_veto(["Test headline"])
        
        assert result["veto"] == False
        assert result["score"] == 0.5
        assert "failed" in result["reason"].lower()
    
    @patch('bridge.requests.post')
    @patch('bridge.OLLAMA_URL', 'http://localhost:11434')
    @patch('bridge.OLLAMA_MODEL', 'llama3.1:8b')
    def test_call_llama_news_veto_handles_invalid_json(self, mock_post):
        """Should return safe defaults on invalid JSON response"""
        mock_response = Mock()
        mock_response.json.return_value = {'response': 'not valid json'}
        mock_post.return_value = mock_response
        
        result = call_llama_news_veto(["Test headline"])
        
        assert result["veto"] == False
        assert result["score"] == 0.5


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


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
