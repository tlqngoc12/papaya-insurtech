package com.papaya.fraud.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.papaya.fraud.io.Json;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Loads the catalog from the four JSON resources under {@code /catalog/} on the classpath.
 */
public final class CatalogLoader {

    static final String PROCEDURES = "/catalog/procedures.json";
    static final String DIAGNOSES = "/catalog/diagnoses.json";
    static final String BUNDLES = "/catalog/bundles.json";
    static final String DIAGNOSIS_PROCEDURES = "/catalog/diagnosis-procedures.json";

    private CatalogLoader() {
    }

    public static Catalog loadDefault() {
        List<Procedure> procedures = read(PROCEDURES, new TypeReference<>() {
        });
        List<Diagnosis> diagnoses = read(DIAGNOSES, new TypeReference<>() {
        });
        List<Bundle> bundles = read(BUNDLES, new TypeReference<>() {
        });
        DiagnosisProcedureMap whitelist = read(DIAGNOSIS_PROCEDURES, new TypeReference<>() {
        });
        return Catalog.create(procedures, diagnoses, bundles, whitelist);
    }

    private static <T> T read(String resource, TypeReference<T> type) {
        try (InputStream in = CatalogLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new CatalogException("missing resource " + resource, null);
            }
            return Json.mapper().readValue(in, type);
        } catch (IOException e) {
            throw new CatalogException("cannot read " + resource + ": " + e.getMessage(), e);
        }
    }
}
