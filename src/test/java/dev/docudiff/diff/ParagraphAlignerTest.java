package dev.docudiff.diff;

import dev.docudiff.document.Block;
import dev.docudiff.document.InlineSpan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParagraphAlignerTest {

    @Test
    @DisplayName("an unchanged paragraph is reported unchanged")
    void unchangedParagraphIsUnchanged() {
        ParagraphAligner aligner = new ParagraphAligner();

        List<BlockDiff> blockDiffs = aligner.align(
                List.of(paragraph("Second paragraph stays the same.")),
                List.of(paragraph("Second paragraph stays the same.")));

        assertEquals(1, blockDiffs.size());
        assertEquals(DiffStatus.UNCHANGED, blockDiffs.get(0).status());
    }

    @Test
    @DisplayName("a one-word edit is one modified block, not a delete plus an insert")
    void oneWordEditIsOneModifiedBlock() {
        ParagraphAligner aligner = new ParagraphAligner();

        List<BlockDiff> blockDiffs = aligner.align(
                List.of(paragraph("The quick brown fox jumps over the lazy dog.")),
                List.of(paragraph("The quick brown fox leaps over the lazy dog.")));

        assertEquals(1, blockDiffs.size());
        assertEquals(DiffStatus.MODIFIED, blockDiffs.get(0).status());
    }

    @Test
    @DisplayName("the measured three-paragraph case reports its three changes in reading order")
    void measuredThreeParagraphCaseReportsThreeChanges() {
        ParagraphAligner aligner = new ParagraphAligner();

        List<BlockDiff> blockDiffs = aligner.align(
                List.of(
                        paragraph("The quick brown fox jumps over the lazy dog."),
                        paragraph("Second paragraph stays the same."),
                        paragraph("Third paragraph will be removed.")),
                List.of(
                        paragraph("The quick brown fox leaps over the lazy dog."),
                        paragraph("Second paragraph stays the same."),
                        paragraph("A brand new fourth paragraph appears.")));

        List<DiffStatus> statuses = blockDiffs.stream().map(BlockDiff::status).toList();
        assertEquals(
                List.of(
                        DiffStatus.MODIFIED,
                        DiffStatus.UNCHANGED,
                        DiffStatus.REMOVED,
                        DiffStatus.ADDED),
                statuses,
                "the one-word edit must pair as MODIFIED; the replaced paragraph must not pair, "
                        + "because bigram Jaccard scores it 0.245 (below the 0.35 threshold)");
    }

    @Test
    @DisplayName("an added paragraph is reported as added")
    void addedParagraphIsAdded() {
        ParagraphAligner aligner = new ParagraphAligner();

        List<BlockDiff> blockDiffs = aligner.align(
                List.of(paragraph("Alpha.")),
                List.of(paragraph("Alpha."), paragraph("Beta.")));

        assertEquals(2, blockDiffs.size());
        assertEquals(DiffStatus.UNCHANGED, blockDiffs.get(0).status());
        assertEquals(DiffStatus.ADDED, blockDiffs.get(1).status());
    }

    @Test
    @DisplayName("a removed paragraph is reported as removed")
    void removedParagraphIsRemoved() {
        ParagraphAligner aligner = new ParagraphAligner();

        List<BlockDiff> blockDiffs = aligner.align(
                List.of(paragraph("Alpha."), paragraph("Beta.")),
                List.of(paragraph("Alpha.")));

        assertEquals(2, blockDiffs.size());
        assertEquals(DiffStatus.UNCHANGED, blockDiffs.get(0).status());
        assertEquals(DiffStatus.REMOVED, blockDiffs.get(1).status());
    }

    @Test
    @DisplayName("a completely different document reports every side as a change")
    void completelyDifferentDocumentReportsChanges() {
        ParagraphAligner aligner = new ParagraphAligner();

        List<BlockDiff> blockDiffs = aligner.align(
                List.of(paragraph("Alpha beta gamma delta epsilon.")),
                List.of(paragraph("Nothing here resembles the other text at all.")));

        assertEquals(2, blockDiffs.size());
        assertEquals(DiffStatus.REMOVED, blockDiffs.get(0).status());
        assertEquals(DiffStatus.ADDED, blockDiffs.get(1).status());
    }

    @Test
    @DisplayName("a paragraph that keeps its text and gains a clause stays one modified block")
    void paragraphWithAddedClauseStaysOneModifiedBlock() {
        ParagraphAligner aligner = new ParagraphAligner();

        List<BlockDiff> blockDiffs = aligner.align(
                List.of(paragraph("Revenue grew this quarter.")),
                List.of(paragraph(
                        "Revenue grew this quarter, driven by strong demand in the north.")));

        assertEquals(1, blockDiffs.size(),
                "an added clause must not split into a delete plus an insert; "
                        + "word Jaccard scores this pair 0.25 but bigram Jaccard scores it 0.426");
        assertEquals(DiffStatus.MODIFIED, blockDiffs.get(0).status());
    }

    @Test
    @DisplayName("a number-only change pairs as one modified block")
    void numberOnlyChangePairs() {
        ParagraphAligner aligner = new ParagraphAligner();

        List<BlockDiff> blockDiffs = aligner.align(
                List.of(paragraph("The quarterly revenue increased by 15% to $2.3 million.")),
                List.of(paragraph("The quarterly revenue decreased by 3% to $2.1 million.")));

        assertEquals(1, blockDiffs.size());
        assertEquals(DiffStatus.MODIFIED, blockDiffs.get(0).status());
    }

    @Test
    @DisplayName("a reworded short heading does not pair, because nothing but the slot is shared")
    void rewordedShortHeadingDoesNotPair() {
        ParagraphAligner aligner = new ParagraphAligner();

        List<BlockDiff> blockDiffs = aligner.align(
                List.of(paragraph("Introduction")),
                List.of(paragraph("Overview")));

        assertEquals(2, blockDiffs.size());
        assertEquals(DiffStatus.REMOVED, blockDiffs.get(0).status());
        assertEquals(DiffStatus.ADDED, blockDiffs.get(1).status());
    }

    private Block paragraph(String text) {
        return new Block.Paragraph(List.of(InlineSpan.plain(text)));
    }
}
