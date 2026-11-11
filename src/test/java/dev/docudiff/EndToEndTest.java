package dev.docudiff;

import dev.docudiff.diff.BlockDiff;
import dev.docudiff.diff.DiffStatus;
import dev.docudiff.diff.DocumentDiff;
import dev.docudiff.diff.DocumentDiffer;
import dev.docudiff.diff.WordMarkKind;
import dev.docudiff.document.DocumentTree;
import dev.docudiff.document.WorkbookModel;
import dev.docudiff.extract.DocxExtractor;
import dev.docudiff.extract.MarkdownExtractor;
import dev.docudiff.extract.XlsxExtractor;
import dev.docudiff.versioning.DocumentFormat;
import dev.docudiff.versioning.SnapshotNameGenerator;
import dev.docudiff.versioning.VersionEntry;
import dev.docudiff.versioning.VersionRepository;
import dev.docudiff.versioning.WorkingFileState;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One end-to-end pass over the whole MVP: import bytes, snapshot, branch, diff, merge.
 *
 * <p>This exercises the real modules with real generated DOCX and XLSX files. It never touches the
 * user's library folder — everything happens in a JUnit temporary directory.
 */
class EndToEndTest {

    @TempDir
    Path libraryRoot;

    private Path documentFolder;
    private VersionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        documentFolder = libraryRoot.resolve("report");
        Files.createDirectories(documentFolder);
        try (Git git = Git.init()
                .setDirectory(documentFolder.toFile())
                .setInitialBranch("main")
                .call()) {
            // Only here to create .git; the repository under test opens it itself.
        }
        repository = new VersionRepository(documentFolder);
    }

    @AfterEach
    void tearDown() {
        // Nothing to close: VersionRepository opens and closes per call.
    }

    @Test
    @DisplayName("docx: import, snapshot, branch, diff, merge")
    void docxEndToEnd() throws Exception {
        Path workingFile = documentFolder.resolve("report.docx");

        Files.write(workingFile, docxOf("The quick brown fox jumps over the lazy dog."));
        String firstCommit = snapshot("report", "report.docx");

        assertEquals(WorkingFileState.CLEAN, repository.probeWorkingFile("report.docx"));

        repository.createBranch("experiment");
        repository.checkoutBranch("experiment");

        Files.write(workingFile, docxOf("The quick brown fox leaps over the lazy dog."));
        String experimentCommit = snapshot("report", "report.docx");

        assertEquals(WorkingFileState.CLEAN, repository.probeWorkingFile("report.docx"));

        DocumentTree beforeTree = new DocxExtractor()
                .extract(repository.bytesOfVersion("report.docx", firstCommit));
        DocumentTree afterTree = new DocxExtractor()
                .extract(repository.bytesOfVersion("report.docx", experimentCommit));

        DocumentDiff documentDiff = new DocumentDiffer().diff(beforeTree, afterTree);

        assertEquals(1, documentDiff.blockDiffs().size());
        BlockDiff blockDiff = documentDiff.blockDiffs().get(0);
        assertEquals(DiffStatus.MODIFIED, blockDiff.status());
        assertEquals(List.of("jumps"), documentDiff.wordMarksOf(0).stream()
                .filter(mark -> mark.kind() == WordMarkKind.REMOVED)
                .map(mark -> mark.text())
                .toList());

        repository.checkoutBranch("main");
        String mergeCommit = repository.mergeForkOnly("main", "experiment");

        assertEquals(2, repository.parentIdsOf(mergeCommit).size(),
                "the fork merge must record both parents");
        assertEquals(repository.treeIdOf(experimentCommit), repository.treeIdOf(mergeCommit),
                "the merge must take the chosen branch's content");
        assertNotEquals(firstCommit, mergeCommit);
    }

    @Test
    @DisplayName("xlsx: an inserted row is one insertion, not a wall of changes")
    void xlsxEndToEnd() throws Exception {
        Path workingFile = documentFolder.resolve("report.xlsx");

        Files.write(workingFile, xlsxOf(List.of("alpha", "beta", "gamma")));
        String firstCommit = snapshot("report", "report.xlsx");

        Files.write(workingFile, xlsxOf(List.of("inserted", "alpha", "beta", "gamma")));
        String secondCommit = snapshot("report", "report.xlsx");

        WorkbookModel beforeModel = new XlsxExtractor()
                .extract(repository.bytesOfVersion("report.xlsx", firstCommit));
        WorkbookModel afterModel = new XlsxExtractor()
                .extract(repository.bytesOfVersion("report.xlsx", secondCommit));

        List<dev.docudiff.diff.RowDiff> rowDiffs = new dev.docudiff.diff.SheetRowAligner()
                .align(beforeModel.sheets().get(0).rows(), afterModel.sheets().get(0).rows());

        long addedCount = rowDiffs.stream()
                .filter(rowDiff -> rowDiff.status() == DiffStatus.ADDED)
                .count();
        long modifiedCount = rowDiffs.stream()
                .filter(rowDiff -> rowDiff.status() == DiffStatus.MODIFIED)
                .count();
        assertEquals(1, addedCount);
        assertEquals(0, modifiedCount);
    }

    @Test
    @DisplayName("markdown: a line change is reported by the line-level diff")
    void markdownEndToEnd() throws Exception {
        Path workingFile = documentFolder.resolve("notes.md");

        Files.writeString(workingFile, "# Title\n\nFirst paragraph.\n");
        String firstCommit = snapshot("notes", "notes.md");

        Files.writeString(workingFile, "# Title\n\nFirst paragraph, now longer.\n");
        String secondCommit = snapshot("notes", "notes.md");

        List<String> beforeLines = new MarkdownExtractor()
                .sourceLines(new String(repository.bytesOfVersion("notes.md", firstCommit)));
        List<String> afterLines = new MarkdownExtractor()
                .sourceLines(new String(repository.bytesOfVersion("notes.md", secondCommit)));

        com.github.difflib.patch.Patch<String> patch =
                com.github.difflib.DiffUtils.diff(beforeLines, afterLines);

        assertEquals(1, patch.getDeltas().size(),
                "one changed line must be one delta");

        DocumentTree beforeTree = new MarkdownExtractor()
                .extract(new String(repository.bytesOfVersion("notes.md", firstCommit)));
        DocumentTree afterTree = new MarkdownExtractor()
                .extract(new String(repository.bytesOfVersion("notes.md", secondCommit)));
        DocumentDiff documentDiff = new DocumentDiffer().diff(beforeTree, afterTree);
        assertTrue(documentDiff.changedBlockCount() >= 1,
                "the rendered tree diff must also report the change");
    }

    @Test
    @DisplayName("snapshot names follow the document-snapshot-date rule on a real repository")
    void snapshotNamesFollowTheRule() throws Exception {
        Path workingFile = documentFolder.resolve("report.docx");
        SnapshotNameGenerator generator = new SnapshotNameGenerator();
        LocalDate today = LocalDate.of(2026, 11, 11);

        Files.write(workingFile, docxOf("first"));
        String firstName = generator.next("report", today, repository.existingSnapshotNames());
        snapshot("report", "report.docx", firstName);

        Files.write(workingFile, docxOf("second"));
        String secondName = generator.next("report", today, repository.existingSnapshotNames());
        snapshot("report", "report.docx", secondName);

        assertEquals("report-snapshot-2026-11-11", firstName);
        assertEquals("report-snapshot-2026-11-11-2", secondName);
        assertEquals(2, repository.listVersions().size());
    }

    @Test
    @DisplayName("formats are recognised from the file extension")
    void formatsAreRecognised() {
        assertEquals(DocumentFormat.DOCX,
                DocumentFormat.fromFileName("report.docx").orElseThrow());
        assertEquals(DocumentFormat.XLSX,
                DocumentFormat.fromFileName("report.xlsx").orElseThrow());
        assertEquals(DocumentFormat.MARKDOWN,
                DocumentFormat.fromFileName("notes.md").orElseThrow());
        assertEquals(DocumentFormat.MARKDOWN,
                DocumentFormat.fromFileName("notes.markdown").orElseThrow());
        assertTrue(DocumentFormat.fromFileName("report.pdf").isEmpty());
    }

    private String snapshot(String documentName, String fileName) throws Exception {
        return snapshot(documentName, fileName, "snapshot");
    }

    private String snapshot(String documentName, String fileName, String snapshotName)
            throws Exception {
        String commitId = repository.snapshot(documentName, snapshotName);
        return commitId;
    }

    private byte[] docxOf(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            document.write(buffer);
            return buffer.toByteArray();
        }
    }

    private byte[] xlsxOf(List<String> values) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");
            for (int index = 0; index < values.size(); index++) {
                sheet.createRow(index).createCell(0).setCellValue(values.get(index));
            }
            workbook.write(buffer);
            return buffer.toByteArray();
        }
    }
}
