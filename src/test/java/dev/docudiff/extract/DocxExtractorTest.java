package dev.docudiff.extract;

import dev.docudiff.document.Block;
import dev.docudiff.document.DocumentSection;
import dev.docudiff.document.DocumentTree;
import dev.docudiff.document.InlineSpan;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DocxExtractorTest {

    @Test
    @DisplayName("a one-paragraph document extracts to one body paragraph block")
    void oneParagraphDocumentExtractsToOneBlock() throws Exception {
        byte[] docxBytes = docxWithSingleParagraph();

        DocumentTree tree = new DocxExtractor().extract(docxBytes);

        DocumentSection body = tree.sections().stream()
                .filter(section -> section.name().equals("Body"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, body.blocks().size());
        Block.Paragraph paragraph = (Block.Paragraph) body.blocks().get(0);
        assertEquals(1, paragraph.spans().size());
        assertEquals("Hello docudiff", paragraph.spans().get(0).text());
    }

    @Test
    @DisplayName("a bold run records bold true and its font")
    void boldRunRecordsFormatting() throws Exception {
        byte[] docxBytes = docxWithBoldRun();

        DocumentTree tree = new DocxExtractor().extract(docxBytes);

        Block.Paragraph paragraph = (Block.Paragraph) tree.allBlocks().get(0);
        InlineSpan boldSpan = paragraph.spans().get(0);
        assertEquals(Boolean.TRUE, boldSpan.bold());
        assertEquals("Liberation Serif", boldSpan.fontFamily());
        assertEquals(14.0, boldSpan.fontSize());
    }

    @Test
    @DisplayName("an unstyled run reports null formatting, not false")
    void unstyledRunReportsNullNotFalse() throws Exception {
        byte[] docxBytes = docxWithBoldRun();

        DocumentTree tree = new DocxExtractor().extract(docxBytes);

        Block.Paragraph paragraph = (Block.Paragraph) tree.allBlocks().get(0);
        InlineSpan plainSpan = paragraph.spans().get(1);
        assertNull(plainSpan.bold(), "an unstyled run must be inherited, never false");
        assertNull(plainSpan.fontFamily());
    }

    @Test
    @DisplayName("a header paragraph lands in its own section, not in the body")
    void headerLandsInItsOwnSection() throws Exception {
        byte[] docxBytes = docxWithHeader();

        DocumentTree tree = new DocxExtractor().extract(docxBytes);

        List<String> sectionNames = tree.sections().stream().map(DocumentSection::name).toList();
        assertEquals(List.of("Header 1", "Body"), sectionNames);
        DocumentSection header = tree.sections().get(0);
        Block.Paragraph headerParagraph = (Block.Paragraph) header.blocks().get(0);
        assertEquals("Header text", headerParagraph.spans().get(0).text());
    }

    private byte[] docxWithSingleParagraph() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("Hello docudiff");
            document.write(buffer);
            return buffer.toByteArray();
        }
    }

    private byte[] docxWithBoldRun() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun boldRun = paragraph.createRun();
            boldRun.setText("bold part");
            boldRun.setBold(true);
            boldRun.setFontFamily("Liberation Serif");
            boldRun.setFontSize(14);
            XWPFRun plainRun = paragraph.createRun();
            plainRun.setText(" plain part");
            document.write(buffer);
            return buffer.toByteArray();
        }
    }

    private byte[] docxWithHeader() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            document.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT)
                    .createParagraph().createRun().setText("Header text");
            document.createParagraph().createRun().setText("Body text");
            document.write(buffer);
            return buffer.toByteArray();
        }
    }
}
