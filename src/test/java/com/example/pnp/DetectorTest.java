package com.example.pnp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Detector} orchestrator with {@link StubLlmClient}.
 */
class DetectorTest {

    private final FormatDetectionPromptBuilder promptBuilder = new FormatDetectionPromptBuilder();
    private final PnpImportFormatConfigValidator validator = new PnpImportFormatConfigValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void detectWithValidStubResponseReturnsValidResult() {
        var stubJson = """
                {
                  "schemaVersion": 1,
                  "confidence": 0.9,
                  "delimiter": ",",
                  "quoteChar": "\\"",
                  "encoding": "UTF-8",
                  "linesToIgnore": [0, 1],
                  "headerRowIndex": 2,
                  "dataStartRowIndex": 3,
                  "dataEndRowIndex": 10,
                  "decimalSeparator": ".",
                  "columns": {
                    "reference": { "source": "HEADER_NAME", "value": "Ref" },
                    "x": { "source": "HEADER_NAME", "value": "X" },
                    "y": { "source": "HEADER_NAME", "value": "Y" },
                    "angle": { "source": "HEADER_NAME", "value": "Angle" }
                  },
                  "units": { "x": "mm", "y": "mm", "angle": "deg" },
                  "valueMappings": {},
                  "warnings": []
                }
                """;
        var client = new StubLlmClient(stubJson);
        var detector = new Detector(promptBuilder, client, validator, objectMapper);
        var sample = createSample(20);

        var result = detector.detect(sample);

        assertTrue(result.isValid(), "Valid stub response should produce valid result");
        assertNotNull(result.config());
        assertEquals(",", result.config().delimiter());
    }

    @Test
    void detectWithInvalidStubJsonReturnsParseError() {
        var client = new StubLlmClient("not valid json");
        var detector = new Detector(promptBuilder, client, validator, objectMapper);

        var result = detector.detect(createSample(5));

        assertFalse(result.isValid());
        assertNull(result.config());
        assertTrue(result.errors().getFirst().contains("Failed to parse"));
    }

    @Test
    void detectWithInvalidConfigReturnsValidationErrors() {
        var stubJson = """
                {
                  "schemaVersion": 1,
                  "confidence": 0.9,
                  "delimiter": "",
                  "quoteChar": "",
                  "encoding": "UTF-8",
                  "linesToIgnore": [],
                  "dataStartRowIndex": 0,
                  "decimalSeparator": ".",
                  "columns": {
                    "x": { "source": "HEADER_NAME", "value": "X" },
                    "y": { "source": "HEADER_NAME", "value": "Y" },
                    "angle": { "source": "HEADER_NAME", "value": "Angle" }
                  },
                  "units": {},
                  "valueMappings": {},
                  "warnings": []
                }
                """;
        var client = new StubLlmClient(stubJson);
        var detector = new Detector(promptBuilder, client, validator, objectMapper);

        var result = detector.detect(createSample(5));

        assertFalse(result.isValid());
        // Missing identity column and blank delimiter
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("identity")));
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("delimiter")));
    }

    @Test
    void detectCallsPromptBuilderAndLlmClient() {
        var stubJson = """
                {"schemaVersion":1,"confidence":0.5,"delimiter":",","quoteChar":"","encoding":"UTF-8",
                "linesToIgnore":[],"dataStartRowIndex":0,"decimalSeparator":".",
                "columns":{"reference":{"source":"HEADER_NAME","value":"R"},
                "x":{"source":"HEADER_NAME","value":"X"},"y":{"source":"HEADER_NAME","value":"Y"},
                "angle":{"source":"HEADER_NAME","value":"A"}},"units":{},"valueMappings":{},"warnings":[]}
                """;
        var client = new StubLlmClient(stubJson);
        var detector = new Detector(promptBuilder, client, validator, objectMapper);

        var sample = createSample(3);
        var result = detector.detect(sample);

        assertTrue(result.isValid());
        assertNotNull(result.config());
    }

    private static SampleResult createSample(int totalLines) {
        var firstLines = new java.util.ArrayList<Line>();
        for (int i = 0; i < Math.min(totalLines, 80); i++) {
            firstLines.add(new Line(i, "content " + i));
        }
        return new SampleResult(totalLines, firstLines, List.of());
    }
}
