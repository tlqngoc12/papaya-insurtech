# Fraud Detection Scoring Engine (AI Challenge 10)

A rule-based engine that scores insurance claims from 0 to 100. Each flag it raises carries a line of evidence. It is written in Java 21 with Maven, JUnit 5 and Jackson, and uses no other library.

Every result number in this README was produced by the program, and each table names the file it comes from. Timeline numbers come from `git log` or are my own estimates, and are marked as such. All paths are relative to `AI_Engineering_Challenges/`.

## Contents

1. [Results summary](#1-results-summary)
2. [How to run](#2-how-to-run)
3. [Design](#3-design)
4. [Data](#4-data)
5. [Method](#5-method)
6. [Detailed results](#6-detailed-results)
7. [Why these weights](#7-why-these-weights)
8. [Interpretations and limitations](#8-interpretations-and-limitations)
9. [Timeline](#9-timeline)
10. [AI usage](#10-ai-usage)

---

## 1. Results summary

The main numbers come from the **held-out** dataset (seed 2024). Every setting used on it was chosen on a separate tuning dataset (seed 42) and frozen before the held-out data was generated (see [section 5](#5-method)).

*Source: `reports/heldout/metrics.json`.*

| Metric | Held-out (seed 2024, T = 7) | Challenge target | Result |
|---|---:|---|---|
| Recall | 96.5% (193 of 200) | ≥ 70% | **PASS** |
| FPR, standard reading: FP / all legit claims | 4.4% (80 of 1,800) | ≤ 20% | **PASS** |
| FDR, other reading: FP / flagged claims | 29.3% (80 of 273) | ≤ 20% | **FAIL** |
| Precision | 70.7% | none (reported only) | |
| F1 | 81.6% | none | |

The challenge does not define "false positive rate", so I report both readings:

- **Standard reading (FP / legit):** passes easily.
- **"Share of alerts that are wrong" (FDR):** fails. 29.3% of flagged claims are legit.

Precision is 70.7%. That is below my first internal goal of 75%. Before any run, I changed that goal to "report only" (decision Q17 in `docs/design-decisions.md`).

**Stability over five more seeds (101–105), same frozen config.**

*Source: `reports/stability/summary.md`.*

| Metric | Mean | SD (percentage points) | Min | Max |
|---|---:|---:|---:|---:|
| Recall | 97.6% | 0.74 | 96.5% | 98.5% |
| FPR (standard) | 4.2% | 0.37 | 3.9% | 4.7% |
| FDR | 28.0% | 1.70 | 26.4% | 30.1% |
| Precision | 72.0% | 1.70 | 69.9% | 73.6% |
| Lowest recall of one pattern | 84.0% | 4.94 | 76.7% | 90.0% |

FDR is above 20% on every seed. The weakest pattern is always upcoding.

**Where the false positives come from (held-out):**

- 26 legit claims were planted on purpose to trigger a rule: 20 in the clustering band and 6 on the phantom day.
- 54 legit claims triggered a rule by chance: rapid re-submission 22, upcoding 17, amount clustering 15.

Details are in [section 6](#6-detailed-results).

**My reading.** Under the usual definition the engine meets the target (FPR 4.4%). It fails the other reading (FDR 29.3%), and precision is 70.7%, below the 75% I first hoped for. I kept the threshold rule that I fixed before tuning, and I report both readings instead of changing the rule after seeing the numbers. All 80 false flags come from four rules: amount clustering 35, rapid re-submission 22, upcoding 17 and phantom billing 6. The four table-based rules (duplicate, unbundling, weekend, mismatch) have no false flags. For real use, flagged claims should go to a human investigator first, and T should be chosen with the investigators' capacity in mind, not on synthetic data. If I changed one thing first, it would be upcoding: it is the only pattern with missed fraud, and its results move the most between seeds (L9).

**Other criteria:**

- **Tests:** `mvn -q test` runs 193 tests in 37 classes, all passing. Every rule has its own test class (`src/test/java/com/papaya/fraud/engine/rules/`).
- **Speed:** in the held-out run, 2,000 claims took 46 ms to load, 159 ms to score and 48 ms to write (`reports/heldout/metrics_report.md`, Timing section; these times change on every run). A separate test scores 100,000 claims under a 30 s limit; its last run took 2,414 ms.

---

## 2. How to run

You need JDK 21 and Maven 3.9 or newer. Run every command from `AI_Engineering_Challenges/`.

### Build

```bash
mvn -q package          # builds target/fraud-engine.jar and runs all tests
mvn -q package -DskipTests   # build only
```

The commands below work the same way on Linux, macOS and Windows (PowerShell or cmd), because `java` accepts `/` in paths on every system.

### Commands

| Command | What it does | Reads ground truth? |
|---|---|---|
| `generate` | Writes `claims.csv` and `ground_truth.csv` for a seed | writes it |
| `score` | Scores claims and writes the ranked `scored_claims.json` | **no** |
| `tune` | Picks threshold T on the tuning data and writes `frozen-config.json` and `threshold-sweep.json` | yes |
| `evaluate` | Scores claims and writes `scored_claims.json`, `metrics.json`, `metrics_report.md` | yes, only in the metrics step |
| `all` | `generate` + `evaluate` for one seed, with the frozen config | yes |

```bash
# 1. Generate a dataset (into out/, so the committed data/ stays untouched)
java -jar target/fraud-engine.jar generate --seed 2024 --out out/data-2024

# 2. Score the committed held-out claims (no labels used)
java -jar target/fraud-engine.jar score --claims data/heldout/claims.csv --config config/frozen-config.json --out out/scored_claims.json

# 3. Tune T on the tuning data (refuses to overwrite an existing file unless you add --force)
java -jar target/fraud-engine.jar tune --claims data/tuning/claims.csv --ground-truth data/tuning/ground_truth.csv --out out/frozen-config.json

# 4. Evaluate with the frozen config
java -jar target/fraud-engine.jar evaluate --claims data/heldout/claims.csv --ground-truth data/heldout/ground_truth.csv --config config/frozen-config.json --out-dir out/heldout --seed 2024

# 5. Everything for one seed in one step
java -jar target/fraud-engine.jar all --seed 2024 --config config/frozen-config.json --out-dir out/all-2024

# Tests
mvn -q test
```

Every command creates its output folder if it does not exist. `--seed` on `evaluate` is only a label for the report. `java -jar target/fraud-engine.jar help` prints the list of commands.

**Expected output.** `evaluate` and `all` print one summary line. For seed 2024 it is:

```
T = 7: recall 96.5%, FPR 4.4%, FDR 29.3%, precision 70.7% (TP 193, FP 80, FN 7, TN 1720)
```

`tune` on the tuning data selects T = 7. The committed `config/threshold-sweep.json` shows the full sweep from that run.

### Check that you get the same files

Output is deterministic. The same seed and config give the same bytes.

- `metrics_report.md` is the one exception. Only its "Timing" section changes between runs.
- `metrics.json` holds no timings and no file paths. Only SHA-256 hashes identify the inputs.

Run `all` for seed 2024 (step 5), then compare hashes:

```bash
# Linux / macOS
sha256sum out/all-2024/claims.csv out/all-2024/ground_truth.csv out/all-2024/scored_claims.json out/all-2024/metrics.json
```

```powershell
# Windows PowerShell
Get-FileHash out/all-2024/claims.csv, out/all-2024/ground_truth.csv, out/all-2024/scored_claims.json, out/all-2024/metrics.json -Algorithm SHA256
```

Expected values. These are the hashes of the committed files:

| File | SHA-256 |
|---|---|
| `data/heldout/claims.csv` | `452adbf0ea83e467ca610b7cb565b58a454bb937aa6f33876a35d7bd4d441a32` |
| `data/heldout/ground_truth.csv` | `217d6c33eb3c8e96e14e5d7f83351fac16ac1129428b8a7acbaec3ab0476f4c3` |
| `reports/heldout/scored_claims.json` | `6bf48c7e25673bb779a01ceb32540cd76ea94a206cf5915f88cf90e4b2f8d9af` |
| `reports/heldout/metrics.json` | `d7d7e538d59da38216f352760a26f63bfecae08b287b7faeef5f0a0ea1bd92a2` |
| `data/tuning/claims.csv` | `43840b63c6795eb66e63a7150ad711a105a3ee3ebd2046c1802c136263af18a1` |
| `data/tuning/ground_truth.csv` | `ac88a63df80b50a23f590e1f7cf8a7324dc503441ed3e3f3590a8193560f13a9` |
| `reports/tuning/scored_claims.json` | `b9e7e4fe0006d2c86e668a94c11e5e716dcaa6da168fb1d80b80da43423f7096` |
| `reports/tuning/metrics.json` | `99570c069bbc8f2ba021e03b43018ff4c495c807ed3c560a0a62048962f6575d` |
| `config/frozen-config.json` | `114e7f8a62011c34026380ab8dcf44987afa41d4bf4054826c5098deb52e9cb0` |

The repository uses LF line endings (`.gitattributes`). A checkout that turns them into CRLF changes the hashes of the committed files, but not of files you generate yourself.

`scripts/StabilitySummary.java` rebuilds `reports/stability/summary.md` from the five `seed-*/metrics.json` files:

```bash
java -cp target/fraud-engine.jar scripts/StabilitySummary.java reports/stability
```

### Repository layout

| Path | Content |
|---|---|
| `data/tuning/`, `data/heldout/` | Datasets (seed 42, seed 2024): `claims.csv` and `ground_truth.csv` |
| `reports/tuning/`, `reports/heldout/` | `scored_claims.json` (all 2,000 claims, ranked), `metrics.json`, `metrics_report.md` |
| `reports/stability/` | `metrics.json` and `metrics_report.md` for seeds 101–105, plus `summary.md` |
| `config/frozen-config.json` | Weights, rule parameters and T, frozen on the tuning data |
| `src/main/resources/fraud-config.json` | The same weights and parameters, without T |
| `src/main/resources/catalog/` | Diagnoses, procedures, bundles, diagnosis–procedure whitelist |
| `docs/design-decisions.md` | Every design decision, with the options I compared (in Vietnamese) |
| `docs/limitations.md` | Known limitations L1–L16 (in Vietnamese) |
| `docs/generator-spec.md` | How the dataset is generated (in Vietnamese) |
| `docs/ai-log/` | Raw logs of the AI sessions |

---

## 3. Design

### Engine

The engine runs in two phases:

1. **Index.** One pass builds a `DatasetIndex`: claims grouped by member and diagnosis, by provider and day, by procedure, plus weekend counts per provider.
2. **Rules.** Each of the 8 rules looks at one claim and the index. It returns at most one flag.

The engine never reads `ground_truth.csv`. An architecture test checks this (`GroundTruthIsolationTest`), and the claims reader refuses a file with a label column such as `is_fraud`.

### The 8 rules

*Source: weights and parameters from `config/frozen-config.json`. Evidence examples are copied from `reports/heldout/scored_claims.json`.*

| Rule | Weight | Fires when | Boundary (fires / does not fire) |
|---|:---:|---|---|
| `duplicate_claim` | 5 | Same member, provider, date and diagnosis as an earlier claim. Only the later copies are flagged. | |
| `rapid_resubmission` | 2 | Same member and diagnosis as an earlier claim within 7 days, any provider. Pairs that are already duplicates are skipped. | 7 days / 8 days |
| `upcoding` | 4 | Single-procedure claim whose amount is more than 2 SD above the mean of claims with that same single procedure. Groups need at least 10 claims. | z > 2.0 / z = 2.0 |
| `unbundling` | 4 | The claim has every component of a bundle, or a bundle code together with one of its components. 6 bundles. | all components / one missing |
| `phantom_billing` | 5 | The provider has more than 30 claims on that day. Every claim of that provider-day is flagged. | 31 / 30 |
| `weekend_anomaly` | 3 | Weekend claim with a surgical procedure, at a provider whose weekend share of its *other* claims is below 5%. Needs at least 20 other claims. | < 5% / = 5% |
| `diagnosis_procedure_mismatch` | 3 | A known procedure is not on the whitelist for the claim's diagnosis. 64 specific pairs plus 2 general codes. | |
| `amount_clustering` | 2 | Amount is in 47,500–49,999, just under the 50,000 auto-approval threshold. | 47,500 / 47,499 and 50,000 |

Each boundary has a pair of tests: one value that fires and one that does not.

**One evidence example per rule (held-out output):**

- **duplicate_claim:** "Duplicate of CLM-00697 (occurrence 2 of 2): same member M-0222, provider PRV-32, date 2024-05-06 and diagnosis S72.0; submitted 95,787 vs 95,787 on the original"
- **rapid_resubmission:** "Re-submitted 2 days after CLM-01894 (2024-12-09, provider PRV-13) for the same member M-0135 and diagnosis I21.9; 1 earlier claim within the 7-day window"
- **upcoding:** "Submitted amount 612 for procedure P-1004 is 2.05 standard deviations above the mean of 488.4 (SD 60.2, n = 44 single-procedure claims of P-1004; threshold z > 2.0)"
- **unbundling:** "Unbundling on 1 bundle: all components of bundle P-2102 billed separately: P-2007, P-2004, P-2005 (37,000 at base price vs bundle price 32,000)"
- **phantom_billing:** "Provider PRV-07 submitted 31 claims on 2024-09-20, more than the maximum of 30 claims per day; every claim of that provider-day is flagged"
- **weekend_anomaly:** "Surgical procedure P-2013 (Total knee replacement) on Sunday 2024-12-01 at provider PRV-38, which has 1 weekend claim out of 73 other claims (1.3%), below the 5.0% weekend threshold"
- **diagnosis_procedure_mismatch:** "Procedure P-2013 (Total knee replacement) is not clinically associated with diagnosis O80 (Single spontaneous delivery); valid procedures for O80: P-0001, P-2001, P-2008"
- **amount_clustering:** "Amount 49,980 is 99.9% of the 50,000 auto-approval threshold (clustering band 47,500-49,999)"

The evidence gives the real values and the reference values, so a reviewer can check the flag by hand. For example, (612 − 488.4) / 60.2 = 2.05.

### Score

```
risk_score = round_half_up( 100 × (sum of weights of the rules that fired) / (sum of all 8 weights) )
```

The sum of all weights is 28. The code uses integers only: `(200·S + 28) / (2·28)`, where S is the sum of fired weights.

**Example:** a claim flagged by upcoding (4) and amount clustering (2), like the example in the challenge, gets round(100 × 6 / 28) = round(21.4) = **21**.

The challenge example shows 78 for the same two flags. I did not match it. A plain "weighted sum normalized to 0–100" gives 21, and the challenge does not say how it reached 78. (Decisions S1 and Q22.)

A single rule gives: weight 2 → 7, weight 3 → 11, weight 4 → 14, weight 5 → 18.

`severity` in each flag is the rule's weight. Each rule adds its weight at most once per claim.

### Ranking

`scored_claims.json` lists all 2,000 claims, including those with no flag. The order is:

1. score, high to low;
2. number of flags, high to low;
3. amount, high to low (bigger claims are worth checking first);
4. `claim_id`, A to Z (so the order is always the same).

---

## 4. Data

There is no real data, so a generator builds it (`generate`).

### What the generator produces

*Sources: `docs/generator-spec.md`; counts from `legit_by_scenario` and `recall_by_pattern` in `reports/heldout/metrics.json`.*

- Exactly 2,000 claims, 500 members and 50 providers.
- Provider tiers: 8 hospitals that only work on weekdays, 4 normal hospitals, 28 clinics, 10 dental clinics.
- Claim types: about 55% outpatient, 25% inpatient, 20% dental.
- Claims are generated in a chain: provider → claim type → member → diagnosis → valid procedures → amount → date. This keeps a dental clinic from billing heart surgery.
- Amount = base price × a normal factor (mean 1, SD 0.15, cut at ±3 SD). Some legit claims therefore land above 2 SD by chance, as real data would.
- Diagnoses use real ICD-10 codes (21 codes). Procedures use internal codes `P-xxxx` (40 codes), because real CPT codes are licensed.
- `claims.csv` has exactly the 10 columns of the challenge. Labels are in a separate `ground_truth.csv` with 5 columns: `claim_id, is_fraud, fraud_pattern, related_claim_id, scenario`.

### The 200 fraud claims

| Pattern | Claims | How they are made |
|---|---:|---|
| duplicate_claim | 30 | Exact copy of a legit claim. Only the copy is labeled fraud. |
| rapid_resubmission | 30 | Same member and diagnosis, 1–7 days after a legit claim; same provider about 70% of the time. |
| upcoding | 30 | 20 claims at 1.5–2.5 × base price, 10 milder ones at 1.2–1.6 ×. |
| unbundling | 25 | 20 with all components of a bundle, 5 with the bundle code plus a component. |
| phantom_billing | 25 | 25 claims on one day at one clinic, plus 6 legit claims that day, so 31 in total. |
| weekend_anomaly | 20 | Weekend surgery at the weekday-only hospitals, 2–3 per hospital. |
| diagnosis_procedure_mismatch | 20 | One procedure swapped for one of the same claim type that does not fit the diagnosis. |
| amount_clustering | 20 | Amount uniform in 47,500–49,999. |

Each fraud claim is built to match one pattern only. A test runs the engine on generated data and reports how many fraud claims match two or more rules. It only reports this; nothing is filtered based on it.

### The 1,800 legit claims

| Group | Claims | Purpose |
|---|---:|---|
| Ordinary (`none`) | 1,719 | Normal claims. Some trigger a rule by chance and count as false positives. |
| `legit_noise` | 26 | Legit claims placed on purpose to trigger a rule: 20 in the clustering band, 6 on the phantom day. |
| `legit_near_miss` | 55 | Legit claims just outside a rule: a clinic with exactly 30 claims in a day (30 claims), 8 days apart, 47,000–47,499 or 50,000–50,500, part of a bundle, weekend surgery at a normal hospital, same day with another diagnosis, amount near +2 SD. |

### Generator independence

The generator shares the reference catalog with the engine (codes, bundles, whitelist), but nothing else:

- It does not import the engine or the engine config. An architecture test checks this.
- It has its own config file with different key names.
- When a random choice breaks one of the generator's own rules, it draws again, at most 1,000 times. It never looks at engine results and never filters the dataset after the fact.

### Seeds

| Seed | Role |
|---|---|
| 42 | Tuning. T was chosen here. |
| 2024 | Held-out. Main results. |
| 101–105 | Stability check, with the frozen config. |

---

## 5. Method

### Freeze protocol

The rule was simple: choose everything on the tuning data, freeze it, and only then look at held-out data. Nothing is changed after seeing held-out results.

The commit order shows this:

| Commit | Step |
|---|---|
| `c93d367` | Tuning data (seed 42), tuning reports and `config/frozen-config.json` with T = 7. Tagged **`frozen-before-heldout`**. |
| `68e6309` | `HeldOutIntegrationTest` written before any held-out data existed. The test asserts recall ≥ 70% and FPR ≤ 20% and was committed before its first run. |
| (not committed) | Held-out data generated and evaluated. |
| `e2efa0d`, `cfdf676` | Format-only change, see below. |
| `530897c` | Held-out data, held-out reports and stability reports committed. |

You can check that no rule, weight, parameter or generator code changed after the tag:

```bash
git diff --stat frozen-before-heldout HEAD -- AI_Engineering_Challenges/src/main AI_Engineering_Challenges/config AI_Engineering_Challenges/data/tuning
```

This lists three files in `metrics/` (`Evaluation.java`, `MetricsReportWriter.java`, `ReportContext.java`), which write the report, and `io/ScoredOutputWriter.java`, which now creates the output folder (see below). None of them changes a score.

### How T was chosen

The engine gives a score. A claim counts as "predicted fraud" when `risk_score >= T`.

The selection rule was fixed before the tuning run (decision S3):

1. Keep only the values of T where standard FPR ≤ 20%.
2. Among those, pick the one with the highest recall.
3. Break ties by the lowest FPR, then by the largest T.

*Source: `config/threshold-sweep.json` (tuning data, seed 42).*

| T | TP | FP | Recall | FPR (standard) | FDR |
|---:|---:|---:|---:|---:|---:|
| 1–7 | 196 | 60 | 98.0% | 3.3% | 23.4% |
| 8–11 | 146 | 15 | 73.0% | 0.8% | 9.3% |
| 12–14 | 106 | 15 | 53.0% | 0.8% | 12.4% |

T = 1 to 7 all flag exactly the same claims, because the lowest non-zero score is 7. The rule picks T = 7.

T = 11 would pass both FPR readings on tuning. It was not chosen, for three reasons:

- Recall drops to 73.0%, only 3 points above the target.
- Rapid re-submission and amount clustering (weight 2, score 7) can never be caught alone.
- It breaks my internal goal of recall ≥ 85%.

The selection rule follows the challenge's words, so I kept it and report both readings instead of changing the rule after seeing the numbers. (Decision S3, Task 17.)

The upcoding method was also reviewed on tuning. The rule set before the run was: switch to median/MAD only if upcoding recall < 70%. It was 86.7%, so the z-score method stayed (decision R4).

### Changes made during the held-out step

*Source: `docs/limitations.md`, L16.*

**Before looking at held-out data:** the 100,000-claim speed test had a 120 s limit. My decision Q19 said < 30 s, so I changed the limit to 30 s (commit `68e6309`).

**After seeing the held-out summary line** (recall 96.5%, FPR 4.4%), I found a format problem. `metrics.json` held the input file paths, so the same data at two different paths gave two different files. I fixed only the format (commit `e2efa0d`):

- `metrics.json` now names inputs by SHA-256 only.
- `metrics_report.md` shows file names only, never a full path.
- A new test checks that the same data at two paths gives a byte-identical `metrics.json`.

No rule, weight, parameter, T or generator code changed. I checked that no score or metric moved:

- `reports/tuning/scored_claims.json` kept its hash `b9e7e4fe…7096`. It was regenerated in commit `cfdf676`.
- `reports/heldout/scored_claims.json` kept the same hash as before the fix, `6bf48c7e…d9af`.
- Both `metrics.json` files only lost the two path lines. Every value stayed the same.
- After the fix, `evaluate` and `all --seed 2024` produce four byte-identical files.

**While writing this README:** `score` was the only command that did not create its output folder. I made it create the folder (commit "Task 19a"). This only creates a folder; the output bytes do not change. Scoring the held-out claims into a new folder gives the same `scored_claims.json` hash, `6bf48c7e…d9af`.

---

## 6. Detailed results

All tables in this section are for the held-out dataset (seed 2024, T = 7) unless the title says otherwise.

### Recall by pattern

*Source: `recall_by_group` and `recall_by_pattern` in `reports/heldout/metrics.json`.*

I split the patterns into two groups:

- **Reference-table rules** match against a table or a fixed key that the generator also uses (bundles, whitelist, the four duplicate fields). Their recall is high by construction, so it proves little (L10).
- **Statistical and threshold rules** are the ones that can really miss.

| Group | Pattern | Claims | Caught | Recall |
|---|---|---:|---:|---:|
| Reference table | duplicate_claim | 30 | 30 | 100.0% |
| | unbundling | 25 | 25 | 100.0% |
| | diagnosis_procedure_mismatch | 20 | 20 | 100.0% |
| | **group total** | **75** | **75** | **100.0%** |
| Statistical | rapid_resubmission | 30 | 30 | 100.0% |
| | upcoding | 30 | 23 | **76.7%** |
| | phantom_billing | 25 | 25 | 100.0% |
| | weekend_anomaly | 20 | 20 | 100.0% |
| | amount_clustering | 20 | 20 | 100.0% |
| | **group total** | **125** | **118** | **94.4%** |

All 7 missed fraud claims are upcoding claims.

Causes of upcoding misses, found on the tuning data (L9):

- Mild upcoding (1.2–1.6 ×) often stays under 2 SD.
- Masking: the mean and SD include the claim itself, so a large amount inflates the SD it is measured against. Several upcoded claims in one group hide each other.

### Precision by rule

*Source: `precision_by_rule` in `reports/heldout/metrics.json`.*

| Rule | Claims flagged | Of which fraud | Precision |
|---|---:|---:|---:|
| duplicate_claim | 30 | 30 | 100.0% |
| rapid_resubmission | 52 | 30 | 57.7% |
| upcoding | 41 | 24 | 58.5% |
| unbundling | 25 | 25 | 100.0% |
| phantom_billing | 31 | 25 | 80.6% |
| weekend_anomaly | 20 | 20 | 100.0% |
| diagnosis_procedure_mismatch | 20 | 20 | 100.0% |
| amount_clustering | 55 | 20 | 36.4% |

One fraud claim matched two rules. CLM-01472 is a phantom-billing claim that also got an upcoding flag (score 32). That is why upcoding shows 24 fraud claims but only 23 upcoding claims.

### False positives by scenario

*Source: `legit_by_scenario` in `reports/heldout/metrics.json`.*

| Label | Scenario | Claims | Flagged (score ≥ 7) | Rules |
|---|---|---:|---:|---|
| legit_noise | band_noise (planted in clustering band) | 20 | 20 | amount_clustering 20 |
| legit_noise | phantom_day_legit (same day as phantom) | 6 | 6 | phantom_billing 6 |
| none | base (ordinary claims) | 1,585 | 27 | upcoding 17, rapid_resubmission 10 |
| none | band_host (ordinary claims in the clustering groups) | 122 | 15 | amount_clustering 15 |
| none | natural_followup (ordinary follow-up visits) | 12 | 12 | rapid_resubmission 12 |
| legit_near_miss | all 8 near-miss scenarios | 55 | 0 | none |
| | **total** | **1,800** | **80** | |

No near-miss claim was flagged. That only shows the generator and the engine agree on where each boundary is (L14). It is not proof that the rules work on real data.

### Ranking quality: precision@k

*Source: `precision_at_k` in `reports/heldout/metrics.json`.*

Fraud share in the top k claims: the engine ranking against the same flags re-scored with every weight set to 1.

| k | Engine weights | All weights = 1 |
|---:|---:|---:|
| 50 | 90.0% (45) | 64.0% (32) |
| 100 | 89.0% (89) | 63.0% (63) |
| 200 | 78.5% (157) | 73.0% (146) |

Weights were not tuned on this. It is only reported.

### Rule coverage

*Source: `coverage` in `reports/heldout/metrics.json`.*

| Rule | Checked | Skipped |
|---|---:|---|
| upcoding | 1,658 | 337 multi-procedure claims, 5 in groups under 10 claims |
| unbundling | 337 | 1,663 single-procedure claims |
| weekend_anomaly | 48 | 0 (short history) |
| diagnosis_procedure_mismatch | 2,000 | 0 |

### Tuning vs held-out

*Sources: `reports/tuning/metrics.json` and `reports/heldout/metrics.json`. The differences were computed from the two files.*

| Metric | Tuning (seed 42) | Held-out (seed 2024) | Difference (pp) |
|---|---:|---:|---:|
| TP / FP / FN / TN | 196 / 60 / 4 / 1,740 | 193 / 80 / 7 / 1,720 | |
| Recall | 98.0% | 96.5% | −1.5 |
| FPR (standard) | 3.3% | 4.4% | +1.1 |
| FDR | 23.4% | 29.3% | +5.9 |
| Precision | 76.6% | 70.7% | −5.9 |
| F1 | 86.0% | 81.6% | −4.4 |
| Recall, statistical group | 96.8% | 94.4% | −2.4 |
| Recall, upcoding | 86.7% | 76.7% | −10.0 |
| Precision, upcoding | 74.3% | 58.5% | −15.7 |
| Precision, rapid_resubmission | 62.5% | 57.7% | −4.8 |
| Precision, amount_clustering | 42.6% | 36.4% | −6.2 |
| precision@50 / @100 / @200 (engine) | 92.0 / 91.0 / 81.0% | 90.0 / 89.0 / 78.5% | −2.0 / −2.0 / −2.5 |

Not in the table, because they did not change (difference 0.0): recall of the reference-table group, recall of the other 7 patterns, and the precision of duplicate, unbundling, phantom, weekend and mismatch.

Upcoding moves the most between seeds. Its result depends on which procedure groups happen to hold upcoded claims (L9).

### Internal goals (set before any run)

*Sources: `reports/heldout/metrics.json`, `reports/stability/summary.md`.*

| Goal | Held-out | Seeds 101–105 | Met? |
|---|---|---|---|
| Recall ≥ 85% | 96.5% | lowest 96.5% | yes |
| Every pattern ≥ 70% | upcoding 76.7% | lowest 76.7% (seed 104) | yes, 6.7 points of margin |
| Standard FPR ≤ 5% | 4.4% | highest 4.7% | yes, close to the limit |
| SD across seeds ≤ 5 pp | | recall 0.74, precision 1.70 | yes |
| Precision ≥ 75% (changed to "report only" by Q17) | 70.7% | 69.9–73.6% | no |

---

## 7. Why these weights

Weights run from 1 to 5. A higher weight means stronger evidence that the claim is fraud, and fewer honest reasons for the pattern. (Section G of `docs/design-decisions.md`.)

| Rule | Weight | Reason |
|---|:---:|---|
| duplicate_claim | 5 | All four fields match exactly. There is almost no honest reason. False alarms come mostly from a system sending a claim twice. |
| phantom_billing | 5 | More than 30 claims a day at one provider is close to physically impossible. The evidence is at provider level, so it is strong. Downside: legit claims on the same day are flagged too. |
| upcoding | 4 | Strong statistical evidence. But a really severe case also costs more than average, and the result depends on how the mean and SD are computed. |
| unbundling | 4 | Structural evidence: parts billed separately when a bundle code exists. But it depends on a bundle table I designed, and billing rules differ between payers. |
| weekend_anomaly | 3 | It can be a real emergency. The evidence is indirect and needs two conditions: surgery, and a provider that rarely works weekends. |
| diagnosis_procedure_mismatch | 3 | A good medical signal. But the whitelist may be incomplete, and diagnosis codes are often entered loosely. |
| rapid_resubmission | 2 | Follow-up visits and refills within 7 days are very common, especially for chronic conditions. Many honest claims match. |
| amount_clustering | 2 | Many honest claims land near the threshold by chance. It is a behavior hint and needs another rule to mean much. |

**What the weights change at T = 7, and what they do not:**

- At T = 7, every claim with at least one flag is predicted fraud. So the weights do **not** change recall, FPR or precision. They only change the **order** of claims (L2).
- The order matters to an investigator who works from the top of the list. On held-out, the engine weights put 45 fraud claims in the top 50, against 32 with equal weights.
- This precision@k result **depends on the generator**. In this dataset, the high-weight rules (duplicate, phantom, unbundling) almost never fire on legit claims, and the low-weight rules (rapid, clustering) often do. That is partly how I built the data. It shows the weights are consistent with the data, not that they are right for real claims.
- The weights also tie to T. Rapid and clustering score 7 alone, so any T ≥ 8 drops both patterns (L15).

**Changes from my first draft.** My first draft had rapid 3, phantom 4 and weekend 2. I changed them to 2, 5 and 3. Rapid went down because honest follow-up visits within 7 days are common, so the signal alone is weak. Phantom went up because a provider billing more claims in one day than is realistic is the hardest pattern to explain innocently. Weekend went up because it needs two conditions at once (surgery, and a provider that rarely works weekends), which makes it more specific than I first thought. I fixed all weights before generating any data and did not change them afterwards.

Later results are consistent with this order: the table-based rules have 100% precision, and rapid and clustering have the lowest (57.7% and 36.4% on held-out). I saw this only after the weights were fixed, so I do not use it as the reason for the weights.

---

## 8. Interpretations and limitations

### Interpretations

The challenge leaves some points open. These are the readings I chose. The codes refer to `docs/design-decisions.md`.

**Rules:**

- **Rapid re-submission (R2):** "within 7 days" means 0–7 days, with any provider. Only the later claim is flagged. Pairs already flagged as duplicates are skipped, so one act is not counted twice.
- **Upcoding (R3, Q1, R4):**
  - Only claims with exactly one procedure are checked. One claim has one amount, and only a single-procedure claim can be compared with the price of that procedure. Multi-procedure claims are counted as skipped (337 on held-out).
  - The comparison group is the claims with that same single procedure.
  - Mean and SD include the claim itself, as the challenge says, and use the sample SD. Groups under 10 claims, or with SD = 0, are not flagged.
- **Weekend anomaly (R7, D11, Q12):**
  - "Surgical" is a flag in the procedure catalog.
  - The weekend share is computed over the provider's other claims (leave-one-out), because with ~40–70 claims per provider, 2–3 weekend fraud claims would push the share over 5% themselves.
  - The provider needs at least 20 other claims. Weekend means Saturday and Sunday; public holidays are not counted.
  - The challenge says "never operate on weekends" in the dataset section and "< 5%" in the rule. I follow the rule and use "never" only to shape the data.
- **Diagnosis–procedure mismatch (R8, Q14):**
  - The whitelist covers every diagnosis in the catalog (64 specific pairs plus 2 general codes).
  - A claim is flagged if any known procedure is not valid for its diagnosis.
  - An unknown diagnosis is not flagged: there is not enough evidence, and flagging it would flag almost everything.

**Labels and data:**

- **Duplicate (D9, R1):** only the later copies are fraud and flagged. The first claim may be real.
- **Phantom billing (D10, Q8):** a provider-day must have 31 claims to trigger, but the challenge asks for 25 phantom claims. I added 6 legit claims to that day. All 31 are flagged, and the 6 count as false positives.
- **Claim order (D14):** there is no submission time. "Earlier" means ordered by `claim_date`, then `claim_id`.
- **Data volume (D6):** "~500 members" and "~50 providers" are generated as exactly 500 and 50. Fraud claims are exactly 200 out of 2,000.

**Scoring and metrics:**

- **Score normalization (S1, Q22):** I divide by the sum of all weights. The challenge example (78 for 4 + 2) does not follow from this; my formula gives 21.
- **Severity (S2):** severity is the rule's configured weight. The evidence text carries the strength of the match.
- **One flag per rule (S6):** a rule adds its weight once, even if it matches several times. The evidence lists every match.
- **Boundaries (R10):** taken word for word from the challenge (see the table in [section 3](#3-design)).
- **False positive rate (M1):** the main reading is FP / legit. FDR = FP / flagged is always reported next to it.
- **Batch statistics (R12):** statistics use the whole year, so a January claim is judged with December data. This is fine for batch scoring but not for a pre-payment check (L3).

**Input and output:**

- **Input checks (R11):** `is_weekend` is recomputed from the date, with a warning if it differs. Rows with amount ≤ 0, a duplicate `claim_id` or no procedures are dropped and reported. Unknown codes are kept, with a warning.
- **Percentages in evidence:** truncated, not rounded, so 49,999 shows as 99.9% and never as 100%.
- **Output schema (O2):** exactly the fields of the challenge example. No extra fields.

### Limitations

The full list, with examples, is in [`docs/limitations.md`](docs/limitations.md) (in Vietnamese). In short:

| # | Limitation |
|---|---|
| L1 | Held-out protects against tuning to one seed, not against tuning to my own generator. All seeds come from the same generator, which plants fraud the way the rules define it. |
| L2 | With T ≤ 7, weights do not change which claims are flagged; they only change the ranking. |
| L3 | Batch scoring uses full-year statistics ("future" data). |
| L4 | Unbundling only looks inside one claim, not across several claims on the same day. |
| L5 | Unbundled claim amounts are capped below the clustering band, so the two patterns never meet in the data. |
| L6 | The `surgical` flag is my own definition, not a standard. |
| L7 | Medical correctness of the whitelist is reviewed by hand, not by tests. |
| L8 | Upcoding skips multi-procedure claims. An overpriced multi-procedure claim without a bundle is checked by no rule. |
| L9 | Upcoding includes the claim itself in its mean and SD (masking). Small groups and several upcoded claims in one group hide fraud. |
| L10 | Reference-table rules reach ~100% recall by construction, because the generator uses the same tables. |
| L11 | The weekday-only hospitals have no weekend emergencies, which is not realistic. |
| L12 | Fraud claims (other than upcoding and clustering) have a narrower amount noise than legit claims. A learned model could pick this up; the engine does not use it. |
| L13 | The 100% weekend result rests on few claims (40 checked on tuning). |
| L14 | 0 flagged near-miss claims only shows the generator and engine agree on boundaries. |
| L15 | Rapid re-submission and clustering only work when T ≤ 7. |
| L16 | Two changes during the held-out step (test time limit before, report format after), both checked by SHA-256 (see [section 5](#5-method)). |

---

## 9. Timeline

### First estimate

Made before coding (decision T7 in `docs/design-decisions.md`). The challenge says 4–6 hours.

| Phase | Estimate |
|---|---:|
| Design | 0.5 h |
| Generator | 1.5 h |
| Rules and index | 1.5 h |
| Scoring, output, metrics | 1 h |
| Tests | 1 h |
| README and report | 0.5 h |
| Buffer (near-miss data, extra seeds) | 1 h |
| **Total** | **about 7 h** |

### Actual, from git commit times

*Source: `git log` (all on 2026-10-25, local time).*

These times are **not** pure coding time. Each one includes the time Claude Code spent running and the time I spent reviewing and deciding before I approved the commit. A commit time is the moment I approved, not the moment the work ended.

| Phase | Commits | From → to | Duration |
|---|---|---|---:|
| Design (before the first commit) | none | 11:56 → about 14:35 (from the timestamps of my advisor chat, `docs/ai-log/advisor-chat-log-2026-10-25.md`, not from git) | about 2 h 40 min |
| Skeleton, catalog, config, CSV reader (Tasks 01–04, plus 2 setup commits) | `eb2fc4b` … `98cb920` | about 14:35 → 16:14 (first commit 14:51) | about 1 h 40 min |
| Index and 8 rules (Tasks 05–10) | `7bda7e6` … `72d5303` | 16:14 → 16:56 | 42 min |
| Scoring, ranking, `score` command (Task 11) | `e79b499` | 16:56 → 17:06 | 10 min |
| Generator spec and generator (Tasks 12–15) | `045729e` … `f6d7481` | 17:06 → 17:55 | 49 min |
| Metrics, `tune`, `evaluate` (Task 16) | `bae0be7` | 17:55 → 18:20 | 25 min |
| Tuning and freeze (Task 17) | `c93d367` | 18:20 → 18:25 | 5 min |
| Held-out and stability (Task 18) | `68e6309` … `530897c` | 18:25 → 18:41 | 16 min |
| README (Task 19) | this file | 18:41 → until this file was committed | about 1 h 30 min (estimate) |

From the first commit (14:51) to the Task 18 commit (18:41) is 3 h 50 min. Tests were written together with the code in every task, so they have no separate line.

In total the work took about 8 hours on 25 October: about 2 h 40 min of design, about 4 h for Tasks 01 to 18, and about 1 h 30 min for the README. The first estimate was 7 hours. Building was faster than planned (about 4 h against 6 h for the generator, rules, scoring, tests and buffer). Design and documentation were slower (2 h 40 min against 0.5 h, and 1 h 30 min against 0.5 h). Reading the brief on 23 October is not counted.

I did not measure how the time splits between my review and the agent's runs. On average one task took about 14 minutes from its start to my approved commit (about 4 h for 18 tasks), and that includes both.

---

## 10. AI usage

The challenge asks us to use AI coding tools. I used two Claude sources.

**1. Claude Code (this repository).**

- Wrote the code, the tests, the generator and the reports, one task at a time.
- Wrote first drafts of the docs: the design-decision options (`docs/design-decisions.md`), the generator spec, the limitations and this README.
- Logs: `docs/ai-log/` (raw `/export` files, not edited).
- After Task 19, a separate Claude Code session reviewed the finished repository without editing it. Its `/export` is also in `docs/ai-log/` (see "Independent review" in [`docs/ai-log/README.md`](docs/ai-log/README.md)).

**2. A separate Claude conversation, used as an advisor.**

- Reviewed Claude Code's output.
- Helped me write the prompts for each task.
- Helped draft the design decisions.
- Its log is [`docs/ai-log/advisor-chat-log-2026-10-25.md`](docs/ai-log/advisor-chat-log-2026-10-25.md) (11:56–20:33 on 25 October). How it was exported and what was left out is explained in [`docs/ai-log/README.md`](docs/ai-log/README.md).

**Where `docs/design-decisions.md` came from.** Claude Code wrote the structure of the file: the issues, the options and one recommendation for each. Before that, in my own notes, I had already decided 12 points and the 8 weights (you can see this in the advisor conversation log). The choices there are mine. The reasons for the duplicate choice, the plan to try median/MAD, the phantom-billing choice, the threshold rule and the FPR definition are in my own words. For the other reasons, the advisor conversation suggested the wording and I accepted or edited it. I then asked the advisor to copy my decisions into Claude Code's file and to draft the remaining ones. Those drafts are marked `[ĐỀ XUẤT, CẦN XÁC NHẬN]` until I confirmed them.

**Who decided, who wrote:**

| Task | Who |
|---|---|
| Every design decision, all 8 weights, Q1–Q24, generator choices G1–G14 | **Me.** I made or confirmed every decision. Some "Decision" lines were first drafted by the advisor conversation and then confirmed or changed by me (see above). Several differ from Claude Code's suggestion: score formula S1, phantom D10, upcoding R4, and 3 of the 8 weights. |
| Choice of T and keeping the z-score for upcoding (Task 17) | **Me**, using a rule I set before the tuning run. |
| Code, tests, generator, report text | **Claude Code**, from my task prompts. |
| Review | **Me**, after every task, helped by the advisor conversation. Nothing was committed without my approval. |

**How I kept control:**

- **Plan:** 19 tasks, one commit each. After each task Claude Code ran the tests, summarized with a worked example, and stopped for my review.
- **Hard rules, set before coding** (also in `CLAUDE.md`):
  - The engine never reads ground truth.
  - T and weights are frozen before held-out.
  - Every reported number comes from a real run.
  - No new library without asking.
  - When a design point is missing, stop and ask.
- **Stops in practice:** Claude Code stopped and asked when something was not covered. The format issue in Task 18 is one example: it stopped and gave me options instead of fixing it alone.

**What worked.**

- Small tasks with a stop after each one, so I could review before every commit.
- Freezing T before I looked at held-out data, and writing the held-out test first.
- A second Claude conversation as an independent reviewer. It re-checked the arithmetic and found errors in Claude Code's reports, for example wrong counts of surgical procedures and diagnosis–procedure pairs (9 instead of 8, and 59 instead of 64).
- Checking details myself: I noticed a BOM character in the first commit message and CRLF line endings, which led to `.gitattributes`.

**What did not work well.**

- Long prompts were cut when I pasted them, so Task 16 waited until I sent the missing part.
- Reports from the AI sometimes needed to be checked again.
- The `evaluate` command printed the held-out summary line before I had decided how to handle the format issue. Next time I would hide metrics unless they are asked for.
