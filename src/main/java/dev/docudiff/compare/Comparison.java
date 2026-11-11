package dev.docudiff.compare;

import dev.docudiff.diff.DocumentDiff;
import dev.docudiff.diff.SheetDiff;

import java.util.List;

/**
 * The result of comparing two documents.
 *
 * <p>Exactly one of {@link #documentDiff()} and {@link #sheetDiffs()} is non-null: word-processor
 * and markdown documents produce a block diff, spreadsheets produce one {@link SheetDiff} per
 * worksheet. Both carry the labels of their sources so the UI never has to remember what it asked
 * for.
 */
public record Comparison(
        String leftLabel,
        String rightLabel,
        DocumentDiff documentDiff,
        List<SheetDiff> sheetDiffs) {

    public Comparison {
        sheetDiffs = sheetDiffs == null ? null : List.copyOf(sheetDiffs);
    }

    /** True when the comparison has something to show. */
    public boolean hasContent() {
        return documentDiff != null || sheetDiffs != null;
    }

    /** Number of changed blocks, or changed rows across every sheet. */
    public long changedCount() {
        if (documentDiff != null) {
            return documentDiff.changedBlockCount();
        }
        if (sheetDiffs != null) {
            return sheetDiffs.stream().mapToLong(SheetDiff::changedRowCount).sum();
        }
        return 0L;
    }

    public boolean isSpreadsheet() {
        return sheetDiffs != null;
    }

    /** Sheets that actually differ, which is what the view renders. */
    public List<SheetDiff> changedSheets() {
        if (sheetDiffs == null) {
            return List.of();
        }
        return sheetDiffs.stream().filter(SheetDiff::isChanged).toList();
    }
}
