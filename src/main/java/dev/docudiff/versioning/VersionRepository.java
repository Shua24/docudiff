package dev.docudiff.versioning;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Owns the JGit repository for one document folder.
 *
 * <p>Two behaviours here are deliberate and must not be "simplified":
 *
 * <ul>
 *   <li>{@link #mergeForkOnly} builds the merge commit by hand. JGit's {@code MergeCommand}
 *       returns {@code MergeStatus.Conflicting} without throwing and writes
 *       {@code <<<<<<< HEAD} markers into the working file, which for a document manager is
 *       silent corruption of the user's copy.</li>
 *   <li>{@link #materialize} writes the chosen version over the working file. JGit has no
 *       worktree support, so there is exactly one working file per repository.</li>
 * </ul>
 */
public final class VersionRepository {

    private static final String AUTHOR_NAME = "Docudiff";
    private static final String AUTHOR_EMAIL = "docudiff@localhost";

    private final Path repositoryDirectory;
    private final Clock clock;

    public VersionRepository(Path repositoryDirectory) {
        this(repositoryDirectory, Clock.systemDefaultZone());
    }

    public VersionRepository(Path repositoryDirectory, Clock clock) {
        this.repositoryDirectory = repositoryDirectory;
        this.clock = clock;
    }

    public WorkingFileState probeWorkingFile(String fileName) throws IOException, GitAPIException {
        try (Git git = openGit()) {
            return WorkingCopy.probe(
                    git.getRepository(), repositoryDirectory.resolve(fileName));
        }
    }

    public String snapshot(String documentName, String snapshotName)
            throws IOException, GitAPIException {
        try (Git git = openGit()) {
            git.add().addFilepattern(".").call();
            RevCommit commit = git.commit()
                    .setMessage(snapshotName)
                    .setAuthor(identity())
                    .setCommitter(identity())
                    .call();
            return commit.getName();
        }
    }

    public List<VersionEntry> listVersions() throws IOException, GitAPIException {
        try (Git git = openGit()) {
            Repository repository = git.getRepository();
            java.util.Set<String> deletedCommitIds = VersionTombstones.deletedCommitIds(repository);
            List<VersionEntry> versions = new ArrayList<>();
            // A freshly initialised repository has no HEAD; git.log() throws NoHeadException
            // rather than returning nothing, so an empty document would crash the version list.
            Iterable<RevCommit> commits;
            try {
                commits = git.log().call();
            } catch (org.eclipse.jgit.api.errors.NoHeadException noHeadException) {
                return versions;
            }
            for (RevCommit commit : commits) {
                long timestamp = commit.getCommitTime() * 1000L;
                versions.add(new VersionEntry(commit.getName(), commit.getFullMessage(), timestamp,
                        deletedCommitIds.contains(commit.getName())));
            }
            return versions;
        }
    }

    /** The commit ids every branch points at, so a deleted branch head can still be listed. */
    public java.util.Set<String> branchHeadCommitIds() throws IOException {
        try (Git git = openGit()) {
            return VersionTombstones.branchHeadCommitIds(git.getRepository());
        }
    }

    /**
     * Marks a version deleted. The commit is kept — see {@link VersionTombstones} for why history
     * is not rewritten — and the version list stops showing it until it is restored.
     */
    public void deleteSnapshot(String commitId) throws IOException {
        try (Git git = openGit()) {
            VersionTombstones.markDeleted(git.getRepository(), commitId);
        }
    }

    /** Removes a version's deletion marker, so it appears in the version list again. */
    public void restoreSnapshot(String commitId) throws IOException {
        try (Git git = openGit()) {
            VersionTombstones.restore(git.getRepository(), commitId);
        }
    }

    public List<String> existingSnapshotNames() throws IOException, GitAPIException {
        List<String> names = listVersions().stream().map(VersionEntry::name).sorted().toList();
        return names;
    }

    public List<String> listBranches() throws IOException, GitAPIException {
        try (Git git = openGit()) {
            List<String> branchNames = new ArrayList<>();
            for (Ref ref : git.branchList().call()) {
                branchNames.add(Repository.shortenRefName(ref.getName()));
            }
            branchNames.sort(Comparator.naturalOrder());
            return branchNames;
        }
    }

    /** The branch currently checked out in this repository. */
    public String currentBranch() throws IOException {
        try (Git git = openGit()) {
            return git.getRepository().getBranch();
        }
    }

    public void createBranch(String branchName) throws IOException, GitAPIException {
        try (Git git = openGit()) {
            git.branchCreate().setName(branchName).call();
        }
    }

    public void checkoutBranch(String branchName) throws IOException, GitAPIException {
        try (Git git = openGit()) {
            String currentBranch = git.getRepository().getBranch();
            if (currentBranch.equals(branchName)) {
                return;
            }
            for (String fileName : RevisionReader.trackedFileNames(git.getRepository())) {
                if (probeWorkingFile(fileName) == WorkingFileState.MODIFIED) {
                    throw new IllegalStateException(
                            "The working file '" + fileName + "' is modified. Snapshot it or "
                                    + "discard the changes before switching branch.");
                }
            }
            git.checkout().setName(branchName).call();
        }
    }

    /**
     * Reads a version's bytes without touching the working file. This is what the renderer and
     * the diff engine use to look at an arbitrary version.
     */
    public byte[] bytesOfVersion(String fileName, String commitId) throws IOException {
        try (Git git = openGit()) {
            Repository repository = git.getRepository();
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(ObjectId.fromString(commitId));
                byte[] content = RevisionReader.bytesAt(repository, commit, fileName);
                if (content == null) {
                    throw new IOException("Version " + commitId + " has no file " + fileName);
                }
                return content;
            }
        }
    }

    /**
     * Writes a version over the working file, for "check out this version to edit".
     *
     * <p>The working file then matches that version, which is only {@link WorkingFileState#CLEAN}
     * when the version is the branch head. Checking out an older version deliberately leaves the
     * file {@code MODIFIED} relative to the head, so a following snapshot records an intentional
     * revert rather than silently discarding it. Use {@link #bytesOfVersion} to merely LOOK at a
     * version.
     */
    public void materialize(String fileName, String commitId) throws IOException, GitAPIException {
        byte[] content = bytesOfVersion(fileName, commitId);
        Files.write(repositoryDirectory.resolve(fileName), content);
    }

    /** A branch that is checked out cannot be deleted, which is git's own rule. */
    public void deleteBranch(String branchName) throws IOException, GitAPIException {
        try (Git git = openGit()) {
            String currentBranch = git.getRepository().getBranch();
            if (currentBranch.equals(branchName)) {
                throw new IllegalStateException(
                        "Cannot delete \"" + branchName + "\": it is the branch you are on. "
                                + "Switch to another branch first.");
            }
            if (!listBranches().contains(branchName)) {
                throw new IllegalStateException("There is no branch named \"" + branchName + "\".");
            }
            // Forced on purpose: the branch may hold versions that no other branch reaches. The
            // confirmation dialog says so, and the versions stay in the repository either way.
            git.branchDelete().setBranchNames(branchName).setForce(true).call();
        }
    }

    /**
     * Puts the working file back to the current branch head, throwing away external edits.
     *
     * <p>This is the only operation that destroys uncommitted content, so it is deliberately not
     * reachable from anywhere else. A following snapshot cannot recover the discarded bytes; the
     * last snapshot can.
     */
    public void discardWorkingChanges(String fileName) throws IOException, GitAPIException {
        try (Git git = openGit()) {
            WorkingCopy.restore(git.getRepository(), repositoryDirectory.resolve(fileName));
        }
    }

    /**
     * Merges by taking the source branch's tree wholesale and recording both heads as parents.
     *
     * <p>Do NOT replace this with {@code git.merge().include(...)}; see {@link ForkMerger} for the
     * measured corruption it causes in the working file.
     */
    public String mergeForkOnly(String targetBranch, String sourceBranch)
            throws IOException, GitAPIException {
        try (Git git = openGit()) {
            Repository repository = git.getRepository();
            ObjectId mergeCommitId =
                    ForkMerger.merge(repository, targetBranch, sourceBranch, identity());
            if (targetBranch.equals(repository.getBranch())) {
                git.reset()
                        .setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD)
                        .setRef("refs/heads/" + targetBranch)
                        .call();
            }
            return mergeCommitId.getName();
        }
    }

    public List<String> parentIdsOf(String commitId) throws IOException {
        try (Git git = openGit();
             RevWalk walk = new RevWalk(git.getRepository())) {
            RevCommit commit = walk.parseCommit(ObjectId.fromString(commitId));
            List<String> parentIds = new ArrayList<>();
            for (RevCommit parent : commit.getParents()) {
                parentIds.add(parent.getName());
            }
            return parentIds;
        }
    }

    public String treeIdOf(String commitId) throws IOException {
        try (Git git = openGit();
             RevWalk walk = new RevWalk(git.getRepository())) {
            RevCommit commit = walk.parseCommit(ObjectId.fromString(commitId));
            return commit.getTree().getId().getName();
        }
    }

    /**
     * Opens the repository with line-ending conversion pinned off.
     *
     * <p>JGit honours {@code core.autocrlf} from the system or global git config, and GitHub's
     * Windows runners set it to {@code true}. A hard reset then rewrites the working file with
     * CRLF, so the same document has different bytes on Windows than on Linux — and the user's
     * Markdown silently changes line endings after every checkout or merge. A document manager
     * must not rewrite the user's line endings, so the setting is forced off for every operation.
     */
    private Git openGit() throws IOException {
        Git git = Git.open(repositoryDirectory.toFile());
        git.getRepository().getConfig().setBoolean("core", null, "autocrlf", false);
        return git;
    }

    private PersonIdent identity() {
        return new PersonIdent(AUTHOR_NAME, AUTHOR_EMAIL, clock.instant(), clock.getZone());
    }
}
