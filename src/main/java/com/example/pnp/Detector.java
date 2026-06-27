package com.example.pnp;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Orchestrates the PnP import format detection workflow.
 * <p>
 * Given a raw numbered sample and an LLM client, the detector:
 * <ol>
 *   <li>Builds a prompt from the sample</li>
 *   <li>Sends the prompt to the LLM client</li>
 *   <li>Parses the response as {@link PnpImportFormatConfig}</li>
 *   <li>Validates the config</li>
 *   <li>Returns the result</li>
 * </ol>
 */
public class Detector {

    private final FormatDetectionPromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final PnpImportFormatConfigValidator validator;
    private final ObjectMapper objectMapper;

    public Detector(FormatDetectionPromptBuilder promptBuilder,
                    LlmClient llmClient,
                    PnpImportFormatConfigValidator validator,
                    ObjectMapper objectMapper) {
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    /**
     * Result of a detection attempt.
     *
     * @param config the parsed config (may be null if parsing failed)
     * @param errors list of validation/parsing errors (empty = success)
     */
    public record DetectionResult(
            PnpImportFormatConfig config,
            List<String> errors) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /**
     * Run detection on the given sample.
     *
     * @param sample the CSV sample
     * @return the detection result
     */
    public DetectionResult detect(SampleResult sample) {
        // 1. Build prompt
        var prompt = promptBuilder.build(sample);

        // 2. Send to LLM
        var response = llmClient.sendPrompt(prompt);

        // 3. Parse response
        PnpImportFormatConfig config;
        try {
            config = objectMapper.readValue(response, PnpImportFormatConfig.class);
        } catch (IOException e) {
            return new DetectionResult(null, List.of(
                    "Failed to parse LLM response as PnpImportFormatConfig: " + e.getMessage()));
        }

        // 4. Validate
        var errors = validator.validate(config);
        if (!errors.isEmpty()) {
            return new DetectionResult(config, errors);
        }

        // 5. Success
        return new DetectionResult(config, List.of());
    }
}
