package dev.docudiff.document;

/**
 * A piece of text plus the formatting that applies to it. Formatting values that the document
 * inherits rather than states are carried as unknown, never as "not formatted" — a paragraph
 * style change would otherwise report every run in the paragraph as reformatted.
 *
 * @param text        the visible characters
 * @param bold        true/false when stated by the run, null when inherited
 * @param italic      true/false when stated by the run, null when inherited
 * @param underline   true/false when stated by the run, null when inherited
 * @param fontFamily  the stated font family, null when inherited
 * @param fontSize    the stated font size in points, null when inherited
 */
public record InlineSpan(
        String text,
        Boolean bold,
        Boolean italic,
        Boolean underline,
        String fontFamily,
        Double fontSize) {

    public static InlineSpan plain(String text) {
        return new InlineSpan(text, null, null, null, null, null);
    }
}
