package com.papaya.fraud.generator;

import com.papaya.fraud.model.Claim;
import java.time.LocalDate;
import java.util.List;

/**
 * Output of {@link DatasetGenerator}: claims and ground truth in claim_id order, plus the generator's own profiles
 * and reserved scenario days (for tests and the generation summary, never for the engine).
 */
public record GeneratedDataset(List<Claim> claims, List<GroundTruthRow> groundTruth, List<ProviderProfile> providers,
                               List<MemberProfile> members, ScenarioDay phantomDay, ScenarioDay day30) {

    /** A provider-day reserved for a scenario; ordinary claims never land on it. */
    public record ScenarioDay(String providerId, LocalDate date) {
    }

    public GeneratedDataset {
        claims = List.copyOf(claims);
        groundTruth = List.copyOf(groundTruth);
        providers = List.copyOf(providers);
        members = List.copyOf(members);
    }
}
