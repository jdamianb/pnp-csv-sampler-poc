package com.example.pnp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RepairLoop}.
 * <p>
 * All tests are deterministic and require no network.
 */
class RepairLoopTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PnpImportFormatConfigValidator validator = new PnpImportFormatConfigValidator();
    private final SimplePnpParser parser = new SimplePnpParser();
    private final RepairPromptBuilder repairPromptBuilder = new RepairPromptBuilder();
    private final FormatDetectionPromptBuilder detectionPromptBuilder = new FormatDetectionPromptBuilder();

    // A valid config for the test file
    private static final String VALID_CONFIG_JSON = """
            {
              "schemaVersion": 1,
              "confidence": 0.9,
              "delimiter": ",",
              "quoteChar": "\\"",
              "encoding": "UTF-8",
              "linesToIgnore": [],
              "headerRowIndex": 0,
              "dataStartRowIndex": 1,
              "decimalSeparator": ".",
              "columns": {
                "reference": { "source": "HEADER_NAME", "value": "Designator" },
                "x": { "source": "HEADER_NAME", "value": "Mid X" },
                "y": { "source": "HEADER_NAME", "value": "Mid Y" },
                "angle": { "source": "HEADER_NAME", "value": "Rotation" }
              }
            }
            """;

    // An invalid config (missing required x column)
    private static final String INVALID_CONFIG_JSON = """
            {
              "schemaVersion": 1,
              "confidence": 0.9,
              "delimiter": ",",
              "quoteChar": "\\"",
              "encoding": "UTF-8",
              "linesToIgnore": [],
              "headerRowIndex": 0,
              "dataStartRowIndex": 1,
              "decimalSeparator": ".",
              "columns": {
                "reference": { "source": "HEADER_NAME", "value": "Designator" }
              }
            }
            """;

    // A config that passes validation but fails parsing (wrong delimiter)
    private static final String PARSER_INVALID_CONFIG_JSON = """
            {
              "schemaVersion": 1,
              "confidence": 0.9,
              "delimiter": "|",
              "quoteChar": "\\"",
              "encoding": "UTF-8",
              "linesToIgnore": [],
              "headerRowIndex": 0,
              "dataStartRowIndex": 1,
              "decimalSeparator": ".",
              "columns": {
                "reference": { "source": "HEADER_NAME", "value": "Designator" },
                "x": { "source": "HEADER_NAME", "value": "Mid X" },
                "y": { "source": "HEADER_NAME", "value": "Mid Y" },
                "angle": { "source": "HEADER_NAME", "value": "Rotation" }
              }
            }
            """;

    // Sample CSV file content
    private static final String FILE_CONTENT = """
            Designator,Comment,Footprint,Mid X,Mid Y,Rotation,Layer
            C1,100nF,C_0603,95.0518,22.6822,270,Top
            R1,10k,R_0603,106.4056,23.0124,90,Top
            """;

    // Sample result corresponding to FILE_CONTENT
    private static final SampleResult SAMPLE_RESULT = new SampleResult(
            2,
            List.of(new Line(0, "Designator,Comment,Footprint,Mid X,Mid Y,Rotation,Layer")),
            List.of(new Line(1, "C1,100nF,C_0603,95.0518,22.6822,270,Top"),
                    new Line(2, "R1,10k,R_0603,106.4056,23.0124,90,Top"))
    );

    // ============================================================
    //  Valid config → no repair needed
    // ============================================================

    @Test
    void validConfigDoesNotTriggerRepair() {
        var llmClient = new StubLlmClient(VALID_CONFIG_JSON);
        var repairLoop = createRepairLoop(llmClient);

        var result = repairLoop.detectWithRepair(
                SAMPLE_RESULT, new StringReader(FILE_CONTENT), 2);

        assertTrue(result.isValid());
        assertEquals(0, result.repairAttempts());
        assertFalse(result.wasRepaired());
    }

    // ============================================================
    //  Invalid config → repair succeeds
    // ============================================================

    @Test
    void invalidConfigRepairsSuccessfully() {
        // First call returns invalid, repair call returns valid
        var llmClient = new SequenceLlmClient(List.of(INVALID_CONFIG_JSON, VALID_CONFIG_JSON));
        var repairLoop = createRepairLoop(llmClient);

        var result = repairLoop.detectWithRepair(
                SAMPLE_RESULT, new StringReader(FILE_CONTENT), 2);

        assertTrue(result.isValid());
        assertEquals(1, result.repairAttempts());
        assertTrue(result.wasRepaired());
    }

    // ============================================================
    //  Stub returns identical JSON → stops after 1 attempt
    // ============================================================

    @Test
    void stubReturnsIdenticalJson_stopsEarly() {
        // StubLlmClient always returns the same JSON
        var llmClient = new StubLlmClient(INVALID_CONFIG_JSON);
        var repairLoop = createRepairLoop(llmClient);

        var result = repairLoop.detectWithRepair(
                SAMPLE_RESULT, new StringReader(FILE_CONTENT), 3);

        assertFalse(result.isValid());
        assertTrue(result.repairAttempts() <= 2); // stops after detecting identical JSON
        assertFalse(result.wasRepaired());
    }

    // ============================================================
    //  Max retries exhausted → failure
    // ============================================================

    @Test
    void maxRetriesExhausted_returnsFailure() {
        // Always returns an invalid-but-different config so stub detection doesn't trigger
        var llmClient = new SequenceLlmClient(List.of(
                "{\"schemaVersion\":1,\"confidence\":0.5,\"delimiter\":\",\",\"headerRowIndex\":0,\"dataStartRowIndex\":1,\"decimalSeparator\":\".\",\"columns\":{\"reference\":{\"source\":\"HEADER_NAME\",\"value\":\"Designator\"}}}",
                "{\"schemaVersion\":1,\"confidence\":0.5,\"delimiter\":\",\",\"headerRowIndex\":0,\"dataStartRowIndex\":1,\"decimalSeparator\":\".\",\"columns\":{\"reference\":{\"source\":\"HEADER_NAME\",\"value\":\"Designator\"},\"x\":{\"source\":\"COLUMN_INDEX\",\"value\":\"0\"},\"y\":{\"source\":\"COLUMN_INDEX\",\"value\":\"0\"},\"angle\":{\"source\":\"COLUMN_INDEX\",\"value\":\"0\"}}}"
        ));
        var repairLoop = createRepairLoop(llmClient);

        var result = repairLoop.detectWithRepair(
                SAMPLE_RESULT, new StringReader(FILE_CONTENT), 2);

        assertFalse(result.isValid());
        assertEquals(2, result.repairAttempts()); // exhausted 2 retries
        assertFalse(result.wasRepaired());
    }

    // ============================================================
    //  Zero max retries = no repair
    // ============================================================

    @Test
    void zeroMaxRetries_noRepair() {
        var llmClient = new StubLlmClient(INVALID_CONFIG_JSON);
        var repairLoop = createRepairLoop(llmClient);

        var result = repairLoop.detectWithRepair(
                SAMPLE_RESULT, new StringReader(FILE_CONTENT), 0);

        assertFalse(result.isValid());
        assertEquals(0, result.repairAttempts());
        assertFalse(result.wasRepaired());
    }

    // ============================================================
    //  Parser-invalid config → repair triggered
    // ============================================================

    @Test
    void configPassesValidationButFailsParser_triggersRepair() {
        var llmClient = new SequenceLlmClient(List.of(
                PARSER_INVALID_CONFIG_JSON, VALID_CONFIG_JSON));
        var repairLoop = createRepairLoop(llmClient);

        var result = repairLoop.detectWithRepair(
                SAMPLE_RESULT, new StringReader(FILE_CONTENT), 2);

        assertTrue(result.isValid());
        assertTrue(result.wasRepaired());
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private RepairLoop createRepairLoop(LlmClient llmClient) {
        var detector = new Detector(detectionPromptBuilder, llmClient,
                validator, mapper);
        return new RepairLoop(detector, repairPromptBuilder, llmClient,
                mapper, validator, parser);
    }

    /**
     * A custom LlmClient that returns responses from a sequence.
     */
    private static class SequenceLlmClient implements LlmClient {
        private final List<String> responses;
        private int callCount = 0;

        SequenceLlmClient(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public String sendPrompt(String prompt) {
            if (callCount < responses.size()) {
                return responses.get(callCount++);
            }
            // If we run out, return the last one
            return responses.get(responses.size() - 1);
        }
    }
}
