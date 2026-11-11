package dev.docudiff.ui;

import dev.docudiff.diff.DocumentDiff;
import dev.docudiff.diff.DocumentDiffer;
import dev.docudiff.diff.RowDiff;
import dev.docudiff.diff.SheetRowAligner;
import dev.docudiff.document.DocumentTree;
import dev.docudiff.document.WorkbookModel;
import dev.docudiff.extract.DocxExtractor;
import dev.docudiff.extract.MarkdownExtractor;
import dev.docudiff.extract.XlsxExtractor;
import dev.docudiff.library.DocumentFolder;
import dev.docudiff.versioning.DocumentFormat;
import dev.docudiff.versioning.VersionEntry;
import dev.docudiff.versioning.VersionRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Turns a document folder plus two versions into whatever the viewer panes need.
 *
 * <p>Kept apart from the window so the diff wiring can be read without wading through JavaFX
 * event handlers.
 */
final class ComparisonService {

    /** Either a document diff or spreadsheet row diffs; exactly one is non-null. */
    record Comparison(DocumentDiff documentDiff, List<RowDiff> rowDiffs, String sheetName) {
    }

    Comparison compare(
            DocumentFolder documentFolder,
            VersionRepository repository,
            VersionEntry leftVersion,
            VersionEntry rightVersion) throws Exception {
        DocumentFormat format = documentFolder.format();
        String fileName = documentFolder.workingFileName();
        byte[] leftBytes = repository.bytesOfVersion(fileName, leftVersion.commitId());
        byte[] rightBytes = repository.bytesOfVersion(fileName, rightVersion.commitId());

        if (format == DocumentFormat.XLSX) {
            WorkbookModel leftWorkbook = new XlsxExtractor().extract(leftBytes);
            WorkbookModel rightWorkbook = new XlsxExtractor().extract(rightBytes);
            dev.docudiff.document.SheetModel leftSheet = leftWorkbook.sheets().get(0);
            dev.docudiff.document.SheetModel rightSheet = rightWorkbook.sheets().get(0);
            List<RowDiff> rowDiffs = new SheetRowAligner().align(
                    leftSheet.rows(), rightSheet.rows());
            // Name the sheet from the newer version: that is the one the reader is moving toward.
            return new Comparison(null, rowDiffs, rightSheet.name());
        }

        DocumentTree leftTree = treeOf(format, leftBytes);
        DocumentTree rightTree = treeOf(format, rightBytes);
        DocumentDiff documentDiff = new DocumentDiffer().diff(leftTree, rightTree);
        return new Comparison(documentDiff, null, null);
    }

    private DocumentTree treeOf(DocumentFormat format, byte[] bytes) throws Exception {
        if (format == DocumentFormat.DOCX) {
            return new DocxExtractor().extract(bytes);
        }
        return new MarkdownExtractor().extract(new String(bytes));
    }

    /**
     * Writes a version's bytes to a scratch file so LibreOffice and the WebView have a path to
     * work with. The file lives outside the library so viewing never mutates the working copy.
     */
    Path scratchFileOf(VersionRepository repository, String fileName, String commitId)
            throws Exception {
        Path directory = Files.createTempDirectory("docudiff-view");
        Path file = directory.resolve(fileName);
        Files.write(file, repository.bytesOfVersion(fileName, commitId));
        return file;
    }
}
