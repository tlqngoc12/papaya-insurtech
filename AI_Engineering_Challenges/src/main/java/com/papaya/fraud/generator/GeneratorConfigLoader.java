package com.papaya.fraud.generator;

import com.papaya.fraud.io.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and checks {@code generator-config.json}. The checks are the arithmetic the spec relies on (counts that
 * must add up, the 25% planted share of the band hosts, Q13's 60-claim minimum).
 */
public final class GeneratorConfigLoader {

    static final String DEFAULT_RESOURCE = "/generator-config.json";

    private GeneratorConfigLoader() {
    }

    public static GeneratorConfig loadDefault() {
        try (InputStream in = GeneratorConfigLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + DEFAULT_RESOURCE);
            }
            return validate(Json.mapper().readValue(in, GeneratorConfig.class));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + DEFAULT_RESOURCE, e);
        }
    }

    public static GeneratorConfig load(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return validate(Json.mapper().readValue(in, GeneratorConfig.class));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    static GeneratorConfig validate(GeneratorConfig c) {
        List<String> problems = new ArrayList<>();
        if (c.dataset().providerTiers().total() <= 0) {
            problems.add("provider tiers must add up to at least one provider");
        }
        double mix = c.volumes().claimTypeMix().values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(mix - 1.0) > 1e-9) {
            problems.add("claim_type_mix must sum to 1, was " + mix);
        }
        if (c.volumes().weekdayOnlyHospitalClaims() < 60) {
            problems.add("weekday_only_hospital_claims must be >= 60 (Q13)");
        }
        if (c.upcode().strongClaims() + c.upcode().mildClaims() != c.fraudCounts().upcode()) {
            problems.add("upcode strong_claims + mild_claims must equal fraud_counts.upcode");
        }
        if (c.unbundle().fullComponentClaims() + c.unbundle().bundleWithComponentClaims()
                != c.fraudCounts().unbundle()) {
            problems.add("unbundle claim counts must add up to fraud_counts.unbundle");
        }
        if (c.phantomDay().fraudClaims() != c.fraudCounts().phantom()) {
            problems.add("phantom_day.fraud_claims must equal fraud_counts.phantom");
        }
        int planted = c.bandHosts().stream().mapToInt(GeneratorConfig.BandHost::plantedClaims).sum();
        if (planted != c.fraudCounts().bandAmount() + c.noise().bandLegitClaims()) {
            problems.add("band_hosts planted_claims must add up to band fraud + band noise claims");
        }
        int nearMiss = c.bandHosts().stream().mapToInt(GeneratorConfig.BandHost::nearMissClaims).sum();
        if (nearMiss != c.noise().nearMiss().bandBelow() + c.noise().nearMiss().bandAbove()) {
            problems.add("band_hosts near_miss_claims must add up to band_below + band_above");
        }
        for (GeneratorConfig.BandHost h : c.bandHosts()) {
            String name = h.diagnosis() + " " + h.procedures();
            if (h.naturalClaims() < 30) {
                problems.add("band host " + name + ": natural_claims must be >= 30 (PROGRESS Task 12)");
            }
            double share = (double) h.plantedClaims() / (h.naturalClaims() + h.plantedClaims());
            if (share > c.maxPlantedShare()) {
                problems.add("band host " + name + ": planted share " + share + " above " + c.maxPlantedShare());
            }
        }
        int weekdayOnly = c.dataset().providerTiers().weekdayOnlyHospitals();
        int threes = c.weekendSurgery().hospitalsWithThreeClaims();
        if (threes < 0 || threes > weekdayOnly || 3 * threes + 2 * (weekdayOnly - threes) != c.fraudCounts().weekendSurgery()) {
            problems.add("weekend_surgery: 3 x hospitals_with_three_claims + 2 x the other weekday-only hospitals"
                    + " must equal fraud_counts.weekend_surgery (Q13)");
        }
        if (c.bandHosts().isEmpty() || c.fraudCounts().bandAmount() % c.bandHosts().size() != 0) {
            problems.add("fraud_counts.band_amount must split evenly over the band hosts");
        }
        int generic = c.baseClaims() - c.bandHostBaseClaims() - c.chronic().followups();
        if (generic <= 0) {
            problems.add("no room left for generic base claims: " + generic);
        }
        if (c.maxRedraws() < 1) {
            problems.add("max_redraws must be >= 1");
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Invalid generator config:\n  - " + String.join("\n  - ", problems));
        }
        return c;
    }
}
