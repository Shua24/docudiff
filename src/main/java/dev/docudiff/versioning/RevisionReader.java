package dev.docudiff.versioning;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only traversal helpers over a repository: head commit, file bytes at a commit, tracked
 * file names.
 *
 * <p>Split out of {@link VersionRepository} purely to keep both classes readable; these are the
 * only places that touch JGit's tree walking, which is the fiddly part.
 */
final class RevisionReader {

    private RevisionReader() {
    }

    static RevCommit headCommit(Repository repository) throws IOException {
        ObjectId headId = repository.resolve(org.eclipse.jgit.lib.Constants.HEAD);
        if (headId == null) {
            return null;
        }
        try (RevWalk walk = new RevWalk(repository)) {
            return walk.parseCommit(headId);
        }
    }

    /** The bytes of {@code fileName} at {@code commit}, or null when the commit lacks the file. */
    static byte[] bytesAt(Repository repository, RevCommit commit, String fileName)
            throws IOException {
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, fileName, commit.getTree())) {
            if (treeWalk == null) {
                return null;
            }
            ObjectId blobId = treeWalk.getObjectId(0);
            return repository.open(blobId).getBytes();
        }
    }

    /** Every file path tracked at HEAD, used to check each working file before a branch switch. */
    static List<String> trackedFileNames(Repository repository) throws IOException {
        List<String> fileNames = new ArrayList<>();
        RevCommit headCommit = headCommit(repository);
        if (headCommit == null) {
            return fileNames;
        }
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(headCommit.getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                fileNames.add(treeWalk.getPathString());
            }
        }
        return fileNames;
    }
}
