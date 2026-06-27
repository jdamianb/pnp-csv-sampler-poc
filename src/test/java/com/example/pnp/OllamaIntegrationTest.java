package com.example.pnp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in integration tests for {@link OllamaLlmClient}.
 * <p>
 * These tests require a running Ollama instance and are disabled by default.
 * Enable with: mvn test -Dllm.integration=true
 */
@EnabledIfSystemProperty(named = "llm.integration", matches = "true")
class OllamaIntegrationTest {

    private static final String OLLAMA_URL = "http://localhost:11434";
    private static final String OLLAMA_MODEL = "qwen2.5:3b";

    @Test
    void ollamaReturnsValidConfig() {
        var filePath = "examples/simple-pnp.csv";

        var args = new String[]{
                "detect", filePath,
                "--llm-provider", "ollama",
                "--llm-url", OLLAMA_URL,
                "--llm-model", OLLAMA_MODEL,
                "--llm-temperature", "0"
        };

        var exitCode = Main.run(args);
        assertEquals(0, exitCode,
                "Detection should succeed with a running Ollama instance");
    }

    @Test
    void ollamaResponseIsValidConfig() {
        var options = new LlmOptions("ollama", OLLAMA_URL, OLLAMA_MODEL, 0);
        var client = new OllamaLlmClient(options);

        var sample = createSample();
        var promptBuilder = new FormatDetectionPromptBuilder();
        var prompt = promptBuilder.build(sample);

        var response = client.sendPrompt(prompt);
        assertNotNull(response);
        assertFalse(response.isBlank());

        // The response should be parseable as PnpImportFormatConfig
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertDoesNotThrow(() -> {
            var config = mapper.readValue(response, PnpImportFormatConfig.class);
            assertNotNull(config);
        });
    }

    @Test
    void ollamaValidConfigPassesValidation() {
        var options = new LlmOptions("ollama", OLLAMA_URL, OLLAMA_MODEL, 0);
        var client = new OllamaLlmClient(options);

        var sample = createSample();
        var promptBuilder = new FormatDetectionPromptBuilder();
        var prompt = promptBuilder.build(sample);

        var response = client.sendPrompt(prompt);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var config = assertDoesNotThrow(() ->
                mapper.readValue(response, PnpImportFormatConfig.class));

        var validator = new PnpImportFormatConfigValidator();
        var errors = validator.validate(config);
        assertTrue(errors.isEmpty(),
                "Ollama response should pass validation. Errors: " + errors);
    }

    private static SampleResult createSample() {
        var lines = java.util.List.of(
                new Line(0, "# Pick and Place Export"),
                new Line(1, "# Source: Example CAD Tool"),
                new Line(2, "# Generated: 2026-06-27"),
                new Line(3, "# Unit: mm, Angle: deg, Side: all"),
                new Line(4, "Designator,Comment,Footprint,Mid X,Mid Y,Rotation,Layer"),
                new Line(5, "C1,100nF,C_0603,95.0518,22.6822,270,Top"),
                new Line(6, "R1,10k,R_0603,106.4056,23.0124,90,Top"),
                new Line(7, "U1,STM32F103C8T6,LQFP-48,50.0,40.0,0,Top"),
                new Line(8, "D1,LED_RED,LED_0603,42.125,12.5,180,Bottom")
        );
        return new SampleResult(9, lines, java.util.List.of());
    }
}
