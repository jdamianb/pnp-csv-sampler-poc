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
 *   java ... com.example.pnp.Main parse &lt;file&gt; &lt;config-json&gt;
 *   java ... com.example.pnp.Main detect &lt;file&gt; [--first N] [--last M]
 *                                       [--llm-provider NAME] [--llm-url URL]
 *                                       [--llm-model MODEL] [--llm-temperature T]
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
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        } catch (LlmException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Parsed CLI options for the current invocation.
     */
    private static class CliOptions {
        boolean detectMode;
        boolean explicitSampleMode;
        boolean parseMode;
        String filePath;
        String configFilePath; // for parse command
        int firstCount = DEFAULT_FIRST;
        int lastCount = DEFAULT_LAST;
        String llmProvider;
        String llmUrl;
        String llmModel;
        Double llmTemperature;
    }

    private static int runInternal(String[] args) throws IOException {
        if (args.length == 0) {
            printUsage();
            return 1;
        }

        var cli = new CliOptions();

        // Determine command mode
        cli.parseMode = "parse".equals(args[0]);
        cli.detectMode = "detect".equals(args[0]);
        cli.explicitSampleMode = "sample".equals(args[0]);
        int argOffset = (cli.parseMode || cli.detectMode || cli.explicitSampleMode) ? 1 : 0;

        if (argOffset >= args.length) {
            System.err.println("Error: missing file path");
            printUsage();
            return 1;
        }

        cli.filePath = args[argOffset];

        // Parse command needs an additional config file argument
        if (cli.parseMode) {
            if (argOffset + 1 >= args.length) {
                System.err.println("Error: parse command requires a config JSON file path");
                printUsage();
                return 1;
            }
            cli.configFilePath = args[argOffset + 1];
            // No optional flags for parse mode; skip flag parsing
            return runParse(cli);
        }

        // Parse optional flags
        int i = argOffset + 1;
        while (i < args.length) {
            switch (args[i]) {
                case "--first" -> {
                    if (++i >= args.length) return missingArg("--first", "positive integer");
                    cli.firstCount = parseIntArg("--first", args[i]);
                    if (cli.firstCount < 1) {
                        System.err.println("Error: --first must be >= 1, got " + cli.firstCount);
                        return 1;
                    }
                }
                case "--last" -> {
                    if (++i >= args.length) return missingArg("--last", "positive integer");
                    cli.lastCount = parseIntArg("--last", args[i]);
                    if (cli.lastCount < 1) {
                        System.err.println("Error: --last must be >= 1, got " + cli.lastCount);
                        return 1;
                    }
                }
                case "--llm-provider" -> {
                    if (++i >= args.length) return missingArg("--llm-provider", "provider name");
                    cli.llmProvider = args[i];
                }
                case "--llm-url" -> {
                    if (++i >= args.length) return missingArg("--llm-url", "URL");
                    cli.llmUrl = args[i];
                }
                case "--llm-model" -> {
                    if (++i >= args.length) return missingArg("--llm-model", "model name");
                    cli.llmModel = args[i];
                }
                case "--llm-temperature" -> {
                    if (++i >= args.length) return missingArg("--llm-temperature", "number");
                    try {
                        cli.llmTemperature = Double.parseDouble(args[i]);
                    } catch (NumberFormatException e) {
                        System.err.println("Error: --llm-temperature must be a number, got '" + args[i] + "'");
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
        var sampler = new Sampler(cli.firstCount, cli.lastCount);
        SampleResult sampleResult;
        try (var reader = new InputStreamReader(
                new FileInputStream(cli.filePath), StandardCharsets.UTF_8)) {
            sampleResult = sampler.sample(reader);
        }

        if (cli.detectMode) {
            return runDetect(sampleResult, cli);
        } else {
            return runSample(sampleResult);
        }
    }

    private static int missingArg(String flag, String expected) {
        System.err.println("Error: " + flag + " requires a " + expected + " argument");
        printUsage();
        return 1;
    }

    private static int parseIntArg(String flag, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    flag + " must be a positive integer, got '" + value + "'");
        }
    }

    private static int runSample(SampleResult sampleResult) throws IOException {
        var mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(System.out, sampleResult);
        return 0;
    }

    private static int runDetect(SampleResult sampleResult, CliOptions cli) throws IOException {
        var promptBuilder = new FormatDetectionPromptBuilder();
        var validator = new PnpImportFormatConfigValidator();
        var objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        // Build LLM options: CLI args override environment variables
        var llmOptions = buildLlmOptions(cli);

        // Create the appropriate LLM client
        var defaultStubJson = getDefaultStubJson();
        var factory = new LlmClientFactory(defaultStubJson);
        LlmClient llmClient;
        try {
            llmClient = factory.create(llmOptions);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }

        var detector = new Detector(promptBuilder, llmClient, validator, objectMapper);
        var result = detector.detect(sampleResult);

        if (result.isValid()) {
            objectMapper.writeValue(System.out, result.config());
            return 0;
        } else {
            // Check if this was an LlmException (caught by Detector as parse error)
            System.err.println("Detection failed:");
            for (var error : result.errors()) {
                System.err.println("  - " + error);
            }
            return 1;
        }
    }

    /**
     * Build LlmOptions from CLI args and environment variables.
     * CLI args take precedence over environment variables.
     */
    private static LlmOptions buildLlmOptions(CliOptions cli) {
        var provider = cli.llmProvider != null
                ? cli.llmProvider
                : System.getenv("PNP_LLM_PROVIDER");

        var baseUrl = cli.llmUrl != null
                ? cli.llmUrl
                : System.getenv("PNP_LLM_BASE_URL");

        var model = cli.llmModel != null
                ? cli.llmModel
                : System.getenv("PNP_LLM_MODEL");

        double temperature = LlmOptions.DEFAULT_TEMPERATURE;
        if (cli.llmTemperature != null) {
            temperature = cli.llmTemperature;
        } else {
            var envTemp = System.getenv("PNP_LLM_TEMPERATURE");
            if (envTemp != null && !envTemp.isBlank()) {
                try {
                    temperature = Double.parseDouble(envTemp);
                } catch (NumberFormatException ignored) {
                    // fall back to default
                }
            }
        }

        return new LlmOptions(provider, baseUrl, model, temperature);
    }

    /**
     * Run the Stage 4 simple parser dry-run.
     * <p>
     * Reads the input file and config JSON, validates the config,
     * runs the parser, and prints the dry-run report.
     */
    private static int runParse(CliOptions cli) throws IOException {
        var mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        // Read and parse config JSON
        PnpImportFormatConfig config;
        try {
            config = mapper.readValue(
                    new java.io.File(cli.configFilePath), PnpImportFormatConfig.class);
        } catch (IOException e) {
            System.err.println("Error: failed to read config file '"
                    + cli.configFilePath + "': " + e.getMessage());
            return 1;
        }

        // Validate config
        var validator = new PnpImportFormatConfigValidator();
        var validationErrors = validator.validate(config);
        if (!validationErrors.isEmpty()) {
            System.err.println("Config validation failed:");
            for (var err : validationErrors) {
                System.err.println("  - " + err);
            }
            return 1;
        }

        // Run parser
        var parser = new SimplePnpParser();
        PnpParseDryRunReport report;
        try (var reader = new InputStreamReader(
                new FileInputStream(cli.filePath), StandardCharsets.UTF_8)) {
            report = parser.parse(reader, config);
        }

        mapper.writeValue(System.out, report);
        return report.success() ? 0 : 1;
    }

    /**
     * Default stub JSON response used when no real LLM provider is configured.
     */
    static String getDefaultStubJson() {
        return """
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
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  java ... com.example.pnp.Main sample <file> [--first N] [--last M]");
        System.err.println("  java ... com.example.pnp.Main parse <file> <config-json>");
        System.err.println("  java ... com.example.pnp.Main detect <file> [--first N] [--last M]");
        System.err.println("                       [--llm-provider NAME] [--llm-url URL]");
        System.err.println("                       [--llm-model MODEL] [--llm-temperature T]");
        System.err.println();
        System.err.println("Commands:");
        System.err.println("  sample    Sample a PnP CSV file and output numbered lines as JSON");
        System.err.println("  parse     Parse a PnP file using a config JSON and output dry-run report");
        System.err.println("  detect    Detect PnP import format configuration via LLM");
        System.err.println();
        System.err.println("If no command is given, 'sample' is assumed for backward compatibility.");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  --first N     Number of lines to sample from the start (default: " + DEFAULT_FIRST + ")");
        System.err.println("  --last M      Number of lines to sample from the end (default: " + DEFAULT_LAST + ")");
        System.err.println();
        System.err.println("LLM options (detect mode only):");
        System.err.println("  --llm-provider NAME       LLM provider (e.g., ollama)");
        System.err.println("  --llm-url URL             LLM endpoint URL");
        System.err.println("  --llm-model MODEL         LLM model name");
        System.err.println("  --llm-temperature T       Temperature (default: 0)");
        System.err.println();
        System.err.println("Environment variables (overridden by CLI args):");
        System.err.println("  PNP_LLM_PROVIDER");
        System.err.println("  PNP_LLM_BASE_URL");
        System.err.println("  PNP_LLM_MODEL");
        System.err.println("  PNP_LLM_TEMPERATURE");
        System.err.println();
        System.err.println("If no LLM provider is configured, StubLlmClient is used.");
    }
}
