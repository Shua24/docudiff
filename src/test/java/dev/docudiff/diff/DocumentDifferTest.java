package dev.docudiff.diff;

import dev.docudiff.document.Block;
import dev.docudiff.document.DocumentSection;
import dev.docudiff.document.DocumentTree;
import dev.docudiff.document.InlineSpan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentDifferTest {

    @Test
    @DisplayName("a one-word edit produces one modified block with two word marks")
    void oneWordEditProducesMarks() {
        DocumentDiff documentDiff = new DocumentDiffer().diff(
                treeOf(paragraph("The quick brown fox jumps over the lazy dog.")),
                treeOf(paragraph("The quick brown fox leaps over the lazy dog.")));

        assertEquals(1, documentDiff.blockDiffs().size());
        BlockDiff blockDiff = documentDiff.blockDiffs().get(0);
        assertEquals(DiffStatus.MODIFIED, blockDiff.status());
        assertEquals(1, documentDiff.wordMarksOf(0).stream()
                .filter(mark -> mark.kind() == WordMarkKind.REMOVED)
                .count());
        assertEquals(1, documentDiff.wordMarksOf(0).stream()
                .filter(mark -> mark.kind() == WordMarkKind.ADDED)
                .count());
    }

    @Test
    @DisplayName("a bold-only change is modified with an annotation and no word marks")
    void boldOnlyChangeIsModifiedWithAnnotation() {
        InlineSpan plainSpan = new InlineSpan("bold part", null, null, null, null, null);
        InlineSpan boldSpan = new InlineSpan("bold part", Boolean.TRUE, null, null, null, null);

        DocumentDiff documentDiff = new DocumentDiffer().diff(
                treeOf(new Block.Paragraph(List.of(plainSpan))),
                treeOf(new Block.Paragraph(List.of(boldSpan))));

        assertEquals(1, documentDiff.blockDiffs().size());
        BlockDiff blockDiff = documentDiff.blockDiffs().get(0);
        assertEquals(DiffStatus.MODIFIED, blockDiff.status(),
                "a formatting-only change is still a change");
        assertEquals(0, documentDiff.wordMarksOf(0).stream()
                .filter(mark -> mark.kind() != WordMarkKind.EQUAL)
                .count(), "formatting must not be expressed as inline word marks");
        assertEquals(List.of("bold applied"), documentDiff.annotationsOf(0));
    }

    @Test
    @DisplayName("a font family change is annotated with both families")
    void fontChangeIsAnnotated() {
        InlineSpan serifSpan = new InlineSpan(
                "text", null, null, null, "Liberation Serif", null);
        InlineSpan sansSpan = new InlineSpan(
                "text", null, null, null, "Liberation Sans", null);

        DocumentDiff documentDiff = new DocumentDiffer().diff(
                treeOf(new Block.Paragraph(List.of(serifSpan))),
                treeOf(new Block.Paragraph(List.of(sansSpan))));

        assertEquals(List.of("font changed to Liberation Sans"), documentDiff.annotationsOf(0));
    }

    @Test
    @DisplayName("a size change is annotated with the new size")
    void sizeChangeIsAnnotated() {
        InlineSpan smallSpan = new InlineSpan("text", null, null, null, null, 11.0);
        InlineSpan largeSpan = new InlineSpan("text", null, null, null, null, 14.0);

        DocumentDiff documentDiff = new DocumentDiffer().diff(
                treeOf(new Block.Paragraph(List.of(smallSpan))),
                treeOf(new Block.Paragraph(List.of(largeSpan))));

        assertEquals(List.of("size changed to 14"), documentDiff.annotationsOf(0));
    }

    @Test
    @DisplayName("an unchanged block reports no annotations and no marks")
    void unchangedBlockReportsNothing() {
        DocumentDiff documentDiff = new DocumentDiffer().diff(
                treeOf(paragraph("same text")),
                treeOf(paragraph("same text")));

        assertEquals(DiffStatus.UNCHANGED, documentDiff.blockDiffs().get(0).status());
        assertEquals(List.of(), documentDiff.annotationsOf(0));
    }

    @Test
    @DisplayName("an added block reports ADDED with no annotations")
    void addedBlockReportsAdded() {
        DocumentDiff documentDiff = new DocumentDiffer().diff(
                treeOf(paragraph("alpha")),
                treeOf(paragraph("alpha"), paragraph("beta")));

        assertEquals(DiffStatus.ADDED, documentDiff.blockDiffs().get(1).status());
        assertEquals(List.of(), documentDiff.annotationsOf(1));
    }

    @Test
    @DisplayName("sections are diffed independently so a header never matches a body paragraph")
    void sectionsAreDiffedIndependently() {
        DocumentTree left = new DocumentTree(List.of(
                new DocumentSection("Header 1", List.of(paragraph("Same text"))),
                new DocumentSection("Body", List.of(paragraph("Same text")))));
        DocumentTree right = new DocumentTree(List.of(
                new DocumentSection("Header 1", List.of(paragraph("Same text"))),
                new DocumentSection("Body", List.of(paragraph("Changed text")))));

        DocumentDiff documentDiff = new DocumentDiffer().diff(left, right);

        assertEquals(2, documentDiff.sectionDiffs().size());
        assertTrue(documentDiff.sectionDiffs().stream()
                .anyMatch(sectionDiff -> sectionDiff.sectionName().equals("Header 1")
                        && sectionDiff.blockDiffs().stream()
                                .allMatch(blockDiff -> blockDiff.status() == DiffStatus.UNCHANGED)),
                "the header must report unchanged");
        assertTrue(documentDiff.sectionDiffs().stream()
                .anyMatch(sectionDiff -> sectionDiff.sectionName().equals("Body")
                        && sectionDiff.blockDiffs().stream()
                                .anyMatch(blockDiff -> blockDiff.status() != DiffStatus.UNCHANGED)),
                "the body must report a change");
    }

    private DocumentTree treeOf(Block... blocks) {
        return new DocumentTree(List.of(new DocumentSection("Body", List.of(blocks))));
    }

    private Block paragraph(String text) {
        return new Block.Paragraph(List.of(InlineSpan.plain(text)));
    }
}
