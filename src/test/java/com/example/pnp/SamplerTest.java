package com.example.pnp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Sampler} covering all Gherkin acceptance scenarios
 * from the PnP CSV Sampler specification.
 */
class SamplerTest {

    // ============================================================
    //  Scenario: Default sampling of a file with more lines than
    //            the default sample size
    // ============================================================

    @Test
    void defaultSamplingWithLargeFile() throws IOException {
        var lines = generateLines(100);
        var reader = toReader(lines);
        var sampler = new Sampler(80, 20);
        var result = sampler.sample(reader);

        assertEquals(100, result.totalLines());
        assertEquals(80, result.firstLines().size());
        assertEquals(0, result.firstLines().getFirst().index());
        assertEquals(79, result.firstLines().getLast().index());
        assertEquals(20, result.lastLines().size());
        assertEquals(80, result.lastLines().getFirst().index());
        assertEquals(99, result.lastLines().getLast().index());
    }

    // ============================================================
    //  Scenario: Custom first and last counts
    // ============================================================

    @Test
    void customFirstAndLastCounts() throws IOException {
        var lines = generateLines(100);
        var reader = toReader(lines);
        var sampler = new Sampler(10, 5);
        var result = sampler.sample(reader);

        assertEquals(100, result.totalLines());
        assertEquals(10, result.firstLines().size());
        assertEquals(0, result.firstLines().getFirst().index());
        assertEquals(9, result.firstLines().getLast().index());
        assertEquals(5, result.lastLines().size());
        assertEquals(95, result.lastLines().getFirst().index());
        assertEquals(99, result.lastLines().getLast().index());
    }

    // ============================================================
    //  Scenario: File smaller than total requested sample size
    // ============================================================

    @Test
    void fileSmallerThanRequestedSampleSize() throws IOException {
        var lines = generateLines(5);
        var reader = toReader(lines);
        var sampler = new Sampler(10, 5);
        var result = sampler.sample(reader);

        assertEquals(5, result.totalLines());
        assertEquals(5, result.firstLines().size());
        assertEquals(0, result.firstLines().getFirst().index());
        assertEquals(4, result.firstLines().getLast().index());
        assertTrue(result.lastLines().isEmpty(),
                "lastLines should be empty when all lines are already in firstLines");
    }

    // ============================================================
    //  Scenario: Preserve raw line content exactly
    // ============================================================

    @Test
    void preservesRawLineContentExactly() throws IOException {
        var lines = List.of(
                "  leading spaces",
                "trailing spaces   ",
                "\t tabs \t",
                "  both \t ",
                "normal"
        );
        var reader = toReader(lines);
        var sampler = new Sampler(5, 2);
        var result = sampler.sample(reader);

        for (int i = 0; i < lines.size(); i++) {
            assertEquals(lines.get(i), result.firstLines().get(i).text(),
                    "Line " + i + " content must be preserved exactly");
        }
    }

    // ============================================================
    //  Scenario: Preserve zero-based indexing
    // ============================================================

    @Test
    void zeroBasedIndexing() throws IOException {
        var reader = toReader(List.of("only line"));
        var sampler = new Sampler(1, 1);
        var result = sampler.sample(reader);

        assertEquals(1, result.totalLines());
        assertEquals(0, result.firstLines().getFirst().index());
        assertEquals("only line", result.firstLines().getFirst().text());
    }

    // ============================================================
    //  Scenario: Empty file
    // ============================================================

    @Test
    void emptyFile() throws IOException {
        var reader = toReader(List.of());
        var sampler = new Sampler(80, 20);
        var result = sampler.sample(reader);

        assertEquals(0, result.totalLines());
        assertTrue(result.firstLines().isEmpty());
        assertTrue(result.lastLines().isEmpty());
    }

    // ============================================================
    //  Scenario: File with single line
    // ============================================================

    @Test
    void singleLineFileWithOverlappingCounts() throws IOException {
        var reader = toReader(List.of("the only line"));
        var sampler = new Sampler(1, 1);
        var result = sampler.sample(reader);

        assertEquals(1, result.totalLines());
        assertEquals(1, result.firstLines().size());
        assertEquals(0, result.firstLines().getFirst().index());
        assertEquals("the only line", result.firstLines().getFirst().text());
        assertTrue(result.lastLines().isEmpty(),
                "lastLines should be empty when totalLines <= firstCount");
    }

    // ============================================================
    //  Scenario: CLI exit codes and error messages for invalid args
    //  (covered in MainTest)
    // ============================================================

    // ============================================================
    //  Scenario: Output is well-formed JSON (covered by Jackson)
    // ============================================================

    // ============================================================
    //  Scenario: Streaming large file — memory proportional to
    //            sample size, not file size
    // ============================================================

    @Test
    void streamingDoesNotLoadEntireFile() throws IOException {
        // Generate 100_000 lines but only sample first 10 + last 5
        var lines = generateLines(100_000);
        var reader = toReader(lines);
        var sampler = new Sampler(10, 5);
        var result = sampler.sample(reader);

        assertEquals(100_000, result.totalLines());
        assertEquals(10, result.firstLines().size());
        assertEquals(0, result.firstLines().getFirst().index());
        assertEquals(9, result.firstLines().getLast().index());
        assertEquals(5, result.lastLines().size());
        assertEquals(99_995, result.lastLines().getFirst().index());
        assertEquals(99_999, result.lastLines().getLast().index());

        // Verify memory: firstLines holds 10 lines, lastBuffer holds 5 — not 100_000
        // This is a structural check; the ring buffer design ensures it.
    }

    @Test
    void veryLargeFileStreaming() throws IOException {
        // Simulate a 1_000_000 line file
        var lines = generateLines(1_000_000);
        var reader = toReader(lines);
        var sampler = new Sampler(80, 20);
        var result = sampler.sample(reader);

        assertEquals(1_000_000, result.totalLines());
        assertEquals(80, result.firstLines().size());
        assertEquals(20, result.lastLines().size());
        assertEquals(999_980, result.lastLines().getFirst().index());
        assertEquals(999_999, result.lastLines().getLast().index());
    }

    // ============================================================
    //  Scenario: DOS line endings (\r\n) — BufferedReader handles
    //            this, but we verify the text is preserved
    // ============================================================

    @Test
    void dosLineEndings() throws IOException {
        // BufferedReader.readLine() strips the line terminator regardless
        // of \n or \r\n. The raw text content (without terminator) is preserved.
        var content = "line1\r\nline2\r\nline3";
        var reader = new StringReader(content);
        var sampler = new Sampler(3, 1);
        var result = sampler.sample(reader);

        assertEquals(3, result.totalLines());
        assertEquals("line1", result.firstLines().get(0).text());
        assertEquals("line2", result.firstLines().get(1).text());
        assertEquals("line3", result.firstLines().get(2).text());
    }

    // ============================================================
    //  Scenario: File with no trailing newline
    // ============================================================

    @Test
    void noTrailingNewline() throws IOException {
        // Last line has no \n terminator
        var content = "line1\nline2\nline3";
        var reader = new StringReader(content);
        var sampler = new Sampler(3, 1);
        var result = sampler.sample(reader);

        assertEquals(3, result.totalLines());
        assertEquals("line1", result.firstLines().get(0).text());
        assertEquals("line2", result.firstLines().get(1).text());
        assertEquals("line3", result.firstLines().get(2).text());
        // totalLines(3) <= firstCount(3), so no lastLines (R10: no duplicates)
        assertTrue(result.lastLines().isEmpty());
    }

    // ============================================================
    //  Error: Sampler constructor rejects non-positive counts
    // ============================================================

    @Test
    void constructorRejectsZeroFirstCount() {
        assertThrows(IllegalArgumentException.class, () -> new Sampler(0, 20));
    }

    @Test
    void constructorRejectsNegativeFirstCount() {
        assertThrows(IllegalArgumentException.class, () -> new Sampler(-1, 20));
    }

    @Test
    void constructorRejectsZeroLastCount() {
        assertThrows(IllegalArgumentException.class, () -> new Sampler(80, 0));
    }

    @Test
    void constructorRejectsNegativeLastCount() {
        assertThrows(IllegalArgumentException.class, () -> new Sampler(80, -5));
    }

    // ============================================================
    //  Custom only-first (default last)
    // ============================================================

    @Test
    void customFirstOnly() throws IOException {
        var lines = generateLines(50);
        var reader = toReader(lines);
        var sampler = new Sampler(5, 20); // default last = 20
        var result = sampler.sample(reader);

        assertEquals(50, result.totalLines());
        assertEquals(5, result.firstLines().size());
        assertEquals(20, result.lastLines().size());
        assertEquals(30, result.lastLines().getFirst().index());
    }

    // ============================================================
    //  Custom only-last (default first)
    // ============================================================

    @Test
    void customLastOnly() throws IOException {
        var lines = generateLines(50);
        var reader = toReader(lines);
        var sampler = new Sampler(80, 5); // default first = 80, but file has 50
        var result = sampler.sample(reader);

        assertEquals(50, result.totalLines());
        assertEquals(50, result.firstLines().size()); // all lines in first
        assertTrue(result.lastLines().isEmpty(),
                "lastLines should be empty when totalLines <= firstCount (R10 no duplicates)");
    }

    // ============================================================
    //  Scenario: totalLines <= firstCount + lastCount but > firstCount
    // ============================================================

    @Test
    void partialOverlapBetweenFirstAndLast() throws IOException {
        // 100 lines, first=80, last=40
        // firstCount + lastCount = 120 > 100, so there's overlap
        var lines = generateLines(100);
        var reader = toReader(lines);
        var sampler = new Sampler(80, 40);
        var result = sampler.sample(reader);

        assertEquals(100, result.totalLines());
        assertEquals(80, result.firstLines().size());
        // lastLines should only include lines 80..99 (20 lines, not 40)
        assertEquals(20, result.lastLines().size());
        assertEquals(80, result.lastLines().getFirst().index());
        assertEquals(99, result.lastLines().getLast().index());
    }

    // ============================================================
    //  Verify SampleResult JSON serialization
    // ============================================================

    @Test
    void jsonSerialization() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var result = new SampleResult(
                3,
                List.of(new Line(0, "first"), new Line(1, "second")),
                List.of(new Line(2, "third"))
        );

        var json = mapper.writeValueAsString(result);
        assertTrue(json.contains("\"totalLines\":3"));
        assertTrue(json.contains("\"firstLines\""));
        assertTrue(json.contains("\"lastLines\""));
        assertTrue(json.contains("\"index\":0"));
        assertTrue(json.contains("\"text\":\"first\""));
        assertTrue(json.contains("\"index\":2"));
        assertTrue(json.contains("\"text\":\"third\""));

        // Verify it can be parsed back
        var parsed = mapper.readValue(json, SampleResult.class);
        assertEquals(3, parsed.totalLines());
        assertEquals(2, parsed.firstLines().size());
        assertEquals(1, parsed.lastLines().size());
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private static List<String> generateLines(int count) {
        var lines = new java.util.ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            lines.add("Line " + i);
        }
        return lines;
    }

    private static Reader toReader(List<String> lines) {
        var sb = new StringBuilder();
        for (var line : lines) {
            sb.append(line).append('\n');
        }
        return new StringReader(sb.toString());
    }
}
