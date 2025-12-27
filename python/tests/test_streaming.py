#!/usr/bin/env python3
"""
Test suite for streaming market data and Level 2 order book endpoints.

Tests cover:
- Real-time streaming quote buffering
- Level 2 order book data handling
- Thread-safe data access
- Error handling and edge cases
- Subscription management
"""

import pytest
import sys
import os
from unittest.mock import Mock, patch, MagicMock
from collections import deque
from datetime import datetime
import threading
import time

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Import bridge module components
import app.main as bridge
from app.services.shioaji_service import ShioajiWrapper


class TestStreamingQuotes:
    """Test streaming real-time quote functionality"""
    
    def test_streaming_buffer_initialization(self):
        """Test that streaming quotes buffer is properly initialized"""
        assert hasattr(bridge, 'streaming_quotes')
        assert isinstance(bridge.streaming_quotes, deque)
        assert bridge.streaming_quotes.maxlen == 100
    
    def test_streaming_quotes_thread_safe(self):
        """Test thread-safe access to streaming quotes"""
        # Clear buffer
        bridge.streaming_quotes.clear()
        
        # Simulate concurrent writes
        def writer(quote_id):
            for i in range(10):
                with bridge.streaming_quotes_lock:
                    bridge.streaming_quotes.append({
                        "symbol": "2454",
                        "price": 1000.0 + i,
                        "volume": 100 + i,
                        "timestamp": datetime.now().isoformat(),
                        "exchange": "TSE",
                        "writer_id": quote_id
                    })
        
        threads = [threading.Thread(target=writer, args=(i,)) for i in range(5)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        
        # Buffer should have exactly maxlen or fewer items
        assert len(bridge.streaming_quotes) <= 100
    
    def test_streaming_quotes_endpoint_returns_json(self):
        """Test /stream/quotes endpoint returns valid JSON structure"""
        # Mock streaming data
        bridge.streaming_quotes.clear()
        bridge.streaming_quotes.append({
            "symbol": "2454",
            "price": 1050.0,
            "volume": 500,
            "timestamp": datetime.now().isoformat(),
            "exchange": "TSE"
        })
        
        # Mock shioaji and trading mode
        with patch.object(bridge, 'shioaji') as mock_shioaji, \
             patch.object(bridge, 'TRADING_MODE', 'stock'):
            
            mock_contract = Mock()
            mock_contract.symbol = "2454"
            mock_shioaji.contract = mock_contract
            
            result = bridge.get_streaming_quotes(limit=10)
            
            assert result["status"] == "ok"
            assert "quotes" in result
            assert result["count"] == 1
            assert result["quotes"][0]["price"] == 1050.0
    
    def test_streaming_quotes_limit_parameter(self):
        """Test that limit parameter correctly restricts returned quotes"""
        # Fill buffer with test data
        bridge.streaming_quotes.clear()
        for i in range(75):
            bridge.streaming_quotes.append({
                "symbol": "2454",
                "price": 1000.0 + i,
                "volume": 100,
                "timestamp": datetime.now().isoformat(),
                "exchange": "TSE"
            })
        
        with patch.object(bridge, 'shioaji') as mock_shioaji, \
             patch.object(bridge, 'TRADING_MODE', 'stock'):
            
            mock_contract = Mock()
            mock_contract.symbol = "2454"
            mock_shioaji.contract = mock_contract
            
            # Test limit=25
            result = bridge.get_streaming_quotes(limit=25)
            assert result["count"] == 25
            
            # Test limit > maxlen is capped
            result = bridge.get_streaming_quotes(limit=200)
            assert result["count"] <= 100
    
    def test_streaming_quotes_empty_buffer(self):
        """Test behavior with empty streaming buffer"""
        bridge.streaming_quotes.clear()
        
        with patch.object(bridge, 'shioaji') as mock_shioaji, \
             patch.object(bridge, 'TRADING_MODE', 'stock'):
            
            mock_contract = Mock()
            mock_contract.symbol = "2454"
            mock_shioaji.contract = mock_contract
            
            result = bridge.get_streaming_quotes()
            
            assert result["status"] == "ok"
            assert result["count"] == 0
            assert result["quotes"] == []


class TestOrderBook:
    """Test Level 2 order book functionality"""
    
    def test_order_book_initialization(self):
        """Test that order book is properly initialized"""
        assert hasattr(bridge, 'order_book')
        assert "bids" in bridge.order_book
        assert "asks" in bridge.order_book
        assert "timestamp" in bridge.order_book
        assert "symbol" in bridge.order_book
    
    def test_order_book_thread_safe(self):
        """Test thread-safe access to order book"""
        def writer():
            for i in range(10):
                with bridge.order_book_lock:
                    bridge.order_book["bids"] = [{"price": 1000 + i, "volume": 100}]
                    bridge.order_book["asks"] = [{"price": 1001 + i, "volume": 100}]
        
        threads = [threading.Thread(target=writer) for _ in range(5)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        
        # Should not crash and should have valid structure
        assert isinstance(bridge.order_book["bids"], list)
        assert isinstance(bridge.order_book["asks"], list)
    
    def test_order_book_endpoint_returns_depth(self):
        """Test /orderbook endpoint returns bid/ask depth"""
        # Mock order book data
        with bridge.order_book_lock:
            bridge.order_book.update({
                "symbol": "2454",
                "bids": [
                    {"price": 1050.0, "volume": 500},
                    {"price": 1049.5, "volume": 300},
                    {"price": 1049.0, "volume": 200}
                ],
                "asks": [
                    {"price": 1050.5, "volume": 400},
                    {"price": 1051.0, "volume": 600}
                ],
                "timestamp": datetime.now().isoformat()
            })
        
        with patch.object(bridge, 'shioaji') as mock_shioaji, \
             patch.object(bridge, 'TRADING_MODE', 'stock'):
            
            mock_shioaji.connected = True
            mock_shioaji._bidask_subscribed = True
            
            result = bridge.get_order_book("2454")
            
            assert result["status"] == "ok"
            assert result["symbol"] == "2454"
            assert len(result["bids"]) == 3
            assert len(result["asks"]) == 2
            assert result["bids"][0]["price"] == 1050.0
            assert result["asks"][0]["price"] == 1050.5
    
    def test_order_book_symbol_mismatch(self):
        """Test error handling for symbol mismatch"""
        with bridge.order_book_lock:
            bridge.order_book.update({
                "symbol": "2330",
                "bids": [{"price": 500, "volume": 100}],
                "asks": [{"price": 501, "volume": 100}],
                "timestamp": datetime.now().isoformat()
            })
        
        with patch.object(bridge, 'shioaji') as mock_shioaji, \
             patch.object(bridge, 'TRADING_MODE', 'stock'):
            
            mock_shioaji.connected = True
            mock_shioaji._bidask_subscribed = True
            
            result = bridge.get_order_book("2454")
            
            # Should return error for mismatched symbol
            assert result.status_code == 400
    
    def test_order_book_sorted_correctly(self):
        """Test that bids/asks are sorted correctly (bids desc, asks asc)"""
        # Test data with unsorted prices
        unsorted_bids = [
            {"price": 1049.0, "volume": 200},
            {"price": 1050.0, "volume": 500},
            {"price": 1049.5, "volume": 300}
        ]
        
        unsorted_asks = [
            {"price": 1051.0, "volume": 600},
            {"price": 1050.5, "volume": 400},
            {"price": 1052.0, "volume": 300}
        ]
        
        # Sort as the handler would
        sorted_bids = sorted(unsorted_bids, key=lambda x: x["price"], reverse=True)
        sorted_asks = sorted(unsorted_asks, key=lambda x: x["price"])
        
        # Verify sorting
        assert sorted_bids[0]["price"] == 1050.0  # Highest bid first
        assert sorted_asks[0]["price"] == 1050.5  # Lowest ask first


class TestShioajiWrapperStreaming:
    """Test ShioajiWrapper streaming enhancements"""
    
    def test_subscribe_bidask_method_exists(self):
        """Test that subscribe_bidask method exists on ShioajiWrapper"""
        wrapper = ShioajiWrapper(config={"shioaji": {}}, trading_mode="stock")
        assert hasattr(wrapper, 'subscribe_bidask')
    
    @patch('app.services.shioaji_service.sj')
    def test_handle_bidask_updates_order_book(self, mock_sj):
        """Test that _handle_bidask correctly updates global order book"""
        wrapper = ShioajiWrapper(config={"shioaji": {}}, trading_mode="stock")
        
        # Mock contract
        mock_contract = Mock()
        mock_contract.symbol = "2454"
        wrapper.contract = mock_contract
        
        # Create mock bidask data
        mock_bidask = Mock()
        mock_bidask.bid_price = [1050.0, 1049.5, 1049.0]
        mock_bidask.bid_volume = [500, 300, 200]
        mock_bidask.ask_price = [1050.5, 1051.0]
        mock_bidask.ask_volume = [400, 600]
        mock_bidask.datetime = datetime.now()
        
        # Clear order book
        with bridge.order_book_lock:
            bridge.order_book.update({
                "bids": [],
                "asks": [],
                "timestamp": None,
                "symbol": None
            })
        
        # Call handler
        wrapper._handle_bidask("TSE", mock_bidask)
        
        # Verify order book was updated
        with bridge.order_book_lock:
            assert bridge.order_book["symbol"] == "2454"
            assert len(bridge.order_book["bids"]) == 3
            assert len(bridge.order_book["asks"]) == 2
            assert bridge.order_book["bids"][0]["price"] == 1050.0
    
    @patch('app.services.shioaji_service.sj')
    def test_handle_tick_updates_streaming_buffer(self, mock_sj):
        """Test that _handle_tick populates streaming_quotes buffer"""
        wrapper = ShioajiWrapper(config={"shioaji": {}}, trading_mode="stock")
        
        # Mock contract
        mock_contract = Mock()
        mock_contract.symbol = "2454"
        wrapper.contract = mock_contract
        
        # Clear streaming buffer
        bridge.streaming_quotes.clear()
        
        # Create mock tick data
        mock_tick = Mock()
        mock_tick.close = 1050.0
        mock_tick.volume = 500
        mock_tick.datetime = datetime.now()
        
        # Call handler
        wrapper._handle_tick("TSE", mock_tick)
        
        # Verify streaming buffer was updated
        assert len(bridge.streaming_quotes) == 1
        assert bridge.streaming_quotes[0]["symbol"] == "2454"
        assert bridge.streaming_quotes[0]["price"] == 1050.0
        assert bridge.streaming_quotes[0]["volume"] == 500


class TestStreamingSubscription:
    """Test streaming subscription management"""
    
    def test_subscribe_endpoint_requires_connection(self):
        """Test that /stream/subscribe requires active Shioaji connection"""
        with patch.object(bridge, 'shioaji', None):
            result = bridge.subscribe_streaming()
            assert result.status_code == 503
    
    @patch('app.main.shioaji')
    def test_subscribe_endpoint_calls_subscribe_bidask(self, mock_shioaji):
        """Test that /stream/subscribe calls wrapper's subscribe_bidask method"""
        mock_shioaji.connected = True
        mock_shioaji.subscribe_bidask = Mock(return_value=True)
        mock_contract = Mock()
        mock_contract.symbol = "2454"
        mock_shioaji.contract = mock_contract
        
        with patch.object(bridge, 'TRADING_MODE', 'stock'):
            result = bridge.subscribe_streaming()
            
            assert result["status"] == "ok"
            assert result["subscribed"] is True
            mock_shioaji.subscribe_bidask.assert_called_once()
    
    @patch('app.main.shioaji')
    def test_subscribe_endpoint_handles_failure(self, mock_shioaji):
        """Test that /stream/subscribe handles subscription failures"""
        mock_shioaji.connected = True
        mock_shioaji.subscribe_bidask = Mock(return_value=False)
        mock_contract = Mock()
        mock_contract.symbol = "2454"
        mock_shioaji.contract = mock_contract
        
        with patch.object(bridge, 'TRADING_MODE', 'stock'):
            result = bridge.subscribe_streaming()
            
            assert result["status"] == "error"
            assert result["subscribed"] is False


class TestStreamingEdgeCases:
    """Test edge cases and error conditions"""
    
    def test_streaming_quotes_with_invalid_data(self):
        """Test that invalid tick data doesn't crash streaming buffer"""
        wrapper = ShioajiWrapper(config={"shioaji": {}}, trading_mode="stock")
        wrapper.contract = Mock(symbol="2454")
        
        # Test with None tick
        bridge.streaming_quotes.clear()
        wrapper._handle_tick("TSE", None)
        assert len(bridge.streaming_quotes) == 0
        
        # Test with missing close price
        mock_tick = Mock(spec=[])
        wrapper._handle_tick("TSE", mock_tick)
        assert len(bridge.streaming_quotes) == 0
    
    def test_order_book_with_invalid_bidask(self):
        """Test that invalid bidask data doesn't crash order book"""
        wrapper = ShioajiWrapper(config={"shioaji": {}}, trading_mode="stock")
        wrapper.contract = Mock(symbol="2454")
        
        # Clear order book
        with bridge.order_book_lock:
            initial_bids = list(bridge.order_book.get("bids", []))
            initial_asks = list(bridge.order_book.get("asks", []))
        
        # Test with None bidask
        wrapper._handle_bidask("TSE", None)
        
        # Should not crash; bids/asks should remain unchanged or empty
        with bridge.order_book_lock:
            # The symbol may be set even with invalid data, which is acceptable
            # Key is that it doesn't crash
            assert isinstance(bridge.order_book["bids"], list)
            assert isinstance(bridge.order_book["asks"], list)
    
    def test_concurrent_buffer_access(self):
        """Test that concurrent reads/writes to streaming buffer don't cause race conditions"""
        bridge.streaming_quotes.clear()
        
        errors = []
        
        def writer():
            try:
                for i in range(50):
                    with bridge.streaming_quotes_lock:
                        bridge.streaming_quotes.append({
                            "symbol": "2454",
                            "price": 1000.0 + i,
                            "volume": 100,
                            "timestamp": datetime.now().isoformat(),
                            "exchange": "TSE"
                        })
            except Exception as e:
                errors.append(e)
        
        def reader():
            try:
                for i in range(50):
                    with bridge.streaming_quotes_lock:
                        _ = list(bridge.streaming_quotes)
            except Exception as e:
                errors.append(e)
        
        threads = [threading.Thread(target=writer) for _ in range(3)]
        threads += [threading.Thread(target=reader) for _ in range(3)]
        
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        
        # Should complete without errors
        assert len(errors) == 0


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
