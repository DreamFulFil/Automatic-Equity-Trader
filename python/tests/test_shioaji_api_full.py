import shioaji as sj


def test_shioaji_full_surface():
    # top-level class
    assert hasattr(sj, 'Shioaji')
    api = sj.Shioaji()

    # methods expected on API
    for method in ['login', 'logout', 'activate_ca', 'place_order', 'Order']:
        assert hasattr(api, method) or hasattr(sj, method) or hasattr(sj, method.capitalize()), f"Missing {method} on Shioaji API"

    # Account and constants
    from shioaji import account, constant
    assert hasattr(account, 'FutureAccount')
    for const in ['Action', 'QuoteType', 'QuoteVersion', 'OrderType', 'FuturesPriceType']:
        assert hasattr(constant, const), f"Missing constant {const}"

    # Contract access structure - Contracts.Futures.TXF.TXFR1
    # Some shioaji builds expose Contracts as module-level; others via api.Contracts after login
    has_contracts = hasattr(api, 'Contracts')
    if not has_contracts:
        try:
            import shioaji.contracts as contracts_mod
            has_contracts = hasattr(contracts_mod, 'Contracts')
        except Exception:
            has_contracts = False
    assert has_contracts, "Contracts structure not found on API or module"

    # quote interface
    assert hasattr(api, 'quote')
    q = api.quote
    # typical quote methods used
    assert hasattr(q, 'subscribe')
    # callback registration
    assert hasattr(q, 'set_on_tick_fop_v1_callback') or hasattr(q, 'set_on_tick_callback')

    # order building and placement
    # Order class may be under api.Order or sj.Order
    assert hasattr(api, 'Order') or hasattr(sj, 'Order')

    # account/margin access
    assert hasattr(api, 'margin') or hasattr(api, 'get_margin')

    # profit/loss list method
    assert hasattr(api, 'list_profit_loss') or hasattr(api, 'list_profit')

    # futopt_account attribute
    assert hasattr(api, 'futopt_account') or hasattr(api, 'account') or hasattr(api, 'accounts')
