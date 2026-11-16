package dev.docudiff.extract;

import dev.docudiff.document.Block;
import dev.docudiff.document.DocumentSection;
import dev.docudiff.document.DocumentTree;
import dev.docudiff.document.InlineSpan;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a DOCX byte array into a {@link DocumentTree}.
 *
 * Headers come first, then the body, then footers. Formatting that the run does not state is
 * carried as null (inherited), never as false — see {@link InlineSpan}.
 */
public final class DocxExtractor {

    public DocumentTree extract(byte[] docxBytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            List<DocumentSection> sections = new ArrayList<>();
            List<org.apache.poi.xwpf.usermodel.XWPFHeader> headers = document.getHeaderList();
            for (int index = 0; index < headers.size(); index++) {
                sections.add(new DocumentSection(
                        "Header " + (index + 1), paragraphsOf(headers.get(index).getParagraphs())));
            }
            sections.add(new DocumentSection("Body", paragraphsOf(document.getParagraphs())));
            List<org.apache.poi.xwpf.usermodel.XWPFFooter> footers = document.getFooterList();
            for (int index = 0; index < footers.size(); index++) {
                sections.add(new DocumentSection(
                        "Footer " + (index + 1), paragraphsOf(footers.get(index).getParagraphs())));
            }
            DocumentTree tree = new DocumentTree(sections, unsupportedConstructsOf(document));
            return tree;
        }
    }

    /**
     * Names what this extractor deliberately does not read, with counts.
     *
     * <p>Measured, because both halves matter: {@code getFootnotes()} and {@code getEndnotes()}
     * return empty lists on a plain document, but {@code getComments()} returns {@code null} when
     * the document has no comments part at all. Treating null as "comments found" would put a
     * warning under every document; treating a non-null empty array as "none" is the correct
     * reading. Nested tables are detected through the cell's own table list, which is what proves
     * the flattening in {@link #paragraphsOf} lost something.
     */
    private List<String> unsupportedConstructsOf(XWPFDocument document) {
        List<String> constructs = new ArrayList<>();
        int footnotes = countOf(document.getFootnotes() == null ? null : document.getFootnotes().size());
        if (footnotes > 0) {
            constructs.add("footnotes (" + footnotes + ")");
        }
        int endnotes = countOf(document.getEndnotes() == null ? null : document.getEndnotes().size());
        if (endnotes > 0) {
            constructs.add("endnotes (" + endnotes + ")");
        }
        int comments = document.getComments() == null ? 0 : document.getComments().length;
        if (comments > 0) {
            constructs.add("comments (" + comments + ")");
        }
        int nestedTables = countNestedTables(document);
        if (nestedTables > 0) {
            constructs.add("nested tables (" + nestedTables + ")");
        }
        return constructs;
    }

    private int countOf(Integer count) {
        return count == null ? 0 : count.intValue();
    }

    private int countNestedTables(XWPFDocument document) {
        int nested = 0;
        for (org.apache.poi.xwpf.usermodel.XWPFTable table : document.getTables()) {
            nested += countNestedTables(table);
        }
        return nested;
    }

    private int countNestedTables(org.apache.poi.xwpf.usermodel.XWPFTable table) {
        int nested = 0;
        for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
            for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                for (org.apache.poi.xwpf.usermodel.XWPFTable innerTable : cell.getTables()) {
                    nested += 1 + countNestedTables(innerTable);
                }
            }
        }
        return nested;
    }

    private List<Block> paragraphsOf(List<XWPFParagraph> paragraphs) {
        List<Block> blocks = new ArrayList<>();
        for (XWPFParagraph paragraph : paragraphs) {
            List<InlineSpan> spans = new ArrayList<>();
            for (XWPFRun run : paragraph.getRuns()) {
                String text = run.text();
                if (text == null || text.isEmpty()) {
                    continue;
                }
                spans.add(new InlineSpan(
                        text,
                        statedBold(run),
                        statedItalic(run),
                        underlineOf(run),
                        run.getFontFamily(),
                        run.getFontSizeAsDouble()));
            }
            if (!spans.isEmpty()) {
                blocks.add(new Block.Paragraph(spans));
            }
        }
        return blocks;
    }

    /*
     * XWPFRun.isBold() returns a primitive boolean that is false both when the run says
     * "not bold" and when it says nothing at all. The only way to tell those apart is to look
     * for the <w:b/> element on the run properties, so do that instead.
     *
     * POI 6.0.0 exposes these as lists (getBList / getIList); the presence of an entry is what
     * says "this run states a value", and the entry's val says which value.
     */
    private Boolean statedBold(XWPFRun run) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr runProperties = runPropertiesOf(run);
        if (runProperties == null || runProperties.getBList().isEmpty()) {
            return null;
        }
        return toggleValueOf(runProperties.getBList().get(0));
    }

    private Boolean statedItalic(XWPFRun run) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr runProperties = runPropertiesOf(run);
        if (runProperties == null || runProperties.getIList().isEmpty()) {
            return null;
        }
        return toggleValueOf(runProperties.getIList().get(0));
    }

    /*
     * A toggle element with no val attribute means ON. An explicit val of false/off/0 means OFF.
     */
    private Boolean toggleValueOf(
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTOnOff toggle) {
        if (toggle == null || !toggle.isSetVal() || toggle.getVal() == null) {
            return Boolean.TRUE;
        }
        String text = String.valueOf(toggle.getVal());
        boolean isOff = text.equals("false") || text.equals("off") || text.equals("0");
        return Boolean.valueOf(!isOff);
    }

    private org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr runPropertiesOf(XWPFRun run) {
        return run.getCTR().getRPr();
    }

    private Boolean underlineOf(XWPFRun run) {
        org.apache.poi.xwpf.usermodel.UnderlinePatterns underline = run.getUnderline();
        if (underline == null || underline == org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }
}
