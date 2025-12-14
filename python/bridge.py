#!/usr/bin/env python3
"""
Dual-Mode Trading Bridge - FastAPI + Shioaji + Ollama
Entry point for the refactored application.
"""
import sys
import os
import uvicorn

# Ensure local compat shims are importable (for Python 3.14 compat)
compat_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'compat')
if compat_dir not in sys.path:
    sys.path.insert(0, compat_dir)

# Add current directory to path so we can import app
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from app.main import app

if __name__ == "__main__":
    # Log Python version for debugging
    print(f"🐍 Python {sys.version} on {sys.platform}")
    
    print("🐍 Python bridge starting on port 8888...")
    uvicorn.run(app, host="0.0.0.0", port=8888, log_level="info")
