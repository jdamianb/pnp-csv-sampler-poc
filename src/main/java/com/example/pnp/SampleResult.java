package com.example.pnp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The result of sampling a PnP CSV file.
 *
 * @param totalLines total number of lines in the file
 * @param firstLines first N lines (top of file)
 * @param lastLines  last M lines (end of file), excluding any already in firstLines
 */
public record SampleResult(
        @JsonProperty("totalLines") long totalLines,
        @JsonProperty("firstLines") List<Line> firstLines,
        @JsonProperty("lastLines") List<Line> lastLines) {
}
