package dev.docudiff.ui;

import dev.docudiff.compare.Comparison;
import dev.docudiff.compare.DocumentComparer;
import dev.docudiff.compare.DocumentSource;
import dev.docudiff.versioning.DocumentFormat;
import javafx.application.Platform;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Search behaviour inside the rendered diff, driven through {@link DiffPane} on the JavaFX thread.
 *
 * <p>These exist because the design decision they check — that a keyword in an unchanged part of
 * the document is found and labelled as unchanged — cannot be verified from the search code alone.
 * It depends on the renderer emitting unchanged content at all.
 */
class DiffPaneSearchTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        started.await();
    }

    @Test
    @DisplayName("a keyword in an unchanged paragraph is found and reported as not changed")
    void unchangedDocumentMatchIsReported() throws Exception {
        String before = "# Title\n\nThe budget line is unchanged.\n\nRevenue grew.\n";
        String after = "# Title\n\nThe budget line is unchanged.\n\nRevenue grew this quarter.\n";
        Comparison comparison = compareMarkdown(before, after);

        runOnFxThread(() -> {
            DiffPane diffPane = new DiffPane();
            diffPane.showComparison(comparison);
            int matchCount = diffPane.applySearch("budget", 0);

            assertEquals(1, matchCount, "the unchanged paragraph must be searchable");
            String note = diffPane.contextNoteFor(0);
            assertTrue(note.contains("not changed"),
                    "searching unchanged text must say so, got: " + note);
        });
    }

    @Test
    @DisplayName("a keyword in a changed paragraph carries no not-changed note")
    void changedDocumentMatchHasNoNote() throws Exception {
        String before = "# Title\n\nRevenue grew.\n";
        String after = "# Title\n\nRevenue grew this quarter.\n";
        Comparison comparison = compareMarkdown(before, after);

        runOnFxThread(() -> {
            DiffPane diffPane = new DiffPane();
            diffPane.showComparison(comparison);
            int matchCount = diffPane.applySearch("quarter", 0);

            assertTrue(matchCount >= 1, "the added word must be searchable");
            assertEquals("", diffPane.contextNoteFor(0),
                    "a hit inside a change must not be labelled unchanged");
        });
    }

    @Test
    @DisplayName("a keyword in an unchanged spreadsheet row is found only while searching")
    void unchangedSpreadsheetRowIsSearchable() throws Exception {
        Comparison comparison = compareWorkbooks(
                List.of("gamma", "alpha"),
                List.of("gamma", "beta"));

        runOnFxThread(() -> {
            DiffPane diffPane = new DiffPane();

            // Without a search, unchanged rows are not rendered, so nothing to find.
            diffPane.showComparison(comparison);
            assertEquals(0, diffPane.applySearch("", -1),
                    "with no query there is no searching to do");

            int matchCount = diffPane.applySearch("gamma", 0);
            assertEquals(1, matchCount,
                    "gamma sits in an unchanged row and must still be found");
            String note = diffPane.contextNoteFor(0);
            assertTrue(note.contains("not changed"),
                    "the spreadsheet match is in an unchanged row, got: " + note);
        });
    }

    @Test
    @DisplayName("every occurrence is counted, not just the current one")
    void allOccurrencesAreCounted() throws Exception {
        String before = "# T\n\nbudget and budget\n";
        String after = "# T\n\nbudget and budget\n";
        Comparison comparison = compareMarkdown(before, after);

        runOnFxThread(() -> {
            DiffPane diffPane = new DiffPane();
            diffPane.showComparison(comparison);

            assertEquals(2, diffPane.applySearch("budget", 0));
        });
    }

    private Comparison compareMarkdown(String before, String after) {
        return new DocumentComparer().compare(
                DocumentSource.ofBytes("before", before, DocumentFormat.MARKDOWN),
                DocumentSource.ofBytes("after", after, DocumentFormat.MARKDOWN));
    }

    private Comparison compareWorkbooks(List<String> before, List<String> after) throws Exception {
        return new DocumentComparer().compare(
                DocumentSource.ofBytes("before.xlsx", workbook(before), DocumentFormat.XLSX),
                DocumentSource.ofBytes("after.xlsx", workbook(after), DocumentFormat.XLSX));
    }

    private byte[] workbook(List<String> columnAValues) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");
            for (int index = 0; index < columnAValues.size(); index++) {
                XSSFRow row = sheet.createRow(index);
                XSSFCell cell = row.createCell(0);
                cell.setCellValue(columnAValues.get(index));
            }
            workbook.write(buffer);
            return buffer.toByteArray();
        }
    }

    /** Runs on the JavaFX thread and rethrows whatever the body threw. */
    private void runOnFxThread(FxBody body) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                done.countDown();
            }
        });
        done.await();
        if (failure.get() != null) {
            throw new AssertionError("failure on the JavaFX thread", failure.get());
        }
    }

    @FunctionalInterface
    private interface FxBody {
        void run() throws Exception;
    }
}
