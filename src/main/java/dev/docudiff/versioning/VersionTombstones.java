package dev.docudiff.versioning;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Marks versions as deleted without rewriting history.
 *
 * <p>A commit in the middle of a history cannot be removed without rebuilding every commit after
 * it, which would rewrite the document's whole past. Instead, a deleted version gets a marker ref
 * of its own — {@code refs/docudiff/deleted/<commit-id>} — and the version list filters it out.
 * Three consequences, all deliberate:
 *
 * <ul>
 *   <li>The commit stays reachable, so it survives garbage collection and travels with a clone or
 *       a backup. A deletion does not depend on this machine's reflog.</li>
 *   <li>No new commit is created, so recording a deletion never appears in the version list as a
 *       snapshot (a committed metadata file would — its own commit would be a version).</li>
 *   <li>Restoring is deleting the marker ref, so the operation is exactly reversible.</li>
 * </ul>
 */
public final class VersionTombstones {

    private static final String PREFIX = "refs/docudiff/deleted/";

    private VersionTombstones() {
    }

    /** Records that a version is deleted. Idempotent. */
    public static void markDeleted(Repository repository, String commitId) throws IOException {
        RefUpdate update = repository.updateRef(PREFIX + commitId);
        update.setNewObjectId(ObjectId.fromString(commitId));
        update.setForceUpdate(true);
        update.update();
    }

    /** Removes the deletion marker, so the version appears in the list again. */
    public static void restore(Repository repository, String commitId) throws IOException {
        Ref existing = repository.exactRef(PREFIX + commitId);
        if (existing == null) {
            return;
        }
        RefUpdate update = repository.updateRef(PREFIX + commitId);
        update.setForceUpdate(true);
        update.delete();
    }

    /** Every commit id currently marked deleted. */
    public static Set<String> deletedCommitIds(Repository repository) throws IOException {
        Set<String> commitIds = new HashSet<>();
        for (Ref ref : repository.getRefDatabase().getRefsByPrefix(PREFIX)) {
            commitIds.add(ref.getName().substring(PREFIX.length()));
        }
        return commitIds;
    }

    /** The commit ids every branch currently points at. */
    public static Set<String> branchHeadCommitIds(Repository repository) throws IOException {
        Set<String> commitIds = new HashSet<>();
        for (Ref ref : repository.getRefDatabase().getRefsByPrefix("refs/heads/")) {
            Ref peeled = repository.getRefDatabase().peel(ref);
            ObjectId objectId = peeled.getPeeledObjectId() != null
                    ? peeled.getPeeledObjectId()
                    : peeled.getObjectId();
            if (objectId != null) {
                commitIds.add(objectId.getName());
            }
        }
        return commitIds;
    }
}
