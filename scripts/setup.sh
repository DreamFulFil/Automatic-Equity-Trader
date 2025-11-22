#!/bin/bash
###############################################################################
# Quick Setup Guide - Run this first
###############################################################################

echo "🎯 MTXF Lunch Bot - Quick Setup"
echo "================================"
echo ""

# Step 1: Install dependencies
echo "Step 1: Installing dependencies..."
if ! command -v brew &> /dev/null; then
    echo "❌ Homebrew not found. Install from https://brew.sh"
    exit 1
fi

echo "Installing Java 21..."
brew list openjdk@21 &>/dev/null || brew install openjdk@21

echo "Installing Maven..."
brew list maven &>/dev/null || brew install maven

echo "Installing Ollama..."
brew list ollama &>/dev/null || brew install ollama

# Step 2: Setup Ollama
echo ""
echo "Step 2: Setting up Ollama + Llama 3.1 8B..."
if ! pgrep -x "ollama" > /dev/null; then
    echo "Starting Ollama service..."
    brew services start ollama
    sleep 3
fi

echo "Downloading Llama 3.1 8B (this may take 5-10 minutes)..."
ollama pull llama3.1:8b-instruct-q5_K_M

# Step 3: Setup Python environment
echo ""
echo "Step 3: Setting up Python environment..."
cd python
python3 -m venv venv
source venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
cd ..

# Step 4: Configuration checklist
echo ""
echo "✅ Setup complete!"
echo ""
echo "📋 Next steps:"
echo "1. Edit src/main/resources/application.yml with your credentials:"
echo "   - Shioaji API key & secret"
echo "   - Telegram bot token & chat ID"
echo "   - Set simulation: true for paper trading"
echo ""
echo "2. Get Telegram bot token:"
echo "   - Message @BotFather on Telegram"
echo "   - Create new bot with /newbot"
echo "   - Copy token to application.yml"
echo ""
echo "3. Get Telegram chat ID:"
echo "   - Send message to your bot"
echo "   - Visit: https://api.telegram.org/bot<TOKEN>/getUpdates"
echo "   - Copy chat.id to application.yml"
echo ""
echo "4. Test in paper trading mode:"
echo "   ./scripts/test-paper-trading.sh"
echo ""
echo "5. Run the bot:"
echo "   ./scripts/start-lunch-bot.sh"
echo ""
echo "📚 Read docs/DEPLOYMENT.md for detailed instructions"
