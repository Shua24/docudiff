package dev.docudiff.compare;

import dev.docudiff.library.DocumentFolder;
import dev.docudiff.versioning.DocumentFormat;
import dev.docudiff.versioning.VersionRepository;

import java.nio.file.Files;

/**
 * Turns a {@link ComparisonSlot} into a {@link DocumentSource} by reading its bytes.
 *
 * <p>The only place that knows how a slot becomes bytes. Reading happens once, eagerly: the
 * resulting {@code DocumentSource} holds a copy, so comparing against the working file and then
 * editing it in LibreOffice does not silently invalidate the comparison on screen.
 */
public final class DocumentSourceResolver {

    private final VersionRepository repository;
    private final DocumentFolder documentFolder;

    /**
     * @param repository     the open document's repository, or null when no document is open
     * @param documentFolder the open document's folder, or null when no document is open
     */
    public DocumentSourceResolver(VersionRepository repository, DocumentFolder documentFolder) {
        this.repository = repository;
        this.documentFolder = documentFolder;
    }

    public DocumentSource resolve(ComparisonSlot slot) throws Exception {
        if (slot instanceof ComparisonSlot.VersionSlot versionSlot) {
            return resolveVersion(versionSlot);
        }
        if (slot instanceof ComparisonSlot.FileSlot fileSlot) {
            return resolveFile(fileSlot);
        }
        if (slot instanceof ComparisonSlot.WorkingFileSlot workingFileSlot) {
            return resolveWorkingFile(workingFileSlot);
        }
        throw new IllegalArgumentException("Unknown slot type: " + slot);
    }

    private DocumentSource resolveVersion(ComparisonSlot.VersionSlot slot) throws Exception {
        if (repository == null || documentFolder == null) {
            throw new IllegalStateException(
                    "No document is open, so version \"" + slot.label() + "\" cannot be read.");
        }
        String fileName = documentFolder.workingFileName();
        byte[] bytes = repository.bytesOfVersion(fileName, slot.commitId());
        return DocumentSource.ofBytes(slot.label(), bytes, documentFolder.format());
    }

    private DocumentSource resolveFile(ComparisonSlot.FileSlot slot) throws Exception {
        byte[] bytes = Files.readAllBytes(slot.path());
        return DocumentSource.ofBytes(slot.label(), bytes, slot.format());
    }

    private DocumentSource resolveWorkingFile(ComparisonSlot.WorkingFileSlot slot)
            throws Exception {
        if (documentFolder == null) {
            throw new IllegalStateException(
                    "No document is open, so the working file cannot be read.");
        }
        java.nio.file.Path workingFile = documentFolder.workingFile()
                .orElseThrow(() -> new IllegalStateException(
                        "The working file is missing from the library folder."));
        byte[] bytes = Files.readAllBytes(workingFile);
        DocumentFormat format = documentFolder.format();
        return DocumentSource.ofBytes(slot.label(), bytes, format);
    }
}
