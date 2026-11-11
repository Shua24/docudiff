package dev.docudiff.diff;

import dev.docudiff.document.Block;
import dev.docudiff.document.DocumentSection;
import dev.docudiff.document.DocumentTree;
import dev.docudiff.document.InlineSpan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes the full document diff: section by section, aligning blocks, marking words inside
 * modified blocks, and annotating formatting changes.
 *
 * <p>Sections are diffed independently and matched by NAME. That is what stops a header paragraph
 * from pairing with a body paragraph that happens to hold identical text.
 */
public final class DocumentDiffer {

    private final ParagraphAligner paragraphAligner = new ParagraphAligner();
    private final WordDiffer wordDiffer = new WordDiffer();
    private final FormattingAnnotator formattingAnnotator = new FormattingAnnotator();

    public DocumentDiff diff(DocumentTree leftTree, DocumentTree rightTree) {
        List<SectionDiff> sectionDiffs = new ArrayList<>();
        List<BlockDiff> allBlockDiffs = new ArrayList<>();
        List<List<WordMark>> allWordMarks = new ArrayList<>();
        List<List<String>> allAnnotations = new ArrayList<>();

        List<String> sectionNames = sectionNamesOf(leftTree, rightTree);
        for (String sectionName : sectionNames) {
            DocumentSection leftSection = sectionOf(leftTree, sectionName);
            DocumentSection rightSection = sectionOf(rightTree, sectionName);
            List<Block> leftBlocks = leftSection == null ? List.of() : leftSection.blocks();
            List<Block> rightBlocks = rightSection == null ? List.of() : rightSection.blocks();

            List<BlockDiff> alignedBlocks = paragraphAligner.align(leftBlocks, rightBlocks);
            List<BlockDiff> blockDiffs = new ArrayList<>();
            for (BlockDiff alignedBlock : alignedBlocks) {
                blockDiffs.add(applyFormatting(alignedBlock));
            }
            sectionDiffs.add(new SectionDiff(sectionName, blockDiffs));

            for (BlockDiff blockDiff : blockDiffs) {
                allBlockDiffs.add(blockDiff);
                allWordMarks.add(wordMarksOf(blockDiff));
                allAnnotations.add(annotationsOf(blockDiff));
            }
        }

        DocumentDiff documentDiff =
                new DocumentDiff(sectionDiffs, allBlockDiffs, allWordMarks, allAnnotations);
        return documentDiff;
    }

    /*
     * The aligner compares TEXT, so a formatting-only edit comes back UNCHANGED. A formatting
     * change is still a change the user asked to see, so upgrade the block to MODIFIED when the
     * annotator has something to say.
     */
    private BlockDiff applyFormatting(BlockDiff blockDiff) {
        if (blockDiff.status() != DiffStatus.UNCHANGED) {
            return blockDiff;
        }
        List<String> annotations =
                formattingAnnotator.annotationsFor(blockDiff.left(), blockDiff.right());
        if (annotations.isEmpty()) {
            return blockDiff;
        }
        return new BlockDiff(blockDiff.left(), blockDiff.right(), DiffStatus.MODIFIED);
    }

    private List<WordMark> wordMarksOf(BlockDiff blockDiff) {
        if (blockDiff.status() != DiffStatus.MODIFIED) {
            return List.of();
        }
        String leftText = textOf(blockDiff.left());
        String rightText = textOf(blockDiff.right());
        if (leftText.equals(rightText)) {
            return List.of();
        }
        return wordDiffer.diff(leftText, rightText);
    }

    private List<String> annotationsOf(BlockDiff blockDiff) {
        if (blockDiff.status() != DiffStatus.MODIFIED) {
            return List.of();
        }
        List<String> annotations =
                formattingAnnotator.annotationsFor(blockDiff.left(), blockDiff.right());
        return annotations;
    }

    private String textOf(Block block) {
        if (block == null) {
            return "";
        }
        if (block instanceof Block.Paragraph paragraph) {
            return joinSpans(paragraph.spans());
        }
        if (block instanceof Block.Heading heading) {
            return joinSpans(heading.spans());
        }
        if (block instanceof Block.CodeBlock codeBlock) {
            return String.join("\n", codeBlock.lines());
        }
        return block.toString();
    }

    private String joinSpans(List<InlineSpan> spans) {
        StringBuilder builder = new StringBuilder();
        for (InlineSpan span : spans) {
            builder.append(span.text());
        }
        String text = builder.toString().trim();
        return text;
    }

    private List<String> sectionNamesOf(DocumentTree leftTree, DocumentTree rightTree) {
        Set<String> names = new LinkedHashSet<>();
        leftTree.sections().forEach(section -> names.add(section.name()));
        rightTree.sections().forEach(section -> names.add(section.name()));
        List<String> orderedNames = new ArrayList<>(names);
        return orderedNames;
    }

    private DocumentSection sectionOf(DocumentTree tree, String sectionName) {
        for (DocumentSection section : tree.sections()) {
            if (section.name().equals(sectionName)) {
                return section;
            }
        }
        return null;
    }
}
