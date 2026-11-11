package dev.docudiff.diff;

import dev.docudiff.document.SheetRow;

import java.util.List;

/**
 * One aligned pair of spreadsheet rows.
 *
 * @param left           the row in the older version, or null when added
 * @param right          the row in the newer version, or null when removed
 * @param status         how the two relate
 * @param changedColumns the 1-based columns whose content differs within a MODIFIED row
 * @param formatChanges  formatting differences within a MODIFIED row, one entry per cell
 */
public record RowDiff(
        SheetRow left,
        SheetRow right,
        DiffStatus status,
        List<Integer> changedColumns,
        List<CellFormatChange> formatChanges) {

    public RowDiff {
        changedColumns = List.copyOf(changedColumns);
        formatChanges = List.copyOf(formatChanges);
    }

    /** A row with no formatting differences, which is the common case. */
    public RowDiff(SheetRow left, SheetRow right, DiffStatus status,
            List<Integer> changedColumns) {
        this(left, right, status, changedColumns, List.of());
    }

    /**
     * True when the rows differ at all — in content or only in formatting.
     *
     * <p>A formatting-only change is a real change: making a header bold alters the document even
     * though no text moved. Without this, such a row would be classed UNCHANGED and the view would
     * skip rendering it entirely.
     */
    public boolean isChanged() {
        return status != DiffStatus.UNCHANGED
                || !changedColumns.isEmpty()
                || !formatChanges.isEmpty();
    }

    /** True when the cell contents are identical and only the presentation differs. */
    public boolean isFormattingOnly() {
        return changedColumns.isEmpty() && !formatChanges.isEmpty();
    }
}
