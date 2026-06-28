package com.example.pnp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple, original, deterministic parser for PnP CSV/TSV-like files.
 * <p>
 * Consumes a {@link PnpImportFormatConfig} and an input file,
 * applies the config rules, and produces a {@link PnpParseDryRunReport}.
 * <p>
 * This is a PoC parser, not production-grade.
 * <p>
 * Supported delimiters: comma, semicolon, tab, WHITESPACE (one or more spaces/tabs).
 * Supported mapping sources: HEADER_NAME, COLUMN_INDEX.
 * Supported decimal separators: ".", ",".
 * Supports stripping common unit suffixes (mm, mil, inch, deg).
 * Supports side value mappings.
 */
public class SimplePnpParser {

    private static final int MAX_SAMPLE_PLACEMENTS = 10;

    /**
     * Parse a PnP file according to the given configuration.
     * <p>
     * All row indexes (headerRowIndex, dataStartRowIndex, dataEndRowIndex,
     * linesToIgnore) use zero-based original line indexes from the input file.
     */
    public PnpParseDryRunReport parse(Reader input, PnpImportFormatConfig config) {
        var lines = readAllLines(input);
        var errors = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        var placements = new ArrayList<PnpPlacement>();

        if (lines.isEmpty()) {
            warnings.add("Input file is empty");
            return new PnpParseDryRunReport(true, 0, 0, 0,
                    List.of(), warnings, List.of(), List.of());
        }

        // Build set of ignored line indexes
        var ignoreSet = config.linesToIgnore() != null
                ? Set.copyOf(config.linesToIgnore())
                : Set.<Integer>of();

        // 1. Resolve column indexes using original line indexes
        Map<String, Integer> resolvedColumns;
        List<String> availableColumns = List.of();
        try {
            resolvedColumns = resolveColumnIndexes(lines, config);
        } catch (IllegalArgumentException e) {
            errors.add("Failed to resolve columns: " + e.getMessage());
            return new PnpParseDryRunReport(false, 0, 0, 0,
                    errors, warnings, List.of(), List.of());
        }

        // Extract available column names from header row if present
        if (config.headerRowIndex() != null && config.headerRowIndex() >= 0
                && config.headerRowIndex() < lines.size()) {
            var headerLine = lines.get(config.headerRowIndex());
            availableColumns = splitLine(headerLine, config.delimiter());
        }

        // 2. Determine data row range using original line indexes
        int totalLines = lines.size();
        int dataStart = config.dataStartRowIndex();
        if (dataStart < 0 || dataStart >= totalLines) {
            errors.add("dataStartRowIndex " + dataStart + " is out of bounds (0.."
                    + (totalLines - 1) + ")");
            return new PnpParseDryRunReport(false, 0, 0, 0,
                    errors, warnings, availableColumns, List.of());
        }

        int dataEnd = config.dataEndRowIndex() != null
                ? Math.min(config.dataEndRowIndex(), totalLines - 1)
                : totalLines - 1;

        if (dataEnd < dataStart) {
            errors.add("dataEndRowIndex " + config.dataEndRowIndex()
                    + " is before dataStartRowIndex " + dataStart);
            return new PnpParseDryRunReport(false, 0, 0, 0,
                    errors, warnings, availableColumns, List.of());
        }

        // 3. Iterate data range using original indexes, skipping ignored lines
        int totalCandidateRows = 0;
        int parsedRows = 0;
        int rejectedRows = 0;

        for (int originalIndex = dataStart; originalIndex <= dataEnd; originalIndex++) {
            // Skip ignored lines
            if (ignoreSet.contains(originalIndex)) {
                continue;
            }

            totalCandidateRows++;

            var line = lines.get(originalIndex);
            var fields = splitLine(line, config.delimiter());

            // For WHITESPACE, trim empty leading/trailing fields
            if ("WHITESPACE".equalsIgnoreCase(config.delimiter())) {
                fields = trimWhitespaceFields(fields);
            }

            String reference = null;
            String partNumber = null;
            String jedec = null;
            Double x = null;
            Double y = null;
            Double angle = null;
            String side = null;

            var rowErrors = new ArrayList<String>();

            // Extract each mapped field
            for (var entry : resolvedColumns.entrySet()) {
                var fieldName = entry.getKey();
                var colIndex = entry.getValue();

                if (colIndex < 0 || colIndex >= fields.size()) {
                    rowErrors.add("Row " + originalIndex + ": column index "
                            + colIndex + " for '" + fieldName + "' is out of range (fields: "
                            + fields.size() + ")");
                    continue;
                }

                var rawValue = fields.get(colIndex).trim();

                switch (fieldName) {
                    case "reference" -> reference = rawValue.isEmpty() ? null : rawValue;
                    case "partNumber" -> partNumber = rawValue.isEmpty() ? null : rawValue;
                    case "jedec" -> jedec = rawValue.isEmpty() ? null : rawValue;
                    case "x" -> {
                        try {
                            x = parseDouble(rawValue, config.decimalSeparator());
                        } catch (NumberFormatException e) {
                            rowErrors.add("Row " + originalIndex
                                    + ": X value '" + rawValue + "' is not a valid number");
                        }
                    }
                    case "y" -> {
                        try {
                            y = parseDouble(rawValue, config.decimalSeparator());
                        } catch (NumberFormatException e) {
                            rowErrors.add("Row " + originalIndex
                                    + ": Y value '" + rawValue + "' is not a valid number");
                        }
                    }
                    case "angle" -> {
                        try {
                            angle = parseDouble(rawValue, config.decimalSeparator());
                        } catch (NumberFormatException e) {
                            rowErrors.add("Row " + originalIndex
                                    + ": angle value '" + rawValue + "' is not a valid number");
                        }
                    }
                    case "side" -> {
                        side = applySideMapping(rawValue, config.valueMappings());
                    }
                }
            }

            // Validate required numeric fields
            if (x == null) {
                rowErrors.add("Row " + originalIndex + ": X is missing or invalid");
            }
            if (y == null) {
                rowErrors.add("Row " + originalIndex + ": Y is missing or invalid");
            }
            if (angle == null) {
                rowErrors.add("Row " + originalIndex + ": angle is missing or invalid");
            }

            if (rowErrors.isEmpty()) {
                placements.add(new PnpPlacement(
                        reference, partNumber, jedec,
                        x, y, angle, side));
                parsedRows++;
            } else {
                errors.addAll(rowErrors);
                rejectedRows++;
            }
        }

        // 4. Build report
        var samplePlacements = placements.size() <= MAX_SAMPLE_PLACEMENTS
                ? placements
                : placements.subList(0, MAX_SAMPLE_PLACEMENTS);

        boolean success = errors.isEmpty();

        return new PnpParseDryRunReport(
                success,
                totalCandidateRows,
                parsedRows,
                rejectedRows,
                errors,
                warnings,
                availableColumns,
                samplePlacements);
    }

    /**
     * Read all lines from the reader into a list.
     */
    private List<String> readAllLines(Reader input) {
        var lines = new ArrayList<String>();
        try (var reader = new BufferedReader(input)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input: " + e.getMessage(), e);
        }
        return lines;
    }

    /**
     * Resolve column indexes from the config.
     * For HEADER_NAME mappings, reads the header row to find column index.
     * For COLUMN_INDEX mappings, uses the value directly.
     */
    private Map<String, Integer> resolveColumnIndexes(
            List<String> lines, PnpImportFormatConfig config) {

        var resolved = new LinkedHashMap<String, Integer>();
        var columns = config.columns();

        // Parse header row if needed
        List<String> headerFields = null;
        boolean needsHeader = columns.values().stream()
                .anyMatch(m -> m.source() == ColumnSource.HEADER_NAME);

        if (needsHeader) {
            if (config.headerRowIndex() == null || config.headerRowIndex() < 0) {
                throw new IllegalArgumentException(
                        "HEADER_NAME mappings require a headerRowIndex, but none was set");
            }
            if (config.headerRowIndex() >= lines.size()) {
                throw new IllegalArgumentException(
                        "headerRowIndex " + config.headerRowIndex()
                                + " is out of bounds (file has " + lines.size() + " lines)");
            }
            var headerLine = lines.get(config.headerRowIndex());
            headerFields = splitLine(headerLine, config.delimiter());
            // For WHITESPACE-delimited files, strip comment prefix (#) from first header field
            if ("WHITESPACE".equalsIgnoreCase(config.delimiter())
                    && !headerFields.isEmpty()
                    && headerFields.get(0).startsWith("#")) {
                var cleaned = headerFields.get(0).substring(1).trim();
                if (cleaned.isEmpty()) {
                    headerFields = headerFields.subList(1, headerFields.size());
                } else {
                    var modified = new ArrayList<>(headerFields);
                    modified.set(0, cleaned);
                    headerFields = modified;
                }
            }
        }

        for (var entry : columns.entrySet()) {
            var fieldName = entry.getKey();
            var mapping = entry.getValue();

            switch (mapping.source()) {
                case HEADER_NAME -> {
                    if (headerFields == null) {
                        throw new IllegalArgumentException(
                                "HEADER_NAME mapping for '" + fieldName
                                        + "' but no header row is available");
                    }
                    int index = -1;
                    for (int i = 0; i < headerFields.size(); i++) {
                        if (headerFields.get(i).trim().equalsIgnoreCase(mapping.value().trim())) {
                            index = i;
                            break;
                        }
                    }
                    if (index < 0) {
                        throw new IllegalArgumentException(
                                "Header column '" + mapping.value()
                                        + "' not found for field '" + fieldName + "'");
                    }
                    resolved.put(fieldName, index);
                }
                case COLUMN_INDEX -> {
                    try {
                        resolved.put(fieldName, Integer.parseInt(mapping.value()));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "Invalid COLUMN_INDEX for '" + fieldName
                                        + "': '" + mapping.value() + "' is not an integer");
                    }
                }
            }
        }

        return resolved;
    }

    /**
     * Split a line into fields, respecting double-quote escaping for CSV delimiters.
     * For WHITESPACE delimiter, splits on one or more whitespace characters.
     */
    static List<String> splitLine(String line, String delimiter) {
        if (line == null || line.isEmpty()) {
            return List.of();
        }

        if ("WHITESPACE".equalsIgnoreCase(delimiter)) {
            var parts = line.trim().split("\\s+");
            return List.of(parts);
        }

        // Handle escaped tab delimiter: JSON stores \t as "\\t"
        String delimStr;
        if ("TAB".equalsIgnoreCase(delimiter) || "\\t".equals(delimiter)) {
            delimStr = "\t";
        } else if (",".equals(delimiter) || ";".equals(delimiter)) {
            delimStr = delimiter;
        } else {
            delimStr = delimiter;
        }

        return splitWithQuotes(line, delimStr);
    }

    /**
     * Split a line on delimiter, respecting double-quoted fields.
     */
    private static List<String> splitWithQuotes(String line, String delimiter) {
        var fields = new ArrayList<String>();
        var current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && c == delimiter.charAt(0)) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());

        return fields;
    }

    /**
     * For WHITESPACE-delimited lines, remove empty leading/trailing fields.
     */
    private static List<String> trimWhitespaceFields(List<String> fields) {
        var result = new ArrayList<String>(fields);
        while (!result.isEmpty() && result.get(0).isEmpty()) {
            result.remove(0);
        }
        while (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    /**
     * Parse a string as a double, supporting decimal separators and unit suffix stripping.
     */
    static double parseDouble(String value, String decimalSeparator) throws NumberFormatException {
        if (value == null || value.isBlank()) {
            throw new NumberFormatException("Empty value");
        }

        var s = value.trim();
        s = stripUnitSuffix(s);

        if (s.isEmpty()) {
            throw new NumberFormatException("Empty value after stripping units");
        }

        if (",".equals(decimalSeparator)) {
            // Remove thousands separators (dots) first: "12.345,678" → "12345,678"
            if (s.contains(".")) {
                s = s.replace(".", "");
            }
            // Then replace decimal comma with dot: "12345,678" → "12345.678"
            s = s.replace(',', '.');
        }

        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Cannot parse '" + value + "' as number");
        }
    }

    /**
     * Strip common unit suffixes from a coordinate string.
     */
    private static String stripUnitSuffix(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        var lower = s.toLowerCase();
        var suffixes = List.of("inches", "inch", "millimeters", "millimeter",
                "mil", "mm", "cm", "deg", "°", "in");

        for (var suffix : suffixes) {
            if (lower.endsWith(suffix)) {
                return s.substring(0, s.length() - suffix.length()).trim();
            }
        }
        return s;
    }

    /**
     * Apply side value mappings if configured.
     */
    private static String applySideMapping(String rawValue,
                                            Map<String, Map<String, String>> valueMappings) {
        if (rawValue == null || rawValue.isEmpty()) {
            return null;
        }

        if (valueMappings != null && valueMappings.containsKey("side")) {
            var sideMappings = valueMappings.get("side");
            var mapped = sideMappings.get(rawValue);
            if (mapped != null) {
                return mapped;
            }
            // Try case-insensitive match
            for (var entry : sideMappings.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(rawValue)) {
                    return entry.getValue();
                }
            }
        }
        return rawValue;
    }
}
