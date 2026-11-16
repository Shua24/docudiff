package dev.docudiff.ui;

import dev.docudiff.versioning.VersionRepository;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Branch operations: create, switch, merge, delete.
 *
 * <p>Split out of {@link DocumentActions} when the project's file-length limit was reached; the
 * behaviour and the dialog wording are unchanged from where they used to live.
 */
final class BranchActions {

    private final Stage stage;
    private final DocumentSelection selection;
    private final Runnable afterChange;
    private final Consumer<String> status;
    private final Consumer<String> error;

    BranchActions(
            Stage stage,
            DocumentSelection selection,
            Runnable afterChange,
            Consumer<String> status,
            Consumer<String> error) {
        this.stage = stage;
        this.selection = selection;
        this.afterChange = afterChange;
        this.status = status;
        this.error = error;
    }

    void createBranch() {
        VersionRepository repository = selection.repository();
        if (repository == null) {
            return;
        }
        Optional<String> branchName = Dialogs.askForName(
                stage, "New branch", "Name the branch", "Branch name:", "experiment");
        if (branchName.isEmpty()) {
            return;
        }
        try {
            repository.createBranch(branchName.get());
            status.accept("Created branch " + branchName.get() + ".");
            afterChange.run();
        } catch (Exception exception) {
            error.accept("Could not create branch: " + exception.getMessage());
        }
    }

    void switchBranch() {
        VersionRepository repository = selection.repository();
        if (repository == null) {
            return;
        }
        try {
            Optional<String> chosenBranch = Dialogs.choose(stage, "Switch branch",
                    "Which branch do you want to work on?", "Branch:", repository.listBranches());
            if (chosenBranch.isEmpty()) {
                return;
            }
            repository.checkoutBranch(chosenBranch.get());
            status.accept("Switched to " + chosenBranch.get() + ".");
            afterChange.run();
        } catch (IllegalStateException refusal) {
            error.accept(refusal.getMessage());
        } catch (Exception exception) {
            error.accept("Could not switch branch: " + exception.getMessage());
        }
    }

    void mergeBranch() {
        VersionRepository repository = selection.repository();
        if (repository == null) {
            return;
        }
        try {
            Optional<String> sourceBranch = Dialogs.choose(stage, "Merge branch",
                    "Take the chosen branch's version into the current branch.\n"
                            + "No content is merged: this records a fork join.",
                    "Take content from:", repository.listBranches());
            if (sourceBranch.isEmpty()) {
                return;
            }
            String targetBranch = repository.currentBranch();
            if (targetBranch.equals(sourceBranch.get())) {
                status.accept("A branch cannot be merged into itself.");
                return;
            }
            repository.mergeForkOnly(targetBranch, sourceBranch.get());
            status.accept("Merged " + sourceBranch.get() + " into " + targetBranch + ".");
            afterChange.run();
        } catch (Exception exception) {
            error.accept("Could not merge: " + exception.getMessage());
        }
    }

    /**
     * Deletes a branch. The checked-out branch is offered only after a switch, because git itself
     * refuses to delete the branch in use.
     */
    void deleteBranch() {
        VersionRepository repository = selection.repository();
        if (repository == null) {
            return;
        }
        try {
            String currentBranch = repository.currentBranch();
            List<String> deletable = repository.listBranches().stream()
                    .filter(name -> !name.equals(currentBranch))
                    .toList();
            if (deletable.isEmpty()) {
                status.accept("This document has only one branch, and it is the one you are on.");
                return;
            }
            Optional<String> chosen = Dialogs.choose(stage, "Delete branch",
                    "Which branch do you want to delete?", "Branch:", deletable);
            if (chosen.isEmpty()) {
                return;
            }
            boolean confirmed = Dialogs.confirm(stage, "Delete branch",
                    "Delete the branch \"" + chosen.get() + "\"?",
                    "Its versions disappear from the version list. The commits stay in the "
                            + "repository, so the document's files are not damaged, but this app "
                            + "cannot bring the branch back.");
            if (!confirmed) {
                return;
            }
            repository.deleteBranch(chosen.get());
            status.accept("Deleted branch " + chosen.get() + ".");
            afterChange.run();
        } catch (IllegalStateException refusal) {
            error.accept(refusal.getMessage());
        } catch (Exception exception) {
            error.accept("Could not delete branch: " + exception.getMessage());
        }
    }
}
