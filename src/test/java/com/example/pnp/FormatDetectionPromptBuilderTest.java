package com.example.pnp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FormatDetectionPromptBuilder}.
 */
class FormatDetectionPromptBuilderTest {

    private final FormatDetectionPromptBuilder builder = new FormatDetectionPromptBuilder();

    @Test
    void promptContainsManualConfigurationExplanation() {
        var sample = createSample(5);
        var prompt = builder.build(sample);

        assertTrue(prompt.contains("manual configuration"),
                "Prompt should explain that LLM replaces the manual configuration step");
    }

    @Test
    void promptStatesParserIsAuthority() {
        var sample = createSample(5);
        var prompt = builder.build(sample);

        assertTrue(prompt.contains("parser remains the authority"),
                "Prompt should state the existing parser remains the authority");
    }

    @Test
    void promptStatesLlmMustNotParseRows() {
        var sample = createSample(5);
        var prompt = builder.build(sample);

        assertTrue(prompt.contains("Do NOT parse"),
                "Prompt should state the LLM must not parse component rows");
    }

    @Test
    void promptIncludesRawSampledLines() {
        var sample = createSample(5);
        var prompt = builder.build(sample);

        for (int i = 0; i < 5; i++) {
            assertTrue(prompt.contains("[0]") || prompt.contains("[4]"),
                    "Prompt should contain raw sampled lines with indexes");
        }
    }

    @Test
    void promptIncludesTotalLines() {
        var sample = createSample(100);
        var prompt = builder.build(sample);

        assertTrue(prompt.contains("100"),
                "Prompt should include the total line count");
    }

    @Test
    void promptIncludesExpectedSchema() {
        var sample = createSample(3);
        var prompt = builder.build(sample);

        assertTrue(prompt.contains("schemaVersion"),
                "Prompt should describe the expected JSON schema");
        assertTrue(prompt.contains("delimiter"),
                "Prompt should mention delimiter in schema");
        assertTrue(prompt.contains("columns"),
                "Prompt should mention columns in schema");
    }

    @Test
    void promptRequiresJsonOnly() {
        var sample = createSample(3);
        var prompt = builder.build(sample);

        assertTrue(prompt.contains("Return ONLY valid JSON"),
                "Prompt should ask for JSON-only response");
    }

    @Test
    void promptContainsSampleLines() {
        var sample = createSample(8);
        var prompt = builder.build(sample);

        assertTrue(prompt.contains("Total lines: 8"));
        assertTrue(prompt.contains("[0]"));
        assertTrue(prompt.contains("[7]"));
    }

    private static SampleResult createSample(int totalLines) {
        var firstLines = new java.util.ArrayList<Line>();
        for (int i = 0; i < Math.min(totalLines, 80); i++) {
            firstLines.add(new Line(i, "Line " + i + " content"));
        }
        return new SampleResult(totalLines, firstLines, List.of());
    }
}
