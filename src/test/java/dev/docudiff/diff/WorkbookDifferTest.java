package dev.docudiff.diff;

import dev.docudiff.document.SheetCell;
import dev.docudiff.document.SheetModel;
import dev.docudiff.document.SheetRow;
import dev.docudiff.document.WorkbookModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-sheet and cell-formatting behaviour of the workbook diff.
 *
 * <p>Both covered defects shared a cause: the comparer took {@code sheets().get(0)} and the row
 * aligner compared content only. A change on the second tab, and a change that altered only a
 * cell's appearance, were therefore both reported as "identical" — a confident wrong answer,
 * which is worse than a missing feature.
 */
class WorkbookDifferTest {

    @Test
    @DisplayName("a change on the third of five sheets is reported")
    void thirdSheetOfFiveIsReported() {
        // Two sheets cannot tell "compares all sheets" apart from "compares the first two".
        // Five can: the change sits past any plausible off-by-one or first-N shortcut.
        WorkbookModel left = workbook(
                sheet("S1", row(1, "a")),
                sheet("S2", row(1, "b")),
                sheet("S3", row(1, "before")),
                sheet("S4", row(1, "d")),
                sheet("S5", row(1, "e")));
        WorkbookModel right = workbook(
                sheet("S1", row(1, "a")),
                sheet("S2", row(1, "b")),
                sheet("S3", row(1, "after")),
                sheet("S4", row(1, "d")),
                sheet("S5", row(1, "e")));

        List<SheetDiff> sheetDiffs = new WorkbookDiffer().diff(left, right);

        assertEquals(5, sheetDiffs.size(), "every sheet must be compared, not just the first few");
        assertEquals(List.of(
                        DiffStatus.UNCHANGED,
                        DiffStatus.UNCHANGED,
                        DiffStatus.MODIFIED,
                        DiffStatus.UNCHANGED,
                        DiffStatus.UNCHANGED),
                statuses(sheetDiffs));
    }

    @Test
    @DisplayName("changes on non-adjacent sheets are each reported, not merged into one")
    void nonAdjacentSheetsAreEachReported() {
        WorkbookModel left = workbook(
                sheet("S1", row(1, "a")),
                sheet("S2", row(1, "two-before")),
                sheet("S3", row(1, "c")),
                sheet("S4", row(1, "four-before")),
                sheet("S5", row(1, "e")));
        WorkbookModel right = workbook(
                sheet("S1", row(1, "a")),
                sheet("S2", row(1, "two-after")),
                sheet("S3", row(1, "c")),
                sheet("S4", row(1, "four-after")),
                sheet("S5", row(1, "e")));

        List<SheetDiff> sheetDiffs = new WorkbookDiffer().diff(left, right);

        assertEquals(List.of(
                        DiffStatus.UNCHANGED,
                        DiffStatus.MODIFIED,
                        DiffStatus.UNCHANGED,
                        DiffStatus.MODIFIED,
                        DiffStatus.UNCHANGED),
                statuses(sheetDiffs));
        assertEquals(2, sheetDiffs.stream().filter(SheetDiff::isChanged).count());
        assertEquals(2, sheetDiffs.stream().mapToLong(SheetDiff::changedRowCount).sum());
    }

    @Test
    @DisplayName("every sheet of a five-sheet workbook appears, in workbook order")
    void allSheetsAppearInOrder() {
        WorkbookModel left = workbook(
                sheet("Alpha", row(1, "1")),
                sheet("Beta", row(1, "2")),
                sheet("Gamma", row(1, "3")),
                sheet("Delta", row(1, "4")),
                sheet("Epsilon", row(1, "5")));
        WorkbookModel right = workbook(
                sheet("Alpha", row(1, "1")),
                sheet("Beta", row(1, "2")),
                sheet("Gamma", row(1, "3-changed")),
                sheet("Delta", row(1, "4")),
                sheet("Epsilon", row(1, "5-changed")));

        List<SheetDiff> sheetDiffs = new WorkbookDiffer().diff(left, right);

        assertEquals(
                List.of("Alpha", "Beta", "Gamma", "Delta", "Epsilon"),
                sheetDiffs.stream().map(SheetDiff::name).toList(),
                "the last sheet is as much part of the diff as the first");
        assertEquals(2, sheetDiffs.stream().filter(SheetDiff::isChanged).count(),
                "a late sheet changing must be found, not swallowed by iteration that stops early");
    }

    @Test
    @DisplayName("a change on the second sheet is reported, not silently ignored")
    void secondSheetChangeIsReported() {
        WorkbookModel left = workbook(
                sheet("First", row(1, "unchanged")),
                sheet("Second", row(1, "before")));
        WorkbookModel right = workbook(
                sheet("First", row(1, "unchanged")),
                sheet("Second", row(1, "after")));

        List<SheetDiff> sheetDiffs = new WorkbookDiffer().diff(left, right);

        assertEquals(2, sheetDiffs.size());
        assertEquals("First", sheetDiffs.get(0).name());
        assertEquals(DiffStatus.UNCHANGED, sheetDiffs.get(0).status());
        assertEquals("Second", sheetDiffs.get(1).name());
        assertEquals(DiffStatus.MODIFIED, sheetDiffs.get(1).status());
        assertEquals(1, sheetDiffs.get(1).changedRowCount());
    }

    @Test
    @DisplayName("a sheet added on the right is reported as added, with all its rows")
    void addedSheetIsReported() {
        WorkbookModel left = workbook(sheet("Only", row(1, "a")));
        WorkbookModel right = workbook(
                sheet("Only", row(1, "a")),
                sheet("Appendix", row(1, "new1"), row(2, "new2")));

        List<SheetDiff> sheetDiffs = new WorkbookDiffer().diff(left, right);

        assertEquals(2, sheetDiffs.size());
        assertEquals(DiffStatus.ADDED, sheetDiffs.get(1).status());
        assertEquals(2, sheetDiffs.get(1).changedRowCount());
    }

    @Test
    @DisplayName("a sheet deleted on the right is reported as removed")
    void removedSheetIsReported() {
        WorkbookModel left = workbook(
                sheet("Kept", row(1, "a")),
                sheet("Dropped", row(1, "gone")));
        WorkbookModel right = workbook(sheet("Kept", row(1, "a")));

        List<SheetDiff> sheetDiffs = new WorkbookDiffer().diff(left, right);

        assertEquals(2, sheetDiffs.size());
        assertEquals("Dropped", sheetDiffs.get(1).name());
        assertEquals(DiffStatus.REMOVED, sheetDiffs.get(1).status());
    }

    @Test
    @DisplayName("sheets are matched by name, so inserting one does not rewrite the rest")
    void sheetsAreMatchedByName() {
        WorkbookModel left = workbook(
                sheet("Alpha", row(1, "a")),
                sheet("Beta", row(1, "b")));
        WorkbookModel right = workbook(
                sheet("New", row(1, "x")),
                sheet("Alpha", row(1, "a")),
                sheet("Beta", row(1, "b")));

        List<SheetDiff> sheetDiffs = new WorkbookDiffer().diff(left, right);

        assertEquals(3, sheetDiffs.size());
        assertEquals(DiffStatus.ADDED, sheetDiffs.get(0).status());
        assertEquals(DiffStatus.UNCHANGED, sheetDiffs.get(1).status());
        assertEquals(DiffStatus.UNCHANGED, sheetDiffs.get(2).status());
    }

    @Test
    @DisplayName("a bold-only change is reported even though no text moved")
    void boldOnlyChangeIsReported() {
        WorkbookModel left = workbook(sheet("S", cellRow(1, plain("header"))));
        WorkbookModel right = workbook(sheet("S", cellRow(1, bold("header"))));

        List<SheetDiff> sheetDiffs = new WorkbookDiffer().diff(left, right);

        SheetDiff onlySheet = sheetDiffs.get(0);
        assertEquals(DiffStatus.MODIFIED, onlySheet.status());
        assertEquals(1, onlySheet.changedRowCount(),
                "a formatting-only change is still a change");
        RowDiff rowDiff = onlySheet.rowDiffs().get(0);
        assertTrue(rowDiff.isFormattingOnly(),
                "no cell content changed, only the presentation");
        assertEquals(1, rowDiff.formatChanges().size());
        assertEquals("bold applied", rowDiff.formatChanges().get(0).description());
    }

    @Test
    @DisplayName("a fill-only change is reported with the new colour")
    void fillOnlyChangeIsReported() {
        WorkbookModel left = workbook(sheet("S", cellRow(1, withFill("total", "FFFFFF"))));
        WorkbookModel right = workbook(sheet("S", cellRow(1, withFill("total", "FFFF00"))));

        RowDiff rowDiff = new WorkbookDiffer().diff(left, right).get(0).rowDiffs().get(0);

        assertEquals(1, rowDiff.formatChanges().size());
        assertEquals("fill changed to FFFF00", rowDiff.formatChanges().get(0).description());
    }

    @Test
    @DisplayName("identical workbooks report no changes at all")
    void identicalWorkbooksAreUnchanged() {
        WorkbookModel left = workbook(sheet("S", cellRow(1, plain("same"))));
        WorkbookModel right = workbook(sheet("S", cellRow(1, plain("same"))));

        List<SheetDiff> sheetDiffs = new WorkbookDiffer().diff(left, right);

        assertEquals(0, sheetDiffs.stream().filter(SheetDiff::isChanged).count());
    }

    private List<DiffStatus> statuses(List<SheetDiff> sheetDiffs) {
        return sheetDiffs.stream().map(SheetDiff::status).toList();
    }

    private WorkbookModel workbook(SheetModel... sheets) {
        return new WorkbookModel(List.of(sheets));
    }

    private SheetModel sheet(String name, SheetRow... rows) {
        return new SheetModel(name, List.of(rows));
    }

    private SheetRow row(int rowNumber, String firstCellValue) {
        return cellRow(rowNumber, plain(firstCellValue));
    }

    private SheetRow cellRow(int rowNumber, SheetCell... cells) {
        List<SheetCell> cellList = new ArrayList<>();
        StringBuilder signature = new StringBuilder();
        for (int index = 0; index < cells.length; index++) {
            SheetCell cell = new SheetCell(index + 1, null, cells[index].displayedValue(),
                    cells[index].bold(), cells[index].fillColor());
            cellList.add(cell);
            signature.append(cell.column()).append(':').append(cell.contentKey()).append('\u001f');
        }
        return new SheetRow(rowNumber, signature.toString(), cellList);
    }

    private SheetCell plain(String value) {
        return new SheetCell(1, null, value, null, null);
    }

    private SheetCell bold(String value) {
        return new SheetCell(1, null, value, true, null);
    }

    private SheetCell withFill(String value, String fillColor) {
        return new SheetCell(1, null, value, null, fillColor);
    }
}
