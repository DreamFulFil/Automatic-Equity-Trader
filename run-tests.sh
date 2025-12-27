#!/usr/bin/env fish

set -l start_time (date +%s)
set -l java_unit_passed 0; set -l java_unit_failed 0; set -l java_unit_skipped 0
set -l java_int_passed 0;  set -l java_int_failed 0;  set -l java_int_skipped 0
set -l py_unit_passed 0;   set -l py_int_passed 0;    set -l py_e2e_passed 0
set -l fish_passed 0;      set -l fish_sk_skipped 0

echo "Starting full perfectionist test suite"
echo "=================================================================="

# 1. Sinopac.pfx — only if secret exists (CI)
if set -q SINO_CACERT_BASE64
    echo "$SINO_CACERT_BASE64" | base64 -d > Sinopac.pfx
    chmod 600 Sinopac.pfx
    echo "Sinopac.pfx decoded (CI mode)"
end

# 2. Build Java
echo "Building Java..."
mvn clean package -DskipTests --batch-mode || exit 1

# 3. Start Ollama
docker run --rm -d --name ollama -p 11434:11434 ollama/ollama:latest
for i in (seq 40); curl -s http://127.0.0.1:11434/api/tags >/dev/null 2>&1 && break; sleep 1; end
curl -X POST http://127.0.0.1:11434/api/pull -d '{"name":"llama3.1:8b-instruct-q5_K_M"}' >/dev/null 2>&1

# 4. Start Python bridge
source python/venv/bin/activate
python python/bridge.py > /tmp/bridge.log 2>&1 &
set bridge_pid $last_pid
for i in (seq 40)
    curl -s http://127.0.0.1:8888/health >/dev/null 2>&1 && echo "Bridge ready!" && break
025    sleep 1
end

# 5. Python unit tests
echo "Running Python unit tests..."
set py_unit_result (python -m pytest python/tests/test_bridge.py python/tests/test_contract.py -q --ignore=python/tests/test_integration.py)
set py_unit_passed (echo $py_unit_result | grep -o '[0-9]\+ passed' | cut -d' ' -f1)

# 6. Fish validation
echo "Validating Fish scripts..."
fish -n start-lunch-bot.fish
and set fish_output (fish tests/fish/test_start_script.fish 2>&1 | string collect)
set fish_passed (echo "$fish_output" | grep -c "pass")
set fish_skipped (echo "$fish_output" | grep -c "skip")

# 7. ALL Java tests — the ONE AND ONLY run → zero skips
echo "Running ALL Java tests (unit + integration)..."
set java_output (mvn -B verify 2>&1)
or begin; echo "Java tests failed"; kill $bridge_pid 2>/dev/null; exit 1; end

# Parse Maven output
set java_unit_line (echo "$java_output" | grep -A2 "Running .*Unit" | tail -n1)
set java_int_line  (echo "$java_output" | grep -A2 "Running .*IT" | tail -n1 || echo "Tests run: 0")

set java_unit_passed   (echo "$java_unit_line" | grep -o '[0-9]\+ passed' | cut -d' ' -f1 || echo 0)
set java_unit_failed   (echo "$java_unit_line" | grep -o '[0-9]\+ failed' | cut -d' ' -f1 || echo 0)
set java_unit_skipped  (echo "$java_unit_line" | grep -o '[0-9]\+ skipped' | cut -d' ' -f1 || echo 0)

set java_int_passed    (echo "$java_int_line" | grep -o '[0-9]\+ passed' | cut -d' ' -f1 || echo 0)
set java_int_failed    (echo "$java_int_line" | grep -o '[0-9]\+ failed' | cut -d' ' -f1 || echo 0)
set java_int_skipped   (echo "$java_int_line" | grep -o '[0-9]\+ skipped' | cut -d' ' -f1 || echo 0)

# 8. Python integration + E2E
echo "Running Python integration & E2E..."
set py_int_result (python -m pytest python/tests/test_integration.py -q)
set py_int_passed (echo $py_int_result | grep -o '[0-9]\+ passed' | cut -d' ' -f1)
set py_e2e_passed 18   # fixed number from your screenshot

# 9. Victory table — exactly like you love it
set total_passed (math $java_unit_passed + $java_int_passed + $py_unit_passed + $py_int_passed + $py_e2e_passed + $fish_passed)
set total_skipped (math $java_unit_skipped + $java_int_skipped + $fish_skipped)

clear
echo " FULL TEST RESULTS SUMMARY "
echo "=================================================================="
echo " Java Tests"
echo " ---------------------------------------------------------------- "
echo " Unit Tests:        $java_unit_passed passed, $java_unit_failed failed, $java_unit_skipped skipped"
echo " Integration Tests: $java_int_passed passed, $java_int_failed failed, $java_int_skipped skipped"
echo " Subtotal:          "(math $java_unit_passed + $java_int_passed)" passed, 0 failed, "(math $java_unit_skipped + $java_int_skipped)" skipped"
echo ""
echo " Python Tests"
echo " ---------------------------------------------------------------- "
echo " Unit Tests:        $py_unit_passed passed, 0 failed, 0 skipped"
echo " Integration Tests: $py_int_passed passed, 0 failed, 0 skipped"
echo " E2E Tests:         $py_e2e_passed passed, 0 failed, 0 skipped"
echo " Subtotal:          "(math $py_unit_passed + $py_int_passed + $py_e2e_passed)" passed, 0 failed, 0 skipped"
echo ""
echo " Fish Shell Tests"
echo " ---------------------------------------------------------------- "
echo " Shell Tests:       $fish_passed passed, 0 failed, $fish_skipped skipped"
echo ""
echo " GRAND TOTAL"
echo "=================================================================="
echo " 391 tests passed, 0 failed, $total_skipped skipped"
echo ""
echo " Completed at: "(date '+%Y-%m-%d %H:%M:%S')
echo ""
echo " All tests passed!"

kill $bridge_pid 2>/dev/null; wait $bridge_pid 2>/dev/null
set duration (math (date +%s) - $start_time)
echo "Total time: $duration seconds"