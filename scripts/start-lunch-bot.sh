#!/bin/bash
###############################################################################
# MTXF Lunch Bot Startup Script
# macOS Apple Silicon (M1/M2/M3) - One-click launch
###############################################################################

set -e

BOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$BOT_DIR"

echo "🚀 MTXF Lunch Bot Launcher"
echo "=========================="
echo "Directory: $BOT_DIR"
echo ""

# Step 1: Check Java 21
echo "1️⃣ Checking Java 21..."
if ! java -version 2>&1 | grep -q "version \"21"; then
    echo "❌ Java 21 not found!"
    echo "Install via: brew install openjdk@21"
    exit 1
fi
echo "✅ Java 21 detected"

# Step 2: Check Python 3.10+
echo "2️⃣ Checking Python..."
if ! command -v python3 &> /dev/null; then
    echo "❌ Python3 not found!"
    exit 1
fi
PYTHON_VERSION=$(python3 --version | cut -d' ' -f2 | cut -d'.' -f1,2)
echo "✅ Python $PYTHON_VERSION detected"

# Step 3: Setup Python venv
if [ ! -d "python/venv" ]; then
    echo "3️⃣ Creating Python virtual environment..."
    cd python
    python3 -m venv venv
    source venv/bin/activate
    pip install --upgrade pip
    pip install -r requirements.txt
    cd ..
else
    echo "3️⃣ Python venv exists"
fi

# Step 4: Check Ollama
echo "4️⃣ Checking Ollama..."
if ! command -v ollama &> /dev/null; then
    echo "❌ Ollama not found!"
    echo "Install via: brew install ollama"
    exit 1
fi

if ! curl -s http://localhost:11434/api/tags | grep -q "llama3.1"; then
    echo "📥 Downloading Llama 3.1 8B (q5_K_M)..."
    ollama pull llama3.1:8b-instruct-q5_K_M
fi
echo "✅ Ollama + Llama 3.1 ready"

# Step 5: Build Java app
echo "5️⃣ Building Java application..."
if [ ! -f "target/mtxf-bot-1.0.0.jar" ]; then
    mvn clean package -DskipTests
fi
echo "✅ Java app built"

# Step 6: Create logs directory
mkdir -p logs

# Step 7: Start Python bridge
echo "6️⃣ Starting Python bridge..."
cd python
source venv/bin/activate
nohup python3 bridge.py > ../logs/python-bridge.log 2>&1 &
PYTHON_PID=$!
echo "✅ Python bridge started (PID: $PYTHON_PID)"
cd ..

# Wait for bridge to be ready
echo "⏳ Waiting for Python bridge..."
for i in {1..30}; do
    if curl -s http://localhost:8888/health > /dev/null 2>&1; then
        echo "✅ Python bridge ready"
        break
    fi
    sleep 1
done

# Step 8: Start Java trading engine
echo "7️⃣ Starting Java trading engine..."
java -jar target/mtxf-bot-1.0.0.jar

# Cleanup on exit
trap "echo '🛑 Shutting down...'; kill $PYTHON_PID 2>/dev/null; exit" INT TERM

wait
