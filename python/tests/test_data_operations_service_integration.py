import pytest
from app.services.data_operations_service import DataOperationsService
from datetime import datetime, timedelta

@pytest.mark.integration
def test_fetch_historical_data_real():
    service = DataOperationsService(db_config={"database": "test", "username": "test", "password": "test"})
    stock_code = "2330"
    days = 7
    # Expected: manually fetched data for 2330.TW for a week in November 2025
    import csv
    expected = []
    with open('/tmp/2330_202511.csv', newline='', encoding='utf-8') as csvfile:
        reader = csv.reader(csvfile)
        for row in reader:
            if len(row) < 9 or not row[0].startswith('114/'):
                continue
            date = datetime.strptime(row[0], '%Y/%m/%d').date()
            expected.append({
                'date': date,
                'open': float(row[3].replace(',', '')),
                'high': float(row[4].replace(',', '')),
                'low': float(row[5].replace(',', '')),
                'close': float(row[6].replace(',', '')),
                'volume': int(row[1].replace(',', ''))
            })
    start = datetime(2025, 11, 17)
    end = start + timedelta(days=days-1)
    result = service.fetch_historical_data(stock_code, days)
    # Only compare the week in question
    filtered = [bar for bar in result if start.date() <= bar["date"] <= end.date()]
    assert filtered == expected, f"Expected {expected}, got {filtered}"
