package dev.docudiff.document;

import java.util.List;

/**
 * A whole document reduced to ordered blocks. Section rectangles (header, body, footer) are kept
 * as a sequence rather than flattened, because the diff must not confuse a header paragraph with a
 * body paragraph that happens to hold the same text.
 *
 * <p>{@code unsupportedConstructs} names the parts of the file that were deliberately not read —
 * footnotes, endnotes, comments, nested tables. It exists so the app can say "this was not
 * compared" instead of reporting a confident "identical" on a document whose only change is a
 * comment. The strings are human-facing and already carry their counts, for example
 * {@code "footnotes (2)"}.
 *
 * @param sections             the document's named regions in reading order
 * @param unsupportedConstructs parts of the file that were not extracted, empty when none
 */
public record DocumentTree(List<DocumentSection> sections, List<String> unsupportedConstructs) {

    public DocumentTree {
        sections = List.copyOf(sections);
        unsupportedConstructs = List.copyOf(unsupportedConstructs);
    }

    /** A document with nothing unsupported in it: the markdown case, and most docx files. */
    public DocumentTree(List<DocumentSection> sections) {
        this(sections, List.of());
    }

    /** Every block in reading order, section boundaries discarded. */
    public List<Block> allBlocks() {
        return sections.stream()
                .flatMap(section -> section.blocks().stream())
                .toList();
    }
}
