import pytest
from unittest.mock import MagicMock
import shioaji as sj
from app.services.shioaji_service import ShioajiWrapper

class DummyAPI:
    def __init__(self):
        self.stock_account = True
        self.Order = MagicMock()
        self.place_order = MagicMock(return_value=MagicMock(status=True, operation=None, status_id='testid'))

@pytest.fixture
def wrapper():
    config = {'shioaji': {'simulation': False}}
    w = ShioajiWrapper(config)
    w.api = DummyAPI()
    w.contract = MagicMock()
    w.connected = True
    return w

def test_odd_lot_order(wrapper):
    # Odd lot: 500 shares
    wrapper.api.Order.reset_mock()
    wrapper._place_stock_order('BUY', 500, 100.0)
    args, kwargs = wrapper.api.Order.call_args
    assert kwargs['order_lot'] == sj.constant.StockOrderLot.Odd

def test_round_lot_order(wrapper):
    # Round lot: 2000 shares
    wrapper.api.Order.reset_mock()
    wrapper._place_stock_order('SELL', 2000, 100.0)
    args, kwargs = wrapper.api.Order.call_args
    assert kwargs['order_lot'] == sj.constant.StockOrderLot.Common
