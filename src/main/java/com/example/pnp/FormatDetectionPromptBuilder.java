package com.example.pnp;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds an LLM prompt from a {@link SampleResult} (CsvSample).
 * <p>
 * The prompt instructs the LLM to act as a PnP import format configuration assistant,
 * not as a CSV parser. It includes the raw numbered sample lines and describes
 * the expected JSON output schema.
 */
public class FormatDetectionPromptBuilder {

    private static final String SYSTEM_CONTEXT = """
            You are a PnP (Pick-and-Place) import format configuration assistant.
            
            Your role is to replace the manual configuration step that a human would perform.
            
            Rules:
            - The existing CSV parser remains the authority. Do NOT parse the file yourself.
            - Do NOT extract, normalize, or transform component data.
            - Do NOT rewrite or modify the CSV content.
            - Do NOT invent component data.
            - Only propose the parser configuration (delimiter, columns, units, etc.).
            
            Return ONLY valid JSON that matches the expected schema.
            Do not include any explanation, markdown formatting, or code fences.
            """;

    private static final String SCHEMA_INSTRUCTION = """
            Expected JSON schema:
            {
              "schemaVersion": 1,
              "confidence": <0.0 to 1.0>,
              "delimiter": "<field delimiter character>",
              "quoteChar": "<quote character or empty>",
              "encoding": "UTF-8",
              "linesToIgnore": [<zero-based line indexes to skip>],
              "headerRowIndex": <zero-based header row index or null>,
              "dataStartRowIndex": <zero-based first data row index>,
              "dataEndRowIndex": <zero-based last data row index or null>,
              "decimalSeparator": "." or ",",
              "columns": {
                "reference": { "source": "HEADER_NAME", "value": "<column header>" },
                "partNumber": { "source": "HEADER_NAME", "value": "<column header>" },
                "jedec": { "source": "HEADER_NAME", "value": "<column header>" },
                "x": { "source": "HEADER_NAME", "value": "<column header>" },
                "y": { "source": "HEADER_NAME", "value": "<column header>" },
                "angle": { "source": "HEADER_NAME", "value": "<column header>" },
                "side": { "source": "HEADER_NAME", "value": "<column header>" }
              },
              "units": {
                "x": "mm", "y": "mm", "angle": "deg"
              },
              "valueMappings": {
                "side": { "<raw>": "<normalized>" }
              },
              "warnings": []
            }
            
            If a header row exists, use HEADER_NAME source.
            If no reliable header exists, use COLUMN_INDEX source with zero-based index.
            The "side" field is optional.
            """;

    private static final String SAMPLE_HEADER = """
            Below is the raw numbered sample from the PnP file.
            Each line shows its zero-based index followed by the exact raw text.
            
            """;

    /**
     * Build a prompt from the given sample.
     *
     * @param sample the CSV sample produced by the sampler
     * @return the full prompt string
     */
    public String build(SampleResult sample) {
        var sampleLines = buildSampleLines(sample);

        return SYSTEM_CONTEXT.trim() + "\n\n"
                + SCHEMA_INSTRUCTION.trim() + "\n\n"
                + SAMPLE_HEADER.trim() + "\n"
                + sampleLines + "\n\n"
                + "Return ONLY the JSON configuration object.";
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
