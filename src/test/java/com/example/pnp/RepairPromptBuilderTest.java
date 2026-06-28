package com.example.pnp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RepairPromptBuilder}.
 */
class RepairPromptBuilderTest {

    private final RepairPromptBuilder builder = new RepairPromptBuilder();

    @Test
    void promptContainsSampleLines() {
        var sample = createSample();
        var configJson = "{\"delimiter\":\",\"}";
        var errors = List.of("Invalid header row index");

        var prompt = builder.build(sample, configJson, errors);

        assertTrue(prompt.contains("[0] Line A"));
        assertTrue(prompt.contains("[1] Line B"));
        assertTrue(prompt.contains("Total lines: 2"));
    }

    @Test
    void promptContainsBrokenConfig() {
        var sample = createSample();
        var configJson = "{\"delimiter\":\",\"}";
        var errors = List.of("Invalid header row index");

        var prompt = builder.build(sample, configJson, errors);

        assertTrue(prompt.contains(configJson));
    }

    @Test
    void promptContainsErrors() {
        var sample = createSample();
        var configJson = "{\"delimiter\":\",\"}";
        var errors = List.of("Invalid header row index", "Missing X column");

        var prompt = builder.build(sample, configJson, errors);

        assertTrue(prompt.contains("Invalid header row index"));
        assertTrue(prompt.contains("Missing X column"));
    }

    @Test
    void promptContainsCorrectionInstructions() {
        var sample = createSample();
        var configJson = "{}";
        var errors = List.of("Error");

        var prompt = builder.build(sample, configJson, errors);

        assertTrue(prompt.contains("correct"));
        assertTrue(prompt.contains("Return ONLY valid JSON"));
    }

    @Test
    void emptyErrorsProducesPlaceholder() {
        var sample = createSample();
        var configJson = "{}";
        List<String> errors = List.of();

        var prompt = builder.build(sample, configJson, errors);

        assertTrue(prompt.contains("no specific errors"));
    }

    @Test
    void nullErrorsProducesPlaceholder() {
        var sample = createSample();
        var configJson = "{}";
        List<String> nullErrors = null;

        var prompt = builder.build(sample, configJson, nullErrors);

        assertTrue(prompt.contains("no specific errors"));
    }

    @Test
    void promptDoesNotContainExplanationInstruction() {
        // The prompt should ask for JSON only, not explanation
        var sample = createSample();
        var configJson = "{}";
        var errors = List.of("Error");

        var prompt = builder.build(sample, configJson, errors);

        assertTrue(prompt.contains("Return ONLY valid JSON"));
        assertTrue(prompt.contains("Do not include any explanation"));
    }

    private static SampleResult createSample() {
        return new SampleResult(2, List.of(
                new Line(0, "Line A"),
                new Line(1, "Line B")
        ), List.of());
    }
}
