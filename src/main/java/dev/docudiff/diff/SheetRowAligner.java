package dev.docudiff.diff;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import dev.docudiff.document.SheetCell;
import dev.docudiff.document.SheetRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Aligns spreadsheet rows by content rather than by position.
 *
 * <p>This is the difference between a usable spreadsheet diff and a useless one. Measured on a
 * 500-row sheet with one row inserted at the top: content alignment reports <b>1</b> insertion,
 * while a position-by-position comparison reports <b>501</b> changed rows.
 */
public final class SheetRowAligner {

    public List<RowDiff> align(List<SheetRow> leftRows, List<SheetRow> rightRows) {
        List<String> leftSignatures = leftRows.stream().map(SheetRow::signature).toList();
        List<String> rightSignatures = rightRows.stream().map(SheetRow::signature).toList();

        Patch<String> patch = DiffUtils.diff(leftSignatures, rightSignatures, String::equals);
        List<RowDiff> rowDiffs = new ArrayList<>();
        int leftCursor = 0;
        int rightCursor = 0;

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            int deltaLeftStart = delta.getSource().getPosition();
            int deltaRightStart = delta.getTarget().getPosition();

            for (int offset = 0; offset < deltaLeftStart - leftCursor; offset++) {
                rowDiffs.add(new RowDiff(
                        leftRows.get(leftCursor + offset),
                        rightRows.get(rightCursor + offset),
                        DiffStatus.UNCHANGED,
                        List.of()));
            }

            switch (delta.getType()) {
                case INSERT -> {
                    for (int offset = 0; offset < delta.getTarget().size(); offset++) {
                        rowDiffs.add(new RowDiff(
                                null, rightRows.get(deltaRightStart + offset),
                                DiffStatus.ADDED, List.of()));
                    }
                }
                case DELETE -> {
                    for (int offset = 0; offset < delta.getSource().size(); offset++) {
                        rowDiffs.add(new RowDiff(
                                leftRows.get(deltaLeftStart + offset), null,
                                DiffStatus.REMOVED, List.of()));
                    }
                }
                case CHANGE -> {
                    int pairedCount = Math.min(
                            delta.getSource().size(), delta.getTarget().size());
                    for (int offset = 0; offset < pairedCount; offset++) {
                        SheetRow leftRow = leftRows.get(deltaLeftStart + offset);
                        SheetRow rightRow = rightRows.get(deltaRightStart + offset);
                        rowDiffs.add(new RowDiff(
                                leftRow, rightRow, DiffStatus.MODIFIED,
                                changedColumns(leftRow, rightRow)));
                    }
                    for (int offset = pairedCount; offset < delta.getSource().size(); offset++) {
                        rowDiffs.add(new RowDiff(
                                leftRows.get(deltaLeftStart + offset), null,
                                DiffStatus.REMOVED, List.of()));
                    }
                    for (int offset = pairedCount; offset < delta.getTarget().size(); offset++) {
                        rowDiffs.add(new RowDiff(
                                null, rightRows.get(deltaRightStart + offset),
                                DiffStatus.ADDED, List.of()));
                    }
                }
                case EQUAL -> {
                    for (int offset = 0; offset < delta.getSource().size(); offset++) {
                        rowDiffs.add(new RowDiff(
                                leftRows.get(deltaLeftStart + offset),
                                rightRows.get(deltaRightStart + offset),
                                DiffStatus.UNCHANGED,
                                List.of()));
                    }
                }
            }

            leftCursor = deltaLeftStart + delta.getSource().size();
            rightCursor = deltaRightStart + delta.getTarget().size();
        }

        while (leftCursor < leftRows.size() && rightCursor < rightRows.size()) {
            rowDiffs.add(new RowDiff(
                    leftRows.get(leftCursor), rightRows.get(rightCursor),
                    DiffStatus.UNCHANGED, List.of()));
            leftCursor++;
            rightCursor++;
        }
        while (leftCursor < leftRows.size()) {
            rowDiffs.add(new RowDiff(leftRows.get(leftCursor), null, DiffStatus.REMOVED, List.of()));
            leftCursor++;
        }
        while (rightCursor < rightRows.size()) {
            rowDiffs.add(new RowDiff(null, rightRows.get(rightCursor), DiffStatus.ADDED, List.of()));
            rightCursor++;
        }
        return rowDiffs;
    }

    private List<Integer> changedColumns(SheetRow leftRow, SheetRow rightRow) {
        List<Integer> changedColumns = new ArrayList<>();
        int columnCount = Math.max(leftRow.cells().size(), rightRow.cells().size());
        for (int index = 0; index < columnCount; index++) {
            SheetCell leftCell = index < leftRow.cells().size() ? leftRow.cells().get(index) : null;
            SheetCell rightCell = index < rightRow.cells().size()
                    ? rightRow.cells().get(index) : null;
            String leftKey = leftCell == null ? "" : leftCell.contentKey();
            String rightKey = rightCell == null ? "" : rightCell.contentKey();
            if (!leftKey.equals(rightKey)) {
                changedColumns.add(index + 1);
            }
        }
        return changedColumns;
    }
}
