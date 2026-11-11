package dev.docudiff.versioning;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
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
        Path workingPath = repositoryDirectory.resolve(fileName);
        if (!Files.exists(workingPath)) {
            return WorkingFileState.MISSING;
        }
        try (Git git = Git.open(repositoryDirectory.toFile())) {
            Repository repository = git.getRepository();
            RevCommit headCommit = RevisionReader.headCommit(repository);
            if (headCommit == null) {
                return WorkingFileState.MODIFIED;
            }
            byte[] committedBytes = RevisionReader.bytesAt(repository, headCommit, fileName);
            if (committedBytes == null) {
                return WorkingFileState.MODIFIED;
            }
            byte[] workingBytes = Files.readAllBytes(workingPath);
            boolean identical = java.util.Arrays.equals(committedBytes, workingBytes);
            return identical ? WorkingFileState.CLEAN : WorkingFileState.MODIFIED;
        }
    }

    public String snapshot(String documentName, String snapshotName)
            throws IOException, GitAPIException {
        try (Git git = Git.open(repositoryDirectory.toFile())) {
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
        try (Git git = Git.open(repositoryDirectory.toFile())) {
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
                versions.add(new VersionEntry(commit.getName(), commit.getFullMessage(), timestamp));
            }
            return versions;
        }
    }

    public List<String> existingSnapshotNames() throws IOException, GitAPIException {
        List<String> names = listVersions().stream().map(VersionEntry::name).sorted().toList();
        return names;
    }

    public List<String> listBranches() throws IOException, GitAPIException {
        try (Git git = Git.open(repositoryDirectory.toFile())) {
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
        try (Git git = Git.open(repositoryDirectory.toFile())) {
            return git.getRepository().getBranch();
        }
    }

    public void createBranch(String branchName) throws IOException, GitAPIException {
        try (Git git = Git.open(repositoryDirectory.toFile())) {
            git.branchCreate().setName(branchName).call();
        }
    }

    public void checkoutBranch(String branchName) throws IOException, GitAPIException {
        try (Git git = Git.open(repositoryDirectory.toFile())) {
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
        try (Git git = Git.open(repositoryDirectory.toFile())) {
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

    /**
     * Merges by taking the source branch's tree wholesale and recording both heads as parents.
     *
     * <p>Do NOT replace this with {@code git.merge().include(...)}. Measured behaviour of that
     * call on a conflicting document: returns {@code MergeStatus.Conflicting} without throwing,
     * writes {@code <<<<<<< HEAD … ======= … >>>>>>>} into the working file, and leaves the branch
     * ref unchanged. The user would find their document corrupted after a "successful" merge.
     */
    public String mergeForkOnly(String targetBranch, String sourceBranch)
            throws IOException, GitAPIException {
        try (Git git = Git.open(repositoryDirectory.toFile())) {
            Repository repository = git.getRepository();
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
                builder.setAuthor(identity());
                builder.setCommitter(identity());
                builder.setMessage("Merge " + sourceBranch + " into " + targetBranch);
                ObjectId mergeCommitId = inserter.insert(builder);
                inserter.flush();

                RefUpdate refUpdate = repository.updateRef("refs/heads/" + targetBranch);
                refUpdate.setNewObjectId(mergeCommitId);
                refUpdate.setForceUpdate(true);
                refUpdate.update();

                if (targetBranch.equals(repository.getBranch())) {
                    git.reset()
                            .setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD)
                            .setRef("refs/heads/" + targetBranch)
                            .call();
                }
                return mergeCommitId.getName();
            }
        }
    }

    public List<String> parentIdsOf(String commitId) throws IOException {
        try (Git git = Git.open(repositoryDirectory.toFile());
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
        try (Git git = Git.open(repositoryDirectory.toFile());
             RevWalk walk = new RevWalk(git.getRepository())) {
            RevCommit commit = walk.parseCommit(ObjectId.fromString(commitId));
            return commit.getTree().getId().getName();
        }
    }

    private PersonIdent identity() {
        return new PersonIdent(AUTHOR_NAME, AUTHOR_EMAIL, clock.instant(), clock.getZone());
    }
}
