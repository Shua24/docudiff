package dev.docudiff.ui;

import dev.docudiff.versioning.VersionRepository;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helpers the viewer panes need that are about files rather than about diffing.
 *
 * <p>Diffing itself now lives in {@code dev.docudiff.compare}, which knows nothing about
 * repositories, versions or the library. What is left here is the one operation that genuinely
 * needs a repository: writing a version's bytes somewhere LibreOffice and the WebView can reach.
 */
final class ComparisonService {

    /**
     * Writes a version's bytes to a scratch file so LibreOffice and the WebView have a path to
     * work with. The file lives outside the library so viewing never mutates the working copy.
     */
    Path scratchFileOf(VersionRepository repository, String fileName, String commitId)
            throws Exception {
        Path directory = Files.createTempDirectory("docudiff-view");
        Path file = directory.resolve(fileName);
        Files.write(file, repository.bytesOfVersion(fileName, commitId));
        return file;
    }
}
