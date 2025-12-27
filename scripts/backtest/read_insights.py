#!/usr/bin/env python3
"""
Script to read trading insights from yesterday's database records.
"""

import psycopg2
from datetime import datetime, timedelta
import sys
import os

def setup_database():
    """Setup the lunchbot database and user if they don't exist"""

    # Connect as postgres user to setup
    setup_config = {
        'host': 'localhost',
        'port': 5432,
        'database': 'postgres',
        'user': 'postgres',
        'password': 'dreamfulfil'
    }

    try:
        conn = psycopg2.connect(**setup_config)
        conn.autocommit = True
        cursor = conn.cursor()

        # Check if lunchbotuser exists
        cursor.execute("SELECT 1 FROM pg_roles WHERE rolname = 'lunchbotuser'")
        user_exists = cursor.fetchone()

        if not user_exists:
            print("🔧 Creating lunchbotuser...")
            cursor.execute("CREATE USER lunchbotuser WITH PASSWORD 'WSYS1r0PE0Ig0iuNX2aNi5k7' LOGIN")
            print("✅ User created")
        else:
            print("🔧 Resetting lunchbotuser password...")
            cursor.execute("ALTER USER lunchbotuser PASSWORD 'WSYS1r0PE0Ig0iuNX2aNi5k7'")
            print("✅ Password reset")

        # Check if lunchbot database exists
        cursor.execute("SELECT 1 FROM pg_database WHERE datname = 'lunchbot'")
        db_exists = cursor.fetchone()

        if not db_exists:
            print("🔧 Creating lunchbot database...")
            cursor.execute("CREATE DATABASE lunchbot OWNER lunchbotuser")
            print("✅ Database created")
        else:
            print("✅ Database lunchbot already exists")

        # Grant privileges
        cursor.execute("GRANT ALL PRIVILEGES ON DATABASE lunchbot TO lunchbotuser")
        print("✅ Privileges granted")

        cursor.close()
        conn.close()

    except psycopg2.Error as e:
        print(f"❌ Setup error: {e}")
        return False

    return True

def get_yesterday_insights():
    """Connect to database and retrieve insights from yesterday"""

    # First setup database if needed
    if not setup_database():
        print("❌ Failed to setup database")
        return

    # Database connection parameters
    db_config = {
        'host': 'localhost',
        'port': 5432,
        'database': 'lunchbot',
        'user': 'lunchbotuser',
        'password': 'WSYS1r0PE0Ig0iuNX2aNi5k7'  # Decrypted Jasypt password
    }

    # Calculate yesterday's date
    yesterday = datetime.now() - timedelta(days=1)
    yesterday_date = yesterday.date()

    try:
        # Connect to database
        conn = psycopg2.connect(**db_config)
        cursor = conn.cursor()

        # Check if daily_statistics table exists and show some info
        cursor.execute("""
            SELECT EXISTS (
                SELECT FROM information_schema.tables
                WHERE table_name = 'daily_statistics'
            )
        """)
        table_exists = cursor.fetchone()[0]

        if not table_exists:
            print("❌ daily_statistics table doesn't exist")
            cursor.close()
            conn.close()
            return

        # Check total records in table
        cursor.execute("SELECT COUNT(*) FROM daily_statistics")
        total_records = cursor.fetchone()[0]
        print(f"📊 Total records in daily_statistics: {total_records}")

        if total_records > 0:
            # Check date range
            cursor.execute("SELECT MIN(trade_date), MAX(trade_date) FROM daily_statistics")
            date_range = cursor.fetchone()
            if date_range[0]:
                print(f"📅 Date range: {date_range[0]} to {date_range[1]}")

            # Check records with insights
            cursor.execute("SELECT COUNT(*) FROM daily_statistics WHERE llama_insight IS NOT NULL")
            insight_records = cursor.fetchone()[0]
            print(f"💡 Records with insights: {insight_records}")

            # Show recent records (last 5)
            cursor.execute("""
                SELECT trade_date, symbol, trading_mode, llama_insight IS NOT NULL as has_insight
                FROM daily_statistics
                ORDER BY trade_date DESC, insight_generated_at DESC
                LIMIT 5
            """)
            recent_records = cursor.fetchall()
            if recent_records:
                print("📋 Recent records:")
                for record in recent_records:
                    trade_date, symbol, mode, has_insight = record
                    insight_status = "✅" if has_insight else "❌"
                    print(f"  {trade_date} | {symbol} | {mode} | Insight: {insight_status}")
        else:
            print("📭 No trading data found in database at all")
            print("🤔 This suggests the trading bot hasn't run successfully yet")

        # Query for insights from yesterday
        query = """
        SELECT trade_date, symbol, llama_insight, insight_generated_at, trading_mode
        FROM daily_statistics
        WHERE trade_date = %s AND llama_insight IS NOT NULL
        ORDER BY insight_generated_at DESC
        """

        cursor.execute(query, (yesterday_date,))
        results = cursor.fetchall()

        if not results:
            print(f"📭 No insights found for {yesterday_date}")
            cursor.close()
            conn.close()
            return

        print(f"📊 Trading Insights for {yesterday_date}")
        print("=" * 50)

        for row in results:
            trade_date, symbol, insight, generated_at, trading_mode = row

            print(f"\n🏷️  Symbol: {symbol}")
            print(f"📅 Trade Date: {trade_date}")
            print(f"🤖 Trading Mode: {trading_mode}")
            print(f"⏰ Generated At: {generated_at}")
            print(f"💡 Insight:\n{insight}")
            print("-" * 50)

        cursor.close()
        conn.close()

    except psycopg2.Error as e:
        print(f"❌ Database error: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    get_yesterday_insights()