package com.example.pnp;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * Bounded repair loop that wraps a {@link Detector} with automatic config correction.
 * <p>
 * When detection fails (validation or parser errors), the repair loop:
 * <ol>
 *   <li>Builds a repair prompt with the sample, broken config, and errors</li>
 *   <li>Sends the repair prompt to the same LLM</li>
 *   <li>Parses and validates the corrected config</li>
 *   <li>Runs the parser dry-run for verification</li>
 *   <li>Repeats up to maxRetries times</li>
 * </ol>
 * <p>
 * Stub-aware: if the LLM returns an identical JSON response, the loop stops early.
 */
public class RepairLoop {

    private final Detector detector;
    private final RepairPromptBuilder repairPromptBuilder;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final PnpImportFormatConfigValidator validator;
    private final SimplePnpParser parser;

    public RepairLoop(Detector detector,
                      RepairPromptBuilder repairPromptBuilder,
                      LlmClient llmClient,
                      ObjectMapper objectMapper,
                      PnpImportFormatConfigValidator validator,
                      SimplePnpParser parser) {
        this.detector = detector;
        this.repairPromptBuilder = repairPromptBuilder;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.parser = parser;
    }

    /**
     * Result of a detection with repair.
     *
     * @param config        the final config (may be null if all attempts failed)
     * @param errors        list of errors (empty = success)
     * @param repairAttempts number of repair attempts made (0 = no repair needed)
     * @param wasRepaired   true if the config was corrected by the repair loop
     */
    public record RepairResult(
            PnpImportFormatConfig config,
            List<String> errors,
            int repairAttempts,
            boolean wasRepaired) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /**
     * Run detection with repair loop.
     *
     * @param sample     the CSV sample
     * @param fileContent a Reader providing the full file content (for parser dry-run)
     * @param maxRetries maximum number of repair attempts (0 = no repair)
     * @return the repair result
     */
    public RepairResult detectWithRepair(SampleResult sample, Reader fileContent, int maxRetries) {
        // 1. First attempt: normal detection
        var detectResult = detector.detect(sample);

        // Track the last LLM response string for stub detection
        String previousResponseJson = null;

        // Check if the config is valid and passes the parser
        var currentResult = checkWithParser(detectResult, fileContent);
        if (currentResult.isValid()) {
            return new RepairResult(currentResult.config(), List.of(), 0, false);
        }

        // 2. Repair loop
        PnpImportFormatConfig currentConfig = currentResult.config();
        List<String> currentErrors = currentResult.errors();

        var configJsonWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            // Serialize current config to JSON for the repair prompt
            String configJson;
            try {
                configJson = configJsonWriter.writeValueAsString(currentConfig);
            } catch (IOException e) {
                currentErrors = List.of("Failed to serialize config: " + e.getMessage());
                continue;
            }

            // Build repair prompt
            var repairPrompt = repairPromptBuilder.build(sample, configJson, currentErrors);
            System.err.println("[RepairLoop] Attempt " + (attempt + 1) + "/" + maxRetries);
            System.err.println("[RepairLoop] Errors to fix: " + currentErrors);
            System.err.println("[RepairLoop] Repair prompt (first 300 chars):");
            System.err.println(repairPrompt.substring(0, Math.min(300, repairPrompt.length())) + "...");
            System.err.println("[RepairLoop] --- end of prompt excerpt ---");

            // Send to LLM
            String response;
            try {
                response = llmClient.sendPrompt(repairPrompt);
                System.err.println("[RepairLoop] LLM response (first 200 chars):");
                System.err.println(response.substring(0, Math.min(200, response.length())) + "...");
                System.err.println("[RepairLoop] --- end of response excerpt ---");
            } catch (LlmException e) {
                System.err.println("[RepairLoop] LLM error: " + e.getMessage());
                currentErrors = List.of("LLM error during repair: " + e.getMessage());
                continue;
            }

            // Check for stub (identical response)
            if (response.equals(previousResponseJson)) {
                // LLM returned the same response — no improvement possible
                return new RepairResult(currentConfig, currentErrors, attempt + 1, false);
            }
            previousResponseJson = response;

            // Parse response
            PnpImportFormatConfig correctedConfig;
            try {
                correctedConfig = objectMapper.readValue(response, PnpImportFormatConfig.class);
                currentConfig = correctedConfig;
            } catch (IOException e) {
                currentErrors = List.of("Failed to parse repair response as config: " + e.getMessage());
                continue;
            }

            // Validate
            var validationErrors = validator.validate(correctedConfig);
            if (!validationErrors.isEmpty()) {
                currentErrors = validationErrors;
                continue;
            }

            // Parser check
            var parserResult = checkParser(correctedConfig, fileContent);
            if (!parserResult.errors().isEmpty()) {
                currentErrors = parserResult.errors();
                continue;
            }

            // Success!
            return new RepairResult(correctedConfig, List.of(), attempt + 1, true);
        }

        // Exhausted retries
        return new RepairResult(currentConfig, currentErrors, maxRetries, false);
    }

    /**
     * Check a detection result with the parser dry-run.
     * Returns the detection result as-is if invalid, or runs the parser if valid.
     */
    private Detector.DetectionResult checkWithParser(
            Detector.DetectionResult detectResult, Reader fileContent) {
        if (!detectResult.isValid() || detectResult.config() == null) {
            return detectResult;
        }
        return checkParser(detectResult.config(), fileContent);
    }

    /**
     * Run the parser on a config and return parser errors (or empty if success).
     */
    private Detector.DetectionResult checkParser(PnpImportFormatConfig config, Reader fileContent) {
        try {
            // Re-read the file (the reader may have been consumed)
            var fileContentStr = readAll(fileContent);
            var report = parser.parse(new java.io.StringReader(fileContentStr), config);

            if (report.success()) {
                return new Detector.DetectionResult(config, List.of());
            } else {
                return new Detector.DetectionResult(config, report.errors());
            }
        } catch (Exception e) {
            return new Detector.DetectionResult(config,
                    List.of("Parser error: " + e.getMessage()));
        }
    }

    /**
     * Read all content from a Reader into a String.
     */
    private static String readAll(Reader reader) throws IOException {
        var sb = new StringBuilder();
        var buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
