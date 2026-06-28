package com.example.pnp;

import java.util.List;

/**
 * Dry-run report produced by the Stage 4 simple parser.
 * <p>
 * Contains summary counts, errors, warnings, available column headers,
 * and a sample of normalized placement rows.
 *
 * @param success           true if parsing completed without errors
 * @param totalCandidateRows number of rows considered for parsing (after bounds)
 * @param parsedRows        number of rows successfully parsed
 * @param rejectedRows      number of rows rejected due to errors
 * @param errors            list of error messages (row-level and general)
 * @param warnings          list of warning messages
 * @param availableColumns  list of available column names (from header row)
 * @param samplePlacements  sample of parsed placement rows (at most 10)
 */
public record PnpParseDryRunReport(
        boolean success,
        int totalCandidateRows,
        int parsedRows,
        int rejectedRows,
        List<String> errors,
        List<String> warnings,
        List<String> availableColumns,
        List<PnpPlacement> samplePlacements) {
}
