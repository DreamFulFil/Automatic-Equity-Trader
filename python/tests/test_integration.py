#!/usr/bin/env python3
"""
Integration tests for MTXF Trading Bridge

These tests verify the full HTTP request/response cycle and
interactions between Python bridge, Java, and Ollama.

Run with: 
    BRIDGE_URL=http://localhost:8888 pytest python/tests/test_integration.py -v

Requires:
    - Python bridge running on port 8888
    - Ollama running on port 11434 (for news tests)
"""

import pytest
import requests
import json
import os
from datetime import datetime


# Skip all tests if BRIDGE_URL not set
BRIDGE_URL = os.environ.get('BRIDGE_URL', 'http://localhost:8888')
OLLAMA_URL = os.environ.get('OLLAMA_URL', 'http://localhost:11434')


def bridge_available():
    """Check if Python bridge is available"""
    try:
        r = requests.get(f"{BRIDGE_URL}/health", timeout=2)
        return r.status_code == 200
    except:
        return False


def ollama_available():
    """Check if Ollama is available"""
    try:
        r = requests.get(f"{OLLAMA_URL}/api/tags", timeout=2)
        return r.status_code == 200
    except:
        return False


@pytest.mark.skipif(not bridge_available(), reason="Python bridge not available")
class TestBridgeEndpoints:
    """Tests for Python bridge HTTP endpoints"""
    
    def test_health_endpoint(self):
        """GET /health should return status ok"""
        r = requests.get(f"{BRIDGE_URL}/health")
        
        assert r.status_code == 200
        data = r.json()
        assert data["status"] == "ok"
        assert "shioaji_connected" in data
        assert "time" in data
    
    def test_signal_endpoint(self):
        """GET /signal should return trading signal"""
        r = requests.get(f"{BRIDGE_URL}/signal")
        
        assert r.status_code == 200
        data = r.json()
        assert "current_price" in data
        assert "direction" in data
        assert data["direction"] in ["LONG", "SHORT", "NEUTRAL"]
        assert "confidence" in data
        assert 0 <= data["confidence"] <= 1
        assert "exit_signal" in data
        assert "timestamp" in data
    
    def test_order_dry_run_buy(self):
        """POST /order/dry-run with BUY should validate"""
        payload = {"action": "BUY", "quantity": 1, "price": 20000.0}
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        
        assert r.status_code == 200
        data = r.json()
        assert data["status"] == "validated"
        assert data["dry_run"] == True
        assert data["order"]["action"] == "BUY"
    
    def test_order_dry_run_sell(self):
        """POST /order/dry-run with SELL should validate"""
        payload = {"action": "SELL", "quantity": 1, "price": 20000.0}
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        
        assert r.status_code == 200
        data = r.json()
        assert data["status"] == "validated"
        assert data["order"]["action"] == "SELL"
    
    def test_order_dry_run_invalid_action(self):
        """POST /order/dry-run with invalid action should return 400"""
        payload = {"action": "INVALID", "quantity": 1, "price": 20000.0}
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        
        assert r.status_code == 400
    
    def test_order_dry_run_zero_quantity(self):
        """POST /order/dry-run with zero quantity should return 400"""
        payload = {"action": "BUY", "quantity": 0, "price": 20000.0}
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        
        assert r.status_code == 400
    
    def test_order_dry_run_negative_price(self):
        """POST /order/dry-run with negative price should return 400"""
        payload = {"action": "BUY", "quantity": 1, "price": -100.0}
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        
        assert r.status_code == 400
    
    def test_order_dry_run_integer_price(self):
        """POST /order/dry-run should accept integer prices"""
        payload = {"action": "BUY", "quantity": 1, "price": 20000}  # int not float
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        
        assert r.status_code == 200
        data = r.json()
        assert data["status"] == "validated"
    
    def test_order_endpoint_rejects_invalid_json(self):
        """POST /order/dry-run with malformed JSON should return 422"""
        r = requests.post(
            f"{BRIDGE_URL}/order/dry-run",
            data="not json",
            headers={"Content-Type": "application/json"}
        )
        
        assert r.status_code == 422


@pytest.mark.skipif(not bridge_available(), reason="Python bridge not available")
@pytest.mark.skipif(not ollama_available(), reason="Ollama not available")
class TestOllamaIntegration:
    """Tests for Ollama integration via Python bridge"""
    
    def test_news_endpoint_returns_veto_decision(self):
        """GET /signal/news should return news analysis"""
        r = requests.get(f"{BRIDGE_URL}/signal/news", timeout=10)
        
        assert r.status_code == 200
        data = r.json()
        assert "news_veto" in data
        assert isinstance(data["news_veto"], bool)
        assert "news_score" in data
        assert 0 <= data["news_score"] <= 1
        assert "news_reason" in data
        assert "headlines_count" in data
        assert "timestamp" in data
    
    def test_news_endpoint_handles_empty_headlines(self):
        """News endpoint should work even with no headlines"""
        r = requests.get(f"{BRIDGE_URL}/signal/news", timeout=10)
        
        assert r.status_code == 200
        data = r.json()
        # Should return safe defaults if no headlines
        assert "news_veto" in data


@pytest.mark.skipif(not bridge_available(), reason="Python bridge not available")
class TestJavaPythonInteraction:
    """Tests simulating how Java TradingEngine calls Python bridge"""
    
    def test_java_style_order_request(self):
        """
        Simulate Java RestTemplate sending order as Map.
        This is the exact scenario that caused the 422 bug.
        """
        # Java sends Map which becomes JSON with proper Content-Type
        headers = {"Content-Type": "application/json"}
        payload = {"action": "BUY", "quantity": 1, "price": 27506.0}
        
        r = requests.post(
            f"{BRIDGE_URL}/order/dry-run",
            json=payload,
            headers=headers
        )
        
        assert r.status_code == 200
        assert "validated" in r.text
    
    def test_signal_polling_sequence(self):
        """
        Simulate Java's 30-second signal polling.
        Get signal multiple times to ensure consistency.
        """
        for i in range(3):
            r = requests.get(f"{BRIDGE_URL}/signal")
            assert r.status_code == 200
            data = r.json()
            assert data["direction"] in ["LONG", "SHORT", "NEUTRAL"]
    
    def test_health_check_sequence(self):
        """
        Simulate Java's startup health check sequence.
        """
        # Step 1: Check health
        r1 = requests.get(f"{BRIDGE_URL}/health")
        assert r1.status_code == 200
        assert r1.json()["status"] == "ok"
        
        # Step 2: Test dry-run order (pre-market check)
        r2 = requests.post(
            f"{BRIDGE_URL}/order/dry-run",
            json={"action": "BUY", "quantity": 1, "price": 20000.0}
        )
        assert r2.status_code == 200
        assert "validated" in r2.text
    
    def test_full_trading_cycle_simulation(self):
        """
        Simulate a complete trading cycle:
        1. Get signal
        2. If high confidence, validate order
        3. Check news veto
        """
        # Step 1: Get signal
        signal_r = requests.get(f"{BRIDGE_URL}/signal")
        assert signal_r.status_code == 200
        signal = signal_r.json()
        
        price = signal.get("current_price", 20000)
        direction = signal.get("direction", "NEUTRAL")
        
        # Step 2: Validate order (dry-run)
        if direction in ["LONG", "SHORT"]:
            action = "BUY" if direction == "LONG" else "SELL"
            order_r = requests.post(
                f"{BRIDGE_URL}/order/dry-run",
                json={"action": action, "quantity": 1, "price": price}
            )
            assert order_r.status_code == 200
        
        # Step 3: News veto check (skip if Ollama not available)
        if ollama_available():
            news_r = requests.get(f"{BRIDGE_URL}/signal/news", timeout=10)
            assert news_r.status_code == 200


@pytest.mark.skipif(not bridge_available(), reason="Python bridge not available")
class TestErrorHandling:
    """Tests for error handling and edge cases"""
    
    def test_missing_required_field(self):
        """Should return 422 for missing required fields"""
        payload = {"action": "BUY", "quantity": 1}  # Missing price
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        
        assert r.status_code == 422
    
    def test_wrong_field_type(self):
        """Should return 422 for wrong field types"""
        payload = {"action": "BUY", "quantity": "one", "price": 20000}
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        
        assert r.status_code == 422
    
    def test_nonexistent_endpoint(self):
        """Should return 404 for nonexistent endpoints"""
        r = requests.get(f"{BRIDGE_URL}/nonexistent")
        
        assert r.status_code == 404


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
