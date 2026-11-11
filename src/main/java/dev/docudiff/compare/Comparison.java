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
 *
 * <p>{@code notices} lists parts of the compared files that the app does not read — footnotes,
 * comments, nested tables. A diff that silently ignores them can report "identical" on a document
 * whose only edit was a comment, so the notice is part of the result, not a detail of the UI.
 *
 * @param leftLabel      what to call the left side on screen
 * @param rightLabel     what to call the right side on screen
 * @param documentDiff   the block diff, or null for spreadsheets
 * @param sheetDiffs     one entry per worksheet, or null for word-processor documents
 * @param notices        human-facing "not compared" lines, empty when everything was read
 */
public record Comparison(
        String leftLabel,
        String rightLabel,
        DocumentDiff documentDiff,
        List<SheetDiff> sheetDiffs,
        List<String> notices) {

    public Comparison {
        sheetDiffs = sheetDiffs == null ? null : List.copyOf(sheetDiffs);
        notices = notices == null ? List.of() : List.copyOf(notices);
    }

    /** A comparison with nothing unsupported in either file. */
    public Comparison(
            String leftLabel,
            String rightLabel,
            DocumentDiff documentDiff,
            List<SheetDiff> sheetDiffs) {
        this(leftLabel, rightLabel, documentDiff, sheetDiffs, List.of());
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
