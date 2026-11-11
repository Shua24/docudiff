package dev.docudiff.diff;

import dev.docudiff.document.Block;

/**
 * One aligned pair of blocks and what happened to them.
 *
 * A {@code null} left side means the block only exists in the new version (ADDED), and a
 * {@code null} right side means it only exists in the old one (REMOVED).
 *
 * @param left   the block in the older version, or null when added
 * @param right  the block in the newer version, or null when removed
 * @param status how the two relate
 */
public record BlockDiff(Block left, Block right, DiffStatus status) {
}
