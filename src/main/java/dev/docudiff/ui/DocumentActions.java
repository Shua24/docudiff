package dev.docudiff.ui;

import dev.docudiff.library.DocumentFolder;
import dev.docudiff.library.ImportService;
import dev.docudiff.library.LibraryRoot;
import dev.docudiff.render.OfficeLocator;
import dev.docudiff.versioning.VersionEntry;
import dev.docudiff.versioning.VersionRepository;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Every user-triggered document operation, as one façade for the UI.
 *
 * <p>The operations themselves live in {@link BranchActions} and {@link VersionActions}; this
 * class owns the open-document state, the import flow and the settings dialog, and delegates the
 * rest. The split exists because this file passed the project's file-length limit once branch and
 * version actions were added to it.
 *
 * <p>All dialogs live in the action classes so {@link MainWindow} stays a layout class.
 */
final class DocumentActions implements DocumentSelection {

    private final Stage stage;
    private final LibraryRoot libraryRoot;
    private final Runnable afterChange;
    private final Consumer<String> status;
    private final Consumer<String> error;

    private final BranchActions branchActions;
    private final VersionActions versionActions;

    private DocumentFolder currentDocument;
    private VersionRepository currentRepository;

    DocumentActions(
            Stage stage,
            LibraryRoot libraryRoot,
            BackgroundRunner runner,
            Runnable afterChange,
            Consumer<String> status,
            Consumer<String> error) {
        this.stage = stage;
        this.libraryRoot = libraryRoot;
        this.afterChange = afterChange;
        this.status = status;
        this.error = error;
        this.branchActions = new BranchActions(stage, this, afterChange, status, error);
        this.versionActions = new VersionActions(stage, this, runner, afterChange, status, error);
    }

    void setCurrentDocument(DocumentFolder documentFolder, VersionRepository repository) {
        this.currentDocument = documentFolder;
        this.currentRepository = repository;
    }

    @Override
    public DocumentFolder document() {
        return currentDocument;
    }

    @Override
    public VersionRepository repository() {
        return currentRepository;
    }

    DocumentFolder currentDocument() {
        return currentDocument;
    }

    VersionRepository currentRepository() {
        return currentRepository;
    }

    void importDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import a document");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                        "Documents", "*.docx", "*.xlsx", "*.md", "*.markdown"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) {
            return;
        }
        try {
            Path folderPath = new ImportService(libraryRoot).importFile(chosen.toPath());
            status.accept("Imported " + folderPath.getFileName() + ".");
            afterChange.run();
        } catch (Exception exception) {
            error.accept("Import failed: " + exception.getMessage());
        }
    }

    void openInLibreOffice() {
        if (currentDocument == null) {
            return;
        }
        try {
            Path file = currentDocument.workingFile().orElseThrow();
            String executable = OfficeLocator.locate()
                    .map(Path::toString)
                    .orElse("libreoffice");
            new ProcessBuilder(executable, file.toString()).start();
            status.accept("Opened in LibreOffice. Save there, then click Snapshot version.");
        } catch (Exception exception) {
            error.accept("Could not open LibreOffice: " + exception.getMessage());
        }
    }

    /** Opens the settings dialog and reports what LibreOffice detection now finds. */
    void openSettings() {
        SettingsDialog dialog = new SettingsDialog(stage);
        Optional<Boolean> saved = dialog.showAndWait();
        if (saved.isEmpty() || !saved.get()) {
            return;
        }
        Optional<Path> office = OfficeLocator.locate();
        status.accept(office
                .map(path -> "Settings saved. LibreOffice: " + path)
                .orElse("Settings saved. No LibreOffice found; the page view will show a "
                        + "placeholder."));
    }

    void takeSnapshot() {
        versionActions.takeSnapshot();
    }

    void deleteSnapshot(VersionEntry version) {
        versionActions.deleteSnapshot(version);
    }

    void restoreSnapshot(VersionEntry version) {
        versionActions.restoreSnapshot(version);
    }

    void discardWorkingChanges() {
        versionActions.discardWorkingChanges();
    }

    void createBranch() {
        branchActions.createBranch();
    }

    void switchBranch() {
        branchActions.switchBranch();
    }

    void mergeBranch() {
        branchActions.mergeBranch();
    }

    void deleteBranch() {
        branchActions.deleteBranch();
    }
}
