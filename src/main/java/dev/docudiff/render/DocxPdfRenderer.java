package dev.docudiff.render;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/**
 * Converts a DOCX to a PDF with LibreOffice in headless mode, caching one PDF per version.
 *
 * <p><b>Every conversion MUST pass its own {@code -env:UserInstallation} profile directory.</b>
 * Measured on LibreOffice 26.8.1.1: three concurrent conversions sharing the default profile
 * produced only two PDFs, and the dropped job printed nothing and exited 0. With a per-job
 * profile all three succeeded. A shared profile therefore loses documents silently.
 */
public final class DocxPdfRenderer {

    private static final long CONVERSION_TIMEOUT_SECONDS = 120;

    private final Path officeExecutable;

    public DocxPdfRenderer(Path officeExecutable) {
        this.officeExecutable = officeExecutable;
    }

    /**
     * Returns the PDF for {@code docxFile}, converting it first if {@code cacheKey} has no cached
     * PDF yet.
     */
    public Path renderToPdf(Path docxFile, Path outputDirectory, String cacheKey)
            throws IOException, InterruptedException {
        Files.createDirectories(outputDirectory);
        Path cachedPdf = outputDirectory.resolve(cacheKey + ".pdf");
        if (Files.exists(cachedPdf) && Files.size(cachedPdf) > 0) {
            return cachedPdf;
        }
        convert(docxFile, outputDirectory, cacheKey);
        if (!Files.exists(cachedPdf)) {
            throw new IOException("LibreOffice produced no PDF for " + docxFile);
        }
        return cachedPdf;
    }

    private void convert(Path docxFile, Path outputDirectory, String cacheKey)
            throws IOException, InterruptedException {
        Path profileDirectory = outputDirectory.resolve(cacheKey + "-profile");
        Files.createDirectories(profileDirectory);
        try {
            Path workDirectory = Files.createTempDirectory(outputDirectory, cacheKey + "-work");
            Path inputCopy = workDirectory.resolve("input.docx");
            Files.copy(docxFile, inputCopy,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            ProcessBuilder builder = new ProcessBuilder(
                    officeExecutable.toString(),
                    "--headless",
                    "--norestore",
                    "-env:UserInstallation=" + profileDirectory.toUri(),
                    "--convert-to", "pdf",
                    "--outdir", workDirectory.toString(),
                    inputCopy.toString());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(CONVERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("LibreOffice conversion timed out for " + docxFile);
            }
            if (process.exitValue() != 0) {
                throw new IOException("LibreOffice failed for " + docxFile + ": " + output.strip());
            }

            Path producedPdf = workDirectory.resolve("input.pdf");
            if (!Files.exists(producedPdf)) {
                throw new IOException("LibreOffice reported success but wrote no PDF for "
                        + docxFile + ": " + output.strip());
            }
            Files.copy(producedPdf, outputDirectory.resolve(cacheKey + ".pdf"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            deleteRecursively(workDirectory);
        } finally {
            deleteRecursively(profileDirectory);
        }
    }

    public int pageCountOf(Path pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile.toFile())) {
            return document.getNumberOfPages();
        }
    }

    private void deleteRecursively(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A leftover profile directory is harmless; the next run uses a fresh one.
                }
            });
        } catch (IOException ignored) {
            // Same reasoning as above.
        }
    }
}
