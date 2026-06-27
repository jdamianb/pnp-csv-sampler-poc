package com.example.pnp;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Source type for a column mapping.
 */
public enum ColumnSource {
    @JsonProperty("HEADER_NAME") HEADER_NAME,
    @JsonProperty("COLUMN_INDEX") COLUMN_INDEX
}
