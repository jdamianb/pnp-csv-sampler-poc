package com.example.pnp;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds a repair prompt for the repair loop.
 * <p>
 * The repair prompt includes the original CSV sample, the broken config as JSON,
 * and the validation/parser error messages, asking the LLM to correct the config.
 */
public class RepairPromptBuilder {

    private static final String REPAIR_HEADER = """
            You previously proposed this PnP import configuration:
            
            """;

    private static final String ERRORS_HEADER = """
            
            That configuration failed with these errors:
            
            """;

    private static final String INSTRUCTION = """
            
            Here is the original CSV sample again:
            
            """;

    private static final String SEPARATOR_ANALYSIS_HEADER = """
            
            DETERMINISTIC SEPARATOR ANALYSIS:
            """;

    private static final String SEPARATOR_ANALYSIS_INSTRUCTION = """
            
            The previous config used a delimiter that may be incorrect.
            Review the separator analysis above and consider correcting the delimiter if the evidence supports it.
            """;

    private static final String FINAL_INSTRUCTION = """
            
            Please correct the configuration to fix these errors.
            Do NOT change the file content or component data — only fix the configuration fields
            (delimiter, column mappings, decimal separator, line indexes, units, etc.).
            
            Return ONLY valid JSON matching the expected schema from the original instructions.
            Do not include any explanation, markdown formatting, or code fences.
            """;

    private final SeparatorCandidateAnalyzer separatorAnalyzer;

    public RepairPromptBuilder() {
        this.separatorAnalyzer = new SeparatorCandidateAnalyzer();
    }

    /**
     * Build a repair prompt from the original sample, the broken config, and the errors.
     *
     * @param sample the original CSV sample
     * @param configJson the broken config as pretty-printed JSON string
     * @param errors the validation/parser error messages
     * @return the full repair prompt string
     */
    public String build(SampleResult sample, String configJson, List<String> errors) {
        var sampleLines = buildSampleLines(sample);
        var separatorJson = buildSeparatorAnalysis(sample);

        var prompt = new StringBuilder();
        prompt.append(REPAIR_HEADER.trim()).append("\n");
        prompt.append(configJson).append("\n");
        prompt.append(ERRORS_HEADER.trim()).append("\n");
        prompt.append(formatErrors(errors)).append("\n");

        // Include separator analysis for delimiter-related errors
        boolean hasDelimiterError = errors != null && errors.stream()
                .anyMatch(e -> e.toLowerCase().contains("delimiter")
                        || e.toLowerCase().contains("column")
                        || e.toLowerCase().contains("not found"));
        if (hasDelimiterError) {
            prompt.append(SEPARATOR_ANALYSIS_HEADER.trim()).append("\n");
            prompt.append(separatorJson).append("\n");
            prompt.append(SEPARATOR_ANALYSIS_INSTRUCTION.trim()).append("\n");
        }

        prompt.append(INSTRUCTION.trim()).append("\n");
        prompt.append(sampleLines).append("\n");
        prompt.append(FINAL_INSTRUCTION.trim());
        return prompt.toString();
    }

    private String buildSeparatorAnalysis(SampleResult sample) {
        try {
            var analysis = separatorAnalyzer.analyze(sample);
            return separatorAnalyzer.toJson(analysis);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String formatErrors(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "  (no specific errors)";
        }
        return errors.stream()
                .map(e -> "  - " + e)
                .collect(Collectors.joining("\n"));
    }

    private static String buildSampleLines(SampleResult sample) {
        var allLines = Stream.concat(
                sample.firstLines().stream(),
                sample.lastLines().stream()
        ).map(line -> "  [" + line.index() + "] " + line.text())
         .collect(Collectors.joining("\n"));

        return "Total lines: " + sample.totalLines() + "\n"
                + "Sampled lines:\n"
                + allLines;
    }
}
