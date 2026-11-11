package dev.docudiff.compare;

import dev.docudiff.diff.DocumentDiff;
import dev.docudiff.diff.DocumentDiffer;
import dev.docudiff.diff.SheetDiff;
import dev.docudiff.diff.WorkbookDiffer;
import dev.docudiff.document.DocumentTree;
import dev.docudiff.document.WorkbookModel;
import dev.docudiff.extract.DocxExtractor;
import dev.docudiff.extract.MarkdownExtractor;
import dev.docudiff.extract.XlsxExtractor;
import dev.docudiff.versioning.DocumentFormat;

import java.util.List;

/**
 * Compares any two documents, whatever they came from.
 *
 * <p>This is the whole comparison surface: it takes two {@link DocumentSource}s and returns a
 * {@link Comparison}. It has no repository, no version and no working file, so the same call
 * serves version-against-version, version-against-returned-file, file-against-file, and
 * working-file-against-version. Callers fetch bytes; this decides what changed.
 */
public final class DocumentComparer {

    public Comparison compare(DocumentSource left, DocumentSource right) {
        if (left.format() != right.format()) {
            throw new UnsupportedComparisonException(left.format(), right.format());
        }
        try {
            if (left.format() == DocumentFormat.XLSX) {
                return compareSpreadsheets(left, right);
            }
            return compareDocuments(left, right);
        } catch (UnsupportedComparisonException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ComparisonFailedException(
                    "Could not compare \"" + left.label() + "\" with \""
                            + right.label() + "\": " + exception.getMessage(), exception);
        }
    }

    private Comparison compareDocuments(DocumentSource left, DocumentSource right)
            throws Exception {
        DocumentTree leftTree = treeOf(left);
        DocumentTree rightTree = treeOf(right);
        DocumentDiff documentDiff = new DocumentDiffer().diff(leftTree, rightTree);
        return new Comparison(left.label(), right.label(), documentDiff, null);
    }

    private Comparison compareSpreadsheets(DocumentSource left, DocumentSource right)
            throws Exception {
        WorkbookModel leftWorkbook = new XlsxExtractor().extract(left.bytes());
        WorkbookModel rightWorkbook = new XlsxExtractor().extract(right.bytes());
        List<SheetDiff> sheetDiffs = new WorkbookDiffer().diff(leftWorkbook, rightWorkbook);
        return new Comparison(left.label(), right.label(), null, sheetDiffs);
    }

    private DocumentTree treeOf(DocumentSource source) throws Exception {
        if (source.format() == DocumentFormat.DOCX) {
            return new DocxExtractor().extract(source.bytes());
        }
        return new MarkdownExtractor().extract(
                new String(source.bytes(), java.nio.charset.StandardCharsets.UTF_8));
    }
}
