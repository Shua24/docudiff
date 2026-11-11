package dev.docudiff.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SheetDiffViewTest {

    @Test
    @DisplayName("column 1 is A")
    void columnOneIsA() {
        assertEquals("A", SheetDiffView.columnName(1));
    }

    @Test
    @DisplayName("column 26 is Z")
    void columnTwentySixIsZ() {
        assertEquals("Z", SheetDiffView.columnName(26));
    }

    @Test
    @DisplayName("column 27 rolls over to AA")
    void columnTwentySevenIsAA() {
        assertEquals("AA", SheetDiffView.columnName(27));
    }

    @Test
    @DisplayName("column 52 is AZ and 53 is BA")
    void doubleLetterColumnsAreCorrect() {
        assertEquals("AZ", SheetDiffView.columnName(52));
        assertEquals("BA", SheetDiffView.columnName(53));
    }

    @Test
    @DisplayName("column 702 is ZZ and 703 is AAA")
    void tripleLetterBoundaryIsCorrect() {
        assertEquals("ZZ", SheetDiffView.columnName(702));
        assertEquals("AAA", SheetDiffView.columnName(703));
    }
}
