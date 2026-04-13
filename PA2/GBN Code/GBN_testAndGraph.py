#!/usr/bin/env python3
"""
run_gbn_sack.py
Automates GBN+SACK simulations, parses statistics, exports CSV, and generates performance graphs.
Includes proper 90% confidence interval calculations using t-distribution.
"""

import subprocess
import re
import csv
import os
import sys
import numpy as np
import matplotlib.pyplot as plt
from scipy import stats
from dataclasses import dataclass
from typing import List, Dict

@dataclass
class SimResult:
    seed: int
    loss: float
    corrupt: float
    original: int
    retransmissions: int
    delivered: int
    acks_sent: int
    corrupted: int
    lost_ratio: float
    corrupt_ratio: float
    avg_rtt: float
    avg_comm_time: float

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
os.chdir(SCRIPT_DIR)

def run_simulation(config: Dict) -> SimResult:
    """Runs a single GBN+SACK simulation with the given configuration."""
    inputs = "\n".join([
        str(config['messages']),
        str(config['loss']),
        str(config['corrupt']),
        str(config['avg_delay']),
        str(config['window']),
        str(config['timeout']),
        str(config['trace']),
        str(config['seed'])
    ]) + "\n"

    try:
        proc = subprocess.run(
            ['java', 'Project'],
            input=inputs,
            text=True,
            capture_output=True,
            timeout=180,
            cwd=SCRIPT_DIR
        )
        
        if proc.returncode != 0:
            print(f"⚠️  Java error (seed {config['seed']}): {proc.stderr.strip()[:100]}")
            return None
            
        return parse_output(proc.stdout, config)
    except subprocess.TimeoutExpired:
        print(f"⏱️  Timeout (seed {config['seed']}). Skipping.")
        return None
    except Exception as e:
        print(f"❌ Exception (seed {config['seed']}): {e}")
        return None

def parse_output(output: str, config: Dict) -> SimResult:
    """Extracts statistics from the simulator's stdout."""
    patterns = {
        'original': r'Number of original packets transmitted by A:\s*([\d.]+)',
        'retransmissions': r'Number of retransmissions by A:\s*([\d.]+)',
        'delivered': r'Number of data packets delivered to layer 5 at B:\s*([\d.]+)',
        'acks_sent': r'Number of ACK packets sent by B:\s*([\d.]+)',
        'corrupted': r'Number of corrupted packets:\s*([\d.]+)',
        'lost_ratio': r'Ratio of lost packets:\s*([\d.]+)',
        'corrupt_ratio': r'Ratio of corrupted packets:\s*([\d.]+)',
        'avg_rtt': r'Average RTT:\s*([\d.]+)',
        'avg_comm_time': r'Average communication time:\s*([\d.]+)'
    }
    
    stats = {}
    for key, pattern in patterns.items():
        match = re.search(pattern, output)
        stats[key] = float(match.group(1)) if match else 0.0
        
    return SimResult(
        seed=config['seed'], loss=config['loss'], corrupt=config['corrupt'], **stats
    )

def calculate_90_ci(values: List[float]) -> tuple:
    """
    Calculate mean and 90% confidence interval.
    Uses t-distribution for small sample sizes.
    Returns (mean, ci_half_width)
    """
    if not values or len(values) < 2:
        return (np.mean(values) if values else 0.0, 0.0)
    
    n = len(values)
    mean = np.mean(values)
    std = np.std(values, ddof=1)  # Sample standard deviation
    
    # t-value for 90% CI with n-1 degrees of freedom
    t_value = stats.t.ppf(0.95, df=n-1)  # 0.95 for two-tailed 90% CI
    ci_half_width = t_value * std / np.sqrt(n)
    
    return (mean, ci_half_width)

def save_csv(results: List[SimResult], filename: str = "gbn_sack_results.csv"):
    """Writes results to a CSV file."""
    with open(filename, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['seed', 'loss', 'corrupt', 'original', 'retransmissions', 'delivered',
                         'acks_sent', 'corrupted', 'lost_ratio', 'corrupt_ratio', 'avg_rtt', 'avg_comm_time'])
        for r in results:
            writer.writerow([r.seed, r.loss, r.corrupt, r.original, r.retransmissions, r.delivered,
                             r.acks_sent, r.corrupted, r.lost_ratio, r.corrupt_ratio, r.avg_rtt, r.avg_comm_time])
    print(f"💾 Saved CSV to {filename}")

def generate_graphs_with_ci(results: List[SimResult]):
    """Generates side-by-side plots with proper 90% confidence intervals."""
    loss_vals = sorted({r.loss for r in results if r.corrupt == 0.0})
    corr_vals = sorted({r.corrupt for r in results if r.loss == 0.0})
    
    loss_means, loss_ci = [], []
    for l in loss_vals:
        times = [r.avg_comm_time for r in results if r.loss == l and r.corrupt == 0.0]
        mean, ci = calculate_90_ci(times)
        loss_means.append(mean)
        loss_ci.append(ci)
        
    corr_means, corr_ci = [], []
    for c in corr_vals:
        times = [r.avg_comm_time for r in results if r.corrupt == c and r.loss == 0.0]
        mean, ci = calculate_90_ci(times)
        corr_means.append(mean)
        corr_ci.append(ci)
        
    # Create figure with 2 subplots
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 6))
    
    # Plot 1: Communication Time vs Loss
    ax1.errorbar(loss_vals, loss_means, yerr=loss_ci, fmt='o-', capsize=5, 
                 color='blue', linewidth=2, markersize=8)
    ax1.set_title('GBN+SACK: Avg Comm Time vs. Loss\n(win=8, timeout=30, corrupt=0)', fontsize=11)
    ax1.set_xlabel('Packet Loss Probability', fontsize=10)
    ax1.set_ylabel('Average Communication Time (time units)', fontsize=10)
    ax1.grid(True, alpha=0.3)
    ax1.set_xlim(-0.01, max(loss_vals) + 0.01)
    
    # Plot 2: Communication Time vs Corruption
    ax2.errorbar(corr_vals, corr_means, yerr=corr_ci, fmt='s-', capsize=5, 
                 color='orange', linewidth=2, markersize=8)
    ax2.set_title('GBN+SACK: Avg Comm Time vs. Corruption\n(win=8, timeout=30, loss=0)', fontsize=11)
    ax2.set_xlabel('Packet Corruption Probability', fontsize=10)
    ax2.set_ylabel('Average Communication Time (time units)', fontsize=10)
    ax2.grid(True, alpha=0.3)
    ax2.set_xlim(-0.01, max(corr_vals) + 0.01)
    
    plt.tight_layout()
    plt.savefig('gbn_sack_performance.png', dpi=300, bbox_inches='tight')
    print("📊 Saved graph to gbn_sack_performance.png")
    plt.show()
    
    # Print summary statistics
    print("\n" + "="*70)
    print("SUMMARY STATISTICS (90% Confidence Intervals)")
    print("="*70)
    print(f"\n{'Loss':<8} {'Mean':<12} {'90% CI':<15} {'Samples':<8}")
    print("-"*50)
    for i, l in enumerate(loss_vals):
        print(f"{l:<8.2f} {loss_means[i]:<12.2f} ±{loss_ci[i]:<14.2f} {len([r for r in results if r.loss == l and r.corrupt == 0.0]):<8}")
    
    print(f"\n{'Corrupt':<8} {'Mean':<12} {'90% CI':<15} {'Samples':<8}")
    print("-"*50)
    for i, c in enumerate(corr_vals):
        print(f"{c:<8.2f} {corr_means[i]:<12.2f} ±{corr_ci[i]:<14.2f} {len([r for r in results if r.corrupt == c and r.loss == 0.0]):<8}")
    print("="*70)

def main():
    # Experiment matrix (5 seeds per config for statistical confidence)
    seeds = [100, 200, 300, 400, 500]
    configs = [
        {'messages': 500, 'avg_delay': 20, 'window': 8, 'timeout': 30, 'trace': 0, 'loss': 0.0, 'corrupt': 0.0},
        {'messages': 500, 'avg_delay': 20, 'window': 8, 'timeout': 30, 'trace': 0, 'loss': 0.1, 'corrupt': 0.0},
        {'messages': 500, 'avg_delay': 20, 'window': 8, 'timeout': 30, 'trace': 0, 'loss': 0.2, 'corrupt': 0.0},
        {'messages': 500, 'avg_delay': 20, 'window': 8, 'timeout': 30, 'trace': 0, 'loss': 0.3, 'corrupt': 0.0},
        {'messages': 500, 'avg_delay': 20, 'window': 8, 'timeout': 30, 'trace': 0, 'loss': 0.0, 'corrupt': 0.1},
        {'messages': 500, 'avg_delay': 20, 'window': 8, 'timeout': 30, 'trace': 0, 'loss': 0.0, 'corrupt': 0.2},
        {'messages': 500, 'avg_delay': 20, 'window': 8, 'timeout': 30, 'trace': 0, 'loss': 0.0, 'corrupt': 0.3},
    ]
    
    all_results = []
    total_runs = len(configs) * len(seeds)
    current = 0
    
    print("🚀 Starting GBN+SACK Automation...")
    for cfg in configs:
        for seed in seeds:
            current += 1
            run_cfg = cfg.copy()
            run_cfg['seed'] = seed
            print(f"[{current}/{total_runs}] loss={run_cfg['loss']} | corrupt={run_cfg['corrupt']} | seed={seed}")
            res = run_simulation(run_cfg)
            if res:
                all_results.append(res)
                
    if not all_results:
        print("❌ No successful simulations. Check that Project.class is compiled.")
        sys.exit(1)
        
    save_csv(all_results)
    generate_graphs_with_ci(all_results)
    print("✅ Done!")

if __name__ == '__main__':
    main()