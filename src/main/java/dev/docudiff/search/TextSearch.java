package dev.docudiff.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Case-insensitive substring search over a single piece of text.
 *
 * <p>Deliberately tiny and option-free: no regex, no case toggle, no whole-word mode. The diff
 * view calls this per text run and highlights what comes back, so the only thing that matters is
 * that offsets are exact and every occurrence is found.
 */
public final class TextSearch {

    private TextSearch() {
    }

    /**
     * One occurrence of the query inside a text.
     *
     * @param start  index of the first matching character
     * @param length number of characters matched
     */
    public record Match(int start, int length) {
        public int end() {
            return start + length;
        }
    }

    /**
     * Finds every non-overlapping occurrence of {@code query} in {@code text}.
     *
     * <p>Matching advances past each hit, so "aa" in "aaaa" yields two matches rather than three
     * overlapping ones — overlapping highlights would render as one long highlight anyway.
     */
    public static List<Match> find(String text, String query) {
        if (text == null || text.isEmpty() || query == null) {
            return List.of();
        }
        String needle = query.trim();
        if (needle.isEmpty()) {
            return List.of();
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        String loweredNeedle = needle.toLowerCase(Locale.ROOT);
        if (loweredNeedle.length() > haystack.length()) {
            return List.of();
        }

        List<Match> matches = new ArrayList<>();
        int cursor = 0;
        while (cursor <= haystack.length() - loweredNeedle.length()) {
            int found = haystack.indexOf(loweredNeedle, cursor);
            if (found < 0) {
                break;
            }
            matches.add(new Match(found, loweredNeedle.length()));
            cursor = found + loweredNeedle.length();
        }
        return matches;
    }

    /** True when the query occurs in the text at least once. */
    public static boolean contains(String text, String query) {
        return !find(text, query).isEmpty();
    }
}
