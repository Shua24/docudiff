package dev.docudiff.render;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DocxPdfRendererTest {

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void requireLibreOffice() {
        assumeTrue(OfficeLocator.locate().isPresent(),
                "LibreOffice is not installed; the renderer tests are skipped on this machine");
    }

    @Test
    @DisplayName("a docx converts to a PDF with at least one page")
    void docxConvertsToPdf() throws Exception {
        Path docx = writeDocx("sample.docx", "Hello docudiff");
        DocxPdfRenderer renderer = new DocxPdfRenderer(OfficeLocator.locate().orElseThrow());

        Path pdf = renderer.renderToPdf(docx, temporaryDirectory.resolve("out"), "cached-version-1");

        assertTrue(Files.exists(pdf), "expected a PDF at " + pdf);
        int pageCount = renderer.pageCountOf(pdf);
        assertTrue(pageCount >= 1, "expected at least one page, got " + pageCount);
    }

    @Test
    @DisplayName("the same version reuses its cached PDF instead of converting again")
    void sameVersionReusesCache() throws Exception {
        Path docx = writeDocx("cached.docx", "Hello docudiff");
        DocxPdfRenderer renderer = new DocxPdfRenderer(OfficeLocator.locate().orElseThrow());
        Path outputDirectory = temporaryDirectory.resolve("cache");
        Files.createDirectories(outputDirectory);

        Path first = renderer.renderToPdf(docx, outputDirectory, "version-a");
        long firstModified = Files.getLastModifiedTime(first).toMillis();
        Thread.sleep(1100);
        Path second = renderer.renderToPdf(docx, outputDirectory, "version-a");
        long secondModified = Files.getLastModifiedTime(second).toMillis();

        assertEquals(first, second);
        assertEquals(firstModified, secondModified,
                "a cached PDF must not be regenerated for the same version");
    }

    @Test
    @DisplayName("two concurrent conversions both produce a PDF")
    void concurrentConversionsBothProducePdf() throws Exception {
        Path firstDocx = writeDocx("first.docx", "First document");
        Path secondDocx = writeDocx("second.docx", "Second document");
        DocxPdfRenderer renderer = new DocxPdfRenderer(OfficeLocator.locate().orElseThrow());
        Path outputDirectory = temporaryDirectory.resolve("concurrent");
        Files.createDirectories(outputDirectory);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Path>> tasks = List.of(
                    () -> renderer.renderToPdf(firstDocx, outputDirectory, "version-one"),
                    () -> renderer.renderToPdf(secondDocx, outputDirectory, "version-two"));
            List<Future<Path>> futures = executor.invokeAll(tasks);
            Path firstPdf = futures.get(0).get();
            Path secondPdf = futures.get(1).get();

            assertTrue(Files.exists(firstPdf), "first conversion was dropped");
            assertTrue(Files.exists(secondPdf),
                    "second conversion was dropped; LibreOffice silently discards concurrent jobs "
                            + "that share a profile directory");
        }
    }

    private Path writeDocx(String fileName, String text) throws Exception {
        Path docx = temporaryDirectory.resolve(fileName);
        try (org.apache.poi.xwpf.usermodel.XWPFDocument document =
                     new org.apache.poi.xwpf.usermodel.XWPFDocument();
             java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(buffer);
            Files.write(docx, buffer.toByteArray());
        }
        return docx;
    }
}
