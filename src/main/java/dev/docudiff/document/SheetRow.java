package dev.docudiff.document;

import java.util.List;

/**
 * One row of a sheet. The signature is what row alignment compares: a stable identity built from
 * the row's cell contents, so an inserted row reads as one insertion instead of shifting every
 * row below it.
 *
 * @param rowNumber the 1-based row number in the sheet
 * @param signature the joined cell contents used for alignment
 * @param cells     the cells in column order
 */
public record SheetRow(int rowNumber, String signature, List<SheetCell> cells) {

    public SheetRow {
        cells = List.copyOf(cells);
    }
}
