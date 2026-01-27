"""
News Service - Fetch news from Yahoo Finance for sentiment analysis.

Provides news articles for trading strategies that use sentiment:
- NewsSentimentStrategy
- NewsRevisionMomentumStrategy

@since 2026-01-26 - Phase 2 Data Improvement Plan
"""
import yfinance as yf
from typing import Optional
from datetime import datetime, timezone
import feedparser
import re


def get_news_for_symbol(symbol: str, max_articles: int = 10) -> dict:
    """
    Fetch news articles for a stock symbol from Yahoo Finance.
    
    Args:
        symbol: Stock ticker (e.g., "2330.TW", "TSMC")
        max_articles: Maximum number of articles to return
        
    Returns:
        Dictionary with list of news articles or error message
    """
    try:
        stock = yf.Ticker(symbol)
        news = stock.news
        
        if not news:
            return {
                "symbol": symbol,
                "articles": [],
                "count": 0,
                "message": "No news available"
            }
        
        articles = []
        for item in news[:max_articles]:
            article = {
                "headline": item.get("title", ""),
                "summary": _extract_summary(item),
                "url": item.get("link", ""),
                "source": item.get("publisher", "Unknown"),
                "published_at": _format_timestamp(item.get("providerPublishTime")),
                "type": item.get("type", "ARTICLE"),
                "thumbnail": _get_thumbnail(item),
            }
            articles.append(article)
        
        return {
            "symbol": symbol,
            "articles": articles,
            "count": len(articles)
        }
        
    except Exception as e:
        return {"error": str(e), "symbol": symbol}


def get_news_for_symbols(symbols: list[str], max_articles_per_symbol: int = 5) -> dict:
    """
    Fetch news for multiple symbols.
    
    Args:
        symbols: List of stock tickers
        max_articles_per_symbol: Maximum articles per symbol
        
    Returns:
        Dictionary with news by symbol
    """
    result = {}
    for symbol in symbols:
        result[symbol] = get_news_for_symbol(symbol, max_articles_per_symbol)
    return result


def get_market_news_tw() -> dict:
    """
    Fetch general Taiwan market news from RSS feeds.
    
    Returns:
        Dictionary with market news articles
    """
    feeds = [
        ("MoneyDJ", "https://www.moneydj.com/rss/RssNews.djhtm"),
        ("UDN_Stock", "https://udn.com/rssfeed/news/2/6638"),
        ("CNYES", "https://news.cnyes.com/rss/cat/tw_stock"),
    ]
    
    articles = []
    
    for source_name, feed_url in feeds:
        try:
            feed = feedparser.parse(feed_url, timeout=10)
            for entry in feed.entries[:5]:  # Get up to 5 per feed
                article = {
                    "headline": entry.get("title", ""),
                    "summary": _clean_html(entry.get("summary", "")),
                    "url": entry.get("link", ""),
                    "source": source_name,
                    "published_at": _parse_rss_date(entry),
                    "type": "RSS"
                }
                articles.append(article)
        except Exception as e:
            pass  # Skip failed feeds
    
    # Sort by published time, most recent first
    articles.sort(key=lambda x: x.get("published_at", ""), reverse=True)
    
    return {
        "symbol": "MARKET_TW",
        "articles": articles[:15],  # Limit total articles
        "count": len(articles[:15])
    }


def extract_symbols_from_headline(headline: str) -> list[str]:
    """
    Extract Taiwan stock symbols from a news headline.
    
    Taiwan stocks are typically formatted as:
    - 4-digit numbers: 2330, 2454, 2317
    - With .TW suffix sometimes
    
    Args:
        headline: News headline text
        
    Returns:
        List of extracted stock symbols
    """
    # Look for 4-digit numbers that could be Taiwan stock codes
    # Common Taiwan stock codes are between 1000 and 9999
    pattern = r'\b([1-9][0-9]{3})\b'
    matches = re.findall(pattern, headline)
    
    # Filter to likely stock codes (avoid year numbers, etc.)
    stock_codes = []
    current_year = datetime.now().year
    
    for match in matches:
        num = int(match)
        # Exclude years and unlikely codes
        if num < current_year - 5 or num > current_year + 5:
            if num >= 1101 and num <= 9999:  # Valid Taiwan stock range
                stock_codes.append(f"{match}.TW")
    
    return list(set(stock_codes))  # Remove duplicates


def _extract_summary(item: dict) -> Optional[str]:
    """Extract summary from news item."""
    # Try different fields that might contain summary
    if "summary" in item:
        return _clean_html(item["summary"])
    if "description" in item:
        return _clean_html(item["description"])
    return None


def _format_timestamp(unix_timestamp: Optional[int]) -> str:
    """Format Unix timestamp to ISO format."""
    if unix_timestamp:
        try:
            dt = datetime.fromtimestamp(unix_timestamp, tz=timezone.utc)
            return dt.isoformat()
        except (ValueError, OSError):
            pass
    return datetime.now(timezone.utc).isoformat()


def _parse_rss_date(entry: dict) -> str:
    """Parse date from RSS feed entry."""
    # Try different date fields
    for field in ["published_parsed", "updated_parsed", "created_parsed"]:
        if field in entry and entry[field]:
            try:
                from time import mktime
                dt = datetime.fromtimestamp(mktime(entry[field]), tz=timezone.utc)
                return dt.isoformat()
            except (ValueError, TypeError):
                pass
    return datetime.now(timezone.utc).isoformat()


def _get_thumbnail(item: dict) -> Optional[str]:
    """Extract thumbnail URL from news item."""
    if "thumbnail" in item and item["thumbnail"]:
        resolutions = item["thumbnail"].get("resolutions", [])
        if resolutions:
            return resolutions[0].get("url")
    return None


def _clean_html(text: str) -> str:
    """Remove HTML tags from text."""
    if not text:
        return ""
    # Simple HTML tag removal
    clean = re.sub(r'<[^>]+>', '', text)
    # Decode common HTML entities
    clean = clean.replace("&amp;", "&")
    clean = clean.replace("&lt;", "<")
    clean = clean.replace("&gt;", ">")
    clean = clean.replace("&quot;", '"')
    clean = clean.replace("&#39;", "'")
    clean = clean.replace("&nbsp;", " ")
    return clean.strip()
