package dev.docudiff.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole document diff, flattened in reading order across sections.
 *
 * <p>Word marks and annotations are indexed by the position in {@link #blockDiffs()}, so the UI can
 * ask for "the marks of block N" without re-running any diffing.
 */
public final class DocumentDiff {

    private final List<SectionDiff> sectionDiffs;
    private final List<BlockDiff> blockDiffs;
    private final List<List<WordMark>> wordMarks;
    private final List<List<String>> annotations;

    DocumentDiff(
            List<SectionDiff> sectionDiffs,
            List<BlockDiff> blockDiffs,
            List<List<WordMark>> wordMarks,
            List<List<String>> annotations) {
        this.sectionDiffs = List.copyOf(sectionDiffs);
        this.blockDiffs = List.copyOf(blockDiffs);
        this.wordMarks = List.copyOf(wordMarks);
        this.annotations = List.copyOf(annotations);
    }

    public List<SectionDiff> sectionDiffs() {
        return sectionDiffs;
    }

    public List<BlockDiff> blockDiffs() {
        return blockDiffs;
    }

    public List<WordMark> wordMarksOf(int blockIndex) {
        return wordMarks.get(blockIndex);
    }

    public List<String> annotationsOf(int blockIndex) {
        return annotations.get(blockIndex);
    }

    /** Number of blocks whose status is not {@link DiffStatus#UNCHANGED}. */
    public long changedBlockCount() {
        return blockDiffs.stream()
                .filter(blockDiff -> blockDiff.status() != DiffStatus.UNCHANGED)
                .count();
    }

    public static DocumentDiff empty() {
        return new DocumentDiff(List.of(), List.of(), List.of(), List.of());
    }

    static List<List<WordMark>> newWordMarkList() {
        return new ArrayList<>();
    }
}
