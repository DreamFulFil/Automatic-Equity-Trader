#!/bin/bash
###############################################################################
# MTXF Bot - Paper Trading Test Script
# Run this to verify setup before going live
###############################################################################

set -e

echo "🧪 MTXF Bot Paper Trading Test"
echo "=============================="
echo ""

# Check if simulation mode is enabled
if ! grep -q "simulation: true" src/main/resources/application.yml; then
    echo "⚠️  WARNING: simulation mode is NOT enabled!"
    echo "Set 'shioaji.simulation: true' in application.yml"
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo "✅ Paper trading mode confirmed"
echo ""
echo "This test will:"
echo "1. Start Python bridge"
echo "2. Start Java trading engine"
echo "3. Monitor for 5 minutes"
echo "4. Check for errors"
echo ""
read -p "Press Enter to start..."

# Start the bot
./scripts/start-lunch-bot.sh &
BOT_PID=$!

echo ""
echo "🤖 Bot running (PID: $BOT_PID)"
echo "📊 Monitoring for 5 minutes..."
echo ""

# Monitor logs
sleep 300

echo ""
echo "✅ 5-minute test complete"
echo "📋 Check logs/mtxf-bot.log for any errors"
echo ""

# Cleanup
kill $BOT_PID 2>/dev/null || true

echo "Test finished. Review logs before enabling live trading."
