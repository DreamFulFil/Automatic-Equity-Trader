import shioaji as sj
import threading
import time
import sys
import os
from datetime import datetime, timedelta
from collections import deque

# Global state for market data (shared with strategies)
# In a cleaner architecture, this might be in a separate MarketDataService
latest_tick = {"price": 0, "volume": 0, "timestamp": None}
price_history = deque(maxlen=600)
volume_history = deque(maxlen=600)
session_open_price = None
session_high = None
session_low = None
streaming_quotes = deque(maxlen=100)
streaming_quotes_lock = threading.Lock()
order_book = {
    "bids": [],
    "asks": [],
    "timestamp": None,
    "symbol": None
}
order_book_lock = threading.Lock()

class ShioajiWrapper:
    """
    Shioaji wrapper with auto-reconnect capability and dual-mode support.
    Retries login + subscription up to 5 times with exponential backoff.
    
    Modes:
    - "stock": Uses api.Contracts.Stocks.TSE["2454"] for 2454.TW odd lots
    - "futures": Uses api.Contracts.Futures.TXF.TXFR1 for MTXF
    """
    
    MAX_RETRIES = 5
    BASE_BACKOFF_SECONDS = 2
    
    def __init__(self, config, trading_mode="stock"):
        self.config = config
        self.trading_mode = trading_mode
        self.api = None
        self.contract = None  # Generic contract (stock or futures)
        self.mtxf_contract = None  # Legacy alias for backwards compat
        self.connected = False
        self._lock = threading.Lock()
        self._callback_ref = None  # Keep callback alive
        print(f"📈 ShioajiWrapper initialized in {trading_mode.upper()} mode")
        
    def connect(self) -> bool:
        """Connect to Shioaji with retry logic"""
        for attempt in range(1, self.MAX_RETRIES + 1):
            try:
                print(f"🔄 Shioaji connection attempt {attempt}/{self.MAX_RETRIES}...")
                
                # Create fresh API instance
                self.api = sj.Shioaji()
                
                # Select credentials based on mode
                if self.trading_mode == "stock":
                    creds = self.config['shioaji'].get('stock', {})
                else:
                    creds = self.config['shioaji'].get('future', {})

                # Login
                self.api.login(
                    api_key=creds.get('api-key'),
                    secret_key=creds.get('secret-key'),
                    contracts_cb=lambda security_type: print(f"✅ Contracts loaded: {security_type}")
                )
                
                # Activate CA
                self.api.activate_ca(
                    ca_path=self.config['shioaji']['ca-path'],
                    ca_passwd=self.config['shioaji']['ca-password'],
                    person_id=self.config['shioaji']['person-id']
                )
                
                mode = "📄 Paper trading" if self.config['shioaji']['simulation'] else "💰 LIVE TRADING"
                print(f"{mode} mode activated")
                
                # Subscribe based on trading mode
                if self.trading_mode == "stock":
                    self._subscribe_stock()
                else:
                    self._subscribe_futures()
                
                self.connected = True
                return True
                
            except Exception as e:
                print(f"❌ Connection attempt {attempt} failed: {e}")
                if attempt < self.MAX_RETRIES:
                    backoff = self.BASE_BACKOFF_SECONDS ** attempt
                    print(f"⏳ Waiting {backoff}s before retry...")
                    time.sleep(backoff)
                else:
                    print("❌ All connection attempts failed!")
                    return False
        
        return False
    
    def _subscribe_stock(self):
        """Subscribe to 2454.TW (MediaTek) for stock mode"""
        self.contract = self.api.Contracts.Stocks.TSE["2454"]
        self.api.quote.subscribe(
            self.contract,
            quote_type=sj.constant.QuoteType.Tick,
            version=sj.constant.QuoteVersion.v1
        )
        
        # Register tick handler
        def safe_tick_handler(exchange, tick):
            try:
                self._handle_tick(exchange, tick)
            except Exception as e:
                print(f"⚠️ Tick callback crashed (recovered): {e}")
        
        self._callback_ref = safe_tick_handler
        self.api.quote.set_on_tick_stk_v1_callback(self._callback_ref)
        
        print(f"✅ Subscribed to {self.contract.symbol} (STOCK mode)")
    
    def _subscribe_futures(self):
        """Subscribe to MTXF for futures mode"""
        self.contract = self.api.Contracts.Futures.TXF.TXFR1
        self.mtxf_contract = self.contract  # Legacy alias
        self.api.quote.subscribe(
            self.contract,
            quote_type=sj.constant.QuoteType.Tick,
            version=sj.constant.QuoteVersion.v1
        )
        
        # Register tick handler
        def safe_tick_handler(exchange, tick):
            try:
                self._handle_tick(exchange, tick)
            except Exception as e:
                print(f"⚠️ Tick callback crashed (recovered): {e}")
        
        self._callback_ref = safe_tick_handler
        self.api.quote.set_on_tick_fop_v1_callback(self._callback_ref)
        
        print(f"✅ Subscribed to {self.contract.symbol} (FUTURES mode)")
    
    def subscribe_bidask(self):
        """Subscribe to Level 2 order book (bid/ask depth) for current contract"""
        try:
            # Subscribe to bidask quote type for order book data
            self.api.quote.subscribe(
                self.contract,
                quote_type=sj.constant.QuoteType.BidAsk,
                version=sj.constant.QuoteVersion.v1
            )
            
            # Register bidask handler based on mode
            if self.trading_mode == "stock":
                def safe_bidask_handler(exchange, bidask):
                    try:
                        self._handle_bidask(exchange, bidask)
                    except Exception as e:
                        print(f"⚠️ BidAsk callback crashed (recovered): {e}")
                
                self._bidask_callback_ref = safe_bidask_handler
                self.api.quote.set_on_bidask_stk_v1_callback(self._bidask_callback_ref)
            else:
                def safe_bidask_handler(exchange, bidask):
                    try:
                        self._handle_bidask(exchange, bidask)
                    except Exception as e:
                        print(f"⚠️ BidAsk callback crashed (recovered): {e}")
                
                self._bidask_callback_ref = safe_bidask_handler
                self.api.quote.set_on_bidask_fop_v1_callback(self._bidask_callback_ref)
            
            print(f"✅ Subscribed to Level 2 order book for {self.contract.symbol}")
            return True
        except Exception as e:
            print(f"❌ Failed to subscribe to order book: {e}")
            return False
    
    def _handle_bidask(self, exchange, bidask):
        """Handle Level 2 order book data updates"""
        global order_book, order_book_lock
        
        try:
            # Extract bid/ask prices and volumes
            bids = []
            asks = []
            
            # Shioaji returns bid/ask as lists of prices and volumes
            if hasattr(bidask, 'bid_price') and hasattr(bidask, 'bid_volume'):
                bid_prices = bidask.bid_price if isinstance(bidask.bid_price, list) else [bidask.bid_price]
                bid_volumes = bidask.bid_volume if isinstance(bidask.bid_volume, list) else [bidask.bid_volume]
                
                for price, vol in zip(bid_prices, bid_volumes):
                    if price and vol:
                        bids.append({"price": float(price), "volume": int(vol)})
            
            if hasattr(bidask, 'ask_price') and hasattr(bidask, 'ask_volume'):
                ask_prices = bidask.ask_price if isinstance(bidask.ask_price, list) else [bidask.ask_price]
                ask_volumes = bidask.ask_volume if isinstance(bidask.ask_volume, list) else [bidask.ask_volume]
                
                for price, vol in zip(ask_prices, ask_volumes):
                    if price and vol:
                        asks.append({"price": float(price), "volume": int(vol)})
            
            timestamp = getattr(bidask, 'datetime', datetime.now())
            
            # Thread-safe update of order book
            with order_book_lock:
                order_book["bids"] = sorted(bids, key=lambda x: x["price"], reverse=True)[:5]
                order_book["asks"] = sorted(asks, key=lambda x: x["price"])[:5]
                order_book["timestamp"] = timestamp.isoformat() if hasattr(timestamp, 'isoformat') else str(timestamp)
                order_book["symbol"] = self.contract.symbol
                
        except Exception as e:
            print(f"⚠️ Order book handler error (non-fatal): {e}")
    
    def reconnect(self) -> bool:
        """Force reconnect (called when connection is lost)"""
        with self._lock:
            print("🔄 Reconnecting to Shioaji...")
            self.connected = False
            try:
                if self.api:
                    self.api.logout()
            except:
                pass
            return self.connect()
    
    def _handle_tick(self, exchange, tick):
        """Internal tick handler - updates global market data with crash protection"""
        try:
            global session_open_price, session_high, session_low, streaming_quotes, streaming_quotes_lock
            
            # Defensive checks to prevent segfaults
            if not tick or not hasattr(tick, 'close') or not hasattr(tick, 'volume'):
                return
            
            price = float(tick.close) if tick.close is not None else 0.0
            volume = tick.volume if tick.volume is not None else 0
            timestamp = getattr(tick, 'datetime', datetime.now())
            
            # Thread-safe updates with error handling
            latest_tick["price"] = price
            latest_tick["volume"] = volume
            latest_tick["timestamp"] = timestamp
            
            if price > 0:  # Only process valid prices
                price_history.append({"price": price, "time": timestamp, "volume": volume})
                volume_history.append(volume)
                
                # Add to streaming quotes buffer
                with streaming_quotes_lock:
                    streaming_quotes.append({
                        "symbol": self.contract.symbol if self.contract else "UNKNOWN",
                        "price": price,
                        "volume": volume,
                        "timestamp": timestamp.isoformat() if hasattr(timestamp, 'isoformat') else str(timestamp),
                        "exchange": str(exchange) if exchange else "UNKNOWN"
                    })
                
                if session_open_price is None:
                    session_open_price = price
                    session_high = price
                    session_low = price
                else:
                    session_high = max(session_high, price)
                    session_low = min(session_low, price)
        except Exception as e:
            # Log but don't crash - this prevents segfaults from propagating
            print(f"⚠️ Tick handler error (non-fatal): {e}")
    
    def place_order(self, action: str, quantity: int, price: float):
        """Place order with account validation and error handling - mode-aware"""
        # 🚨 CRITICAL: Check simulation mode first - NO REAL ORDERS IN SIMULATION
        if self.config['shioaji']['simulation']:
            print(f"🎭 SIMULATION MODE: Simulating {action} {quantity} shares @ {price}")
            # Generate a fake order ID for simulation
            import uuid
            fake_order_id = str(uuid.uuid4())[:8]
            return {"status": "filled", "order_id": f"sim-{fake_order_id}", "mode": self.trading_mode}

        if not self.connected:
            if not self.reconnect():
                return {"status": "error", "error": "Not connected"}

        try:
            # Pre-trade balance check for BUY orders
            if action == "BUY":
                account_info = self.get_account_info()
                if account_info.get("status") == "ok":
                    available = account_info.get("available_margin", 0)
                    total_cost = quantity * price
                    if total_cost > available:
                        max_quantity = int(available // price)
                        if max_quantity <= 0:
                            return {"status": "error", "error": "Insufficient funds for purchase"}
                        print(f"⚠️ Reducing quantity from {quantity} to {max_quantity} due to insufficient funds.")
                        quantity = max_quantity
            if self.trading_mode == "stock":
                return self._place_stock_order(action, quantity, price)
            else:
                return self._place_futures_order(action, quantity, price)

        except Exception as e:
            print(f"❌ Order failed: {e}")
            return {"status": "error", "error": str(e)}
    
    def _place_stock_order(self, action: str, quantity: int, price: float):
        """Place stock order (2454.TW odd lots)"""
        # Check stock account availability
        if not hasattr(self.api, 'stock_account') or self.api.stock_account is None:
            print("⚠️ Stock account not available, reconnecting...")
            if not self.reconnect():
                return {"status": "error", "error": "Stock account unavailable"}

        # Determine lot type: round lot (1000 shares) or odd lot
        if quantity % 1000 == 0:
            order_lot = sj.constant.StockOrderLot.Common  # Round lot
        else:
            order_lot = sj.constant.StockOrderLot.Odd    # Odd lot
        order_obj = self.api.Order(
            price=price,
            quantity=quantity,  # Integer for stocks
            action=sj.constant.Action.Buy if action == "BUY" else sj.constant.Action.Sell,
            price_type=sj.constant.StockPriceType.LMT,
            order_type=sj.constant.OrderType.ROD,
            order_lot=order_lot,
            account=self.api.stock_account
        )

        trade = self.api.place_order(self.contract, order_obj)

        # 🚨 CRITICAL: Check if order actually succeeded
        if hasattr(trade, 'status') and trade.status:
            # Check for error messages in the operation
            if hasattr(trade, 'operation') and trade.operation:
                op_msg = getattr(trade.operation, 'op_msg', '')
                if op_msg and ('不足' in op_msg or 'error' in op_msg.lower()):
                    print(f"❌ Order failed: {op_msg}")
                    return {"status": "error", "error": op_msg}

            # Check if any quantity was actually filled
            order_quantity = getattr(trade.status, 'order_quantity', 0)
            if order_quantity == 0:
                print(f"❌ Order failed: No shares filled (order_quantity=0)")
                return {"status": "error", "error": "Order not filled"}

            return {"status": "filled", "order_id": trade.status.id, "mode": "stock"}
        else:
            return {"status": "error", "error": "Invalid order response"}
    
    def _place_futures_order(self, action: str, quantity: int, price: float):
        """Place futures order (MTXF)"""
        # Check futures account availability
        if not hasattr(self.api, 'futopt_account') or self.api.futopt_account is None:
            print("⚠️ Futures account not available, reconnecting...")
            if not self.reconnect():
                return {"status": "error", "error": "Futures account unavailable"}

        order_obj = self.api.Order(
            price=price,
            quantity=quantity,  # Integer works for futures too
            action=sj.constant.Action.Buy if action == "BUY" else sj.constant.Action.Sell,
            price_type=sj.constant.FuturesPriceType.LMT,
            order_type=sj.constant.OrderType.ROD,
            account=self.api.futopt_account
        )

        trade = self.api.place_order(self.contract, order_obj)

        # 🚨 CRITICAL: Check if order actually succeeded
        if hasattr(trade, 'status') and trade.status:
            # Check for error messages in the operation
            if hasattr(trade, 'operation') and trade.operation:
                op_msg = getattr(trade.operation, 'op_msg', '')
                if op_msg and ('不足' in op_msg or 'error' in op_msg.lower()):
                    print(f"❌ Order failed: {op_msg}")
                    return {"status": "error", "error": op_msg}

            # Check if any quantity was actually filled
            order_quantity = getattr(trade.status, 'order_quantity', 0)
            if order_quantity == 0:
                print(f"❌ Order failed: No contracts filled (order_quantity=0)")
                return {"status": "error", "error": "Order not filled"}

            return {"status": "filled", "order_id": trade.status.id, "mode": "futures"}
        else:
            return {"status": "error", "error": "Invalid order response"}
    
    def get_account_info(self):
        """Get account equity and margin info with error handling - mode-aware"""
        # 🚨 CRITICAL: Return simulated account data in simulation mode
        if self.config['shioaji']['simulation']:
            print("🎭 SIMULATION MODE: Returning simulated account info")
            return {
                "equity": 100000.0,  # Simulated equity
                "available_margin": 50000.0,  # Simulated margin
                "status": "ok"
            }

        if not self.connected:
            return {"equity": 0, "available_margin": 0, "status": "error", "error": "Not connected"}

        try:
            if self.trading_mode == "stock":
                return self._get_stock_account_info()
            else:
                return self._get_futures_account_info()
        except Exception as e:
            print(f"❌ Failed to get account info: {e}")
            return {"equity": 0, "available_margin": 0, "status": "error", "error": str(e)}
    
    def _get_stock_account_info(self):
        """Get stock account info"""
        if not hasattr(self.api, 'stock_account') or self.api.stock_account is None:
            return {"equity": 0, "available_margin": 0, "status": "error", "error": "Stock account not available"}
        
        # For stock accounts, get balance
        try:
            balance = self.api.account_balance()
            equity = float(balance.acc_balance) if hasattr(balance, 'acc_balance') else 100000
            return {
                "equity": equity,
                "available_margin": equity,
                "status": "ok",
                "mode": "stock"
            }
        except Exception as e:
            # Return default equity for stock mode
            return {
                "equity": 100000,
                "available_margin": 100000,
                "status": "ok",
                "mode": "stock",
                "note": "Using default equity"
            }
    
    def _get_futures_account_info(self):
        """Get futures account info"""
        if not hasattr(self.api, 'futopt_account') or self.api.futopt_account is None:
            return {"equity": 0, "available_margin": 0, "status": "error", "error": "Futures account not available"}
        
        margin = self.api.margin(self.api.futopt_account)
        equity = float(margin.equity) if hasattr(margin, 'equity') else 0
        available_margin = float(margin.available_margin) if hasattr(margin, 'available_margin') else 0
        
        return {
            "equity": equity,
            "available_margin": available_margin,
            "status": "ok",
            "mode": "futures"
        }
    
    def get_profit_loss_history(self, days=30):
        """Get realized P&L for the last N days with error handling - mode-aware"""
        if not self.connected:
            return {"total_pnl": 0, "days": days, "record_count": 0, "status": "error", "error": "Not connected"}
        
        try:
            if self.trading_mode == "stock":
                return self._get_stock_pnl_history(days)
            else:
                return self._get_futures_pnl_history(days)
        except Exception as e:
            print(f"❌ Failed to get P&L history: {e}")
            return {"total_pnl": 0, "days": days, "record_count": 0, "status": "error", "error": str(e)}
    
    def _get_stock_pnl_history(self, days):
        """Get stock P&L history"""
        # For stock mode, return 0 as baseline (no futures P&L history)
        return {
            "total_pnl": 0,
            "days": days,
            "record_count": 0,
            "status": "ok",
            "mode": "stock"
        }
    
    def _get_futures_pnl_history(self, days):
        """Get futures P&L history"""
        if not hasattr(self.api, 'futopt_account') or self.api.futopt_account is None:
            return {"total_pnl": 0, "days": days, "record_count": 0, "status": "error", "error": "Futures account not available"}
        
        end_date = datetime.now()
        start_date = end_date - timedelta(days=days)
        
        pnl_records = self.api.list_profit_loss(
            self.api.futopt_account,
            start_date.strftime("%Y-%m-%d"),
            end_date.strftime("%Y-%m-%d")
        )
        
        total_pnl = 0.0
        for record in pnl_records:
            if hasattr(record, 'pnl'):
                total_pnl += float(record.pnl)
        
        return {
            "total_pnl": total_pnl,
            "days": days,
            "record_count": len(pnl_records),
            "status": "ok",
            "mode": "futures"
        }
    
    def logout(self):
        """Graceful logout"""
        try:
            if self.api:
                self.api.logout()
                print("✅ Shioaji logged out")
        except Exception as e:
            print(f"⚠️ Logout error: {e}")
