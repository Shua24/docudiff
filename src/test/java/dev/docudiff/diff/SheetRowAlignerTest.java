package dev.docudiff.diff;

import dev.docudiff.document.SheetRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SheetRowAlignerTest {

    @Test
    @DisplayName("a row inserted at the top of a 500-row sheet is one insertion")
    void rowInsertedAtTopIsOneInsertion() {
        List<SheetRow> before = rowsOf(1, 500);
        List<SheetRow> after = new ArrayList<>();
        after.add(row(0, "new-product", "999"));
        after.addAll(before);

        List<RowDiff> rowDiffs = new SheetRowAligner().align(before, after);

        long addedCount = rowDiffs.stream()
                .filter(rowDiff -> rowDiff.status() == DiffStatus.ADDED)
                .count();
        long modifiedCount = rowDiffs.stream()
                .filter(rowDiff -> rowDiff.status() == DiffStatus.MODIFIED)
                .count();
        assertEquals(1, addedCount, "one inserted row must read as one insertion");
        assertEquals(0, modifiedCount,
                "position-by-position comparison would report 501 changed rows; "
                        + "content alignment must report none");
    }

    @Test
    @DisplayName("a changed cell marks that column inside one modified row")
    void changedCellMarksColumn() {
        List<SheetRow> before = List.of(row(1, "alpha", "10"));
        List<SheetRow> after = List.of(row(1, "alpha", "11"));

        List<RowDiff> rowDiffs = new SheetRowAligner().align(before, after);

        assertEquals(1, rowDiffs.size());
        assertEquals(DiffStatus.MODIFIED, rowDiffs.get(0).status());
        assertEquals(List.of(2), rowDiffs.get(0).changedColumns());
    }

    @Test
    @DisplayName("identical sheets produce only unchanged rows")
    void identicalSheetsProduceUnchangedRows() {
        List<SheetRow> rows = rowsOf(1, 10);

        List<RowDiff> rowDiffs = new SheetRowAligner().align(rows, rows);

        assertEquals(10, rowDiffs.size());
        assertEquals(0, rowDiffs.stream()
                .filter(rowDiff -> rowDiff.status() != DiffStatus.UNCHANGED)
                .count());
    }

    @Test
    @DisplayName("a removed row is one removal")
    void removedRowIsOneRemoval() {
        List<SheetRow> before = rowsOf(1, 5);
        List<SheetRow> after = new ArrayList<>(before);
        after.remove(2);

        List<RowDiff> rowDiffs = new SheetRowAligner().align(before, after);

        long removedCount = rowDiffs.stream()
                .filter(rowDiff -> rowDiff.status() == DiffStatus.REMOVED)
                .count();
        assertEquals(1, removedCount);
    }

    private List<SheetRow> rowsOf(int first, int last) {
        List<SheetRow> rows = new ArrayList<>();
        for (int number = first; number <= last; number++) {
            rows.add(row(number, "product-" + number, String.valueOf(number * 10)));
        }
        return rows;
    }

    private SheetRow row(int rowNumber, String name, String amount) {
        String signature = "1:" + name + "\u001f2:" + amount + "\u001f";
        return new SheetRow(
                rowNumber,
                signature,
                List.of(
                        new dev.docudiff.document.SheetCell(1, null, name, null, null),
                        new dev.docudiff.document.SheetCell(2, null, amount, null, null)));
    }
}
