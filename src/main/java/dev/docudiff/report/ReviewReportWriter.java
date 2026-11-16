package dev.docudiff.report;

import dev.docudiff.compare.Comparison;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Builds the document a reviewer hands back: a Word file that names both sides, states the size of
 * the change, says what was not compared, and then shows every change in reading order.
 *
 * <p>This is the "document feedback" feature. It reads a comparison and writes a report — it does
 * not modify either document, record a version, or merge anything, so the app stays read-only
 * about content. The one file it writes is the report the user asked for.
 *
 * <p>A comparison with no changes still produces a report: "I checked, nothing changed" is a real
 * answer a reviewer needs to be able to send.
 */
public final class ReviewReportWriter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final DocumentChangesWriter documentChangesWriter = new DocumentChangesWriter();
    private final SheetChangesWriter sheetChangesWriter = new SheetChangesWriter();

    /** The report as bytes, so callers can write it wherever they like. */
    public byte[] write(Comparison comparison, Instant generatedAt) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeHeader(document, comparison, generatedAt);
            if (comparison.isSpreadsheet()) {
                sheetChangesWriter.write(document, comparison.sheetDiffs());
            } else if (comparison.documentDiff() != null) {
                documentChangesWriter.write(document, comparison.documentDiff());
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    /** Writes the report to a file, replacing whatever is there. */
    public void writeToFile(Comparison comparison, Instant generatedAt, Path target)
            throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(target, write(comparison, generatedAt));
    }

    private void writeHeader(XWPFDocument document, Comparison comparison, Instant generatedAt) {
        XWPFParagraph title = document.createParagraph();
        XWPFRun titleRun = title.createRun();
        titleRun.setText("Review report");
        titleRun.setBold(true);
        titleRun.setFontSize(18);

        ReportRuns.text(document.createParagraph(), "Before:  " + comparison.leftLabel());
        ReportRuns.text(document.createParagraph(), "After:  " + comparison.rightLabel());
        ReportRuns.muted(document, "Generated " + TIMESTAMP_FORMAT.format(generatedAt));

        long changed = comparison.changedCount();
        String unit = comparison.isSpreadsheet() ? " changed row(s)" : " changed block(s)";
        XWPFParagraph summary = document.createParagraph();
        XWPFRun summaryRun = summary.createRun();
        summaryRun.setBold(true);
        summaryRun.setText(changed == 0
                ? "No differences found."
                : changed + unit + " between the two files.");

        for (String notice : comparison.notices()) {
            ReportRuns.muted(document, "Not compared: " + notice);
        }
    }

    /**
     * A file name for the save dialog, taken from the two side labels.
     *
     * <p>Sanitised because labels come from file names and snapshot names, which can contain
     * characters a file system will not accept.
     */
    public static String suggestedFileName(Comparison comparison) {
        String left = sanitise(comparison.leftLabel());
        String right = sanitise(comparison.rightLabel());
        return "review-" + left + "-vs-" + right + ".docx";
    }

    private static String sanitise(String label) {
        String withoutExtension = label.replaceAll("\\.[A-Za-z0-9]+$", "");
        String sanitised = withoutExtension.replaceAll("[^A-Za-z0-9._-]+", "-");
        String trimmed = sanitised.replaceAll("^-+|-+$", "");
        if (trimmed.isEmpty()) {
            return "document";
        }
        return trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed;
    }
}
