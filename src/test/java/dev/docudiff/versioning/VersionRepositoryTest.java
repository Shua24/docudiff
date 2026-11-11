package dev.docudiff.versioning;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionRepositoryTest {

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
    @DisplayName("a fresh repository reports the working file as missing")
    void freshRepositoryReportsMissing() throws Exception {
        WorkingFileState state = repository.probeWorkingFile("report.docx");

        assertEquals(WorkingFileState.MISSING, state);
    }

    @Test
    @DisplayName("a committed working file reports clean")
    void committedWorkingFileReportsClean() throws Exception {
        Files.writeString(workingFile, "version one");
        repository.snapshot("report", "report-snapshot-2026-11-11");

        WorkingFileState state = repository.probeWorkingFile("report.docx");

        assertEquals(WorkingFileState.CLEAN, state);
    }

    @Test
    @DisplayName("an externally edited working file reports modified")
    void externallyEditedWorkingFileReportsModified() throws Exception {
        Files.writeString(workingFile, "version one");
        repository.snapshot("report", "report-snapshot-2026-11-11");
        Files.writeString(workingFile, "version two, edited in LibreOffice");

        WorkingFileState state = repository.probeWorkingFile("report.docx");

        assertEquals(WorkingFileState.MODIFIED, state);
    }

    @Test
    @DisplayName("a snapshot records the given name as the commit message")
    void snapshotRecordsGivenName() throws Exception {
        Files.writeString(workingFile, "version one");

        String commitId = repository.snapshot("report", "report-snapshot-2026-11-11");

        List<VersionEntry> versions = repository.listVersions();
        assertEquals(1, versions.size());
        assertEquals("report-snapshot-2026-11-11", versions.get(0).name());
        assertEquals(commitId, versions.get(0).commitId());
    }

    @Test
    @DisplayName("existing snapshot names are offered to the name generator")
    void existingNamesAreListed() throws Exception {
        Files.writeString(workingFile, "version one");
        repository.snapshot("report", "report-snapshot-2026-11-11");
        Files.writeString(workingFile, "version two");
        repository.snapshot("report", "report-snapshot-2026-11-11-2");

        List<String> names = repository.existingSnapshotNames();

        assertEquals(List.of("report-snapshot-2026-11-11", "report-snapshot-2026-11-11-2"), names);
    }

    @Test
    @DisplayName("a branch can be created and listed")
    void branchCanBeCreatedAndListed() throws Exception {
        Files.writeString(workingFile, "version one");
        repository.snapshot("report", "report-snapshot-2026-11-11");

        repository.createBranch("experiment");

        assertTrue(repository.listBranches().contains("experiment"));
        assertTrue(repository.listBranches().contains("main"));
    }

    @Test
    @DisplayName("materializing the branch head restores the working file and reports clean")
    void materializeHeadRestoresWorkingFile() throws Exception {
        Files.writeString(workingFile, "version one");
        repository.snapshot("report", "report-snapshot-2026-11-11");
        Files.writeString(workingFile, "version two");
        String secondCommit = repository.snapshot("report", "report-snapshot-2026-11-11-2");
        Files.writeString(workingFile, "unsaved scribble");

        repository.materialize("report.docx", secondCommit);

        assertEquals("version two", Files.readString(workingFile));
        assertEquals(WorkingFileState.CLEAN, repository.probeWorkingFile("report.docx"));
    }

    @Test
    @DisplayName("materializing an older version leaves the file modified relative to the head")
    void materializeOlderVersionLeavesFileModified() throws Exception {
        Files.writeString(workingFile, "version one");
        String firstCommit = repository.snapshot("report", "report-snapshot-2026-11-11");
        Files.writeString(workingFile, "version two");
        repository.snapshot("report", "report-snapshot-2026-11-11-2");

        repository.materialize("report.docx", firstCommit);

        assertEquals("version one", Files.readString(workingFile));
        assertEquals(WorkingFileState.MODIFIED, repository.probeWorkingFile("report.docx"),
                "checking out an older version is a deliberate revert, so it must read as modified "
                        + "against the head rather than silently looking clean");
    }

    @Test
    @DisplayName("reading a version's bytes does not touch the working file")
    void readingVersionBytesDoesNotTouchWorkingFile() throws Exception {
        Files.writeString(workingFile, "version one");
        String firstCommit = repository.snapshot("report", "report-snapshot-2026-11-11");
        Files.writeString(workingFile, "version two");
        repository.snapshot("report", "report-snapshot-2026-11-11-2");

        byte[] firstVersionBytes = repository.bytesOfVersion("report.docx", firstCommit);

        assertEquals("version one", new String(firstVersionBytes));
        assertEquals("version two", Files.readString(workingFile),
                "looking at an old version must not overwrite the working file");
    }

    @Test
    @DisplayName("a fork-only merge has two parents and takes the source tree")
    void forkOnlyMergeHasTwoParentsAndSourceTree() throws Exception {
        Files.writeString(workingFile, "base");
        repository.snapshot("report", "report-snapshot-2026-11-11");
        repository.createBranch("experiment");

        Files.writeString(workingFile, "main change");
        String mainCommit = repository.snapshot("report", "report-snapshot-2026-11-11-2");

        repository.checkoutBranch("experiment");
        Files.writeString(workingFile, "experiment change");
        String experimentCommit = repository.snapshot("report", "report-snapshot-2026-11-11-3");
        repository.checkoutBranch("main");

        String mergeCommit = repository.mergeForkOnly("main", "experiment");

        List<String> parentIds = repository.parentIdsOf(mergeCommit);
        assertEquals(2, parentIds.size(), "a fork merge must have two parents");
        assertTrue(parentIds.contains(mainCommit));
        assertTrue(parentIds.contains(experimentCommit));
        assertEquals(
                repository.treeIdOf(experimentCommit),
                repository.treeIdOf(mergeCommit),
                "the merge tree must equal the source branch's tree");
        assertNotEquals(mainCommit, mergeCommit);
    }

    @Test
    @DisplayName("a fork-only merge never writes conflict markers into the working file")
    void forkOnlyMergeNeverWritesConflictMarkers() throws Exception {
        Files.writeString(workingFile, "line one\nline two\n");
        repository.snapshot("report", "report-snapshot-2026-11-11");
        repository.createBranch("experiment");

        Files.writeString(workingFile, "line one CHANGED ON MAIN\nline two\n");
        repository.snapshot("report", "report-snapshot-2026-11-11-2");

        repository.checkoutBranch("experiment");
        Files.writeString(workingFile, "line one CHANGED ON EXPERIMENT\nline two\n");
        repository.snapshot("report", "report-snapshot-2026-11-11-3");
        repository.checkoutBranch("main");

        repository.mergeForkOnly("main", "experiment");

        String workingContent = Files.readString(workingFile);
        assertTrue(
                !workingContent.contains("<<<<<<<"),
                "MergeCommand writes conflict markers into the working file; mergeForkOnly must not. "
                        + "Actual content: " + workingContent);
        assertEquals("line one CHANGED ON EXPERIMENT\nline two\n", workingContent);
    }

    @Test
    @DisplayName("switching branches is refused while the working file is modified")
    void branchSwitchRefusedWhileModified() throws Exception {
        Files.writeString(workingFile, "version one");
        repository.snapshot("report", "report-snapshot-2026-11-11");
        repository.createBranch("experiment");
        Files.writeString(workingFile, "unsaved edit");

        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> repository.checkoutBranch("experiment"));

        assertTrue(thrown.getMessage().contains("modified"),
                "the refusal must say the working file is modified, got: " + thrown.getMessage());
    }
}
