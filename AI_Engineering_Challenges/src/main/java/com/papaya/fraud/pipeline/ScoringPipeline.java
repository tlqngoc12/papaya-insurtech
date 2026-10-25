package com.papaya.fraud.pipeline;

import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.FraudEngine;
import com.papaya.fraud.io.ClaimCsvReader;
import com.papaya.fraud.io.LoadResult;
import com.papaya.fraud.io.ScoredOutputWriter;
import java.nio.file.Path;

/**
 * The single scoring path shared by {@code score} and {@code evaluate}: read and validate claims.csv, run the engine,
 * write scored_claims.json. Never sees ground truth.
 */
public final class ScoringPipeline {

    /** What was loaded and scored, with the engine (for coverage) and the measured times. */
    public record Run(LoadResult loaded, FraudEngine engine, FraudEngine.Result result, long loadMs, long scoreMs,
                      long writeMs) {
    }

    private ScoringPipeline() {
    }

    public static Run run(Path claims, FraudConfig config, Catalog catalog, Path scoredOut) {
        long start = System.nanoTime();
        LoadResult loaded = new ClaimCsvReader(catalog).read(claims);
        long loadedAt = System.nanoTime();
        FraudEngine engine = new FraudEngine(config, catalog);
        FraudEngine.Result result = engine.run(loaded.claims());
        long scoredAt = System.nanoTime();
        ScoredOutputWriter.write(result.ranked(), scoredOut);
        long writtenAt = System.nanoTime();
        return new Run(loaded, engine, result, ms(start, loadedAt), ms(loadedAt, scoredAt), ms(scoredAt, writtenAt));
    }

    private static long ms(long from, long to) {
        return (to - from) / 1_000_000;
    }
}
