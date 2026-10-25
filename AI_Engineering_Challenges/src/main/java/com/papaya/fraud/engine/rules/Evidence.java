package com.papaya.fraud.engine.rules;

import java.util.Locale;

/**
 * Number formatting for evidence strings (O3): English, Locale.US grouping ("85,000"), independent of the
 * default locale.
 */
final class Evidence {

    private Evidence() {
    }

    static String amount(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    /**
     * numerator / denominator as a percentage with one decimal, truncated rather than rounded, so a share below
     * 100% never reads as 100.0% (49,999 of 50,000 is "99.9"). No % sign.
     */
    static String percent(long numerator, long denominator) {
        long permille = numerator * 1000 / denominator;
        return permille / 10 + "." + permille % 10;
    }

    static String format(String template, Object... args) {
        return String.format(Locale.US, template, args);
    }
}
