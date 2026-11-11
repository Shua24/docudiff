package dev.docudiff.document;

import java.util.List;

/**
 * One named region of a document: the body, a header, a footer. The name is the section's
 * identity for diffing, so "header 1" and "body" never cross-match.
 *
 * @param name   human-facing section name, for example {@code Body} or {@code Header 1}
 * @param blocks the section's blocks in reading order
 */
public record DocumentSection(String name, List<Block> blocks) {

    public DocumentSection {
        blocks = List.copyOf(blocks);
    }
}
