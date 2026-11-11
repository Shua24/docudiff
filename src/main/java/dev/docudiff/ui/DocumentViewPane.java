package dev.docudiff.ui;

import dev.docudiff.render.DocxPdfRenderer;
import dev.docudiff.render.OfficeLocator;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * The "looks like Word" view: LibreOffice renders the version to PDF, PDFBox turns pages into
 * images, and the images stack in a scroll pane. Markdown renders through the embedded WebView.
 *
 * <p>Everything here is best-effort by design. Without LibreOffice the app still diffs documents;
 * this pane explains what is missing instead of preventing the app from starting.
 */
public final class DocumentViewPane extends StackPane {

    private static final float RENDER_DPI = 96f;

    private final ScrollPane scrollPane = new ScrollPane();
    private final VBox pages = new VBox(12);
    private final MarkdownViewRenderer markdownView = new MarkdownViewRenderer();

    public DocumentViewPane() {
        scrollPane.setFitToWidth(true);
        scrollPane.setContent(pages);
        getChildren().add(scrollPane);
        showPlaceholder("Select a version to view the document.");
    }

    public void showPlaceholder(String message) {
        Label label = UiParts.muted(message);
        label.setMaxWidth(620);
        pages.getChildren().setAll(label);
        scrollPane.setContent(pages);
    }

    public void showMarkdown(String markdown) {
        markdownView.render(markdown);
        scrollPane.setContent(markdownView.node());
    }

    /**
     * Renders the given docx to page images.
     *
     * @param docxFile      a file on disk holding the version's bytes
     * @param cacheDirectory where cached PDFs for this document are kept
     * @param cacheKey      a stable key for this version, so a cached PDF is reused
     */
    public void showDocx(Path docxFile, Path cacheDirectory, String cacheKey) {
        Optional<Path> office = OfficeLocator.locate();
        if (office.isEmpty()) {
            showPlaceholder(
                    "LibreOffice was not found, so the page view is unavailable. Diffing still "
                            + "works. Install LibreOffice, or set DOCUDIFF_LIBREOFFICE to its path "
                            + "and restart Docudiff.");
            return;
        }
        try {
            DocxPdfRenderer renderer = new DocxPdfRenderer(office.get());
            Path pdf = renderer.renderToPdf(docxFile, cacheDirectory, cacheKey);
            scrollPane.setContent(pages);
            pages.getChildren().setAll(renderPdfPages(pdf));
        } catch (Exception exception) {
            showPlaceholder("Could not render this version: " + exception.getMessage());
        }
    }

    private javafx.scene.Node[] renderPdfPages(Path pdfFile) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfFile.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            javafx.scene.Node[] pageNodes = new javafx.scene.Node[document.getNumberOfPages()];
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage bufferedImage = renderer.renderImageWithDPI(pageIndex, RENDER_DPI);
                ImageView imageView = new ImageView(toFxImage(bufferedImage));
                imageView.setPreserveRatio(true);
                imageView.setFitWidth(760);
                Label pageLabel = UiParts.muted("Page " + (pageIndex + 1));
                VBox pageBox = new VBox(4, pageLabel, imageView);
                pageBox.getStyleClass().add("pdf-page");
                pageNodes[pageIndex] = pageBox;
            }
            return pageNodes;
        }
    }

    /**
     * Converts without javafx.embed.swing: encoding to PNG and letting JavaFX decode avoids adding
     * the javafx-swing module just to move pixels between two image types.
     */
    private Image toFxImage(BufferedImage bufferedImage) throws Exception {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, "png", buffer);
            return new Image(new ByteArrayInputStream(buffer.toByteArray()));
        }
    }
}
