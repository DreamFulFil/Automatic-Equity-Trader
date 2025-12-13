"""
Lifecycle management for the trading bridge.
Handles startup and shutdown operations.
"""

import time
import os
import threading
from datetime import datetime

def handle_startup(config, shioaji_wrapper):
    """
    Handle bridge startup operations.
    
    Args:
        config: Configuration dictionary
        shioaji_wrapper: ShioajiWrapper instance
    
    Returns:
        tuple: (success: bool, message: str)
    """
    try:
        print(f"🤖 Bridge startup initiated at {datetime.now().isoformat()}")
        
        # Initialize Shioaji connection
        if not shioaji_wrapper.connect():
            return False, "Failed to connect to Shioaji"
        
        print(f"🤖 Bridge startup complete - Shioaji connected")
        return True, "Bridge started successfully"
        
    except Exception as e:
        print(f"❌ Startup error: {e}")
        return False, str(e)


def handle_shutdown(shioaji_wrapper):
    """
    Handle graceful shutdown of the bridge.
    
    Args:
        shioaji_wrapper: ShioajiWrapper instance
    """
    def do_shutdown():
        time.sleep(1)
        print("🤖 Shutdown requested - cleaning up...")
        if shioaji_wrapper:
            shioaji_wrapper.logout()
        print("🤖 Shutdown complete")
        os._exit(0)
    
    threading.Thread(target=do_shutdown).start()
    return {"status": "shutting_down", "message": "🤖 Python bridge shutting down gracefully"}
