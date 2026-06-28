package com.example.pnp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic analyzer that inspects sampled raw lines to compute
 * delimiter candidates and their confidence scores.
 * <p>
 * The analysis is injected into the LLM prompt as strong evidence,
 * helping the LLM avoid blind guessing of delimiter type.
 */
public class SeparatorCandidateAnalyzer {

    private static final List<String> CANDIDATE_DELIMITERS = List.of(",", ";", "\t", "WHITESPACE");
    private static final double MIN_CONFIDENCE = 0.3;

    // PnP header keywords for column matching
    private static final Set<String> REF_KEYWORDS = Set.of(
            "ref", "refdes", "designator", "design", "component", "item");
    private static final Set<String> PART_KEYWORDS = Set.of(
            "part", "partno", "pn", "partnumber", "componentid", "value", "comment");
    private static final Set<String> PACKAGE_KEYWORDS = Set.of(
            "package", "footprint", "jedec", "pattern");
    private static final Set<String> X_KEYWORDS = Set.of(
            "x", "posx", "x-pos", "x_pos", "mid x", "midx", "centerx", "x(mm)", "x (mm)");
    private static final Set<String> Y_KEYWORDS = Set.of(
            "y", "posy", "y-pos", "y_pos", "mid y", "midy", "centery", "y(mm)", "y (mm)");
    private static final Set<String> ANGLE_KEYWORDS = Set.of(
            "angle", "rotation", "rot", "theta", "r(deg)", "rotate");
    private static final Set<String> SIDE_KEYWORDS = Set.of(
            "side", "layer", "mountside", "boardside");

    private static final Set<String> ALL_KEYWORDS = new HashSet<>();
    static {
        ALL_KEYWORDS.addAll(REF_KEYWORDS);
        ALL_KEYWORDS.addAll(PART_KEYWORDS);
        ALL_KEYWORDS.addAll(PACKAGE_KEYWORDS);
        ALL_KEYWORDS.addAll(X_KEYWORDS);
        ALL_KEYWORDS.addAll(Y_KEYWORDS);
        ALL_KEYWORDS.addAll(ANGLE_KEYWORDS);
        ALL_KEYWORDS.addAll(SIDE_KEYWORDS);
    }

    public record SeparatorCandidate(
            @JsonProperty("delimiter") String delimiter,
            @JsonProperty("nonEmptyAnalyzedLines") int nonEmptyAnalyzedLines,
            @JsonProperty("linesWithAtLeastTwoColumns") int linesWithAtLeastTwoColumns,
            @JsonProperty("maxColumnCount") int maxColumnCount,
            @JsonProperty("averageColumnCount") double averageColumnCount,
            @JsonProperty("mostCommonColumnCount") int mostCommonColumnCount,
            @JsonProperty("mostCommonColumnCountFrequency") int mostCommonColumnCountFrequency,
            @JsonProperty("columnCountConsistencyRatio") double columnCountConsistencyRatio,
            @JsonProperty("candidateHeaderRowIndex") int candidateHeaderRowIndex,
            @JsonProperty("candidateDataStartRowIndex") int candidateDataStartRowIndex,
            @JsonProperty("sampleHeaderColumns") List<String> sampleHeaderColumns,
            @JsonProperty("likelyDecimalSeparator") String likelyDecimalSeparator,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("warnings") List<String> warnings) {
    }

    public record SeparatorAnalysis(
            @JsonProperty("delimiterCandidates") List<SeparatorCandidate> candidates,
            @JsonProperty("recommendedDelimiter") String recommendedDelimiter,
            @JsonProperty("recommendedDecimalSeparator") String recommendedDecimalSeparator) {
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Analyze the given sample for delimiter candidates.
     *
     * @param sample the CSV sample from the sampler
     * @return separator analysis with all candidates and recommendations
     */
    public SeparatorAnalysis analyze(SampleResult sample) {
        var allLines = new ArrayList<SampleLine>();
        for (var line : sample.firstLines()) {
            allLines.add(new SampleLine(line.index(), line.text()));
        }
        for (var line : sample.lastLines()) {
            // Avoid duplicates
            if (allLines.stream().noneMatch(l -> l.index == line.index())) {
                allLines.add(new SampleLine(line.index(), line.text()));
            }
        }
        return analyze(allLines);
    }

    /**
     * Analyze a list of raw lines for delimiter candidates.
     */
    public SeparatorAnalysis analyze(List<SampleLine> lines) {
        var candidates = new ArrayList<SeparatorCandidate>();

        for (String delim : CANDIDATE_DELIMITERS) {
            var candidate = analyzeDelimiter(lines, delim);
            candidates.add(candidate);
        }

        // Sort by confidence descending
        candidates.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));

        String recommendedDelimiter = ","; // fallback
        String recommendedDecimalSeparator = ".";
        double topConfidence = 0.0;
        double secondConfidence = 0.0;

        if (!candidates.isEmpty()) {
            var top = candidates.get(0);
            topConfidence = top.confidence();
            if (topConfidence >= MIN_CONFIDENCE) {
                recommendedDelimiter = top.delimiter();
                recommendedDecimalSeparator = top.likelyDecimalSeparator();
            }
            if (candidates.size() > 1) {
                secondConfidence = candidates.get(1).confidence();
            }
        }

        // If comma has low confidence and something else has much higher, prefer that
        // This is already handled by the sort.

        return new SeparatorAnalysis(candidates, recommendedDelimiter, recommendedDecimalSeparator);
    }

    private SeparatorCandidate analyzeDelimiter(List<SampleLine> lines, String delimiter) {
        var warnings = new ArrayList<String>();
        boolean isWhitespace = "WHITESPACE".equals(delimiter);

        // Split each line by delimiter
        var splitLines = new ArrayList<SplitLine>();
        for (var line : lines) {
            String[] parts;
            if (isWhitespace) {
                parts = line.text.trim().split("\\s+");
            } else {
                parts = line.text.split(escapeDelimiter(delimiter), -1);
            }
            splitLines.add(new SplitLine(line.index, line.text, parts));
        }

        // Filter non-empty analyzed lines (lines with at least some content)
        var nonEmptyLines = splitLines.stream()
                .filter(sl -> sl.text.trim().length() > 0)
                .collect(Collectors.toList());

        int nonEmptyAnalyzedLines = nonEmptyLines.size();

        // Lines with at least 2 columns
        var linesWith2Cols = nonEmptyLines.stream()
                .filter(sl -> sl.parts.length >= 2)
                .collect(Collectors.toList());
        int linesWithAtLeastTwoColumns = linesWith2Cols.size();

        if (linesWith2Cols.isEmpty()) {
            return new SeparatorCandidate(delimiter, nonEmptyAnalyzedLines, 0, 0, 0.0, 0, 0, 0.0,
                    -1, -1, List.of(), ".", 0.0, warnings);
        }

        // Compute column count statistics for lines with at least 2 columns
        var colCounts = linesWith2Cols.stream()
                .mapToInt(sl -> sl.parts.length)
                .toArray();

        int maxColCount = Arrays.stream(colCounts).max().orElse(0);
        double avgColCount = Arrays.stream(colCounts).average().orElse(0.0);

        // Find most common column count
        var freqMap = new HashMap<Integer, Integer>();
        for (int cc : colCounts) {
            freqMap.merge(cc, 1, Integer::sum);
        }
        int mostCommonColCount = 0;
        int mostCommonFreq = 0;
        for (var entry : freqMap.entrySet()) {
            if (entry.getValue() > mostCommonFreq) {
                mostCommonFreq = entry.getValue();
                mostCommonColCount = entry.getKey();
            }
        }

        // Consistency ratio: percentage of lines matching the most common column count
        double consistencyRatio = mostCommonFreq / (double) linesWith2Cols.size();

        // Find candidate header row and data start row
        int candidateHeaderRowIndex = -1;
        int candidateDataStartRowIndex = -1;
        List<String> sampleHeaderColumns = List.of();

        // Look for a row with plausible PnP header keywords
        for (int i = 0; i < linesWith2Cols.size(); i++) {
            var sl = linesWith2Cols.get(i);
            var partsLower = Arrays.stream(sl.parts)
                    .map(p -> p.trim().toLowerCase())
                    .collect(Collectors.toList());

            int keywordMatches = 0;
            for (String p : partsLower) {
                if (ALL_KEYWORDS.contains(p)) {
                    keywordMatches++;
                }
            }

            // A header row should have at least 2 keyword matches OR contain X/Y/angle keywords
            boolean hasXLike = partsLower.stream().anyMatch(p -> X_KEYWORDS.contains(p));
            boolean hasYLike = partsLower.stream().anyMatch(p -> Y_KEYWORDS.contains(p));
            boolean hasAngleLike = partsLower.stream().anyMatch(p -> ANGLE_KEYWORDS.contains(p));
            boolean hasRefLike = partsLower.stream().anyMatch(p -> REF_KEYWORDS.contains(p));

            if (keywordMatches >= 2 || (hasRefLike && (hasXLike || hasYLike))) {
                candidateHeaderRowIndex = sl.index;
                sampleHeaderColumns = partsLower.stream()
                        .map(p -> capitalizeFirst(p))
                        .collect(Collectors.toList());

                // Data starts at the next line with consistent column count
                for (int j = i + 1; j < linesWith2Cols.size(); j++) {
                    var dataSl = linesWith2Cols.get(j);
                    if (dataSl.parts.length == mostCommonColCount) {
                        candidateDataStartRowIndex = dataSl.index;
                        break;
                    }
                }
                break;
            }
        }

        // If no header row found, use the first non-metadata line with most common column count
        if (candidateHeaderRowIndex == -1) {
            for (int i = 0; i < linesWith2Cols.size(); i++) {
                var sl = linesWith2Cols.get(i);
                if (sl.parts.length == mostCommonColCount) {
                    candidateHeaderRowIndex = sl.index;
                    sampleHeaderColumns = Arrays.stream(sl.parts)
                            .map(p -> p.trim())
                            .collect(Collectors.toList());
                    // Data starts at next line with same column count
                    for (int j = i + 1; j < linesWith2Cols.size(); j++) {
                        var dataSl = linesWith2Cols.get(j);
                        if (dataSl.parts.length == mostCommonColCount) {
                            candidateDataStartRowIndex = dataSl.index;
                            break;
                        }
                    }
                    break;
                }
            }
        }

        // Detect decimal separator: check if numeric-looking values contain comma decimals
        String likelyDecimalSeparator = ".";
        if (candidateDataStartRowIndex >= 0 || candidateHeaderRowIndex >= 0) {
            boolean foundCommaDecimal = false;
            boolean foundDotDecimal = false;
            for (var sl : linesWith2Cols) {
                for (String part : sl.parts) {
                    var trimmed = part.trim();
                    // Look for patterns like 12,5 (European decimal) or 12.5 (decimal)
                    if (trimmed.matches("\\d+,\\d+")) {
                        foundCommaDecimal = true;
                    }
                    if (trimmed.matches("\\d+\\.\\d+")) {
                        foundDotDecimal = true;
                    }
                }
            }
            if (foundCommaDecimal && !foundDotDecimal) {
                likelyDecimalSeparator = ",";
            }
        }

        // Compute confidence score
        double confidence = computeConfidence(
                delimiter, consistencyRatio, mostCommonColCount,
                linesWith2Cols.size(), nonEmptyAnalyzedLines,
                sampleHeaderColumns, candidateHeaderRowIndex);

        return new SeparatorCandidate(
                delimiter, nonEmptyAnalyzedLines, linesWithAtLeastTwoColumns,
                maxColCount, avgColCount, mostCommonColCount, mostCommonFreq,
                consistencyRatio, candidateHeaderRowIndex, candidateDataStartRowIndex,
                sampleHeaderColumns, likelyDecimalSeparator, confidence, warnings);
    }

    private double computeConfidence(
            String delimiter, double consistencyRatio, int mostCommonColCount,
            int linesWith2Cols, int totalNonEmpty,
            List<String> headerColumns, int headerRowIndex) {

        double score = 0.0;

        // Base: column count consistency (0 to 0.4)
        score += consistencyRatio * 0.4;

        // Bonus: at least 4 columns (0 to 0.2)
        if (mostCommonColCount >= 4) {
            score += 0.2;
        } else if (mostCommonColCount >= 2) {
            score += 0.05;
        }

        // Bonus: enough data lines (0 to 0.1)
        double dataRatio = linesWith2Cols / (double) Math.max(totalNonEmpty, 1);
        score += dataRatio * 0.1;

        // Bonus: has plausible header (0 to 0.2)
        if (headerRowIndex >= 0 && !headerColumns.isEmpty()) {
            boolean hasRefKeyword = headerColumns.stream()
                    .map(String::toLowerCase)
                    .anyMatch(REF_KEYWORDS::contains);
            boolean hasPartKeyword = headerColumns.stream()
                    .map(String::toLowerCase)
                    .anyMatch(PART_KEYWORDS::contains);
            boolean hasXKeyword = headerColumns.stream()
                    .map(String::toLowerCase)
                    .anyMatch(X_KEYWORDS::contains);
            boolean hasYKeyword = headerColumns.stream()
                    .map(String::toLowerCase)
                    .anyMatch(Y_KEYWORDS::contains);
            boolean hasAngleKeyword = headerColumns.stream()
                    .map(String::toLowerCase)
                    .anyMatch(ANGLE_KEYWORDS::contains);

            if (hasRefKeyword || hasPartKeyword) {
                score += 0.1;
            }
            if (hasXKeyword && hasYKeyword) {
                score += 0.1;
            }
            if (hasAngleKeyword) {
                score += 0.05;
            }
        }

        // Bonus for whitespace: only if header contains KiCad-like fields
        if ("WHITESPACE".equals(delimiter)) {
            boolean hasRefVal = headerColumns.stream()
                    .map(String::toLowerCase)
                    .anyMatch(h -> h.equals("ref") || h.equals("val") || h.equals("package"));
            if (!hasRefVal && headerRowIndex >= 0) {
                score -= 0.2; // Penalty: metadata lines may appear as whitespace
            }
        }

        // Bonus for tab: actual tab characters in raw lines
        if ("\t".equals(delimiter)) {
            // Already split on tab, bonus is implicit in consistency
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * Render the separator analysis as JSON for prompt injection.
     */
    public String toJson(SeparatorAnalysis analysis) {
        try {
            return JSON_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(analysis);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String escapeDelimiter(String delimiter) {
        return switch (delimiter) {
            case "\t" -> "\\t";
            case "." -> "\\.";
            case "|" -> "\\|";
            default -> delimiter;
        };
    }

    private static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * A raw line with its index and split parts.
     */
    static class SampleLine {
        final int index;
        final String text;

        SampleLine(int index, String text) {
            this.index = index;
            this.text = text;
        }
    }

    private static class SplitLine {
        final int index;
        final String text;
        final String[] parts;

        SplitLine(int index, String text, String[] parts) {
            this.index = index;
            this.text = text;
            this.parts = parts;
        }
    }
}
