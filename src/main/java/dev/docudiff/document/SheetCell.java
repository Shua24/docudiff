package dev.docudiff.document;

/**
 * One spreadsheet cell.
 *
 * <p>The formula is the cell's CONTENT: a formula change is a diff even when the cached result is
 * identical. {@code displayedValue} is the value the user sees, carried as context.
 *
 * @param column         the 1-based column index
 * @param formula        the formula text without the leading {@code =}, or null for a literal cell
 * @param displayedValue the value as the spreadsheet renders it
 * @param bold           true/false when stated, null when inherited
 * @param fillColor      the fill colour when stated, null when inherited
 */
public record SheetCell(
        int column,
        String formula,
        String displayedValue,
        Boolean bold,
        String fillColor) {

    /**
     * What alignment and diffing compare. A formula cell is identified by its formula, a literal
     * cell by its displayed value.
     */
    public String contentKey() {
        if (formula != null && !formula.isBlank()) {
            return "=" + formula;
        }
        return displayedValue == null ? "" : displayedValue;
    }
}
