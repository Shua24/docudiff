package dev.docudiff.extract;

import dev.docudiff.document.Block;
import dev.docudiff.document.DocumentSection;
import dev.docudiff.document.DocumentTree;
import dev.docudiff.document.InlineSpan;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads markdown into a {@link DocumentTree} for block diffing, and exposes the raw source lines
 * for the git-style line diff.
 *
 * <p>Markdown is the one format where the source is already line-structured, so both views are
 * honest: the block tree powers the rendered/side-by-side view, and {@link #sourceLines} powers
 * the {@code +}/{@code -} gutter view.
 */
public final class MarkdownExtractor {

    private static final Parser PARSER = Parser.builder().build();

    public DocumentTree extract(String markdown) {
        Node document = PARSER.parse(normaliseLineEndings(markdown));
        List<Block> blocks = new ArrayList<>();
        collectBlocks(document, blocks);
        DocumentTree tree = new DocumentTree(List.of(new DocumentSection("Body", blocks)));
        return tree;
    }

    /**
     * The source split into lines, with CRLF normalised so a Windows-authored file does not report
     * a trailing carriage return on every line as a change.
     */
    public List<String> sourceLines(String markdown) {
        String normalised = normaliseLineEndings(markdown);
        if (normalised.isEmpty()) {
            return List.of();
        }
        String withoutTrailingBreak = normalised.endsWith("\n")
                ? normalised.substring(0, normalised.length() - 1)
                : normalised;
        return List.of(withoutTrailingBreak.split("\n", -1));
    }

    private String normaliseLineEndings(String markdown) {
        return markdown.replace("\r\n", "\n").replace("\r", "\n");
    }

    private void collectBlocks(Node parent, List<Block> blocks) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Heading heading) {
                blocks.add(new Block.Heading(heading.getLevel(), inlineSpansOf(heading)));
            } else if (child instanceof FencedCodeBlock codeBlock) {
                List<String> lines = new ArrayList<>();
                for (String line : codeBlock.getLiteral().split("\n", -1)) {
                    if (!line.isEmpty()) {
                        lines.add(line);
                    }
                }
                blocks.add(new Block.CodeBlock(codeBlock.getInfo(), lines));
            } else if (child instanceof Paragraph paragraph) {
                List<InlineSpan> spans = inlineSpansOf(paragraph);
                if (!spans.isEmpty()) {
                    blocks.add(new Block.Paragraph(spans));
                }
            } else if (child instanceof BulletList || child instanceof OrderedList) {
                collectListItems(child, blocks);
            } else {
                collectBlocks(child, blocks);
            }
        }
    }

    private void collectListItems(Node listNode, List<Block> blocks) {
        for (Node child = listNode.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof ListItem) {
                List<InlineSpan> spans = new ArrayList<>();
                for (Node itemChild = child.getFirstChild(); itemChild != null;
                        itemChild = itemChild.getNext()) {
                    spans.addAll(inlineSpansOf(itemChild));
                }
                if (!spans.isEmpty()) {
                    blocks.add(new Block.Paragraph(spans));
                }
            }
        }
    }

    private List<InlineSpan> inlineSpansOf(Node node) {
        List<InlineSpan> spans = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        Boolean bold = Boolean.FALSE;
        Boolean italic = Boolean.FALSE;
        collectText(node, builder, bold, italic, spans);
        String text = builder.toString().trim();
        if (!text.isEmpty()) {
            spans.add(new InlineSpan(text, null, null, null, null, null));
        }
        return spans;
    }

    private void collectText(
            Node node, StringBuilder builder, Boolean bold, Boolean italic, List<InlineSpan> spans) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text textNode) {
                builder.append(textNode.getLiteral());
            } else if (child instanceof Code codeNode) {
                builder.append(codeNode.getLiteral());
            } else if (child instanceof StrongEmphasis) {
                collectText(child, builder, Boolean.TRUE, italic, spans);
            } else if (child instanceof Emphasis) {
                collectText(child, builder, bold, Boolean.TRUE, spans);
            } else {
                collectText(child, builder, bold, italic, spans);
            }
        }
    }
}
