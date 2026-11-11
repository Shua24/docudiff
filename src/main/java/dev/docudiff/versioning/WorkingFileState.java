package dev.docudiff.versioning;

/** Whether the file in the library folder matches the version that is checked out. */
public enum WorkingFileState {
    CLEAN,
    MODIFIED,
    MISSING
}
