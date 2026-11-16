package dev.docudiff.versioning;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lifecycle operations added in v1: delete a branch, discard working changes, and mark a
 * version deleted without rewriting history.
 */
class VersionRepositoryLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    private Git git;
    private VersionRepository repository;
    private Path workingFile;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(temporaryDirectory.toFile()).setInitialBranch("main").call();
        workingFile = temporaryDirectory.resolve("report.docx");
        repository = new VersionRepository(temporaryDirectory);
    }

    @AfterEach
    void tearDown() {
        git.close();
    }

    @Test
    @DisplayName("a branch that is not checked out can be deleted")
    void nonCurrentBranchCanBeDeleted() throws Exception {
        Files.writeString(workingFile, "version one");
        repository.snapshot("report", "report-snapshot-2026-11-11");
        repository.createBranch("experiment");

        repository.deleteBranch("experiment");

        assertFalse(repository.listBranches().contains("experiment"));
        assertTrue(repository.listBranches().contains("main"));
    }

    @Test
    @DisplayName("the checked-out branch is refused with a message the UI can show")
    void currentBranchIsRefused() throws Exception {
        Files.writeString(workingFile, "version one");
        repository.snapshot("report", "report-snapshot-2026-11-11");

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> repository.deleteBranch("main"));

        assertTrue(thrown.getMessage().contains("branch you are on"),
                "the refusal must explain why, got: " + thrown.getMessage());
        assertTrue(repository.listBranches().contains("main"));
    }

    @Test
    @DisplayName("deleting a branch that does not exist is refused")
    void unknownBranchIsRefused() throws Exception {
        Files.writeString(workingFile, "version one");
        repository.snapshot("report", "report-snapshot-2026-11-11");

        assertThrows(IllegalStateException.class, () -> repository.deleteBranch("nope"));
    }

    @Test
    @DisplayName("a deleted version is hidden but its commit and bytes survive")
    void deletedVersionStaysInRepository() throws Exception {
        Files.writeString(workingFile, "version one");
        String firstCommit = repository.snapshot("report", "report-snapshot-2026-11-11");
        Files.writeString(workingFile, "version two");
        String secondCommit = repository.snapshot("report", "report-snapshot-2026-11-11-2");

        repository.deleteSnapshot(firstCommit);

        VersionEntry firstEntry = entryOf(repository.listVersions(), firstCommit);
        assertTrue(firstEntry.deleted(), "the version list must know the version is deleted");
        assertEquals("version one", new String(repository.bytesOfVersion("report.docx", firstCommit)),
                "a deleted version is only hidden in the list; its content must still be readable");
        assertEquals("version two",
                new String(repository.bytesOfVersion("report.docx", secondCommit)));
    }

    @Test
    @DisplayName("restoring a deleted version clears the marker")
    void restoreClearsDeletion() throws Exception {
        Files.writeString(workingFile, "version one");
        String commitId = repository.snapshot("report", "report-snapshot-2026-11-11");
        repository.deleteSnapshot(commitId);

        repository.restoreSnapshot(commitId);

        assertFalse(entryOf(repository.listVersions(), commitId).deleted());
    }

    @Test
    @DisplayName("a deleted branch head is still reported as a branch head")
    void deletedHeadIsStillABranchHead() throws Exception {
        Files.writeString(workingFile, "version one");
        String commitId = repository.snapshot("report", "report-snapshot-2026-11-11");

        repository.deleteSnapshot(commitId);

        Set<String> heads = repository.branchHeadCommitIds();
        assertTrue(heads.contains(commitId),
                "hiding a live branch head would leave the branch pointing at an invisible version");
    }

    @Test
    @DisplayName("discarding changes puts the branch head's bytes back")
    void discardRestoresHeadBytes() throws Exception {
        Files.writeString(workingFile, "version one");
        repository.snapshot("report", "report-snapshot-2026-11-11");
        Files.writeString(workingFile, "unsaved scribble");
        assertEquals(WorkingFileState.MODIFIED,
                repository.probeWorkingFile("report.docx"));

        repository.discardWorkingChanges("report.docx");

        assertEquals("version one", Files.readString(workingFile));
        assertEquals(WorkingFileState.CLEAN, repository.probeWorkingFile("report.docx"));
    }

    @Test
    @DisplayName("discarding changes with no snapshot explains why it cannot")
    void discardWithoutSnapshotIsRefused() throws Exception {
        Files.writeString(workingFile, "nothing committed yet");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> repository.discardWorkingChanges("report.docx"));

        assertTrue(thrown.getMessage().contains("no snapshot"),
                "the refusal must say there is nothing to restore, got: " + thrown.getMessage());
        assertEquals("nothing committed yet", Files.readString(workingFile),
                "a refused discard must not touch the working file");
    }

    private VersionEntry entryOf(List<VersionEntry> versions, String commitId) {
        return versions.stream()
                .filter(version -> version.commitId().equals(commitId))
                .findFirst()
                .orElseThrow();
    }
}
