#!/bin/bash
###############################################################################
# MTXF Bot - Pre-Flight Verification
# Run this to verify all files are present and ready
###############################################################################

echo "🔍 MTXF Lunch Bot - Pre-Flight Verification"
echo "==========================================="
echo ""

ERRORS=0

# Check core files
check_file() {
    if [ -f "$1" ]; then
        echo "✅ $1"
    else
        echo "❌ MISSING: $1"
        ((ERRORS++))
    fi
}

echo "📦 Core Application Files:"
check_file "pom.xml"
check_file "src/main/java/tw/gc/mtxfbot/MtxfBotApplication.java"
check_file "src/main/java/tw/gc/mtxfbot/TradingEngine.java"
check_file "src/main/java/tw/gc/mtxfbot/TelegramService.java"
check_file "src/main/java/tw/gc/mtxfbot/AppConfig.java"
check_file "src/main/resources/application.yml"
echo ""

echo "🐍 Python Bridge:"
check_file "python/bridge.py"
check_file "python/requirements.txt"
echo ""

echo "🚀 Scripts:"
check_file "scripts/setup.sh"
check_file "scripts/start-lunch-bot.sh"
check_file "scripts/test-paper-trading.sh"
check_file "scripts/tw.gc.mtxfbot.plist"
echo ""

echo "📚 Documentation:"
check_file "README.md"
check_file "QUICKSTART.md"
check_file "ARCHITECTURE.md"
check_file "SUMMARY.txt"
check_file "docs/DEPLOYMENT.md"
check_file "docs/STRATEGY.md"
check_file "docs/CHECKLIST.md"
check_file "docs/MANIFEST.md"
echo ""

# Check executability
echo "🔐 Script Permissions:"
if [ -x "scripts/setup.sh" ]; then
    echo "✅ setup.sh is executable"
else
    echo "⚠️  setup.sh not executable (will auto-fix)"
    chmod +x scripts/setup.sh
fi

if [ -x "scripts/start-lunch-bot.sh" ]; then
    echo "✅ start-lunch-bot.sh is executable"
else
    echo "⚠️  start-lunch-bot.sh not executable (will auto-fix)"
    chmod +x scripts/start-lunch-bot.sh
fi

if [ -x "scripts/test-paper-trading.sh" ]; then
    echo "✅ test-paper-trading.sh is executable"
else
    echo "⚠️  test-paper-trading.sh not executable (will auto-fix)"
    chmod +x scripts/test-paper-trading.sh
fi
echo ""

# Check configuration
echo "⚙️  Configuration Check:"
if grep -q "YOUR_SHIOAJI_API_KEY" src/main/resources/application.yml; then
    echo "⚠️  application.yml contains placeholder credentials"
    echo "   → Edit src/main/resources/application.yml before running"
else
    echo "✅ application.yml appears configured"
fi

if grep -q "YOUR_TELEGRAM_BOT_TOKEN" src/main/resources/application.yml; then
    echo "⚠️  Telegram credentials not set"
else
    echo "✅ Telegram credentials configured"
fi

if grep -q "simulation: true" src/main/resources/application.yml; then
    echo "✅ Paper trading mode enabled (safe)"
else
    echo "⚠️  WARNING: Live trading mode enabled!"
fi
echo ""

# Summary
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ $ERRORS -eq 0 ]; then
    echo "✅ All files present and ready!"
    echo ""
    echo "Next steps:"
    echo "1. Edit src/main/resources/application.yml"
    echo "2. Run ./scripts/setup.sh"
    echo "3. Test with ./scripts/test-paper-trading.sh"
    echo ""
    echo "📚 Read QUICKSTART.md for detailed instructions"
else
    echo "❌ $ERRORS file(s) missing!"
    echo "Please check the project structure"
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
