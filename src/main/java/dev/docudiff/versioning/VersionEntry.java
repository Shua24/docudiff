package dev.docudiff.versioning;

/**
 * One version in the list the user sees.
 *
 * @param commitId the full commit id
 * @param name     the snapshot name, which is the commit message
 * @param timestamp milliseconds since the epoch, when the snapshot was taken
 * @param deleted  true when the version is marked deleted; the commit itself still exists
 */
public record VersionEntry(String commitId, String name, long timestamp, boolean deleted) {

    /** A version that is not marked deleted, which is every version until someone removes it. */
    public VersionEntry(String commitId, String name, long timestamp) {
        this(commitId, name, timestamp, false);
    }
}
