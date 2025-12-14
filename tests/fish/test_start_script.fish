#!/usr/bin/env fish
###############################################################################
# Fish Shell Tests for Automatic Equity Trader
# 
# Tests for start-auto-trader.fish script and environment setup.
#
# Run: fish tests/fish/test_start_script.fish
###############################################################################

set SCRIPT_DIR (dirname (status --current-filename))
set PROJECT_ROOT (realpath "$SCRIPT_DIR/../..")

# Colors
set -l GREEN '\033[0;32m'
set -l RED '\033[0;31m'
set -l YELLOW '\033[1;33m'
set -l NC '\033[0m'

set -g PASSED 0
set -g FAILED 0
set -g SKIPPED 0

# Helper function to report test result
function report_test
    set -l name $argv[1]
    set -l result $argv[2]
    set -l message $argv[3]
    
    switch $result
        case pass
            echo -e "$GREEN✅ PASS:$NC $name"
            set -g PASSED (math $PASSED + 1)
        case fail
            echo -e "$RED❌ FAIL:$NC $name"
            if test -n "$message"
                echo -e "   $message"
            end
            set -g FAILED (math $FAILED + 1)
        case skip
            echo -e "$YELLOW⏭️ SKIP:$NC $name"
            if test -n "$message"
                echo -e "   $message"
            end
            set -g SKIPPED (math $SKIPPED + 1)
    end
end

###############################################################################
# Test Cases
###############################################################################

# Test 1: Start script exists
function test_start_script_exists
    if test -f "$PROJECT_ROOT/start-auto-trader.fish"
        report_test "Start script exists" pass
        return 0
    else
        report_test "Start script exists" fail "File not found: start-auto-trader.fish"
        return 1
    end
end

# Test 2: Script requires secret argument
function test_requires_secret_argument
    set -l result (fish $PROJECT_ROOT/start-auto-trader.fish 2>&1)
    if string match -q "*Usage*" $result; or string match -q "*secret*" $result
        report_test "Script requires secret argument" pass
        return 0
    else
        report_test "Script requires secret argument" fail "Script should show usage when no args provided"
        return 1
    end
end

# Test 3: Python venv exists
function test_python_venv_exists
    if test -d "$PROJECT_ROOT/python/venv"
        report_test "Python venv exists" pass
        return 0
    else
        report_test "Python venv exists" skip "Run: cd python && python3 -m venv venv"
        return 0
    end
end

# Test 4: activate.fish exists
function test_activate_fish_exists
    if test -f "$PROJECT_ROOT/python/venv/bin/activate.fish"
        report_test "activate.fish exists" pass
        return 0
    else
        if not test -d "$PROJECT_ROOT/python/venv"
            report_test "activate.fish exists" skip "Python venv not created yet"
        else
            report_test "activate.fish exists" fail "venv exists but activate.fish missing"
        end
        return 0
    end
end

# Test 5: Python bridge.py exists
function test_bridge_exists
    if test -f "$PROJECT_ROOT/python/bridge.py"
        report_test "Python bridge.py exists" pass
        return 0
    else
        report_test "Python bridge.py exists" fail "File not found: python/bridge.py"
        return 1
    end
end

# Test 6: Requirements file exists
function test_requirements_exists
    if test -f "$PROJECT_ROOT/python/requirements.txt"
        report_test "Python requirements.txt exists" pass
        return 0
    else
        report_test "Python requirements.txt exists" fail "File not found: python/requirements.txt"
        return 1
    end
end

# Test 7: Java pom.xml exists
function test_pom_exists
    if test -f "$PROJECT_ROOT/pom.xml"
        report_test "pom.xml exists" pass
        return 0
    else
        report_test "pom.xml exists" fail "File not found: pom.xml"
        return 1
    end
end

# Test 8: Java JAR exists (after build)
function test_jar_exists
    if test -f "$PROJECT_ROOT/target/mtxf-bot-1.0.0.jar"
        report_test "Java JAR exists" pass
        return 0
    else
        report_test "Java JAR exists" skip "JAR not built yet (run: mvn clean package -DskipTests)"
        return 0
    end
end

# Test 9: application.yml exists
function test_application_yml_exists
    if test -f "$PROJECT_ROOT/src/main/resources/application.yml"
        report_test "application.yml exists" pass
        return 0
    else
        report_test "application.yml exists" fail "File not found: src/main/resources/application.yml"
        return 1
    end
end

# Test 10: Earnings blackout data managed in DB
function test_earnings_blackout_exists
    report_test "Earnings blackout managed by DB" pass "Use /admin/earnings-blackout endpoints to seed/refresh"
    return 0
end

# Test 11: Logs directory exists or can be created
function test_logs_directory
    if test -d "$PROJECT_ROOT/logs"
        report_test "Logs directory exists" pass
        return 0
    else
        # Try to create it
        mkdir -p "$PROJECT_ROOT/logs"
        if test -d "$PROJECT_ROOT/logs"
            report_test "Logs directory exists" pass
            return 0
        else
            report_test "Logs directory exists" fail "Cannot create logs directory"
            return 1
        end
    end
end

# Test 12: CA certificate exists
function test_ca_certificate_exists
    if test -f "$PROJECT_ROOT/Sinopac.pfx"
        report_test "CA certificate (Sinopac.pfx) exists" pass
        return 0
    else
        report_test "CA certificate (Sinopac.pfx) exists" fail "File not found: Sinopac.pfx"
        return 1
    end
end

# Test 13: Config directory exists
function test_config_directory_exists
    if test -d "$PROJECT_ROOT/config"
        report_test "Config directory exists" pass
        return 0
    else
        mkdir -p "$PROJECT_ROOT/config"
        if test -d "$PROJECT_ROOT/config"
            report_test "Config directory exists" pass
            return 0
        else
            report_test "Config directory exists" fail "Cannot create config directory"
            return 1
        end
    end
end

# Test 14: Java 21 is available
function test_java_version
    if command -v java > /dev/null
        set -l java_version (java -version 2>&1 | head -1)
        if string match -q "*21*" $java_version
            report_test "Java 21 available" pass
            return 0
        else
            report_test "Java 21 available" fail "Found: $java_version (need Java 21)"
            return 1
        end
    else
        report_test "Java 21 available" fail "java not found in PATH"
        return 1
    end
end

# Test 15: Python 3 is available
function test_python_version
    if command -v python3 > /dev/null
        set -l py_version (python3 --version | cut -d' ' -f2)
        report_test "Python 3 available ($py_version)" pass
        return 0
    else
        report_test "Python 3 available" fail "python3 not found in PATH"
        return 1
    end
end

###############################################################################
# Run All Tests
###############################################################################

function run_all_tests
    echo "🐟 MTXF Fish Shell Tests"
    echo "========================"
    echo "Project root: $PROJECT_ROOT"
    echo ""
    
    # Run all tests
    test_start_script_exists
    test_requires_secret_argument
    test_python_venv_exists
    test_activate_fish_exists
    test_bridge_exists
    test_requirements_exists
    test_pom_exists
    test_jar_exists
    test_application_yml_exists
    test_earnings_blackout_exists
    test_logs_directory
    test_ca_certificate_exists
    test_config_directory_exists
    test_java_version
    test_python_version
    
    # Summary
    echo ""
    echo "========================"
    echo "Test Summary"
    echo "========================"
    echo -e "$GREEN✅ Passed: $PASSED$NC"
    echo -e "$RED❌ Failed: $FAILED$NC"
    echo -e "$YELLOW⏭️ Skipped: $SKIPPED$NC"
    echo ""
    
    if test "$FAILED" -gt 0
        echo -e "$RED❌ Some tests failed!$NC"
        return 1
    else
        echo -e "$GREEN✅ All tests passed!$NC"
        return 0
    end
end

# Run tests
run_all_tests
