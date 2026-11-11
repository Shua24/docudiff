package dev.docudiff.ui;

import dev.docudiff.diff.CellFormatChange;
import dev.docudiff.diff.DiffStatus;
import dev.docudiff.diff.RowDiff;
import dev.docudiff.document.SheetCell;
import dev.docudiff.document.SheetRow;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One row of the spreadsheet diff: status marker, row number, then the cells.
 *
 * <p>Split out of {@link SheetDiffView} so that adding search highlighting did not push the view
 * past the project's file-length limit, and so the cell layout can be read on its own.
 */
final class SheetRowView {

    private static final int MAX_DISPLAY_VALUE_LENGTH = 60;

    private SheetRowView() {
    }

    /**
     * Renders one row and appends a {@link MatchSpot} for every search hit it contains.
     *
     * @return the row, ready to add to the sheet
     */
    static Region render(
            RowDiff rowDiff,
            int rowIndex,
            int columnCount,
            String query,
            int currentMatch,
            List<MatchSpot> matchSpots) {
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
        boolean unchanged = !rowDiff.isChanged();

        for (int column = 1; column <= columnCount; column++) {
            // Every match appends to matchSpots, so its size is always the next match's index.
            Node cellNode = cellNode(rowDiff, column, changedColumns, query, currentMatch,
                    matchSpots, rowIndex, unchanged);
            line.add(cellNode, column + 1, 0);
            line.getColumnConstraints().add(tabularColumn());
        }

        if (rowDiff.formatChanges().isEmpty()) {
            return line;
        }
        // A formatting-only change has no cell value to point at, so it is reported underneath the
        // row, exactly as the document diff reports "formatting: bold applied" under a paragraph.
        VBox withNotes = new VBox(2, line);
        for (CellFormatChange change : rowDiff.formatChanges()) {
            withNotes.getChildren().add(UiParts.muted("formatting: "
                    + SheetDiffView.columnName(change.column()) + " " + change.description()));
        }
        return withNotes;
    }

    /** One cell. For a changed cell the old value is shown struck through, then the new value. */
    private static Node cellNode(
            RowDiff rowDiff,
            int column,
            Set<Integer> changedColumns,
            String query,
            int currentMatch,
            List<MatchSpot> matchSpots,
            int rowIndex,
            boolean unchanged) {
        SheetCell leftCell = cellAt(rowDiff.left(), column);
        SheetCell rightCell = cellAt(rowDiff.right(), column);

        HBox box = new HBox(4);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("sheet-cell");

        boolean isChanged = changedColumns.contains(column);
        if (!isChanged) {
            addValue(box, rightCell != null ? rightCell : leftCell, null, query, currentMatch,
                    matchSpots, rowIndex, unchanged);
            return box;
        }

        box.getStyleClass().add("sheet-cell-changed");
        if (leftCell != null && rightCell == null) {
            addValue(box, leftCell, "sheet-value-removed", query, currentMatch,
                    matchSpots, rowIndex, unchanged);
            return box;
        }
        if (leftCell == null && rightCell != null) {
            addValue(box, rightCell, "sheet-value-added", query, currentMatch,
                    matchSpots, rowIndex, unchanged);
            return box;
        }

        addValue(box, leftCell, "sheet-value-removed", query, currentMatch,
                matchSpots, rowIndex, unchanged);
        box.getChildren().add(arrow());
        addValue(box, rightCell, "sheet-value-added", query, currentMatch,
                matchSpots, rowIndex, unchanged);

        if (rightCell != null && rightCell.formula() != null) {
            Label formula = new Label("=" + rightCell.formula());
            formula.getStyleClass().add("sheet-cell-formula");
            box.getChildren().add(formula);
        }
        return box;
    }

    /** Adds one value to the cell box and records any matches it contains. */
    private static void addValue(
            HBox box,
            SheetCell cell,
            String styleClass,
            String query,
            int currentMatch,
            List<MatchSpot> matchSpots,
            int rowIndex,
            boolean unchanged) {
        String text = cell == null || cell.displayedValue() == null
                ? "" : cell.displayedValue();
        if (text.length() > MAX_DISPLAY_VALUE_LENGTH) {
            text = text.substring(0, MAX_DISPLAY_VALUE_LENGTH - 1) + "…";
        }
        SheetCellText.Built built = SheetCellText.build(
                text, styleClass, query, currentMatch, matchSpots.size());
        box.getChildren().add(built.node());
        for (int index = 0; index < built.matchCount(); index++) {
            matchSpots.add(new MatchSpot(rowIndex, unchanged, "an unchanged row"));
        }
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
