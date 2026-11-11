package dev.docudiff.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The library root: {@code ~/Documents/Docudiff}, one folder per document.
 *
 * <p>The folder is visible on purpose. Users who barely know the CLI can find their documents,
 * back them up, and copy them to another machine; a hidden dot-directory would make the app's
 * data invisible to every backup tool they already trust.
 */
public final class LibraryRoot {

    public static final String DIRECTORY_NAME = "Docudiff";

    private final Path rootPath;

    public LibraryRoot(Path rootPath) {
        this.rootPath = rootPath;
    }

    public static LibraryRoot defaultRoot() {
        String userHome = System.getProperty("user.home");
        Path documents = Path.of(userHome, "Documents");
        Path base = Files.isDirectory(documents) ? documents : Path.of(userHome);
        return new LibraryRoot(base.resolve(DIRECTORY_NAME));
    }

    public Path rootPath() {
        return rootPath;
    }

    public void ensureExists() throws IOException {
        Files.createDirectories(rootPath);
    }

    /** Every document folder that has a {@code .git} directory, sorted by name. */
    public List<Path> documentFolders() throws IOException {
        ensureExists();
        List<Path> folders = new ArrayList<>();
        try (var entries = Files.list(rootPath)) {
            entries.filter(Files::isDirectory)
                    .filter(path -> Files.isDirectory(path.resolve(".git")))
                    .sorted()
                    .forEach(folders::add);
        }
        return folders;
    }

    /**
     * Creates the folder for a new document, adding {@code -2}, {@code -3} on a name collision
     * rather than overwriting an existing document.
     */
    public Path createDocumentFolder(String documentName) throws IOException {
        ensureExists();
        Path candidate = rootPath.resolve(sanitise(documentName));
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = rootPath.resolve(sanitise(documentName) + "-" + suffix);
            suffix++;
        }
        Files.createDirectories(candidate);
        return candidate;
    }

    private String sanitise(String documentName) {
        String cleaned = documentName.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        if (cleaned.isEmpty()) {
            cleaned = "document";
        }
        return cleaned;
    }
}
