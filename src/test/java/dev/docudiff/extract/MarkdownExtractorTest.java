package dev.docudiff.extract;

import dev.docudiff.document.Block;
import dev.docudiff.document.DocumentSection;
import dev.docudiff.document.DocumentTree;
import dev.docudiff.document.InlineSpan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownExtractorTest {

    @Test
    @DisplayName("a heading becomes a heading block carrying its level")
    void headingBecomesHeadingBlock() {
        DocumentTree tree = new MarkdownExtractor().extract("# Title\n");

        Block.Heading heading = (Block.Heading) tree.allBlocks().get(0);
        assertEquals(1, heading.level());
        assertEquals("Title", heading.spans().get(0).text());
    }

    @Test
    @DisplayName("a paragraph becomes a paragraph block")
    void paragraphBecomesParagraphBlock() {
        DocumentTree tree = new MarkdownExtractor().extract("Some *emphasised* text.\n");

        Block.Paragraph paragraph = (Block.Paragraph) tree.allBlocks().get(0);
        assertEquals("Some emphasised text.", paragraph.spans().get(0).text());
    }

    @Test
    @DisplayName("a fenced code block becomes a code block and keeps its lines verbatim")
    void fencedCodeBecomesCodeBlock() {
        String markdown = "```java\nint x = 1;\nint y = 2;\n```\n";

        DocumentTree tree = new MarkdownExtractor().extract(markdown);

        Block.CodeBlock codeBlock = (Block.CodeBlock) tree.allBlocks().get(0);
        assertEquals("java", codeBlock.language());
        assertEquals(List.of("int x = 1;", "int y = 2;"), codeBlock.lines());
    }

    @Test
    @DisplayName("a list becomes one block per item")
    void listBecomesOneBlockPerItem() {
        String markdown = "- first\n- second\n- third\n";

        DocumentTree tree = new MarkdownExtractor().extract(markdown);

        assertEquals(3, tree.allBlocks().size());
    }

    @Test
    @DisplayName("blank lines do not produce empty blocks")
    void blankLinesProduceNoBlocks() {
        DocumentTree tree = new MarkdownExtractor().extract("\n\n# Title\n\n\nText.\n\n");

        assertEquals(2, tree.allBlocks().size());
    }

    @Test
    @DisplayName("blocks land in a single Body section")
    void blocksLandInBodySection() {
        DocumentTree tree = new MarkdownExtractor().extract("# Title\n\nText.\n");

        List<DocumentSection> sections = tree.sections();
        assertEquals(1, sections.size());
        assertEquals("Body", sections.get(0).name());
        assertTrue(sections.get(0).blocks().size() >= 2);
    }

    @Test
    @DisplayName("plain source lines are exposed for the git-style line diff")
    void plainSourceLinesAreExposed() {
        String markdown = "# Title\n\nText.\n";

        List<String> lines = new MarkdownExtractor().sourceLines(markdown);

        assertEquals(List.of("# Title", "", "Text."), lines);
    }

    @Test
    @DisplayName("CRLF line endings do not leak carriage returns into source lines")
    void crlfLineEndingsAreNormalised() {
        List<String> lines = new MarkdownExtractor().sourceLines("# Title\r\n\r\nText.\r\n");

        assertEquals(List.of("# Title", "", "Text."), lines);
    }
}
