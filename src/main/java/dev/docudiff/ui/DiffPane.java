package dev.docudiff.ui;

import dev.docudiff.diff.BlockDiff;
import dev.docudiff.diff.DocumentDiff;
import dev.docudiff.diff.RowDiff;
import dev.docudiff.diff.WordMark;
import dev.docudiff.diff.WordMarkKind;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link DocumentDiff} as blocks with text-level marks and formatting annotations
 * underneath.
 *
 * <p>Additions and removals are shown inline in one flow rather than in two columns: for documents
 * that are mostly identical this reads far better, and it keeps the pane usable on a narrow window.
 */
public final class DiffPane extends ScrollPane {

    private final VBox blocks = new VBox(10);

    public DiffPane() {
        setFitToWidth(true);
        setContent(blocks);
        showPlaceholder("Import a document, then pick two versions to compare.");
    }

    public void showPlaceholder(String message) {
        blocks.getChildren().setAll(UiParts.muted(message));
    }

    public void showDocumentDiff(DocumentDiff documentDiff) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        if (documentDiff.blockDiffs().isEmpty()) {
            blocks.getChildren().setAll(UiParts.muted("There is nothing to compare."));
            return;
        }
        List<BlockDiff> blockDiffs = documentDiff.blockDiffs();
        for (int blockIndex = 0; blockIndex < blockDiffs.size(); blockIndex++) {
            BlockDiff blockDiff = blockDiffs.get(blockIndex);
            nodes.add(renderBlock(documentDiff, blockIndex, blockDiff));
        }
        blocks.getChildren().setAll(nodes);
    }

    public void showRowDiffs(List<RowDiff> rowDiffs) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        for (RowDiff rowDiff : rowDiffs) {
            String text = switch (rowDiff.status()) {
                case ADDED -> "+ row " + rowDiff.right().rowNumber()
                        + ": " + rowDiff.right().signature().replace('\u001f', ' ');
                case REMOVED -> "- row " + rowDiff.left().rowNumber()
                        + ": " + rowDiff.left().signature().replace('\u001f', ' ');
                case MODIFIED -> "~ row " + rowDiff.right().rowNumber()
                        + " columns " + rowDiff.changedColumns();
                case UNCHANGED -> null;
            };
            if (text != null) {
                Text line = new Text(text);
                TextFlow flow = new TextFlow(line);
                flow.getStyleClass().add("diff-block");
                flow.getStyleClass().add(styleClassFor(rowDiff.status().name().toLowerCase()));
                nodes.add(flow);
            }
        }
        if (nodes.isEmpty()) {
            blocks.getChildren().setAll(UiParts.muted("The two sheets are identical."));
            return;
        }
        blocks.getChildren().setAll(nodes);
    }

    private javafx.scene.Node renderBlock(
            DocumentDiff documentDiff, int blockIndex, BlockDiff blockDiff) {
        TextFlow flow = new TextFlow();

        List<WordMark> wordMarks = documentDiff.wordMarksOf(blockIndex);
        if (wordMarks.isEmpty()) {
            flow.getChildren().add(new Text(textOf(blockDiff)));
        } else {
            for (WordMark wordMark : wordMarks) {
                Text word = new Text(wordMark.text() + " ");
                switch (wordMark.kind()) {
                    case ADDED -> word.getStyleClass().add("word-added");
                    case REMOVED -> word.getStyleClass().add("word-removed");
                    case EQUAL -> word.getStyleClass().add("word-equal");
                }
                flow.getChildren().add(word);
            }
        }

        List<String> annotations = documentDiff.annotationsOf(blockIndex);
        javafx.scene.Node card;
        if (annotations.isEmpty()) {
            card = flow;
        } else {
            VBox blockBox = new VBox(3, flow);
            for (String annotation : annotations) {
                blockBox.getChildren().add(UiParts.muted("formatting: " + annotation));
            }
            card = blockBox;
        }

        // The card class goes on exactly one node: the outer one. Applying it to both the wrapper
        // and the inner flow double-draws the border and padding.
        card.getStyleClass().add("diff-block");
        card.getStyleClass().add(styleClassFor(blockDiff.status().name().toLowerCase()));
        return card;
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
        String text = builder.toString();
        return text;
    }

    private String styleClassFor(String name) {
        return switch (name) {
            case "added" -> "diff-added";
            case "removed" -> "diff-removed";
            case "modified" -> "diff-modified";
            default -> "diff-unchanged";
        };
    }
}
