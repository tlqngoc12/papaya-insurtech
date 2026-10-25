package com.papaya.fraud.generator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Per-stage random sources (G1): each stage gets its own {@link SplittableRandom} seeded with
 * {@code mix64(seed XOR fnv1a64(stage))}. A stage's sequence depends only on the dataset seed and the stage name,
 * so drawing more or fewer numbers in one stage never shifts another.
 */
final class StageRandom {

    private StageRandom() {
    }

    static SplittableRandom forStage(long seed, String stage) {
        return new SplittableRandom(mix64(seed ^ fnv1a64(stage)));
    }

    /** SplitMix64 finaliser. */
    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    static long fnv1a64(String text) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    // --- sampling helpers shared by the stages ---

    static int weightedIndex(SplittableRandom r, double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double x = r.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            x -= weights[i];
            if (x < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    static <T> T pick(SplittableRandom r, List<T> items) {
        return items.get(r.nextInt(items.size()));
    }

    static <T> void shuffle(SplittableRandom r, List<T> items) {
        for (int i = items.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            T tmp = items.get(i);
            items.set(i, items.get(j));
            items.set(j, tmp);
        }
    }

    /** Gamma(shape, 1) by Marsaglia and Tsang; shape must be >= 1. */
    static double gamma(SplittableRandom r, double shape) {
        double d = shape - 1.0 / 3;
        double c = 1 / Math.sqrt(9 * d);
        while (true) {
            double x = r.nextGaussian();
            double v = 1 + c * x;
            if (v <= 0) {
                continue;
            }
            v = v * v * v;
            double u = r.nextDouble();
            if (u < 1 - 0.0331 * x * x * x * x || Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) {
                return d * v;
            }
        }
    }

    /** Price multiplier 1 + cv * z with z ~ N(0, 1) truncated to [-sigmas, +sigmas] (D8). */
    static double priceMultiplier(SplittableRandom r, double cv, double sigmas) {
        while (true) {
            double z = r.nextGaussian();
            if (Math.abs(z) <= sigmas) {
                return 1 + cv * z;
            }
        }
    }
}
