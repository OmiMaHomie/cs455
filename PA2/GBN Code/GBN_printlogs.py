#!/usr/bin/env python3
"""
run_pa2_tests.py
Runs each relevant GBN+SACK test scenario once, captures full output, 
and saves each log to its own .txt file for the design/testing documentation.
"""

import subprocess
import os
import sys

# Directory to store the test logs
LOG_DIR = "test_logs"
os.makedirs(LOG_DIR, exist_ok=True)

# Define the exact test cases needed for the grading criteria
TEST_CASES = {
    "01_happy_path.txt": {
        "desc": "No loss, no corruption",
        "msgs": 100, "loss": 0.0, "corrupt": 0.0, "delay": 10.0,
        "winsize": 8, "timeout": 30.0, "trace": 1, "seed": 9431
    },
    "02_data_loss_timeout.txt": {
        "desc": "Data loss & timeout recovery",
        "msgs": 100, "loss": 0.15, "corrupt": 0.0, "delay": 20.0,
        "winsize": 8, "timeout": 30.0, "trace": 1, "seed": 12308
    },
    "03_ack_loss_cumack.txt": {
        "desc": "ACK loss & cumulative ACK recovery",
        "msgs": 100, "loss": 0.15, "corrupt": 0.0, "delay": 10.0,
        "winsize": 8, "timeout": 25.0, "trace": 1, "seed": 437859
    },
    "04_data_corruption.txt": {
        "desc": "Data corruption & checksum detection",
        "msgs": 100, "loss": 0.0, "corrupt": 0.2, "delay": 15.0,
        "winsize": 8, "timeout": 30.0, "trace": 1, "seed": 13428790
    },
    "05_stress_test.txt": {
        "desc": "High loss & high corruption stress test",
        "msgs": 500, "loss": 0.3, "corrupt": 0.3, "delay": 50.0,
        "winsize": 10, "timeout": 40.0, "trace": 1, "seed": 98328
    }
}

def run_test(filename, params):
    """Runs a single test case and saves output to a file."""
    print(f"\n[Running] {filename} -> {params['desc']}")
    
    # Format input exactly as Project.java expects (newline-separated)
    input_data = (
        f"{params['msgs']}\n"
        f"{params['loss']}\n"
        f"{params['corrupt']}\n"
        f"{params['delay']}\n"
        f"{params['winsize']}\n"
        f"{params['timeout']}\n"
        f"{params['trace']}\n"
        f"{params['seed']}\n"
    )
    
    try:
        # Run java Project with piped input
        result = subprocess.run(
            ["java", "Project"],
            input=input_data,
            capture_output=True,
            text=True,
            timeout=300,  # 5-minute timeout for stress tests
            cwd=os.path.dirname(os.path.abspath(__file__))
        )
        
        # Save full output to file
        log_path = os.path.join(LOG_DIR, filename)
        with open(log_path, "w", encoding="utf-8") as f:
            f.write(result.stdout)
            
        print(f"  ✅ Success | Log saved to: {log_path}")
        return True
        
    except subprocess.TimeoutExpired:
        print(f"  ⚠️  TIMEOUT after 300s. Skipped.")
        return False
    except Exception as e:
        print(f"  ❌ ERROR: {e}")
        return False

def main():
    print("="*60)
    print("PA2 GBN+SACK Test Runner")
    print("="*60)
    print(f"Output directory: {os.path.abspath(LOG_DIR)}")
    print("Ensure Project.class is compiled and in the same directory.")
    print("="*60)
    
    success_count = 0
    for filename, params in TEST_CASES.items():
        if run_test(filename, params):
            success_count += 1
            
    print("\n" + "="*60)
    print(f"Completed: {success_count}/{len(TEST_CASES)} tests successful.")
    print(f"All logs are in: {os.path.abspath(LOG_DIR)}/")
    print("="*60)

if __name__ == "__main__":
    main()