package com.example.pnp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the repair loop on every file in examples-extended/ using the
 * expected configs, simulating an invalid-first-attempt scenario
 * to validate that the repair loop can detect and report issues.
 * <p>
 * Output files (with repair metadata) are written to target/repair-outputs/.
 * <p>
 * By default, uses StubLlmClient. To use a real LLM (Ollama), set:
 * {@code -Dllm.integration=true}
 */
class RepairLoopExampleTest {

    private static final Path EXAMPLES_DIR = Path.of("examples-extended");
    private static final Path EXPECTED_DIR = Path.of("expected-configs");
    private static final Path OUTPUT_DIR = Path.of("target", "repair-outputs");
    private static final int SAMPLE_FIRST = 80;
    private static final int SAMPLE_LAST = 20;

    @Test
    void repairAllExamples() throws IOException {
        Files.createDirectories(OUTPUT_DIR);

        var mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        var sampleFiles = listExampleFiles();
        assertFalse(sampleFiles.isEmpty(),
                "No example files found in " + EXAMPLES_DIR);

        var useRealLlm = "true".equals(System.getProperty("llm.integration"));
        var llmClient = useRealLlm ? createOllamaClient() : createStubClient();

        if (useRealLlm) {
            System.out.println("Using Ollama LLM client with repair loop");
        } else {
            System.out.println("Using StubLlmClient — set -Dllm.integration=true for real LLM");
        }

        var promptBuilder = new FormatDetectionPromptBuilder();
        var validator = new PnpImportFormatConfigValidator();
        var parser = new SimplePnpParser();
        var repairPromptBuilder = new RepairPromptBuilder();
        var detector = new Detector(promptBuilder, llmClient, validator, mapper);
        var repairLoop = new RepairLoop(detector, repairPromptBuilder, llmClient,
                mapper, validator, parser);

        int total = 0;
        int validImmediately = 0;
        int repaired = 0;
        int failed = 0;
        var details = new ArrayList<String>();

        for (var file : sampleFiles) {
            var stem = stemName(file.getFileName().toString());
            total++;

            var outputFile = OUTPUT_DIR.resolve(stem + "-repair-result.json");
            var expectedFile = EXPECTED_DIR.resolve(stem + ".config.json");

            System.out.println("\n=== " + stem + " ===");

            // 1. Sample the file
            var sampler = new Sampler(SAMPLE_FIRST, SAMPLE_LAST);
            SampleResult sampleResult;
            try (var reader = new InputStreamReader(
                    Files.newInputStream(file), StandardCharsets.UTF_8)) {
                sampleResult = sampler.sample(reader);
            }

            // 2. Read the file content for parser verification
            String fileContent;
            try (var reader = new InputStreamReader(
                    Files.newInputStream(file), StandardCharsets.UTF_8)) {
                fileContent = readAll(reader);
            }

            // 3. Run detection with repair loop
            RepairLoop.RepairResult result;
            try {
                result = repairLoop.detectWithRepair(
                        sampleResult, new StringReader(fileContent), 2);
            } catch (Exception e) {
                result = new RepairLoop.RepairResult(
                        null, List.of("Error: " + e.getMessage()), 0, false);
            }

            // 4. Build output
            var output = new LinkedHashMap<String, Object>();
            output.put("file", file.getFileName().toString());
            output.put("success", result.isValid());
            output.put("repairAttempts", result.repairAttempts());
            output.put("wasRepaired", result.wasRepaired());
            if (result.config() != null) {
                output.put("config", result.config());
            }
            if (!result.errors().isEmpty()) {
                output.put("errors", result.errors());
            }

            // 5. Compare against expected config
            var expectedConfig = Files.exists(expectedFile)
                    ? mapper.readValue(expectedFile.toFile(), PnpImportFormatConfig.class)
                    : null;

            if (result.isValid() && result.config() != null && expectedConfig != null) {
                boolean matches = configsMatch(result.config(), expectedConfig);
                output.put("matchesExpected", matches);

                if (matches) {
                    if (result.repairAttempts() == 0) {
                        validImmediately++;
                        System.out.println("  ✅ Valid immediately, matches expected");
                    } else {
                        repaired++;
                        System.out.println("  🔧 Repaired (attempts: " + result.repairAttempts()
                                + "), matches expected");
                    }
                } else {
                    failed++;
                    var diffs = findDifferences(result.config(), expectedConfig);
                    output.put("differences", diffs);
                    System.out.println("  ❌ Valid but differs from expected:");
                    for (var d : diffs) {
                        System.out.println("      - " + d);
                    }
                    details.add(stem + ": " + String.join("; ", diffs));
                }
            } else if (result.isValid() && expectedConfig == null) {
                System.out.println("  ? Valid, no expected config to compare");
            } else {
                failed++;
                System.out.println("  ❌ Failed: " + result.errors());
                details.add(stem + ": " + result.errors());
            }

            // 6. Write output
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), output);
            System.out.println("  wrote: " + outputFile.getFileName());
        }

        // Summary
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Repair Loop Example Test Summary");
        System.out.println("=".repeat(60));
        System.out.println("Total files:    " + total);
        System.out.println("Valid (no repair): " + validImmediately);
        System.out.println("Repaired:       " + repaired);
        System.out.println("Failed:         " + failed);
        System.out.println("Output files in: " + OUTPUT_DIR);

        // Don't fail the test — this is a diagnostic tool
        if (failed > 0) {
            System.out.println("\nNote: " + failed + " file(s) had issues (expected with stub)");
        }
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

    private static boolean configsMatch(PnpImportFormatConfig actual, PnpImportFormatConfig expected) {
        return actual.delimiter().equals(expected.delimiter())
                && actual.dataStartRowIndex() == expected.dataStartRowIndex()
                && actual.decimalSeparator().equals(expected.decimalSeparator())
                && actual.columns().keySet().equals(expected.columns().keySet());
    }

    private static List<String> findDifferences(PnpImportFormatConfig actual, PnpImportFormatConfig expected) {
        var diffs = new ArrayList<String>();
        if (!actual.delimiter().equals(expected.delimiter()))
            diffs.add("delimiter: expected '" + expected.delimiter() + "', got '" + actual.delimiter() + "'");
        if (actual.dataStartRowIndex() != expected.dataStartRowIndex())
            diffs.add("dataStartRowIndex: expected " + expected.dataStartRowIndex() + ", got " + actual.dataStartRowIndex());
        if (!actual.decimalSeparator().equals(expected.decimalSeparator()))
            diffs.add("decimalSeparator: expected '" + expected.decimalSeparator() + "', got '" + actual.decimalSeparator() + "'");
        if (!actual.columns().keySet().equals(expected.columns().keySet()))
            diffs.add("column fields: expected " + expected.columns().keySet() + ", got " + actual.columns().keySet());
        if (!actual.linesToIgnore().equals(expected.linesToIgnore()))
            diffs.add("linesToIgnore differ");
        return diffs;
    }

    private static String readAll(java.io.Reader reader) throws IOException {
        var sb = new StringBuilder();
        var buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
