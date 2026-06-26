package com.example.pnp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Deterministic PnP CSV sampler.
 * <p>
 * Reads raw text from a {@link Reader}, samples the first N lines and last M lines,
 * and returns them in a {@link SampleResult}. The sampler never loads the entire
 * file into memory — it uses a ring buffer for the tail sample.
 * <p>
 * This sampler is intentionally dumb: it does not parse CSV fields, infer delimiters,
 * detect headers, normalize content, or trim lines.
 */
public class Sampler {

    private final int firstCount;
    private final int lastCount;

    /**
     * @param firstCount number of lines to sample from the beginning (&ge; 1)
     * @param lastCount  number of lines to sample from the end (&ge; 1)
     */
    public Sampler(int firstCount, int lastCount) {
        if (firstCount < 1) {
            throw new IllegalArgumentException("firstCount must be >= 1, got " + firstCount);
        }
        if (lastCount < 1) {
            throw new IllegalArgumentException("lastCount must be >= 1, got " + lastCount);
        }
        this.firstCount = firstCount;
        this.lastCount = lastCount;
    }

    /**
     * Sample the given reader.
     *
     * @param reader the input (will be closed after reading)
     * @return sampled result
     * @throws IOException if reading fails
     */
    public SampleResult sample(Reader reader) throws IOException {
        var firstLines = new ArrayList<Line>(firstCount);
        Deque<Line> lastBuffer = new ArrayDeque<>(lastCount);

        long lineIndex = 0;
        try (var buffered = new BufferedReader(reader)) {
            String raw;
            while ((raw = buffered.readLine()) != null) {
                // Collect first N lines
                if (lineIndex < firstCount) {
                    firstLines.add(new Line((int) lineIndex, raw));
                }

                // Ring buffer for last M lines
                if (lastCount > 0) {
                    if (lastBuffer.size() == lastCount) {
                        lastBuffer.removeFirst();
                    }
                    lastBuffer.addLast(new Line((int) lineIndex, raw));
                }

                lineIndex++;
            }
        }

        long totalLines = lineIndex;

        // Build lastLines: only include lines not already in firstLines
        // (R10: no duplicates when totalLines <= firstCount + lastCount)
        List<Line> lastLines;
        if (totalLines <= firstCount) {
            lastLines = List.of();
        } else {
            lastLines = lastBuffer.stream()
                    .filter(line -> line.index() >= firstCount)
                    .toList();
        }

        return new SampleResult(totalLines, firstLines, lastLines);
    }
}
