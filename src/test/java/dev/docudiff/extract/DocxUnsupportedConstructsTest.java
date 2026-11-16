package dev.docudiff.extract;

import dev.docudiff.document.DocumentTree;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the extractor reports as "not compared".
 *
 * <p>The distinction these tests protect: {@code getComments()} is {@code null} when a document
 * has no comments part, and {@code getFootnotes()} is an empty list rather than null. Reading the
 * null as "comments found" would put a warning under every plain document.
 */
class DocxUnsupportedConstructsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("a plain document reports nothing unsupported")
    void plainDocumentReportsNothing() throws Exception {
        DocumentTree tree = new DocxExtractor().extract(bytesOf(document -> {
            document.createParagraph().createRun().setText("just text");
        }));

        assertEquals(java.util.List.of(), tree.unsupportedConstructs(),
                "a plain document must not carry a warning");
    }

    @Test
    @DisplayName("footnotes and endnotes are counted")
    void footnotesAndEndnotesAreCounted() throws Exception {
        DocumentTree tree = new DocxExtractor().extract(bytesOf(document -> {
            document.createParagraph().createRun().setText("text with notes");
            document.createFootnote();
            document.createFootnote();
            document.createEndnote();
        }));

        assertEquals(java.util.List.of("footnotes (2)", "endnotes (1)"),
                tree.unsupportedConstructs());
    }

    @Test
    @DisplayName("a comment is counted even though the document text looks unchanged")
    void commentIsCounted() throws Exception {
        DocumentTree tree = new DocxExtractor().extract(bytesOf(document -> {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("commented text");
            XWPFComment comment = document.createComments()
                    .createComment(BigInteger.ONE);
            comment.createParagraph().createRun().setText("please check this");
            run.getCTR().addNewCommentReference().setId(BigInteger.ONE);
        }));

        assertTrue(tree.unsupportedConstructs().contains("comments (1)"),
                "a diff that ignores comments must say so, got: " + tree.unsupportedConstructs());
    }

    @Test
    @DisplayName("a nested table is counted, because extraction flattens its cells")
    void nestedTableIsCounted() throws Exception {
        DocumentTree tree = new DocxExtractor().extract(bytesOf(DocxUnsupportedConstructsTest::addNestedTable));

        assertTrue(tree.unsupportedConstructs().contains("nested tables (1)"),
                "a flattened nested table is lost content, so it must be reported, got: "
                        + tree.unsupportedConstructs());
    }

    /*
     * POI's XWPF wrapper for a freshly inserted nested table is disconnected from the XML tree, so
     * building the inner row through XWPFTable.createRow() throws
     * XmlValueDisconnectedException. Creating the row through the CT layer is what actually
     * produces a nested table POI can write and re-read.
     */
    private static void addNestedTable(XWPFDocument document) {
        XWPFTable outer = document.createTable(1, 1);
        XWPFTableCell cell = outer.getRow(0).getCell(0);
        cell.setText("outer cell");
        XWPFTable inner = cell.insertNewTbl(
                cell.getParagraphs().get(0).getCTP().newCursor());
        CTTbl nestedTable = inner.getCTTbl();
        CTRow nestedRow = nestedTable.addNewTr();
        CTTc nestedCell = nestedRow.addNewTc();
        nestedCell.addNewP().addNewR().addNewT().setStringValue("nested cell");
    }

    private interface DocumentBuilder {
        void build(XWPFDocument document) throws Exception;
    }

    private byte[] bytesOf(DocumentBuilder builder) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            builder.build(document);
            document.write(output);
            return output.toByteArray();
        }
    }
}
