package dev.docudiff.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

/**
 * Copies a file the user picked into the library and takes its first snapshot.
 */
public final class ImportService {

    private final LibraryRoot libraryRoot;
    private final dev.docudiff.versioning.SnapshotNameGenerator nameGenerator =
            new dev.docudiff.versioning.SnapshotNameGenerator();

    public ImportService(LibraryRoot libraryRoot) {
        this.libraryRoot = libraryRoot;
    }

    /**
     * @param sourceFile the file the user chose
     * @return the folder the document now lives in
     */
    public Path importFile(Path sourceFile) throws Exception {
        String fileName = sourceFile.getFileName().toString();
        dev.docudiff.versioning.DocumentFormat format =
                dev.docudiff.versioning.DocumentFormat.fromFileName(fileName)
                        .orElseThrow(() -> new IOException(
                                "Unsupported file type: " + fileName));

        int dotIndex = fileName.lastIndexOf('.');
        String documentName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String extension = format.canonicalExtension();
        String libraryFileName = documentName + "." + extension;

        Path folderPath = libraryRoot.createDocumentFolder(documentName);
        DocumentFolder documentFolder =
                DocumentFolder.create(folderPath, documentName, extension);

        Path destination = folderPath.resolve(libraryFileName);
        Files.copy(sourceFile, destination, StandardCopyOption.REPLACE_EXISTING);

        dev.docudiff.versioning.VersionRepository repository =
                new dev.docudiff.versioning.VersionRepository(folderPath);
        String snapshotName = nameGenerator.next(
                documentName, LocalDate.now(), repository.existingSnapshotNames());
        repository.snapshot(documentName, snapshotName);
        return folderPath;
    }
}
