package com.papaya.fraud.generator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Parameters of the dataset generator, read from {@code generator-config.json}. Deliberately separate from the
 * engine's configuration file, with different key names (generator spec section 8, H2#1).
 */
public record GeneratorConfig(Dataset dataset, Volumes volumes, Chronic chronic, Pricing pricing,
                              Map<String, List<CaseMixEntry>> caseMix, List<BandHost> bandHosts,
                              double maxPlantedShare, FraudCounts fraudCounts, Upcode upcode,
                              Resubmission resubmission, Unbundle unbundle, LongRange plantedAmountRange,
                              PhantomDay phantomDay, Noise noise, int maxRedraws) {

    public record Dataset(int totalClaims, int memberCount, int year, ProviderTiers providerTiers) {
    }

    public record ProviderTiers(int weekdayOnlyHospitals, int regularHospitals, int clinics, int dentalClinics) {
        int total() {
            return weekdayOnlyHospitals + regularHospitals + clinics + dentalClinics;
        }
    }

    public record Volumes(Map<String, Double> claimTypeMix, double outpatientHospitalShare,
                          int weekdayOnlyHospitalClaims, double providerWeightShape, double memberWeightShape) {
    }

    public record Chronic(double memberShare, List<String> diagnoses, double outpatientShare, int followups,
                          IntRange followupGapDays) {
    }

    public record Pricing(double noiseCv, double noiseTruncationSigmas, double fraudNoiseCap) {
    }

    /** One diagnosis (or a pool of diagnoses, e.g. "inpatient_only") with its weight and procedure templates. */
    public record CaseMixEntry(String diagnosis, String diagnosisPool, double weight, List<Template> templates) {
    }

    public record Template(List<String> procedures, double weight) {
    }

    /** One of the four groups hosting the clustering band (Task 02 option 3, generator spec 4.5). */
    public record BandHost(String diagnosis, List<String> procedures, int naturalClaims, int nearMissClaims,
                           int plantedClaims) {
    }

    public record FraudCounts(int duplicate, int resubmission, int upcode, int unbundle, int phantom,
                              int weekendSurgery, int mismatch, int bandAmount) {
        int total() {
            return duplicate + resubmission + upcode + unbundle + phantom + weekendSurgery + mismatch + bandAmount;
        }
    }

    public record Upcode(int strongClaims, DoubleRange strongFactor, int mildClaims, DoubleRange mildFactor,
                         int maxPerGroup, int minGroupClaims) {
    }

    public record Resubmission(IntRange gapDays, double sameProviderShare) {
    }

    public record Unbundle(int fullComponentClaims, int bundleWithComponentClaims, long amountCap) {
    }

    public record PhantomDay(int fraudClaims, int legitClaims) {
    }

    public record Noise(int bandLegitClaims, NearMiss nearMiss,
                        @JsonProperty("day_30_claims") int day30Claims) {
    }

    public record NearMiss(int bandBelow, int bandAbove,
                           @JsonProperty("resubmission_8_days") int resubmission8Days,
                           int sameDayOtherDiagnosis, int weekendSurgeryRegularHospital, int partialBundle,
                           int mildAmount) {
        int total() {
            return bandBelow + bandAbove + resubmission8Days + sameDayOtherDiagnosis + weekendSurgeryRegularHospital
                    + partialBundle + mildAmount;
        }
    }

    public record IntRange(int min, int max) {
    }

    public record LongRange(long min, long max) {
    }

    public record DoubleRange(double min, double max) {
    }

    /**
     * Ordinary legit claims ({@code none}) the generator creates first: everything that is not a fraud claim, a
     * planted noise claim, a near-miss or part of the 30-claim day. 1,719 with the shipped config.
     */
    public int baseClaims() {
        return dataset.totalClaims() - fraudCounts.total() - noise.bandLegitClaims() - phantomDay.legitClaims()
                - noise.nearMiss().total() - noise.day30Claims();
    }

    /** Natural claims of the band hosts created with the base; their near-miss claims come later (Task 15). */
    public int bandHostBaseClaims() {
        return bandHosts.stream().mapToInt(h -> h.naturalClaims() - h.nearMissClaims()).sum();
    }
}
