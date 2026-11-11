package dev.docudiff.diff;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks word-level insertions and deletions inside a paragraph whose text changed.
 *
 * Words are compared with exact equality — this runs only on pairs the paragraph aligner already
 * decided are the same paragraph, so plain LCS is the right tool here and no similarity heuristic
 * is needed.
 */
public final class WordDiffer {

    public List<WordMark> diff(String leftText, String rightText) {
        List<String> leftWords = wordsOf(leftText);
        List<String> rightWords = wordsOf(rightText);
        Patch<String> patch = DiffUtils.diff(leftWords, rightWords);

        List<AbstractDelta<String>> deltas = new ArrayList<>(patch.getDeltas());
        deltas.sort(java.util.Comparator.comparingInt(
                delta -> delta.getSource().getPosition()));

        List<WordMark> marks = new ArrayList<>();
        int leftCursor = 0;
        int rightCursor = 0;

        for (AbstractDelta<String> delta : deltas) {
            int deltaLeftStart = delta.getSource().getPosition();
            int deltaRightStart = delta.getTarget().getPosition();

            for (int offset = 0; offset < deltaLeftStart - leftCursor; offset++) {
                marks.add(new WordMark(leftWords.get(leftCursor + offset), WordMarkKind.EQUAL));
            }

            switch (delta.getType()) {
                case DELETE -> delta.getSource().getLines().forEach(
                        word -> marks.add(new WordMark(word, WordMarkKind.REMOVED)));
                case INSERT -> delta.getTarget().getLines().forEach(
                        word -> marks.add(new WordMark(word, WordMarkKind.ADDED)));
                case CHANGE -> {
                    delta.getSource().getLines().forEach(
                            word -> marks.add(new WordMark(word, WordMarkKind.REMOVED)));
                    delta.getTarget().getLines().forEach(
                            word -> marks.add(new WordMark(word, WordMarkKind.ADDED)));
                }
                case EQUAL -> delta.getSource().getLines().forEach(
                        word -> marks.add(new WordMark(word, WordMarkKind.EQUAL)));
            }

            leftCursor = deltaLeftStart + delta.getSource().size();
            rightCursor = deltaRightStart + delta.getTarget().size();
        }

        while (leftCursor < leftWords.size() && rightCursor < rightWords.size()) {
            marks.add(new WordMark(leftWords.get(leftCursor), WordMarkKind.EQUAL));
            leftCursor++;
            rightCursor++;
        }
        while (leftCursor < leftWords.size()) {
            marks.add(new WordMark(leftWords.get(leftCursor), WordMarkKind.REMOVED));
            leftCursor++;
        }
        while (rightCursor < rightWords.size()) {
            marks.add(new WordMark(rightWords.get(rightCursor), WordMarkKind.ADDED));
            rightCursor++;
        }
        return marks;
    }

    private List<String> wordsOf(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return List.of(trimmed.split("\\s+"));
    }
}
