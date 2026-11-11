package dev.docudiff.search;

import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;

/**
 * Builds JavaFX text nodes with the search hits highlighted.
 *
 * <p>Highlighting cannot be a post-processing pass over the scene graph: the diff renderer already
 * emits one {@link Text} node per word mark, and a match can straddle a word boundary or sit in
 * the middle of one. So the renderer asks this class for nodes, and each text is split at the
 * match boundaries before it reaches the scene.
 */
public final class SearchHighlighter {

    private static final String HIT_STYLE = "search-hit";
    private static final String HIT_CURRENT_STYLE = "search-hit-current";

    private SearchHighlighter() {
    }

    /**
     * Appends {@code text} to {@code flow}, splitting it so every match gets its own styled node.
     *
     * @param baseStyle the style class the unhighlighted text should carry, or null for none
     * @param currentMatchIndex index of the match that should be emphasised, or -1 for none
     * @param matchOffsetInFlow how many matches were already emitted before this text
     * @return the number of matches this text contributed
     */
    public static int appendHighlighted(
            TextFlow flow,
            String text,
            String query,
            String baseStyle,
            int currentMatchIndex,
            int matchOffsetInFlow) {
        List<TextSearch.Match> matches = TextSearch.find(text, query);
        if (matches.isEmpty()) {
            Text plain = new Text(text);
            if (baseStyle != null) {
                plain.getStyleClass().add(baseStyle);
            }
            flow.getChildren().add(plain);
            return 0;
        }

        int cursor = 0;
        for (int index = 0; index < matches.size(); index++) {
            TextSearch.Match match = matches.get(index);
            if (match.start() > cursor) {
                Text before = new Text(text.substring(cursor, match.start()));
                if (baseStyle != null) {
                    before.getStyleClass().add(baseStyle);
                }
                flow.getChildren().add(before);
            }

            Text hit = new Text(text.substring(match.start(), match.end()));
            if (baseStyle != null) {
                hit.getStyleClass().add(baseStyle);
            }
            hit.getStyleClass().add(HIT_STYLE);
            int absoluteMatchIndex = matchOffsetInFlow + index;
            if (absoluteMatchIndex == currentMatchIndex) {
                hit.getStyleClass().add(HIT_CURRENT_STYLE);
            }
            flow.getChildren().add(hit);
            cursor = match.end();
        }

        if (cursor < text.length()) {
            Text tail = new Text(text.substring(cursor));
            if (baseStyle != null) {
                tail.getStyleClass().add(baseStyle);
            }
            flow.getChildren().add(tail);
        }
        return matches.size();
    }

    /** Convenience overload for text with no base style and no current-match emphasis. */
    public static int appendHighlighted(TextFlow flow, String text, String query) {
        return appendHighlighted(flow, text, query, null, -1, 0);
    }

    /** How many matches {@code text} contributes, for counting before rendering. */
    public static int countMatches(String text, String query) {
        return TextSearch.find(text, query).size();
    }
}
