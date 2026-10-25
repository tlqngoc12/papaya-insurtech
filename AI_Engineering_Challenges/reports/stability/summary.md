# Stability across seeds

Computed by `scripts/StabilitySummary.java` from `seed-*/metrics.json`, each produced by `all` with `config/frozen-config.json` (T frozen on the tuning seed 42). Percentages; SD is the sample standard deviation (n - 1) in percentage points.

| Seed | T | Recall | FPR (standard) | FDR | Precision | Lowest pattern recall |
|---:|---:|---:|---:|---:|---:|---|
| 101 | 7 | 98.0% | 4.0% | 26.9% | 73.1% | 86.7% (upcoding) |
| 102 | 7 | 97.5% | 3.9% | 26.4% | 73.6% | 83.3% (upcoding) |
| 103 | 7 | 98.5% | 4.6% | 29.6% | 70.4% | 90.0% (upcoding) |
| 104 | 7 | 96.5% | 4.0% | 27.2% | 72.8% | 76.7% (upcoding) |
| 105 | 7 | 97.5% | 4.7% | 30.1% | 69.9% | 83.3% (upcoding) |

| Metric | Mean | SD (pp) | Min | Max |
|---|---:|---:|---:|---:|
| Recall | 97.6% | 0.74 | 96.5% | 98.5% |
| FPR (standard) | 4.2% | 0.37 | 3.9% | 4.7% |
| FDR | 28.0% | 1.70 | 26.4% | 30.1% |
| Precision | 72.0% | 1.70 | 69.9% | 73.6% |
| Lowest pattern recall | 84.0% | 4.94 | 76.7% | 90.0% |
