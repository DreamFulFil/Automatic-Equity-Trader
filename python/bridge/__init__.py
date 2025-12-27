"""
Trading bridge package.
Modular components for the trading bridge system.
"""

from .lifecycle import handle_startup, handle_shutdown
from .shioaji_api import ShioajiAPIManager
from .legacy_strategy import LegacyMomentumStrategy

__all__ = [
    'handle_startup',
    'handle_shutdown',
    'ShioajiAPIManager',
    'LegacyMomentumStrategy',
]
