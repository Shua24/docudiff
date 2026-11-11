package dev.docudiff.versioning;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/**
 * Builds the fork-only merge commit: the source branch's tree, with both heads as parents.
 *
 * <p>Do NOT replace this with {@code git.merge().include(...)}. Measured behaviour of that call on
 * a conflicting document: it returns {@code MergeStatus.Conflicting} without throwing, writes
 * {@code <<<<<<< HEAD … ======= … >>>>>>>} into the working file, and leaves the branch ref
 * unchanged. The user would find their document corrupted after a "successful" merge.
 *
 * <p>Kept out of {@link VersionRepository} so that class stays within the project's file-length
 * limit without dropping any of its documented behaviour.
 */
final class ForkMerger {

    private ForkMerger() {
    }

    /**
     * Writes the merge commit and moves {@code targetBranch} onto it.
     *
     * @return the id of the new merge commit
     */
    static ObjectId merge(
            Repository repository, String targetBranch, String sourceBranch, PersonIdent identity)
            throws IOException {
        ObjectId targetId = repository.resolve("refs/heads/" + targetBranch);
        ObjectId sourceId = repository.resolve("refs/heads/" + sourceBranch);
        if (targetId == null || sourceId == null) {
            throw new IOException("Unknown branch: "
                    + (targetId == null ? targetBranch : sourceBranch));
        }
        try (RevWalk walk = new RevWalk(repository);
             ObjectInserter inserter = repository.newObjectInserter()) {
            RevCommit targetCommit = walk.parseCommit(targetId);
            RevCommit sourceCommit = walk.parseCommit(sourceId);

            CommitBuilder builder = new CommitBuilder();
            builder.setTreeId(sourceCommit.getTree().getId());
            builder.setParentIds(targetCommit, sourceCommit);
            builder.setAuthor(identity);
            builder.setCommitter(identity);
            builder.setMessage("Merge " + sourceBranch + " into " + targetBranch);
            ObjectId mergeCommitId = inserter.insert(builder);
            inserter.flush();

            RefUpdate refUpdate = repository.updateRef("refs/heads/" + targetBranch);
            refUpdate.setNewObjectId(mergeCommitId);
            refUpdate.setForceUpdate(true);
            refUpdate.update();
            return mergeCommitId;
        }
    }
}
