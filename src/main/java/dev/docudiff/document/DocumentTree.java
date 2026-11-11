package dev.docudiff.document;

import java.util.List;

/**
 * A whole document reduced to ordered blocks. Section rectangles (header, body, footer) are kept
 * as a sequence rather than flattened, because the diff must not confuse a header paragraph with
 * a body paragraph that happens to hold the same text.
 */
public record DocumentTree(List<DocumentSection> sections) {

    public DocumentTree {
        sections = List.copyOf(sections);
    }

    /** Every block in reading order, section boundaries discarded. */
    public List<Block> allBlocks() {
        return sections.stream()
                .flatMap(section -> section.blocks().stream())
                .toList();
    }
}
