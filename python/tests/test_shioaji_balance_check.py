import pytest
from unittest.mock import MagicMock
from app.services.shioaji_service import ShioajiWrapper

class DummyAPI:
    def __init__(self):
        self.stock_account = True
        # Patch Order to return a real object with integer quantity
        def Order(**kwargs):
            class OrderObj:
                pass
            for k, v in kwargs.items():
                setattr(OrderObj, k, v)
            return OrderObj
        self.Order = Order
        # Simulate a successful fill if quantity > 0
        def place_order(contract, order_obj):
            class Status:
                id = 'testid'
                order_quantity = order_obj.quantity
            class Trade:
                status = Status()
                operation = None
            if order_obj.quantity > 0:
                return Trade()
            else:
                class TradeFail:
                    status = None
                    operation = None
                return TradeFail()
        self.place_order = place_order
    def account_balance(self):
        class Balance:
            acc_balance = 10000
        return Balance()

@pytest.fixture
def wrapper():
    config = {'shioaji': {'simulation': False}}
    w = ShioajiWrapper(config)
    w.api = DummyAPI()
    w.contract = MagicMock()
    w.connected = True
    return w

def test_buy_with_sufficient_funds(wrapper):
    # Should not reduce quantity
    result = wrapper.place_order('BUY', 50, 100.0)
    assert result['status'] == 'filled'
    # The last order placed should have quantity 50
    assert wrapper.api.Order(quantity=50, price=100.0).quantity == 50

def test_buy_with_insufficient_funds(wrapper):
    # Should reduce quantity to max affordable
    result = wrapper.place_order('BUY', 200, 100.0)
    assert result['status'] == 'filled'
    # The last order placed should have quantity 100 (max affordable)
    assert wrapper.api.Order(quantity=100, price=100.0).quantity == 100

def test_buy_with_zero_funds(wrapper):
    wrapper.api.account_balance = lambda: type('Balance', (), {'acc_balance': 0})()
    result = wrapper.place_order('BUY', 10, 100.0)
    assert result['status'] == 'error'
    assert 'Insufficient funds' in result['error']
