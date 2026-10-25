package com.papaya.fraud.engine;

import com.papaya.fraud.model.Claim;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Dataset-wide facts the rules need, built once from the claims (phase 1 of T1). Immutable and deterministic:
 * every collection is ordered (TreeMap, or lists sorted by {@link #ORDER}), so the result does not depend on the
 * input order. Built in O(n log n); lookups are O(log n).
 *
 * <p>Weekend counts use {@link Claim#isWeekend()}, which {@code ClaimCsvReader} recomputes from the date (R11).
 * Ground truth is never an input.
 */
public final class DatasetIndex {

    /** Submission order (D14): claim_date, then claim_id. */
    public static final Comparator<Claim> ORDER =
            Comparator.comparing(Claim::claimDate).thenComparing(Claim::claimId);

    public record MemberDiagnosis(String memberId, String diagnosisCode) implements Comparable<MemberDiagnosis> {
        private static final Comparator<MemberDiagnosis> CMP = Comparator.comparing(MemberDiagnosis::memberId)
                .thenComparing(MemberDiagnosis::diagnosisCode);

        @Override
        public int compareTo(MemberDiagnosis o) {
            return CMP.compare(this, o);
        }
    }

    public record ProviderDay(String providerId, LocalDate date) implements Comparable<ProviderDay> {
        private static final Comparator<ProviderDay> CMP = Comparator.comparing(ProviderDay::providerId)
                .thenComparing(ProviderDay::date);

        @Override
        public int compareTo(ProviderDay o) {
            return CMP.compare(this, o);
        }
    }

    /**
     * Amounts of the claims sharing one procedure signature. {@code sampleSd} divides by n - 1 and is NaN when
     * n &lt; 2. {@code sortedAmounts} is kept so later rules can use other statistics (e.g. median/MAD, Q2).
     */
    public record AmountStats(int n, double mean, double sampleSd, List<Long> sortedAmounts) {
        public AmountStats {
            sortedAmounts = List.copyOf(sortedAmounts);
        }
    }

    /** Claim counts of one provider over the whole dataset. */
    public record ProviderVolume(int total, int weekend) {
    }

    private static final ProviderVolume NO_VOLUME = new ProviderVolume(0, 0);

    private final List<Claim> ordered;
    private final NavigableMap<String, Claim> byId;
    private final NavigableMap<MemberDiagnosis, List<Claim>> byMemberDiagnosis;
    private final NavigableMap<ProviderDay, List<Claim>> byProviderDay;
    private final NavigableMap<String, AmountStats> bySignature;
    private final NavigableMap<String, ProviderVolume> byProvider;

    private DatasetIndex(List<Claim> claims) {
        List<Claim> sorted = new ArrayList<>(claims);
        sorted.sort(ORDER);
        this.ordered = List.copyOf(sorted);

        TreeMap<String, Claim> ids = new TreeMap<>();
        TreeMap<MemberDiagnosis, List<Claim>> memberDx = new TreeMap<>();
        TreeMap<ProviderDay, List<Claim>> providerDay = new TreeMap<>();
        TreeMap<String, List<Long>> amounts = new TreeMap<>();
        TreeMap<String, int[]> volume = new TreeMap<>();
        for (Claim c : ordered) {
            if (ids.put(c.claimId(), c) != null) {
                throw new IllegalArgumentException("duplicate claim_id " + c.claimId());
            }
            // Iterating in ORDER keeps every group list in ORDER too.
            memberDx.computeIfAbsent(new MemberDiagnosis(c.memberId(), c.diagnosisCode()), k -> new ArrayList<>())
                    .add(c);
            providerDay.computeIfAbsent(new ProviderDay(c.providerId(), c.claimDate()), k -> new ArrayList<>())
                    .add(c);
            amounts.computeIfAbsent(signature(c), k -> new ArrayList<>()).add(c.submittedAmount());
            int[] v = volume.computeIfAbsent(c.providerId(), k -> new int[2]);
            v[0]++;
            if (c.isWeekend()) {
                v[1]++;
            }
        }
        this.byId = Collections.unmodifiableNavigableMap(ids);
        this.byMemberDiagnosis = freezeLists(memberDx);
        this.byProviderDay = freezeLists(providerDay);
        TreeMap<String, AmountStats> stats = new TreeMap<>();
        amounts.forEach((sig, values) -> stats.put(sig, stats(values)));
        this.bySignature = Collections.unmodifiableNavigableMap(stats);
        TreeMap<String, ProviderVolume> volumes = new TreeMap<>();
        volume.forEach((p, v) -> volumes.put(p, new ProviderVolume(v[0], v[1])));
        this.byProvider = Collections.unmodifiableNavigableMap(volumes);
    }

    /** Builds the index. Claim ids must be unique (the CSV reader already rejects duplicates). */
    public static DatasetIndex of(List<Claim> claims) {
        return new DatasetIndex(claims);
    }

    /**
     * The procedure signature R3 groups amounts by: the distinct codes of the claim, sorted, joined with "|".
     * The order in which codes were submitted does not matter.
     */
    public static String signature(Claim claim) {
        return String.join("|", new TreeSet<>(claim.procedureCodes()));
    }

    // --- whole dataset ---

    public List<Claim> claimsInOrder() {
        return ordered;
    }

    public int size() {
        return ordered.size();
    }

    public Optional<Claim> claim(String claimId) {
        return Optional.ofNullable(byId.get(claimId));
    }

    // --- member + diagnosis (duplicate, rapid re-submission) ---

    /** All claims with the claim's member and diagnosis, including itself, in {@link #ORDER}. */
    public List<Claim> sameMemberAndDiagnosis(Claim claim) {
        return byMemberDiagnosis.getOrDefault(new MemberDiagnosis(claim.memberId(), claim.diagnosisCode()),
                List.of());
    }

    /** The claims of {@link #sameMemberAndDiagnosis} that come strictly before this claim, in {@link #ORDER}. */
    public List<Claim> earlierSameMemberAndDiagnosis(Claim claim) {
        List<Claim> group = sameMemberAndDiagnosis(claim);
        int pos = Collections.binarySearch(group, claim, ORDER);
        return pos < 0 ? group.subList(0, -pos - 1) : group.subList(0, pos);
    }

    public NavigableMap<MemberDiagnosis, List<Claim>> memberDiagnosisGroups() {
        return byMemberDiagnosis;
    }

    // --- provider + day (phantom billing) ---

    public List<Claim> claimsOnProviderDay(String providerId, LocalDate date) {
        return byProviderDay.getOrDefault(new ProviderDay(providerId, date), List.of());
    }

    public int countOnProviderDay(String providerId, LocalDate date) {
        return claimsOnProviderDay(providerId, date).size();
    }

    public NavigableMap<ProviderDay, List<Claim>> providerDays() {
        return byProviderDay;
    }

    // --- procedure signature amounts (upcoding) ---

    public Optional<AmountStats> signatureStats(String signature) {
        return Optional.ofNullable(bySignature.get(signature));
    }

    public NavigableMap<String, AmountStats> signatureGroups() {
        return bySignature;
    }

    // --- provider weekend volume (weekend anomaly) ---

    public ProviderVolume providerVolume(String providerId) {
        return byProvider.getOrDefault(providerId, NO_VOLUME);
    }

    /** The provider's volume without this claim: total and weekend counts of its other claims (D11, R7). */
    public ProviderVolume volumeExcluding(Claim claim) {
        Claim indexed = byId.get(claim.claimId());
        if (indexed == null) {
            throw new IllegalArgumentException("claim " + claim.claimId() + " is not in the index");
        }
        ProviderVolume v = providerVolume(indexed.providerId());
        return new ProviderVolume(v.total() - 1, v.weekend() - (indexed.isWeekend() ? 1 : 0));
    }

    /** Number of the provider's claims other than this one: the leave-one-out history size (Q12). */
    public int historyExcluding(Claim claim) {
        return Math.max(0, providerVolume(claim.providerId()).total() - 1);
    }

    /**
     * Weekend share of the provider's other claims, leaving this claim out (D11, R7). Empty when the provider has
     * no other claim.
     */
    public OptionalDouble weekendRatioExcluding(Claim claim) {
        ProviderVolume others = volumeExcluding(claim);
        if (others.total() <= 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) others.weekend() / others.total());
    }

    private static <K> NavigableMap<K, List<Claim>> freezeLists(TreeMap<K, List<Claim>> map) {
        TreeMap<K, List<Claim>> frozen = new TreeMap<>(map.comparator());
        map.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        return Collections.unmodifiableNavigableMap(frozen);
    }

    private static AmountStats stats(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        double mean = sorted.stream().mapToLong(Long::longValue).sum() / (double) n;
        double sd = Double.NaN;
        if (n >= 2) {
            double squares = 0;
            for (long x : sorted) {
                squares += (x - mean) * (x - mean);
            }
            sd = Math.sqrt(squares / (n - 1));
        }
        return new AmountStats(n, mean, sd, sorted);
    }
}
