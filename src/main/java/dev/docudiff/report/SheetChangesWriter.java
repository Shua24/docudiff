package dev.docudiff.report;

import dev.docudiff.diff.CellFormatChange;
import dev.docudiff.diff.DiffStatus;
import dev.docudiff.diff.RowDiff;
import dev.docudiff.diff.SheetDiff;
import dev.docudiff.document.SheetCell;
import dev.docudiff.document.SheetRow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes the spreadsheet half of a review report: one table per changed row, with the grid kept so
 * a cell that moved or changed is visible in place.
 *
 * <p>A spreadsheet diff does not map onto inline paragraphs — the change is a cell, not a word —
 * so each changed row is written as a small before/after table with the changed cells shaded and
 * bold. The row context line above it gives the row numbers, which are real spreadsheet identity
 * rather than an internal key.
 */
final class SheetChangesWriter {

    private static final int MAXIMUM_CHANGED_ROWS = 100;
    private static final String ADDED_FILL = "E8F1F5";
    private static final String REMOVED_FILL = "F7EAE7";
    private static final String CHANGED_FILL = "FFF4CC";
    private static final String BORDER_COLOR = "BFBFBF";

    void write(XWPFDocument document, List<SheetDiff> sheetDiffs) {
        for (SheetDiff sheetDiff : sheetDiffs) {
            if (!sheetDiff.isChanged()) {
                continue;
            }
            ReportRuns.heading(document, "Sheet \"" + sheetDiff.name() + "\"");
            if (sheetDiff.status() == DiffStatus.ADDED) {
                ReportRuns.muted(document, "This sheet is new.");
            } else if (sheetDiff.status() == DiffStatus.REMOVED) {
                ReportRuns.muted(document, "This sheet no longer exists.");
            }
            writeRows(document, sheetDiff);
        }
    }

    private void writeRows(XWPFDocument document, SheetDiff sheetDiff) {
        int written = 0;
        for (RowDiff rowDiff : sheetDiff.rowDiffs()) {
            if (!rowDiff.isChanged()) {
                continue;
            }
            if (written >= MAXIMUM_CHANGED_ROWS) {
                ReportRuns.muted(document, "… and more changed rows in this sheet; the report "
                        + "stops after " + MAXIMUM_CHANGED_ROWS + ".");
                return;
            }
            writeRow(document, rowDiff);
            written++;
        }
    }

    private void writeRow(XWPFDocument document, RowDiff rowDiff) {
        ReportRuns.muted(document, contextLine(rowDiff));
        for (CellFormatChange formatChange : rowDiff.formatChanges()) {
            ReportRuns.muted(document, "  column " + formatChange.column() + ": "
                    + formatChange.description());
        }

        int columns = Math.max(1, columnCount(rowDiff));
        boolean bothSides = rowDiff.left() != null && rowDiff.right() != null;
        XWPFTable table = document.createTable(bothSides ? 2 : 1, columns);
        table.setWidth("100%");
        applyBorders(table);

        if (rowDiff.left() != null) {
            fillRow(table.getRow(0), rowDiff.left(), markedColumns(rowDiff),
                    rowDiff.status() == DiffStatus.REMOVED ? REMOVED_FILL : null);
        }
        if (rowDiff.right() != null) {
            fillRow(table.getRow(bothSides ? 1 : 0), rowDiff.right(), markedColumns(rowDiff),
                    rowDiff.status() == DiffStatus.ADDED ? ADDED_FILL : null);
        }
        // A blank paragraph after a table stops two consecutive tables from merging in Word.
        ReportRuns.paragraph(document);
    }

    private void fillRow(
            XWPFTableRow tableRow, SheetRow sheetRow, Set<Integer> markedColumns, String fill) {
        List<XWPFTableCell> cells = tableRow.getTableCells();
        for (int index = 0; index < cells.size(); index++) {
            int column = index + 1;
            XWPFTableCell cell = cells.get(index);
            SheetCell sheetCell = cellAt(sheetRow, column);
            cell.setText(ReportText.cellText(sheetCell));

            boolean marked = fill != null || markedColumns.contains(column);
            if (!marked) {
                continue;
            }
            cell.setColor(fill == null ? CHANGED_FILL : fill);
            if (!cell.getParagraphs().isEmpty()
                    && !cell.getParagraphs().get(0).getRuns().isEmpty()) {
                cell.getParagraphs().get(0).getRuns().get(0).setBold(true);
            }
        }
    }

    private SheetCell cellAt(SheetRow sheetRow, int column) {
        for (SheetCell cell : sheetRow.cells()) {
            if (cell.column() == column) {
                return cell;
            }
        }
        return null;
    }

    /** Columns whose content or formatting changed, 1-based. */
    private Set<Integer> markedColumns(RowDiff rowDiff) {
        Set<Integer> columns = new LinkedHashSet<>(rowDiff.changedColumns());
        for (CellFormatChange formatChange : rowDiff.formatChanges()) {
            columns.add(formatChange.column());
        }
        return columns;
    }

    private int columnCount(RowDiff rowDiff) {
        int maximum = 0;
        maximum = Math.max(maximum, highestColumn(rowDiff.left()));
        maximum = Math.max(maximum, highestColumn(rowDiff.right()));
        return maximum;
    }

    private int highestColumn(SheetRow sheetRow) {
        if (sheetRow == null) {
            return 0;
        }
        int highest = 0;
        for (SheetCell cell : sheetRow.cells()) {
            highest = Math.max(highest, cell.column());
        }
        return highest;
    }

    private String contextLine(RowDiff rowDiff) {
        if (rowDiff.status() == DiffStatus.ADDED) {
            return "Row " + rowDiff.right().rowNumber() + " is new.";
        }
        if (rowDiff.status() == DiffStatus.REMOVED) {
            return "Row " + rowDiff.left().rowNumber() + " was removed.";
        }
        if (rowDiff.isFormattingOnly()) {
            return "Row " + rowDiff.right().rowNumber() + ": formatting changed, values are the "
                    + "same.";
        }
        return "Row " + rowDiff.left().rowNumber() + " became row "
                + rowDiff.right().rowNumber() + ".";
    }

    private void applyBorders(XWPFTable table) {
        XWPFTable.XWPFBorderType single = XWPFTable.XWPFBorderType.SINGLE;
        table.setTopBorder(single, 4, 0, BORDER_COLOR);
        table.setBottomBorder(single, 4, 0, BORDER_COLOR);
        table.setLeftBorder(single, 4, 0, BORDER_COLOR);
        table.setRightBorder(single, 4, 0, BORDER_COLOR);
        table.setInsideHBorder(single, 4, 0, BORDER_COLOR);
        table.setInsideVBorder(single, 4, 0, BORDER_COLOR);
    }
}
