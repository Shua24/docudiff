package dev.docudiff.ui;

import dev.docudiff.library.DocumentFolder;
import dev.docudiff.library.ImportService;
import dev.docudiff.library.LibraryRoot;
import dev.docudiff.versioning.SnapshotNameGenerator;
import dev.docudiff.versioning.VersionEntry;
import dev.docudiff.versioning.VersionRepository;
import dev.docudiff.versioning.WorkingFileState;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Every user-triggered document operation: import, snapshot, branch, switch, merge.
 *
 * <p>All dialogs live here so {@link MainWindow} stays a layout class.
 */
final class DocumentActions {

    private final Stage stage;
    private final LibraryRoot libraryRoot;
    private final BackgroundRunner runner;
    private final Runnable afterChange;
    private final java.util.function.Consumer<String> statusHandler;
    private final java.util.function.Consumer<String> errorHandler;

    private DocumentFolder currentDocument;
    private VersionRepository currentRepository;

    DocumentActions(
            Stage stage,
            LibraryRoot libraryRoot,
            BackgroundRunner runner,
            Runnable afterChange,
            java.util.function.Consumer<String> statusHandler,
            java.util.function.Consumer<String> errorHandler) {
        this.stage = stage;
        this.libraryRoot = libraryRoot;
        this.runner = runner;
        this.afterChange = afterChange;
        this.statusHandler = statusHandler;
        this.errorHandler = errorHandler;
    }

    void setCurrentDocument(DocumentFolder documentFolder, VersionRepository repository) {
        this.currentDocument = documentFolder;
        this.currentRepository = repository;
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
            statusHandler.accept("Imported " + folderPath.getFileName() + ".");
            afterChange.run();
        } catch (Exception exception) {
            errorHandler.accept("Import failed: " + exception.getMessage());
        }
    }

    void takeSnapshot() {
        if (currentDocument == null || currentRepository == null) {
            statusHandler.accept("Open a document first.");
            return;
        }
        try {
            String fileName = currentDocument.workingFileName();
            WorkingFileState state = currentRepository.probeWorkingFile(fileName);
            if (state == WorkingFileState.CLEAN) {
                statusHandler.accept("The working file has not changed since the last snapshot.");
                return;
            }
            if (state == WorkingFileState.MISSING) {
                errorHandler.accept("The working file is missing from the library folder.");
                return;
            }
            String documentName = currentDocument.documentName();
            String suggestedName = new SnapshotNameGenerator().next(
                    documentName, LocalDate.now(), currentRepository.existingSnapshotNames());
            Optional<String> chosenName = askForName("Snapshot version", "Name this version",
                    "Name:", suggestedName);
            if (chosenName.isEmpty()) {
                return;
            }
            commitSnapshot(documentName, chosenName.get());
        } catch (Exception exception) {
            errorHandler.accept("Could not snapshot: " + exception.getMessage());
        }
    }

    /*
     * The commit and the re-render that follows it both take real time on a large document.
     * Running them through the runner is what gives the user the "Making snapshot…" overlay
     * instead of a window that appears to have ignored the click.
     */
    private void commitSnapshot(String documentName, String snapshotName) {
        runner.run("Making snapshot \"" + snapshotName + "\"…",
                () -> currentRepository.snapshot(documentName, snapshotName),
                commitId -> {
                    statusHandler.accept("Saved version " + snapshotName + ".");
                    afterChange.run();
                });
    }

    void openInLibreOffice() {
        if (currentDocument == null) {
            return;
        }
        try {
            Path file = currentDocument.workingFile().orElseThrow();
            String executable = dev.docudiff.render.OfficeLocator.locate()
                    .map(Path::toString)
                    .orElse("libreoffice");
            new ProcessBuilder(executable, file.toString()).start();
            statusHandler.accept(
                    "Opened in LibreOffice. Save there, then click Snapshot version.");
        } catch (Exception exception) {
            errorHandler.accept("Could not open LibreOffice: " + exception.getMessage());
        }
    }

    void createBranch() {
        if (currentRepository == null) {
            return;
        }
        Optional<String> branchName = askForName(
                "New branch", "Name the branch", "Branch name:", "experiment");
        if (branchName.isEmpty()) {
            return;
        }
        try {
            currentRepository.createBranch(branchName.get());
            statusHandler.accept("Created branch " + branchName.get() + ".");
        } catch (Exception exception) {
            errorHandler.accept("Could not create branch: " + exception.getMessage());
        }
    }

    void switchBranch() {
        if (currentRepository == null) {
            return;
        }
        try {
            Optional<String> chosenBranch = askToChooseBranch(
                    "Switch branch", "Which branch do you want to work on?", "Branch:");
            if (chosenBranch.isEmpty()) {
                return;
            }
            currentRepository.checkoutBranch(chosenBranch.get());
            statusHandler.accept("Switched to " + chosenBranch.get() + ".");
            afterChange.run();
        } catch (IllegalStateException refusal) {
            errorHandler.accept(refusal.getMessage());
        } catch (Exception exception) {
            errorHandler.accept("Could not switch branch: " + exception.getMessage());
        }
    }

    void mergeBranch() {
        if (currentRepository == null) {
            return;
        }
        try {
            Optional<String> sourceBranch = askToChooseBranch(
                    "Merge branch",
                    "Take the chosen branch's version into the current branch.\n"
                            + "No content is merged: this records a fork join.",
                    "Take content from:");
            if (sourceBranch.isEmpty()) {
                return;
            }
            String targetBranch = currentRepository.currentBranch();
            if (targetBranch.equals(sourceBranch.get())) {
                statusHandler.accept("A branch cannot be merged into itself.");
                return;
            }
            currentRepository.mergeForkOnly(targetBranch, sourceBranch.get());
            statusHandler.accept(
                    "Merged " + sourceBranch.get() + " into " + targetBranch + ".");
            afterChange.run();
        } catch (Exception exception) {
            errorHandler.accept("Could not merge: " + exception.getMessage());
        }
    }

    private Optional<String> askForName(
            String title, String header, String contentLabel, String suggested) {
        TextInputDialog dialog = new TextInputDialog(suggested);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(contentLabel);
        dialog.initOwner(stage);
        Optional<String> result = dialog.showAndWait()
                .map(String::trim)
                .filter(text -> !text.isBlank());
        return result;
    }

    private Optional<String> askToChooseBranch(String title, String header, String contentLabel)
            throws Exception {
        List<String> branches = currentRepository.listBranches();
        if (branches.isEmpty()) {
            return Optional.empty();
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(branches.get(0), branches);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(contentLabel);
        dialog.initOwner(stage);
        return dialog.showAndWait();
    }

    void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.initOwner(stage);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
