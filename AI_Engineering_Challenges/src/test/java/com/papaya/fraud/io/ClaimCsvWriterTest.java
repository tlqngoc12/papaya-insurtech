package com.papaya.fraud.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.ClaimType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ClaimCsvWriterTest {

    static final List<Claim> CLAIMS = List.of(
            new Claim("CLM-00001", "M-0001", "PRV-01", "Saigon General Hospital, District 1",
                    LocalDate.of(2024, 3, 4), ClaimType.OUTPATIENT, "J06.9", List.of("P-1002", "P-1001"), 900, false),
            new Claim("CLM-00002", "M-0002", "PRV-02", "Clinic \"An Binh\"",
                    LocalDate.of(2024, 3, 9), ClaimType.DENTAL, "K02.1", List.of("P-3002"), 800, true),
            new Claim("CLM-00003", "M-0003", "PRV-01", "Saigon General Hospital, District 1",
                    LocalDate.of(2024, 12, 31), ClaimType.INPATIENT, "I21.9", List.of("P-2010", "P-2011"),
                    1_234_567_890_123L, false));

    @Test
    void writesTenColumnsWithIsoDatesPipesQuotingAndLfOnly() {
        String expected = """
                claim_id,member_id,provider_id,provider_name,claim_date,claim_type,diagnosis_code,procedure_codes,submitted_amount,is_weekend
                CLM-00001,M-0001,PRV-01,"Saigon General Hospital, District 1",2024-03-04,OUTPATIENT,J06.9,P-1002|P-1001,900,false
                CLM-00002,M-0002,PRV-02,"Clinic ""An Binh\"\"\",2024-03-09,DENTAL,K02.1,P-3002,800,true
                CLM-00003,M-0003,PRV-01,"Saigon General Hospital, District 1",2024-12-31,INPATIENT,I21.9,P-2010|P-2011,1234567890123,false
                """;
        byte[] bytes = ClaimCsvWriter.toBytes(CLAIMS);

        assertEquals(expected, new String(bytes, StandardCharsets.UTF_8));
        assertTrue(bytes[0] == 'c', "no BOM: file starts with the header");
        for (byte b : bytes) {
            assertTrue(b != '\r', "line endings are \\n only");
        }
    }

    @Test
    void roundTripIsByteForByteIdentical() {
        byte[] first = ClaimCsvWriter.toBytes(CLAIMS);
        LoadResult loaded = new ClaimCsvReader(CatalogLoader.loadDefault()).read(first);
        byte[] second = ClaimCsvWriter.toBytes(loaded.claims());

        assertEquals(CLAIMS, loaded.claims(), "procedure order and every field survive");
        assertEquals(List.of(), loaded.rejected());
        assertArrayEquals(first, second);
    }

    @Test
    void defaultLocaleDoesNotChangeTheBytes() {
        byte[] reference = ClaimCsvWriter.toBytes(CLAIMS);
        Locale original = Locale.getDefault();
        try {
            for (Locale locale : List.of(Locale.GERMANY, Locale.forLanguageTag("vi-VN"), Locale.forLanguageTag("ar-EG"))) {
                Locale.setDefault(locale);
                assertArrayEquals(reference, ClaimCsvWriter.toBytes(CLAIMS), locale.toString());
            }
        } finally {
            Locale.setDefault(original);
        }
    }
}
