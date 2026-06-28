package com.example.pnp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SeparatorCandidateAnalyzer}.
 */
class SeparatorCandidateAnalyzerTest {

    private final SeparatorCandidateAnalyzer analyzer = new SeparatorCandidateAnalyzer();

    @Test
    void commaDelimiterDetected() {
        var lines = List.of(
                new SeparatorCandidateAnalyzer.SampleLine(0, "Designator,Mid X,Mid Y,Layer,Rotation"),
                new SeparatorCandidateAnalyzer.SampleLine(1, "C1,95.0518mm,22.6822mm,Top,270"),
                new SeparatorCandidateAnalyzer.SampleLine(2, "R1,106.4056mm,23.0124mm,Top,90")
        );
        var analysis = analyzer.analyze(lines);
        var top = analysis.candidates().get(0);
        assertEquals(",", top.delimiter(), "Comma should be top delimiter");
        assertTrue(top.confidence() >= 0.5, "Comma confidence should be >= 0.5");
    }

    @Test
    void semicolonWithDecimalCommaDetected() {
        var lines = List.of(
                new SeparatorCandidateAnalyzer.SampleLine(0, "No.;RefDes;PartNo;Package;X-Pos;Y-Pos;Angle;MountSide;Feeder"),
                new SeparatorCandidateAnalyzer.SampleLine(1, "1;R1;RES-10K-0603;0603;12,500;33,100;90;T;F01"),
                new SeparatorCandidateAnalyzer.SampleLine(2, "2;C1;CAP-100NF-0603;0603;45,200;22,800;0;B;F02")
        );
        var analysis = analyzer.analyze(lines);
        var top = analysis.candidates().get(0);
        assertEquals(";", top.delimiter(), "Semicolon should be top delimiter");
        assertEquals(",", top.likelyDecimalSeparator(), "Decimal separator should be comma");
        assertTrue(top.confidence() >= 0.5, "Semicolon confidence should be >= 0.5");
    }

    @Test
    void tabDelimiterDetected() {
        var lines = List.of(
                new SeparatorCandidateAnalyzer.SampleLine(0, "Index\tDesignator\tPN\tJEDEC\tCenterX\tCenterY\tTheta\tBoardSide"),
                new SeparatorCandidateAnalyzer.SampleLine(1, "1\tR1\tRES-10K-0603\t0603\t12.500\t33.100\t90\tTOP"),
                new SeparatorCandidateAnalyzer.SampleLine(2, "2\tC1\tCAP-100NF-0603\t0603\t45.200\t22.800\t0\tBOTTOM")
        );
        var analysis = analyzer.analyze(lines);
        var top = analysis.candidates().get(0);
        assertEquals("\t", top.delimiter(), "Tab should be top delimiter");
        assertTrue(top.confidence() >= 0.5, "Tab confidence should be >= 0.5");
    }

    @Test
    void whitespaceDelimiterDetected() {
        var lines = List.of(
                new SeparatorCandidateAnalyzer.SampleLine(0, "Ref Val Package PosX PosY Rot Side"),
                new SeparatorCandidateAnalyzer.SampleLine(1, "C1 100n C_0603_1608Metric 95.0518 22.6822 270.0 top"),
                new SeparatorCandidateAnalyzer.SampleLine(2, "R1 10k R_0603_1608Metric 106.4056 23.0124 90.0 top")
        );
        var analysis = analyzer.analyze(lines);
        var top = analysis.candidates().get(0);
        assertEquals("WHITESPACE", top.delimiter(), "Whitespace should be top delimiter");
        assertTrue(top.confidence() >= 0.5, "Whitespace confidence should be >= 0.5");
    }

    @Test
    void metadataDoesNotWinOverSemicolon() {
        var lines = List.of(
                new SeparatorCandidateAnalyzer.SampleLine(0, "Job Name: BOARD-1234"),
                new SeparatorCandidateAnalyzer.SampleLine(1, "Generated: 2026-06-27"),
                new SeparatorCandidateAnalyzer.SampleLine(2, "Machine: Synthetic-PnP-9000"),
                new SeparatorCandidateAnalyzer.SampleLine(3, "No.;RefDes;PartNo;Package;X-Pos;Y-Pos;Angle;MountSide;Feeder"),
                new SeparatorCandidateAnalyzer.SampleLine(4, "1;R1;RES-10K-0603;0603;12,500;33,100;90;T;F01"),
                new SeparatorCandidateAnalyzer.SampleLine(5, "2;C1;CAP-100NF-0603;0603;45,200;22,800;0;B;F02")
        );
        var analysis = analyzer.analyze(lines);
        var top = analysis.candidates().get(0);
        assertEquals(";", top.delimiter(), "Semicolon should be top delimiter despite metadata");
        assertTrue(top.confidence() >= 0.5, "Semicolon confidence should be >= 0.5");

        // WHITESPACE should not be the top candidate
        var whitespaceCandidate = analysis.candidates().stream()
                .filter(c -> "WHITESPACE".equals(c.delimiter()))
                .findFirst().orElse(null);
        assertNotNull(whitespaceCandidate, "Whitespace candidate should exist");
        assertTrue(whitespaceCandidate.confidence() < top.confidence(),
                "Whitespace confidence should be lower than semicolon");
    }

    @Test
    void emptySampleReturnsAllZeroConfidence() {
        var lines = List.<SeparatorCandidateAnalyzer.SampleLine>of();
        var analysis = analyzer.analyze(lines);
        for (var c : analysis.candidates()) {
            assertEquals(0.0, c.confidence(), 0.001, "Empty sample should have 0 confidence");
        }
        assertEquals(",", analysis.recommendedDelimiter(), "Fallback delimiter should be comma");
    }

    @Test
    void analysisToJsonIsValid() {
        var lines = List.of(
                new SeparatorCandidateAnalyzer.SampleLine(0, "Designator,Mid X,Mid Y,Layer,Rotation"),
                new SeparatorCandidateAnalyzer.SampleLine(1, "C1,95.0518mm,22.6822mm,Top,270")
        );
        var analysis = analyzer.analyze(lines);
        var json = analyzer.toJson(analysis);
        assertNotNull(json);
        assertTrue(json.contains("delimiterCandidates"), "JSON should contain delimiterCandidates");
        assertTrue(json.contains("recommendedDelimiter"), "JSON should contain recommendedDelimiter");
    }

    @Test
    void promptIncludesSeparatorAnalysis() {
        var sample = createSample(
                List.of("Designator,Mid X,Mid Y,Layer,Rotation",
                        "C1,95.0518mm,22.6822mm,Top,270")
        );
        var builder = new FormatDetectionPromptBuilder();
        var prompt = builder.build(sample);
        assertTrue(prompt.contains("DETERMINISTIC SEPARATOR ANALYSIS"),
                "Prompt should include separator analysis section");
        assertTrue(prompt.contains("recommendedDelimiter"),
                "Prompt should include recommended delimiter");
    }

    @Test
    void repairPromptIncludesSeparatorOnDelimiterErrors() {
        var sample = createSample(
                List.of("No.;RefDes;PartNo;Package;X-Pos;Y-Pos;Angle;MountSide;Feeder",
                        "1;R1;RES-10K-0603;0603;12,500;33,100;90;T;F01")
        );
        var builder = new RepairPromptBuilder();
        var prompt = builder.build(sample,
                "{\"delimiter\": \",\"}",
                List.of("Failed to resolve columns: Header column 'No.' not found for field 'reference'"));
        assertTrue(prompt.contains("DETERMINISTIC SEPARATOR ANALYSIS"),
                "Repair prompt should include separator analysis on delimiter errors");
    }

    @Test
    void repairPromptSkipsSeparatorOnNonDelimiterErrors() {
        var sample = createSample(
                List.of("Designator,Mid X,Mid Y,Layer,Rotation",
                        "C1,95.0518mm,22.6822mm,Top,270")
        );
        var builder = new RepairPromptBuilder();
        var prompt = builder.build(sample,
                "{\"delimiter\": \",\"}",
                List.of("Failed to parse: invalid number format"));
        assertFalse(prompt.contains("DETERMINISTIC SEPARATOR ANALYSIS"),
                "Repair prompt should skip separator analysis for non-delimiter errors");
    }

    @Test
    void candidateHeaderRowDetected() {
        var lines = List.of(
                new SeparatorCandidateAnalyzer.SampleLine(0, "# header comment"),
                new SeparatorCandidateAnalyzer.SampleLine(1, "Designator,Mid X,Mid Y,Layer,Rotation"),
                new SeparatorCandidateAnalyzer.SampleLine(2, "C1,95.0518mm,22.6822mm,Top,270")
        );
        var analysis = analyzer.analyze(lines);
        for (var c : analysis.candidates()) {
            if (",".equals(c.delimiter())) {
                assertEquals(1, c.candidateHeaderRowIndex(),
                        "Header row should be detected at index 1");
                break;
            }
        }
    }

    // --- helpers ---

    private static SampleResult createSample(List<String> rawLines) {
        var lines = new java.util.ArrayList<com.example.pnp.Line>();
        for (int i = 0; i < rawLines.size(); i++) {
            lines.add(new com.example.pnp.Line(i, rawLines.get(i)));
        }
        // Put all lines in firstLines, lastLines empty
        return new SampleResult(rawLines.size(), lines, List.of());
    }
}
