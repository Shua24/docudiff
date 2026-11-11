package dev.docudiff.versioning;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the user-visible version name for a snapshot.
 *
 * The name is {@code <documentName>-snapshot-<yyyy-MM-dd>}; a second snapshot on the same
 * date gets {@code -2}, then {@code -3}, and so on. A gap in the sequence is filled before
 * extending it, so deleting nothing (history is append-only) still yields a stable rule.
 */
public final class SnapshotNameGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * @param documentName    the document name without its file extension
     * @param snapshotDate    the local date the snapshot is taken on
     * @param existingNames   every snapshot name already present in the repository
     * @return a name not present in {@code existingNames}
     */
    public String next(String documentName, LocalDate snapshotDate, List<String> existingNames) {
        String baseName = documentName + "-snapshot-" + DATE_FORMAT.format(snapshotDate);
        Set<String> takenNames = new HashSet<>(existingNames);
        if (!takenNames.contains(baseName)) {
            return baseName;
        }
        int suffix = 2;
        while (takenNames.contains(baseName + "-" + suffix)) {
            suffix++;
        }
        String suffixedName = baseName + "-" + suffix;
        return suffixedName;
    }
}
