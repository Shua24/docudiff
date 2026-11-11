package dev.docudiff.diff;

import dev.docudiff.document.InlineSpan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordDifferTest {

    @Test
    @DisplayName("a one-word change marks exactly that word as removed and added")
    void oneWordChangeMarksThatWord() {
        List<WordMark> marks = new WordDiffer().diff(
                "The quick brown fox jumps over the lazy dog.",
                "The quick brown fox leaps over the lazy dog.");

        List<String> removed = marks.stream()
                .filter(mark -> mark.kind() == WordMarkKind.REMOVED)
                .map(WordMark::text)
                .toList();
        List<String> added = marks.stream()
                .filter(mark -> mark.kind() == WordMarkKind.ADDED)
                .map(WordMark::text)
                .toList();

        assertEquals(List.of("jumps"), removed);
        assertEquals(List.of("leaps"), added);
    }

    @Test
    @DisplayName("identical text produces no marks at all")
    void identicalTextProducesNoMarks() {
        List<WordMark> marks = new WordDiffer().diff("same text here", "same text here");

        assertEquals(0, marks.stream()
                .filter(mark -> mark.kind() != WordMarkKind.EQUAL)
                .count());
    }

    @Test
    @DisplayName("an inserted clause marks only the new words")
    void insertedClauseMarksOnlyNewWords() {
        // Sentences chosen so the shared prefix is word-identical: no punctuation edits, which
        // are their own word-level change by design (see the punctuation test below).
        List<WordMark> marks = new WordDiffer().diff(
                "Revenue grew this quarter",
                "Revenue grew this quarter driven by strong demand");

        List<String> added = marks.stream()
                .filter(mark -> mark.kind() == WordMarkKind.ADDED)
                .map(WordMark::text)
                .toList();

        assertEquals(List.of("driven", "by", "strong", "demand"), added);
    }

    @Test
    @DisplayName("a punctuation-only edit is reported as a word-level change")
    void punctuationEditIsReported() {
        // Deliberate: "quarter." to "quarter," is a real edit a reviewer may want to see.
        // Word's track changes shows it too. Tightening punctuation silently would hide it.
        List<WordMark> marks = new WordDiffer().diff(
                "Revenue grew this quarter.",
                "Revenue grew this quarter, driven by strong demand");

        List<String> removed = marks.stream()
                .filter(mark -> mark.kind() == WordMarkKind.REMOVED)
                .map(WordMark::text)
                .toList();

        assertEquals(List.of("quarter."), removed);
    }

    @Test
    @DisplayName("marks reassemble into the original texts")
    void marksReassembleIntoOriginalTexts() {
        String left = "The quick brown fox jumps over the lazy dog.";
        String right = "The quick brown fox leaps over the lazy dog.";

        List<WordMark> marks = new WordDiffer().diff(left, right);

        String rebuiltLeft = marks.stream()
                .filter(mark -> mark.kind() != WordMarkKind.ADDED)
                .map(WordMark::text)
                .reduce("", (accumulated, word) -> accumulated.isEmpty()
                        ? word : accumulated + " " + word);
        String rebuiltRight = marks.stream()
                .filter(mark -> mark.kind() != WordMarkKind.REMOVED)
                .map(WordMark::text)
                .reduce("", (accumulated, word) -> accumulated.isEmpty()
                        ? word : accumulated + " " + word);

        assertEquals(left, rebuiltLeft);
        assertEquals(right, rebuiltRight);
    }

    @Test
    @DisplayName("formatting-only changes produce no word marks")
    void formattingOnlyChangeProducesNoWordMarks() {
        List<WordMark> marks = new WordDiffer().diff("bold part", "bold part");

        assertEquals(0, marks.stream()
                .filter(mark -> mark.kind() != WordMarkKind.EQUAL)
                .count());
    }
}
