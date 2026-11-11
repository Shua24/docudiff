package dev.docudiff.ui;

/**
 * Where one search match landed, so the search bar can describe it.
 *
 * @param blockIndex  the block or row the match sits in
 * @param unchanged   true when that block or row is not itself a change
 * @param description how to name that place to the reader
 */
record MatchSpot(int blockIndex, boolean unchanged, String description) {
}
