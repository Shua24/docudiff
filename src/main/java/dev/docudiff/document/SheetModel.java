package dev.docudiff.document;

import java.util.List;

/**
 * One worksheet.
 *
 * @param name the sheet name as the user sees it
 * @param rows the rows in sheet order
 */
public record SheetModel(String name, List<SheetRow> rows) {

    public SheetModel {
        rows = List.copyOf(rows);
    }
}
