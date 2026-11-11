package dev.docudiff.diff;

import java.util.List;

/**
 * The diff for one section, for example {@code Body} or {@code Header 1}.
 *
 * @param sectionName the section these blocks belong to
 * @param blockDiffs  the aligned blocks in reading order
 */
public record SectionDiff(String sectionName, List<BlockDiff> blockDiffs) {

    public SectionDiff {
        blockDiffs = List.copyOf(blockDiffs);
    }
}
