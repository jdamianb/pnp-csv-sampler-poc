package com.example.pnp;

import java.io.FileReader;

public class TestAnalyzer {
    public static void main(String[] args) throws Exception {
        var sampler = new Sampler(10, 5);
        var analyzer = new SeparatorCandidateAnalyzer();

        String[] testFiles = {
            "examples-extended/01_kicad_native_ascii_mm.pos",
            "examples-extended/02_kicad_csv_ref_val_package_pos.csv",
            "examples-extended/08_machine_semicolon_decimal_comma.csv",
            "examples-extended/09_machine_tab_separated_with_metadata.tsv"
        };

        for (String path : testFiles) {
            System.out.println("=== " + path + " ===");
            var sample = sampler.sample(new FileReader(path));
            var analysis = analyzer.analyze(sample);
            System.out.println("Recommended delimiter: " + analysis.recommendedDelimiter());
            System.out.println("Recommended decimal sep: " + analysis.recommendedDecimalSeparator());
            for (var c : analysis.candidates()) {
                System.out.printf("  %-12s conf=%.2f headerRow=%d cols=%d%n",
                        c.delimiter(), c.confidence(), c.candidateHeaderRowIndex(), c.mostCommonColumnCount());
            }
            System.out.println();
        }
        System.out.println("=== Done ===");
    }
}
