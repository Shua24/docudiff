package dev.docudiff.ui;

import dev.docudiff.library.ImportService;
import dev.docudiff.library.LibraryRoot;
import dev.docudiff.versioning.VersionRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Not part of the app. Builds a demo library through the real import and snapshot path, so the
 * capture harness sees exactly what a user would have after importing and editing a document.
 *
 * <p>Run with -Duser.home pointing at a scratch directory.
 */
public final class DemoLibraryBuilder {

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]);
        LibraryRoot libraryRoot = LibraryRoot.defaultRoot();
        System.out.println("building demo library at " + libraryRoot.rootPath());

        Path folder = new ImportService(libraryRoot).importFile(source);
        System.out.println("imported into " + folder);

        VersionRepository repository = new VersionRepository(folder);
        String fileName = Files.list(folder)
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".docx"))
                .findFirst()
                .orElseThrow()
                .getFileName()
                .toString();

        // Second version: reword one paragraph and bold another, so the diff has both a word-level
        // change and a formatting-only change to show.
        Path workingFile = folder.resolve(fileName);
        byte[] edited = DocxEditor.reword(Files.readAllBytes(workingFile));
        Files.write(workingFile, edited);
        repository.snapshot("quarterly-report", "quarterly-report-snapshot-"
                + LocalDate.now().minusDays(1));

        System.out.println("versions: " + repository.listVersions().size());
        repository.listVersions().forEach(version ->
                System.out.println("  " + version.name()));
    }
}
