package dev.docudiff.compare;

import dev.docudiff.library.DocumentFolder;
import dev.docudiff.versioning.DocumentFormat;
import dev.docudiff.versioning.VersionRepository;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentSourceResolverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("two files compare with no document open at all")
    void fileToFileNeedsNoRepository() throws Exception {
        Path left = Files.writeString(temporaryDirectory.resolve("before.md"), "# A\n\none\n");
        Path right = Files.writeString(temporaryDirectory.resolve("after.md"), "# A\n\ntwo\n");
        DocumentSourceResolver resolver = new DocumentSourceResolver(null, null);

        DocumentSource leftSource = resolver.resolve(new ComparisonSlot.FileSlot(
                "before.md", left, DocumentFormat.MARKDOWN));
        DocumentSource rightSource = resolver.resolve(new ComparisonSlot.FileSlot(
                "after.md", right, DocumentFormat.MARKDOWN));

        assertEquals("before.md", leftSource.label());
        assertEquals(DocumentFormat.MARKDOWN, leftSource.format());
        assertTrue(new String(rightSource.bytes(), StandardCharsets.UTF_8).contains("two"));
    }

    @Test
    @DisplayName("the working file is readable as a source, including unsaved edits")
    void workingFileIsReadable() throws Exception {
        Path folderPath = createRepositoryWithOneVersion("first version");
        DocumentFolder documentFolder = new DocumentFolder(folderPath);
        VersionRepository repository = new VersionRepository(folderPath);
        Files.write(folderPath.resolve("notes.md"), "unsaved edit".getBytes(StandardCharsets.UTF_8));

        DocumentSourceResolver resolver = new DocumentSourceResolver(repository, documentFolder);
        DocumentSource source = resolver.resolve(
                new ComparisonSlot.WorkingFileSlot("working file"));

        assertTrue(new String(source.bytes(), StandardCharsets.UTF_8).contains("unsaved edit"),
                "the working file slot must read the file on disk, not the last snapshot");
    }

    @Test
    @DisplayName("a version slot reads the committed bytes, not the working file")
    void versionSlotReadsCommittedBytes() throws Exception {
        Path folderPath = createRepositoryWithOneVersion("committed text");
        DocumentFolder documentFolder = new DocumentFolder(folderPath);
        VersionRepository repository = new VersionRepository(folderPath);
        String commitId = repository.listVersions().get(0).commitId();
        Files.write(folderPath.resolve("notes.md"), "diverged".getBytes(StandardCharsets.UTF_8));

        DocumentSourceResolver resolver = new DocumentSourceResolver(repository, documentFolder);
        DocumentSource source = resolver.resolve(
                new ComparisonSlot.VersionSlot("v1", commitId));

        assertTrue(new String(source.bytes(), StandardCharsets.UTF_8).contains("committed text"),
                "a version slot must ignore later edits to the working file");
    }

    @Test
    @DisplayName("a version slot without an open document fails with a clear message")
    void versionSlotWithoutDocumentFails() {
        DocumentSourceResolver resolver = new DocumentSourceResolver(null, null);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(new ComparisonSlot.VersionSlot("v1", "deadbeef")));

        assertTrue(thrown.getMessage().contains("No document is open"),
                "got: " + thrown.getMessage());
    }

    @Test
    @DisplayName("reading is eager, so a later edit does not change an already-resolved source")
    void bytesAreCapturedAtResolveTime() throws Exception {
        Path file = Files.writeString(temporaryDirectory.resolve("live.md"), "original");
        DocumentSourceResolver resolver = new DocumentSourceResolver(null, null);

        DocumentSource source = resolver.resolve(
                new ComparisonSlot.FileSlot("live.md", file, DocumentFormat.MARKDOWN));
        Files.writeString(file, "changed afterwards");

        assertTrue(new String(source.bytes(), StandardCharsets.UTF_8).contains("original"),
                "the source holds a copy; editing the file later must not rewrite history");
    }

    private Path createRepositoryWithOneVersion(String content) throws Exception {
        Path folderPath = temporaryDirectory.resolve("doc-" + Math.abs(content.hashCode()));
        Files.createDirectories(folderPath);
        try (Git git = Git.init().setDirectory(folderPath.toFile())
                .setInitialBranch("main").call()) {
            // repository created; commit below
        }
        Files.write(folderPath.resolve("notes.md"), content.getBytes(StandardCharsets.UTF_8));
        new VersionRepository(folderPath).snapshot("notes", "notes-snapshot-2026-11-11");
        return folderPath;
    }
}
