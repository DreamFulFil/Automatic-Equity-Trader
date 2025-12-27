import pytest
from app.services.data_operations_service import DataOperationsService
from unittest.mock import patch
from datetime import datetime, timedelta

class DummyDBConfig(dict):
    def get(self, k, default=None):
        return super().get(k, default)

@pytest.fixture
def service():
    return DataOperationsService(db_config=DummyDBConfig(database='test', username='test', password='test'))

def test_fetch_historical_data_merges_sources(service):
    # Simulate partial data from TWSE, Shioaji, Yahoo
    today = datetime(2025, 12, 21)
    days = 5
    start = today - timedelta(days=days-1)
    twse_data = [
        {'date': start + timedelta(days=0), 'open': 1, 'high': 2, 'low': 0.5, 'close': 1.5, 'volume': 100},
        {'date': start + timedelta(days=1), 'open': 2, 'high': 3, 'low': 1.5, 'close': 2.5, 'volume': 200},
    ]
    shioaji_data = [
        {'date': start + timedelta(days=2), 'open': 3, 'high': 4, 'low': 2.5, 'close': 3.5, 'volume': 300},
    ]
    yahoo_data = [
        {'date': start + timedelta(days=3), 'open': 4, 'high': 5, 'low': 3.5, 'close': 4.5, 'volume': 400},
        {'date': start + timedelta(days=4), 'open': 5, 'high': 6, 'low': 4.5, 'close': 5.5, 'volume': 500},
    ]
    with patch.object(service, '_fetch_twse', return_value=twse_data), \
         patch.object(service, '_fetch_shioaji', return_value=shioaji_data), \
         patch.object(service, '_fetch_yahoo', return_value=yahoo_data):
        result = service.fetch_historical_data('2330', days)
        # Verify structured response object
        assert result['status'] == 'success'
        assert result['symbol'] == '2330.TW'
        # Source now shows primary source (shioaji has data so it's the primary)
        assert result['source'] == 'shioaji'
        # Verify source_breakdown shows contribution from each source
        assert 'source_breakdown' in result
        assert result['source_breakdown']['shioaji'] == 1
        assert result['source_breakdown']['yahoo'] == 2
        assert result['source_breakdown']['twse'] == 2
        assert result['count'] == 5
        assert len(result['data']) == 5
        # Verify data structure
        for bar in result['data']:
            assert all(k in bar for k in ['timestamp', 'open', 'high', 'low', 'close', 'volume'])


def test_fetch_historical_data_prefers_shioaji(service):
    # Ensure Shioaji data overrides other sources when present
    from datetime import datetime, timedelta
    today = datetime.now().date()
    fake_shioaji = [{'date': today, 'open': 10.0, 'high': 11.0, 'low': 9.0, 'close': 10.5, 'volume': 1000}]
    fake_yahoo = [{'date': today - timedelta(days=1), 'open': 20.0, 'high': 21.0, 'low': 19.0, 'close': 20.5, 'volume': 2000}]
    fake_twse = []

    with patch.object(service, '_fetch_shioaji', return_value=fake_shioaji), \
         patch.object(service, '_fetch_yahoo', return_value=fake_yahoo), \
         patch.object(service, '_fetch_twse', return_value=fake_twse):
        res = service.fetch_historical_data('2330', days=2, symbol='2330.TW')
        assert res['status'] == 'success'
        assert res['count'] == 2
        # Ensure shioaji-provided bar is present in merged data
        assert any(bar['close'] == 10.5 for bar in res['data'])


def test_source_stats_tracking(service):
    """Test that source statistics are properly tracked"""
    from app.services.data_operations_service import get_source_stats, reset_source_stats
    from datetime import datetime, timedelta
    
    # Reset stats before test
    reset_source_stats()
    
    today = datetime.now().date()
    fake_shioaji = [{'date': today, 'open': 10.0, 'high': 11.0, 'low': 9.0, 'close': 10.5, 'volume': 1000}]
    fake_yahoo = [{'date': today - timedelta(days=1), 'open': 20.0, 'high': 21.0, 'low': 19.0, 'close': 20.5, 'volume': 2000}]
    fake_twse = []

    with patch.object(service, '_fetch_shioaji', return_value=fake_shioaji), \
         patch.object(service, '_fetch_yahoo', return_value=fake_yahoo), \
         patch.object(service, '_fetch_twse', return_value=fake_twse):
        service.fetch_historical_data('2330', days=2, symbol='2330.TW')
    
    stats = get_source_stats()
    
    # Verify stats structure
    assert 'shioaji' in stats
    assert 'yahoo' in stats
    assert 'twse' in stats
    assert 'last_fetch' in stats
    assert 'total_fetches' in stats
    
    # Verify shioaji and yahoo had successes
    assert stats['shioaji']['success'] == 1
    assert stats['shioaji']['records'] == 1
    assert stats['yahoo']['success'] == 1
    assert stats['yahoo']['records'] == 1
    
    # Verify last_fetch tracking for the symbol
    assert '2330' in stats['last_fetch']
    assert stats['last_fetch']['2330']['primary_source'] == 'shioaji'
    assert stats['total_fetches'] == 1
