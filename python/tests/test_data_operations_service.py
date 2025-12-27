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
        merged = service.fetch_historical_data('2330', days)
        assert len(merged) == 5
        # Dates should be unique and sorted
        dates = [bar['date'] for bar in merged]
        assert dates == sorted(dates)
        # All fields present
        for bar in merged:
            assert all(k in bar for k in ['date', 'open', 'high', 'low', 'close', 'volume'])
