import os
import runpy
import sys


def _ensure_python_path():
    python_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    if python_dir not in sys.path:
        sys.path.insert(0, python_dir)


def test_bridge_import_adds_compat_path():
    _ensure_python_path()

    import bridge  # noqa: F401

    compat_dir = os.path.join(os.path.dirname(os.path.abspath(bridge.__file__)), "compat")
    assert compat_dir in sys.path


def test_bridge_main_invokes_uvicorn(monkeypatch):
    _ensure_python_path()

    import uvicorn

    called = {}

    def fake_run(app, host, port, log_level):
        called.update({"host": host, "port": port, "log_level": log_level, "app": app})

    monkeypatch.setattr(uvicorn, "run", fake_run)

    runpy.run_module("bridge", run_name="__main__")

    assert called["host"] == "0.0.0.0"
    assert called["port"] == 8888
    assert called["log_level"] == "info"
    assert called["app"] is not None
