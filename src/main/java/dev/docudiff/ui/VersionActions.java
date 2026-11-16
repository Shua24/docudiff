package dev.docudiff.ui;

import dev.docudiff.library.DocumentFolder;
import dev.docudiff.versioning.SnapshotNameGenerator;
import dev.docudiff.versioning.VersionEntry;
import dev.docudiff.versioning.VersionRepository;
import dev.docudiff.versioning.WorkingFileState;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Operations on versions and on the working file: snapshot, delete a snapshot, restore a deleted
 * one, and discard external edits.
 *
 * <p>Only {@link #discardWorkingChanges()} destroys content, and it asks first. Deleting a
 * snapshot does not destroy anything: the commit stays in the repository and
 * {@code Show deleted} brings it back.
 */
final class VersionActions {

    private final Stage stage;
    private final DocumentSelection selection;
    private final BackgroundRunner runner;
    private final Runnable afterChange;
    private final Consumer<String> status;
    private final Consumer<String> error;

    VersionActions(
            Stage stage,
            DocumentSelection selection,
            BackgroundRunner runner,
            Runnable afterChange,
            Consumer<String> status,
            Consumer<String> error) {
        this.stage = stage;
        this.selection = selection;
        this.runner = runner;
        this.afterChange = afterChange;
        this.status = status;
        this.error = error;
    }

    void takeSnapshot() {
        DocumentFolder document = selection.document();
        VersionRepository repository = selection.repository();
        if (document == null || repository == null) {
            status.accept("Open a document first.");
            return;
        }
        try {
            String fileName = document.workingFileName();
            WorkingFileState state = repository.probeWorkingFile(fileName);
            if (state == WorkingFileState.CLEAN) {
                status.accept("The working file has not changed since the last snapshot.");
                return;
            }
            if (state == WorkingFileState.MISSING) {
                error.accept("The working file is missing from the library folder.");
                return;
            }
            String documentName = document.documentName();
            String suggestedName = new SnapshotNameGenerator().next(
                    documentName, LocalDate.now(), repository.existingSnapshotNames());
            Optional<String> chosenName = Dialogs.askForName(
                    stage, "Snapshot version", "Name this version", "Name:", suggestedName);
            if (chosenName.isEmpty()) {
                return;
            }
            commitSnapshot(documentName, chosenName.get());
        } catch (Exception exception) {
            error.accept("Could not snapshot: " + exception.getMessage());
        }
    }

    /*
     * The commit and the re-render that follows it both take real time on a large document.
     * Running them through the runner is what gives the user the "Making snapshot…" overlay
     * instead of a window that appears to have ignored the click.
     */
    private void commitSnapshot(String documentName, String snapshotName) {
        runner.run("Making snapshot \"" + snapshotName + "\"…",
                () -> selection.repository().snapshot(documentName, snapshotName),
                commitId -> {
                    status.accept("Saved version " + snapshotName + ".");
                    afterChange.run();
                });
    }

    void deleteSnapshot(VersionEntry version) {
        VersionRepository repository = selection.repository();
        if (repository == null || version == null) {
            status.accept("Pick a version in the Versions tab first.");
            return;
        }
        if (version.deleted()) {
            status.accept("That version is already deleted.");
            return;
        }
        boolean confirmed = Dialogs.confirm(stage, "Delete version",
                "Delete the version \"" + version.name() + "\"?",
                "It disappears from the version list. Nothing is rewritten: the version is kept "
                        + "in the document's history and comes back with \"Show deleted\".");
        if (!confirmed) {
            return;
        }
        runner.run("Deleting version…",
                () -> {
                    repository.deleteSnapshot(version.commitId());
                    return null;
                },
                unused -> {
                    status.accept("Deleted version " + version.name() + ".");
                    afterChange.run();
                });
    }

    void restoreSnapshot(VersionEntry version) {
        VersionRepository repository = selection.repository();
        if (repository == null || version == null) {
            return;
        }
        runner.run("Restoring version…",
                () -> {
                    repository.restoreSnapshot(version.commitId());
                    return null;
                },
                unused -> {
                    status.accept("Restored version " + version.name() + ".");
                    afterChange.run();
                });
    }

    void discardWorkingChanges() {
        DocumentFolder document = selection.document();
        VersionRepository repository = selection.repository();
        if (document == null || repository == null) {
            status.accept("Open a document first.");
            return;
        }
        try {
            String fileName = document.workingFileName();
            WorkingFileState state = repository.probeWorkingFile(fileName);
            if (state == WorkingFileState.CLEAN) {
                status.accept("The working file already matches the newest snapshot.");
                return;
            }
            boolean confirmed = Dialogs.confirm(stage, "Discard changes",
                    state == WorkingFileState.MISSING
                            ? "Put the missing file back?"
                            : "Throw away the changes to \"" + fileName + "\"?",
                    "The file goes back to the newest snapshot of this branch. Edits made since "
                            + "then cannot be recovered afterwards.");
            if (!confirmed) {
                return;
            }
            runner.run("Discarding changes…",
                    () -> {
                        repository.discardWorkingChanges(fileName);
                        return null;
                    },
                    unused -> {
                        status.accept("The working file matches the newest snapshot again.");
                        afterChange.run();
                    });
        } catch (Exception exception) {
            error.accept("Could not discard the changes: " + exception.getMessage());
        }
    }
}
