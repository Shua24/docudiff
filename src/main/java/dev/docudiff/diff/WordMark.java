package dev.docudiff.diff;

/** One word of a modified block and whether it was kept, added, or removed. */
public record WordMark(String text, WordMarkKind kind) {
}
