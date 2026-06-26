package com.example.pnp;

/**
 * A single sampled line with its zero-based index and original raw text.
 *
 * @param index zero-based line number
 * @param text  original raw line content, exactly as read from the file
 */
public record Line(int index, String text) {
}
