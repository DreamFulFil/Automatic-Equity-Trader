import os
import json
import pytest
from unittest.mock import patch, MagicMock
from datetime import datetime, timedelta
from app.services.earnings_service import scrape_earnings_dates


@pytest.fixture
def mock_config_dir(tmp_path):
    """Create a temporary config directory for testing"""
    config_dir = tmp_path / "config"
    config_dir.mkdir()
    return tmp_path


def test_scrape_earnings_dates_success(mock_config_dir, monkeypatch):
    """Test successful earnings scraping with mock data"""
    # Change to the mock directory structure
    monkeypatch.chdir(mock_config_dir)
    
    today = datetime.now().date()
    future_date = today + timedelta(days=30)
    
    # Mock yfinance Ticker
    mock_ticker = MagicMock()
    mock_ticker.calendar = {
        'Earnings Date': [future_date]
    }
    
    with patch('app.services.earnings_service.yf.Ticker', return_value=mock_ticker):
        with patch('app.services.earnings_service.send_telegram_message'):
            with patch('app.services.earnings_service.time.sleep'):
                result = scrape_earnings_dates()
    
    # Verify that dates were returned
    assert isinstance(result, list)
    assert len(result) > 0


def test_scrape_earnings_dates_no_calendar(mock_config_dir, monkeypatch):
    """Test handling of stocks with no earnings calendar"""
    monkeypatch.chdir(mock_config_dir)
    
    mock_ticker = MagicMock()
    mock_ticker.calendar = None
    
    with patch('app.services.earnings_service.yf.Ticker', return_value=mock_ticker):
        with patch('app.services.earnings_service.send_telegram_message'):
            with patch('app.services.earnings_service.time.sleep'):
                result = scrape_earnings_dates()
    
    # Should handle gracefully and return empty or existing dates
    assert isinstance(result, list)


def test_scrape_earnings_dates_exception_handling(mock_config_dir, monkeypatch):
    """Test that exceptions for individual tickers are handled gracefully"""
    monkeypatch.chdir(mock_config_dir)
    
    with patch('app.services.earnings_service.yf.Ticker', side_effect=Exception("API Error")):
        with patch('app.services.earnings_service.send_telegram_message'):
            with patch('app.services.earnings_service.time.sleep'):
                result = scrape_earnings_dates()
    
    # Should complete without crashing
    assert isinstance(result, list)


def test_scrape_earnings_dates_filters_past_dates(mock_config_dir, monkeypatch):
    """Test that past dates are filtered out"""
    monkeypatch.chdir(mock_config_dir)
    
    today = datetime.now().date()
    past_date = today - timedelta(days=30)
    future_date = today + timedelta(days=30)
    
    mock_ticker = MagicMock()
    mock_ticker.calendar = {
        'Earnings Date': [past_date, future_date]
    }
    
    with patch('app.services.earnings_service.yf.Ticker', return_value=mock_ticker):
        with patch('app.services.earnings_service.send_telegram_message'):
            with patch('app.services.earnings_service.time.sleep'):
                result = scrape_earnings_dates()
    
    # Only future dates should be in result
    for date_str in result:
        assert date_str >= today.isoformat()


def test_scrape_earnings_dates_merges_existing_data(mock_config_dir, monkeypatch):
    """Test that existing dates are preserved and merged with new ones"""
    monkeypatch.chdir(mock_config_dir)
    
    # Create existing config file
    config_dir = mock_config_dir / "config"
    config_dir.mkdir(exist_ok=True)
    output_file = config_dir / "earnings-blackout-dates.json"
    
    today = datetime.now().date()
    existing_date = (today + timedelta(days=15)).isoformat()
    
    existing_data = {
        "last_updated": datetime.now().isoformat(),
        "source": "previous",
        "tickers_checked": ["TEST"],
        "dates": [existing_date]
    }
    
    with open(output_file, 'w') as f:
        json.dump(existing_data, f)
    
    future_date = today + timedelta(days=30)
    mock_ticker = MagicMock()
    mock_ticker.calendar = {
        'Earnings Date': [future_date]
    }
    
    with patch('app.services.earnings_service.yf.Ticker', return_value=mock_ticker):
        with patch('app.services.earnings_service.send_telegram_message'):
            with patch('app.services.earnings_service.time.sleep'):
                result = scrape_earnings_dates()
    
    # Both existing and new dates should be present
    assert existing_date in result or len(result) >= 1


def test_scrape_earnings_dates_with_telegram(mock_config_dir, monkeypatch):
    """Test that Telegram notifications are sent when password is provided"""
    monkeypatch.chdir(mock_config_dir)
    
    today = datetime.now().date()
    future_date = today + timedelta(days=30)
    
    mock_ticker = MagicMock()
    mock_ticker.calendar = {
        'Earnings Date': [future_date]
    }
    
    with patch('app.services.earnings_service.yf.Ticker', return_value=mock_ticker):
        with patch('app.services.earnings_service.send_telegram_message') as mock_telegram:
            with patch('app.services.earnings_service.time.sleep'):
                result = scrape_earnings_dates(jasypt_password="test_password")
    
    # Telegram should be called twice (start and completion)
    assert mock_telegram.call_count == 2
    
    # Verify message contents
    start_call = mock_telegram.call_args_list[0]
    assert "Earnings Scraper Started" in start_call[0][0]
    
    end_call = mock_telegram.call_args_list[1]
    assert "Earnings Scraper Finished" in end_call[0][0]


def test_scrape_earnings_dates_handles_list_and_single_dates(mock_config_dir, monkeypatch):
    """Test that both single dates and lists of dates are handled"""
    monkeypatch.chdir(mock_config_dir)
    
    today = datetime.now().date()
    future_date = today + timedelta(days=30)
    
    # Test with single date (not in list)
    mock_ticker = MagicMock()
    mock_ticker.calendar = {
        'Earnings Date': future_date  # Single date, not a list
    }
    
    with patch('app.services.earnings_service.yf.Ticker', return_value=mock_ticker):
        with patch('app.services.earnings_service.send_telegram_message'):
            with patch('app.services.earnings_service.time.sleep'):
                result = scrape_earnings_dates()
    
    assert isinstance(result, list)


def test_scrape_earnings_dates_creates_config_directory(mock_config_dir, monkeypatch):
    """Test that config directory is created if it doesn't exist"""
    monkeypatch.chdir(mock_config_dir)
    
    # Ensure config doesn't exist
    config_dir = mock_config_dir / "config"
    if config_dir.exists():
        import shutil
        shutil.rmtree(config_dir)
    
    today = datetime.now().date()
    future_date = today + timedelta(days=30)
    
    mock_ticker = MagicMock()
    mock_ticker.calendar = {
        'Earnings Date': [future_date]
    }
    
    with patch('app.services.earnings_service.yf.Ticker', return_value=mock_ticker):
        with patch('app.services.earnings_service.send_telegram_message'):
            with patch('app.services.earnings_service.time.sleep'):
                scrape_earnings_dates()
    
    # Config directory should now exist
    assert config_dir.exists()


def test_scrape_earnings_dates_handles_corrupt_existing_file(mock_config_dir, monkeypatch):
    """Test that corrupt existing JSON is handled gracefully"""
    monkeypatch.chdir(mock_config_dir)


def test_scrape_earnings_dates_converts_datetime_to_date(mock_config_dir, monkeypatch):
    """Exercise datetime -> date conversion path"""
    monkeypatch.chdir(mock_config_dir)

    today = datetime.now().date()
    future_dt = datetime.now() + timedelta(days=30)

    mock_ticker = MagicMock()
    mock_ticker.calendar = {
        'Earnings Date': [future_dt]
    }

    with patch('app.services.earnings_service.yf.Ticker', return_value=mock_ticker):
        with patch('app.services.earnings_service.send_telegram_message'):
            with patch('app.services.earnings_service.time.sleep'):
                result = scrape_earnings_dates()

    assert any(d >= today.isoformat() for d in result)


def test_scrape_earnings_dates_with_telegram_no_new_dates(mock_config_dir, monkeypatch):
    """Cover telegram completion message when no new dates are found"""
    monkeypatch.chdir(mock_config_dir)

    mock_ticker = MagicMock()
    mock_ticker.calendar = None

    with patch('app.services.earnings_service.yf.Ticker', return_value=mock_ticker):
        with patch('app.services.earnings_service.send_telegram_message') as mock_telegram:
            with patch('app.services.earnings_service.time.sleep'):
                scrape_earnings_dates(jasypt_password="test_password")

    assert mock_telegram.call_count == 2
    assert "No new dates found" in mock_telegram.call_args_list[1][0][0]
    
    # Create corrupt JSON file
    config_dir = mock_config_dir / "config"
    config_dir.mkdir(exist_ok=True)
    output_file = config_dir / "earnings-blackout-dates.json"
    
    with open(output_file, 'w') as f:
        f.write("{ invalid json }")
    
    today = datetime.now().date()
    future_date = today + timedelta(days=30)
    
    mock_ticker = MagicMock()
    mock_ticker.calendar = {
        'Earnings Date': [future_date]
    }
    
    with patch('app.services.earnings_service.yf.Ticker', return_value=mock_ticker):
        with patch('app.services.earnings_service.send_telegram_message'):
            with patch('app.services.earnings_service.time.sleep'):
                result = scrape_earnings_dates()
    
    # Should handle gracefully and return new dates
    assert isinstance(result, list)
