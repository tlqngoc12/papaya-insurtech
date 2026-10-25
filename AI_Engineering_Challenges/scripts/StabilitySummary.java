import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Reads reports/stability/seed-N/metrics.json and writes reports/stability/summary.md: one row per seed, then mean,
 * sample SD (n - 1), min and max of recall, standard FPR, FDR, precision and the lowest per-pattern recall.
 * Deterministic: seeds sorted numerically, LF line endings, UTF-8 without BOM, Locale.US.
 *
 * <p>Run from AI_Engineering_Challenges/ after {@code mvn package}:
 * {@code java -cp target/fraud-engine.jar scripts/StabilitySummary.java reports/stability}
 */
public final class StabilitySummary {

    private record SeedRow(long seed, int threshold, double recall, double fpr, double fdr, double precision,
                           double minPatternRecall, String minPattern) {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "reports/stability");
        List<SeedRow> rows = new ArrayList<>();
        try (Stream<Path> seeds = Files.list(dir)) {
            for (Path seedDir : seeds.filter(p -> p.getFileName().toString().startsWith("seed-")).toList()) {
                rows.add(read(seedDir.resolve("metrics.json")));
            }
        }
        if (rows.size() < 2) {
            throw new IllegalStateException("need at least two seeds under " + dir);
        }
        rows.sort(Comparator.comparingLong(SeedRow::seed));

        StringBuilder md = new StringBuilder();
        line(md, "# Stability across seeds");
        line(md, "");
        line(md, "Computed by `scripts/StabilitySummary.java` from `seed-*/metrics.json`, each produced by `all` with"
                + " `config/frozen-config.json` (T frozen on the tuning seed 42). Percentages; SD is the sample standard"
                + " deviation (n - 1) in percentage points.");
        line(md, "");
        line(md, "| Seed | T | Recall | FPR (standard) | FDR | Precision | Lowest pattern recall |");
        line(md, "|---:|---:|---:|---:|---:|---:|---|");
        for (SeedRow r : rows) {
            line(md, "| " + r.seed() + " | " + r.threshold() + " | " + pct(r.recall()) + " | " + pct(r.fpr()) + " | "
                    + pct(r.fdr()) + " | " + pct(r.precision()) + " | " + pct(r.minPatternRecall()) + " ("
                    + r.minPattern() + ") |");
        }
        line(md, "");
        line(md, "| Metric | Mean | SD (pp) | Min | Max |");
        line(md, "|---|---:|---:|---:|---:|");
        stat(md, "Recall", rows.stream().mapToDouble(SeedRow::recall).toArray());
        stat(md, "FPR (standard)", rows.stream().mapToDouble(SeedRow::fpr).toArray());
        stat(md, "FDR", rows.stream().mapToDouble(SeedRow::fdr).toArray());
        stat(md, "Precision", rows.stream().mapToDouble(SeedRow::precision).toArray());
        stat(md, "Lowest pattern recall", rows.stream().mapToDouble(SeedRow::minPatternRecall).toArray());

        Files.write(dir.resolve("summary.md"), md.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(md);
    }

    private static SeedRow read(Path metricsJson) throws IOException {
        JsonNode doc = new ObjectMapper().readTree(metricsJson.toFile());
        JsonNode c = doc.get("confusion");
        double minRecall = Double.MAX_VALUE;
        String minPattern = "";
        for (JsonNode p : doc.get("recall_by_pattern")) {
            if (p.get("claims").asInt() > 0 && p.get("recall").asDouble() < minRecall) {
                minRecall = p.get("recall").asDouble();
                minPattern = p.get("pattern").asText();
            }
        }
        return new SeedRow(Long.parseLong(doc.get("inputs").get("seed").asText()), doc.get("threshold").asInt(),
                c.get("recall").asDouble(), c.get("fpr_standard").asDouble(), c.get("fdr").asDouble(),
                c.get("precision").asDouble(), minRecall, minPattern);
    }

    private static void stat(StringBuilder md, String name, double[] values) {
        double mean = 0;
        for (double v : values) {
            mean += v;
        }
        mean /= values.length;
        double squares = 0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : values) {
            squares += (v - mean) * (v - mean);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double sd = Math.sqrt(squares / (values.length - 1));
        line(md, "| " + name + " | " + pct(mean) + " | " + String.format(Locale.US, "%.2f", sd * 100) + " | " + pct(min)
                + " | " + pct(max) + " |");
    }

    private static String pct(double value) {
        return String.format(Locale.US, "%.1f%%", value * 100);
    }

    private static void line(StringBuilder md, String text) {
        md.append(text).append('\n');
    }
}
