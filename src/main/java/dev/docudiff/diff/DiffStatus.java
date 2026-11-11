package dev.docudiff.diff;

/** How one block of a document relates between the two versions being compared. */
public enum DiffStatus {
    UNCHANGED,
    MODIFIED,
    ADDED,
    REMOVED
}
