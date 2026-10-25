package com.papaya.fraud.io;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.io.LoadResult.RejectedRow;
import com.papaya.fraud.io.LoadResult.Warning;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.ClaimType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code claims.csv} and validates it (R11). Accepts a UTF-8 BOM and CRLF line endings, as written by Excel.
 * Rejected: wrong column count, empty required value, unparseable date/type/amount, amount &lt;= 0, empty or blank
 * procedure codes, duplicate claim_id (the first occurrence is kept), wrong header (nothing is read). Kept with
 * a warning: is_weekend that disagrees with claim_date (the recomputed value is used), codes missing from the
 * catalog.
 */
public final class ClaimCsvReader {

    private static final char BOM = '\uFEFF';
    private static final CsvMapper MAPPER = new CsvMapper().enable(CsvParser.Feature.WRAP_AS_ARRAY);

    private final Catalog catalog;

    public ClaimCsvReader(Catalog catalog) {
        this.catalog = catalog;
    }

    public LoadResult read(Path file) {
        try {
            return read(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    public LoadResult read(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == BOM) {
            text = text.substring(1);
        }
        List<Claim> claims = new ArrayList<>();
        List<RejectedRow> rejected = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        Map<String, Long> firstLineById = new HashMap<>();

        long rowStart = 1;
        try (MappingIterator<String[]> rows = MAPPER.readerFor(String[].class)
                .with(CsvParser.Feature.SKIP_EMPTY_LINES)
                .readValues(text)) {
            boolean header = true;
            while (true) {
                rowStart = rows.getCurrentLocation().getLineNr();
                if (!rows.hasNextValue()) {
                    break;
                }
                String[] row = rows.nextValue();
                if (header) {
                    header = false;
                    String problem = headerProblem(row);
                    if (problem != null) {
                        rejected.add(new RejectedRow(rowStart, null, problem + "; file not read"));
                        break;
                    }
                    continue;
                }
                parseRow(rowStart, row, firstLineById, claims, rejected, warnings);
            }
        } catch (IOException | RuntimeException e) {
            // MappingIterator wraps parse errors in RuntimeJsonMappingException. The parser only notices an
            // unclosed quote at the end of input, so the useful line is where the broken row started.
            Throwable cause = e.getCause() instanceof JsonProcessingException ? e.getCause() : e;
            String detectedAt = cause instanceof JsonProcessingException jpe && jpe.getLocation() != null
                    ? " (parser stopped at line " + jpe.getLocation().getLineNr() + ")"
                    : "";
            String message = cause instanceof JsonProcessingException jpe ? jpe.getOriginalMessage() : e.getMessage();
            rejected.add(new RejectedRow(rowStart, null,
                    "malformed CSV in the row starting here" + detectedAt + ", reading stopped: " + message));
        }
        return new LoadResult(claims, rejected, warnings);
    }

    /** Null when the header is exactly the ten columns in order; otherwise what is wrong with it. */
    private static String headerProblem(String[] header) {
        List<String> actual = Arrays.asList(header);
        if (actual.equals(ClaimCsv.COLUMNS)) {
            return null;
        }
        List<String> unexpected = actual.stream().filter(c -> !ClaimCsv.COLUMNS.contains(c)).toList();
        List<String> missing = ClaimCsv.COLUMNS.stream().filter(c -> !actual.contains(c)).toList();
        StringBuilder problem = new StringBuilder("invalid header");
        if (!unexpected.isEmpty()) {
            problem.append(": unexpected column(s) ").append(unexpected);
        }
        if (!missing.isEmpty()) {
            problem.append(unexpected.isEmpty() ? ": " : ", ").append("missing column(s) ").append(missing);
        }
        if (unexpected.isEmpty() && missing.isEmpty()) {
            problem.append(": columns out of order");
        }
        return problem.append(". Expected exactly ").append(String.join(",", ClaimCsv.COLUMNS)).toString();
    }

    private void parseRow(long line, String[] row, Map<String, Long> firstLineById, List<Claim> claims,
                          List<RejectedRow> rejected, List<Warning> warnings) {
        String id = row.length > 0 ? row[0] : null;
        if (row.length != ClaimCsv.COLUMNS.size()) {
            rejected.add(new RejectedRow(line, id,
                    "expected " + ClaimCsv.COLUMNS.size() + " columns, found " + row.length));
            return;
        }
        for (int i : new int[] {0, 1, 2, 4, 5, 6, 8}) {
            if (row[i].isBlank()) {
                rejected.add(new RejectedRow(line, id, ClaimCsv.COLUMNS.get(i) + " is empty"));
                return;
            }
        }
        LocalDate date;
        try {
            date = LocalDate.parse(row[4]);
        } catch (DateTimeParseException e) {
            rejected.add(new RejectedRow(line, id, "claim_date '" + row[4] + "' is not an ISO date (yyyy-MM-dd)"));
            return;
        }
        ClaimType type;
        try {
            type = ClaimType.valueOf(row[5]);
        } catch (IllegalArgumentException e) {
            rejected.add(new RejectedRow(line, id, "claim_type '" + row[5] + "' is not one of "
                    + Arrays.toString(ClaimType.values())));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(row[8]);
        } catch (NumberFormatException e) {
            rejected.add(new RejectedRow(line, id, "submitted_amount '" + row[8] + "' is not an integer"));
            return;
        }
        if (amount <= 0) {
            rejected.add(new RejectedRow(line, id, "submitted_amount " + amount + " must be > 0"));
            return;
        }
        if (row[7].isBlank()) {
            rejected.add(new RejectedRow(line, id, "procedure_codes is empty"));
            return;
        }
        List<String> procedures = Arrays.asList(row[7].split("\\|", -1));
        if (procedures.stream().anyMatch(String::isBlank)) {
            rejected.add(new RejectedRow(line, id, "procedure_codes '" + row[7] + "' contains an empty code"));
            return;
        }
        Long firstLine = firstLineById.putIfAbsent(id, line);
        if (firstLine != null) {
            rejected.add(new RejectedRow(line, id, "duplicate claim_id, first seen on line " + firstLine));
            return;
        }

        boolean weekend = ClaimCsv.isWeekend(date);
        String declared = row[9];
        // Case-insensitive: Excel saves booleans as TRUE/FALSE.
        if (!declared.equalsIgnoreCase(Boolean.toString(weekend))) {
            warnings.add(new Warning(line, id, "is_weekend '" + declared + "' disagrees with claim_date " + date
                    + " (" + date.getDayOfWeek() + "); using " + weekend));
        }
        if (catalog.diagnosis(row[6]).isEmpty()) {
            warnings.add(new Warning(line, id, "diagnosis_code " + row[6] + " is not in the catalog"));
        }
        for (String code : procedures) {
            if (catalog.procedure(code).isEmpty()) {
                warnings.add(new Warning(line, id, "procedure_code " + code + " is not in the catalog"));
            }
        }
        claims.add(new Claim(id, row[1], row[2], row[3], date, type, row[6], procedures, amount, weekend));
    }
}
