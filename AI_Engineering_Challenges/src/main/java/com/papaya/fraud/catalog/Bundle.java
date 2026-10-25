package com.papaya.fraud.catalog;

import java.util.List;

/**
 * A bundled procedure code that should be billed instead of its individual components.
 */
public record Bundle(String bundleCode, List<String> components) {

    public Bundle {
        components = components == null ? List.of() : List.copyOf(components);
    }
}
