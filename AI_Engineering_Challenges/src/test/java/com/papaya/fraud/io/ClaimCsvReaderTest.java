package com.papaya.fraud.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.io.LoadResult.RejectedRow;
import com.papaya.fraud.io.LoadResult.Warning;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.ClaimType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimCsvReaderTest {

    private static final String HEADER = String.join(",", ClaimCsv.COLUMNS) + "\n";

    private final ClaimCsvReader reader = new ClaimCsvReader(CatalogLoader.loadDefault());

    /** Header plus data lines; line 1 is the header, so the first data row is line 2. */
    private LoadResult read(String... rows) {
        return reader.read((HEADER + String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> ids(LoadResult result) {
        return result.claims().stream().map(Claim::claimId).toList();
    }

    @Test
    void fiveRowExampleFromTheTaskSummary() {
        LoadResult result = read(
                "CLM-00001,M-0001,PRV-01,\"Saigon General Hospital, District 1\",2024-03-04,OUTPATIENT,J06.9,P-1001|P-1002,900,false",
                "CLM-00002,M-0002,PRV-02,Hanoi Dental Clinic,2024-03-09,DENTAL,K02.1,P-3002,800,false",
                "CLM-00003,M-0003,PRV-01,\"Saigon General Hospital, District 1\",2024-03-05,INPATIENT,K35.8,P-2101,30500,false",
                "CLM-00004,M-0004,PRV-03,Da Nang Clinic,2024-03-06,OUTPATIENT,I10,P-1005|P-9999,1400,false",
                "CLM-00005,M-0005,PRV-03,Da Nang Clinic,2024-03-07,OUTPATIENT,E11.9,P-1101,0,false");

        assertEquals(List.of("CLM-00001", "CLM-00002", "CLM-00003", "CLM-00004"), ids(result));
        assertEquals(List.of(new RejectedRow(6, "CLM-00005", "submitted_amount 0 must be > 0")), result.rejected());
        assertEquals(List.of(
                new Warning(3, "CLM-00002",
                        "is_weekend 'false' disagrees with claim_date 2024-03-09 (SATURDAY); using true"),
                new Warning(5, "CLM-00004", "procedure_code P-9999 is not in the catalog")), result.warnings());
        assertTrue(result.claims().get(1).isWeekend(), "the recomputed is_weekend is kept");
    }

    @Test
    void readsFileWithBomAndCrlfLikeExcel() {
        byte[] lf = ClaimCsvWriter.toBytes(ClaimCsvWriterTest.CLAIMS);
        String crlf = new String(lf, StandardCharsets.UTF_8).replace("\n", "\r\n");
        ByteArrayOutputStream excel = new ByteArrayOutputStream();
        excel.writeBytes(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        excel.writeBytes(crlf.getBytes(StandardCharsets.UTF_8));

        LoadResult result = reader.read(excel.toByteArray());

        assertEquals(ClaimCsvWriterTest.CLAIMS, result.claims());
        assertEquals(List.of(), result.rejected());
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void isWeekendIsRecomputedFromTheDateAndUppercaseBooleansAreAccepted() {
        LoadResult result = read(
                "C1,M1,P1,Clinic,2024-03-09,OUTPATIENT,J06.9,P-1001,300,TRUE",
                "C2,M1,P1,Clinic,2024-03-11,OUTPATIENT,J06.9,P-1001,300,true",
                "C3,M1,P1,Clinic,2024-03-12,OUTPATIENT,J06.9,P-1001,300,yes");

        assertEquals(List.of(true, false, false), result.claims().stream().map(Claim::isWeekend).toList());
        assertEquals(List.of(
                new Warning(3, "C2", "is_weekend 'true' disagrees with claim_date 2024-03-11 (MONDAY); using false"),
                new Warning(4, "C3", "is_weekend 'yes' disagrees with claim_date 2024-03-12 (TUESDAY); using false")),
                result.warnings());
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        LoadResult result = read(
                "C1,M1,P1,Clinic,2024-03-11,OUTPATIENT,J06.9,P-1001,0,false",
                "C2,M1,P1,Clinic,2024-03-11,OUTPATIENT,J06.9,P-1001,-5,false",
                "C3,M1,P1,Clinic,2024-03-11,OUTPATIENT,J06.9,P-1001,1,false");

        assertEquals(List.of("C3"), ids(result));
        assertEquals(List.of(
                new RejectedRow(2, "C1", "submitted_amount 0 must be > 0"),
                new RejectedRow(3, "C2", "submitted_amount -5 must be > 0")), result.rejected());
    }

    @Test
    void rejectsDuplicateClaimIdAndKeepsTheFirstOccurrence() {
        LoadResult result = read(
                "C1,M1,P1,Clinic,2024-03-11,OUTPATIENT,J06.9,P-1001,300,false",
                "C2,M1,P1,Clinic,2024-03-11,OUTPATIENT,J06.9,P-1001,300,false",
                "C1,M9,P9,Other,2024-04-01,DENTAL,K02.1,P-3002,800,false");

        assertEquals(List.of("C1", "C2"), ids(result));
        assertEquals("M1", result.claims().get(0).memberId());
        assertEquals(List.of(new RejectedRow(4, "C1", "duplicate claim_id, first seen on line 2")),
                result.rejected());
    }

    @Test
    void rejectsEmptyProcedureCodesAndEmptyCodeInTheList() {
        LoadResult result = read(
                "C1,M1,P1,Clinic,2024-03-11,OUTPATIENT,J06.9,,300,false",
                "C2,M1,P1,Clinic,2024-03-11,OUTPATIENT,J06.9,P-1001|,300,false");

        assertEquals(List.of(), result.claims());
        assertEquals(List.of(
                new RejectedRow(2, "C1", "procedure_codes is empty"),
                new RejectedRow(3, "C2", "procedure_codes 'P-1001|' contains an empty code")), result.rejected());
    }

    @Test
    void keepsCodesMissingFromTheCatalogWithWarnings() {
        LoadResult result = read("C1,M1,P1,Clinic,2024-03-11,OUTPATIENT,Z99.9,P-1001|P-0000,300,false");

        assertEquals(List.of("C1"), ids(result));
        assertEquals(List.of("P-1001", "P-0000"), result.claims().get(0).procedureCodes());
        assertEquals(List.of(
                new Warning(2, "C1", "diagnosis_code Z99.9 is not in the catalog"),
                new Warning(2, "C1", "procedure_code P-0000 is not in the catalog")), result.warnings());
    }

    @Test
    void rejectsMissingColumnBadDateAndBadTypeWithLineNumbers() {
        LoadResult result = read(
                "C1,M1,P1,Clinic,2024-03-11,OUTPATIENT,J06.9,P-1001,300",
                "C2,M1,P1,Clinic,11/03/2024,OUTPATIENT,J06.9,P-1001,300,false",
                "",
                "C3,M1,P1,Clinic,2024-02-30,OUTPATIENT,J06.9,P-1001,300,false",
                "C4,M1,P1,Clinic,2024-03-11,EMERGENCY,J06.9,P-1001,300,false",
                "C5,,P1,Clinic,2024-03-11,OUTPATIENT,J06.9,P-1001,300,false");

        assertEquals(List.of(), result.claims());
        assertEquals(List.of(
                new RejectedRow(2, "C1", "expected 10 columns, found 9"),
                new RejectedRow(3, "C2", "claim_date '11/03/2024' is not an ISO date (yyyy-MM-dd)"),
                new RejectedRow(5, "C3", "claim_date '2024-02-30' is not an ISO date (yyyy-MM-dd)"),
                new RejectedRow(6, "C4", "claim_type 'EMERGENCY' is not one of [OUTPATIENT, INPATIENT, DENTAL]"),
                new RejectedRow(7, "C5", "member_id is empty")), result.rejected());
    }

    @Test
    void wrongHeaderRejectsTheWholeFileWithoutThrowing() {
        LoadResult result = reader.read(
                "id,member\nC1,M1\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of(), result.claims());
        assertEquals(1, result.rejected().size());
        assertEquals(1, result.rejected().get(0).line());
        assertTrue(result.rejected().get(0).reason().startsWith(
                "invalid header: unexpected column(s) [id, member], missing column(s) [claim_id, member_id,"),
                result.rejected().get(0).reason());
    }

    /** Guards "the engine never reads ground truth": a claims file carrying a label column is refused. */
    @Test
    void headerWithGroundTruthColumnIsRejectedAndNamesIt() {
        String header = String.join(",", ClaimCsv.COLUMNS) + ",is_fraud\n";
        String row = "C1,M1,P1,Clinic,2024-03-11,OUTPATIENT,J06.9,P-1001,300,false,true\n";
        LoadResult result = reader.read((header + row).getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of(), result.claims());
        assertEquals(List.of(new RejectedRow(1, null,
                "invalid header: unexpected column(s) [is_fraud]. Expected exactly "
                        + String.join(",", ClaimCsv.COLUMNS) + "; file not read")), result.rejected());
    }

    @Test
    void unclosedQuoteStopsReadingAndReportsTheLineWhereTheBrokenRowStarts() {
        LoadResult result = read(
                "C1,M1,P1,Clinic,2024-03-11,OUTPATIENT,J06.9,P-1001,300,false",
                "C2,M1,P1,\"Clinic without closing quote,2024-03-11,OUTPATIENT,J06.9,P-1001,300,false",
                "C3,M1,P1,Clinic,2024-03-12,OUTPATIENT,J06.9,P-1001,300,false");

        assertEquals(List.of("C1"), ids(result), "rows before the broken one are kept");
        assertEquals(1, result.rejected().size());
        RejectedRow broken = result.rejected().get(0);
        assertEquals(3, broken.line());
        assertTrue(broken.reason().startsWith("malformed CSV in the row starting here (parser stopped at line "),
                broken.reason());
    }

    @Test
    void parsedClaimHasEveryFieldTyped() {
        Claim c = read("C1,M1,P1,Clinic,2024-03-11,INPATIENT,K35.8,P-2101|P-1001,30500,false").claims().get(0);
        assertEquals(new Claim("C1", "M1", "P1", "Clinic", LocalDate.of(2024, 3, 11), ClaimType.INPATIENT, "K35.8",
                List.of("P-2101", "P-1001"), 30_500L, false), c);
    }
}
