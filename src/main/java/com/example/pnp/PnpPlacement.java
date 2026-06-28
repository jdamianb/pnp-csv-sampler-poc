package com.example.pnp;

/**
 * A normalized placement row produced by the Stage 4 simple parser.
 *
 * @param reference the component reference/designator (may be null if missing)
 * @param partNumber the component part number/value (may be null if missing)
 * @param jedec the JEDEC/package/footprint name (may be null if missing)
 * @param x the X coordinate in mm
 * @param y the Y coordinate in mm
 * @param angle the rotation angle in degrees
 * @param side the side/layer (e.g., "Top", "Bottom"), may be null
 */
public record PnpPlacement(
        String reference,
        String partNumber,
        String jedec,
        double x,
        double y,
        double angle,
        String side) {
}
