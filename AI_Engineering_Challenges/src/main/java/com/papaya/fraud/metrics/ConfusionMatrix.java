package com.papaya.fraud.metrics;

/**
 * Claim-level confusion matrix at a threshold T: a claim is predicted fraud when risk_score &gt;= T. Ratios are NaN
 * when their denominator is zero.
 *
 * <ul>
 *   <li>precision = TP / (TP + FP)</li>
 *   <li>recall = TP / (TP + FN)</li>
 *   <li>FPR (standard, M1) = FP / (FP + TN) = false positives over all legit claims</li>
 *   <li>FDR (the other reading of "false positive rate") = FP / (TP + FP) = 1 - precision</li>
 *   <li>F1 = 2 x precision x recall / (precision + recall)</li>
 * </ul>
 */
public record ConfusionMatrix(int tp, int fp, int fn, int tn) {

    public int flagged() {
        return tp + fp;
    }

    public int fraud() {
        return tp + fn;
    }

    public int legit() {
        return fp + tn;
    }

    public double precision() {
        return ratio(tp, tp + fp);
    }

    public double recall() {
        return ratio(tp, tp + fn);
    }

    public double fpr() {
        return ratio(fp, fp + tn);
    }

    public double fdr() {
        return ratio(fp, tp + fp);
    }

    public double f1() {
        double p = precision();
        double r = recall();
        return Double.isNaN(p) || Double.isNaN(r) || p + r == 0 ? Double.NaN : 2 * p * r / (p + r);
    }

    /** FPR &lt;= limit, compared exactly on counts (fp / legit &lt;= limit). */
    public boolean fprAtMost(double limit) {
        return legit() > 0 && fp <= limit * legit() + 1e-9;
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? Double.NaN : (double) numerator / denominator;
    }
}
