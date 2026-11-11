package dev.docudiff.compare;

import dev.docudiff.versioning.DocumentFormat;

import java.nio.file.Path;

/**
 * One slot in the comparison UI: either a version from the open document's history, a file on
 * disk, or the working file as it stands right now.
 *
 * <p>This exists so the UI can hold "what the user picked" without knowing how to read it, and so
 * resolving a slot to bytes is one small method instead of a branch at every call site.
 */
public sealed interface ComparisonSlot
        permits ComparisonSlot.VersionSlot, ComparisonSlot.FileSlot, ComparisonSlot.WorkingFileSlot {

    /** A version from the open document's repository. */
    record VersionSlot(String label, String commitId) implements ComparisonSlot {
    }

    /** A file anywhere on disk, which is how a returned copy arrives. */
    record FileSlot(String label, Path path, DocumentFormat format) implements ComparisonSlot {
    }

    /**
     * The working file of the open document as it is right now, including unsaved edits. This is
     * the only slot whose bytes can change between two comparisons, so the header should say so.
     */
    record WorkingFileSlot(String label) implements ComparisonSlot {
    }
}
