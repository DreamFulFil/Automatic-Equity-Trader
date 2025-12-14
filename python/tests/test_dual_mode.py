#!/usr/bin/env python3
"""
Unit tests for dual-mode (stock/futures) functionality in bridge.py

Run with: pytest python/tests/test_dual_mode.py -v
"""

import pytest
from unittest.mock import Mock, patch, MagicMock
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


class TestShioajiWrapperDualMode:
    """Tests for ShioajiWrapper dual-mode functionality"""
    
    def test_wrapper_initializes_with_stock_mode(self):
        """Should initialize in stock mode when specified"""
        from app.services.shioaji_service import ShioajiWrapper
        
        config = {
            'shioaji': {
                'api-key': 'test',
                'secret-key': 'test',
                'ca-path': '/test/path',
                'ca-password': 'test',
                'person-id': 'test',
                'simulation': True
            }
        }
        
        wrapper = ShioajiWrapper(config, trading_mode="stock")
        
        assert wrapper.trading_mode == "stock"
        assert wrapper.connected == False
    
    def test_wrapper_initializes_with_futures_mode(self):
        """Should initialize in futures mode when specified"""
        from app.services.shioaji_service import ShioajiWrapper
        
        config = {
            'shioaji': {
                'api-key': 'test',
                'secret-key': 'test',
                'ca-path': '/test/path',
                'ca-password': 'test',
                'person-id': 'test',
                'simulation': True
            }
        }
        
        wrapper = ShioajiWrapper(config, trading_mode="futures")
        
        assert wrapper.trading_mode == "futures"
    
    def test_wrapper_defaults_to_stock_mode(self):
        """Should default to stock mode when not specified"""
        from app.services.shioaji_service import ShioajiWrapper
        
        config = {
            'shioaji': {
                'api-key': 'test',
                'secret-key': 'test',
                'ca-path': '/test/path',
                'ca-password': 'test',
                'person-id': 'test',
                'simulation': True
            }
        }
        
        wrapper = ShioajiWrapper(config)
        
        assert wrapper.trading_mode == "stock"


class TestTradingModeEnvironment:
    """Tests for TRADING_MODE environment variable handling"""
    
    @patch.dict(os.environ, {'JASYPT_PASSWORD': 'test', 'TRADING_MODE': 'stock'})
    @patch('app.main.load_config_with_decryption')
    @patch('app.main.ShioajiWrapper')
    def test_init_reads_stock_mode_from_env(self, mock_wrapper_class, mock_load_config):
        """Should read stock mode from TRADING_MODE env var"""
        from app.main import init_trading_mode
        
        mock_config = {
            'shioaji': {'simulation': True},
            'ollama': {'url': 'http://localhost:11434', 'model': 'test'}
        }
        mock_load_config.return_value = mock_config
        
        mock_wrapper = Mock()
        mock_wrapper.connect.return_value = True
        mock_wrapper_class.return_value = mock_wrapper
        
        init_trading_mode()
        
        # Verify ShioajiWrapper was created with stock mode
        mock_wrapper_class.assert_called_once()
        call_kwargs = mock_wrapper_class.call_args[1]
        assert call_kwargs['trading_mode'] == 'stock'
    
    @patch.dict(os.environ, {'JASYPT_PASSWORD': 'test', 'TRADING_MODE': 'futures'})
    @patch('app.main.load_config_with_decryption')
    @patch('app.main.ShioajiWrapper')
    def test_init_reads_futures_mode_from_env(self, mock_wrapper_class, mock_load_config):
        """Should read futures mode from TRADING_MODE env var"""
        from app.main import init_trading_mode
        
        mock_config = {
            'shioaji': {'simulation': True},
            'ollama': {'url': 'http://localhost:11434', 'model': 'test'}
        }
        mock_load_config.return_value = mock_config
        
        mock_wrapper = Mock()
        mock_wrapper.connect.return_value = True
        mock_wrapper_class.return_value = mock_wrapper
        
        init_trading_mode()
        
        # Verify ShioajiWrapper was created with futures mode
        mock_wrapper_class.assert_called_once()
        call_kwargs = mock_wrapper_class.call_args[1]
        assert call_kwargs['trading_mode'] == 'futures'
    
    @patch.dict(os.environ, {'JASYPT_PASSWORD': 'test'}, clear=False)
    @patch('app.main.load_config_with_decryption')
    @patch('app.main.ShioajiWrapper')
    def test_init_defaults_to_stock_mode(self, mock_wrapper_class, mock_load_config):
        """Should default to stock mode when TRADING_MODE not set"""
        # Remove TRADING_MODE if present
        os.environ.pop('TRADING_MODE', None)
        
        from app.main import init_trading_mode
        
        mock_config = {
            'shioaji': {'simulation': True},
            'ollama': {'url': 'http://localhost:11434', 'model': 'test'}
        }
        mock_load_config.return_value = mock_config
        
        mock_wrapper = Mock()
        mock_wrapper.connect.return_value = True
        mock_wrapper_class.return_value = mock_wrapper
        
        init_trading_mode()
        
        # Verify ShioajiWrapper was created with stock mode (default)
        mock_wrapper_class.assert_called_once()
        call_kwargs = mock_wrapper_class.call_args[1]
        assert call_kwargs['trading_mode'] == 'stock'


class TestHealthEndpointWithMode:
    """Tests for health endpoint showing trading mode"""
    
    @patch('app.main.shioaji')
    @patch('app.main.TRADING_MODE', 'stock')
    def test_health_returns_stock_mode(self, mock_shioaji):
        """Health endpoint should return trading mode"""
        from app.main import health
        
        mock_shioaji.connected = True
        
        result = health()
        
        assert result['trading_mode'] == 'stock'
        assert result['status'] == 'ok'
    
    @patch('app.main.shioaji')
    @patch('app.main.TRADING_MODE', 'futures')
    def test_health_returns_futures_mode(self, mock_shioaji):
        """Health endpoint should return futures trading mode"""
        from app.main import health
        
        mock_shioaji.connected = True
        
        result = health()
        
        assert result['trading_mode'] == 'futures'


class TestModeSeparation:
    """Tests to verify strict mode separation (no crossover)"""
    
    def test_stock_order_uses_stock_account(self):
        """Stock mode should use stock_account, not futopt_account"""
        from app.services.shioaji_service import ShioajiWrapper
        
        config = {
            'shioaji': {
                'api-key': 'test',
                'secret-key': 'test',
                'ca-path': '/test/path',
                'ca-password': 'test',
                'person-id': 'test',
                'simulation': False
            }
        }
        
        wrapper = ShioajiWrapper(config, trading_mode="stock")
        wrapper.api = Mock()
        wrapper.api.stock_account = Mock()
        wrapper.api.futopt_account = Mock()
        wrapper.connected = True
        wrapper.contract = Mock()
        
        # Mock the Order and place_order
        wrapper.api.Order = Mock(return_value=Mock())
        mock_trade = Mock()
        mock_trade.status.id = "test_order_id"
        # Configure operation to avoid "Mock is not iterable" error
        mock_trade.operation.op_msg = ""
        # Configure status.order_quantity to avoid "Order not filled" error
        mock_trade.status.order_quantity = 100
        wrapper.api.place_order = Mock(return_value=mock_trade)
    
        result = wrapper.place_order("BUY", 100, 700.0)
        
        # Verify stock_account was used
        order_call = wrapper.api.Order.call_args
        assert order_call[1]['account'] == wrapper.api.stock_account
        assert result['mode'] == 'stock'
    
    def test_futures_order_uses_futopt_account(self):
        """Futures mode should use futopt_account, not stock_account"""
        from app.services.shioaji_service import ShioajiWrapper
        
        config = {
            'shioaji': {
                'api-key': 'test',
                'secret-key': 'test',
                'ca-path': '/test/path',
                'ca-password': 'test',
                'person-id': 'test',
                'simulation': False
            }
        }
        
        wrapper = ShioajiWrapper(config, trading_mode="futures")
        wrapper.api = Mock()
        wrapper.api.stock_account = Mock()
        wrapper.api.futopt_account = Mock()
        wrapper.connected = True
        wrapper.contract = Mock()
        
        # Mock the Order and place_order
        wrapper.api.Order = Mock(return_value=Mock())
        mock_trade = Mock()
        mock_trade.status.id = "test_order_id"
        # Configure operation to avoid "Mock is not iterable" error
        mock_trade.operation.op_msg = ""
        # Configure status.order_quantity to avoid "Order not filled" error
        mock_trade.status.order_quantity = 1
        wrapper.api.place_order = Mock(return_value=mock_trade)
    
        result = wrapper.place_order("BUY", 1, 22500.0)
        
        # Verify futopt_account was used
        order_call = wrapper.api.Order.call_args
        assert order_call[1]['account'] == wrapper.api.futopt_account
        assert result['mode'] == 'futures'


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
