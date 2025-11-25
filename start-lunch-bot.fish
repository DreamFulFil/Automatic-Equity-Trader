#!/usr/bin/env fish
###############################################################################
# MTXF Lunch Bot Startup Script (Fish Shell)
# Usage: fish start-lunch-bot.fish <jasypt-secret>
###############################################################################

# Check for required secret parameter
if test (count $argv) -lt 1
    echo "❌ Usage: fish start-lunch-bot.fish <jasypt-secret>"
    echo "   The secret is required to decrypt application properties."
    exit 1
end

set JASYPT_SECRET $argv[1]

set BOT_DIR (dirname (status --current-filename))
cd $BOT_DIR

echo "🚀 MTXF Lunch Bot Launcher (Fish Shell)"
echo "========================================"
echo "Directory: $BOT_DIR"
echo ""

# Colors for Fish
set -l GREEN '\033[0;32m'
set -l RED '\033[0;31m'
set -l YELLOW '\033[1;33m'
set -l NC '\033[0m' # No Color

# Step 1: Check Java 21
echo "1️⃣ Checking Java 21..."
if not java -version 2>&1 | grep -q "version \"21"
    echo -e "$RED❌ Java 21 not found!$NC"
    echo "Install via: brew install openjdk@21"
    exit 1
end
echo -e "$GREEN✅ Java 21 detected$NC"

# Step 2: Check Python 3.10+
echo "2️⃣ Checking Python..."
if not command -v python3 > /dev/null
    echo -e "$RED❌ Python3 not found!$NC"
    exit 1
end
set PYTHON_VERSION (python3 --version | cut -d' ' -f2 | cut -d'.' -f1,2)
echo -e "$GREEN✅ Python $PYTHON_VERSION detected$NC"

# Step 3: Setup Python venv (Fish-specific)
if not test -d "python/venv"
    echo "3️⃣ Creating Python virtual environment..."
    cd python
    python3 -m venv venv
    source venv/bin/activate.fish  # Fish-specific activation
    pip install --upgrade pip --quiet
    pip install -r requirements.txt --quiet
    cd ..
else
    echo -e "$GREEN3️⃣ Python venv exists$NC"
end

# Step 4: Check Ollama
echo "4️⃣ Checking Ollama..."
if not command -v ollama > /dev/null
    echo -e "$RED❌ Ollama not found!$NC"
    echo "Install via: brew install ollama"
    exit 1
end

# Check if Ollama service is running
if not pgrep -x "ollama" > /dev/null
    echo "Starting Ollama service..."
    ollama serve > /dev/null 2>&1 &
    sleep 2
end

if not curl -s http://localhost:11434/api/tags | grep -q "llama3.1"
    echo "📥 Downloading Llama 3.1 8B (q5_K_M)..."
    ollama pull llama3.1:8b-instruct-q5_K_M
end
echo -e "$GREEN✅ Ollama + Llama 3.1 ready$NC"

# Step 5: Build Java app
echo "5️⃣ Building Java application..."
if not test -f "target/mtxf-bot-1.0.0.jar"
    mvn clean package -DskipTests --quiet
end
echo -e "$GREEN✅ Java app built$NC"

# Step 6: Create logs directory
mkdir -p logs

# Step 7: Start Python bridge (Fish background job)
echo "6️⃣ Starting Python bridge..."
cd python
source venv/bin/activate.fish  # Fish activation
nohup python3 bridge.py > ../logs/python-bridge.log 2>&1 &
set PYTHON_PID $last_pid
echo -e "$GREEN✅ Python bridge started (PID: $PYTHON_PID)$NC"
cd ..

# Wait for bridge to be ready (more reliable)
echo "Waiting for Python bridge..."
set -l attempts 0
while test $attempts -lt 30
    if curl -f -s http://localhost:8888/health > /dev/null 2>&1
        echo -e "$GREENPython bridge ready$NC"
        break
    end
    set attempts (math $attempts + 1)
    sleep 1
end

# Final check — if still not ready, show error but continue
if test $attempts -eq 30
    echo -e "$REDWarning: Python bridge not ready after 30s, starting Java anyway...$NC"
end

# Step 8: Start Java trading engine
echo "7️⃣ Starting Java trading engine..."
echo ""
echo -e "$YELLOW📊 Bot is running! Press Ctrl+C to stop.$NC"
echo -e "$YELLOW📱 Check your Telegram for alerts.$NC"
echo ""

java -jar target/mtxf-bot-1.0.0.jar --jasypt.encryptor.password="$JASYPT_SECRET"

# Cleanup on exit (Fish trap)
function cleanup --on-signal INT --on-signal TERM
    echo ""
    echo "🛑 Shutting down..."
    kill $PYTHON_PID 2>/dev/null
    exit
end
