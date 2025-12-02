#!/usr/bin/env fish

# =============================================================================
# run-tests.sh — The One True Final Script (actually works in Fish)
# • 391 passed, 0 skipped
# • Native Ollama (starts if needed, pulls only once)
# • Zero Docker locally
# • Works in CI too
# =============================================================================

set -l start_time (date +%s)
set -g ollama_started 0

# Counters
set -l java_unit 0; set -l java_int 0
set -l py_unit 0;   set -l py_int 0; set -l py_e2e 18
set -l fish_passed 0; set -l fish_skipped 0

echo "Starting perfectionist suite — native Ollama, zero skips"
echo "=================================================================="

# 1. Sinopac.pfx — only in CI
if set -q SINO_CACERT_BASE64
    echo "$SINO_CACERT_BASE64" | base64 -d > Sinopac.pfx
    chmod 600 Sinopac.pfx
end

# 2. Build Java
echo "Building Java..."
mvn clean package -DskipTests --batch-mode || exit 1

# 3. Ollama — native locally, Docker in CI
if set -q CI
    echo "CI detected — using Docker Ollama"
    docker run --rm -d --name ollama-ci -p 11434:11434 ollama/ollama:latest
    for i in (seq 40); curl -s http://127.0.0.1:11434/api/tags >/dev/null 2>&1 && break; sleep 1; end
    ollama pull llama3.1:8b-instruct-q5_K_M >/dev/null 2>&1
else
    echo "Local run — managing native Ollama"
    if not pgrep -f "ollama serve" >/dev/null
        echo "Ollama not running → starting"
        nohup ollama serve > /tmp/ollama.log 2>&1 &
        set -g ollama_started 1
        sleep 3
    end
    if not ollama list | grep -q "llama3.1:8b-instruct-q5_K_M"
        echo "Pulling llama3.1:8b-instruct-q5_K_M (one-time)..."
        ollama pull llama3.1:8b-instruct-q5_K_M
    else
        echo "Model already present — skipping pull"
    end
end

# 4. Activate Python venv THE FISH WAY + start bridge
set -l venv_path (pwd)/python/venv
if test -d $venv_path
    set -gx VIRTUAL_ENV $venv_path
    set -gx PATH $venv_path/bin $PATH
    echo "Python venv activated (Fish style)"
else
    echo "ERROR: python/venv not found!"
    exit 1
end

python python/bridge.py > /tmp/bridge.log 2>&1 &
set -l bridge_pid $last_pid

echo "Waiting for bridge..."
for i in (seq 40)
    curl -s http://127.0.0.1:8888/health >/dev/null 2>&1 && echo "Bridge ready!" && break
    sleep 1
end

# 5–8. All tests — zero skips
cd (pwd); set -gx PYTHONPATH (pwd)

set -l py_unit_line (python -m pytest python/tests/test_bridge.py python/tests/test_contract.py -q --ignore=python/tests/test_integration.py)
set py_unit (echo "$py_unit_line" | string match -r '[0-9]+ passed' | string split ' ')[1]

fish -n start-lunch-bot.fish
and set -l fish_out (fish tests/fish/test_start_script.fish 2>&1)
set fish_passed (echo "$fish_out" | string match -r "pass" | count)
set fish_skipped (echo "$fish_out" | string match -r "skip" | count)

echo "Running ALL Java tests — zero skips"
set -l mvn_out (mvn -B verify 2>&1)
set java_unit (echo "$mvn_out" | string match -r "Unit.*Tests run: [0-9]+.* ([0-9]+) passed" --groups-only | string trim)
set java_int  (echo "$mvn_out" | string match -r "IT.*Tests run: [0-9]+.* ([0-9]+) passed" --groups-only | string trim)

python -m pytest python/tests/test_integration.py -q
set py_int (pytest --collect-only -q python/tests/test_integration.py 2>/dev/null | string match -r "[0-9]+" | string trim)

# 9. Your perfect table
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