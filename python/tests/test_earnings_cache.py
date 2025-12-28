import os
import json
import shutil
from app import main as main_module

CONFIG_PATH = os.path.join(os.getcwd(), 'config', 'earnings-blackout-dates.json')


def _backup_and_write(content: dict):
    backup = None
    if os.path.exists(CONFIG_PATH):
        backup = CONFIG_PATH + '.bak'
        shutil.copy(CONFIG_PATH, backup)
    with open(CONFIG_PATH, 'w') as f:
        json.dump(content, f)
    return backup


def _restore_backup(backup):
    if backup:
        shutil.move(backup, CONFIG_PATH)
    else:
        try:
            os.remove(CONFIG_PATH)
        except FileNotFoundError:
            pass


def test_earnings_endpoint_returns_cached_file(monkeypatch):
    """GET /earnings/scrape should quickly return cached file when present"""
    cached = {
        "last_updated": "2025-12-28T00:00:00",
        "source": "test-cache",
        "tickers_checked": ["TSM"],
        "dates": ["2026-01-01"]
    }

    # Backup existing config and write test cache
    backup = _backup_and_write(cached)

    # Ensure JASYPT_PASSWORD is not set so cached path is used without requiring password
    prev_jasypt = os.environ.pop('JASYPT_PASSWORD', None)

    result = main_module.scrape_earnings_endpoint()
    # Direct function call returns the cached dict
    assert isinstance(result, dict)
    assert result == cached

    # Cleanup
    if prev_jasypt is not None:
        os.environ['JASYPT_PASSWORD'] = prev_jasypt
    _restore_backup(backup)


def test_earnings_endpoint_force_triggers_live_scrape(monkeypatch):
    """GET /earnings/scrape?force=true should call scrape_earnings_dates and return live result"""
    # Backup existing config (leave cache present to ensure force overrides it)
    cached = {
        "last_updated": "2025-12-28T00:00:00",
        "source": "test-cache",
        "tickers_checked": ["TSM"],
        "dates": ["2026-01-01"]
    }
    backup = _backup_and_write(cached)

    # Ensure JASYPT_PASSWORD is set so code will allow live scrape
    prev_jasypt = os.environ.get('JASYPT_PASSWORD')
    os.environ['JASYPT_PASSWORD'] = 'dummy'

    # Monkeypatch the scrape_earnings_dates function used by main
    def fake_scrape(pw):
        assert pw == 'dummy'
        return ["2026-02-02"]

    monkeypatch.setattr(main_module, 'scrape_earnings_dates', fake_scrape)

    result = main_module.scrape_earnings_endpoint(force=True)
    # Expect a dict result from the live scrape path
    assert isinstance(result, dict)
    assert result.get('dates') == ["2026-02-02"]

    # Restore env and files
    if prev_jasypt is None:
        del os.environ['JASYPT_PASSWORD']
    else:
        os.environ['JASYPT_PASSWORD'] = prev_jasypt
    _restore_backup(backup)
