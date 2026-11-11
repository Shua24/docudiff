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
 */
public record RowDiff(
        SheetRow left,
        SheetRow right,
        DiffStatus status,
        List<Integer> changedColumns) {

    public RowDiff {
        changedColumns = List.copyOf(changedColumns);
    }
}
