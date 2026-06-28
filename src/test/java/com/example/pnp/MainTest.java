package com.example.pnp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Main} CLI entry point.
 */
class MainTest {

    @TempDir
    Path tempDir;

    // ============================================================
    //  Scenario: File not found
    // ============================================================

    @Test
    void fileNotFound() throws IOException {
        var exitCode = Main.run(new String[]{"/nonexistent/file.csv"});
        assertEquals(1, exitCode);
    }

    // ============================================================
    //  Scenario: Missing file argument
    // ============================================================

    @Test
    void missingFileArgument() throws IOException {
        var exitCode = Main.run(new String[]{});
        assertEquals(1, exitCode);
    }

    // ============================================================
    //  Scenario: Invalid --first value (negative)
    // ============================================================

    @Test
    void negativeFirstValue() throws IOException {
        var file = createTempFile("content");
        var exitCode = Main.run(new String[]{file.toString(), "--first", "-5"});
        assertEquals(1, exitCode);
    }

    // ============================================================
    //  Scenario: Invalid --first value (zero)
    // ============================================================

    @Test
    void zeroFirstValue() throws IOException {
        var file = createTempFile("content");
        var exitCode = Main.run(new String[]{file.toString(), "--first", "0"});
        assertEquals(1, exitCode);
    }

    // ============================================================
    //  Scenario: Invalid --last value (negative)
    // ============================================================

    @Test
    void negativeLastValue() throws IOException {
        var file = createTempFile("content");
        var exitCode = Main.run(new String[]{file.toString(), "--last", "-5"});
        assertEquals(1, exitCode);
    }

    // ============================================================
    //  Scenario: Invalid --last value (zero)
    // ============================================================

    @Test
    void zeroLastValue() throws IOException {
        var file = createTempFile("content");
        var exitCode = Main.run(new String[]{file.toString(), "--last", "0"});
        assertEquals(1, exitCode);
    }

    // ============================================================
    //  Scenario: --first without value
    // ============================================================

    @Test
    void firstFlagWithoutValue() throws IOException {
        var file = createTempFile("content");
        var exitCode = Main.run(new String[]{file.toString(), "--first"});
        assertEquals(1, exitCode);
    }

    // ============================================================
    //  Scenario: --last without value
    // ============================================================

    @Test
    void lastFlagWithoutValue() throws IOException {
        var file = createTempFile("content");
        var exitCode = Main.run(new String[]{file.toString(), "--last"});
        assertEquals(1, exitCode);
    }

    // ============================================================
    //  Scenario: --first with non-integer value
    // ============================================================

    @Test
    void firstFlagWithNonInteger() throws IOException {
        var file = createTempFile("content");
        var exitCode = Main.run(new String[]{file.toString(), "--first", "abc"});
        assertEquals(1, exitCode);
    }

    // ============================================================
    //  Scenario: unknown option
    // ============================================================

    @Test
    void unknownOption() throws IOException {
        var file = createTempFile("content");
        var exitCode = Main.run(new String[]{file.toString(), "--unknown"});
        assertEquals(1, exitCode);
    }

    // ============================================================
    //  Scenario: valid invocation produces exit code 0
    // ============================================================

    @Test
    void validInvocationReturnsZero(@TempDir Path tempDir) throws IOException {
        var file = createTempFile("line1\nline2\nline3\n");
        var exitCode = Main.run(new String[]{file.toString()});
        assertEquals(0, exitCode);
    }

    // ============================================================
    //  Scenario: valid invocation with custom counts
    // ============================================================

    @Test
    void validInvocationWithCustomCounts(@TempDir Path tempDir) throws IOException {
        var file = createTempFile(generateLines(50));
        var exitCode = Main.run(new String[]{file.toString(), "--first", "10", "--last", "5"});
        assertEquals(0, exitCode);
    }

    // ============================================================
    //  Stage 2: detect command
    // ============================================================

    @Test
    void detectCommandReturnsZero() throws IOException {
        var file = createTempFile("header\nR1,10k,0603\n");
        var exitCode = Main.run(new String[]{"detect", file.toString()});
        assertEquals(0, exitCode);
    }

    @Test
    void detectCommandWithCustomFlags() throws IOException {
        var file = createTempFile(generateLines(10));
        var exitCode = Main.run(new String[]{"detect", file.toString(), "--first", "5", "--last", "2"});
        assertEquals(0, exitCode);
    }

    @Test
    void detectCommandFileNotFound() throws IOException {
        var exitCode = Main.run(new String[]{"detect", "/nonexistent/detect-test.csv"});
        assertEquals(1, exitCode);
    }

    @Test
    void detectCommandMissingFileArg() throws IOException {
        var exitCode = Main.run(new String[]{"detect"});
        assertEquals(1, exitCode);
    }

    @Test
    void sampleCommandExplicit() throws IOException {
        var file = createTempFile("line1\nline2\n");
        var exitCode = Main.run(new String[]{"sample", file.toString()});
        assertEquals(0, exitCode);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Path createTempFile(String content) throws IOException {
        var file = tempDir.resolve("test.csv");
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(content);
        }
        return file;
    }

    private static String generateLines(int count) {
        var sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("Line ").append(i).append('\n');
        }
        return sb.toString();
    }

    // ============================================================
    //  Stage 3: --llm-* flags
    // ============================================================

    @Test
    void detectWithOllamaFlagsFailsConnection() throws IOException {
        var file = createTempFile("data\n");
        var exitCode = Main.run(new String[]{
                "detect", file.toString(),
                "--llm-provider", "ollama",
                "--llm-url", "http://localhost:18765",
                "--llm-model", "test-model"
        });
        assertEquals(1, exitCode);
    }

    @Test
    void detectWithoutLlmFlagsUsesStub() throws IOException {
        var file = createTempFile("header\nR1,10k\n");
        var exitCode = Main.run(new String[]{"detect", file.toString()});
        assertEquals(0, exitCode);
    }

    @Test
    void detectWithUnknownProvider() throws IOException {
        var file = createTempFile("data\n");
        var exitCode = Main.run(new String[]{
                "detect", file.toString(),
                "--llm-provider", "unknown-provider"
        });
        assertEquals(1, exitCode);
    }

    @Test
    void llmFlagsInSampleModeAllowed() throws IOException {
        var file = createTempFile("line1\nline2\n");
        var exitCode = Main.run(new String[]{
                "sample", file.toString(),
                "--llm-provider", "ollama"
        });
        assertEquals(0, exitCode);
    }

    @Test
    void detectWithInvalidTemperature() throws IOException {
        var file = createTempFile("data\n");
        var exitCode = Main.run(new String[]{
                "detect", file.toString(),
                "--llm-temperature", "abc"
        });
        assertEquals(1, exitCode);
    }

    @Test
    void detectWithMissingProviderValue() throws IOException {
        var file = createTempFile("data\n");
        var exitCode = Main.run(new String[]{
                "detect", file.toString(),
                "--llm-provider"
        });
        assertEquals(1, exitCode);
    }

    @Test
    void detectWithMissingUrlValue() throws IOException {
        var file = createTempFile("data\n");
        var exitCode = Main.run(new String[]{
                "detect", file.toString(),
                "--llm-provider", "ollama",
                "--llm-url"
        });
        assertEquals(1, exitCode);
    }

    @Test
    void detectWithMissingModelValue() throws IOException {
        var file = createTempFile("data\n");
        var exitCode = Main.run(new String[]{
                "detect", file.toString(),
                "--llm-provider", "ollama",
                "--llm-url", "http://localhost:11434",
                "--llm-model"
        });
        assertEquals(1, exitCode);
    }

    // ============================================================
    //  Stage 5: Repair loop CLI tests
    // ============================================================

    @Test
    void detectWithNoRepairFlag() throws IOException {
        var file = createTempFile("data\n");
        var exitCode = Main.run(new String[]{
                "detect", file.toString(),
                "--no-repair"
        });
        // Should run without repair — stub config is valid, so exit 0
        assertEquals(0, exitCode);
    }

    @Test
    void detectWithRepairMaxZero() throws IOException {
        var file = createTempFile("data\n");
        var exitCode = Main.run(new String[]{
                "detect", file.toString(),
                "--repair-max", "0"
        });
        // --repair-max 0 is equivalent to --no-repair
        assertEquals(0, exitCode);
    }

    @Test
    void detectWithRepairMaxNegative() throws IOException {
        var file = createTempFile("data\n");
        var exitCode = Main.run(new String[]{
                "detect", file.toString(),
                "--repair-max", "-1"
        });
        assertEquals(1, exitCode);
    }

    @Test
    void detectWithRepairMaxNonNumeric() throws IOException {
        var file = createTempFile("data\n");
        var exitCode = Main.run(new String[]{
                "detect", file.toString(),
                "--repair-max", "abc"
        });
        assertEquals(1, exitCode);
    }
}
