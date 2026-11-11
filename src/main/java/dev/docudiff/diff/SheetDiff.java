package dev.docudiff.diff;

import java.util.List;

/**
 * One worksheet's worth of diff, including sheets that exist on only one side.
 *
 * <p>Before this existed, the comparer took {@code sheets().get(0)} from each workbook and silently
 * ignored every other tab. A workbook whose change was on the second sheet reported "identical",
 * which is worse than a missing feature: it is a confident wrong answer.
 *
 * @param name     the sheet name as the user sees it
 * @param rowDiffs the aligned rows, empty for a sheet that is entirely new or entirely gone
 * @param status   how the sheet as a whole relates
 */
public record SheetDiff(String name, List<RowDiff> rowDiffs, DiffStatus status) {

    public SheetDiff {
        rowDiffs = List.copyOf(rowDiffs);
    }

    /** A sheet present on both sides, with its rows already aligned. */
    public static SheetDiff compared(String name, List<RowDiff> rowDiffs) {
        boolean anyChange = rowDiffs.stream().anyMatch(RowDiff::isChanged);
        return new SheetDiff(name, rowDiffs, anyChange ? DiffStatus.MODIFIED : DiffStatus.UNCHANGED);
    }

    /** A sheet that exists only in the newer workbook: every row is an addition. */
    public static SheetDiff added(String name, List<dev.docudiff.document.SheetRow> rows) {
        List<RowDiff> rowDiffs = rows.stream()
                .map(row -> new RowDiff(null, row, DiffStatus.ADDED, List.of()))
                .toList();
        return new SheetDiff(name, rowDiffs, DiffStatus.ADDED);
    }

    /** A sheet that exists only in the older workbook: every row is a removal. */
    public static SheetDiff removed(String name, List<dev.docudiff.document.SheetRow> rows) {
        List<RowDiff> rowDiffs = rows.stream()
                .map(row -> new RowDiff(row, null, DiffStatus.REMOVED, List.of()))
                .toList();
        return new SheetDiff(name, rowDiffs, DiffStatus.REMOVED);
    }

    public boolean isChanged() {
        return status != DiffStatus.UNCHANGED;
    }

    /** Changed rows, counting every row of an added or removed sheet. */
    public long changedRowCount() {
        return rowDiffs.stream().filter(RowDiff::isChanged).count();
    }
}
