#!/usr/bin/env fish
###############################################################################
# Automatic Equity Trader Startup Script (Fish Shell)
# Usage: fish start-auto-trader.fish <jasypt-secret> [mode]
#
# Arguments:
#   jasypt-secret  (required) - Jasypt encryption password
#   mode           (optional) - "stock" (default) or "futures"
#
# Examples:
#   ./start-auto-trader.fish secret123            → stock mode (2330.TW odd lots)
#   ./start-auto-trader.fish secret123 futures    → futures mode (MTXF)
###############################################################################

# --- ROBUST Cleanup: Only use POSIX-compliant kill commands ---
echo "🧹 Cleaning up leftover processes from previous runs..."

# Kill Python bridge processes (ROBUST: verify PID exists before killing)
for pid in (pgrep -f "python.*bridge.py" 2>/dev/null || echo "")
    if test -n "$pid"
        echo "  Killing old Python bridge process: $pid"
        /bin/kill -9 $pid 2>/dev/null || true
    end
end

# Kill Ollama server processes (ROBUST: verify PID exists)
for pid in (pgrep -x ollama 2>/dev/null || echo "")
    if test -n "$pid"
        echo "  Killing old Ollama process: $pid"
        /bin/kill -9 $pid 2>/dev/null || true
    end
end

# Kill any leftover Java MTXF processes
for pid in (pgrep -f "java.*auto-equity-trader" 2>/dev/null || echo "")
    if test -n "$pid"
        echo "  Killing old Java bot process: $pid"
        /bin/kill -9 $pid 2>/dev/null || true
    end
end

echo "🧹 Cleanup complete."
# --- End Cleanup ---

# Set a higher resource limit for file descriptors
ulimit -n 16384
echo "🔧 Set file descriptor limit to 16384."

# Check for required secret parameter
if test (count $argv) -lt 1
    echo "❌ Usage: fish start-auto-trader.fish <jasypt-secret> [mode]"
    echo "   jasypt-secret  - Required: Jasypt encryption password"
    echo "   mode           - Optional: 'stock' (default) or 'futures'"
    echo ""
    echo "Examples:"
    echo "   ./start-auto-trader.fish secret123            → stock mode (2330.TW odd lots)"
    echo "   ./start-auto-trader.fish secret123 futures    → futures mode (MTXF)"
    exit 1
end

set JASYPT_SECRET $argv[1]

# Parse trading mode (default: stock)
set TRADING_MODE "stock"
if test (count $argv) -ge 2
    if test "$argv[2]" = "futures"
        set TRADING_MODE "futures"
    else if test "$argv[2]" = "stock"
        set TRADING_MODE "stock"
    else
        echo "❌ Invalid mode: $argv[2]. Use 'stock' or 'futures'."
        exit 1
    end
end

echo "📈 Trading Mode: $TRADING_MODE"

set BOT_DIR (cd (dirname (status --current-filename)) && pwd)
cd $BOT_DIR

# Use jenv for Java version management (ROBUST: works in cron + CI + local)
set -x PATH $HOME/.jenv/shims $PATH
if command -v jenv > /dev/null
    # Use jenv exec for all Java commands (most stable)
    set -x JAVA_HOME (jenv javahome)
    # Verify Java 21 is active
    if not jenv version | grep -q "21.0"
        echo "⚠️ Warning: jenv not using Java 21, setting explicitly..."
        jenv local 21.0 > /dev/null 2>&1 || true
        set -x JAVA_HOME (jenv javahome)
    end
else
    # Fallback to direct JAVA_HOME (for CI or systems without jenv)
    if test -d /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
        set -x JAVA_HOME /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
    else if test -d /usr/lib/jvm/java-21-openjdk-amd64
        set -x JAVA_HOME /usr/lib/jvm/java-21-openjdk-amd64
    else
        echo "❌ Java 21 not found! Install via: brew install openjdk@21"
        exit 1
    end
end

# Ensure Java is in PATH
set -x PATH $JAVA_HOME/bin $PATH

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║     🤖 Automatic Equity Trader - Taiwan Stock Trading System    ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  Directory: $BOT_DIR                                           ║"
echo "║  Mode:      $TRADING_MODE                                      ║"
echo "║  Started:   "(date '+%Y-%m-%d %H:%M:%S')"                      ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Colors for Fish
set -l GREEN '\033[0;32m'
set -l RED '\033[0;31m'
set -l YELLOW '\033[1;33m'
set -l NC '\033[0m' # No Color

# Step 1: Check Java 21 (ROBUST: works with or without jenv)
echo "1️⃣ Checking Java 21..."
set JAVA_CMD "java"
if command -v jenv > /dev/null
    # Use jenv exec for stability in cron
    set JAVA_CMD "jenv exec java"
end

if not eval $JAVA_CMD -version 2>&1 | grep -q "version \"21"
    echo -e "$RED❌ Java 21 not found!$NC"
    echo "Install via: brew install openjdk@21"
    echo "Current Java version:"
    eval $JAVA_CMD -version
    exit 1
end
echo -e "$GREEN✅ Java 21 detected (using: $JAVA_CMD)$NC"

# Step 2: Force Python 3.12 via brew (skip pyenv)
echo "2️⃣ Installing Python 3.12 via brew..."

# Install Python 3.12 directly via brew
if not command -v python3.12 > /dev/null
    echo "Installing python@3.12..."
    brew install python@3.12
end

# Verify Python 3.12 is available
if command -v python3.12 > /dev/null
    echo "Python 3.12: "(python3.12 --version)
    echo "Python 3.12 path: "(which python3.12)
else
    echo -e "$RED❌ Python 3.12 not found after install!$NC"
    exit 1
end
echo -e "$GREEN✅ Python 3.12 ready$NC"

# Step 3: Force rebuild venv with Python 3.12
echo "3️⃣ Creating venv with Python 3.12..."
cd python
rm -rf venv

# Create venv with explicit Python 3.12
echo "Creating venv with python3.12..."
python3.12 -m venv venv
source venv/bin/activate.fish
echo "Venv Python: "(python --version)
echo "Venv path: "(which python)
pip install --upgrade pip --quiet
pip install -r requirements.txt --quiet
cd ..
echo -e "$GREEN✅ Venv created with Python 3.12$NC"

# Step 4: Check Ollama
echo "4️⃣ Checking Ollama..."

# Try common Ollama paths (cron doesn't have full PATH)
set OLLAMA_BIN ""
if command -v ollama > /dev/null
    set OLLAMA_BIN ollama
else if test -x /usr/local/bin/ollama
    set OLLAMA_BIN /usr/local/bin/ollama
else if test -x /opt/homebrew/bin/ollama
    set OLLAMA_BIN /opt/homebrew/bin/ollama
end

if test -z "$OLLAMA_BIN"
    echo -e "$RED❌ Ollama not found!$NC"
    echo "Install via: brew install ollama"
    exit 1
end

# Check if Ollama service is running
if not pgrep -x "ollama" > /dev/null
    echo "Starting Ollama service..."
    $OLLAMA_BIN serve > /dev/null 2>&1 &
    sleep 2
end

if not curl -s http://localhost:11434/api/tags | grep -q "llama3.1"
    echo "📥 Downloading Llama 3.1 8B (q5_K_M)..."
    $OLLAMA_BIN pull llama3.1:8b-instruct-q5_K_M
end
echo -e "$GREEN✅ Ollama + Llama 3.1 ready$NC"

# Step 4.5: Check PostgreSQL (only for local runs, not CI)
if not set -q CI
    echo "4.5️⃣ Checking PostgreSQL..."
    
    # Check if psql container is running
    if not docker ps --filter "name=psql" --filter "status=running" | grep -q "psql"
        echo "Starting PostgreSQL container..."
        docker start psql
        sleep 2
    end
    echo -e "$GREEN✅ PostgreSQL ready$NC"
end

# Step 5: Build Java app (ROBUST: use jenv exec mvn if available)
echo "5️⃣ Building Java application..."
set MVN_CMD "mvn"
if command -v jenv > /dev/null
    set MVN_CMD "jenv exec mvn"
end

set JAR_FILE "target/auto-equity-trader.jar"

if not test -f "$JAR_FILE"
    echo "Building JAR file (using: $MVN_CMD)..."
    eval $MVN_CMD clean package -DskipTests
    if test $status -ne 0
        echo "❌ Maven build failed!"
        exit 1
    end
end

echo "✅ Using JAR file (JVM mode)"

# Step 5.5: Create logs directory BEFORE any logging happens
set BOT_DIR_ABS (pwd)
mkdir -p $BOT_DIR_ABS/logs

# Remove any existing stop-file from previous runs
set STOP_FILE "$BOT_DIR_ABS/logs/supervisor.stop"
rm -f $STOP_FILE

# Step 7: Start Python bridge with supervision loop (Fish background job)
echo "6️⃣ Starting Python bridge with intra-day supervision..."

# Supervisor function that runs in background
function supervise_python_bridge
    set -l BOT_DIR $argv[1]
    set -l JASYPT_SECRET $argv[2]
    set -l STOP_FILE "$BOT_DIR/logs/supervisor.stop"
    
    # Ensure logs directory exists (critical for background process)
    mkdir -p $BOT_DIR/logs
    
    cd $BOT_DIR/python
    
    set -l restart_count 0
    
    while not test -f $STOP_FILE
        echo "🔄 [Supervisor] Starting Python bridge (attempt "(math $restart_count + 1)")..." >> $BOT_DIR/logs/supervisor.log
        
        set -x JASYPT_PASSWORD $JASYPT_SECRET
        set -x POSTGRES_PASSWORD "WSYS1r0PE0Ig0iuNX2aNi5k7"
        set -x TRADING_MODE $TRADING_MODE
        # Use venv Python explicitly
        $BOT_DIR/python/venv/bin/python bridge.py >> $BOT_DIR/logs/python-bridge.log 2>&1
        set -l exit_code $status
        
        # Check if stop was requested
        if test -f $STOP_FILE
            echo "✅ [Supervisor] Stop file detected, exiting supervision loop." >> $BOT_DIR/logs/supervisor.log
            break
        end
        
        # Bridge crashed unexpectedly
        set restart_count (math $restart_count + 1)
        echo "⚠️ [Supervisor] Python bridge crashed with exit code $exit_code. Restarting in 5 seconds... (restart #$restart_count)" >> $BOT_DIR/logs/supervisor.log
        
        # Log to main output as well for visibility
        echo "⚠️ Python bridge crashed! Auto-restarting in 5s... (restart #$restart_count)"
        
        sleep 5
    end
    
    echo "🛑 [Supervisor] Supervision loop terminated." >> $BOT_DIR/logs/supervisor.log
end

# Start the supervisor in the background (using nohup for reliability)
nohup fish -c "
    set BOT_DIR '$BOT_DIR_ABS'
    set JASYPT_SECRET '$JASYPT_SECRET'
    set TRADING_MODE '$TRADING_MODE'
    set STOP_FILE '\$BOT_DIR/logs/supervisor.stop'
    
    mkdir -p \$BOT_DIR/logs
    cd \$BOT_DIR/python
    
    set restart_count 0
    
    while not test -f \$STOP_FILE
        echo '🔄 [Supervisor] Starting Python bridge (attempt '(math \$restart_count + 1)')...' >> \$BOT_DIR/logs/supervisor.log
        
        set -x JASYPT_PASSWORD \$JASYPT_SECRET
        set -x POSTGRES_PASSWORD 'WSYS1r0PE0Ig0iuNX2aNi5k7'
        set -x TRADING_MODE \$TRADING_MODE
        # Use venv Python explicitly
        \$BOT_DIR/python/venv/bin/python bridge.py >> \$BOT_DIR/logs/python-bridge.log 2>&1
        set exit_code \$status
        
        if test -f \$STOP_FILE
            echo '✅ [Supervisor] Stop file detected, exiting supervision loop.' >> \$BOT_DIR/logs/supervisor.log
            break
        end
        
        set restart_count (math \$restart_count + 1)
        echo '⚠️ [Supervisor] Python bridge crashed with exit code '\$exit_code'. Restarting in 5 seconds... (restart #'\$restart_count')' >> \$BOT_DIR/logs/supervisor.log
        echo '⚠️ Python bridge crashed! Auto-restarting in 5s... (restart #'\$restart_count')'
        
        sleep 5
    end
    
    echo '🛑 [Supervisor] Supervision loop terminated.' >> \$BOT_DIR/logs/supervisor.log
" > /dev/null 2>&1 &
set SUPERVISOR_PID $last_pid
echo "✅ Python bridge supervisor started (PID: $SUPERVISOR_PID)"

# Wait for bridge to be ready (more reliable)
echo "Waiting for Python bridge..."
set -l attempts 0
while test $attempts -lt 30
    if curl -f -s http://localhost:8888/health > /dev/null 2>&1
        echo "✅ Python bridge ready"
        break
    end
    set attempts (math $attempts + 1)
    sleep 1
end

# Final check — if still not ready, show error but continue
if test $attempts -eq 30
    echo "⚠️ Warning: Python bridge not ready after 30s, starting Java anyway..."
end

# Step 8: Start Java trading engine (ROBUST: use jenv exec)
echo "7️⃣ Starting Java trading engine..."
echo ""
echo "📊 Bot is running! Press Ctrl+C to stop."
echo "📱 Check your Telegram for alerts."
echo "📈 Mode: $TRADING_MODE"
echo ""

# Use jenv exec for cron reliability, fallback to java otherwise
if command -v jenv > /dev/null
    jenv exec java -Dtrading.mode="$TRADING_MODE" -jar target/auto-equity-trader.jar --jasypt.encryptor.password="$JASYPT_SECRET"
else
    java -Dtrading.mode="$TRADING_MODE" -jar target/auto-equity-trader.jar --jasypt.encryptor.password="$JASYPT_SECRET"
end
set JAVA_EXIT_CODE $status

# Java has exited (either normally at 13:00 or due to error)
echo ""
echo "☕ Java application exited with code: $JAVA_EXIT_CODE"

# --- START: ROBUST shutdown logic with supervisor ---

# Signal the supervisor to stop by creating the stop file
echo "🛑 Signaling supervisor to stop..."
touch $STOP_FILE

# Cleanup: Stop Python bridge via its own API
echo "🐍 Requesting graceful shutdown of Python bridge via API..."
set -l response_code (curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8888/shutdown)

if test $response_code -eq 200
    echo "✅ Python bridge accepted shutdown request. Waiting for supervisor to exit..."
    # Wait up to 15 seconds for the supervisor to terminate
    set -l wait_time 0
    while /bin/kill -0 $SUPERVISOR_PID 2>/dev/null
        if test $wait_time -ge 15
            echo "⚠️ Supervisor did not exit after 15s. Force killing..."
            /bin/kill -9 $SUPERVISOR_PID 2>/dev/null || true
            # Also kill any remaining Python bridge processes
            for pid in (pgrep -f "python.*bridge.py" 2>/dev/null || echo "")
                if test -n "$pid"
                    /bin/kill -9 $pid 2>/dev/null || true
                end
            end
            break
        end
        sleep 1
        set wait_time (math $wait_time + 1)
    end
else
    echo "⚠️ Python bridge did not respond to shutdown command (HTTP: $response_code). Force killing supervisor..."
    /bin/kill -9 $SUPERVISOR_PID 2>/dev/null || true
    # Also kill any remaining Python bridge processes
    for pid in (pgrep -f "python.*bridge.py" 2>/dev/null || echo "")
        if test -n "$pid"
            /bin/kill -9 $pid 2>/dev/null || true
        end
    end
end

if not /bin/kill -0 $SUPERVISOR_PID 2>/dev/null
    echo "✅ Supervisor and Python bridge have stopped."
end

# Stop Ollama service (ROBUST: use absolute path)
echo "🦙 Stopping Ollama service..."
/usr/bin/pkill -x ollama 2>/dev/null || true

# Clean up the stop file
rm -f $STOP_FILE

echo "✅ All services stopped. Goodbye!"

# --- END: New and improved shutdown logic with supervisor ---

# Cleanup on manual interrupt (Ctrl+C) - ROBUST
function cleanup --on-signal INT --on-signal TERM
    echo ""
    echo "🛑 Manual shutdown requested..."
    # Create stop file to signal supervisor
    touch $STOP_FILE
    # Kill supervisor and any Python bridge processes (ROBUST: use absolute paths)
    /bin/kill $SUPERVISOR_PID 2>/dev/null || true
    for pid in (pgrep -f "python.*bridge.py" 2>/dev/null || echo "")
        if test -n "$pid"
            /bin/kill -9 $pid 2>/dev/null || true
        end
    end
    /usr/bin/pkill -x ollama 2>/dev/null || true
    # Clean up stop file
    rm -f $STOP_FILE
    exit
end
