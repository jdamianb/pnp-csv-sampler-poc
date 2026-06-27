package com.example.pnp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generates sample and detect output for every file in examples-extended/,
 * then compares the detect result against the expected config in expected-configs/.
 * <p>
 * Output files are written to target/example-outputs/ for manual inspection.
 * <p>
 * By default, uses StubLlmClient. To use a real LLM (Ollama), set:
 * {@code -Dllm.integration=true} and optionally
 * {@code -Dllm.url=http://localhost:11434} and
 * {@code -Dllm.model=qwen2.5:3b}
 */
class ExampleOutputGeneratorTest {

    private static final Path EXAMPLES_DIR = Path.of("examples-extended");
    private static final Path EXPECTED_DIR = Path.of("expected-configs");
    private static final Path OUTPUT_DIR = Path.of("target", "example-outputs");
    private static final int SAMPLE_FIRST = 80;
    private static final int SAMPLE_LAST = 20;

    @Test
    void generateAndCompareAll() throws IOException {
        Files.createDirectories(OUTPUT_DIR);

        var mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        var sampleFiles = listExampleFiles();
        assertFalse(sampleFiles.isEmpty(),
                "No example files found in " + EXAMPLES_DIR);

        // Determine LLM client: use Ollama if integration test flag is set
        var useRealLlm = "true".equals(System.getProperty("llm.integration"));
        var llmClient = useRealLlm ? createOllamaClient() : createStubClient();

        if (useRealLlm) {
            System.out.println("Using Ollama LLM client for detection");
        } else {
            System.out.println("Using StubLlmClient — set -Dllm.integration=true for real LLM");
        }

        var promptBuilder = new FormatDetectionPromptBuilder();
        var validator = new PnpImportFormatConfigValidator();
        var detector = new Detector(promptBuilder, llmClient, validator, mapper);

        int total = 0;
        int matched = 0;
        int mismatched = 0;
        int missingExpected = 0;
        int errors = 0;
        var details = new ArrayList<String>();

        for (var file : sampleFiles) {
            var stem = stemName(file.getFileName().toString());
            total++;

            var sampleOutput = OUTPUT_DIR.resolve(stem + "-sample.json");
            var detectOutput = OUTPUT_DIR.resolve(stem + "-detect.json");
            var expectedFile = EXPECTED_DIR.resolve(stem + ".config.json");

            // --- Generate sample output ---
            var sampler = new Sampler(SAMPLE_FIRST, SAMPLE_LAST);
            SampleResult sampleResult;
            try (var reader = new InputStreamReader(
                    Files.newInputStream(file), StandardCharsets.UTF_8)) {
                sampleResult = sampler.sample(reader);
            }
            mapper.writeValue(sampleOutput.toFile(), sampleResult);
            System.out.println("  wrote: " + sampleOutput.getFileName());

            // --- Generate detect output ---
            try {
                var result = detector.detect(sampleResult);

                if (result.isValid()) {
                    mapper.writeValue(detectOutput.toFile(), result.config());
                } else {
                    mapper.writeValue(detectOutput.toFile(), Map.of(
                            "file", file.getFileName().toString(),
                            "error", "detection failed",
                            "details", result.errors()
                    ));
                }
                System.out.println("  wrote: " + detectOutput.getFileName());

                // --- Compare against expected config ---
                if (Files.exists(expectedFile)) {
                    var expected = mapper.readValue(expectedFile.toFile(), PnpImportFormatConfig.class);

                    if (result.isValid() && configsMatch(result.config(), expected)) {
                        matched++;
                        System.out.println("  ✓ " + stem + " matches expected config");
                    } else if (result.isValid()) {
                        mismatched++;
                        var reason = findDifferences(result.config(), expected);
                        System.out.println("  ✗ " + stem + " differs from expected config");
                        for (var r : reason) {
                            System.out.println("      - " + r);
                        }
                        details.add(stem + ": " + String.join("; ", reason));
                    } else {
                        mismatched++;
                        System.out.println("  ✗ " + stem + " — detection failed");
                        details.add(stem + ": detection failed: " + result.errors());
                    }
                } else {
                    missingExpected++;
                    System.out.println("  ? " + stem + " — no expected config at " + expectedFile.getFileName());
                }
            } catch (Exception e) {
                errors++;
                System.out.println("  ! " + stem + " — error: " + e.getMessage());
                try {
                    mapper.writeValue(detectOutput.toFile(), Map.of(
                            "file", file.getFileName().toString(),
                            "error", e.getMessage()
                    ));
                } catch (Exception ignored) {
                }
            }

            System.out.println();
        }

        // Print summary
        System.out.println("=".repeat(60));
        System.out.println("Summary: " + total + " files processed");
        System.out.println("  ✓  " + matched + " matched expected config");
        System.out.println("  ✗  " + mismatched + " differed from expected config");
        System.out.println("  ?  " + missingExpected + " had no expected config");
        System.out.println("  !  " + errors + " errors during detection");
        System.out.println("Output files in: " + OUTPUT_DIR);
    }

    private static LlmClient createStubClient() {
        var stubJson = """
                {
                  "schemaVersion": 1,
                  "confidence": 0.85,
                  "delimiter": ",",
                  "quoteChar": "\\"",
                  "encoding": "UTF-8",
                  "linesToIgnore": [0, 1, 2],
                  "headerRowIndex": 3,
                  "dataStartRowIndex": 4,
                  "dataEndRowIndex": 7,
                  "decimalSeparator": ".",
                  "columns": {
                    "reference": { "source": "HEADER_NAME", "value": "Designator" },
                    "partNumber": { "source": "HEADER_NAME", "value": "Comment" },
                    "jedec": { "source": "HEADER_NAME", "value": "Footprint" },
                    "x": { "source": "HEADER_NAME", "value": "Mid X" },
                    "y": { "source": "HEADER_NAME", "value": "Mid Y" },
                    "angle": { "source": "HEADER_NAME", "value": "Rotation" },
                    "side": { "source": "HEADER_NAME", "value": "Layer" }
                  },
                  "units": { "x": "mm", "y": "mm", "angle": "deg" },
                  "valueMappings": { "side": { "Top": "Top", "Bottom": "Bottom" } },
                  "warnings": []
                }
                """;
        return new StubLlmClient(stubJson);
    }

    private static LlmClient createOllamaClient() {
        var url = System.getProperty("llm.url", "http://localhost:11434");
        var model = System.getProperty("llm.model", "qwen2.5:3b");
        var options = new LlmOptions("ollama", url, model, 0);
        System.out.println("  Ollama: " + url + ", model: " + model);
        return new OllamaLlmClient(options);
    }

    /**
     * Check whether two configs match on key fields.
     */
    private static boolean configsMatch(PnpImportFormatConfig actual, PnpImportFormatConfig expected) {
        return actual.delimiter().equals(expected.delimiter())
                && actual.dataStartRowIndex() == expected.dataStartRowIndex()
                && actual.decimalSeparator().equals(expected.decimalSeparator())
                && actual.columns().keySet().equals(expected.columns().keySet());
    }

    /**
     * Find differences between actual and expected configs.
     */
    private static List<String> findDifferences(PnpImportFormatConfig actual, PnpImportFormatConfig expected) {
        var diffs = new ArrayList<String>();

        if (!actual.delimiter().equals(expected.delimiter())) {
            diffs.add("delimiter: expected '" + expected.delimiter() + "', got '" + actual.delimiter() + "'");
        }
        if (actual.dataStartRowIndex() != expected.dataStartRowIndex()) {
            diffs.add("dataStartRowIndex: expected " + expected.dataStartRowIndex()
                    + ", got " + actual.dataStartRowIndex());
        }
        if (!actual.decimalSeparator().equals(expected.decimalSeparator())) {
            diffs.add("decimalSeparator: expected '" + expected.decimalSeparator()
                    + "', got '" + actual.decimalSeparator() + "'");
        }
        if (!actual.columns().keySet().equals(expected.columns().keySet())) {
            diffs.add("column fields: expected " + expected.columns().keySet()
                    + ", got " + actual.columns().keySet());
        }
        if (actual.headerRowIndex() != null && expected.headerRowIndex() != null
                && !actual.headerRowIndex().equals(expected.headerRowIndex())) {
            diffs.add("headerRowIndex: expected " + expected.headerRowIndex()
                    + ", got " + actual.headerRowIndex());
        }
        if (!actual.linesToIgnore().equals(expected.linesToIgnore())) {
            diffs.add("linesToIgnore differ: expected " + expected.linesToIgnore()
                    + ", got " + actual.linesToIgnore());
        }
        if (!actual.columns().equals(expected.columns())) {
            diffs.add("column mappings differ");
        }

        return diffs;
    }

    private static List<Path> listExampleFiles() throws IOException {
        try (var files = Files.list(EXAMPLES_DIR)) {
            return files.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    private static String stemName(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0) ? filename.substring(0, dot) : filename;
    }
}
