#!/usr/bin/env python3
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from scipy import stats

# Load your pre-generated CSV
df = pd.read_csv("performance_data.csv")

def calculate_ci(data, confidence=0.90):
    """Calculate mean and confidence interval"""
    if len(data) < 2:
        return np.mean(data), 0
    mean = np.mean(data)
    sem = stats.sem(data)
    ci = sem * stats.t.ppf((1 + confidence) / 2., len(data)-1)
    return mean, ci

# Create figure with 2 subplots
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))

# ===== PLOT 1: Comm Time vs Loss (corrupt=0) =====
loss_df = df[df['corrupt'] == 0.0].copy()
loss_groups = loss_df.groupby('loss')['avg_comm_time']

x_loss = sorted(loss_groups.groups.keys())
means_loss = []
cis_loss = []

for loss_val in x_loss:
    data = loss_groups.get_group(loss_val)
    mean, ci = calculate_ci(data)
    means_loss.append(mean)
    cis_loss.append(ci)
    print(f"Loss={loss_val:.2f}: mean={mean:.2f} ±{ci:.2f} (n={len(data)})")

ax1.errorbar(x_loss, means_loss, yerr=cis_loss, fmt='o-', capsize=5, 
             linewidth=2, markersize=8, color='blue', label='90% CI')
ax1.set_xlabel('Packet Loss Probability', fontsize=11)
ax1.set_ylabel('Average Communication Time (time units)', fontsize=11)
ax1.set_title('SR Protocol: Comm Time vs. Loss\n(win=8, timeout=30, corrupt=0)', fontsize=11)
ax1.grid(True, alpha=0.3)
ax1.set_xticks(x_loss)
ax1.set_xticklabels([f'{x:.2f}' for x in x_loss])

# ===== PLOT 2: Comm Time vs Corruption (loss=0) =====
# Note: Your current CSV only has corrupt=0 runs
# If you add corrupt sweeps later, this will auto-generate
corrupt_df = df[df['loss'] == 0.0].copy()
corrupt_groups = corrupt_df.groupby('corrupt')['avg_comm_time']

if len(corrupt_groups) > 1:  # Only plot if we have multiple corruption levels
    x_corr = sorted(corrupt_groups.groups.keys())
    means_corr = []
    cis_corr = []
    
    for corr_val in x_corr:
        data = corrupt_groups.get_group(corr_val)
        mean, ci = calculate_ci(data)
        means_corr.append(mean)
        cis_corr.append(ci)
        print(f"Corrupt={corr_val:.2f}: mean={mean:.2f} ±{ci:.2f} (n={len(data)})")
    
    ax2.errorbar(x_corr, means_corr, yerr=cis_corr, fmt='s-', capsize=5, 
                 linewidth=2, markersize=8, color='orange', label='90% CI')
    ax2.set_xlabel('Packet Corruption Probability', fontsize=11)
    ax2.set_ylabel('Average Communication Time (time units)', fontsize=11)
    ax2.set_title('SR Protocol: Comm Time vs. Corruption\n(win=8, timeout=30, loss=0)', fontsize=11)
    ax2.grid(True, alpha=0.3)
    ax2.set_xticks(x_corr)
    ax2.set_xticklabels([f'{x:.2f}' for x in x_corr])
else:
    ax2.text(0.5, 0.5, 'Add runs with\nloss=0, corrupt>0\nto generate this plot', 
             ha='center', va='center', transform=ax2.transAxes, fontsize=10, style='italic')
    ax2.set_xlabel('Packet Corruption Probability', fontsize=11)
    ax2.set_ylabel('Average Communication Time (time units)', fontsize=11)
    ax2.set_title('SR Protocol: Comm Time vs. Corruption\n(win=8, timeout=30, loss=0)', fontsize=11)
    ax2.grid(True, alpha=0.3)

plt.tight_layout()
plt.savefig('performance_graphs.png', dpi=300, bbox_inches='tight')
print(f"\n💾 Plots saved to: performance_graphs.png")
plt.show()

# Print summary table for your report
print("\n" + "="*70)
print("SUMMARY TABLE (90% Confidence Intervals)")
print("="*70)
print("\n📉 Loss Sweep (corrupt=0):")
print(f"{'Loss':<8} {'Mean Comm Time':<18} {'90% CI':<15} {'Samples'}")
print("-"*50)
for i, loss_val in enumerate(x_loss):
    print(f"{loss_val:<8.2f} {means_loss[i]:<18.2f} ±{cis_loss[i]:<14.2f} {len(loss_groups.get_group(loss_val))}")
print("="*70)