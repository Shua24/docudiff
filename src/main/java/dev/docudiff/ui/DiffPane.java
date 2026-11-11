package dev.docudiff.ui;

import dev.docudiff.compare.Comparison;
import dev.docudiff.diff.BlockDiff;
import dev.docudiff.diff.DiffStatus;
import dev.docudiff.diff.DocumentDiff;
import dev.docudiff.diff.RowDiff;
import dev.docudiff.diff.WordMark;
import dev.docudiff.diff.WordMarkKind;
import dev.docudiff.search.SearchHighlighter;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Renders a {@link Comparison} — either a block diff of a document or a cell grid for a
 * spreadsheet — with search hits highlighted in place.
 *
 * <p>Search is built into rendering rather than applied afterwards: the document renderer emits
 * one {@code Text} node per word mark, so a match can straddle those boundaries and has to be
 * split during construction. Every emitted match is recorded with the block it landed in, which is
 * what lets the search bar say whether the hit is inside changed or unchanged content.
 *
 * <p>Additions and removals are shown inline in one flow rather than in two columns: for documents
 * that are mostly identical this reads far better, and it keeps the pane usable on a narrow window.
 */
public final class DiffPane extends ScrollPane {

    private final VBox blocks = new VBox(10);
    private final List<MatchSpot> matchSpots = new ArrayList<>();

    private Comparison comparison;
    private String query = "";
    private int currentMatch = -1;
    private IntConsumer matchCountConsumer = count -> { };

    public DiffPane() {
        setFitToWidth(true);
        setContent(blocks);
        showPlaceholder("Import a document, then pick two versions to compare.");
    }

    /** Receives the number of matches each time the pane re-renders with a query. */
    public void setMatchCountConsumer(IntConsumer matchCountConsumer) {
        this.matchCountConsumer = matchCountConsumer;
    }

    public void showPlaceholder(String message) {
        comparison = null;
        matchSpots.clear();
        query = "";
        currentMatch = -1;
        blocks.getChildren().setAll(UiParts.muted(message));
    }

    /** Shows a comparison with no search applied. */
    public void showComparison(Comparison comparison) {
        this.comparison = comparison;
        this.query = "";
        this.currentMatch = -1;
        render();
    }

    /**
     * Re-renders with a query and an emphasised match.
     *
     * @param searchQuery  the search text; empty clears highlighting
     * @param currentMatch index of the match to emphasise, or -1 for none
     * @return the number of matches the query found
     */
    public int applySearch(String searchQuery, int currentMatch) {
        this.query = searchQuery == null ? "" : searchQuery;
        this.currentMatch = currentMatch;
        if (comparison != null) {
            render();
        }
        return matchSpots.size();
    }

    public int matchCount() {
        return matchSpots.size();
    }

    /**
     * The note for the current match: empty when the hit is in changed content, otherwise a
     * sentence saying the match is not itself a change. Empty when there is no current match.
     */
    public String contextNoteFor(int matchIndex) {
        if (matchIndex < 0 || matchIndex >= matchSpots.size()) {
            return "";
        }
        MatchSpot spot = matchSpots.get(matchIndex);
        return spot.unchanged()
                ? "this part is not changed — the match is in " + spot.description()
                : "";
    }

    private void render() {
        matchSpots.clear();
        if (comparison == null) {
            return;
        }
        List<Node> nodes = comparison.isSpreadsheet()
                ? renderSheet(comparison.rowDiffs(), comparison.sheetName())
                : renderDocument(comparison.documentDiff());
        blocks.getChildren().setAll(nodes);
        matchCountConsumer.accept(matchSpots.size());
    }

    private List<Node> renderDocument(DocumentDiff documentDiff) {
        List<Node> nodes = new ArrayList<>();
        if (documentDiff == null) {
            return nodes;
        }
        List<BlockDiff> blockDiffs = documentDiff.blockDiffs();
        if (blockDiffs.isEmpty()) {
            nodes.add(UiParts.muted("There is nothing to compare."));
            return nodes;
        }
        for (int blockIndex = 0; blockIndex < blockDiffs.size(); blockIndex++) {
            nodes.add(renderBlock(documentDiff, blockIndex, blockDiffs.get(blockIndex)));
        }
        return nodes;
    }

    private Node renderBlock(DocumentDiff documentDiff, int blockIndex, BlockDiff blockDiff) {
        TextFlow flow = new TextFlow();
        boolean unchanged = blockDiff.status() == DiffStatus.UNCHANGED;

        List<WordMark> wordMarks = documentDiff.wordMarksOf(blockIndex);
        if (wordMarks.isEmpty()) {
            appendText(flow, textOf(blockDiff), null, blockIndex, unchanged);
        } else {
            for (WordMark wordMark : wordMarks) {
                appendText(flow, wordMark.text() + " ", styleFor(wordMark.kind()),
                        blockIndex, unchanged);
            }
        }

        List<String> annotations = documentDiff.annotationsOf(blockIndex);
        Node card = flow;
        if (!annotations.isEmpty()) {
            VBox blockBox = new VBox(3, flow);
            for (String annotation : annotations) {
                blockBox.getChildren().add(UiParts.muted("formatting: " + annotation));
            }
            card = blockBox;
        }

        // The card class goes on exactly one node: the outer one. Applying it to both the wrapper
        // and the inner flow double-draws the border and padding.
        card.getStyleClass().add("diff-block");
        card.getStyleClass().add(styleClassFor(blockDiff.status()));
        return card;
    }

    /*
     * One place that both emits highlighted text and records where each match landed. Every text
     * node the diff renders goes through here, so the match index the search bar navigates by is
     * exactly the render order.
     */
    private void appendText(
            TextFlow flow, String text, String baseStyle, int blockIndex, boolean unchanged) {
        int matches = SearchHighlighter.appendHighlighted(
                flow, text, query, baseStyle, currentMatch, matchSpots.size());
        for (int index = 0; index < matches; index++) {
            matchSpots.add(new MatchSpot(blockIndex, unchanged,
                    unchanged ? "an unchanged part of the document" : "a changed part"));
        }
    }

    private List<Node> renderSheet(List<RowDiff> rowDiffs, String sheetName) {
        // While a search is active every row is rendered, so a keyword in an untouched row can be
        // found and reported as unchanged. With no query, only changed rows are shown.
        return List.of(SheetDiffView.render(rowDiffs, sheetName, query, currentMatch,
                !query.isEmpty(), matchSpots));
    }

    static String styleFor(WordMarkKind kind) {
        return switch (kind) {
            case ADDED -> "word-added";
            case REMOVED -> "word-removed";
            case EQUAL -> "word-equal";
        };
    }

    private String styleClassFor(DiffStatus status) {
        return switch (status) {
            case ADDED -> "diff-added";
            case REMOVED -> "diff-removed";
            case MODIFIED -> "diff-modified";
            case UNCHANGED -> "diff-unchanged";
        };
    }

    private String textOf(BlockDiff blockDiff) {
        if (blockDiff.right() != null) {
            return blockText(blockDiff.right());
        }
        if (blockDiff.left() != null) {
            return blockText(blockDiff.left());
        }
        return "";
    }

    private String blockText(dev.docudiff.document.Block block) {
        if (block instanceof dev.docudiff.document.Block.Paragraph paragraph) {
            return joinSpans(paragraph.spans());
        }
        if (block instanceof dev.docudiff.document.Block.Heading heading) {
            return joinSpans(heading.spans());
        }
        if (block instanceof dev.docudiff.document.Block.CodeBlock codeBlock) {
            return String.join("\n", codeBlock.lines());
        }
        return "";
    }

    private String joinSpans(List<dev.docudiff.document.InlineSpan> spans) {
        StringBuilder builder = new StringBuilder();
        for (dev.docudiff.document.InlineSpan span : spans) {
            builder.append(span.text());
        }
        return builder.toString();
    }
}
