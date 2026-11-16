package dev.docudiff.versioning;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Reads and restores the single working file of a document repository.
 *
 * <p>Split out of {@link VersionRepository} to keep that class within the project's file-length
 * limit. Callers always hand in a {@link Repository} obtained from {@code VersionRepository}, so
 * the {@code core.autocrlf} pinning that protects the working file's line endings still applies.
 */
final class WorkingCopy {

    private WorkingCopy() {
    }

    /** Compares the working file's bytes with the checked-out branch head. */
    static WorkingFileState probe(Repository repository, Path workingFile) throws IOException {
        if (!Files.exists(workingFile)) {
            return WorkingFileState.MISSING;
        }
        RevCommit headCommit = RevisionReader.headCommit(repository);
        if (headCommit == null) {
            return WorkingFileState.MODIFIED;
        }
        byte[] committedBytes = RevisionReader.bytesAt(
                repository, headCommit, workingFile.getFileName().toString());
        if (committedBytes == null) {
            return WorkingFileState.MODIFIED;
        }
        boolean identical = Arrays.equals(committedBytes, Files.readAllBytes(workingFile));
        return identical ? WorkingFileState.CLEAN : WorkingFileState.MODIFIED;
    }

    /** Overwrites the working file with the branch head's bytes. */
    static void restore(Repository repository, Path workingFile) throws IOException {
        RevCommit headCommit = RevisionReader.headCommit(repository);
        if (headCommit == null) {
            throw new IllegalStateException(
                    "This document has no snapshot yet, so there is nothing to restore.");
        }
        String fileName = workingFile.getFileName().toString();
        byte[] headBytes = RevisionReader.bytesAt(repository, headCommit, fileName);
        if (headBytes == null) {
            throw new IOException("Version " + headCommit.getName() + " has no file " + fileName);
        }
        Files.write(workingFile, headBytes);
    }
}
