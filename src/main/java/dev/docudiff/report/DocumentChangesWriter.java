package dev.docudiff.report;

import dev.docudiff.diff.BlockDiff;
import dev.docudiff.diff.DiffStatus;
import dev.docudiff.diff.DocumentDiff;
import dev.docudiff.diff.SectionDiff;
import dev.docudiff.diff.WordMark;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.util.List;

/**
 * Writes the word-processor half of a review report: one paragraph per change, with removed words
 * struck through and inserted words underlined, exactly how Word's own comparison reads.
 *
 * <p>Only changed blocks are written. A review report that reproduces the whole document buries
 * the changes; the reader already has both files, and the point of the report is to say what is
 * different.
 */
final class DocumentChangesWriter {

    private static final int MAXIMUM_CHANGED_BLOCKS = 200;

    void write(XWPFDocument document, DocumentDiff documentDiff) {
        int written = 0;
        int blockIndex = 0;

        for (SectionDiff sectionDiff : documentDiff.sectionDiffs()) {
            boolean headingWritten = false;
            for (BlockDiff blockDiff : sectionDiff.blockDiffs()) {
                if (blockDiff.status() == DiffStatus.UNCHANGED) {
                    blockIndex++;
                    continue;
                }
                if (written >= MAXIMUM_CHANGED_BLOCKS) {
                    ReportRuns.muted(document, "… and more changes in this document; the report "
                            + "stops after " + MAXIMUM_CHANGED_BLOCKS + " changed blocks.");
                    return;
                }
                if (!headingWritten) {
                    ReportRuns.heading(document, sectionDiff.sectionName());
                    headingWritten = true;
                }
                writeBlock(document, documentDiff, blockIndex, blockDiff);
                written++;
                blockIndex++;
            }
        }
    }

    private void writeBlock(
            XWPFDocument document, DocumentDiff documentDiff, int blockIndex, BlockDiff blockDiff) {
        XWPFParagraph paragraph = ReportRuns.paragraph(document);
        List<WordMark> wordMarks = documentDiff.wordMarksOf(blockIndex);

        if (blockDiff.status() == DiffStatus.REMOVED) {
            ReportRuns.removed(paragraph, ReportText.textOf(blockDiff.left()));
        } else if (blockDiff.status() == DiffStatus.ADDED) {
            ReportRuns.added(paragraph, ReportText.textOf(blockDiff.right()));
        } else if (wordMarks.isEmpty()) {
            // A formatting-only change: the words are identical, and the annotation below carries
            // the whole story.
            XWPFRun run = ReportRuns.text(paragraph, ReportText.textOf(blockDiff.right()));
            if (ReportText.isCode(blockDiff.right())) {
                ReportRuns.monospace(run);
            }
            if (ReportText.isHeading(blockDiff.right())) {
                run.setBold(true);
            }
        } else {
            for (WordMark wordMark : wordMarks) {
                String text = wordMark.text() + " ";
                switch (wordMark.kind()) {
                    case ADDED -> ReportRuns.added(paragraph, text);
                    case REMOVED -> ReportRuns.removed(paragraph, text);
                    case EQUAL -> ReportRuns.text(paragraph, text);
                }
            }
        }

        for (String annotation : documentDiff.annotationsOf(blockIndex)) {
            ReportRuns.muted(document, "formatting: " + annotation);
        }
    }
}
