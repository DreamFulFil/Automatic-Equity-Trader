import pytest
from unittest.mock import patch, MagicMock
from datetime import datetime, timedelta
from collections import deque
from app.strategies import legacy_strategy


@pytest.fixture
def reset_strategy_state():
    """Reset global state before each test"""
    legacy_strategy.consecutive_signal_count = 0
    legacy_strategy.last_signal_direction = "NEUTRAL"
    legacy_strategy.last_trade_time = None
    legacy_strategy.signal_confirmation_history = deque(maxlen=6)
    legacy_strategy.last_direction = "NEUTRAL"
    yield
    # Reset after test as well
    legacy_strategy.consecutive_signal_count = 0
    legacy_strategy.last_signal_direction = "NEUTRAL"
    legacy_strategy.last_trade_time = None
    legacy_strategy.signal_confirmation_history = deque(maxlen=6)
    legacy_strategy.last_direction = "NEUTRAL"


def test_get_signal_legacy_insufficient_data(reset_strategy_state):
    """Test signal generation with insufficient price history"""
    with patch('app.strategies.legacy_strategy.latest_tick', {'price': 100.0}):
        with patch('app.strategies.legacy_strategy.price_history', []):
            with patch('app.strategies.legacy_strategy.volume_history', []):
                with patch('app.strategies.legacy_strategy.session_high', 105.0):
                    with patch('app.strategies.legacy_strategy.session_low', 95.0):
                        result = legacy_strategy.get_signal_legacy()
    
    assert result['direction'] == 'NEUTRAL'
    assert result['confidence'] == 0.0
    assert result['exit_signal'] is False
    assert 'Insufficient data' in result['reason']
    assert result['current_price'] == 100.0


def test_get_signal_legacy_with_warmup_data(reset_strategy_state):
    """Test signal generation with warmup period (less than 120 ticks)"""
    # Create 50 ticks of price data
    price_data = [100.0 + i * 0.1 for i in range(50)]
    volume_data = [1000] * 50
    
    with patch('app.strategies.legacy_strategy.latest_tick', {'price': 105.0}):
        with patch('app.strategies.legacy_strategy.price_history', price_data):
            with patch('app.strategies.legacy_strategy.volume_history', volume_data):
                with patch('app.strategies.legacy_strategy.session_high', 105.0):
                    with patch('app.strategies.legacy_strategy.session_low', 95.0):
                        result = legacy_strategy.get_signal_legacy()
    
    # Should still be in warmup
    assert result['direction'] == 'NEUTRAL'
    assert 'Insufficient data' in result['reason']


def test_get_signal_legacy_basic_structure(reset_strategy_state):
    """Test that signal has all required fields"""
    # Create sufficient price history (120+ ticks) - price_history expects dicts with 'price' key
    price_data = [{"price": 100.0} for _ in range(150)]
    volume_data = [1000] * 150
    
    with patch('app.strategies.legacy_strategy.latest_tick', {'price': 100.0}):
        with patch('app.strategies.legacy_strategy.price_history', price_data):
            with patch('app.strategies.legacy_strategy.volume_history', volume_data):
                with patch('app.strategies.legacy_strategy.session_high', 105.0):
                    with patch('app.strategies.legacy_strategy.session_low', 95.0):
                        result = legacy_strategy.get_signal_legacy()
    
    # Check all expected fields are present
    assert 'current_price' in result
    assert 'direction' in result
    assert 'confidence' in result
    assert 'exit_signal' in result
    assert 'momentum_3min' in result
    assert 'momentum_5min' in result
    assert 'momentum_10min' in result
    assert 'volume_ratio' in result
    assert 'rsi' in result
    assert 'consecutive_signals' in result
    assert 'timestamp' in result


def test_get_signal_legacy_uptrend_momentum(reset_strategy_state):
    """Test signal generation with uptrend momentum"""
    # Create upward trending price data
    base_price = 100.0
    price_data = [{"price": base_price + (i * 0.02)} for i in range(150)]  # Gradual uptrend
    volume_data = [1000 + (i * 10) for i in range(150)]  # Increasing volume
    
    with patch('app.strategies.legacy_strategy.latest_tick', {'price': price_data[-1]["price"]}):
        with patch('app.strategies.legacy_strategy.price_history', price_data):
            with patch('app.strategies.legacy_strategy.volume_history', volume_data):
                with patch('app.strategies.legacy_strategy.session_high', max(p["price"] for p in price_data)):
                    with patch('app.strategies.legacy_strategy.session_low', min(p["price"] for p in price_data)):
                        result = legacy_strategy.get_signal_legacy()
    
    # Should detect upward momentum (though may require consecutive confirmation)
    assert 'momentum_3min' in result
    assert isinstance(result['momentum_3min'], (int, float))


def test_get_signal_legacy_downtrend_momentum(reset_strategy_state):
    """Test signal generation with downtrend momentum"""
    # Create downward trending price data
    base_price = 103.0
    price_data = [{"price": base_price - (i * 0.02)} for i in range(150)]  # Gradual downtrend
    volume_data = [1000 + (i * 10) for i in range(150)]  # Increasing volume
    
    with patch('app.strategies.legacy_strategy.latest_tick', {'price': price_data[-1]["price"]}):
        with patch('app.strategies.legacy_strategy.price_history', price_data):
            with patch('app.strategies.legacy_strategy.volume_history', volume_data):
                with patch('app.strategies.legacy_strategy.session_high', max(p["price"] for p in price_data)):
                    with patch('app.strategies.legacy_strategy.session_low', min(p["price"] for p in price_data)):
                        result = legacy_strategy.get_signal_legacy()
    
    # Should detect downward momentum
    assert 'momentum_3min' in result
    assert isinstance(result['momentum_3min'], (int, float))


def test_get_signal_legacy_neutral_market(reset_strategy_state):
    """Test signal generation in neutral/sideways market"""
    # Create sideways price movement
    price_data = [{"price": 100.0 + (0.1 if i % 2 == 0 else -0.1)} for i in range(150)]
    volume_data = [1000] * 150
    
    with patch('app.strategies.legacy_strategy.latest_tick', {'price': 100.0}):
        with patch('app.strategies.legacy_strategy.price_history', price_data):
            with patch('app.strategies.legacy_strategy.volume_history', volume_data):
                with patch('app.strategies.legacy_strategy.session_high', 100.5):
                    with patch('app.strategies.legacy_strategy.session_low', 99.5):
                        result = legacy_strategy.get_signal_legacy()
    
    # In sideways market, strategy should likely stay neutral
    assert result['direction'] in ['LONG', 'SHORT', 'NEUTRAL']
    assert 0.0 <= result['confidence'] <= 1.0


def test_get_signal_legacy_cooldown_after_trade(reset_strategy_state):
    """Test cooldown period after a trade"""
    legacy_strategy.last_trade_time = datetime.now() - timedelta(seconds=60)  # 1 minute ago
    
    price_data = [{"price": 100.0} for _ in range(150)]
    volume_data = [1000] * 150
    
    with patch('app.strategies.legacy_strategy.latest_tick', {'price': 100.0}):
        with patch('app.strategies.legacy_strategy.price_history', price_data):
            with patch('app.strategies.legacy_strategy.volume_history', volume_data):
                with patch('app.strategies.legacy_strategy.session_high', 105.0):
                    with patch('app.strategies.legacy_strategy.session_low', 95.0):
                        result = legacy_strategy.get_signal_legacy()
    
    # Should show cooldown info
    assert 'in_cooldown' in result
    assert 'cooldown_remaining' in result


def test_get_signal_legacy_consecutive_signals_tracking(reset_strategy_state):
    """Test consecutive signal tracking"""
    legacy_strategy.consecutive_signal_count = 2
    legacy_strategy.last_signal_direction = "LONG"
    
    price_data = [{"price": 100.0} for _ in range(150)]
    volume_data = [1000] * 150
    
    with patch('app.strategies.legacy_strategy.latest_tick', {'price': 100.0}):
        with patch('app.strategies.legacy_strategy.price_history', price_data):
            with patch('app.strategies.legacy_strategy.volume_history', volume_data):
                with patch('app.strategies.legacy_strategy.session_high', 105.0):
                    with patch('app.strategies.legacy_strategy.session_low', 95.0):
                        result = legacy_strategy.get_signal_legacy()
    
    # Should track consecutive signals
    assert 'consecutive_signals' in result
    assert isinstance(result['consecutive_signals'], int)


def test_get_signal_legacy_volume_ratio_calculation(reset_strategy_state):
    """Test volume ratio calculation"""
    price_data = [{"price": 100.0} for _ in range(150)]
    # Recent higher volume, older lower volume
    volume_data = [500] * 120 + [2000] * 30
    
    with patch('app.strategies.legacy_strategy.latest_tick', {'price': 100.0}):
        with patch('app.strategies.legacy_strategy.price_history', price_data):
            with patch('app.strategies.legacy_strategy.volume_history', volume_data):
                with patch('app.strategies.legacy_strategy.session_high', 105.0):
                    with patch('app.strategies.legacy_strategy.session_low', 95.0):
                        result = legacy_strategy.get_signal_legacy()
    
    # Should have volume ratio calculated
    assert 'volume_ratio' in result
    assert isinstance(result['volume_ratio'], (int, float))
    assert result['volume_ratio'] > 0


def test_get_signal_legacy_rsi_calculation(reset_strategy_state):
    """Test RSI calculation and bounds"""
    price_data = [{"price": 100.0 + i * 0.05} for i in range(150)]
    volume_data = [1000] * 150
    
    with patch('app.strategies.legacy_strategy.latest_tick', {'price': price_data[-1]["price"]}):
        with patch('app.strategies.legacy_strategy.price_history', price_data):
            with patch('app.strategies.legacy_strategy.volume_history', volume_data):
                with patch('app.strategies.legacy_strategy.session_high', max(p["price"] for p in price_data)):
                    with patch('app.strategies.legacy_strategy.session_low', min(p["price"] for p in price_data)):
                        result = legacy_strategy.get_signal_legacy()
    
    # RSI should be between 0 and 100
    assert 'rsi' in result
    assert 0 <= result['rsi'] <= 100


def test_get_signal_legacy_session_high_low_tracking(reset_strategy_state):
    """Test session high/low tracking"""
    price_data = [{"price": 100.0 + (i * 0.01)} for i in range(150)]
    volume_data = [1000] * 150
    
    session_high = max(p["price"] for p in price_data) + 1.0
    session_low = min(p["price"] for p in price_data) - 1.0
    
    with patch('app.strategies.legacy_strategy.latest_tick', {'price': price_data[-1]["price"]}):
        with patch('app.strategies.legacy_strategy.price_history', price_data):
            with patch('app.strategies.legacy_strategy.volume_history', volume_data):
                with patch('app.strategies.legacy_strategy.session_high', session_high):
                    with patch('app.strategies.legacy_strategy.session_low', session_low):
                        result = legacy_strategy.get_signal_legacy()
    
    # Should include session bounds
    assert 'session_high' in result
    assert 'session_low' in result
    assert result['session_high'] >= result['current_price']
    assert result['session_low'] <= result['current_price']
