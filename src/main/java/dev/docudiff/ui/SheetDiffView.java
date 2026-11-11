package dev.docudiff.ui;

import dev.docudiff.diff.DiffStatus;
import dev.docudiff.diff.RowDiff;
import dev.docudiff.diff.SheetDiff;
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

import java.util.List;

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
 *
 * <p>While a search is active every row is rendered, not just the changed ones. Unchanged rows are
 * what a keyword is most likely to be sitting in, and skipping them would make the search box
 * silently unable to find things that are visibly in the sheet.
 */
public final class SheetDiffView {

    private SheetDiffView() {
    }

    /** The whole sheet diff with no search applied. */
    public static Region render(SheetDiff sheetDiff) {
        return render(sheetDiff, "", -1, false, null);
    }

    /**
     * The whole sheet diff: a header strip, a column ruler, then the rows.
     *
     * @param query          the search text; empty for no highlighting
     * @param currentMatch   index of the match to emphasise, or -1 for none
     * @param showUnchanged  true to render unchanged rows as well as changed ones
     * @param matchSpots     collects one entry per match found, or null to skip recording
     */
    public static Region render(
            SheetDiff sheetDiff,
            String query,
            int currentMatch,
            boolean showUnchanged,
            List<MatchSpot> matchSpots) {
        List<RowDiff> rowDiffs = sheetDiff.rowDiffs();
        VBox root = new VBox(0);
        root.getStyleClass().add("sheet-diff");

        int columnCount = highestColumn(rowDiffs);
        int changedRows = (int) rowDiffs.stream().filter(RowDiff::isChanged).count();

        root.getChildren().add(summaryBar(
                sheetDiff.name(), rowDiffs.size(), changedRows, showUnchanged, sheetDiff.status()));
        if (changedRows == 0 && !showUnchanged) {
            Label identical = UiParts.muted("The two sheets are identical.");
            identical.setPadding(new Insets(16, 12, 16, 12));
            root.getChildren().add(identical);
            return root;
        }

        root.getChildren().add(columnRuler(columnCount));
        for (int rowIndex = 0; rowIndex < rowDiffs.size(); rowIndex++) {
            RowDiff rowDiff = rowDiffs.get(rowIndex);
            if (!rowDiff.isChanged() && !showUnchanged) {
                continue;
            }
            root.getChildren().add(SheetRowView.render(
                    rowDiff, rowIndex, columnCount, query, currentMatch,
                    matchSpots == null ? List.of() : matchSpots));
        }
        return root;
    }

    private static Region summaryBar(
            String sheetName,
            int totalRows,
            int changedRows,
            boolean showUnchanged,
            DiffStatus sheetStatus) {
        Label sheetLabel = new Label(sheetName == null || sheetName.isBlank()
                ? "Sheet" : sheetName);
        sheetLabel.getStyleClass().add("sheet-diff-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label counts = new Label(countText(totalRows, changedRows, showUnchanged, sheetStatus));
        counts.getStyleClass().add("legend-note");

        HBox bar = new HBox(10, sheetLabel, spacer, counts);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 10, 6, 10));
        bar.getStyleClass().add("sheet-diff-summary");
        return bar;
    }

    /** The count has to say which mode it is counting in, or it silently lies while searching. */
    private static String countText(
            int totalRows, int changedRows, boolean showUnchanged, DiffStatus sheetStatus) {
        if (showUnchanged) {
            return "showing all " + totalRows + " row(s) · search active";
        }
        if (sheetStatus == DiffStatus.ADDED) {
            return "whole sheet added · " + totalRows + " row(s)";
        }
        if (sheetStatus == DiffStatus.REMOVED) {
            return "whole sheet removed · " + totalRows + " row(s)";
        }
        if (changedRows == 0) {
            return "no changes · " + totalRows + " row(s)";
        }
        return changedRows + " changed row(s) of " + totalRows;
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
}
