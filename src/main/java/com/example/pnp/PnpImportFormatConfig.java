package com.example.pnp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Proposed PnP import format configuration produced by the LLM-assisted detector.
 * <p>
 * This represents the manual settings a user would otherwise provide
 * before the existing parser can read a PnP file.
 *
 * @param schemaVersion      must be 1
 * @param confidence         confidence score between 0 and 1
 * @param delimiter          field delimiter character
 * @param quoteChar          quote character (may be null/blank for none)
 * @param encoding           file encoding (default "UTF-8")
 * @param linesToIgnore      zero-based line indexes to skip
 * @param headerRowIndex     zero-based header row index (may be null)
 * @param dataStartRowIndex  zero-based index where data rows begin
 * @param dataEndRowIndex    zero-based index where data rows end (may be null)
 * @param decimalSeparator   "." or ","
 * @param columns            mapping from canonical field name to column mapping
 * @param units              mapping from canonical field name to unit string
 * @param valueMappings      mapping from field name to raw-to-normalized value map
 * @param warnings           list of warning strings
 */
public record PnpImportFormatConfig(
        @JsonProperty("schemaVersion") int schemaVersion,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("delimiter") String delimiter,
        @JsonProperty("quoteChar") String quoteChar,
        @JsonProperty("encoding") String encoding,
        @JsonProperty("linesToIgnore") List<Integer> linesToIgnore,
        @JsonProperty("headerRowIndex") Integer headerRowIndex,
        @JsonProperty("dataStartRowIndex") int dataStartRowIndex,
        @JsonProperty("dataEndRowIndex") Integer dataEndRowIndex,
        @JsonProperty("decimalSeparator") String decimalSeparator,
        @JsonProperty("columns") Map<String, ColumnMapping> columns,
        @JsonProperty("units") Map<String, String> units,
        @JsonProperty("valueMappings") Map<String, Map<String, String>> valueMappings,
        @JsonProperty("warnings") List<String> warnings) {
}
