package com.example.pnp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * CLI entry point for the PnP CSV sampler.
 * <p>
 * Usage: java ... com.example.pnp.Main &lt;file&gt; [--first N] [--last M]
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
        }
    }

    private static int runInternal(String[] args) throws IOException {
        if (args.length == 0) {
            printUsage();
            return 1;
        }

        String filePath = args[0];
        int firstCount = DEFAULT_FIRST;
        int lastCount = DEFAULT_LAST;

        // Parse optional flags
        int i = 1;
        while (i < args.length) {
            switch (args[i]) {
                case "--first" -> {
                    i++;
                    if (i >= args.length) {
                        System.err.println("Error: --first requires a positive integer argument");
                        printUsage();
                        return 1;
                    }
                    try {
                        firstCount = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        System.err.println("Error: --first must be a positive integer, got '" + args[i] + "'");
                        return 1;
                    }
                    if (firstCount < 1) {
                        System.err.println("Error: --first must be >= 1, got " + firstCount);
                        return 1;
                    }
                }
                case "--last" -> {
                    i++;
                    if (i >= args.length) {
                        System.err.println("Error: --last requires a positive integer argument");
                        printUsage();
                        return 1;
                    }
                    try {
                        lastCount = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        System.err.println("Error: --last must be a positive integer, got '" + args[i] + "'");
                        return 1;
                    }
                    if (lastCount < 1) {
                        System.err.println("Error: --last must be >= 1, got " + lastCount);
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

        var sampler = new Sampler(firstCount, lastCount);

        try (var reader = new InputStreamReader(
                new FileInputStream(filePath), StandardCharsets.UTF_8)) {
            var result = sampler.sample(reader);

            var mapper = new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(System.out, result);
        }

        return 0;
    }

    private static void printUsage() {
        System.err.println("Usage: java ... com.example.pnp.Main <file> [--first N] [--last M]");
        System.err.println("  <file>        Path to PnP CSV file (required)");
        System.err.println("  --first N     Number of lines to sample from the start (default: " + DEFAULT_FIRST + ")");
        System.err.println("  --last M      Number of lines to sample from the end (default: " + DEFAULT_LAST + ")");
        System.err.println();
        System.err.println("Outputs a JSON object with totalLines, firstLines, and lastLines.");
    }
}
