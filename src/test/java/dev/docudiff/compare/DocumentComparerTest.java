package dev.docudiff.compare;

import dev.docudiff.versioning.DocumentFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The comparison engine must be a pure function over two documents: no repository, no version,
 * no working file. These tests construct sources from bytes only, which is the whole point of the
 * refactor — if a test here needed a git repo, the weld would still be there.
 */
class DocumentComparerTest {

    @Test
    @DisplayName("two identical markdown sources compare as unchanged")
    void identicalSourcesAreUnchanged() {
        DocumentComparer comparer = new DocumentComparer();
        DocumentSource left = DocumentSource.ofBytes("before", "# Title\n\nBody text.\n",
                DocumentFormat.MARKDOWN);
        DocumentSource right = DocumentSource.ofBytes("after", "# Title\n\nBody text.\n",
                DocumentFormat.MARKDOWN);

        Comparison comparison = comparer.compare(left, right);

        assertEquals(0, comparison.changedCount());
        assertTrue(comparison.hasContent());
    }

    @Test
    @DisplayName("a changed markdown line reports one changed block")
    void changedLineReportsOneBlock() {
        DocumentComparer comparer = new DocumentComparer();
        DocumentSource left = DocumentSource.ofBytes("before", "# Title\n\nRevenue grew.\n",
                DocumentFormat.MARKDOWN);
        DocumentSource right = DocumentSource.ofBytes("after",
                "# Title\n\nRevenue grew this quarter.\n", DocumentFormat.MARKDOWN);

        Comparison comparison = comparer.compare(left, right);

        assertTrue(comparison.changedCount() >= 1,
                "the reworded paragraph must register as a change");
    }

    @Test
    @DisplayName("cross-format comparison is refused, naming both formats")
    void crossFormatIsRefused() {
        DocumentComparer comparer = new DocumentComparer();
        DocumentSource left = DocumentSource.ofBytes("report.docx", "x", DocumentFormat.DOCX);
        DocumentSource right = DocumentSource.ofBytes("notes.md", "x", DocumentFormat.MARKDOWN);

        UnsupportedComparisonException thrown = assertThrows(
                UnsupportedComparisonException.class,
                () -> comparer.compare(left, right));

        assertTrue(thrown.getMessage().contains("DOCX"),
                "the refusal must name the left format, got: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("MARKDOWN"),
                "the refusal must name the right format, got: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a label is carried through so the UI can say what it is showing")
    void labelsAreCarriedThrough() {
        DocumentComparer comparer = new DocumentComparer();
        DocumentSource left = DocumentSource.ofBytes(
                "quarterly-report-snapshot-2026-11-10", "# T\n\nA\n", DocumentFormat.MARKDOWN);
        DocumentSource right = DocumentSource.ofBytes(
                "alice-returned.docx.md", "# T\n\nB\n", DocumentFormat.MARKDOWN);

        Comparison comparison = comparer.compare(left, right);

        assertEquals("quarterly-report-snapshot-2026-11-10", comparison.leftLabel());
        assertEquals("alice-returned.docx.md", comparison.rightLabel());
    }

    @Test
    @DisplayName("a spreadsheet comparison carries per-sheet diffs, not block diffs")
    void spreadsheetComparisonCarriesSheets() throws Exception {
        DocumentComparer comparer = new DocumentComparer();
        DocumentSource left = DocumentSource.ofBytes("a.xlsx",
                SpreadsheetFixture.workbook(List.of("alpha", "beta")), DocumentFormat.XLSX);
        DocumentSource right = DocumentSource.ofBytes("b.xlsx",
                SpreadsheetFixture.workbook(List.of("alpha", "beta", "gamma")), DocumentFormat.XLSX);

        Comparison comparison = comparer.compare(left, right);

        assertEquals(null, comparison.documentDiff(),
                "a spreadsheet must not produce a block diff");
        assertTrue(comparison.sheetDiffs() != null, "a spreadsheet must produce sheet diffs");
        assertEquals(1, comparison.changedCount());
        assertEquals("Sheet1", comparison.sheetDiffs().get(0).name());
    }
}
