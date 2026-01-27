"""
Calendar Service - Economic calendar and market holiday data for trading strategies.

Provides:
- Taiwan market holidays
- Futures expiration dates
- Economic event calendar
- Seasonal strength indicators
"""

from datetime import date, datetime, timedelta
from typing import Optional
import calendar
import logging

logger = logging.getLogger(__name__)


# Taiwan fixed holidays (month, day) - approximate dates
TAIWAN_FIXED_HOLIDAYS = [
    (1, 1),   # New Year's Day
    (2, 28),  # Peace Memorial Day
    (4, 4),   # Children's Day
    (4, 5),   # Tomb Sweeping Day (approximate)
    (5, 1),   # Labor Day
    (10, 10), # Double Ten Day
]

# 2026 Taiwan holidays (including lunar calendar holidays)
TAIWAN_HOLIDAYS_2026 = [
    date(2026, 1, 1),   # New Year
    date(2026, 1, 2),   # New Year Holiday
    date(2026, 2, 13),  # CNY Eve Observance
    date(2026, 2, 16),  # Chinese New Year Eve
    date(2026, 2, 17),  # Chinese New Year Day 1
    date(2026, 2, 18),  # Chinese New Year Day 2
    date(2026, 2, 19),  # Chinese New Year Day 3
    date(2026, 2, 20),  # Chinese New Year Holiday
    date(2026, 2, 28),  # Peace Memorial Day
    date(2026, 4, 3),   # Children's Day Observed
    date(2026, 4, 4),   # Children's Day
    date(2026, 4, 5),   # Tomb Sweeping Day
    date(2026, 4, 6),   # Tomb Sweeping Holiday
    date(2026, 5, 1),   # Labor Day
    date(2026, 6, 19),  # Dragon Boat Festival
    date(2026, 10, 3),  # Mid-Autumn Festival
    date(2026, 10, 10), # Double Ten Day
]

# US market holidays 2026
US_HOLIDAYS_2026 = [
    date(2026, 1, 1),   # New Year's Day
    date(2026, 1, 19),  # Martin Luther King Jr. Day
    date(2026, 2, 16),  # Presidents Day
    date(2026, 4, 3),   # Good Friday
    date(2026, 5, 25),  # Memorial Day
    date(2026, 6, 19),  # Juneteenth
    date(2026, 7, 3),   # Independence Day (observed)
    date(2026, 9, 7),   # Labor Day
    date(2026, 11, 26), # Thanksgiving Day
    date(2026, 12, 25), # Christmas Day
]

# Seasonal strength by month (January effect, Sell in May, etc.)
SEASONAL_STRENGTH = {
    1: 0.6,   # January effect
    2: 0.3,   # Post-January momentum
    3: 0.2,   # Quarter end
    4: 0.4,   # Pre-earnings
    5: -0.3,  # Sell in May
    6: -0.4,  # Summer doldrums
    7: -0.2,  # Summer continues
    8: -0.1,  # Late summer
    9: -0.5,  # Historically worst month
    10: 0.2,  # Recovery
    11: 0.5,  # Pre-holiday rally
    12: 0.6,  # Santa Claus rally
}


def get_holidays(country: str, year: int) -> list[dict]:
    """
    Get market holidays for a country and year.
    
    Args:
        country: Country code (TW, US, etc.)
        year: Year to get holidays for
        
    Returns:
        List of holiday dictionaries with date, name, and country
    """
    holidays = []
    
    if country.upper() == "TW":
        if year == 2026:
            holiday_dates = TAIWAN_HOLIDAYS_2026
            names = [
                "New Year's Day", "New Year Holiday",
                "CNY Eve Observance", "Chinese New Year Eve",
                "Chinese New Year Day 1", "Chinese New Year Day 2",
                "Chinese New Year Day 3", "Chinese New Year Holiday",
                "Peace Memorial Day",
                "Children's Day Observed", "Children's Day",
                "Tomb Sweeping Day", "Tomb Sweeping Holiday",
                "Labor Day", "Dragon Boat Festival",
                "Mid-Autumn Festival", "Double Ten Day"
            ]
            for i, d in enumerate(holiday_dates):
                holidays.append({
                    "date": d.isoformat(),
                    "name": names[i] if i < len(names) else f"Holiday {i+1}",
                    "country": "TW"
                })
        else:
            # Generate basic holidays for other years
            for month, day in TAIWAN_FIXED_HOLIDAYS:
                try:
                    d = date(year, month, day)
                    holidays.append({
                        "date": d.isoformat(),
                        "name": f"Taiwan Holiday",
                        "country": "TW"
                    })
                except ValueError:
                    pass
                    
    elif country.upper() == "US":
        if year == 2026:
            names = [
                "New Year's Day", "MLK Day", "Presidents Day",
                "Good Friday", "Memorial Day", "Juneteenth",
                "Independence Day", "Labor Day",
                "Thanksgiving", "Christmas"
            ]
            for i, d in enumerate(US_HOLIDAYS_2026):
                holidays.append({
                    "date": d.isoformat(),
                    "name": names[i] if i < len(names) else f"Holiday {i+1}",
                    "country": "US"
                })
    
    return holidays


def is_holiday(check_date: date, country: str = "TW") -> bool:
    """
    Check if a date is a market holiday.
    
    Args:
        check_date: Date to check
        country: Country code
        
    Returns:
        True if holiday, False if trading day
    """
    # Weekend check
    if check_date.weekday() >= 5:
        return True
    
    # Get holidays for the year
    holidays_data = get_holidays(country, check_date.year)
    holiday_dates = [date.fromisoformat(h["date"]) for h in holidays_data]
    
    return check_date in holiday_dates


def is_trading_day(check_date: date, country: str = "TW") -> bool:
    """Check if a date is a trading day."""
    return not is_holiday(check_date, country)


def get_next_trading_day(from_date: date, country: str = "TW") -> date:
    """Get the next trading day after a date."""
    next_day = from_date + timedelta(days=1)
    max_iterations = 30
    while is_holiday(next_day, country) and max_iterations > 0:
        next_day += timedelta(days=1)
        max_iterations -= 1
    return next_day


def get_previous_trading_day(from_date: date, country: str = "TW") -> date:
    """Get the previous trading day before a date."""
    prev_day = from_date - timedelta(days=1)
    max_iterations = 30
    while is_holiday(prev_day, country) and max_iterations > 0:
        prev_day -= timedelta(days=1)
        max_iterations -= 1
    return prev_day


def get_trading_days(start_date: date, end_date: date, country: str = "TW") -> list[str]:
    """Get all trading days between two dates."""
    trading_days = []
    current = start_date
    while current <= end_date:
        if is_trading_day(current, country):
            trading_days.append(current.isoformat())
        current += timedelta(days=1)
    return trading_days


def count_trading_days(start_date: date, end_date: date, country: str = "TW") -> int:
    """Count trading days between two dates."""
    return len(get_trading_days(start_date, end_date, country))


def get_futures_expiration(year: int, month: int) -> date:
    """
    Calculate Taiwan futures settlement date.
    Rule: Third Wednesday of each month.
    
    Args:
        year: Year
        month: Month (1-12)
        
    Returns:
        Futures expiration date
    """
    # Find first day of month
    first_day = date(year, month, 1)
    
    # Find first Wednesday
    days_to_wed = (2 - first_day.weekday()) % 7  # Wednesday is 2
    first_wed = first_day + timedelta(days=days_to_wed)
    
    # Third Wednesday
    third_wed = first_wed + timedelta(days=14)
    
    # If holiday, move to previous trading day
    while is_holiday(third_wed, "TW"):
        third_wed = get_previous_trading_day(third_wed, "TW")
    
    return third_wed


def get_next_futures_expiration(from_date: Optional[date] = None) -> dict:
    """
    Get the next futures expiration date.
    
    Args:
        from_date: Start date (default: today)
        
    Returns:
        Dictionary with expiration date and days until
    """
    if from_date is None:
        from_date = date.today()
    
    # Check current month's expiration
    exp_date = get_futures_expiration(from_date.year, from_date.month)
    
    if from_date > exp_date:
        # Move to next month
        if from_date.month == 12:
            exp_date = get_futures_expiration(from_date.year + 1, 1)
        else:
            exp_date = get_futures_expiration(from_date.year, from_date.month + 1)
    
    days_until = (exp_date - from_date).days
    
    return {
        "expiration_date": exp_date.isoformat(),
        "days_until": days_until,
        "is_settlement_day": from_date == exp_date,
        "is_settlement_week": 0 <= days_until <= 3
    }


def get_futures_expirations(year: int) -> list[dict]:
    """
    Get all futures expiration dates for a year.
    
    Args:
        year: Year to get expirations for
        
    Returns:
        List of expiration date dictionaries
    """
    expirations = []
    for month in range(1, 13):
        exp_date = get_futures_expiration(year, month)
        expirations.append({
            "month": month,
            "expiration_date": exp_date.isoformat(),
            "month_name": calendar.month_abbr[month]
        })
    return expirations


def get_seasonal_strength(month: Optional[int] = None) -> dict:
    """
    Get seasonal strength indicator for a month.
    
    Args:
        month: Month (1-12), default: current month
        
    Returns:
        Dictionary with strength value and classification
    """
    if month is None:
        month = date.today().month
    
    strength = SEASONAL_STRENGTH.get(month, 0.0)
    
    if strength > 0.3:
        classification = "STRONG"
    elif strength < -0.2:
        classification = "WEAK"
    else:
        classification = "NEUTRAL"
    
    return {
        "month": month,
        "month_name": calendar.month_name[month],
        "strength": strength,
        "classification": classification,
        "is_strong_month": strength > 0.3,
        "is_weak_month": strength < -0.2
    }


def get_all_seasonal_strength() -> list[dict]:
    """Get seasonal strength for all months."""
    return [get_seasonal_strength(m) for m in range(1, 13)]


def get_event_risk_level(check_date: date) -> dict:
    """
    Get event risk level for a date.
    
    Args:
        check_date: Date to check
        
    Returns:
        Dictionary with risk level and recommendation
    """
    # Check if holiday
    if is_holiday(check_date, "TW"):
        return {
            "date": check_date.isoformat(),
            "risk_level": 1.0,
            "reason": "Market holiday",
            "position_multiplier": 0.0
        }
    
    # Check if settlement day
    exp_info = get_next_futures_expiration(check_date)
    if exp_info["is_settlement_day"]:
        return {
            "date": check_date.isoformat(),
            "risk_level": 0.7,
            "reason": "Futures settlement day",
            "position_multiplier": 0.3
        }
    
    # Check if settlement week
    if exp_info["is_settlement_week"]:
        days_to_exp = exp_info["days_until"]
        risk = 0.5 - (days_to_exp * 0.1)  # Risk increases as expiration approaches
        return {
            "date": check_date.isoformat(),
            "risk_level": max(0.3, risk),
            "reason": f"Settlement week ({days_to_exp} days to expiration)",
            "position_multiplier": 1.0 - max(0.3, risk)
        }
    
    # Default: low risk
    return {
        "date": check_date.isoformat(),
        "risk_level": 0.0,
        "reason": "Normal trading day",
        "position_multiplier": 1.0
    }


def get_market_calendar_summary(from_date: Optional[date] = None, days: int = 30) -> dict:
    """
    Get comprehensive market calendar summary.
    
    Args:
        from_date: Start date (default: today)
        days: Number of days to look ahead
        
    Returns:
        Dictionary with calendar summary
    """
    if from_date is None:
        from_date = date.today()
    
    end_date = from_date + timedelta(days=days)
    
    # Get holidays in range
    holidays_tw = get_holidays("TW", from_date.year)
    upcoming_holidays = [
        h for h in holidays_tw 
        if from_date <= date.fromisoformat(h["date"]) <= end_date
    ]
    
    # Get trading days count
    trading_days_count = count_trading_days(from_date, end_date)
    
    # Get futures info
    futures_info = get_next_futures_expiration(from_date)
    
    # Get seasonal info
    seasonal_info = get_seasonal_strength()
    
    return {
        "start_date": from_date.isoformat(),
        "end_date": end_date.isoformat(),
        "today": date.today().isoformat(),
        "is_trading_day": is_trading_day(from_date, "TW"),
        "trading_days_in_period": trading_days_count,
        "calendar_days_in_period": days,
        "upcoming_holidays": upcoming_holidays,
        "holiday_count": len(upcoming_holidays),
        "next_futures_expiration": futures_info,
        "seasonal": seasonal_info,
        "current_risk": get_event_risk_level(from_date)
    }
