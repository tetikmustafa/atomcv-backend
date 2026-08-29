package com.mustafatetik.atomcv.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * EK D.6's table and {@link ErrorCode} are one contract kept in two places,
 * and this is the only thing that notices when they stop agreeing (F-017).
 *
 * <p><strong>Why the table is load-bearing and not documentation.</strong> The
 * frontend's own catalogue test reads the parameter names and types out of
 * this table and formats every code's ICU message against them. A code with no
 * row is therefore not merely undocumented: it is a code whose message can be
 * written with the wrong arguments and still pass their test. That is the hole
 * B-043 was found in, and {@code COVER_LETTER_REJECTED} fell straight into it —
 * it shipped, it was described in Bolum 34.4.1, and the table never learned
 * about it.
 *
 * <p>The enum is the authority on both sides of every assertion here. The
 * table is prose and drifts; the enum is compiled and cannot.
 */
class ErrorCatalogueSpecTest {

    /**
     * Relative, because Gradle runs tests from the project directory. A path
     * that stopped resolving would fail {@link #theTableWasActuallyFound}
     * rather than quietly turn every case below into a comparison of two empty
     * maps — a test file the suite cannot find is the purest form of the
     * unverified wiring Bolum 51.7 warns about.
     */
    private static final Path SPEC = Path.of("docs", "spec", "08b-api-contract.md");

    private static final String TABLE_HEADER = "| Kod | HTTP |";

    /** The table's spelling of each {@link ParamType}, as the frontend reads it. */
    private static final Map<String, ParamType> WIRE_TYPES = Map.of(
            "string", ParamType.STRING,
            "integer", ParamType.INTEGER,
            "number", ParamType.NUMBER,
            "boolean", ParamType.BOOLEAN,
            "timestamp", ParamType.TIMESTAMP,
            "uuid", ParamType.UUID_VALUE,
            "string[]", ParamType.STRING_ARRAY);

    @Test
    void theTableWasActuallyFound() throws IOException {
        assertThat(rows())
                .as("EK D.6's table, read from %s", SPEC)
                .hasSizeGreaterThan(30);
    }

    /**
     * The failure this was written for. Five codes were missing when it first
     * ran, and only one of them had been reported.
     */
    @Test
    void everyCodeHasARowInTheTable() throws IOException {
        Map<String, Row> table = rows();

        assertThat(names())
                .as("codes the API can answer with that EK D.6 does not list")
                .allSatisfy(code -> assertThat(table).containsKey(code));
    }

    /** The other direction: a row for a code that no longer exists. */
    @Test
    void everyRowInTheTableIsACodeThatExists() throws IOException {
        assertThat(rows().keySet())
                .as("rows in EK D.6 with nothing behind them")
                .isSubsetOf(names());
    }

    @Test
    void theTableAgreesOnEveryStatus() throws IOException {
        Map<String, Row> table = rows();

        for (ErrorCode code : ErrorCode.values()) {
            Row row = table.get(code.name());
            if (row != null) {
                assertThat(row.status())
                        .as("%s: EK D.6 says %d", code, row.status())
                        .isEqualTo(code.httpStatus());
            }
        }
    }

    /**
     * Names, types <em>and</em> order. Order because the table is read by a
     * person deciding what an ICU message says first, and two parameters
     * listed the other way round is a message that reads backwards.
     */
    @Test
    void theTableAgreesOnEveryParameter() throws IOException {
        Map<String, Row> table = rows();

        for (ErrorCode code : ErrorCode.values()) {
            Row row = table.get(code.name());
            if (row != null) {
                assertThat(row.params())
                        .as("%s: EK D.6's parameters", code)
                        .isEqualTo(code.params());
            }
        }
    }

    // ── reading the table ─────────────────────────────────────────────────

    private record Row(int status, List<ErrorCode.Param> params) { }

    private static List<String> names() {
        List<String> names = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            names.add(code.name());
        }
        return names;
    }

    /**
     * Every row under EK D.6's header, up to the blank line that ends it.
     *
     * <p>LinkedHashMap: the order rows appear in is the order a reader meets
     * them, and a failure message that lists them in a different order every
     * run is harder to read than one that does not.
     */
    private static Map<String, Row> rows() throws IOException {
        List<String> lines = Files.readAllLines(SPEC, StandardCharsets.UTF_8);
        Map<String, Row> rows = new LinkedHashMap<>();

        boolean inside = false;
        for (String line : lines) {
            if (line.startsWith(TABLE_HEADER)) {
                inside = true;
                continue;
            }
            if (!inside) {
                continue;
            }
            if (!line.startsWith("|")) {
                break;
            }
            String[] cells = line.split("\\|", -1);
            // The separator row, and anything that is not a three-column row.
            if (cells.length < 5 || cells[1].strip().startsWith("---")) {
                continue;
            }
            String code = unquote(cells[1]);
            if (!code.matches("[A-Z][A-Z0-9_]*")) {
                continue;
            }
            rows.put(code, new Row(
                    Integer.parseInt(cells[2].strip()), parameters(cells[3])));
        }
        return rows;
    }

    /** {@code —} means the code carries none, and is not a parameter named "—". */
    private static List<ErrorCode.Param> parameters(String cell) {
        String written = cell.strip();
        if (written.isEmpty() || "—".equals(written) || "-".equals(written)) {
            return List.of();
        }

        List<ErrorCode.Param> params = new ArrayList<>();
        for (String each : written.split(",")) {
            String[] halves = unquote(each).split(":", 2);
            String type = halves[1].strip().toLowerCase(Locale.ROOT);
            ParamType resolved = WIRE_TYPES.get(type);
            assertThat(resolved)
                    .as("EK D.6 spells a type this test does not know: '%s'", type)
                    .isNotNull();
            params.add(new ErrorCode.Param(halves[0].strip(), resolved));
        }
        return params;
    }

    private static String unquote(String cell) {
        return cell.strip().replace("`", "").strip();
    }
}
