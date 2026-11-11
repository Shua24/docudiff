package dev.docudiff.diff;

/**
 * One formatting difference in a single cell.
 *
 * @param column      the 1-based column the change is in
 * @param description what changed, phrased for a reader: "bold applied", "fill changed to FFFF00"
 */
public record CellFormatChange(int column, String description) {
}
