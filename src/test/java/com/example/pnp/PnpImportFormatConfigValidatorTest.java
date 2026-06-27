package com.example.pnp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PnpImportFormatConfigValidator}.
 */
class PnpImportFormatConfigValidatorTest {

    private final PnpImportFormatConfigValidator validator = new PnpImportFormatConfigValidator();

    @Test
    void validConfigPassesValidation() {
        var config = createValidConfig();
        var errors = validator.validate(config);
        assertTrue(errors.isEmpty(), "Valid config should pass: " + errors);
    }

    @Test
    void validConfigWithoutSidePasses() {
        var config = createValidConfigBuilder()
                .columns(Map.of(
                        "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "RefDes"),
                        "x", new ColumnMapping(ColumnSource.HEADER_NAME, "X"),
                        "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Y"),
                        "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Angle")
                ))
                .build();
        var errors = validator.validate(config);
        assertTrue(errors.isEmpty(), "Config without side should pass: " + errors);
    }

    @Test
    void rejectNullConfig() {
        var errors = validator.validate(null);
        assertFalse(errors.isEmpty());
        assertTrue(errors.getFirst().contains("null"));
    }

    @Test
    void rejectInvalidSchemaVersion() {
        var config = createValidConfigBuilder().schemaVersion(2).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("schemaVersion")));
    }

    @Test
    void rejectConfidenceTooHigh() {
        var config = createValidConfigBuilder().confidence(1.5).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("confidence")));
    }

    @Test
    void rejectConfidenceNegative() {
        var config = createValidConfigBuilder().confidence(-0.5).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("confidence")));
    }

    @Test
    void rejectBlankDelimiter() {
        var config = createValidConfigBuilder().delimiter("").build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("delimiter")));
    }

    @Test
    void rejectNullDelimiter() {
        var config = createValidConfigBuilder().delimiter(null).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("delimiter")));
    }

    @Test
    void rejectInvalidDecimalSeparator() {
        var config = createValidConfigBuilder().decimalSeparator(";").build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("decimalSeparator")));
    }

    @Test
    void rejectNullDecimalSeparator() {
        var config = createValidConfigBuilder().decimalSeparator(null).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("decimalSeparator")));
    }

    @Test
    void rejectNegativeDataStartRowIndex() {
        var config = createValidConfigBuilder().dataStartRowIndex(-1).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("dataStartRowIndex")));
    }

    @Test
    void rejectDataEndRowIndexBeforeDataStart() {
        var config = createValidConfigBuilder()
                .dataStartRowIndex(10)
                .dataEndRowIndex(5)
                .build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("dataEndRowIndex")));
    }

    @Test
    void rejectNegativeHeaderRowIndex() {
        var config = createValidConfigBuilder().headerRowIndex(-1).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("headerRowIndex")));
    }

    @Test
    void rejectMissingXColumn() {
        var columns = Map.of(
                "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "RefDes"),
                "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Y"),
                "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Angle")
        );
        var config = createValidConfigBuilder().columns(columns).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("x") && e.contains("Missing")));
    }

    @Test
    void rejectMissingYColumn() {
        var columns = Map.of(
                "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "RefDes"),
                "x", new ColumnMapping(ColumnSource.HEADER_NAME, "X"),
                "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Angle")
        );
        var config = createValidConfigBuilder().columns(columns).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("y") && e.contains("Missing")));
    }

    @Test
    void rejectMissingAngleColumn() {
        var columns = Map.of(
                "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "RefDes"),
                "x", new ColumnMapping(ColumnSource.HEADER_NAME, "X"),
                "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Y")
        );
        var config = createValidConfigBuilder().columns(columns).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("angle") && e.contains("Missing")));
    }

    @Test
    void rejectNoIdentityColumn() {
        var columns = Map.of(
                "x", new ColumnMapping(ColumnSource.HEADER_NAME, "X"),
                "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Y"),
                "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Angle")
        );
        var config = createValidConfigBuilder().columns(columns).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("identity")));
    }

    @Test
    void rejectEmptyColumns() {
        var config = createValidConfigBuilder().columns(Map.of()).build();
        var errors = validator.validate(config);
        assertFalse(errors.isEmpty());
    }

    @Test
    void rejectNullColumns() {
        var config = createValidConfigBuilder().columns(null).build();
        var errors = validator.validate(config);
        assertFalse(errors.isEmpty());
    }

    @Test
    void rejectAllSameColumnIndex() {
        var columns = Map.of(
                "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "RefDes"),
                "x", new ColumnMapping(ColumnSource.COLUMN_INDEX, "2"),
                "y", new ColumnMapping(ColumnSource.COLUMN_INDEX, "2"),
                "angle", new ColumnMapping(ColumnSource.COLUMN_INDEX, "2")
        );
        var config = createValidConfigBuilder().columns(columns).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("same column")));
    }

    @Test
    void rejectNonNumericColumnIndex() {
        var columns = Map.of(
                "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "RefDes"),
                "x", new ColumnMapping(ColumnSource.COLUMN_INDEX, "abc"),
                "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Y"),
                "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Angle")
        );
        var config = createValidConfigBuilder().columns(columns).build();
        var errors = validator.validate(config);
        assertTrue(errors.stream().anyMatch(e -> e.contains("COLUMN_INDEX") && e.contains("abc")));
    }

    // Helper to build valid configs with overrides
    private static PnpImportFormatConfig createValidConfig() {
        return createValidConfigBuilder().build();
    }

    private static PnpImportFormatConfigBuilder createValidConfigBuilder() {
        return new PnpImportFormatConfigBuilder()
                .schemaVersion(1)
                .confidence(0.85)
                .delimiter(",")
                .quoteChar("\"")
                .encoding("UTF-8")
                .linesToIgnore(List.of(0, 1, 2))
                .headerRowIndex(3)
                .dataStartRowIndex(4)
                .dataEndRowIndex(7)
                .decimalSeparator(".")
                .columns(Map.of(
                        "reference", new ColumnMapping(ColumnSource.HEADER_NAME, "RefDes"),
                        "partNumber", new ColumnMapping(ColumnSource.HEADER_NAME, "PartNo"),
                        "jedec", new ColumnMapping(ColumnSource.HEADER_NAME, "Package"),
                        "x", new ColumnMapping(ColumnSource.HEADER_NAME, "X-Pos"),
                        "y", new ColumnMapping(ColumnSource.HEADER_NAME, "Y-Pos"),
                        "angle", new ColumnMapping(ColumnSource.HEADER_NAME, "Angle"),
                        "side", new ColumnMapping(ColumnSource.HEADER_NAME, "Side")
                ))
                .units(Map.of("x", "mm", "y", "mm", "angle", "deg"))
                .valueMappings(Map.of("side", Map.of("T", "Top", "B", "Bottom")))
                .warnings(List.of());
    }

    /**
     * Builder for PnpImportFormatConfig to make test setup cleaner.
     */
    private static class PnpImportFormatConfigBuilder {
        private int schemaVersion;
        private double confidence;
        private String delimiter;
        private String quoteChar;
        private String encoding;
        private List<Integer> linesToIgnore;
        private Integer headerRowIndex;
        private int dataStartRowIndex;
        private Integer dataEndRowIndex;
        private String decimalSeparator;
        private Map<String, ColumnMapping> columns;
        private Map<String, String> units;
        private Map<String, Map<String, String>> valueMappings;
        private List<String> warnings;

        PnpImportFormatConfigBuilder schemaVersion(int v) { this.schemaVersion = v; return this; }
        PnpImportFormatConfigBuilder confidence(double v) { this.confidence = v; return this; }
        PnpImportFormatConfigBuilder delimiter(String v) { this.delimiter = v; return this; }
        PnpImportFormatConfigBuilder quoteChar(String v) { this.quoteChar = v; return this; }
        PnpImportFormatConfigBuilder encoding(String v) { this.encoding = v; return this; }
        PnpImportFormatConfigBuilder linesToIgnore(List<Integer> v) { this.linesToIgnore = v; return this; }
        PnpImportFormatConfigBuilder headerRowIndex(Integer v) { this.headerRowIndex = v; return this; }
        PnpImportFormatConfigBuilder dataStartRowIndex(int v) { this.dataStartRowIndex = v; return this; }
        PnpImportFormatConfigBuilder dataEndRowIndex(Integer v) { this.dataEndRowIndex = v; return this; }
        PnpImportFormatConfigBuilder decimalSeparator(String v) { this.decimalSeparator = v; return this; }
        PnpImportFormatConfigBuilder columns(Map<String, ColumnMapping> v) { this.columns = v; return this; }
        PnpImportFormatConfigBuilder units(Map<String, String> v) { this.units = v; return this; }
        PnpImportFormatConfigBuilder valueMappings(Map<String, Map<String, String>> v) { this.valueMappings = v; return this; }
        PnpImportFormatConfigBuilder warnings(List<String> v) { this.warnings = v; return this; }

        PnpImportFormatConfig build() {
            return new PnpImportFormatConfig(
                    schemaVersion, confidence, delimiter, quoteChar, encoding,
                    linesToIgnore, headerRowIndex, dataStartRowIndex, dataEndRowIndex,
                    decimalSeparator, columns, units, valueMappings, warnings);
        }
    }
}
