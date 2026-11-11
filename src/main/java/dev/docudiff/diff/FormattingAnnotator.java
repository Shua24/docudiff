package dev.docudiff.diff;

import dev.docudiff.document.Block;
import dev.docudiff.document.InlineSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns a pair of aligned blocks plus their formatting into the human annotations shown under a
 * block: "bold applied", "font changed to Liberation Sans", "size changed to 14".
 *
 * <p>Formatting is reported as an annotation, never as inline word marks, because a bold-only edit
 * has no word-level text change to point at. An unknown value (null) means "inherited from the
 * style"; a transition from null to a value is therefore a real, reportable change.
 */
public final class FormattingAnnotator {

    public List<String> annotationsFor(Block left, Block right) {
        if (left == null || right == null) {
            return List.of();
        }
        List<InlineSpan> leftSpans = spansOf(left);
        List<InlineSpan> rightSpans = spansOf(right);
        if (leftSpans.isEmpty() || rightSpans.isEmpty()) {
            return List.of();
        }
        List<String> annotations = new ArrayList<>();
        int spanCount = Math.min(leftSpans.size(), rightSpans.size());
        for (int index = 0; index < spanCount; index++) {
            annotations.addAll(annotationsFor(leftSpans.get(index), rightSpans.get(index)));
        }
        return annotations;
    }

    private List<String> annotationsFor(InlineSpan left, InlineSpan right) {
        List<String> annotations = new ArrayList<>();
        addToggle(annotations, "bold", left.bold(), right.bold());
        addToggle(annotations, "italic", left.italic(), right.italic());
        addToggle(annotations, "underline", left.underline(), right.underline());

        if (!Objects.equals(left.fontFamily(), right.fontFamily()) && right.fontFamily() != null) {
            annotations.add("font changed to " + right.fontFamily());
        }
        if (!Objects.equals(left.fontSize(), right.fontSize()) && right.fontSize() != null) {
            String sizeText = formatSize(right.fontSize());
            annotations.add("size changed to " + sizeText);
        }
        return annotations;
    }

    private void addToggle(
            List<String> annotations, String attributeName, Boolean leftValue, Boolean rightValue) {
        if (Objects.equals(leftValue, rightValue)) {
            return;
        }
        if (Boolean.TRUE.equals(rightValue)) {
            annotations.add(attributeName + " applied");
            return;
        }
        if (Boolean.TRUE.equals(leftValue)) {
            annotations.add(attributeName + " removed");
        }
    }

    private String formatSize(Double fontSize) {
        if (fontSize == null) {
            return "default";
        }
        if (fontSize == Math.rint(fontSize)) {
            return String.valueOf(fontSize.intValue());
        }
        return String.valueOf(fontSize);
    }

    private List<InlineSpan> spansOf(Block block) {
        if (block instanceof Block.Paragraph paragraph) {
            return paragraph.spans();
        }
        if (block instanceof Block.Heading heading) {
            return heading.spans();
        }
        return List.of();
    }
}
