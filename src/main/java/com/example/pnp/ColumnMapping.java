package com.example.pnp;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A column mapping that links a canonical field to a source column.
 *
 * @param source how the column is identified (HEADER_NAME or COLUMN_INDEX)
 * @param value  the column's header name or zero-based index as a string
 */
public record ColumnMapping(
        @JsonProperty("source") ColumnSource source,
        @JsonProperty("value") String value) {
}
