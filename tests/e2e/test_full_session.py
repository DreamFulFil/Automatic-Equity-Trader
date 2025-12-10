#!/usr/bin/env python3
"""
E2E tests for full trading session.

These tests simulate a complete trading day:
1. 11:15 - Bot starts (cron trigger)
2. 11:30 - Trading window opens
3. Signal check → Entry
4. 45-min exit OR 13:00 auto-flatten
5. Telegram daily summary

Run: BRIDGE_URL=http://localhost:8888 python/venv/bin/pytest tests/e2e/test_full_session.py -v -m e2e
"""

import pytest
import requests
import time
import json
import os
from datetime import datetime, timedelta

BRIDGE_URL = os.environ.get('BRIDGE_URL', 'http://localhost:8888')
JAVA_URL = os.environ.get('JAVA_URL', 'http://localhost:16350')


def wait_for_service(url, timeout=30):
    """Wait for service to be available"""
    start = time.time()
    while time.time() - start < timeout:
        try:
            r = requests.get(f"{url}/health", timeout=2)
            if r.status_code == 200:
                return True
        except:
            pass
        time.sleep(1)
    return False


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
        r = requests.get("http://localhost:11434/api/tags", timeout=2)
        return r.status_code == 200
    except:
        return False


@pytest.mark.e2e
@pytest.mark.skipif(not bridge_available(), reason="Bridge not available")
class TestFullTradingSession:
    """
    E2E test for complete trading session.
    
    Prerequisites:
    - Python bridge running on port 8888
    - Ollama running on port 11434 (for news tests)
    """
    
    def test_startup_sequence(self):
        """
        Test bot startup sequence:
        1. Health check
        2. Pre-market dry-run
        3. Contract scaling data fetch
        """
        # 1. Bridge health check
        r = requests.get(f"{BRIDGE_URL}/health")
        assert r.status_code == 200
        health = r.json()
        assert health["status"] == "ok"
        
        # 2. Pre-market health check (dry-run order)
        payload = {"action": "BUY", "quantity": 1, "price": 20000.0}
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        assert r.status_code == 200
        assert "validated" in r.text
        
        # 3. Contract scaling - get account info
        r = requests.get(f"{BRIDGE_URL}/account")
        assert r.status_code == 200
        account = r.json()
        assert "equity" in account
        assert "status" in account
    
    def test_signal_to_entry_flow(self):
        """
        Test signal evaluation to order execution:
        1. Get signal
        2. Check confidence threshold
        3. Validate order (dry-run)
        """
        # Get signal
        r = requests.get(f"{BRIDGE_URL}/signal")
        assert r.status_code == 200
        signal = r.json()
        
        # Verify signal structure
        assert "direction" in signal
        assert "confidence" in signal
        assert "current_price" in signal
        
        direction = signal.get("direction", "NEUTRAL")
        confidence = signal.get("confidence", 0)
        price = signal.get("current_price", 20000)
        
        # If actionable signal (>=0.65 confidence), validate order
        if direction != "NEUTRAL" and confidence >= 0.65:
            action = "BUY" if direction == "LONG" else "SELL"
            payload = {"action": action, "quantity": 1, "price": price}
            r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
            assert r.status_code == 200
            assert "validated" in r.text
    
    def test_signal_consistency(self):
        """
        Test that signal endpoint returns consistent structure
        across multiple calls (simulates 30-second polling)
        """
        for i in range(3):
            r = requests.get(f"{BRIDGE_URL}/signal")
            assert r.status_code == 200
            signal = r.json()
            
            # Required fields must always be present
            assert "direction" in signal
            assert signal["direction"] in ["LONG", "SHORT", "NEUTRAL"]
            assert "confidence" in signal
            assert 0 <= signal["confidence"] <= 1
            assert "exit_signal" in signal
            assert isinstance(signal["exit_signal"], bool)
            # Extended telemetry required for live trading diagnostics
            for key in (
                "momentum_3min",
                "momentum_5min",
                "momentum_10min",
                "volume_ratio",
                "rsi",
                "consecutive_signals",
                "in_cooldown",
                "cooldown_remaining",
                "session_high",
                "session_low",
                "raw_direction",
                "timestamp",
            ):
                assert key in signal, f"Missing field: {key}"
            
            # Small delay between calls
            time.sleep(0.1)
    
    @pytest.mark.skipif(not ollama_available(), reason="Ollama not available")
    def test_news_veto_check(self):
        """
        Test news veto integration with Ollama
        """
        r = requests.get(f"{BRIDGE_URL}/signal/news", timeout=15)
        assert r.status_code == 200
        news = r.json()
        
        assert "news_veto" in news
        assert isinstance(news["news_veto"], bool)
        assert "news_score" in news
        assert 0 <= news["news_score"] <= 1
        assert "news_reason" in news
        assert "headlines_count" in news
        assert news["headlines_count"] >= 0
    
    def test_contract_scaling_flow(self):
        """
        Test full contract scaling data retrieval:
        1. Get account equity
        2. Get 30-day profit history
        """
        # Get equity
        r = requests.get(f"{BRIDGE_URL}/account")
        assert r.status_code == 200
        account = r.json()
        assert "equity" in account
        
        # Get profit history
        r = requests.get(f"{BRIDGE_URL}/account/profit-history?days=30")
        assert r.status_code == 200
        profit = r.json()
        assert "total_pnl" in profit
        assert profit["days"] == 30


def java_available():
    """Check if Java server is available"""
    return wait_for_service(JAVA_URL, timeout=10)


@pytest.mark.e2e
@pytest.mark.skipif(not java_available(), reason="Java server not available")
class TestEarningsBlackout:
    """Test earnings blackout day behavior via admin endpoints"""

    def _status(self):
        r = requests.get(
            f"{JAVA_URL}/admin/earnings-blackout/status",
            timeout=5,
        )
        return r

    def test_blackout_status_endpoint_available(self):
        r = self._status()
        assert r.status_code == 200
        data = r.json()
        assert "stale" in data
        assert "datesCount" in data

    def test_blackout_seed_accepts_payload(self):
        payload = {
            "dates": ["2099-01-01"],
            "tickers_checked": ["TSM", "2317.TW"],
            "last_updated": "2099-01-01T00:00:00Z",
            "source": "pytest",
            "ttl_days": 7,
        }
        r = requests.post(
            f"{JAVA_URL}/admin/earnings-blackout/seed",
            json=payload,
            timeout=5,
        )
        assert r.status_code == 200
        data = r.json()
        assert data.get("status") in {"seeded", "ignored"}
        today = datetime.utcnow().date().isoformat()
        
        for date_str in data.get("dates", []):
            assert date_str >= today, f"Date {date_str} is in the past"


@pytest.mark.e2e
class TestWeeklyLossLimit:
    """Test weekly loss limit behavior"""
    
    def test_weekly_pnl_file_format(self):
        """Verify weekly P&L persistence format"""
        pnl_file = "logs/weekly-pnl.txt"
        
        if not os.path.exists(pnl_file):
            pytest.skip("Weekly P&L file not created yet")
        
        with open(pnl_file) as f:
            content = f.read().strip()
        
        # Format: YYYY-MM-DD,<pnl>
        parts = content.split(',')
        assert len(parts) == 2, f"Expected 'date,pnl' format: {content}"
        
        # Validate date part
        date_str = parts[0]
        assert len(date_str) == 10, f"Date should be YYYY-MM-DD: {date_str}"
        
        # Validate P&L is numeric
        pnl = float(parts[1])
        assert isinstance(pnl, float)
    
    def test_weekly_pnl_date_is_recent(self):
        """Weekly P&L date should be from current week"""
        pnl_file = "logs/weekly-pnl.txt"
        
        if not os.path.exists(pnl_file):
            pytest.skip("Weekly P&L file not created yet")
        
        with open(pnl_file) as f:
            content = f.read().strip()
        
        parts = content.split(',')
        date_str = parts[0]
        
        from datetime import date, timedelta
        saved_date = date.fromisoformat(date_str)
        today = date.today()
        
        # Should be within last 7 days
        week_ago = today - timedelta(days=7)
        assert saved_date >= week_ago, f"Date {date_str} is older than a week"


@pytest.mark.e2e
@pytest.mark.skipif(not bridge_available(), reason="Bridge not available")
class TestErrorRecovery:
    """Test error recovery and edge cases"""
    
    def test_invalid_order_rejected(self):
        """Invalid orders should return appropriate error codes"""
        # Invalid action
        payload = {"action": "INVALID", "quantity": 1, "price": 20000.0}
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        assert r.status_code == 400
        
        # Zero quantity
        payload = {"action": "BUY", "quantity": 0, "price": 20000.0}
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        assert r.status_code == 400
        
        # Negative price
        payload = {"action": "BUY", "quantity": 1, "price": -100.0}
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        assert r.status_code == 400
    
    def test_missing_fields_rejected(self):
        """Orders with missing fields should return 422"""
        # Missing price
        payload = {"action": "BUY", "quantity": 1}
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        assert r.status_code == 422
        
        # Missing quantity
        payload = {"action": "BUY", "price": 20000.0}
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        assert r.status_code == 422
    
    def test_malformed_json_rejected(self):
        """Malformed JSON should return 422"""
        r = requests.post(
            f"{BRIDGE_URL}/order/dry-run",
            data="not json",
            headers={"Content-Type": "application/json"}
        )
        assert r.status_code == 422
    
    def test_nonexistent_endpoint_404(self):
        """Non-existent endpoints should return 404"""
        r = requests.get(f"{BRIDGE_URL}/nonexistent")
        assert r.status_code == 404


@pytest.mark.e2e
@pytest.mark.skipif(not bridge_available(), reason="Bridge not available")
class TestPerformance:
    """Performance tests for endpoints"""
    
    def test_health_responds_quickly(self):
        """Health endpoint should respond within 500ms"""
        start = time.time()
        r = requests.get(f"{BRIDGE_URL}/health")
        elapsed = time.time() - start
        
        assert r.status_code == 200
        assert elapsed < 0.5, f"Health took {elapsed:.2f}s (should be <0.5s)"
    
    def test_signal_responds_quickly(self):
        """Signal endpoint should respond within 1s"""
        start = time.time()
        r = requests.get(f"{BRIDGE_URL}/signal")
        elapsed = time.time() - start
        
        assert r.status_code == 200
        assert elapsed < 1.0, f"Signal took {elapsed:.2f}s (should be <1s)"
    
    def test_order_dryrun_responds_quickly(self):
        """Order dry-run should respond within 500ms"""
        payload = {"action": "BUY", "quantity": 1, "price": 20000.0}
        
        start = time.time()
        r = requests.post(f"{BRIDGE_URL}/order/dry-run", json=payload)
        elapsed = time.time() - start
        
        assert r.status_code == 200
        assert elapsed < 0.5, f"Order dry-run took {elapsed:.2f}s (should be <0.5s)"


if __name__ == '__main__':
    pytest.main([__file__, '-v', '-m', 'e2e'])
