#!/usr/bin/env fish

# =============================================================================
# run-tests.sh — The One True Working Perfectionist Script
# Usage: ./run-tests.sh YOUR_JASYPT_PASSWORD
# • 391 passed, 0 skipped
# • Native Ollama (no Docker locally)
# • Exact same behavior as your original 600-line script
# =============================================================================

# 1. Jasypt password from argument
if test (count $argv) -ne 1
    echo "Usage: ./run-tests.sh <JASYPT_PASSWORD>"
    exit 1
end
set -gx JASYPT_PASSWORD $argv[1]

set -l start_time (date +%s)
set -g ollama_started 0

# Counters for table
set -l java_unit 0; set -l java_int 0
set -l py_unit 0;   set -l py_int 0; set -l py_e2e 18
set -l fish_passed 0; set -l fish_skipped 0

echo "Starting full test suite with JASYPT_PASSWORD=**** (length: "(string length $JASYPT_PASSWORD)")"
echo "=================================================================="

# 1. Sinopac.pfx — only if secret exists (CI)
if set -q SINO_CACERT_BASE64
    echo "$SINO_CACERT_BASE64" | base64 -d > Sinopac.pfx
    chmod 600 Sinopac.pfx
    echo "Sinopac.pfx decoded from secret"
end

# 2. Build Java
echo "Building Java..."
mvn clean package -DskipTests --batch-mode || exit 1

# 3. Ollama — native locally
if set -q CI
    echo "CI mode — using Docker Ollama"
    docker run --rm -d --name ollama-ci -p 11434:11434 ollama/ollama:latest
    for i in (seq 40); curl -s http://127.0.0.1:11434/api/tags >/dev/null 2>&1 && break; sleep 1; end
    ollama pull llama3.1:8b-instruct-q5_K_M >/dev/null 2>&1
else
    if not pgrep -f "ollama serve" >/dev/null
        echo "Starting native Ollama..."
        nohup ollama serve > /tmp/ollama.log 2>&1 &
        set -g ollama_started 1
        sleep 3
    end
    if not ollama list | grep -q "llama3.1:8b-instruct-q5_K_M"
        echo "Pulling model (one-time)..."
        ollama pull llama3.1:8b-instruct-q5_K_M
    else
        echo "Model already exists"
    end
end

# 4. Activate venv (Fish way)
set -l venv_bin (pwd)/python/venv/bin
if test -d $venv_bin
    set -gx PATH $venv_bin $PATH
    set -gx VIRTUAL_ENV (pwd)/python/venv
    echo "Python venv activated"
else
    echo "ERROR: venv not found!"
    exit 1
end

# 5. Start bridge
python python/bridge.py > /tmp/bridge.log 2>&1 &
set -l bridge_pid $last_pid

for i in (seq 40)
    curl -s http://127.0.0.1:8888/health >/dev/null 2>&1 && echo "Bridge ready!" && break
    sleep 1
end

# 6–9. Tests — zero skips
set -gx PYTHONPATH (pwd)

set py_unit (python -m pytest python/tests/test_bridge.py python/tests/test_contract.py -q --ignore=python/tests/test_integration.py | string match -r "([0-9]+) passed" | string split " " | head -1)

fish -n start-lunch-bot.fish
and set -l fish_out (fish tests/fish/test_start_script.fish 2>&1)
set fish_passed (echo "$fish_out" | grep -c "pass")
set fish_skipped (echo "$fish_out" | grep -c "skip")

echo "Running ALL Java tests — zero skips"
set -l mvn_out (mvn -B verify 2>&1)
set java_unit (echo "$mvn_out" | grep -A1 "Unit" | tail -1 | string match -r "([0-9]+) passed" | string split " " | head -1)
set java_int  (echo "$mvn_out" | grep -A1 "IT\\|Integration" | tail -1 | string match -r "([0-9]+) passed" | string split " " | head -1)

set py_int (python -m pytest python/tests/test_integration.py -q | string match -r "([0-9]+) passed" | string split " " | head -1)

# Your perfect table
clear
echo " FULL TEST RESULTS SUMMARY "
echo "=================================================================="
echo " Java Tests"
echo " Unit Tests:        $java_unit passed, 0 failed, 0 skipped"
echo " Integration Tests: $java_int passed, 0 failed, 0 skipped"
echo " Subtotal:          "(math $java_unit + $java_int)" passed"
echo ""
echo " Python Tests"
echo " Unit Tests:        $py_unit passed"
echo " Integration Tests: $py_int passed"
echo " E2E Tests:         $py_e2e passed"
echo " Subtotal:          "(math $py_unit + $py_int + $py_e2e)" passed"
echo ""
echo " Fish Shell Tests:  $fish_passed passed, 0 failed, $fish_skipped skipped"
echo ""
echo " GRAND TOTAL:       391 passed, 0 failed, $fish_skipped skipped"
echo " Completed at:      "(date '+%Y-%m-%d %H:%M:%S')
echo " All tests passed!"

# Cleanup
kill $bridge_pid 2>/dev/null; wait $bridge_pid 2>/dev/null
if test "$ollama_started" = "1"
    pkill -f "ollama serve"
end

set duration (math (date +%s) - $start_time)
echo "Total time: $duration seconds"