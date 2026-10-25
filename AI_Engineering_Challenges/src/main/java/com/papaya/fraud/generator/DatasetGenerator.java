package com.papaya.fraud.generator;

import com.papaya.fraud.catalog.Bundle;
import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.catalog.Diagnosis;
import com.papaya.fraud.catalog.Procedure;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds the synthetic dataset described in {@code docs/generator-spec.md}. Never uses the engine or its config;
 * all parameters come from {@link GeneratorConfig}, reference data from {@link Catalog}.
 *
 * <p>Implemented so far: providers, members, the reserved scenario days, the ordinary legit claims (including the
 * natural claims of the four band hosts and the chronic follow-ups) (Task 12); duplicate, rapid re-submission,
 * upcoding and unbundling fraud (Task 13); phantom billing, weekend anomaly, diagnosis-procedure mismatch and
 * amount clustering fraud (Task 14); claim ids and ground truth.
 */
public final class DatasetGenerator {

    static final String STAGE_PROVIDERS = "providers";
    static final String STAGE_MEMBERS = "members";
    static final String STAGE_RESERVATIONS = "reservations";
    static final String STAGE_BASE = "base";
    static final String STAGE_FOLLOWUPS = "followups";
    static final String STAGE_CLAIM_IDS = "claim_ids";
    static final String STAGE_DUPLICATE = "fraud_duplicate";
    static final String STAGE_RESUBMISSION = "fraud_resubmission";
    static final String STAGE_UPCODE = "fraud_upcode";
    static final String STAGE_UNBUNDLE = "fraud_unbundle";
    static final String STAGE_PHANTOM = "fraud_phantom";
    static final String STAGE_WEEKEND = "fraud_weekend";
    static final String STAGE_MISMATCH = "fraud_mismatch";
    static final String STAGE_BAND = "fraud_band";

    static final String PHANTOM = "phantom_billing";
    static final String WEEKEND = "weekend_anomaly";
    static final String MISMATCH = "diagnosis_procedure_mismatch";
    static final String BAND = "amount_clustering";

    static final String DUPLICATE = "duplicate_claim";
    static final String RESUBMISSION = "rapid_resubmission";
    static final String UPCODE = "upcoding";
    static final String UNBUNDLE = "unbundling";

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

        Context ctx = new Context(drafts, providers, members, reservations);
        duplicates(StageRandom.forStage(seed, STAGE_DUPLICATE), ctx);
        resubmissions(StageRandom.forStage(seed, STAGE_RESUBMISSION), ctx);
        upcodes(StageRandom.forStage(seed, STAGE_UPCODE), ctx);
        unbundles(StageRandom.forStage(seed, STAGE_UNBUNDLE), ctx);
        phantomDay(StageRandom.forStage(seed, STAGE_PHANTOM), ctx);
        weekendSurgeries(StageRandom.forStage(seed, STAGE_WEEKEND), ctx);
        mismatches(StageRandom.forStage(seed, STAGE_MISMATCH), ctx);
        bandAmounts(StageRandom.forStage(seed, STAGE_BAND), ctx);

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

    // ---------------------------------------------------------------- fraud patterns, shared machinery

    /** State shared by the fraud stages: all drafts so far and the generator's own member-diagnosis ledger. */
    private final class Context {
        final List<Draft> drafts;
        final List<ProviderProfile> providers;
        final List<MemberProfile> members;
        final double[] memberWeights;
        final Reservations reservations;
        final Ledger ledger = new Ledger();
        final Set<Draft> usedAsSource = new HashSet<>();

        Context(List<Draft> drafts, List<ProviderProfile> providers, List<MemberProfile> members,
                Reservations reservations) {
            this.drafts = drafts;
            this.providers = providers;
            this.members = members;
            this.memberWeights = members.stream().mapToDouble(MemberProfile::weight).toArray();
            this.reservations = reservations;
            drafts.forEach(ledger::add);
        }

        void add(Draft d) {
            drafts.add(d);
            ledger.add(d);
        }

        /** Days around a claim in which no other claim of the same member and diagnosis may sit (R2 window). */
        int isolation() {
            return config.resubmission().gapDays().max();
        }
    }

    /** Dates of the claims per member and diagnosis, as the generator itself placed them (no engine involved). */
    private static final class Ledger {
        private final Map<String, List<LocalDate>> dates = new TreeMap<>();

        void add(Draft d) {
            dates.computeIfAbsent(d.member.id() + "|" + d.diagnosis, k -> new ArrayList<>()).add(d.date);
        }

        int countWithin(String memberId, String diagnosis, LocalDate from, LocalDate to) {
            return (int) dates.getOrDefault(memberId + "|" + diagnosis, List.of()).stream()
                    .filter(d -> !d.isBefore(from) && !d.isAfter(to)).count();
        }
    }

    /**
     * An ordinary legit claim that can serve as the original of a duplicate or resubmission: unused, moderate
     * noise (m within the fraud cap, so the copy cannot look upcoded), outside the clustering band, and alone with
     * its member and diagnosis within the R2 window on both sides.
     */
    private boolean isolatedSource(Draft d, Context ctx) {
        int w = ctx.isolation();
        return d.pattern.equals("none") && d.scenario.equals("base") && !ctx.usedAsSource.contains(d)
                && d.multiplier <= config.pricing().fraudNoiseCap() && !inBand(d.amount)
                && ctx.ledger.countWithin(d.member.id(), d.diagnosis, d.date.minusDays(w), d.date.plusDays(w)) == 1;
    }

    private boolean inBand(long amount) {
        return amount >= config.plantedAmountRange().min() && amount <= config.plantedAmountRange().max();
    }

    /** Truncated-normal multiplier also capped at fraud_noise_cap (G7). */
    private double fraudMultiplier(SplittableRandom r) {
        for (int attempt = 0; attempt < config.maxRedraws(); attempt++) {
            double m = StageRandom.priceMultiplier(r, config.pricing().noiseCv(),
                    config.pricing().noiseTruncationSigmas());
            if (m <= config.pricing().fraudNoiseCap()) {
                return m;
            }
        }
        throw new IllegalStateException("no fraud multiplier under the cap after " + config.maxRedraws() + " draws");
    }

    /**
     * Picks a member and date for a new fraud claim (G2: redraws only on generator constraints): the provider's
     * date pool, never a reserved scenario day, and no other claim of that member and diagnosis within the window.
     */
    private void placeNewClaim(SplittableRandom r, Draft d, Context ctx) {
        List<LocalDate> pool = d.provider.weekdayOnly() ? weekdays : allDays;
        int w = ctx.isolation();
        for (int attempt = 0; attempt < config.maxRedraws(); attempt++) {
            MemberProfile member = ctx.members.get(StageRandom.weightedIndex(r, ctx.memberWeights));
            LocalDate date = StageRandom.pick(r, pool);
            if (!ctx.reservations.contains(d.provider, date)
                    && ctx.ledger.countWithin(member.id(), d.diagnosis, date.minusDays(w), date.plusDays(w)) == 0) {
                d.member = member;
                d.date = date;
                return;
            }
        }
        throw new IllegalStateException("cannot place a " + d.pattern + " claim after " + config.maxRedraws()
                + " draws");
    }

    private List<Draft> shuffledSources(SplittableRandom r, Context ctx) {
        List<Draft> sources = new ArrayList<>(ctx.drafts.stream().filter(d -> isolatedSource(d, ctx)).toList());
        StageRandom.shuffle(r, sources);
        return sources;
    }

    // ---------------------------------------------------------------- duplicate (R1, D9, Q15)

    /** Exact copies of isolated originals; only the copy is fraud and it always gets the later claim id. */
    private void duplicates(SplittableRandom r, Context ctx) {
        int wanted = config.fraudCounts().duplicate();
        int created = 0;
        for (Draft original : shuffledSources(r, ctx)) {
            if (created == wanted) {
                break;
            }
            if (!isolatedSource(original, ctx)) {
                continue;
            }
            Draft copy = new Draft(ctx.drafts.size());
            copy.type = original.type;
            copy.provider = original.provider;
            copy.member = original.member;
            copy.diagnosis = original.diagnosis;
            copy.procedures = original.procedures;
            copy.amount = original.amount;
            copy.multiplier = original.multiplier;
            copy.date = original.date;
            copy.pattern = DUPLICATE;
            copy.scenario = "duplicate";
            copy.related = original;
            copy.mustFollow = original;
            ctx.usedAsSource.add(original);
            ctx.add(copy);
            created++;
        }
        requireCount(DUPLICATE, created, wanted);
    }

    // ---------------------------------------------------------------- rapid re-submission (R2, Q15, G8)

    /**
     * Same member and diagnosis 1-7 days after an isolated claim, at the same provider 70% of the time, with a fresh
     * amount. Never on a weekend at a weekday-only hospital, never on a reserved day, and nothing else of that member
     * and diagnosis within the window around the pair.
     */
    private void resubmissions(SplittableRandom r, Context ctx) {
        int wanted = config.fraudCounts().resubmission();
        GeneratorConfig.IntRange gap = config.resubmission().gapDays();
        int w = ctx.isolation();
        int created = 0;
        for (Draft source : shuffledSources(r, ctx)) {
            if (created == wanted) {
                break;
            }
            if (!isolatedSource(source, ctx)) {
                continue;
            }
            for (int attempt = 0; attempt < config.maxRedraws(); attempt++) {
                LocalDate date = source.date.plusDays(gap.min() + r.nextInt(gap.max() - gap.min() + 1));
                ProviderProfile provider = r.nextDouble() < config.resubmission().sameProviderShare()
                        ? source.provider : otherProviderOfTier(r, source.provider, ctx);
                boolean ok = date.getYear() == config.dataset().year()
                        && !(provider.weekdayOnly() && ClaimCsv.isWeekend(date))
                        && !ctx.reservations.contains(provider, date)
                        && ctx.ledger.countWithin(source.member.id(), source.diagnosis,
                                source.date.minusDays(w), date.plusDays(w)) == 1;
                if (!ok) {
                    continue;
                }
                Draft re = new Draft(ctx.drafts.size());
                re.type = source.type;
                re.provider = provider;
                re.member = source.member;
                re.diagnosis = source.diagnosis;
                re.procedures = caseEntry(source.type, source.diagnosis)
                        .map(e -> List.copyOf(template(r, e).procedures()))
                        .orElse(source.procedures);
                re.date = date;
                if (!priceFraudClaim(r, re, Long.MAX_VALUE)) {
                    continue;
                }
                re.pattern = RESUBMISSION;
                re.scenario = "resubmission";
                re.related = source;
                ctx.usedAsSource.add(source);
                ctx.add(re);
                created++;
                break;
            }
        }
        requireCount(RESUBMISSION, created, wanted);
    }

    private static ProviderProfile otherProviderOfTier(SplittableRandom r, ProviderProfile provider, Context ctx) {
        List<ProviderProfile> others = ctx.providers.stream()
                .filter(p -> p.tier() == provider.tier() && !p.id().equals(provider.id())).toList();
        return others.isEmpty() ? provider : StageRandom.pick(r, others);
    }

    private Optional<CaseMixEntry> caseEntry(ClaimType type, String diagnosis) {
        return config.caseMix().get(type.name()).stream().filter(e -> diagnosis.equals(e.diagnosis())).findFirst();
    }

    /** Fresh capped noise; false if no amount below {@code cap} and outside the clustering band was found. */
    private boolean priceFraudClaim(SplittableRandom r, Draft d, long cap) {
        long base = basePrice(d.procedures);
        for (int attempt = 0; attempt < config.maxRedraws(); attempt++) {
            double m = fraudMultiplier(r);
            long amount = Math.max(1, Math.round(base * m));
            if (amount <= cap && !inBand(amount)) {
                d.multiplier = m;
                d.amount = amount;
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- upcoding (Q1, Q2, (c)1, G11)

    /**
     * New single-procedure claims priced at base x factor: strong U(1.5, 2.5) and mild U(1.2, 1.6). Targets only
     * signatures with at least min_group_claims ordinary legit single-procedure claims, never a band host, at most
     * max_per_group per signature; a factor landing the amount in the clustering band is redrawn.
     */
    private void upcodes(SplittableRandom r, Context ctx) {
        GeneratorConfig.Upcode cfg = config.upcode();
        Set<String> hostSignatures = new TreeSet<>();
        for (BandHost h : config.bandHosts()) {
            if (h.procedures().size() == 1) {
                hostSignatures.add(h.procedures().get(0));
            }
        }
        Map<String, List<Draft>> groups = new TreeMap<>();
        for (Draft d : ctx.drafts) {
            if (d.pattern.equals("none") && d.procedures.size() == 1 && !hostSignatures.contains(d.procedures.get(0))) {
                groups.computeIfAbsent(d.procedures.get(0), k -> new ArrayList<>()).add(d);
            }
        }
        groups.values().removeIf(models -> models.size() < cfg.minGroupClaims());
        List<String> signatures = new ArrayList<>(groups.keySet());
        Map<String, Integer> used = new TreeMap<>();

        int total = cfg.strongClaims() + cfg.mildClaims();
        for (int i = 0; i < total; i++) {
            boolean strong = i < cfg.strongClaims();
            GeneratorConfig.DoubleRange factor = strong ? cfg.strongFactor() : cfg.mildFactor();
            List<String> open = signatures.stream().filter(s -> used.getOrDefault(s, 0) < cfg.maxPerGroup()).toList();
            if (open.isEmpty()) {
                throw new IllegalStateException("no upcoding target group left after " + i + " claims");
            }
            String signature = StageRandom.pick(r, open);
            Draft model = StageRandom.pick(r, groups.get(signature));
            Draft up = new Draft(ctx.drafts.size());
            up.type = model.type;
            up.provider = model.provider;
            up.diagnosis = model.diagnosis;
            up.procedures = model.procedures;
            up.pattern = UPCODE;
            up.scenario = strong ? "upcode_strong" : "upcode_mild";
            long base = basePrice(up.procedures);
            boolean priced = false;
            for (int attempt = 0; attempt < config.maxRedraws() && !priced; attempt++) {
                double f = factor.min() + r.nextDouble() * (factor.max() - factor.min());
                long amount = Math.round(base * f);
                if (!inBand(amount)) {
                    up.multiplier = f;
                    up.amount = amount;
                    priced = true;
                }
            }
            if (!priced) {
                throw new IllegalStateException("no upcoded amount outside the clustering band for " + signature);
            }
            placeNewClaim(r, up, ctx);
            used.merge(signature, 1, Integer::sum);
            ctx.add(up);
        }
    }

    // ---------------------------------------------------------------- unbundling (R5, (c)2, G9)

    /**
     * New claims billing every component of a bundle (full) or the bundle code with one of its components (double
     * billing), with the bundle's own diagnosis; amount from capped noise, at most amount_cap and outside the band.
     */
    private void unbundles(SplittableRandom r, Context ctx) {
        GeneratorConfig.Unbundle cfg = config.unbundle();
        List<Bundle> bundles = catalog.bundles();
        int total = cfg.fullComponentClaims() + cfg.bundleWithComponentClaims();
        for (int i = 0; i < total; i++) {
            boolean full = i < cfg.fullComponentClaims();
            Bundle bundle = StageRandom.pick(r, bundles);
            Draft u = new Draft(ctx.drafts.size());
            u.diagnosis = bundleDiagnosis(bundle);
            u.procedures = full
                    ? List.copyOf(bundle.components())
                    : List.of(bundle.bundleCode(), StageRandom.pick(r, bundle.components()));
            u.type = commonClaimType(u.diagnosis, u.procedures);
            u.provider = providerFor(r, u.type, ctx);
            u.pattern = UNBUNDLE;
            u.scenario = full ? "unbundle_full" : "unbundle_double_billing";
            if (!priceFraudClaim(r, u, cfg.amountCap())) {
                throw new IllegalStateException("no unbundled amount under " + cfg.amountCap() + " for "
                        + bundle.bundleCode());
            }
            placeNewClaim(r, u, ctx);
            ctx.add(u);
        }
    }

    /** The first catalog diagnosis that allows the bundle code and all its components (e.g. K35.8 for P-2101). */
    private String bundleDiagnosis(Bundle bundle) {
        return catalog.diagnoses().stream().map(Diagnosis::code)
                .filter(dx -> catalog.isValidPair(dx, bundle.bundleCode())
                        && bundle.components().stream().allMatch(c -> catalog.isValidPair(dx, c)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no diagnosis for bundle " + bundle.bundleCode()));
    }

    private ClaimType commonClaimType(String diagnosis, List<String> procedures) {
        EnumSet<ClaimType> types = EnumSet.copyOf(catalog.diagnosis(diagnosis).orElseThrow().claimTypes());
        for (String code : procedures) {
            types.retainAll(catalog.procedure(code).orElseThrow().claimTypes());
        }
        return types.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("no claim type for " + diagnosis + " " + procedures));
    }

    /** A provider of a tier serving the claim type, by weight (outpatient at hospitals with the configured share). */
    private ProviderProfile providerFor(SplittableRandom r, ClaimType type, Context ctx) {
        List<ProviderProfile> pool = switch (type) {
            case INPATIENT -> ctx.providers.stream().filter(ProviderProfile::hospital).toList();
            case DENTAL -> byTier(ctx.providers, Tier.DENTAL_CLINIC);
            case OUTPATIENT -> r.nextDouble() < config.volumes().outpatientHospitalShare()
                    ? ctx.providers.stream().filter(ProviderProfile::hospital).toList()
                    : byTier(ctx.providers, Tier.CLINIC);
        };
        return pool.get(StageRandom.weightedIndex(r, pool.stream().mapToDouble(ProviderProfile::weight).toArray()));
    }

    // ---------------------------------------------------------------- phantom billing (R6, D10-B, Q8)

    /**
     * The fraud part of the phantom day: phantom_day.fraud_claims ordinary-looking outpatient claims of distinct
     * members at the reserved clinic on the reserved weekday. The 6 genuine claims of that day come in Task 15.
     */
    private void phantomDay(SplittableRandom r, Context ctx) {
        ScenarioDay day = ctx.reservations.phantomDay();
        ProviderProfile clinic = ctx.providers.stream().filter(p -> p.id().equals(day.providerId())).findFirst()
                .orElseThrow();
        Set<String> members = new HashSet<>();
        int w = ctx.isolation();
        List<CaseMixEntry> entries = config.caseMix().get(ClaimType.OUTPATIENT.name());
        double[] entryWeights = entries.stream().mapToDouble(CaseMixEntry::weight).toArray();
        for (int i = 0; i < config.phantomDay().fraudClaims(); i++) {
            Draft d = new Draft(ctx.drafts.size());
            d.type = ClaimType.OUTPATIENT;
            d.provider = clinic;
            d.date = day.date();
            boolean placed = false;
            for (int attempt = 0; attempt < config.maxRedraws() && !placed; attempt++) {
                CaseMixEntry entry = entries.get(StageRandom.weightedIndex(r, entryWeights));
                MemberProfile member = ctx.members.get(StageRandom.weightedIndex(r, ctx.memberWeights));
                if (members.contains(member.id()) || ctx.ledger.countWithin(member.id(), entry.diagnosis(),
                        day.date().minusDays(w), day.date().plusDays(w)) > 0) {
                    continue;
                }
                d.member = member;
                d.diagnosis = entry.diagnosis();
                d.procedures = List.copyOf(template(r, entry).procedures());
                placed = priceFraudClaim(r, d, Long.MAX_VALUE);
            }
            if (!placed) {
                throw new IllegalStateException("cannot place phantom claim " + i);
            }
            d.pattern = PHANTOM;
            d.scenario = "phantom_day";
            members.add(d.member.id());
            ctx.add(d);
        }
    }

    // ---------------------------------------------------------------- weekend anomaly (R7, D11, Q12, Q13)

    /**
     * Surgical inpatient claims on Saturdays and Sundays at the weekday-only hospitals: 3 at
     * hospitals_with_three_claims of them, 2 at the others. With 65 ordinary weekday claims each (plus any weekday
     * fraud), every one sees at most 2 other weekend claims, i.e. below 5% leave-one-out.
     */
    private void weekendSurgeries(SplittableRandom r, Context ctx) {
        List<ProviderProfile> hospitals = new ArrayList<>(byTier(ctx.providers, Tier.WEEKDAY_ONLY_HOSPITAL));
        StageRandom.shuffle(r, hospitals);
        List<LocalDate> weekendDays = allDays.stream().filter(ClaimCsv::isWeekend).toList();
        List<GeneratorConfig.SurgeryTemplate> templates = config.weekendSurgery().templates();
        double[] weights = templates.stream().mapToDouble(GeneratorConfig.SurgeryTemplate::weight).toArray();
        int w = ctx.isolation();
        for (int h = 0; h < hospitals.size(); h++) {
            int claims = h < config.weekendSurgery().hospitalsWithThreeClaims() ? 3 : 2;
            for (int i = 0; i < claims; i++) {
                GeneratorConfig.SurgeryTemplate t = templates.get(StageRandom.weightedIndex(r, weights));
                Draft d = new Draft(ctx.drafts.size());
                d.type = ClaimType.INPATIENT;
                d.provider = hospitals.get(h);
                d.diagnosis = t.diagnosis();
                d.procedures = List.copyOf(t.procedures());
                if (!priceFraudClaim(r, d, Long.MAX_VALUE)) {
                    throw new IllegalStateException("cannot price weekend surgery " + t.procedures());
                }
                boolean placed = false;
                for (int attempt = 0; attempt < config.maxRedraws() && !placed; attempt++) {
                    MemberProfile member = ctx.members.get(StageRandom.weightedIndex(r, ctx.memberWeights));
                    LocalDate date = StageRandom.pick(r, weekendDays);
                    if (ctx.ledger.countWithin(member.id(), d.diagnosis, date.minusDays(w), date.plusDays(w)) == 0) {
                        d.member = member;
                        d.date = date;
                        placed = true;
                    }
                }
                if (!placed) {
                    throw new IllegalStateException("cannot place a weekend surgery at " + d.provider.id());
                }
                d.pattern = WEEKEND;
                d.scenario = "weekend_surgery";
                ctx.add(d);
            }
        }
    }

    // ---------------------------------------------------------------- diagnosis-procedure mismatch (R8, Q14, Q15)

    /**
     * Copies the case of an ordinary claim and replaces exactly one procedure with one billable on the same claim
     * type but not valid for the diagnosis. The replacement never completes a bundle, never is the bundle code of a
     * procedure already there, never is a band host procedure. Weekday-only hospitals only get weekday dates, so a
     * surgical replacement can never fall on their weekend.
     */
    private void mismatches(SplittableRandom r, Context ctx) {
        List<Draft> models = ctx.drafts.stream().filter(d -> d.pattern.equals("none") && d.scenario.equals("base"))
                .toList();
        Set<String> excluded = Set.copyOf(config.mismatch().excludedReplacements());
        for (int i = 0; i < config.fraudCounts().mismatch(); i++) {
            Draft d = null;
            for (int attempt = 0; attempt < config.maxRedraws() && d == null; attempt++) {
                Draft model = StageRandom.pick(r, models);
                int slot = r.nextInt(model.procedures.size());
                List<String> candidates = catalog.procedures().stream()
                        .filter(p -> p.claimTypes().contains(model.type))
                        .map(Procedure::code)
                        .filter(code -> !excluded.contains(code) && !model.procedures.contains(code)
                                && !catalog.isValidPair(model.diagnosis, code))
                        .toList();
                if (candidates.isEmpty()) {
                    continue;
                }
                List<String> procedures = new ArrayList<>(model.procedures);
                procedures.set(slot, StageRandom.pick(r, candidates));
                if (formsBundle(procedures)) {
                    continue;
                }
                Draft m = new Draft(ctx.drafts.size());
                m.type = model.type;
                m.provider = model.provider;
                m.diagnosis = model.diagnosis;
                m.procedures = List.copyOf(procedures);
                if (priceFraudClaim(r, m, Long.MAX_VALUE)) {
                    d = m;
                }
            }
            if (d == null) {
                throw new IllegalStateException("cannot build mismatch claim " + i);
            }
            d.pattern = MISMATCH;
            d.scenario = "mismatch";
            placeNewClaim(r, d, ctx);
            ctx.add(d);
        }
    }

    /** All components of a bundle, or a bundle code together with one of its components. */
    private boolean formsBundle(List<String> procedures) {
        Set<String> codes = Set.copyOf(procedures);
        return catalog.bundles().stream().anyMatch(b -> codes.containsAll(b.components())
                || (codes.contains(b.bundleCode()) && b.components().stream().anyMatch(codes::contains)));
    }

    // ---------------------------------------------------------------- amount clustering (R9, Q3, Q15)

    /**
     * band_amount claims spread evenly over the four band hosts, amount uniform in the planted range. P-2009 is
     * surgical, but weekday-only hospitals only get weekday dates, so these claims never trigger weekend anomaly.
     */
    private void bandAmounts(SplittableRandom r, Context ctx) {
        int perHost = config.fraudCounts().bandAmount() / config.bandHosts().size();
        GeneratorConfig.LongRange range = config.plantedAmountRange();
        for (BandHost host : config.bandHosts()) {
            for (int i = 0; i < perHost; i++) {
                Draft d = new Draft(ctx.drafts.size());
                d.type = ClaimType.INPATIENT;
                d.host = host;
                d.diagnosis = host.diagnosis();
                d.procedures = List.copyOf(host.procedures());
                d.provider = providerFor(r, ClaimType.INPATIENT, ctx);
                d.amount = range.min() + r.nextLong(range.max() - range.min() + 1);
                d.multiplier = (double) d.amount / basePrice(d.procedures);
                d.pattern = BAND;
                d.scenario = "band_amount";
                placeNewClaim(r, d, ctx);
                ctx.add(d);
            }
        }
    }

    private static void requireCount(String pattern, int created, int wanted) {
        if (created < wanted) {
            throw new IllegalStateException("only " + created + " of " + wanted + " " + pattern + " claims placed");
        }
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
        for (GeneratorConfig.SurgeryTemplate t : config.weekendSurgery().templates()) {
            checkTemplate(problems, ClaimType.INPATIENT, t.diagnosis(), t.procedures());
            if (t.procedures().stream().noneMatch(code -> catalog.procedure(code).orElseThrow().surgical())) {
                problems.add("weekend surgery template " + t.procedures() + " has no surgical procedure");
            }
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
