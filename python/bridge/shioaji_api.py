"""
Shioaji API wrapper and management.
Handles all Shioaji-specific operations.
"""

import shioaji as sj
import threading
from datetime import datetime, timedelta


class ShioajiAPIManager:
    """
    Manages Shioaji API connections and operations.
    This is a thin wrapper around ShioajiWrapper for better separation of concerns.
    """
    
    def __init__(self, shioaji_wrapper):
        """
        Initialize the API manager.
        
        Args:
            shioaji_wrapper: The ShioajiWrapper instance
        """
        self.wrapper = shioaji_wrapper
    
    def get_account_info(self):
        """
        Get account equity and margin information.
        
        Returns:
            dict: Account information
        """
        return self.wrapper.get_account_info()
    
    def get_profit_loss_history(self, days=30):
        """
        Get profit/loss history for the specified number of days.
        
        Args:
            days: Number of days to retrieve
        
        Returns:
            dict: P&L history
        """
        return self.wrapper.get_profit_loss_history(days)
    
    def place_order(self, action, quantity, price, strategy_name="Unknown"):
        """
        Place an order through Shioaji.
        
        Args:
            action: "BUY" or "SELL"
            quantity: Order quantity
            price: Order price
            strategy_name: Name of the strategy placing the order
        
        Returns:
            dict: Order result
        """
        print(f"🤖 [API][Strategy: {strategy_name}] Placing {action} order: {quantity} @ {price}")
        result = self.wrapper.place_order(action, quantity, price)
        
        if result.get("status") == "filled":
            print(f"🤖 [API][Strategy: {strategy_name}] Order filled successfully")
        elif result.get("status") == "error":
            print(f"❌ [API][Strategy: {strategy_name}] Order failed: {result.get('error')}")
        
        return result
    
    def subscribe_bidask(self):
        """
        Subscribe to Level 2 order book data.
        
        Returns:
            bool: Success status
        """
        return self.wrapper.subscribe_bidask()
    
    def is_connected(self):
        """
        Check if Shioaji is connected.
        
        Returns:
            bool: Connection status
        """
        return self.wrapper.connected
    
    def reconnect(self):
        """
        Attempt to reconnect to Shioaji.
        
        Returns:
            bool: Reconnection success status
        """
        return self.wrapper.reconnect()
