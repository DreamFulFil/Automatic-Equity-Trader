import pytest
from app.services.data_operations_service import (
    get_source_stats,
    reset_source_stats,
    _source_stats,
    _source_stats_lock
)


def test_get_source_stats_initial_state():
    """Test getting source stats returns expected structure"""
    reset_source_stats()
    stats = get_source_stats()
    
    assert "shioaji" in stats
    assert "yahoo" in stats
    assert "twse" in stats
    assert "last_fetch" in stats
    assert "total_fetches" in stats
    
    assert stats["shioaji"]["success"] == 0
    assert stats["shioaji"]["failed"] == 0
    assert stats["shioaji"]["records"] == 0
    assert stats["total_fetches"] == 0


def test_reset_source_stats():
    """Test reset clears all statistics"""
    # Manually modify stats
    with _source_stats_lock:
        _source_stats["shioaji"]["success"] = 10
        _source_stats["yahoo"]["failed"] = 5
        _source_stats["total_fetches"] = 100
        _source_stats["last_fetch"]["2330"] = {"source": "shioaji", "count": 50, "timestamp": "2025-01-01"}
    
    # Reset
    reset_source_stats()
    
    # Verify all cleared
    stats = get_source_stats()
    assert stats["shioaji"]["success"] == 0
    assert stats["yahoo"]["failed"] == 0
    assert stats["total_fetches"] == 0
    assert len(stats["last_fetch"]) == 0


def test_get_source_stats_returns_copy():
    """Test that get_source_stats returns a copy, not reference"""
    reset_source_stats()
    stats1 = get_source_stats()
    stats2 = get_source_stats()
    
    # Modify stats1
    stats1["shioaji"]["success"] = 999
    
    # stats2 should not be affected
    assert stats2["shioaji"]["success"] == 0


def test_source_stats_structure():
    """Test that source stats have correct structure"""
    reset_source_stats()
    stats = get_source_stats()
    
    # Each source should have success, failed, records
    for source in ["shioaji", "yahoo", "twse"]:
        assert "success" in stats[source]
        assert "failed" in stats[source]
        assert "records" in stats[source]
        assert isinstance(stats[source]["success"], int)
        assert isinstance(stats[source]["failed"], int)
        assert isinstance(stats[source]["records"], int)


def test_source_stats_last_fetch_tracking():
    """Test last_fetch dictionary tracking"""
    reset_source_stats()
    
    # Simulate a fetch
    with _source_stats_lock:
        _source_stats["last_fetch"]["2330"] = {
            "source": "yahoo",
            "count": 100,
            "timestamp": "2025-12-30T10:00:00"
        }
        _source_stats["total_fetches"] += 1
    
    stats = get_source_stats()
    assert "2330" in stats["last_fetch"]
    assert stats["last_fetch"]["2330"]["source"] == "yahoo"
    assert stats["last_fetch"]["2330"]["count"] == 100
    assert stats["total_fetches"] == 1


def test_multiple_symbols_last_fetch():
    """Test tracking multiple symbols in last_fetch"""
    reset_source_stats()
    
    symbols = ["2330", "2454", "2317"]
    with _source_stats_lock:
        for i, symbol in enumerate(symbols):
            _source_stats["last_fetch"][symbol] = {
                "source": "shioaji",
                "count": (i + 1) * 50,
                "timestamp": f"2025-12-30T10:{i:02d}:00"
            }
    
    stats = get_source_stats()
    assert len(stats["last_fetch"]) == 3
    for symbol in symbols:
        assert symbol in stats["last_fetch"]


def test_concurrent_stats_access():
    """Test thread-safe access to stats"""
    import threading
    
    reset_source_stats()
    
    def increment_stats():
        for _ in range(100):
            with _source_stats_lock:
                _source_stats["shioaji"]["success"] += 1
                _source_stats["total_fetches"] += 1
    
    threads = [threading.Thread(target=increment_stats) for _ in range(5)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    
    stats = get_source_stats()
    assert stats["shioaji"]["success"] == 500  # 100 * 5 threads
    assert stats["total_fetches"] == 500


def test_source_stats_all_sources_independent():
    """Test that different sources have independent counters"""
    reset_source_stats()
    
    with _source_stats_lock:
        _source_stats["shioaji"]["success"] = 10
        _source_stats["yahoo"]["failed"] = 5
        _source_stats["twse"]["records"] = 1000
    
    stats = get_source_stats()
    assert stats["shioaji"]["success"] == 10
    assert stats["shioaji"]["failed"] == 0  # Not modified
    assert stats["yahoo"]["failed"] == 5
    assert stats["yahoo"]["success"] == 0  # Not modified
    assert stats["twse"]["records"] == 1000
    assert stats["twse"]["success"] == 0  # Not modified
