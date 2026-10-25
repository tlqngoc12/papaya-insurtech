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

    static String format(String template, Object... args) {
        return String.format(Locale.US, template, args);
    }
}
