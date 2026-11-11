package dev.docudiff.ui;

import dev.docudiff.diff.DiffStatus;
import dev.docudiff.diff.RowDiff;
import dev.docudiff.document.SheetCell;
import dev.docudiff.document.SheetRow;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders a spreadsheet diff as an actual grid of cells, with the changed cells highlighted.
 *
 * <p>Replaces a placeholder that printed {@code "~ row 2 columns [2]"} and nothing else — a line
 * that named a row and a column number but never showed either value, so it could not answer the
 * only question a spreadsheet diff exists to answer: what did this cell used to be, and what is it
 * now?
 *
 * <p>Rows carry their own cell lists (from {@link RowDiff}), and cells are laid out by their
 * absolute {@code column} index, so an inserted column lands in the right place instead of
 * shifting everything to the left.
 */
public final class SheetDiffView {

    private static final int MAX_DISPLAY_VALUE_LENGTH = 60;

    private SheetDiffView() {
    }

    /** The whole sheet diff: a header strip, a column ruler, then the rows. */
    public static Region render(List<RowDiff> rowDiffs, String sheetName) {
        VBox root = new VBox(0);
        root.getStyleClass().add("sheet-diff");

        int columnCount = highestColumn(rowDiffs);
        int changedRows = (int) rowDiffs.stream()
                .filter(rowDiff -> rowDiff.status() != DiffStatus.UNCHANGED)
                .count();

        root.getChildren().add(summaryBar(sheetName, rowDiffs.size(), changedRows));
        if (changedRows == 0) {
            Label identical = UiParts.muted("The two sheets are identical.");
            identical.setPadding(new Insets(16, 12, 16, 12));
            root.getChildren().add(identical);
            return root;
        }

        root.getChildren().add(columnRuler(columnCount));
        for (RowDiff rowDiff : rowDiffs) {
            if (rowDiff.status() == DiffStatus.UNCHANGED) {
                continue;
            }
            root.getChildren().add(rowLine(rowDiff, columnCount));
        }
        return root;
    }

    private static Region summaryBar(String sheetName, int totalRows, int changedRows) {
        Label sheetLabel = new Label(sheetName == null || sheetName.isBlank()
                ? "Sheet" : sheetName);
        sheetLabel.getStyleClass().add("sheet-diff-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label counts = new Label(changedRows + " changed row(s) of " + totalRows);
        counts.getStyleClass().add("legend-note");

        HBox bar = new HBox(10, sheetLabel, spacer, counts);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 10, 6, 10));
        bar.getStyleClass().add("sheet-diff-summary");
        return bar;
    }

    private static Region columnRuler(int columnCount) {
        GridPane ruler = new GridPane();
        ruler.getStyleClass().add("sheet-diff-ruler");
        ruler.setPadding(new Insets(2, 8, 2, 8));
        addGutterColumns(ruler);

        for (int column = 1; column <= columnCount; column++) {
            Label heading = new Label(columnName(column));
            heading.getStyleClass().add("sheet-column-heading");
            heading.setMaxWidth(Double.MAX_VALUE);
            heading.setAlignment(Pos.CENTER_LEFT);
            ruler.add(heading, column + 1, 0);
            ruler.getColumnConstraints().add(tabularColumn());
        }
        return ruler;
    }

    private static Region rowLine(RowDiff rowDiff, int columnCount) {
        GridPane line = new GridPane();
        line.getStyleClass().add("sheet-diff-row");
        line.getStyleClass().add(statusClass(rowDiff.status()));
        line.setPadding(new Insets(3, 8, 3, 8));
        addGutterColumns(line);

        Label marker = new Label(markerFor(rowDiff.status()));
        marker.getStyleClass().add("sheet-row-marker");
        line.add(marker, 0, 0);

        String rowNumberText = rowDiff.right() != null
                ? String.valueOf(rowDiff.right().rowNumber())
                : String.valueOf(rowDiff.left().rowNumber());
        Label number = new Label(rowNumberText);
        number.getStyleClass().add("sheet-row-number");
        line.add(number, 1, 0);

        Set<Integer> changedColumns = new HashSet<>(rowDiff.changedColumns());

        for (int column = 1; column <= columnCount; column++) {
            Region cellNode = cellNode(rowDiff, column, changedColumns);
            line.add(cellNode, column + 1, 0);
            line.getColumnConstraints().add(tabularColumn());
        }
        return line;
    }

    /**
     * One cell. For a changed cell the old value is shown struck through and the new value
     * follows it, because "12" alone does not tell a reader what it used to be.
     */
    private static Region cellNode(RowDiff rowDiff, int column, Set<Integer> changedColumns) {
        SheetCell leftCell = cellAt(rowDiff.left(), column);
        SheetCell rightCell = cellAt(rowDiff.right(), column);

        HBox box = new HBox(4);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("sheet-cell");

        boolean isChanged = changedColumns.contains(column);
        if (!isChanged) {
            box.getChildren().add(valueLabel(rightCell != null ? rightCell : leftCell));
            return box;
        }

        box.getStyleClass().add("sheet-cell-changed");
        if (leftCell != null && rightCell == null) {
            Label removed = valueLabel(leftCell);
            removed.getStyleClass().add("sheet-value-removed");
            box.getChildren().add(removed);
            return box;
        }
        if (leftCell == null && rightCell != null) {
            Label added = valueLabel(rightCell);
            added.getStyleClass().add("sheet-value-added");
            box.getChildren().add(added);
            return box;
        }

        Label was = valueLabel(leftCell);
        was.getStyleClass().add("sheet-value-removed");
        Label now = valueLabel(rightCell);
        now.getStyleClass().add("sheet-value-added");
        box.getChildren().addAll(was, arrow(), now);

        if (rightCell != null && rightCell.formula() != null) {
            Label formula = new Label("=" + rightCell.formula());
            formula.getStyleClass().add("sheet-cell-formula");
            box.getChildren().add(formula);
        }
        return box;
    }

    private static Label valueLabel(SheetCell cell) {
        String text = cell == null || cell.displayedValue() == null
                ? "" : cell.displayedValue();
        if (text.length() > MAX_DISPLAY_VALUE_LENGTH) {
            text = text.substring(0, MAX_DISPLAY_VALUE_LENGTH - 1) + "…";
        }
        Label label = new Label(text);
        label.getStyleClass().add("sheet-value");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static Label arrow() {
        Label arrow = new Label("→");
        arrow.getStyleClass().add("sheet-arrow");
        return arrow;
    }

    /** Two fixed columns before the data: the status marker, then the row number. */
    private static void addGutterColumns(GridPane grid) {
        ColumnConstraints markerColumn = new ColumnConstraints();
        markerColumn.setMinWidth(14);
        markerColumn.setPrefWidth(14);
        grid.getColumnConstraints().add(markerColumn);

        ColumnConstraints numberColumn = new ColumnConstraints();
        numberColumn.setMinWidth(34);
        numberColumn.setPrefWidth(34);
        grid.getColumnConstraints().add(numberColumn);
    }

    private static ColumnConstraints tabularColumn() {
        ColumnConstraints column = new ColumnConstraints();
        column.setMinWidth(80);
        column.setPrefWidth(120);
        column.setHgrow(Priority.SOMETIMES);
        return column;
    }

    private static SheetCell cellAt(SheetRow row, int column) {
        if (row == null) {
            return null;
        }
        for (SheetCell cell : row.cells()) {
            if (cell.column() == column) {
                return cell;
            }
        }
        return null;
    }

    private static int highestColumn(List<RowDiff> rowDiffs) {
        int highest = 1;
        for (RowDiff rowDiff : rowDiffs) {
            for (SheetRow row : new SheetRow[]{rowDiff.left(), rowDiff.right()}) {
                if (row == null) {
                    continue;
                }
                for (SheetCell cell : row.cells()) {
                    highest = Math.max(highest, cell.column());
                }
            }
        }
        return highest;
    }

    /** Spreadsheet column letters: 1 to A, 26 to Z, 27 to AA. */
    static String columnName(int columnNumber) {
        StringBuilder name = new StringBuilder();
        int remaining = columnNumber;
        while (remaining > 0) {
            int index = (remaining - 1) % 26;
            name.insert(0, (char) ('A' + index));
            remaining = (remaining - 1) / 26;
        }
        String result = name.toString();
        return result;
    }

    private static String markerFor(DiffStatus status) {
        return switch (status) {
            case ADDED -> "+";
            case REMOVED -> "−";
            case MODIFIED -> "~";
            case UNCHANGED -> "";
        };
    }

    private static String statusClass(DiffStatus status) {
        return switch (status) {
            case ADDED -> "diff-added";
            case REMOVED -> "diff-removed";
            case MODIFIED -> "diff-modified";
            case UNCHANGED -> "diff-unchanged";
        };
    }
}
