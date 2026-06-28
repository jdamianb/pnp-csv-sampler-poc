package com.example.pnp;

import java.io.FileReader;

public class TestPrompt {
    public static void main(String[] args) throws Exception {
        var sampler = new Sampler(10, 5);
        var sample = sampler.sample(new FileReader("examples-extended/09_machine_tab_separated_with_metadata.tsv"));
        var builder = new FormatDetectionPromptBuilder();
        var prompt = builder.build(sample);
        System.out.println(prompt);
    }
}
