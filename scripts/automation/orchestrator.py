#!/usr/bin/env python3
"""
Orchestrator - Full System Boot & Backtest Automation

This script:
1. Starts Java (Port 16350) and Python Bridge (Port 8888)
2. Polls health endpoints until services are ready
3. Triggers backtest via REST API
4. Monitors backtest progress with 30-second polling
5. Provides summary table of Download/Backtest results
6. Executes auto-selection

Usage:
    python orchestrator.py <jasypt-password>
    
Example:
    python orchestrator.py mysecretpassword
"""

import os
import sys
import time
import subprocess
import requests
from datetime import datetime
from typing import Optional, Dict, Any

# Configuration
JAVA_SERVICE_URL = "http://localhost:16350"
PYTHON_BRIDGE_URL = "http://localhost:8888"
HEALTH_CHECK_INTERVAL = 5
HEALTH_CHECK_TIMEOUT = 180
BACKTEST_POLL_INTERVAL = 30
BACKTEST_TIMEOUT = 3600  # 1 hour max


def print_banner():
    """Print orchestrator banner"""
    print("""
╔══════════════════════════════════════════════════════════════════╗
║          🚀 AUTOMATIC EQUITY TRADER ORCHESTRATOR 🚀              ║
╠══════════════════════════════════════════════════════════════════╣
║  Boots Java + Python Bridge → Runs Backtest → Auto-Selects      ║
╚══════════════════════════════════════════════════════════════════╝
""")


def log(message: str, level: str = "INFO"):
    """Log with timestamp"""
    timestamp = datetime.now().strftime("%H:%M:%S")
    icons = {
        "INFO": "ℹ️ ",
        "OK": "✅",
        "WARN": "⚠️ ",
        "ERROR": "❌",
        "WAIT": "⏳"
    }
    icon = icons.get(level, "")
    print(f"[{timestamp}] {icon} {message}")


def check_service_health(url: str, name: str) -> bool:
    """Check if a service is healthy"""
    try:
        response = requests.get(f"{url}/health", timeout=5)
        if response.status_code == 200:
            return True
    except requests.exceptions.RequestException:
        pass
    return False


def wait_for_services(java_process: subprocess.Popen, bridge_process: subprocess.Popen) -> bool:
    """Wait for both services to become healthy"""
    log(f"Waiting for services to start (timeout: {HEALTH_CHECK_TIMEOUT}s)...", "WAIT")
    
    start = time.time()
    java_ready = False
    bridge_ready = False
    
    while time.time() - start < HEALTH_CHECK_TIMEOUT:
        if not java_ready:
            java_ready = check_service_health(JAVA_SERVICE_URL, "Java")
            if java_ready:
                log("Java service is ready", "OK")
        
        if not bridge_ready:
            bridge_ready = check_service_health(PYTHON_BRIDGE_URL, "Python Bridge")
            if bridge_ready:
                log("Python Bridge is ready", "OK")
        
        if java_ready and bridge_ready:
            return True
        
        # Check if processes died
        if java_process.poll() is not None:
            log("Java process died unexpectedly", "ERROR")
            return False
        if bridge_process.poll() is not None:
            log("Python Bridge process died unexpectedly", "ERROR")
            return False
        
        time.sleep(HEALTH_CHECK_INTERVAL)
    
    log(f"Timeout waiting for services after {HEALTH_CHECK_TIMEOUT}s", "ERROR")
    return False


def start_java_service(jasypt_password: str) -> Optional[subprocess.Popen]:
    """Start Java service with jasypt password"""
    log("Starting Java service (port 16350)...", "INFO")
    
    project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    
    env = os.environ.copy()
    env["JASYPT_ENCRYPTOR_PASSWORD"] = jasypt_password
    
    cmd = [
        "jenv", "exec", "mvn",
        "-f", os.path.join(project_root, "pom.xml"),
        "spring-boot:run",
        "-DskipTests"
    ]
    
    try:
        process = subprocess.Popen(
            cmd,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            cwd=project_root
        )
        log(f"Java service started (PID: {process.pid})", "OK")
        return process
    except Exception as e:
        log(f"Failed to start Java service: {e}", "ERROR")
        return None


def start_python_bridge(jasypt_password: str) -> Optional[subprocess.Popen]:
    """Start Python bridge service"""
    log("Starting Python Bridge (port 8888)...", "INFO")
    
    project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    python_dir = os.path.join(project_root, "python")
    
    env = os.environ.copy()
    env["JASYPT_ENCRYPTOR_PASSWORD"] = jasypt_password
    
    cmd = [
        "python3", "main.py"
    ]
    
    try:
        process = subprocess.Popen(
            cmd,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            cwd=python_dir
        )
        log(f"Python Bridge started (PID: {process.pid})", "OK")
        return process
    except Exception as e:
        log(f"Failed to start Python Bridge: {e}", "ERROR")
        return None


def trigger_backtest() -> bool:
    """Trigger backtest via REST API"""
    log("Triggering backtest via POST /api/backtest/run...", "INFO")
    
    try:
        response = requests.post(
            f"{JAVA_SERVICE_URL}/api/backtest/run",
            timeout=30
        )
        if response.status_code in [200, 202]:
            log("Backtest triggered successfully", "OK")
            return True
        else:
            log(f"Backtest trigger failed: HTTP {response.status_code}", "ERROR")
            return False
    except requests.exceptions.RequestException as e:
        log(f"Backtest trigger failed: {e}", "ERROR")
        return False


def check_backtest_status() -> Dict[str, Any]:
    """Check backtest running status"""
    try:
        response = requests.get(f"{JAVA_SERVICE_URL}/api/status", timeout=10)
        if response.status_code == 200:
            data = response.json()
            return {
                "backtest_running": data.get("backtestRunning", False),
                "download_running": data.get("historyDownloadRunning", False),
                "operation_running": data.get("operationRunning", False)
            }
    except:
        pass
    return {"backtest_running": False, "download_running": False, "operation_running": False}


def poll_backtest_progress():
    """Poll backtest progress until complete"""
    log(f"Monitoring backtest progress (poll every {BACKTEST_POLL_INTERVAL}s)...", "WAIT")
    
    start = time.time()
    poll_count = 0
    
    while time.time() - start < BACKTEST_TIMEOUT:
        status = check_backtest_status()
        poll_count += 1
        
        elapsed = int(time.time() - start)
        elapsed_str = f"{elapsed // 60}m {elapsed % 60}s"
        
        if status["download_running"]:
            log(f"[Poll #{poll_count}] Historical data download in progress... ({elapsed_str})", "WAIT")
        elif status["backtest_running"]:
            log(f"[Poll #{poll_count}] Backtest in progress... ({elapsed_str})", "WAIT")
        elif status["operation_running"]:
            log(f"[Poll #{poll_count}] Operation in progress... ({elapsed_str})", "WAIT")
        else:
            log(f"Backtest completed in {elapsed_str}", "OK")
            return True
        
        time.sleep(BACKTEST_POLL_INTERVAL)
    
    log(f"Backtest timed out after {BACKTEST_TIMEOUT}s", "ERROR")
    return False


def get_backtest_summary() -> Optional[Dict]:
    """Fetch backtest summary from API"""
    try:
        response = requests.get(f"{JAVA_SERVICE_URL}/api/backtest/summary", timeout=30)
        if response.status_code == 200:
            return response.json()
    except:
        pass
    return None


def print_summary_table(summary: Optional[Dict]):
    """Print summary table of results"""
    print("\n" + "═" * 70)
    print("                    📊 BACKTEST SUMMARY                    ")
    print("═" * 70)
    
    if not summary:
        print("  ⚠️  Summary data unavailable")
        print("═" * 70)
        return
    
    # Print download stats
    if "download" in summary:
        dl = summary["download"]
        print(f"\n  📥 DOWNLOAD RESULTS")
        print(f"  ├─ Stocks processed: {dl.get('stocks', 'N/A')}")
        print(f"  ├─ Records downloaded: {dl.get('records', 'N/A')}")
        print(f"  └─ Duration: {dl.get('duration', 'N/A')}")
    
    # Print backtest stats
    if "backtest" in summary:
        bt = summary["backtest"]
        print(f"\n  🧪 BACKTEST RESULTS")
        print(f"  ├─ Stocks tested: {bt.get('stocks', 'N/A')}")
        print(f"  ├─ Strategies tested: {bt.get('strategies', 'N/A')}")
        print(f"  ├─ Total results: {bt.get('totalResults', 'N/A')}")
        print(f"  └─ Duration: {bt.get('duration', 'N/A')}")
    
    # Print top performers
    if "topPerformers" in summary:
        print(f"\n  🏆 TOP 5 PERFORMERS")
        print(f"  {'Symbol':<12} {'Strategy':<30} {'Return %':<10} {'Sharpe':<8}")
        print(f"  {'-' * 62}")
        
        for i, perf in enumerate(summary["topPerformers"][:5]):
            symbol = perf.get("symbol", "N/A")[:12]
            strategy = perf.get("strategyName", "N/A")[:30]
            ret = perf.get("totalReturnPct", 0)
            sharpe = perf.get("sharpeRatio", 0)
            print(f"  {symbol:<12} {strategy:<30} {ret:>8.2f}% {sharpe:>7.2f}")
    
    print("\n" + "═" * 70)


def trigger_auto_selection() -> bool:
    """Trigger auto-selection via REST API"""
    log("Triggering auto-selection via POST /api/auto-selection/run-now...", "INFO")
    
    try:
        response = requests.post(
            f"{JAVA_SERVICE_URL}/api/auto-selection/run-now",
            timeout=60
        )
        if response.status_code == 200:
            data = response.json()
            if data.get("status") == "success":
                log("Auto selection done.", "OK")
                return True
            else:
                log(f"Auto-selection returned: {data.get('message', 'Unknown error')}", "WARN")
                return False
        else:
            log(f"Auto-selection failed: HTTP {response.status_code}", "ERROR")
            return False
    except requests.exceptions.RequestException as e:
        log(f"Auto-selection failed: {e}", "ERROR")
        return False


def cleanup(java_process: Optional[subprocess.Popen], bridge_process: Optional[subprocess.Popen]):
    """Cleanup processes on exit"""
    log("Cleaning up processes...", "INFO")
    
    if java_process and java_process.poll() is None:
        java_process.terminate()
        java_process.wait(timeout=10)
        log("Java service stopped", "OK")
    
    if bridge_process and bridge_process.poll() is None:
        bridge_process.terminate()
        bridge_process.wait(timeout=10)
        log("Python Bridge stopped", "OK")


def main():
    """Main orchestrator flow"""
    print_banner()
    
    if len(sys.argv) < 2:
        print("❌ Usage: python orchestrator.py <jasypt-password>")
        print("\nExample:")
        print("  python orchestrator.py mysecretpassword")
        sys.exit(1)
    
    jasypt_password = sys.argv[1]
    java_process = None
    bridge_process = None
    
    try:
        # Step 1: Start services
        log("=" * 50, "INFO")
        log("PHASE 1: Starting Services", "INFO")
        log("=" * 50, "INFO")
        
        java_process = start_java_service(jasypt_password)
        bridge_process = start_python_bridge(jasypt_password)
        
        if not java_process or not bridge_process:
            log("Failed to start one or more services", "ERROR")
            cleanup(java_process, bridge_process)
            sys.exit(1)
        
        # Step 2: Wait for services to be healthy
        if not wait_for_services(java_process, bridge_process):
            cleanup(java_process, bridge_process)
            sys.exit(1)
        
        log("All services ready!", "OK")
        
        # Step 3: Trigger backtest
        log("=" * 50, "INFO")
        log("PHASE 2: Running Backtest", "INFO")
        log("=" * 50, "INFO")
        
        if not trigger_backtest():
            log("Backtest trigger failed", "ERROR")
            cleanup(java_process, bridge_process)
            sys.exit(1)
        
        # Step 4: Poll for completion
        if not poll_backtest_progress():
            log("Backtest did not complete successfully", "ERROR")
            cleanup(java_process, bridge_process)
            sys.exit(1)
        
        # Step 5: Print summary
        summary = get_backtest_summary()
        print_summary_table(summary)
        
        # Step 6: Auto-selection
        log("=" * 50, "INFO")
        log("PHASE 3: Auto-Selection", "INFO")
        log("=" * 50, "INFO")
        
        trigger_auto_selection()
        
        log("=" * 50, "INFO")
        log("🎉 ORCHESTRATION COMPLETE", "OK")
        log("=" * 50, "INFO")
        
        # Keep services running
        log("Services are running. Press Ctrl+C to stop.", "INFO")
        
        while True:
            if java_process.poll() is not None or bridge_process.poll() is not None:
                log("A service has stopped unexpectedly", "WARN")
                break
            time.sleep(60)
        
    except KeyboardInterrupt:
        log("\nReceived shutdown signal", "INFO")
    finally:
        cleanup(java_process, bridge_process)


if __name__ == "__main__":
    main()
