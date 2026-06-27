package com.example.pnp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * CLI entry point for the PnP CSV sampler and format detector.
 * <p>
 * Usage:
 *   java ... com.example.pnp.Main sample &lt;file&gt; [--first N] [--last M]
 *   java ... com.example.pnp.Main detect &lt;file&gt; [--first N] [--last M]
 * <p>
 * If no command is given, defaults to sample mode for backward compatibility.
 */
public class Main {

    private static final int DEFAULT_FIRST = 80;
    private static final int DEFAULT_LAST = 20;

    public static void main(String[] args) {
        try {
            int exitCode = run(args);
            System.exit(exitCode);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args) {
        try {
            return runInternal(args);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static int runInternal(String[] args) throws IOException {
        if (args.length == 0) {
            printUsage();
            return 1;
        }

        // Determine command mode: "detect", "sample", or default to sample
        boolean detectMode = "detect".equals(args[0]);
        boolean explicitSampleMode = "sample".equals(args[0]);
        int argOffset = (detectMode || explicitSampleMode) ? 1 : 0;

        if (argOffset >= args.length) {
            System.err.println("Error: missing file path");
            printUsage();
            return 1;
        }

        String filePath = args[argOffset];
        int firstCount = DEFAULT_FIRST;
        int lastCount = DEFAULT_LAST;

        // Parse optional flags (start after command + file)
        int i = argOffset + 1;
        while (i < args.length) {
            switch (args[i]) {
                case "--first" -> {
                    i++;
                    if (i >= args.length) {
                        System.err.println("Error: --first requires a positive integer argument");
                        printUsage();
                        return 1;
                    }
                    try {
                        firstCount = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        System.err.println("Error: --first must be a positive integer, got '" + args[i] + "'");
                        return 1;
                    }
                    if (firstCount < 1) {
                        System.err.println("Error: --first must be >= 1, got " + firstCount);
                        return 1;
                    }
                }
                case "--last" -> {
                    i++;
                    if (i >= args.length) {
                        System.err.println("Error: --last requires a positive integer argument");
                        printUsage();
                        return 1;
                    }
                    try {
                        lastCount = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        System.err.println("Error: --last must be a positive integer, got '" + args[i] + "'");
                        return 1;
                    }
                    if (lastCount < 1) {
                        System.err.println("Error: --last must be >= 1, got " + lastCount);
                        return 1;
                    }
                }
                default -> {
                    System.err.println("Error: unknown option '" + args[i] + "'");
                    printUsage();
                    return 1;
                }
            }
            i++;
        }

        // Sample the file
        var sampler = new Sampler(firstCount, lastCount);
        SampleResult sampleResult;
        try (var reader = new InputStreamReader(
                new FileInputStream(filePath), StandardCharsets.UTF_8)) {
            sampleResult = sampler.sample(reader);
        }

        if (detectMode) {
            return runDetect(sampleResult);
        } else {
            return runSample(sampleResult);
        }
    }

    private static int runSample(SampleResult sampleResult) throws IOException {
        var mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(System.out, sampleResult);
        return 0;
    }

    private static int runDetect(SampleResult sampleResult) throws IOException {
        var promptBuilder = new FormatDetectionPromptBuilder();

        // Stage 2: use StubLlmClient with a default stub response
        // Stage 3: replace with a real LLM client
        var defaultStubJson = """
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

        var llmClient = new StubLlmClient(defaultStubJson);
        var validator = new PnpImportFormatConfigValidator();
        var objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        var detector = new Detector(promptBuilder, llmClient, validator, objectMapper);
        var result = detector.detect(sampleResult);

        if (result.isValid()) {
            objectMapper.writeValue(System.out, result.config());
            return 0;
        } else {
            System.err.println("Detection failed:");
            for (var error : result.errors()) {
                System.err.println("  - " + error);
            }
            return 1;
        }
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  java ... com.example.pnp.Main sample <file> [--first N] [--last M]");
        System.err.println("  java ... com.example.pnp.Main detect <file> [--first N] [--last M]");
        System.err.println();
        System.err.println("Commands:");
        System.err.println("  sample    Sample a PnP CSV file and output numbered lines as JSON");
        System.err.println("  detect    Detect PnP import format configuration via LLM");
        System.err.println();
        System.err.println("If no command is given, 'sample' is assumed for backward compatibility.");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  --first N     Number of lines to sample from the start (default: " + DEFAULT_FIRST + ")");
        System.err.println("  --last M      Number of lines to sample from the end (default: " + DEFAULT_LAST + ")");
    }
}
