package dev.docudiff.ui;

import dev.docudiff.library.ImportService;
import dev.docudiff.library.LibraryRoot;
import dev.docudiff.versioning.VersionEntry;
import dev.docudiff.versioning.VersionRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Not part of the app. Builds a demo library through the real import and snapshot path, so the
 * capture harness sees exactly what a user would have after importing and editing a document.
 *
 * <p>Run with -Duser.home pointing at a scratch directory. With {@code -Ddemo.extras=true} it also
 * adds a third version, marks the middle one deleted, and imports a document with a footnote —
 * the three states the v1 UI work needs to be looked at.
 */
public final class DemoLibraryBuilder {

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]);
        LibraryRoot libraryRoot = LibraryRoot.defaultRoot();
        System.out.println("building demo library at " + libraryRoot.rootPath());

        if (!Files.exists(source)) {
            // A caller that only wants a demo of the new states should not have to bring their own
            // document; the builder writes one with a paragraph worth rewording.
            Files.createDirectories(source.getParent() == null
                    ? Path.of(".") : source.getParent());
            Files.write(source, sampleDocument());
            System.out.println("wrote a sample document at " + source);
        }

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

        if (Boolean.getBoolean("demo.extras")) {
            addThirdVersionAndDeletedOne(repository, workingFile);
            importFootnoteDocument(libraryRoot);
        }

        System.out.println("versions: " + repository.listVersions().size());
        for (VersionEntry version : repository.listVersions()) {
            System.out.println("  " + version.name() + " deleted=" + version.deleted());
        }
    }

    /**
     * A third snapshot, then the middle one marked deleted. The middle version is not a branch
     * head, so with "Show deleted" off the list should show two versions out of three.
     */
    private static void addThirdVersionAndDeletedOne(
            VersionRepository repository, Path workingFile) throws Exception {
        // Reword in place: applying reword() again would append the same new paragraph a second
        // time, and a report that shows one addition twice reads as a bug in the report.
        Files.write(workingFile, DocxEditor.changeWord(
                Files.readAllBytes(workingFile), "leaps", "sprints"));
        repository.snapshot("quarterly-report",
                "quarterly-report-snapshot-" + LocalDate.now());
        VersionEntry middle = repository.listVersions().get(1);
        repository.deleteSnapshot(middle.commitId());
        System.out.println("marked deleted: " + middle.name());
    }

    /** A document with one plain paragraph and one bold run, so a diff has something to show. */
    private static byte[] sampleDocument() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XWPFParagraph first = document.createParagraph();
            first.createRun().setText("The quick brown fox jumps over the lazy dog.");
            XWPFParagraph second = document.createParagraph();
            XWPFRun bold = second.createRun();
            bold.setText("Revenue grew this quarter.");
            bold.setBold(true);
            document.write(buffer);
            return buffer.toByteArray();
        }
    }

    /** A document whose only unsupported construct is a footnote, to exercise the notice. */
    private static void importFootnoteDocument(LibraryRoot libraryRoot) throws Exception {
        Path sourceDirectory = Files.createTempDirectory("docudiff-demo");
        Path temporary = sourceDirectory.resolve("annotated.docx");
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("This paragraph carries a footnote, which Docudiff does not compare.");
            document.createFootnote();
            document.write(buffer);
            Files.write(temporary, buffer.toByteArray());
        }
        Path folder = new ImportService(libraryRoot).importFile(temporary);
        Files.deleteIfExists(temporary);
        Files.deleteIfExists(sourceDirectory);

        // A second version, so the Diff tab has two sides and the notice appears above a real
        // comparison rather than above "pick two documents".
        VersionRepository repository = new VersionRepository(folder);
        Path workingFile = folder.resolve("annotated.docx");
        Files.write(workingFile, DocxEditor.reword(Files.readAllBytes(workingFile)));
        String snapshotName = new dev.docudiff.versioning.SnapshotNameGenerator().next(
                "annotated", LocalDate.now(), repository.existingSnapshotNames());
        repository.snapshot("annotated", snapshotName);
        System.out.println("imported footnote document into " + folder);
    }
}
