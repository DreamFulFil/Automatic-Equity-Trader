#!/usr/bin/env fish
###############################################################################
# Fish Shell Tests for Supervisor Functionality
# 
# Tests for the Python bridge supervisor in start-auto-trader.fish
#
# Run: fish tests/fish/test_supervisor.fish
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

# Test 1: Supervisor function exists in start script
function test_supervisor_function_exists
    set -l content (cat "$PROJECT_ROOT/start-auto-trader.fish")
    if string match -q "*function supervise_python_bridge*" $content
        report_test "Supervisor function exists" pass
        return 0
    else
        report_test "Supervisor function exists" fail "supervise_python_bridge function not found"
        return 1
    end
end

# Test 2: Supervisor creates stop file
function test_supervisor_uses_stop_file
    set -l content (cat "$PROJECT_ROOT/start-auto-trader.fish")
    if string match -q "*STOP_FILE*" $content; and string match -q "*supervisor.stop*" $content
        report_test "Supervisor uses stop file mechanism" pass
        return 0
    else
        report_test "Supervisor uses stop file mechanism" fail "Stop file mechanism not implemented"
        return 1
    end
end

# Test 3: Supervisor loop checks for stop file
function test_supervisor_loop_checks_stop_file
    set -l content (cat "$PROJECT_ROOT/start-auto-trader.fish")
    if string match -q "*while not test -f*STOP_FILE*" $content
        report_test "Supervisor loop checks stop file" pass
        return 0
    else
        report_test "Supervisor loop checks stop file" fail "Loop does not check for stop file"
        return 1
    end
end

# Test 4: Supervisor logs to supervisor.log
function test_supervisor_logs_to_file
    set -l content (cat "$PROJECT_ROOT/start-auto-trader.fish")
    if string match -q "*supervisor.log*" $content
        report_test "Supervisor logs to supervisor.log" pass
        return 0
    else
        report_test "Supervisor logs to supervisor.log" fail "Supervisor logging not found"
        return 1
    end
end

# Test 5: Supervisor restarts on crash
function test_supervisor_restarts_on_crash
    set -l content (cat "$PROJECT_ROOT/start-auto-trader.fish")
    if string match -q "*Python bridge crashed*" $content; and string match -q "*Restarting*" $content
        report_test "Supervisor restarts on crash" pass
        return 0
    else
        report_test "Supervisor restarts on crash" fail "Restart logic not found"
        return 1
    end
end

# Test 6: Supervisor has restart delay
function test_supervisor_has_restart_delay
    set -l content (cat "$PROJECT_ROOT/start-auto-trader.fish")
    if string match -q "*sleep 5*" $content; or string match -q "*sleep*" $content
        report_test "Supervisor has restart delay" pass
        return 0
    else
        report_test "Supervisor has restart delay" fail "No restart delay found"
        return 1
    end
end

# Test 7: Startup cleanup removes old stop file
function test_startup_removes_old_stop_file
    set -l content (cat "$PROJECT_ROOT/start-auto-trader.fish")
    if string match -q "*rm -f*STOP_FILE*" $content
        report_test "Startup removes old stop file" pass
        return 0
    else
        report_test "Startup removes old stop file" fail "Stop file cleanup not found"
        return 1
    end
end

# Test 8: Shutdown creates stop file
function test_shutdown_creates_stop_file
    set -l content (cat "$PROJECT_ROOT/start-auto-trader.fish")
    if string match -q "*touch*STOP_FILE*" $content
        report_test "Shutdown creates stop file" pass
        return 0
    else
        report_test "Shutdown creates stop file" fail "Stop file creation not found"
        return 1
    end
end

# Test 9: Shutdown waits for supervisor to exit
function test_shutdown_waits_for_supervisor
    set -l content (cat "$PROJECT_ROOT/start-auto-trader.fish")
    if string match -q "*kill -0*SUPERVISOR_PID*" $content; or string match -q "*Waiting for supervisor*" $content
        report_test "Shutdown waits for supervisor" pass
        return 0
    else
        report_test "Shutdown waits for supervisor" fail "Supervisor wait logic not found"
        return 1
    end
end

# Test 10: Manual interrupt handler uses stop file
function test_interrupt_handler_uses_stop_file
    set -l content (cat "$PROJECT_ROOT/start-auto-trader.fish")
    set -l in_cleanup_function 0
    set -l found_touch_stop_file 0
    
    for line in $content
        if string match -q "*function cleanup*" $line
            set in_cleanup_function 1
        else if string match -q "*end*" $line; and test $in_cleanup_function -eq 1
            set in_cleanup_function 0
        else if test $in_cleanup_function -eq 1; and string match -q "*touch*STOP_FILE*" $line
            set found_touch_stop_file 1
        end
    end
    
    if test $found_touch_stop_file -eq 1
        report_test "Interrupt handler uses stop file" pass
        return 0
    else
        report_test "Interrupt handler uses stop file" fail "Stop file not used in interrupt handler"
        return 1
    end
end

# Test 11: Supervisor tracks restart count
function test_supervisor_tracks_restart_count
    set -l content (cat "$PROJECT_ROOT/start-auto-trader.fish")
    if string match -q "*restart_count*" $content
        report_test "Supervisor tracks restart count" pass
        return 0
    else
        report_test "Supervisor tracks restart count" fail "Restart count tracking not found"
        return 1
    end
end

# Test 12: Cleanup removes stop file at end
function test_cleanup_removes_stop_file
    set -l content (cat "$PROJECT_ROOT/start-auto-trader.fish")
    # Check that there's a cleanup of stop file after shutdown logic
    if string match -q "*rm -f*STOP_FILE*" $content
        report_test "Cleanup removes stop file at end" pass
        return 0
    else
        report_test "Cleanup removes stop file at end" fail "Stop file not cleaned up"
        return 1
    end
end

###############################################################################
# Run All Tests
###############################################################################

function run_all_tests
    echo "🔄 MTXF Supervisor Tests"
    echo "========================"
    echo "Project root: $PROJECT_ROOT"
    echo ""
    
    # Run all tests
    test_supervisor_function_exists
    test_supervisor_uses_stop_file
    test_supervisor_loop_checks_stop_file
    test_supervisor_logs_to_file
    test_supervisor_restarts_on_crash
    test_supervisor_has_restart_delay
    test_startup_removes_old_stop_file
    test_shutdown_creates_stop_file
    test_shutdown_waits_for_supervisor
    test_interrupt_handler_uses_stop_file
    test_supervisor_tracks_restart_count
    test_cleanup_removes_stop_file
    
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
