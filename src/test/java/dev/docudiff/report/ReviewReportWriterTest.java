package dev.docudiff.report;

import dev.docudiff.compare.Comparison;
import dev.docudiff.diff.DiffStatus;
import dev.docudiff.diff.DocumentDiffer;
import dev.docudiff.diff.DocumentDiff;
import dev.docudiff.diff.RowDiff;
import dev.docudiff.diff.SheetDiff;
import dev.docudiff.document.Block;
import dev.docudiff.document.DocumentSection;
import dev.docudiff.document.DocumentTree;
import dev.docudiff.document.InlineSpan;
import dev.docudiff.document.SheetCell;
import dev.docudiff.document.SheetRow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The review report, read back out of the generated .docx rather than trusted because bytes were
 * produced.
 *
 * <p>Two things are checked that a "the file exists" assertion would miss: the removed word
 * carries strike-through and the inserted word carries underline, which is the difference between
 * a readable review document and a wall of plain text.
 */
class ReviewReportWriterTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-11-11T16:00:00Z");

    @Test
    @DisplayName("a changed paragraph becomes one paragraph with a struck and an underlined word")
    void changedParagraphCarriesMarks() throws Exception {
        Comparison comparison = comparisonOf(
                tree(paragraph("The quick brown fox jumps over the lazy dog.")),
                tree(paragraph("The quick brown fox leaps over the lazy dog.")));

        List<XWPFRun> runs = runsOf(write(comparison));

        assertTrue(hasStruckText(runs, "jumps"), "the removed word must be struck through");
        assertTrue(hasUnderlinedText(runs, "leaps"), "the inserted word must be underlined");
        assertTrue(joinedText(runs).contains("The quick brown fox"),
                "unchanged words must be plain text, got: " + joinedText(runs));
    }

    @Test
    @DisplayName("the header names both sides and states the size of the change")
    void headerNamesBothSides() throws Exception {
        Comparison comparison = comparisonOf(
                tree(paragraph("one")),
                tree(paragraph("two")));

        List<String> paragraphs = paragraphTexts(write(comparison));

        assertTrue(paragraphs.contains("Review report"), "the report needs a title");
        assertTrue(paragraphs.stream().anyMatch(text -> text.contains("Before:  sent-version")),
                "the left side must be named, got: " + paragraphs);
        assertTrue(paragraphs.stream().anyMatch(text -> text.contains("After:  returned.docx")),
                "the right side must be named, got: " + paragraphs);
        assertTrue(paragraphs.stream().anyMatch(text -> text.contains("2 changed block(s)")),
                "\"one\" and \"two\" share no words, so this is one removal and one addition; "
                        + "the reader needs the size of the change, got: " + paragraphs);
    }

    @Test
    @DisplayName("an unchanged pair says so instead of producing an empty document")
    void unchangedPairSaysSo() throws Exception {
        Comparison comparison = comparisonOf(
                tree(paragraph("same")),
                tree(paragraph("same")));

        List<String> paragraphs = paragraphTexts(write(comparison));

        assertTrue(paragraphs.contains("No differences found."),
                "a clean review is a real answer and must be stated, got: " + paragraphs);
    }

    @Test
    @DisplayName("what was not compared is stated in the report, not only in the app")
    void noticesAreCarriedIntoTheReport() throws Exception {
        DocumentDiff documentDiff = new DocumentDiffer().diff(
                tree(paragraph("a")), tree(paragraph("a")));
        Comparison comparison = new Comparison("mine", "theirs", documentDiff, null,
                List.of("\"theirs\" contains comments (1), which are not compared."));

        List<String> paragraphs = paragraphTexts(write(comparison));

        assertTrue(paragraphs.stream().anyMatch(
                        text -> text.contains("Not compared: ") && text.contains("comments (1)")),
                "a report that silently ignores a comment is worse than no report, got: "
                        + paragraphs);
    }

    @Test
    @DisplayName("a formatting-only change reports the annotation and no marks")
    void formattingOnlyChangeIsAnnotated() throws Exception {
        InlineSpan plain = new InlineSpan("bold part", null, null, null, null, null);
        InlineSpan bold = new InlineSpan("bold part", Boolean.TRUE, null, null, null, null);
        Comparison comparison = comparisonOf(
                tree(new Block.Paragraph(List.of(plain))),
                tree(new Block.Paragraph(List.of(bold))));

        List<XWPFRun> runs = runsOf(write(comparison));

        assertTrue(hasText(runs, "formatting: bold applied"),
                "the annotation must appear under the block");
        assertFalse(runs.stream().anyMatch(XWPFRun::isStrikeThrough),
                "a formatting-only change must not strike text out");
    }

    @Test
    @DisplayName("a spreadsheet change is a table with the changed cell bold and the grid kept")
    void spreadsheetChangeBecomesTable() throws Exception {
        SheetRow before = row(3, "Alpha", "10");
        SheetRow after = row(3, "Alpha", "12");
        SheetDiff sheetDiff = SheetDiff.compared("Budget",
                List.of(new RowDiff(before, after, DiffStatus.MODIFIED, List.of(2))));
        Comparison comparison = new Comparison("old.xlsx", "new.xlsx", null,
                List.of(sheetDiff), List.of());

        try (XWPFDocument document = read(write(comparison))) {
            assertEquals(1, document.getTables().size(),
                    "one changed row means one before/after table");
            XWPFTable table = document.getTables().get(0);
            assertEquals(2, table.getNumberOfRows(), "before and after are two rows");
            assertEquals("Alpha", table.getRow(0).getCell(0).getText().strip());
            assertEquals("10", table.getRow(0).getCell(1).getText().strip());
            assertEquals("12", table.getRow(1).getCell(1).getText().strip(),
                    "the changed cell must show the new value, not a row index");
        }
    }

    @Test
    @DisplayName("the suggested file name is derived from both sides and is filesystem safe")
    void suggestedNameIsSafe() {
        Comparison comparison = new Comparison(
                "report final: v2.docx", "reviewer/returned.docx", null, null, List.of());

        String name = ReviewReportWriter.suggestedFileName(comparison);

        assertTrue(name.startsWith("review-"), "got: " + name);
        assertTrue(name.endsWith(".docx"), "got: " + name);
        assertFalse(name.contains("/") || name.contains(":"), "got: " + name);
    }

    private byte[] write(Comparison comparison) throws Exception {
        return new ReviewReportWriter().write(comparison, GENERATED_AT);
    }

    private XWPFDocument read(byte[] bytes) throws Exception {
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }

    private List<XWPFRun> runsOf(byte[] bytes) throws Exception {
        List<XWPFRun> runs = new ArrayList<>();
        try (XWPFDocument document = read(bytes)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                runs.addAll(paragraph.getRuns());
            }
        }
        return runs;
    }

    private List<String> paragraphTexts(byte[] bytes) throws Exception {
        List<String> texts = new ArrayList<>();
        try (XWPFDocument document = read(bytes)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                texts.add(paragraph.getText());
            }
        }
        return texts;
    }

    private boolean hasText(List<XWPFRun> runs, String expected) {
        return runs.stream().anyMatch(run -> run.text().contains(expected));
    }

    /** All run text joined, because the writer emits one run per word mark. */
    private String joinedText(List<XWPFRun> runs) {
        StringBuilder builder = new StringBuilder();
        for (XWPFRun run : runs) {
            builder.append(run.text());
        }
        return builder.toString();
    }

    private boolean hasStruckText(List<XWPFRun> runs, String expected) {
        return runs.stream().anyMatch(run -> run.isStrikeThrough() && run.text().contains(expected));
    }

    private boolean hasUnderlinedText(List<XWPFRun> runs, String expected) {
        return runs.stream().anyMatch(run -> run.getUnderline() != null
                && run.getUnderline() != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE
                && run.text().contains(expected));
    }

    private Comparison comparisonOf(DocumentTree left, DocumentTree right) {
        DocumentDiff documentDiff = new DocumentDiffer().diff(left, right);
        return new Comparison("sent-version", "returned.docx", documentDiff, null, List.of());
    }

    private DocumentTree tree(Block... blocks) {
        return new DocumentTree(List.of(new DocumentSection("Body", List.of(blocks))));
    }

    private Block paragraph(String text) {
        return new Block.Paragraph(List.of(InlineSpan.plain(text)));
    }

    private SheetRow row(int rowNumber, String... values) {
        List<SheetCell> cells = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            cells.add(new SheetCell(index + 1, null, values[index], null, null));
        }
        return new SheetRow(rowNumber, String.join("\u001f", values), cells);
    }
}
