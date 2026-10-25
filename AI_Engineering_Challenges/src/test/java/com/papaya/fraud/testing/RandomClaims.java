package com.papaya.fraud.testing;

import static com.papaya.fraud.testing.ClaimBuilder.claim;

import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.ClaimType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Random but valid claims for load tests (Q19: not the dataset generator). About 500 members and 50 providers
 * over 2024, a mix of single- and multi-procedure claims, amounts around each procedure's base price.
 */
public final class RandomClaims {

    private record Template(ClaimType type, String diagnosis, String[] procedures, long basePrice) {
    }

    private static final Template[] TEMPLATES = {
        new Template(ClaimType.OUTPATIENT, "J06.9", new String[] {"P-1001"}, 300),
        new Template(ClaimType.OUTPATIENT, "J06.9", new String[] {"P-1001", "P-1002"}, 900),
        new Template(ClaimType.OUTPATIENT, "I10", new String[] {"P-1102"}, 1_300),
        new Template(ClaimType.OUTPATIENT, "E11.9", new String[] {"P-1101"}, 1_200),
        new Template(ClaimType.DENTAL, "K02.1", new String[] {"P-3002"}, 800),
        new Template(ClaimType.INPATIENT, "J18.9", new String[] {"P-2001", "P-2002"}, 21_000),
        new Template(ClaimType.INPATIENT, "K35.8", new String[] {"P-2101"}, 30_000),
        new Template(ClaimType.INPATIENT, "I21.9", new String[] {"P-2010"}, 40_000),
    };

    private RandomClaims() {
    }

    public static List<Claim> generate(int n, long seed) {
        Random random = new Random(seed);
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<Claim> claims = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Template t = TEMPLATES[random.nextInt(TEMPLATES.length)];
            long amount = Math.max(1, Math.round(t.basePrice() * (1 + 0.15 * random.nextGaussian())));
            claims.add(claim(String.format("CLM-%07d", i))
                    .withMember("M-" + random.nextInt(500))
                    .withProvider("PRV-" + random.nextInt(50))
                    .withDate(start.plusDays(random.nextInt(366)))
                    .withType(t.type())
                    .withDiagnosis(t.diagnosis())
                    .withProcedures(t.procedures())
                    .withAmount(amount)
                    .build());
        }
        return claims;
    }
}
