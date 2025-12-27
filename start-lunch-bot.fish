#!/usr/bin/env fish
###############################################################################
# MTXF Lunch Bot Startup Script (Fish Shell)
# Usage: fish start-lunch-bot.fish <jasypt-secret>
###############################################################################

# --- Robust Cleanup of previous runs ---
echo "🧹 Cleaning up leftover processes from previous runs..."
# Kill Python bridge processes (matching the full command line)
for pid in (pgrep -f "python3 bridge.py")
    echo "  Killing old Python bridge process: $pid"
    kill -9 $pid > /dev/null 2>&1
end

# Kill Ollama server processes (matching the executable name)
for pid in (pgrep -x ollama)
    echo "  Killing old Ollama process: $pid"
    kill -9 $pid > /dev/null 2>&1
end
echo "🧹 Cleanup complete."
# --- End Cleanup ---

# Set a higher resource limit for file descriptors
ulimit -n 16384
echo "🔧 Set file descriptor limit to 16384."

# Check for required secret parameter
if test (count $argv) -lt 1
    echo "❌ Usage: fish start-lunch-bot.fish <jasypt-secret>"
    echo "   The secret is required to decrypt application properties."
    exit 1
end

set JASYPT_SECRET $argv[1]

set BOT_DIR (dirname (status --current-filename))
cd $BOT_DIR

# Set JAVA_HOME for Maven (critical for cron environment)
set -x JAVA_HOME /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home

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

# Step 5: Build Java app
echo "5️⃣ Building Java application..."
if not test -f "target/mtxf-bot-1.0.0.jar"
    echo "Building JAR file..."
    mvn clean package -DskipTests
    if test $status -ne 0
        echo "❌ Maven build failed!"
        exit 1
    end
end
echo "✅ Java app built"

# Step 5.5: Create logs directory BEFORE any logging happens
set BOT_DIR_ABS (pwd)
mkdir -p $BOT_DIR_ABS/logs

# Step 5.6: Ensure earnings blackout dates exist
if not test -f "config/earnings-blackout-dates.json"
    echo "📅 Scraping initial earnings blackout dates..."
    cd python
    source venv/bin/activate.fish
    python3 bridge.py --scrape-earnings
    cd ..
else
    echo -e "$GREEN📅 Earnings blackout dates loaded$NC"
end

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
        $BOT_DIR/python/venv/bin/python3 bridge.py >> $BOT_DIR/logs/python-bridge.log 2>&1
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
    set STOP_FILE '\$BOT_DIR/logs/supervisor.stop'
    
    mkdir -p \$BOT_DIR/logs
    cd \$BOT_DIR/python
    
    set restart_count 0
    
    while not test -f \$STOP_FILE
        echo '🔄 [Supervisor] Starting Python bridge (attempt '(math \$restart_count + 1)')...' >> \$BOT_DIR/logs/supervisor.log
        
        set -x JASYPT_PASSWORD \$JASYPT_SECRET
        \$BOT_DIR/python/venv/bin/python3 bridge.py >> \$BOT_DIR/logs/python-bridge.log 2>&1
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

# Step 8: Start Java trading engine
echo "7️⃣ Starting Java trading engine..."
echo ""
echo "📊 Bot is running! Press Ctrl+C to stop."
echo "📱 Check your Telegram for alerts."
echo ""

java -jar target/mtxf-bot-1.0.0.jar --jasypt.encryptor.password="$JASYPT_SECRET"
set JAVA_EXIT_CODE $status

# Java has exited (either normally at 13:00 or due to error)
echo ""
echo "☕ Java application exited with code: $JAVA_EXIT_CODE"

# --- START: New and improved shutdown logic with supervisor ---

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
    while kill -0 $SUPERVISOR_PID 2>/dev/null
        if test $wait_time -ge 15
            echo "⚠️ Supervisor did not exit after 15s. Force killing..."
            kill -9 $SUPERVISOR_PID 2>/dev/null
            # Also kill any remaining Python bridge processes
            for pid in (pgrep -f "python3 bridge.py")
                kill -9 $pid 2>/dev/null
            end
            break
        end
        sleep 1
        set wait_time (math $wait_time + 1)
    end
else
    echo "⚠️ Python bridge did not respond to shutdown command (HTTP: $response_code). Force killing supervisor..."
    kill -9 $SUPERVISOR_PID 2>/dev/null
    # Also kill any remaining Python bridge processes
    for pid in (pgrep -f "python3 bridge.py")
        kill -9 $pid 2>/dev/null
    end
end

if not kill -0 $SUPERVISOR_PID 2>/dev/null
    echo "✅ Supervisor and Python bridge have stopped."
end

# Stop Ollama service
echo "🦙 Stopping Ollama service..."
pkill -x ollama 2>/dev/null

# Clean up the stop file
rm -f $STOP_FILE

echo "✅ All services stopped. Goodbye!"

# --- END: New and improved shutdown logic with supervisor ---

# Cleanup on manual interrupt (Ctrl+C)
function cleanup --on-signal INT --on-signal TERM
    echo ""
    echo "🛑 Manual shutdown requested..."
    # Create stop file to signal supervisor
    touch $STOP_FILE
    # Kill supervisor and any Python bridge processes
    kill $SUPERVISOR_PID 2>/dev/null
    for pid in (pgrep -f "python3 bridge.py")
        kill -9 $pid 2>/dev/null
    end
    pkill -x ollama 2>/dev/null
    # Clean up stop file
    rm -f $STOP_FILE
    exit
end
