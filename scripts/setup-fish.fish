#!/usr/bin/env fish
###############################################################################
# MTXF Bot - Fish Shell Setup Script
# Run once to install all dependencies
###############################################################################

echo "🎯 MTXF Lunch Bot - Fish Shell Setup"
echo "====================================="
echo ""

set -l GREEN '\033[0;32m'
set -l RED '\033[0;31m'
set -l NC '\033[0m'

# Step 1: Install dependencies
echo "Step 1: Installing dependencies..."
if not command -v brew > /dev/null
    echo -e "$RED❌ Homebrew not found. Install from https://brew.sh$NC"
    exit 1
end

echo "Installing Java 21..."
if not brew list openjdk@21 > /dev/null 2>&1
    brew install openjdk@21
end

echo "Installing Maven..."
if not brew list maven > /dev/null 2>&1
    brew install maven
end

echo "Installing Ollama..."
if not brew list ollama > /dev/null 2>&1
    brew install ollama
end

# Step 2: Setup Ollama
echo ""
echo "Step 2: Setting up Ollama + Llama 3.1 8B..."
if not pgrep -x "ollama" > /dev/null
    echo "Starting Ollama service..."
    brew services start ollama
    sleep 3
end

echo "Downloading Llama 3.1 8B (this may take 5-10 minutes)..."
ollama pull llama3.1:8b-instruct-q5_K_M

# Step 3: Setup Python environment (Fish-aware)
echo ""
echo "Step 3: Setting up Python environment..."
cd python
python3 -m venv venv
source venv/bin/activate.fish  # Fish-specific
pip install --upgrade pip
pip install -r requirements.txt
cd ..

# Step 4: Configuration checklist
echo ""
echo -e "$GREEN✅ Setup complete!$NC"
echo ""
echo "📋 Next steps:"
echo "1. Edit src/main/resources/application.yml:"
echo "   - Add Shioaji API key & secret"
echo "   - Add Telegram bot token & chat ID"
echo "   - Set ca-password to your National ID (身分證字號)"
echo "   - Keep simulation: true for paper trading"
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
echo "4. Run the bot:"
echo "   fish start-lunch-bot.fish"
echo ""
echo "📚 Read docs/DEPLOYMENT.md for detailed instructions"
