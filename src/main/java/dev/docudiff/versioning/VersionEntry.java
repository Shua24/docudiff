package dev.docudiff.versioning;

/**
 * One version in the list the user sees.
 *
 * @param commitId the full commit id
 * @param name     the snapshot name, which is the commit message
 * @param timestamp milliseconds since the epoch, when the snapshot was taken
 */
public record VersionEntry(String commitId, String name, long timestamp) {
}
