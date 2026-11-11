package dev.docudiff.report;

import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

/**
 * The handful of Word-writing helpers the report builders share.
 *
 * <p>The report is deliberately plain Word: real paragraphs and real tables, so it opens in Word
 * and LibreOffice like any other document. Added text is underlined and removed text is struck
 * through — the break-test for a reader who prints it in black and white, with a restrained colour
 * as a second signal.
 */
final class ReportRuns {

    static final String REMOVED_COLOR = "9B2C2C";
    static final String ADDED_COLOR = "1F5F7A";
    static final String MUTED_COLOR = "6B7280";

    private ReportRuns() {
    }

    static XWPFParagraph paragraph(XWPFDocument document) {
        return document.createParagraph();
    }

    /** Adds a run with only text, inheriting everything else. */
    static XWPFRun text(XWPFParagraph paragraph, String text) {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        return run;
    }

    /** A section heading: bold, slightly larger, with room above it. */
    static void heading(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(200);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(13);
    }

    /** Small grey text: context lines, annotations, and the unsupported-construct notices. */
    static void muted(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(9);
        run.setColor(MUTED_COLOR);
    }

    static XWPFRun removed(XWPFParagraph paragraph, String text) {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setStrikeThrough(true);
        run.setColor(REMOVED_COLOR);
        return run;
    }

    static XWPFRun added(XWPFParagraph paragraph, String text) {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setColor(ADDED_COLOR);
        return run;
    }

    /** A line break inside a paragraph, used for code blocks and multi-line values. */
    static void lineBreak(XWPFRun run) {
        run.addBreak();
    }

    static void monospace(XWPFRun run) {
        run.setFontFamily("Consolas");
        run.setFontSize(9);
    }
}
