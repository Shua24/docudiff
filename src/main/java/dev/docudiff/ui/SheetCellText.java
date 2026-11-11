package dev.docudiff.ui;

import dev.docudiff.search.SearchHighlighter;
import javafx.scene.Node;
import javafx.scene.text.TextFlow;

/**
 * One spreadsheet cell's text, with search hits highlighted.
 *
 * <p>A {@code Label} cannot style part of its text, so a cell that may contain a match is built as
 * a {@code TextFlow} of {@code Text} nodes instead. Cells with no query still go through here, so
 * there is one code path rather than two that can drift apart.
 */
final class SheetCellText {

    /** The built node plus how many matches it contributed, for the caller's running count. */
    record Built(Node node, int matchCount) {
    }

    private SheetCellText() {
    }

    static Built build(
            String text,
            String styleClass,
            String query,
            int currentMatch,
            int matchOffset) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("sheet-value-flow");
        // Keep the base cell-value class on the flow so its font size and colour cascade to the
        // Text children, which are what actually render.
        flow.getStyleClass().add("sheet-value");
        int matches = SearchHighlighter.appendHighlighted(
                flow, text, query, styleClass, currentMatch, matchOffset);
        return new Built(flow, matches);
    }
}
