package dev.docudiff.diff;

import dev.docudiff.document.SheetCell;
import dev.docudiff.document.SheetRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns a pair of aligned spreadsheet rows into the formatting annotations shown under a row.
 *
 * <p>Mirrors {@link FormattingAnnotator}, which does the same job for document blocks. A cell whose
 * text did not move but whose weight or fill changed is still a change, and before this existed the
 * extractor carried {@code bold} and {@code fillColor} all the way to the diff only for them to be
 * dropped on the floor.
 *
 * <p>Cells are matched by <b>column index</b>, not by list position, because a row may have gaps —
 * column B can be absent while column C is present.
 */
public final class SheetFormattingAnnotator {

    public List<CellFormatChange> changesFor(SheetRow leftRow, SheetRow rightRow) {
        if (leftRow == null || rightRow == null) {
            return List.of();
        }
        List<CellFormatChange> changes = new ArrayList<>();
        int highestColumn = highestColumn(leftRow, rightRow);
        for (int column = 1; column <= highestColumn; column++) {
            SheetCell leftCell = cellAt(leftRow, column);
            SheetCell rightCell = cellAt(rightRow, column);
            if (leftCell == null || rightCell == null) {
                // An added or removed cell is already reported as a content change; its formatting
                // has nothing to be compared against.
                continue;
            }
            changes.addAll(changesFor(column, leftCell, rightCell));
        }
        return changes;
    }

    private List<CellFormatChange> changesFor(
            int column, SheetCell leftCell, SheetCell rightCell) {
        List<CellFormatChange> changes = new ArrayList<>();

        if (!Objects.equals(leftCell.bold(), rightCell.bold())) {
            if (Boolean.TRUE.equals(rightCell.bold())) {
                changes.add(new CellFormatChange(column, "bold applied"));
            } else if (Boolean.TRUE.equals(leftCell.bold())) {
                changes.add(new CellFormatChange(column, "bold removed"));
            }
        }

        if (!Objects.equals(leftCell.fillColor(), rightCell.fillColor())) {
            if (rightCell.fillColor() != null) {
                changes.add(new CellFormatChange(
                        column, "fill changed to " + rightCell.fillColor()));
            } else {
                changes.add(new CellFormatChange(column, "fill removed"));
            }
        }
        return changes;
    }

    private int highestColumn(SheetRow leftRow, SheetRow rightRow) {
        int highest = 0;
        for (SheetCell cell : leftRow.cells()) {
            highest = Math.max(highest, cell.column());
        }
        for (SheetCell cell : rightRow.cells()) {
            highest = Math.max(highest, cell.column());
        }
        return highest;
    }

    private SheetCell cellAt(SheetRow row, int column) {
        for (SheetCell cell : row.cells()) {
            if (cell.column() == column) {
                return cell;
            }
        }
        return null;
    }
}
