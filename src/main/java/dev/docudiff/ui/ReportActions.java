package dev.docudiff.ui;

import dev.docudiff.compare.Comparison;
import dev.docudiff.report.ReviewReportWriter;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * The "Export review report…" action.
 *
 * <p>Writes the current comparison into a Word file the reviewer can send back. The default folder
 * is the user's Documents directory and deliberately NOT the library: a report saved inside a
 * document's own folder would be picked up as document content by the next snapshot, and the
 * report would then appear inside the history it describes.
 *
 * <p>Writing a report is the only file the app ever creates from a comparison. Nothing is
 * committed, imported, or recorded as a version.
 */
final class ReportActions {

    private final BackgroundRunner runner;
    private final Consumer<String> status;
    private final ReviewReportWriter writer = new ReviewReportWriter();

    ReportActions(BackgroundRunner runner, Consumer<String> status) {
        this.runner = runner;
        this.status = status;
    }

    void export(Comparison comparison, Window owner) {
        if (comparison == null) {
            status.accept("Compare two documents first, then export the report.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save review report");
        chooser.setInitialFileName(ReviewReportWriter.suggestedFileName(comparison));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Word document", "*.docx"));
        Path documentsDirectory = documentsDirectory();
        if (Files.isDirectory(documentsDirectory)) {
            chooser.setInitialDirectory(documentsDirectory.toFile());
        }
        java.io.File chosen = chooser.showSaveDialog(owner);
        if (chosen == null) {
            return;
        }
        Path target = chosen.toPath();
        runner.run("Writing review report…",
                () -> {
                    writer.writeToFile(comparison, Instant.now(), target);
                    return target;
                },
                written -> status.accept("Review report saved to " + written + "."));
    }

    private Path documentsDirectory() {
        return Path.of(System.getProperty("user.home"), "Documents");
    }
}
