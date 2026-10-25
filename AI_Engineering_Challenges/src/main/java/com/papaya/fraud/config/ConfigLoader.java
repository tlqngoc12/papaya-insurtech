package com.papaya.fraud.config;

import com.papaya.fraud.io.Json;
import com.papaya.fraud.model.RuleId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Loads {@link FraudConfig} from the bundled {@code /fraud-config.json} or from a file given with {@code --config}.
 */
public final class ConfigLoader {

    static final String DEFAULT_RESOURCE = "/fraud-config.json";
    public static final String CONFIG_FLAG = "--config";

    static final int MIN_WEIGHT = 1;
    static final int MAX_WEIGHT = 5;

    /** Shape of the JSON file; {@code threshold} is only present in a frozen config. */
    record ConfigFile(Map<String, Integer> weights, RuleParams rules, Integer threshold) {
    }

    private ConfigLoader() {
    }

    public static FraudConfig loadDefault() {
        try (InputStream in = ConfigLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new ConfigException("missing resource " + DEFAULT_RESOURCE);
            }
            return validate(DEFAULT_RESOURCE, Json.mapper().readValue(in, ConfigFile.class));
        } catch (IOException e) {
            throw new ConfigException("cannot read " + DEFAULT_RESOURCE + ": " + e.getMessage(), e);
        }
    }

    public static FraudConfig load(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new ConfigException("config file not found: " + path);
        }
        try (InputStream in = Files.newInputStream(path)) {
            return validate(path.toString(), Json.mapper().readValue(in, ConfigFile.class));
        } catch (IOException e) {
            throw new ConfigException("cannot read " + path + ": " + e.getMessage(), e);
        }
    }

    /** Uses the file after {@code --config} when present, otherwise the bundled default. */
    public static FraudConfig fromArgs(List<String> args) {
        int i = args.indexOf(CONFIG_FLAG);
        if (i < 0) {
            return loadDefault();
        }
        if (i + 1 >= args.size() || args.get(i + 1).startsWith("--")) {
            throw new ConfigException(CONFIG_FLAG + " requires a file path");
        }
        return load(Path.of(args.get(i + 1)));
    }

    static FraudConfig validate(String source, ConfigFile file) {
        List<String> problems = new ArrayList<>();
        Map<RuleId, Integer> weights = validateWeights(file.weights(), problems);
        RuleParams params = file.rules();
        if (params == null) {
            problems.add("rules: missing");
        } else {
            validateParams(params, problems);
        }
        Integer t = file.threshold();
        if (t != null && (t < 1 || t > 100)) {
            problems.add("threshold: must be 1-100, was " + t);
        }
        if (!problems.isEmpty()) {
            throw new ConfigException(source, problems);
        }
        return new FraudConfig(weights, params, t == null ? OptionalInt.empty() : OptionalInt.of(t));
    }

    private static Map<RuleId, Integer> validateWeights(Map<String, Integer> raw, List<String> problems) {
        Map<RuleId, Integer> weights = new EnumMap<>(RuleId.class);
        if (raw == null) {
            problems.add("weights: missing");
            return weights;
        }
        raw.forEach((id, w) -> {
            var rule = RuleId.fromId(id);
            if (rule.isEmpty()) {
                problems.add("weights: unknown rule '" + id + "'");
            } else if (w == null || w < MIN_WEIGHT || w > MAX_WEIGHT) {
                problems.add("weights." + id + ": must be " + MIN_WEIGHT + "-" + MAX_WEIGHT + ", was " + w);
            } else {
                weights.put(rule.get(), w);
            }
        });
        for (RuleId rule : RuleId.values()) {
            if (!raw.containsKey(rule.id())) {
                problems.add("weights." + rule.id() + ": missing");
            }
        }
        return weights;
    }

    private static void validateParams(RuleParams p, List<String> problems) {
        if (p.rapidResubmission() == null) {
            problems.add("rules.rapid_resubmission: missing");
        } else if (p.rapidResubmission().windowDays() < 1) {
            problems.add("rules.rapid_resubmission.window_days: must be >= 1, was "
                    + p.rapidResubmission().windowDays());
        }
        if (p.upcoding() == null) {
            problems.add("rules.upcoding: missing");
        } else {
            if (!(p.upcoding().zThreshold() > 0)) {
                problems.add("rules.upcoding.z_threshold: must be > 0, was " + p.upcoding().zThreshold());
            }
            // The sample standard deviation (n - 1) needs at least two claims.
            if (p.upcoding().minGroupSize() < 2) {
                problems.add("rules.upcoding.min_group_size: must be >= 2, was " + p.upcoding().minGroupSize());
            }
        }
        if (p.phantomBilling() == null) {
            problems.add("rules.phantom_billing: missing");
        } else if (p.phantomBilling().maxClaimsPerDay() < 1) {
            problems.add("rules.phantom_billing.max_claims_per_day: must be >= 1, was "
                    + p.phantomBilling().maxClaimsPerDay());
        }
        if (p.weekendAnomaly() == null) {
            problems.add("rules.weekend_anomaly: missing");
        } else {
            double ratio = p.weekendAnomaly().maxWeekendRatio();
            if (!(ratio > 0 && ratio < 1)) {
                problems.add("rules.weekend_anomaly.max_weekend_ratio: must be between 0 and 1 exclusive, was "
                        + ratio);
            }
            if (p.weekendAnomaly().minHistory() < 1) {
                problems.add("rules.weekend_anomaly.min_history: must be >= 1, was "
                        + p.weekendAnomaly().minHistory());
            }
        }
        if (p.amountClustering() == null) {
            problems.add("rules.amount_clustering: missing");
        } else {
            long low = p.amountClustering().lowerBound();
            long high = p.amountClustering().upperBound();
            if (low <= 0) {
                problems.add("rules.amount_clustering.lower_bound: must be > 0, was " + low);
            }
            if (low > high) {
                problems.add("rules.amount_clustering: lower_bound " + low + " is above upper_bound " + high);
            }
            long threshold = p.amountClustering().autoApprovalThreshold();
            if (high >= threshold) {
                problems.add("rules.amount_clustering: upper_bound " + high
                        + " must be below auto_approval_threshold " + threshold);
            }
        }
    }
}
