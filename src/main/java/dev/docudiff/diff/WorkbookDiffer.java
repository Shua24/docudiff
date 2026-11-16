package dev.docudiff.diff;

import dev.docudiff.document.SheetModel;
import dev.docudiff.document.WorkbookModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aligns the sheets of two workbooks by name, then diffs each pair.
 *
 * <p>Sheets are matched by <b>name</b>, not by position, for the same reason rows are matched by
 * content: inserting a sheet at the front must not make every later sheet look rewritten. A sheet
 * that exists on only one side is reported as added or removed rather than being dropped.
 *
 * <p>Order follows the newer workbook, so the reader walks the document as it now stands; sheets
 * that were deleted are appended at the end, where they cannot be mistaken for current content.
 */
public final class WorkbookDiffer {

    private final SheetRowAligner rowAligner = new SheetRowAligner();
    private final SheetFormattingAnnotator formattingAnnotator = new SheetFormattingAnnotator();

    public List<SheetDiff> diff(WorkbookModel left, WorkbookModel right) {
        Map<String, SheetModel> leftByName = byName(left);
        Map<String, SheetModel> rightByName = byName(right);

        List<SheetDiff> sheetDiffs = new ArrayList<>();
        for (SheetModel rightSheet : right.sheets()) {
            SheetModel leftSheet = leftByName.get(rightSheet.name());
            if (leftSheet == null) {
                sheetDiffs.add(SheetDiff.added(rightSheet.name(), rightSheet.rows()));
                continue;
            }
            sheetDiffs.add(SheetDiff.compared(
                    rightSheet.name(), diffRows(leftSheet, rightSheet)));
        }
        for (SheetModel leftSheet : left.sheets()) {
            if (!rightByName.containsKey(leftSheet.name())) {
                sheetDiffs.add(SheetDiff.removed(leftSheet.name(), leftSheet.rows()));
            }
        }
        return sheetDiffs;
    }

    /**
     * Aligns rows, then attaches formatting differences.
     *
     * <p>Alignment uses content only, deliberately: a bold change must not make a row look
     * inserted. Formatting is layered on afterwards, and a row whose text is identical but whose
     * presentation changed is promoted to MODIFIED so the view does not skip it.
     */
    private List<RowDiff> diffRows(SheetModel leftSheet, SheetModel rightSheet) {
        List<RowDiff> aligned = rowAligner.align(leftSheet.rows(), rightSheet.rows());
        List<RowDiff> withFormatting = new ArrayList<>(aligned.size());
        for (RowDiff rowDiff : aligned) {
            withFormatting.add(attachFormatting(rowDiff));
        }
        return withFormatting;
    }

    private RowDiff attachFormatting(RowDiff rowDiff) {
        if (rowDiff.left() == null || rowDiff.right() == null) {
            return rowDiff;
        }
        List<CellFormatChange> formatChanges =
                formattingAnnotator.changesFor(rowDiff.left(), rowDiff.right());
        if (formatChanges.isEmpty()) {
            return rowDiff;
        }
        DiffStatus status = rowDiff.status() == DiffStatus.UNCHANGED
                ? DiffStatus.MODIFIED
                : rowDiff.status();
        return new RowDiff(rowDiff.left(), rowDiff.right(), status,
                rowDiff.changedColumns(), formatChanges);
    }

    private Map<String, SheetModel> byName(WorkbookModel workbook) {
        Map<String, SheetModel> byName = new LinkedHashMap<>();
        for (SheetModel sheet : workbook.sheets()) {
            byName.put(sheet.name(), sheet);
        }
        return byName;
    }
}
