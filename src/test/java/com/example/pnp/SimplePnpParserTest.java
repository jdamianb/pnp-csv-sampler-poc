package com.example.pnp;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SimplePnpParser}.
 * <p>
 * All tests use synthetic examples and are fully deterministic (no network).
 */
class SimplePnpParserTest {

    private final SimplePnpParser parser = new SimplePnpParser();

    // ============================================================
    //  Comma-delimited with HEADER_NAME
    // ============================================================

    @Test
    void commaDelimitedWithHeader() {
        var input = """
                Designator,Comment,Footprint,Mid X,Mid Y,Rotation,Layer
                C1,100nF,C_0603,95.0518,22.6822,270,Top
                R1,10k,R_0603,106.4056,23.0124,90,Top
                """;

        var config = createCommaConfig();

        var report = parser.parse(new StringReader(input), config);

        assertTrue(report.success());
        assertEquals(2, report.parsedRows());
        assertEquals(0, report.rejectedRows());
        assertEquals(2, report.samplePlacements().size());
        assertEquals("C1", report.samplePlacements().get(0).reference());
        assertEquals(95.0518, report.samplePlacements().get(0).x(), 0.0001);
        assertEquals(22.6822, report.samplePlacements().get(0).y(), 0.0001);
        assertEquals(270.0, report.samplePlacements().get(0).angle(), 0.0001);
        assertEquals("Top", report.samplePlacements().get(0).side());
    }

    // ============================================================
    //  Semicolon-delimited with decimal comma
    // ============================================================

    @Test
    void semicolonDelimitedWithDecimalComma() {
        var input = """
                Designator;Comment;Footprint;Mid X;Mid Y;Rotation;Layer
                R1;10k;0603;12,5;33,1;90;Top
                C1;100n;0603;14,8;31,9;180;Top
                """;

        var config = new PnpImportFormatConfig(
                1, 0.9, ";", null, "UTF-8",
                List.of(), 0, 1, null, ",",
                Map.of(
                        "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "Designator"),
                        "partNumber", new ColumnMapping(ColumnSource.HEADER_NAME, "Comment"),
                        "jedec", new ColumnMapping(ColumnSource.HEADER_NAME, "Footprint"),
                        "x", new ColumnMapping(ColumnSource.HEADER_NAME, "Mid X"),
                        "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Mid Y"),
                        "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Rotation"),
                        "side", new ColumnMapping(ColumnSource.HEADER_NAME, "Layer")
                ),
                null, Map.of("side", Map.of("Top", "Top", "Bottom", "Bottom")),
                List.of());

        var report = parser.parse(new StringReader(input), config);

        assertTrue(report.success());
        assertEquals(2, report.parsedRows());
        assertEquals(12.5, report.samplePlacements().get(0).x(), 0.0001);
        assertEquals(33.1, report.samplePlacements().get(0).y(), 0.0001);
    }

    // ============================================================
    //  Tab-delimited
    // ============================================================

    @Test
    void tabDelimited() {
        var input = "Designator\tComment\tFootprint\tMid X\tMid Y\tRotation\tLayer\n"
                + "R1\t10k\t0603\t12.5\t33.1\t90\tTop\n"
                + "C1\t100n\t0603\t14.8\t31.9\t180\tTop\n";

        var config = new PnpImportFormatConfig(
                1, 0.9, "\\t", null, "UTF-8",
                List.of(), 0, 1, null, ".",
                Map.of(
                        "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "Designator"),
                        "x", new ColumnMapping(ColumnSource.HEADER_NAME, "Mid X"),
                        "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Mid Y"),
                        "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Rotation")
                ),
                null, null, List.of());

        var report = parser.parse(new StringReader(input), config);
        assertTrue(report.success());
        assertEquals(2, report.parsedRows());
        assertEquals(12.5, report.samplePlacements().get(0).x(), 0.0001);
    }

    // ============================================================
    //  Whitespace-delimited (KiCad-like)
    // ============================================================

    @Test
    void whitespaceDelimited() {
        var input = """
                # Ref Val Package PosX PosY Rot Side
                C1 100n C_0603 95.0518 22.6822 270.0 top
                R1 10k R_0603 106.4056 23.0124 90.0 top
                """;

        var config = new PnpImportFormatConfig(
                1, 0.9, "WHITESPACE", null, "UTF-8",
                List.of(), 0, 1, 2, ".",
                Map.of(
                        "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "Ref"),
                        "partNumber", new ColumnMapping(ColumnSource.HEADER_NAME, "Val"),
                        "jedec", new ColumnMapping(ColumnSource.HEADER_NAME, "Package"),
                        "x", new ColumnMapping(ColumnSource.HEADER_NAME, "PosX"),
                        "y", new ColumnMapping(ColumnSource.HEADER_NAME, "PosY"),
                        "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Rot"),
                        "side", new ColumnMapping(ColumnSource.HEADER_NAME, "Side")
                ),
                null, Map.of("side", Map.of("top", "Top", "bottom", "Bottom")),
                List.of());

        var report = parser.parse(new StringReader(input), config);

        assertTrue(report.success());
        assertEquals(2, report.parsedRows());
        assertEquals("C1", report.samplePlacements().get(0).reference());
        assertEquals("100n", report.samplePlacements().get(0).partNumber());
        assertEquals(95.0518, report.samplePlacements().get(0).x(), 0.0001);
        assertEquals("Top", report.samplePlacements().get(0).side());
    }

    // ============================================================
    //  COLUMN_INDEX (no header)
    // ============================================================

    @Test
    void columnIndexMappings() {
        var input = """
                R1,10k,0603,12.5,33.1,90,Top
                C1,100n,0603,14.8,31.9,180,Top
                """;

        var config = new PnpImportFormatConfig(
                1, 0.9, ",", null, "UTF-8",
                List.of(), null, 0, null, ".",
                Map.of(
                        "reference", new ColumnMapping(ColumnSource.COLUMN_INDEX, "0"),
                        "partNumber", new ColumnMapping(ColumnSource.COLUMN_INDEX, "1"),
                        "x", new ColumnMapping(ColumnSource.COLUMN_INDEX, "3"),
                        "y", new ColumnMapping(ColumnSource.COLUMN_INDEX, "4"),
                        "angle", new ColumnMapping(ColumnSource.COLUMN_INDEX, "5")
                ),
                null, null, List.of());

        var report = parser.parse(new StringReader(input), config);

        assertTrue(report.success());
        assertEquals(2, report.parsedRows());
        assertEquals("R1", report.samplePlacements().get(0).reference());
        assertEquals(12.5, report.samplePlacements().get(0).x(), 0.0001);
    }

    // ============================================================
    //  Non-numeric X value → row-level error
    // ============================================================

    @Test
    void nonNumericXProducesError() {
        var input = """
                Designator,Comment,Footprint,Mid X,Mid Y,Rotation,Layer
                C1,100nF,C_0603,abc,22.6822,270,Top
                """;

        var config = createCommaConfig();
        var report = parser.parse(new StringReader(input), config);

        assertFalse(report.success());
        assertEquals(0, report.parsedRows());
        assertEquals(1, report.rejectedRows());
        assertTrue(report.errors().get(0).contains("X"));
    }

    // ============================================================
    //  Non-numeric angle → row-level error
    // ============================================================

    @Test
    void nonNumericAngleProducesError() {
        var input = """
                Designator,Comment,Footprint,Mid X,Mid Y,Rotation,Layer
                C1,100nF,C_0603,95.0518,22.6822,abc,Top
                """;

        var config = createCommaConfig();
        var report = parser.parse(new StringReader(input), config);

        assertFalse(report.success());
        assertTrue(report.errors().get(0).contains("angle"));
    }

    // ============================================================
    //  Empty file
    // ============================================================

    @Test
    void emptyFile() {
        var config = createCommaConfig();
        var report = parser.parse(new StringReader(""), config);

        assertTrue(report.success());
        assertEquals(0, report.parsedRows());
        assertEquals(1, report.warnings().size());
    }

    // ============================================================
    //  LinesToIgnore
    // ============================================================

    @Test
    void linesToIgnore() {
        var input = """
                # Comment line 1
                # Comment line 2
                Designator,Comment,Footprint,Mid X,Mid Y,Rotation,Layer
                C1,100nF,C_0603,95.0518,22.6822,270,Top
                """;

        var config = new PnpImportFormatConfig(
                1, 0.9, ",", null, "UTF-8",
                List.of(0, 1), 2, 3, null, ".",
                createColumnMappings(true),
                null, null, List.of());

        var report = parser.parse(new StringReader(input), config);
        assertTrue(report.success());
        assertEquals(1, report.parsedRows());
    }

    // ============================================================
    //  DataStartRowIndex and DataEndRowIndex
    // ============================================================

    @Test
    void dataBounds() {
        var input = """
                Ref,X,Y,Angle
                R1,1.0,2.0,90
                R2,3.0,4.0,180
                R3,5.0,6.0,270
                # Footer
                """;

        var config = new PnpImportFormatConfig(
                1, 0.9, ",", null, "UTF-8",
                List.of(), 0, 1, 2, ".",
                Map.of(
                        "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "Ref"),
                        "x", new ColumnMapping(ColumnSource.HEADER_NAME, "X"),
                        "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Y"),
                        "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Angle")
                ),
                null, null, List.of());

        var report = parser.parse(new StringReader(input), config);
        assertTrue(report.success());
        assertEquals(2, report.parsedRows()); // R1, R2 only (R3 excluded by dataEndRowIndex=2)
    }

    // ============================================================
    //  Unit suffix stripping
    // ============================================================

    @Test
    void unitSuffixStripping() {
        var input = """
                Ref,X,Y,Angle
                C1,95.0518mm,22.6822mm,270deg
                """;

        var config = new PnpImportFormatConfig(
                1, 0.9, ",", null, "UTF-8",
                List.of(), 0, 1, null, ".",
                Map.of(
                        "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "Ref"),
                        "x", new ColumnMapping(ColumnSource.HEADER_NAME, "X"),
                        "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Y"),
                        "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Angle")
                ),
                null, null, List.of());

        var report = parser.parse(new StringReader(input), config);
        assertTrue(report.success());
        assertEquals(95.0518, report.samplePlacements().get(0).x(), 0.0001);
        assertEquals(270.0, report.samplePlacements().get(0).angle(), 0.0001);
    }

    // ============================================================
    //  Side value mapping
    // ============================================================

    @Test
    void sideValueMapping() {
        var input = """
                Ref,X,Y,Angle,Layer
                C1,1.0,2.0,0,top
                R1,3.0,4.0,180,BOTTOM
                """;

        var config = new PnpImportFormatConfig(
                1, 0.9, ",", null, "UTF-8",
                List.of(), 0, 1, null, ".",
                Map.of(
                        "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "Ref"),
                        "x", new ColumnMapping(ColumnSource.HEADER_NAME, "X"),
                        "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Y"),
                        "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Angle"),
                        "side", new ColumnMapping(ColumnSource.HEADER_NAME, "Layer")
                ),
                null, Map.of("side", Map.of("top", "Top", "BOTTOM", "Bottom")),
                List.of());

        var report = parser.parse(new StringReader(input), config);
        assertTrue(report.success());
        assertEquals("Top", report.samplePlacements().get(0).side());
        assertEquals("Bottom", report.samplePlacements().get(1).side());
    }

    // ============================================================
    //  Unrecognized delimiter
    // ============================================================

    @Test
    void unrecognizedDelimiter() {
        var input = "R1|1.0|2.0|90\n";
        var config = new PnpImportFormatConfig(
                1, 0.9, "|", null, "UTF-8",
                List.of(), null, 0, null, ".",
                Map.of(
                        "reference", new ColumnMapping(ColumnSource.COLUMN_INDEX, "0"),
                        "x", new ColumnMapping(ColumnSource.COLUMN_INDEX, "1"),
                        "y", new ColumnMapping(ColumnSource.COLUMN_INDEX, "2"),
                        "angle", new ColumnMapping(ColumnSource.COLUMN_INDEX, "3")
                ),
                null, null, List.of());

        // Should still parse — unknown delimiter is treated as literal character
        var report = parser.parse(new StringReader(input), config);
        assertTrue(report.success());
        assertEquals(1, report.parsedRows());
    }

    // ============================================================
    //  Report includes available columns
    // ============================================================

    @Test
    void reportIncludesAvailableColumns() {
        var input = "Designator,Comment,Mid X,Mid Y,Rotation,Layer\nR1,10k,1.0,2.0,90,Top\n";
        var config = new PnpImportFormatConfig(
                1, 0.9, ",", null, "UTF-8",
                List.of(), 0, 1, null, ".",
                Map.of(
                        "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "Designator"),
                        "x", new ColumnMapping(ColumnSource.HEADER_NAME, "Mid X"),
                        "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Mid Y"),
                        "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Rotation")
                ),
                null, null, List.of());
        var report = parser.parse(new StringReader(input), config);

        assertTrue(report.availableColumns().contains("Designator"));
        assertTrue(report.availableColumns().contains("Comment"));
    }

    // ============================================================
    //  Sample placements limited to 10
    // ============================================================

    @Test
    void samplePlacementsLimited() {
        var sb = new StringBuilder("Designator,Comment,Footprint,Mid X,Mid Y,Rotation,Layer\n");
        for (int i = 0; i < 15; i++) {
            sb.append("C").append(i).append(",val,pkg,1.0,2.0,0,Top\n");
        }

        var config = createCommaConfig();
        var report = parser.parse(new StringReader(sb.toString()), config);

        assertEquals(15, report.parsedRows());
        assertTrue(report.samplePlacements().size() <= 10);
    }

    // ============================================================
    //  No matching header column
    // ============================================================

    @Test
    void headerColumnNotFound() {
        var input = "Something,Else\nR1,10k\n";
        var config = new PnpImportFormatConfig(
                1, 0.9, ",", null, "UTF-8",
                List.of(), 0, 1, null, ".",
                Map.of("reference", new ColumnMapping(ColumnSource.HEADER_NAME, "Designator")),
                null, null, List.of());

        var report = parser.parse(new StringReader(input), config);
        assertFalse(report.success());
        assertTrue(report.errors().get(0).contains("not found"));
    }

    // ============================================================
    //  parseDouble helper tests
    // ============================================================

    @Test
    void parseDoubleNormal() {
        assertEquals(12.345, SimplePnpParser.parseDouble("12.345", "."), 0.0001);
    }

    @Test
    void parseDoubleCommaDecimal() {
        assertEquals(12.345, SimplePnpParser.parseDouble("12,345", ","), 0.0001);
    }

    @Test
    void parseDoubleWithUnitSuffix() {
        assertEquals(12.3, SimplePnpParser.parseDouble("12.3mm", "."), 0.0001);
        assertEquals(45.0, SimplePnpParser.parseDouble("45.0deg", "."), 0.0001);
    }

    @Test
    void parseDoubleEmptyThrows() {
        assertThrows(NumberFormatException.class,
                () -> SimplePnpParser.parseDouble("", "."));
    }

    // ============================================================
    //  splitLine helper tests
    // ============================================================

    @Test
    void splitLineComma() {
        var fields = SimplePnpParser.splitLine("a,b,c", ",");
        assertEquals(List.of("a", "b", "c"), fields);
    }

    @Test
    void splitLineWhitespace() {
        var fields = SimplePnpParser.splitLine("a   b  c", "WHITESPACE");
        assertEquals(List.of("a", "b", "c"), fields);
    }

    @Test
    void splitLineQuoted() {
        var fields = SimplePnpParser.splitLine("a,\"b,c\",d", ",");
        assertEquals(List.of("a", "b,c", "d"), fields);
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private static PnpImportFormatConfig createCommaConfig() {
        return new PnpImportFormatConfig(
                1, 0.9, ",", null, "UTF-8",
                List.of(), 0, 1, null, ".",
                createColumnMappings(true),
                null, Map.of("side", Map.of("Top", "Top", "Bottom", "Bottom")),
                List.of());
    }

    private static Map<String, ColumnMapping> createColumnMappings(boolean withSide) {
        var mappings = new java.util.LinkedHashMap<String, ColumnMapping>();
        mappings.put("reference", new ColumnMapping(ColumnSource.HEADER_NAME, "Designator"));
        mappings.put("partNumber", new ColumnMapping(ColumnSource.HEADER_NAME, "Comment"));
        mappings.put("jedec", new ColumnMapping(ColumnSource.HEADER_NAME, "Footprint"));
        mappings.put("x", new ColumnMapping(ColumnSource.HEADER_NAME, "Mid X"));
        mappings.put("y", new ColumnMapping(ColumnSource.HEADER_NAME, "Mid Y"));
        mappings.put("angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Rotation"));
        if (withSide) {
            mappings.put("side", new ColumnMapping(ColumnSource.HEADER_NAME, "Layer"));
        }
        return mappings;
    }
}
