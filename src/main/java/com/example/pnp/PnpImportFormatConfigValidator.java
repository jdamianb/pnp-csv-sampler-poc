package com.example.pnp;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a {@link PnpImportFormatConfig} against the expected rules.
 * <p>
 * Returns a list of error messages. An empty list means the config is valid.
 */
public class PnpImportFormatConfigValidator {

    private static final List<String> IDENTITY_FIELDS = List.of("reference", "partNumber", "jedec");
    private static final List<String> REQUIRED_FIELDS = List.of("x", "y", "angle");

    /**
     * Validate the given config.
     *
     * @param config the config to validate
     * @return list of error messages (empty = valid)
     */
    public List<String> validate(PnpImportFormatConfig config) {
        var errors = new ArrayList<String>();

        if (config == null) {
            errors.add("Config is null");
            return errors;
        }

        // schemaVersion
        if (config.schemaVersion() != 1) {
            errors.add("schemaVersion must be 1, got " + config.schemaVersion());
        }

        // confidence
        if (config.confidence() < 0.0 || config.confidence() > 1.0) {
            errors.add("confidence must be between 0.0 and 1.0, got " + config.confidence());
        }

        // delimiter
        if (config.delimiter() == null || config.delimiter().isBlank()) {
            errors.add("delimiter must not be blank");
        }

        // decimalSeparator
        if (config.decimalSeparator() == null
                || (!config.decimalSeparator().equals(".") && !config.decimalSeparator().equals(","))) {
            errors.add("decimalSeparator must be '.' or ',', got '"
                    + config.decimalSeparator() + "'");
        }

        // dataStartRowIndex
        if (config.dataStartRowIndex() < 0) {
            errors.add("dataStartRowIndex must be >= 0, got " + config.dataStartRowIndex());
        }

        // dataEndRowIndex
        if (config.dataEndRowIndex() != null
                && config.dataEndRowIndex() < config.dataStartRowIndex()) {
            errors.add("dataEndRowIndex (" + config.dataEndRowIndex()
                    + ") must be >= dataStartRowIndex (" + config.dataStartRowIndex() + ")");
        }

        // headerRowIndex
        if (config.headerRowIndex() != null && config.headerRowIndex() < 0) {
            errors.add("headerRowIndex must be >= 0, got " + config.headerRowIndex());
        }

        // Column validation
        var columns = config.columns();
        if (columns == null || columns.isEmpty()) {
            errors.add("columns must not be empty");
            return errors;
        }

        // Required fields: x, y, angle
        for (var field : REQUIRED_FIELDS) {
            if (!columns.containsKey(field)) {
                errors.add("Missing required column: " + field);
            }
        }

        // At least one identity column
        boolean hasIdentity = IDENTITY_FIELDS.stream().anyMatch(columns::containsKey);
        if (!hasIdentity) {
            errors.add("At least one identity column (reference, partNumber, jedec) is required");
        }

        // Check if x, y, angle all point to the same COLUMN_INDEX
        if (columns.containsKey("x") && columns.containsKey("y") && columns.containsKey("angle")) {
            var xCol = columns.get("x");
            var yCol = columns.get("y");
            var angleCol = columns.get("angle");
            if (xCol.source() == ColumnSource.COLUMN_INDEX
                    && yCol.source() == ColumnSource.COLUMN_INDEX
                    && angleCol.source() == ColumnSource.COLUMN_INDEX
                    && xCol.value().equals(yCol.value())
                    && yCol.value().equals(angleCol.value())) {
                errors.add("x, y, and angle must not all point to the same column index: "
                        + xCol.value());
            }
        }

        // COLUMN_INDEX values must be valid integers
        if (columns != null) {
            for (var entry : columns.entrySet()) {
                var mapping = entry.getValue();
                if (mapping.source() == ColumnSource.COLUMN_INDEX) {
                    try {
                        Integer.parseInt(mapping.value());
                    } catch (NumberFormatException e) {
                        errors.add("COLUMN_INDEX value for '" + entry.getKey()
                                + "' is not a valid integer: " + mapping.value());
                    }
                }
            }
        }

        return errors;
    }
}
