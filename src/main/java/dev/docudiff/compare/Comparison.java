package dev.docudiff.compare;

import dev.docudiff.diff.DiffStatus;
import dev.docudiff.diff.DocumentDiff;
import dev.docudiff.diff.RowDiff;

import java.util.List;

/**
 * The result of comparing two documents.
 *
 * <p>Exactly one of {@link #documentDiff()} and {@link #rowDiffs()} is non-null: word-processor
 * and markdown documents produce a block diff, spreadsheets produce row diffs. Both carry the
 * labels of their sources so the UI never has to remember what it asked for.
 */
public record Comparison(
        String leftLabel,
        String rightLabel,
        DocumentDiff documentDiff,
        List<RowDiff> rowDiffs,
        String sheetName) {

    public Comparison {
        rowDiffs = rowDiffs == null ? null : List.copyOf(rowDiffs);
    }

    /** True when the comparison has something to show. */
    public boolean hasContent() {
        return documentDiff != null || rowDiffs != null;
    }

    /** Number of changed blocks or changed rows, whichever this comparison holds. */
    public long changedCount() {
        if (documentDiff != null) {
            return documentDiff.changedBlockCount();
        }
        if (rowDiffs != null) {
            return rowDiffs.stream()
                    .filter(rowDiff -> rowDiff.status() != DiffStatus.UNCHANGED)
                    .count();
        }
        return 0L;
    }

    public boolean isSpreadsheet() {
        return rowDiffs != null;
    }
}
