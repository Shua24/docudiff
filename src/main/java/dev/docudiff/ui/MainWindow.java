package dev.docudiff.ui;

import dev.docudiff.library.DocumentFolder;
import dev.docudiff.library.LibraryRoot;
import dev.docudiff.versioning.DocumentFormat;
import dev.docudiff.versioning.VersionEntry;
import dev.docudiff.versioning.VersionRepository;
import dev.docudiff.versioning.WorkingFileState;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * The main window: library on the left, document / diff / versions tabs on the right, actions
 * along the bottom, and a busy overlay that covers the whole window while slow work runs.
 *
 * <p>Owns layout and view state only. Document operations live in {@link DocumentActions}; the
 * comparison surface lives in {@link DiffTab}.
 *
 * <p><b>Every slow operation goes through {@link #run}.</b> Rendering a docx shells out to
 * LibreOffice and rasterises a PDF; doing that on the JavaFX thread both freezes the window and
 * makes the busy overlay impossible, because a blocked thread cannot paint a spinner.
 */
public final class MainWindow extends StackPane implements BackgroundRunner {

    private final LibraryRoot libraryRoot;
    private final ComparisonService comparisonService = new ComparisonService();
    private final BusyOverlay busyOverlay = new BusyOverlay();
    private final BorderPane content = new BorderPane();

    private final LibraryPane libraryPane;
    private final VersionListPane versionListPane;
    private final DocumentViewPane documentViewPane = new DocumentViewPane();
    private final DiffTab diffTab;
    private final Label statusLabel = UiParts.muted("Ready.");
    private final DocumentActions actions;

    public MainWindow(Stage stage) {
        this.libraryRoot = LibraryRoot.defaultRoot();
        this.actions = new DocumentActions(
                stage, libraryRoot, this, this::refreshLibrary, this::setStatus, this::showError);
        this.libraryPane = new LibraryPane(libraryRoot, this::openDocument);
        this.versionListPane = new VersionListPane((version, unusedFlag) -> {
            DocumentFolder documentFolder = actions.currentDocument();
            VersionRepository repository = actions.currentRepository();
            if (documentFolder != null && repository != null) {
                renderDocumentView(documentFolder, repository, version);
            }
        });
        this.diffTab = new DiffTab(this, this::setStatus);

        content.setLeft(libraryPane);
        content.setCenter(buildTabs());
        content.setBottom(buildToolbar());
        content.setPadding(new Insets(8));
        libraryPane.setPrefWidth(240);

        getChildren().addAll(content, busyOverlay);
    }

    private TabPane buildTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
                new Tab("Document", documentViewPane),
                new Tab("Diff", diffTab),
                new Tab("Versions", versionListPane));
        return tabPane;
    }

    private javafx.scene.Node buildToolbar() {
        Button importButton = new Button("Import…");
        importButton.setOnAction(event -> actions.importDocument());

        Button snapshotButton = new Button("Snapshot version");
        snapshotButton.setOnAction(event -> actions.takeSnapshot());

        Button openButton = new Button("Open in LibreOffice");
        openButton.setOnAction(event -> actions.openInLibreOffice());

        Button branchButton = new Button("New branch…");
        branchButton.setOnAction(event -> actions.createBranch());

        Button switchButton = new Button("Switch branch…");
        switchButton.setOnAction(event -> actions.switchBranch());

        Button mergeButton = new Button("Merge branch…");
        mergeButton.setOnAction(event -> actions.mergeBranch());

        Button helpButton = new Button("How to read the diff");
        helpButton.getStyleClass().add("help-button");
        helpButton.setOnAction(event -> DiffLegend.showHelpDialog(windowOrNull()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttons = new HBox(8,
                importButton, snapshotButton, openButton,
                branchButton, switchButton, mergeButton, spacer, helpButton);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        return new VBox(4, statusLabel, buttons);
    }

    @Override
    public <T> void run(String busyMessage, Callable<T> work, Consumer<T> onSuccess) {
        busyOverlay.show(busyMessage);
        Task<T> task = BackgroundRunner.taskOf(work);
        task.setOnSucceeded(event -> {
            busyOverlay.hide();
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busyOverlay.hide();
            showError("Something went wrong: " + task.getException().getMessage());
        });
        Thread worker = new Thread(task, "docudiff-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Lets a long operation update the overlay as it moves between stages. */
    void updateBusyMessage(String message) {
        busyOverlay.setMessage(message);
    }

    private void refreshLibrary() {
        libraryPane.refresh(libraryRoot);
        DocumentFolder current = actions.currentDocument();
        if (current != null) {
            libraryPane.selectByName(current.documentName());
        }
    }

    private void openDocument(DocumentFolder documentFolder) {
        VersionRepository repository = new VersionRepository(documentFolder.folderPath());
        actions.setCurrentDocument(documentFolder, repository);
        refreshVersions(documentFolder, repository);
    }

    private void refreshVersions(DocumentFolder documentFolder, VersionRepository repository) {
        run("Reading version history…", repository::listVersions, versions -> {
            versionListPane.setVersions(versions);
            diffTab.setDocument(documentFolder, repository, versions);
            showCurrentVersion(documentFolder, repository, versions);
        });
    }

    private void showCurrentVersion(
            DocumentFolder documentFolder,
            VersionRepository repository,
            List<VersionEntry> versions) {
        run("Checking the working file…",
                () -> repository.probeWorkingFile(documentFolder.workingFileName()),
                (WorkingFileState state) -> {
                    setStatus("Working file: "
                            + state.name().toLowerCase(java.util.Locale.ROOT) + ".");
                    if (versions.isEmpty()) {
                        documentViewPane.showPlaceholder("This document has no versions yet.");
                        return;
                    }
                    renderDocumentView(documentFolder, repository, versions.get(0));
                });
    }

    private void renderDocumentView(
            DocumentFolder documentFolder,
            VersionRepository repository,
            VersionEntry version) {
        DocumentFormat format;
        String fileName;
        try {
            format = documentFolder.format();
            fileName = documentFolder.workingFileName();
        } catch (Exception exception) {
            documentViewPane.showPlaceholder("This folder holds no supported document.");
            return;
        }
        if (format == DocumentFormat.XLSX) {
            documentViewPane.showPlaceholder(
                    "Spreadsheets appear in the Diff tab as aligned rows.");
            return;
        }
        Callable<Path> scratchCopy = () -> comparisonService.scratchFileOf(
                repository, fileName, version.commitId());

        if (format == DocumentFormat.MARKDOWN) {
            run("Loading document…",
                    () -> Files.readString(scratchCopy.call()),
                    documentViewPane::showMarkdown);
            return;
        }
        run("Rendering page view…",
                scratchCopy,
                scratchFile -> documentViewPane.showDocx(
                        scratchFile, documentFolder.cacheDirectory(), version.commitId()));
    }

    void setStatus(String message) {
        statusLabel.setText(message);
    }

    void showError(String message) {
        setStatus(message);
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        if (windowOrNull() != null) {
            alert.initOwner(windowOrNull());
        }
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private Window windowOrNull() {
        return getScene() == null ? null : getScene().getWindow();
    }
}
