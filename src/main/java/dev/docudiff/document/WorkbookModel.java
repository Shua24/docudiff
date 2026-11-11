package dev.docudiff.document;

import java.util.List;

/**
 * A spreadsheet reduced to sheets of rows of cells. Kept separate from {@link Block} because a
 * sheet diff aligns rows, not paragraphs, and mixing the two shapes would make both worse.
 */
public record WorkbookModel(List<SheetModel> sheets) {

    public WorkbookModel {
        sheets = List.copyOf(sheets);
    }
}
