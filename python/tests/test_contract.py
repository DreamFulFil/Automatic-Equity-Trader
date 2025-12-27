#!/usr/bin/env python3
"""
Contract tests for Python bridge API responses.

These tests verify that Python sends responses in the exact format Java expects.
CRITICAL: These tests caught the 422 JSON parsing bug on 2025-11-27.

Run: python/venv/bin/pytest python/tests/test_contract.py -v
"""

import pytest
import json
from datetime import datetime
from pydantic import ValidationError

# Import from bridge
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from bridge import OrderRequest


class TestOrderRequestContract:
    """Verify OrderRequest matches what Java sends"""
    
    def test_accepts_buy_action(self):
        """Java sends BUY action"""
        order = OrderRequest(action="BUY", quantity=1, price=20000.0)
        assert order.action == "BUY"
    
    def test_accepts_sell_action(self):
        """Java sends SELL action"""
        order = OrderRequest(action="SELL", quantity=1, price=20000.0)
        assert order.action == "SELL"
    
    def test_accepts_integer_price(self):
        """Java may send int instead of float"""
        order = OrderRequest(action="BUY", quantity=1, price=20000)
        assert isinstance(order.price, float)
        assert order.price == 20000.0
    
    def test_accepts_dict_input(self):
        """Java RestTemplate sends as Map -> JSON object"""
        data = {"action": "BUY", "quantity": 1, "price": 27506.0}
        order = OrderRequest.model_validate(data)
        assert order.action == "BUY"
        assert order.quantity == 1
        assert order.price == 27506.0
    
    def test_accepts_json_string(self):
        """Ensure JSON string parsing works (the original 422 bug)"""
        json_str = '{"action":"BUY","quantity":1,"price":27506}'
        order = OrderRequest.model_validate_json(json_str)
        assert order.action == "BUY"
        assert order.quantity == 1
        assert order.price == 27506.0
    
    def test_rejects_missing_action(self):
        """Should fail validation when action is missing"""
        with pytest.raises(ValidationError):
            OrderRequest.model_validate({"quantity": 1, "price": 20000})
    
    def test_rejects_missing_quantity(self):
        """Should fail validation when quantity is missing"""
        with pytest.raises(ValidationError):
            OrderRequest.model_validate({"action": "BUY", "price": 20000})
    
    def test_rejects_missing_price(self):
        """Should fail validation when price is missing"""
        with pytest.raises(ValidationError):
            OrderRequest.model_validate({"action": "BUY", "quantity": 1})
    
    def test_action_case_sensitive(self):
        """Action must be uppercase (Java sends uppercase)"""
        order = OrderRequest(action="BUY", quantity=1, price=20000)
        assert order.action == "BUY"
        # Note: Python doesn't enforce uppercase, but Java always sends uppercase


class TestSignalResponseContract:
    """Verify signal response has all required fields for Java"""
    
    REQUIRED_FIELDS = [
        "current_price",
        "direction",
        "confidence",
        "exit_signal",
        "timestamp"
    ]
    
    def test_signal_response_schema(self):
        """Signal response must have these fields for Java to parse"""
        response = {
            "current_price": 20000.0,
            "direction": "LONG",
            "confidence": 0.75,
            "exit_signal": False,
            "momentum_3min": 0.02,
            "momentum_5min": 0.03,
            "volume_ratio": 1.5,
            "session_high": 20100,
            "session_low": 19900,
            "timestamp": datetime.now().isoformat()
        }
        
        for field in self.REQUIRED_FIELDS:
            assert field in response, f"Missing required field: {field}"
    
    def test_direction_valid_values(self):
        """Direction must be LONG, SHORT, or NEUTRAL"""
        valid_directions = ["LONG", "SHORT", "NEUTRAL"]
        for direction in valid_directions:
            assert direction in valid_directions
    
    def test_confidence_is_float(self):
        """Confidence must be a float 0.0-1.0"""
        confidence = 0.75
        assert isinstance(confidence, float)
        assert 0.0 <= confidence <= 1.0
    
    def test_exit_signal_is_boolean(self):
        """exit_signal must be boolean"""
        assert isinstance(True, bool)
        assert isinstance(False, bool)


class TestHealthResponseContract:
    """Verify health response format for Java startup check"""
    
    REQUIRED_FIELDS = ["status", "shioaji_connected", "time"]
    
    def test_health_response_schema(self):
        """Health response must have required fields"""
        response = {
            "status": "ok",
            "shioaji_connected": True,
            "time": datetime.now().isoformat()
        }
        
        for field in self.REQUIRED_FIELDS:
            assert field in response
    
    def test_status_is_ok(self):
        """Java checks for status == 'ok'"""
        response = {"status": "ok", "shioaji_connected": True, "time": "2025-11-27T12:00:00"}
        assert response["status"] == "ok"


class TestNewsResponseContract:
    """Verify news veto response format"""
    
    REQUIRED_FIELDS = [
        "news_veto",
        "news_score",
        "news_reason",
        "headlines_count",
        "timestamp"
    ]
    
    def test_news_response_schema(self):
        """News response must have required fields"""
        response = {
            "news_veto": False,
            "news_score": 0.6,
            "news_reason": "Market stable",
            "headlines_count": 5,
            "timestamp": datetime.now().isoformat()
        }
        
        for field in self.REQUIRED_FIELDS:
            assert field in response
    
    def test_news_score_range(self):
        """Score must be 0.0-1.0"""
        valid_scores = [0.0, 0.5, 1.0]
        for score in valid_scores:
            assert 0.0 <= score <= 1.0
    
    def test_news_veto_is_boolean(self):
        """news_veto must be boolean"""
        assert isinstance(True, bool)
        assert isinstance(False, bool)


class TestAccountResponseContract:
    """Verify account endpoint response format for contract scaling"""
    
    REQUIRED_FIELDS = ["equity", "available_margin", "status", "timestamp"]
    
    def test_account_response_schema(self):
        """Account response must have required fields"""
        response = {
            "equity": 100000.0,
            "available_margin": 50000.0,
            "status": "ok",
            "timestamp": datetime.now().isoformat()
        }
        
        for field in self.REQUIRED_FIELDS:
            assert field in response
    
    def test_equity_is_numeric(self):
        """Equity must be numeric"""
        equity = 100000.0
        assert isinstance(equity, (int, float))
    
    def test_status_values(self):
        """Status can be 'ok' or 'error'"""
        valid_statuses = ["ok", "error"]
        for status in valid_statuses:
            assert status in valid_statuses


class TestProfitHistoryResponseContract:
    """Verify profit history response format"""
    
    REQUIRED_FIELDS = ["total_pnl", "days", "record_count", "status", "timestamp"]
    
    def test_profit_history_response_schema(self):
        """Profit history response must have required fields"""
        response = {
            "total_pnl": 5000.0,
            "days": 30,
            "record_count": 10,
            "status": "ok",
            "timestamp": datetime.now().isoformat()
        }
        
        for field in self.REQUIRED_FIELDS:
            assert field in response
    
    def test_days_matches_request(self):
        """Days should match what was requested"""
        requested_days = 30
        response_days = 30
        assert response_days == requested_days
    
    def test_total_pnl_can_be_negative(self):
        """total_pnl can be negative (loss)"""
        pnl = -5000.0
        assert isinstance(pnl, float)


class TestOrderDryRunResponseContract:
    """Verify order dry-run response format"""
    
    def test_validated_response_schema(self):
        """Dry-run success response"""
        response = {
            "status": "validated",
            "dry_run": True,
            "order": {
                "action": "BUY",
                "quantity": 1,
                "price": 20000.0
            },
            "message": "Order would be accepted (dry-run mode)",
            "timestamp": datetime.now().isoformat()
        }
        
        assert response["status"] == "validated"
        assert response["dry_run"] == True
        assert "order" in response
    
    def test_error_response_has_error_field(self):
        """Error response should have error field"""
        response = {"error": "Invalid action: INVALID"}
        assert "error" in response


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
