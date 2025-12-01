import shioaji as sj


def test_shioaji_api_surface():
    # Import and basic instantiation should not raise
    assert hasattr(sj, "Shioaji")
    api = sj.Shioaji()

    # Core methods used by bridge
    assert callable(getattr(api, "login", None))
    assert callable(getattr(api, "logout", None))
    assert callable(getattr(api, "activate_ca", None))
    assert callable(getattr(api, "list_accounts", None))

    # Quote interface should exist
    assert hasattr(api, "quote")

    # Account and constant enums used by bridge
    from shioaji import account, constant
    assert hasattr(account, "FutureAccount")
    assert hasattr(constant, "Action")
    assert hasattr(constant, "QuoteType")
    assert hasattr(constant, "QuoteVersion")
    assert hasattr(constant, "OrderType")

    # Optional: Ordering and contract classes are available in module
    # These are sanity checks rather than functional tests
    assert hasattr(sj, "Shioaji")
