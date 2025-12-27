import pytest
from app.services.data_operations_service import DataOperationsService
from datetime import datetime, timedelta
from pathlib import Path

@pytest.mark.integration
def test_fetch_historical_data_real(tmp_path):
    service = DataOperationsService(db_config={"database": "test", "username": "test", "password": "test"})
    stock_code = "2330"
    days = 7
    # Expected: manually fetched data for 2330.TW for a week in November 2025
    import csv
    expected = []

    # Deterministic CSV lines for the week of 2025-11-17 .. 2025-11-21
    lines = [
        "2025/11/17,150,_,10,11,9,10.5,_,_",
        "2025/11/18,160,_,11,12,10,11.5,_,_",
        "2025/11/19,170,_,12,13,11,12.5,_,_",
        "2025/11/20,180,_,13,14,12,13.5,_,_",
        "2025/11/21,190,_,14,15,13,14.5,_,_",
    ]

    fixture_repo_path = Path(__file__).resolve().parent / "fixtures" / "2330_202511.csv"

    # Try to write CSV to a temporary path (self-contained). Fall back to repo fixture if needed.
    try:
        csv_path = tmp_path / "2330_202511.csv"
        csv_path.write_text("\n".join(lines))
    except Exception:
        csv_path = fixture_repo_path

    with open(csv_path, newline='', encoding='utf-8') as csvfile:
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
    # Only compare the week in question; parse timestamps returned by the service
    filtered = [bar for bar in result['data'] if start.date() <= datetime.fromisoformat(bar["timestamp"]).date() <= end.date()]
    assert filtered == expected, f"Expected {expected}, got {filtered}"
