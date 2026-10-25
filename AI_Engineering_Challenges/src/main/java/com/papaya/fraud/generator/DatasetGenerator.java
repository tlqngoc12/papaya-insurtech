package com.papaya.fraud.generator;

import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.catalog.Diagnosis;
import com.papaya.fraud.generator.GeneratedDataset.ScenarioDay;
import com.papaya.fraud.generator.GeneratorConfig.BandHost;
import com.papaya.fraud.generator.GeneratorConfig.CaseMixEntry;
import com.papaya.fraud.generator.GeneratorConfig.Template;
import com.papaya.fraud.generator.ProviderProfile.Tier;
import com.papaya.fraud.io.ClaimCsv;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.ClaimType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Builds the synthetic dataset described in {@code docs/generator-spec.md}. Never uses the engine or its config;
 * all parameters come from {@link GeneratorConfig}, reference data from {@link Catalog}.
 *
 * <p>Implemented so far (Task 12): providers, members, the reserved scenario days, the ordinary legit claims
 * (including the natural claims of the four band hosts and the chronic follow-ups), claim ids and ground truth.
 */
public final class DatasetGenerator {

    static final String STAGE_PROVIDERS = "providers";
    static final String STAGE_MEMBERS = "members";
    static final String STAGE_RESERVATIONS = "reservations";
    static final String STAGE_BASE = "base";
    static final String STAGE_FOLLOWUPS = "followups";
    static final String STAGE_CLAIM_IDS = "claim_ids";

    static final String INPATIENT_ONLY_POOL = "inpatient_only";

    private static final List<String> NAME_PREFIXES = List.of(
            "Riverside", "Lotus", "Sunrise", "Harbor", "Maple", "Silver Oak", "Lakeside", "Northfield", "Crescent",
            "Bluebell", "Willow", "Cedar", "Pinecrest", "Meadow", "Summit", "Brookside", "Starlight", "Evergreen",
            "Hillcrest", "Oakwood", "Seaview", "Clearwater", "Kingsley", "Ashford", "Fairview", "Rosewood",
            "Bayview", "Amberfield", "Larkspur", "Moonstone");

    private final GeneratorConfig config;
    private final Catalog catalog;
    private final List<String> inpatientOnlyDiagnoses;
    private final List<LocalDate> allDays;
    private final List<LocalDate> weekdays;

    public DatasetGenerator(GeneratorConfig config, Catalog catalog) {
        this.config = config;
        this.catalog = catalog;
        this.inpatientOnlyDiagnoses = catalog.diagnoses().stream()
                .filter(d -> d.claimTypes().equals(EnumSet.of(ClaimType.INPATIENT)))
                .map(Diagnosis::code).sorted().toList();
        int year = config.dataset().year();
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = LocalDate.of(year, 1, 1); d.getYear() == year; d = d.plusDays(1)) {
            days.add(d);
        }
        this.allDays = List.copyOf(days);
        this.weekdays = days.stream().filter(d -> !ClaimCsv.isWeekend(d)).toList();
        checkCaseMixAgainstCatalog();
    }

    public GeneratedDataset generate(long seed) {
        List<ProviderProfile> providers = providers(StageRandom.forStage(seed, STAGE_PROVIDERS));
        List<MemberProfile> members = members(StageRandom.forStage(seed, STAGE_MEMBERS));
        Reservations reservations = reservations(StageRandom.forStage(seed, STAGE_RESERVATIONS), providers);
        List<Draft> drafts = base(StageRandom.forStage(seed, STAGE_BASE), providers, members, reservations);
        followups(StageRandom.forStage(seed, STAGE_FOLLOWUPS), drafts, reservations);
        assignClaimIds(StageRandom.forStage(seed, STAGE_CLAIM_IDS), drafts);
        return output(drafts, providers, members, reservations);
    }

    // ---------------------------------------------------------------- providers and members

    private List<ProviderProfile> providers(SplittableRandom r) {
        GeneratorConfig.ProviderTiers tiers = config.dataset().providerTiers();
        int total = tiers.total();
        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            numbers.add(i);
        }
        StageRandom.shuffle(r, numbers); // ids do not reveal the tier (G12)

        List<String> hospitalNames = shuffledPrefixes(r);
        List<String> clinicNames = shuffledPrefixes(r);
        List<String> dentalNames = shuffledPrefixes(r);
        double shape = config.volumes().providerWeightShape();
        List<ProviderProfile> providers = new ArrayList<>();
        int next = 0;
        int hospital = 0;
        for (int i = 0; i < tiers.weekdayOnlyHospitals(); i++) {
            providers.add(new ProviderProfile(providerId(numbers.get(next++)),
                    hospitalNames.get(hospital++) + " General Hospital", Tier.WEEKDAY_ONLY_HOSPITAL, 1.0));
        }
        for (int i = 0; i < tiers.regularHospitals(); i++) {
            providers.add(new ProviderProfile(providerId(numbers.get(next++)),
                    hospitalNames.get(hospital++) + " General Hospital", Tier.REGULAR_HOSPITAL,
                    StageRandom.gamma(r, shape)));
        }
        for (int i = 0; i < tiers.clinics(); i++) {
            providers.add(new ProviderProfile(providerId(numbers.get(next++)), clinicNames.get(i) + " Family Clinic",
                    Tier.CLINIC, StageRandom.gamma(r, shape)));
        }
        for (int i = 0; i < tiers.dentalClinics(); i++) {
            providers.add(new ProviderProfile(providerId(numbers.get(next++)), dentalNames.get(i) + " Dental Care",
                    Tier.DENTAL_CLINIC, StageRandom.gamma(r, shape)));
        }
        return providers;
    }

    private static String providerId(int number) {
        return String.format("PRV-%02d", number);
    }

    private static List<String> shuffledPrefixes(SplittableRandom r) {
        List<String> names = new ArrayList<>(NAME_PREFIXES);
        StageRandom.shuffle(r, names);
        return names;
    }

    private List<MemberProfile> members(SplittableRandom r) {
        int n = config.dataset().memberCount();
        double[] weights = new double[n];
        for (int i = 0; i < n; i++) {
            weights[i] = StageRandom.gamma(r, config.volumes().memberWeightShape());
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        StageRandom.shuffle(r, order);
        int chronicCount = (int) Math.round(config.chronic().memberShare() * n);
        String[] chronic = new String[n];
        for (int i = 0; i < chronicCount; i++) {
            chronic[order.get(i)] = StageRandom.pick(r, config.chronic().diagnoses());
        }
        List<MemberProfile> members = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            members.add(new MemberProfile(String.format("M-%04d", i + 1), weights[i], chronic[i]));
        }
        return members;
    }

    // ---------------------------------------------------------------- reserved scenario days

    private record Reservations(ScenarioDay phantomDay, ScenarioDay day30) {
        boolean contains(ProviderProfile provider, LocalDate date) {
            return matches(phantomDay, provider, date) || matches(day30, provider, date);
        }

        private static boolean matches(ScenarioDay day, ProviderProfile provider, LocalDate date) {
            return day.providerId().equals(provider.id()) && day.date().equals(date);
        }
    }

    /** Two mid-sized clinics (Q8): the phantom day and the 30-claim day, each on a weekday. */
    private Reservations reservations(SplittableRandom r, List<ProviderProfile> providers) {
        List<ProviderProfile> clinics = providers.stream().filter(p -> p.tier() == Tier.CLINIC)
                .sorted(Comparator.comparingDouble(ProviderProfile::weight).thenComparing(ProviderProfile::id))
                .toList();
        ProviderProfile phantom = clinics.get(clinics.size() / 2);
        ProviderProfile day30 = clinics.get(clinics.size() / 2 + 1);
        return new Reservations(new ScenarioDay(phantom.id(), StageRandom.pick(r, weekdays)),
                new ScenarioDay(day30.id(), StageRandom.pick(r, weekdays)));
    }

    // ---------------------------------------------------------------- ordinary legit claims

    private List<Draft> base(SplittableRandom r, List<ProviderProfile> providers, List<MemberProfile> members,
                             Reservations reservations) {
        int total = config.baseClaims();
        int hostClaims = config.bandHostBaseClaims();
        int followups = config.chronic().followups();
        int generic = total - hostClaims - followups;
        var mix = config.volumes().claimTypeMix();
        // Expected type counts over the whole base; band hosts are inpatient, follow-ups outpatient.
        ClaimType[] types = {ClaimType.OUTPATIENT, ClaimType.INPATIENT, ClaimType.DENTAL};
        double[] typeWeights = {
            mix.get("OUTPATIENT") * total - followups,
            mix.get("INPATIENT") * total - hostClaims,
            mix.get("DENTAL") * total};

        List<Draft> drafts = new ArrayList<>();
        for (BandHost host : config.bandHosts()) {
            for (int i = 0; i < host.naturalClaims() - host.nearMissClaims(); i++) {
                Draft d = new Draft(drafts.size());
                d.type = ClaimType.INPATIENT;
                d.host = host;
                d.diagnosis = host.diagnosis();
                d.procedures = List.copyOf(host.procedures());
                d.scenario = "band_host";
                drafts.add(d);
            }
        }
        for (int i = 0; i < generic; i++) {
            Draft d = new Draft(drafts.size());
            d.type = types[StageRandom.weightedIndex(r, typeWeights)];
            d.outpatientAtHospital = d.type == ClaimType.OUTPATIENT
                    && r.nextDouble() < config.volumes().outpatientHospitalShare();
            drafts.add(d);
        }

        assignProviders(r, drafts, providers);
        assignMembers(r, drafts, members);
        for (Draft d : drafts) {
            if (d.host == null) {
                chooseCase(r, d);
            }
            price(r, d);
            d.date = drawDate(r, d.provider, reservations);
        }
        return drafts;
    }

    private void assignProviders(SplittableRandom r, List<Draft> drafts, List<ProviderProfile> providers) {
        List<Draft> hospital = new ArrayList<>();
        List<Draft> clinic = new ArrayList<>();
        List<Draft> dental = new ArrayList<>();
        for (Draft d : drafts) {
            if (d.type == ClaimType.INPATIENT || d.outpatientAtHospital) {
                hospital.add(d);
            } else if (d.type == ClaimType.OUTPATIENT) {
                clinic.add(d);
            } else {
                dental.add(d);
            }
        }
        StageRandom.shuffle(r, hospital);
        StageRandom.shuffle(r, clinic);
        StageRandom.shuffle(r, dental);

        List<ProviderProfile> weekdayOnly = byTier(providers, Tier.WEEKDAY_ONLY_HOSPITAL);
        int quota = config.volumes().weekdayOnlyHospitalClaims();
        int reserved = quota * weekdayOnly.size();
        if (hospital.size() < reserved) {
            throw new IllegalStateException("only " + hospital.size() + " hospital claims for a weekday-only quota of "
                    + reserved);
        }
        for (int i = 0; i < reserved; i++) {
            hospital.get(i).provider = weekdayOnly.get(i % weekdayOnly.size()); // exactly `quota` each (Q13)
        }
        spread(r, hospital.subList(reserved, hospital.size()), byTier(providers, Tier.REGULAR_HOSPITAL));
        spread(r, clinic, byTier(providers, Tier.CLINIC));
        spread(r, dental, byTier(providers, Tier.DENTAL_CLINIC));
    }

    /** Gives every provider at least one claim, then the rest by provider weight. */
    private static void spread(SplittableRandom r, List<Draft> drafts, List<ProviderProfile> providers) {
        double[] weights = providers.stream().mapToDouble(ProviderProfile::weight).toArray();
        for (int i = 0; i < drafts.size(); i++) {
            drafts.get(i).provider = i < providers.size()
                    ? providers.get(i)
                    : providers.get(StageRandom.weightedIndex(r, weights));
        }
    }

    private static List<ProviderProfile> byTier(List<ProviderProfile> providers, Tier tier) {
        return providers.stream().filter(p -> p.tier() == tier).toList();
    }

    /** Every member gets one claim first (so all 500 appear), the rest go by member weight. */
    private static void assignMembers(SplittableRandom r, List<Draft> drafts, List<MemberProfile> members) {
        List<Draft> order = new ArrayList<>(drafts);
        StageRandom.shuffle(r, order);
        List<MemberProfile> firstPass = new ArrayList<>(members);
        StageRandom.shuffle(r, firstPass);
        double[] weights = members.stream().mapToDouble(MemberProfile::weight).toArray();
        for (int i = 0; i < order.size(); i++) {
            order.get(i).member = i < firstPass.size()
                    ? firstPass.get(i)
                    : members.get(StageRandom.weightedIndex(r, weights));
        }
    }

    /** Diagnosis then procedure template, following D7; chronic members mostly use their chronic diagnosis. */
    private void chooseCase(SplittableRandom r, Draft d) {
        List<CaseMixEntry> entries = config.caseMix().get(d.type.name());
        CaseMixEntry entry;
        String chronic = d.member.chronicDiagnosis();
        if (d.type == ClaimType.OUTPATIENT && chronic != null
                && r.nextDouble() < config.chronic().outpatientShare()) {
            entry = entryFor(ClaimType.OUTPATIENT, chronic);
        } else {
            entry = entries.get(StageRandom.weightedIndex(r,
                    entries.stream().mapToDouble(CaseMixEntry::weight).toArray()));
        }
        d.diagnosis = INPATIENT_ONLY_POOL.equals(entry.diagnosisPool())
                ? StageRandom.pick(r, inpatientOnlyDiagnoses)
                : entry.diagnosis();
        d.procedures = List.copyOf(template(r, entry).procedures());
    }

    private CaseMixEntry entryFor(ClaimType type, String diagnosis) {
        return config.caseMix().get(type.name()).stream()
                .filter(e -> diagnosis.equals(e.diagnosis()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no " + type + " case mix for " + diagnosis));
    }

    private static Template template(SplittableRandom r, CaseMixEntry entry) {
        return entry.templates().get(StageRandom.weightedIndex(r,
                entry.templates().stream().mapToDouble(Template::weight).toArray()));
    }

    /** amount = round(sum of base prices x m), m ~ N(1, cv) truncated at +-sigmas (D8). */
    private void price(SplittableRandom r, Draft d) {
        d.multiplier = StageRandom.priceMultiplier(r, config.pricing().noiseCv(),
                config.pricing().noiseTruncationSigmas());
        d.amount = Math.max(1, Math.round(basePrice(d.procedures) * d.multiplier));
    }

    private long basePrice(List<String> procedures) {
        return procedures.stream().mapToLong(code -> catalog.procedure(code).orElseThrow().basePrice()).sum();
    }

    /** Weekday-only hospitals never get a weekend date; reserved scenario days are never used (G2 redraw). */
    private LocalDate drawDate(SplittableRandom r, ProviderProfile provider, Reservations reservations) {
        List<LocalDate> pool = provider.weekdayOnly() ? weekdays : allDays;
        for (int attempt = 0; attempt < config.maxRedraws(); attempt++) {
            LocalDate date = StageRandom.pick(r, pool);
            if (!reservations.contains(provider, date)) {
                return date;
            }
        }
        throw new IllegalStateException("no free date for " + provider.id() + " after " + config.maxRedraws()
                + " draws");
    }

    // ---------------------------------------------------------------- natural follow-ups (G3)

    /**
     * A few chronic members come back 2-7 days later with the same diagnosis: genuine claims that the rapid
     * re-submission rule will flag (natural false positives, kept as {@code none}). Never at a weekday-only
     * hospital, whose claim count is a fixed quota.
     */
    private void followups(SplittableRandom r, List<Draft> drafts, Reservations reservations) {
        int wanted = config.chronic().followups();
        List<Draft> sources = new ArrayList<>(drafts.stream()
                .filter(d -> d.type == ClaimType.OUTPATIENT && d.member.chronicDiagnosis() != null
                        && d.member.chronicDiagnosis().equals(d.diagnosis) && !d.provider.weekdayOnly())
                .toList());
        StageRandom.shuffle(r, sources);
        int created = 0;
        for (Draft source : sources) {
            if (created == wanted) {
                break;
            }
            LocalDate date = followupDate(r, source, reservations);
            if (date == null) {
                continue;
            }
            Draft f = new Draft(drafts.size());
            f.type = ClaimType.OUTPATIENT;
            f.provider = source.provider;
            f.member = source.member;
            f.diagnosis = source.diagnosis;
            f.procedures = List.copyOf(template(r, entryFor(ClaimType.OUTPATIENT, source.diagnosis)).procedures());
            price(r, f);
            f.date = date;
            f.scenario = "natural_followup";
            f.related = source;
            drafts.add(f);
            created++;
        }
        if (created < wanted) {
            throw new IllegalStateException("only " + created + " of " + wanted + " follow-ups could be placed");
        }
    }

    private LocalDate followupDate(SplittableRandom r, Draft source, Reservations reservations) {
        GeneratorConfig.IntRange gap = config.chronic().followupGapDays();
        for (int attempt = 0; attempt < config.maxRedraws(); attempt++) {
            LocalDate date = source.date.plusDays(gap.min() + r.nextInt(gap.max() - gap.min() + 1));
            boolean ok = date.getYear() == config.dataset().year()
                    && !(source.provider.weekdayOnly() && ClaimCsv.isWeekend(date))
                    && !reservations.contains(source.provider, date);
            if (ok) {
                return date;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- claim ids (D14, G12)

    /**
     * Sorted by date; within a day by a random permutation (so ids do not leak which claims were generated later),
     * except that a claim that must follow another on the same day (a duplicate copy) is moved after it.
     */
    private static void assignClaimIds(SplittableRandom r, List<Draft> drafts) {
        for (Draft d : drafts) {
            d.tieBreak = r.nextLong();
        }
        List<Draft> order = new ArrayList<>(drafts);
        order.sort(Comparator.comparing((Draft d) -> d.date).thenComparingLong(d -> d.tieBreak)
                .thenComparingInt(d -> d.key));
        for (Draft d : drafts) {
            if (d.mustFollow != null) {
                int me = order.indexOf(d);
                int other = order.indexOf(d.mustFollow);
                if (me < other) {
                    order.set(me, d.mustFollow);
                    order.set(other, d);
                }
            }
        }
        for (int i = 0; i < order.size(); i++) {
            order.get(i).claimId = String.format("CLM-%05d", i + 1);
            order.get(i).position = i;
        }
    }

    // ---------------------------------------------------------------- output

    private static final Set<String> LEGIT_LABELS = Set.of("none", "legit_near_miss", "legit_noise");

    private GeneratedDataset output(List<Draft> drafts, List<ProviderProfile> providers, List<MemberProfile> members,
                                    Reservations reservations) {
        List<Draft> order = new ArrayList<>(drafts);
        order.sort(Comparator.comparingInt(d -> d.position));
        List<Claim> claims = new ArrayList<>(order.size());
        List<GroundTruthRow> truth = new ArrayList<>(order.size());
        for (Draft d : order) {
            claims.add(new Claim(d.claimId, d.member.id(), d.provider.id(), d.provider.name(), d.date, d.type,
                    d.diagnosis, d.procedures, d.amount, ClaimCsv.isWeekend(d.date)));
            truth.add(new GroundTruthRow(d.claimId, !LEGIT_LABELS.contains(d.pattern), d.pattern,
                    d.related == null ? "" : d.related.claimId, d.scenario));
        }
        List<ProviderProfile> byId = providers.stream().sorted(Comparator.comparing(ProviderProfile::id)).toList();
        return new GeneratedDataset(claims, truth, byId, members, reservations.phantomDay(), reservations.day30());
    }

    /** Fails fast if a template is not valid for its diagnosis or claim type (D7). */
    private void checkCaseMixAgainstCatalog() {
        List<String> problems = new ArrayList<>();
        config.caseMix().forEach((typeName, entries) -> {
            ClaimType type = ClaimType.valueOf(typeName);
            for (CaseMixEntry e : entries) {
                List<String> diagnoses = INPATIENT_ONLY_POOL.equals(e.diagnosisPool())
                        ? inpatientOnlyDiagnoses : List.of(e.diagnosis());
                for (String dx : diagnoses) {
                    for (Template t : e.templates()) {
                        checkTemplate(problems, type, dx, t.procedures());
                    }
                }
            }
        });
        for (BandHost h : config.bandHosts()) {
            checkTemplate(problems, ClaimType.INPATIENT, h.diagnosis(), h.procedures());
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Generator case mix inconsistent with the catalog:\n  - "
                    + String.join("\n  - ", problems));
        }
    }

    private void checkTemplate(List<String> problems, ClaimType type, String dx, List<String> procedures) {
        if (catalog.diagnosis(dx).filter(d -> d.claimTypes().contains(type)).isEmpty()) {
            problems.add(dx + " is not a " + type + " diagnosis");
        }
        for (String code : procedures) {
            if (!catalog.isValidPair(dx, code)) {
                problems.add(dx + " + " + code + " is not in the whitelist");
            } else if (!catalog.procedure(code).orElseThrow().claimTypes().contains(type)) {
                problems.add(code + " is not billable on a " + type + " claim");
            }
        }
    }

    /** Mutable working record of one claim while the stages fill it in. */
    static final class Draft {
        final int key;
        ClaimType type;
        boolean outpatientAtHospital;
        BandHost host;
        ProviderProfile provider;
        MemberProfile member;
        String diagnosis;
        List<String> procedures;
        long amount;
        double multiplier;
        LocalDate date;
        String pattern = "none";
        String scenario = "base";
        Draft related;
        Draft mustFollow;
        long tieBreak;
        String claimId;
        int position;

        Draft(int key) {
            this.key = key;
        }
    }
}
