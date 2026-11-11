package dev.docudiff.library;

import dev.docudiff.versioning.DocumentFormat;
import org.eclipse.jgit.api.Git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

/**
 * One document folder: its working file, its repository, and the format it holds.
 */
public final class DocumentFolder {

    private final Path folderPath;

    public DocumentFolder(Path folderPath) {
        this.folderPath = folderPath;
    }

    public Path folderPath() {
        return folderPath;
    }

    /** The document name, so a folder's identity is legible in logs and list cells. */
    @Override
    public String toString() {
        return documentName();
    }

    public String documentName() {
        return folderPath.getFileName().toString();
    }

    /** The working file, if the folder holds exactly one supported document. */
    public Optional<Path> workingFile() throws IOException {
        try (var entries = Files.list(folderPath)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> DocumentFormat.fromFileName(
                            path.getFileName().toString()).isPresent())
                    .findFirst();
        }
    }

    public DocumentFormat format() throws IOException {
        Optional<Path> workingFile = workingFile();
        if (workingFile.isEmpty()) {
            throw new IOException("No document file in " + folderPath);
        }
        return DocumentFormat.fromFileName(workingFile.get().getFileName().toString())
                .orElseThrow();
    }

    public String workingFileName() throws IOException {
        return workingFile().orElseThrow().getFileName().toString();
    }

    public static DocumentFolder create(Path folderPath, String documentName, String extension)
            throws Exception {
        Files.createDirectories(folderPath);
        try (Git git = Git.init()
                .setDirectory(folderPath.toFile())
                .setInitialBranch("main")
                .call()) {
            // Repository created; the caller writes the document and takes the first snapshot.
            excludeCacheDirectory(folderPath);
        }
        return new DocumentFolder(folderPath);
    }

    /**
     * Keeps the PDF cache out of git without adding a committed .gitignore to the user's folder,
     * which would show up in their own tooling and look like clutter.
     */
    static void excludeCacheDirectory(Path folderPath) throws IOException {
        Path excludeFile = folderPath.resolve(".git").resolve("info").resolve("exclude");
        Files.createDirectories(excludeFile.getParent());
        String existing = Files.exists(excludeFile) ? Files.readString(excludeFile) : "";
        if (!existing.contains(".docudiff/cache/")) {
            Files.writeString(excludeFile, existing + "\n.docudiff/cache/\n");
        }
    }

    public Path cacheDirectory() {
        return folderPath.resolve(".docudiff").resolve("cache");
    }

    /** Sorts newest first, which is the order a version list should show. */
    public static Comparator<Path> newestFirst() {
        return Comparator.<Path>comparingLong(path -> {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException exception) {
                return 0L;
            }
        }).reversed();
    }
}
