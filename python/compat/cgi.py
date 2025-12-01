# Compatibility shim for Python 3.14 removal of cgi module used by feedparser
# Minimal functions expected by feedparser during import

def parse_qs(qs, keep_blank_values=False, strict_parsing=False):
    # Very small placeholder - not used in tests
    from urllib.parse import parse_qs as _parse_qs
    return _parse_qs(qs, keep_blank_values=keep_blank_values)

FieldStorage = object  # placeholder

# Provide escape function if needed
def escape(s, quote=True):
    return str(s)
