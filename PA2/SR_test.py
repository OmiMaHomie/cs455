#!/usr/bin/env python3
import subprocess
import csv
import re
import time
import sys

# ==========================================
# CONFIGURATION
# ==========================================
JAVA_CMD = ["java", "-cp", "PA2_helper/PA2-Java/", "Project"]

# Fixed parameters
MSGS = 1000
AVG_DELAY = 200
WINSIZE = 8
TIMEOUT = 30.0
TRACE = 0
SEEDS = [100, 200, 300, 400, 500]

# Sweep parameters
LOSS_PROBS = [0.0, 0.05, 0.1, 0.15, 0.2]
CORRUPT_PROBS = [0.0, 0.05, 0.1, 0.15, 0.2]

OUTPUT_CSV = "performance_data.csv"
TIMEOUT_PER_RUN = 300

def run_single_test(loss, corrupt, seed):
    # Format input string
    inputs = f"{MSGS}\n{loss}\n{corrupt}\n{AVG_DELAY}\n{WINSIZE}\n{TIMEOUT}\n{TRACE}\n{seed}\n"
    
    try:
        result = subprocess.run(
            JAVA_CMD,
            input=inputs,
            text=True,
            capture_output=True,
            timeout=TIMEOUT_PER_RUN
        )
        
        output = result.stdout
        rtt_match = re.search(r"Average RTT:\s*([\d.]+)", output)
        comm_match = re.search(r"Average communication time:\s*([\d.]+)", output)
        
        if rtt_match and comm_match:
            return float(rtt_match.group(1)), float(comm_match.group(1))
        else:
            print(f"⚠️  WARNING: Failed to parse stats for loss={loss}, corrupt={corrupt}, seed={seed}")
            return None, None
            
    except subprocess.TimeoutExpired:
        print(f"⏱️  TIMEOUT: loss={loss}, corrupt={corrupt}, seed={seed}")
        return None, None
    except Exception as e:
        print(f"❌ ERROR: {e}")
        return None, None

def main():
    print("🚀 Starting Automated Performance Data Collection...")
    
    results = []
    
    # ==========================================
    # SWEEP 1: Loss (corrupt=0)
    # ==========================================
    print("\n--- Phase 1: Loss Sweep (corrupt=0) ---")
    for loss in LOSS_PROBS:
        for seed in SEEDS:
            print(f"🔄 Running: loss={loss:.2f} | corrupt=0.0 | seed={seed}... ", end="", flush=True)
            avg_rtt, avg_comm = run_single_test(loss, 0.0, seed)
            
            if avg_rtt is not None:
                results.append({
                    "seed": seed,
                    "loss": loss,
                    "corrupt": 0.0,
                    "avg_rtt": avg_rtt,
                    "avg_comm_time": avg_comm
                })
                print(f"✅ Comm Time: {avg_comm:.2f}")
            else:
                print("❌ Failed")
            time.sleep(0.2)

    # ==========================================
    # SWEEP 2: Corruption (loss=0)
    # ==========================================
    print("\n--- Phase 2: Corruption Sweep (loss=0) ---")
    # Skip corrupt=0.0 because it's the same as loss=0.0, corrupt=0.0 from Phase 1
    for corrupt in CORRUPT_PROBS:
        if corrupt == 0.0: continue 
        
        for seed in SEEDS:
            print(f"🔄 Running: loss=0.0 | corrupt={corrupt:.2f} | seed={seed}... ", end="", flush=True)
            avg_rtt, avg_comm = run_single_test(0.0, corrupt, seed)
            
            if avg_rtt is not None:
                results.append({
                    "seed": seed,
                    "loss": 0.0,
                    "corrupt": corrupt,
                    "avg_rtt": avg_rtt,
                    "avg_comm_time": avg_comm
                })
                print(f"✅ Comm Time: {avg_comm:.2f}")
            else:
                print("❌ Failed")
            time.sleep(0.2)

    # Save to CSV
    if results:
        with open(OUTPUT_CSV, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=["seed", "loss", "corrupt", "avg_rtt", "avg_comm_time"])
            writer.writeheader()
            writer.writerows(results)
        
        print(f"\n📊 DONE! Successfully collected {len(results)} data points.")
        print(f"💾 Results saved to: {OUTPUT_CSV}")
        print(f"📈 Next step: Run `python3 generate_graphs.py`. It will now automatically generate BOTH plots!")
    else:
        print("\n❌ No data collected.")

if __name__ == "__main__":
    main()