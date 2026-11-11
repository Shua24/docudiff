package dev.docudiff.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextSearchTest {

    @Test
    @DisplayName("an empty query matches nothing")
    void emptyQueryMatchesNothing() {
        assertEquals(List.of(), TextSearch.find("some text", ""));
        assertEquals(List.of(), TextSearch.find("some text", "   "));
    }

    @Test
    @DisplayName("a substring match reports its offset and length")
    void substringMatchReportsOffset() {
        List<TextSearch.Match> matches = TextSearch.find("the budget line", "budget");

        assertEquals(1, matches.size());
        assertEquals(4, matches.get(0).start());
        assertEquals(6, matches.get(0).length());
    }

    @Test
    @DisplayName("matching is case-insensitive")
    void matchingIsCaseInsensitive() {
        List<TextSearch.Match> matches = TextSearch.find("The BUDGET line", "budget");

        assertEquals(1, matches.size());
        assertEquals(4, matches.get(0).start());
    }

    @Test
    @DisplayName("every occurrence is reported, not just the first")
    void everyOccurrenceIsReported() {
        List<TextSearch.Match> matches = TextSearch.find("budget and budget and budget", "budget");

        assertEquals(3, matches.size());
        assertEquals(0, matches.get(0).start());
        assertEquals(11, matches.get(1).start());
        assertEquals(22, matches.get(2).start());
    }

    @Test
    @DisplayName("overlapping occurrences are not double-counted")
    void overlappingOccurrencesAreNotDoubleCounted() {
        List<TextSearch.Match> matches = TextSearch.find("aaaa", "aa");

        assertEquals(2, matches.size(), "advance past each match, do not rescan inside it");
        assertEquals(0, matches.get(0).start());
        assertEquals(2, matches.get(1).start());
    }

    @Test
    @DisplayName("a query longer than the text matches nothing")
    void queryLongerThanTextMatchesNothing() {
        assertEquals(List.of(), TextSearch.find("short", "much longer query"));
    }

    @Test
    @DisplayName("null text is treated as empty rather than throwing")
    void nullTextIsSafe() {
        assertEquals(List.of(), TextSearch.find(null, "anything"));
    }

    @Test
    @DisplayName("the query is trimmed, so a trailing space does not break the match")
    void queryIsTrimmed() {
        List<TextSearch.Match> matches = TextSearch.find("the budget line", "  budget  ");

        assertEquals(1, matches.size());
        assertTrue(matches.get(0).start() == 4);
    }
}
