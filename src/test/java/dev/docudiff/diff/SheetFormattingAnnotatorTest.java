package dev.docudiff.diff;

import dev.docudiff.document.SheetCell;
import dev.docudiff.document.SheetRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SheetFormattingAnnotatorTest {

    @Test
    @DisplayName("applying bold to a cell is reported")
    void boldAppliedIsReported() {
        List<CellFormatChange> changes = annotate(
                row(cell(1, "header", null, null)),
                row(cell(1, "header", true, null)));

        assertEquals(1, changes.size());
        assertEquals("bold applied", changes.get(0).description());
        assertEquals(1, changes.get(0).column());
    }

    @Test
    @DisplayName("removing bold from a cell is reported")
    void boldRemovedIsReported() {
        List<CellFormatChange> changes = annotate(
                row(cell(1, "header", true, null)),
                row(cell(1, "header", false, null)));

        assertEquals(1, changes.size());
        assertEquals("bold removed", changes.get(0).description());
    }

    @Test
    @DisplayName("an inherited (null) bold becoming false is not a change")
    void nullToFalseIsNotAChange() {
        List<CellFormatChange> changes = annotate(
                row(cell(1, "x", null, null)),
                row(cell(1, "x", false, null)));

        assertEquals(0, changes.size(),
                "null means inherited and false means explicitly off; neither is visible to a "
                        + "reader, so reporting it would be noise");
    }

    @Test
    @DisplayName("changing a fill colour is reported with the new colour")
    void fillChangeIsReported() {
        List<CellFormatChange> changes = annotate(
                row(cell(2, "total", null, null)),
                row(cell(2, "total", null, "FFFF00")));

        assertEquals(1, changes.size());
        assertEquals("fill changed to FFFF00", changes.get(0).description());
    }

    @Test
    @DisplayName("removing a fill is reported as removed, not as changed to null")
    void fillRemovedIsReported() {
        List<CellFormatChange> changes = annotate(
                row(cell(2, "total", null, "FFFF00")),
                row(cell(2, "total", null, null)));

        assertEquals(1, changes.size());
        assertEquals("fill removed", changes.get(0).description());
    }

    @Test
    @DisplayName("identical formatting produces no annotations")
    void identicalFormattingIsSilent() {
        List<CellFormatChange> changes = annotate(
                row(cell(1, "a", true, "DDDDDD")),
                row(cell(1, "a", true, "DDDDDD")));

        assertEquals(0, changes.size());
    }

    @Test
    @DisplayName("a cell added on the right is not reported as a formatting change")
    void addedCellIsNotAFormattingChange() {
        List<CellFormatChange> changes = annotate(
                row(cell(1, "a", null, null)),
                row(cell(1, "a", null, null), cell(2, "new", true, "FFFF00")));

        assertEquals(0, changes.size(),
                "an added cell is a content change; there is no old formatting to compare against");
    }

    @Test
    @DisplayName("a gap in the row does not shift which cell is compared")
    void columnGapsAreMatchedByIndex() {
        // Column 2 is absent on both sides; column 3 changes. Matching by list position would
        // compare the wrong pair or skip the change entirely.
        List<CellFormatChange> changes = annotate(
                row(cell(1, "a", null, null), cell(3, "c", null, null)),
                row(cell(1, "a", null, null), cell(3, "c", true, null)));

        assertEquals(1, changes.size());
        assertEquals(3, changes.get(0).column());
        assertTrue(changes.get(0).description().contains("bold"));
    }

    @Test
    @DisplayName("several cells changing in one row are all reported")
    void severalChangesAreAllReported() {
        List<CellFormatChange> changes = annotate(
                row(cell(1, "a", null, null), cell(2, "b", null, null)),
                row(cell(1, "a", true, null), cell(2, "b", null, "00FF00")));

        assertEquals(2, changes.size());
        assertEquals(1, changes.get(0).column());
        assertEquals(2, changes.get(1).column());
    }

    private List<CellFormatChange> annotate(SheetRow leftRow, SheetRow rightRow) {
        return new SheetFormattingAnnotator().changesFor(leftRow, rightRow);
    }

    private SheetRow row(SheetCell... cells) {
        StringBuilder signature = new StringBuilder();
        for (SheetCell cell : cells) {
            signature.append(cell.column()).append(':').append(cell.contentKey()).append('\u001f');
        }
        return new SheetRow(1, signature.toString(), List.of(cells));
    }

    private SheetCell cell(int column, String value, Boolean bold, String fillColor) {
        return new SheetCell(column, null, value, bold, fillColor);
    }
}
