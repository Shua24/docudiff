package dev.docudiff.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Finds the LibreOffice executable. The app degrades to a placeholder document view when this
 * returns empty, so a missing Office install never prevents diffing.
 */
public final class OfficeLocator {

    private static final String[] CANDIDATE_NAMES = {"soffice", "libreoffice"};

    private OfficeLocator() {
    }

    public static Optional<Path> locate() {
        String configured = System.getenv("DOCUDIFF_LIBREOFFICE");
        if (configured != null && !configured.isBlank() && Files.isExecutable(Path.of(configured))) {
            return Optional.of(Path.of(configured));
        }
        String pathVariable = System.getenv("PATH");
        if (pathVariable == null) {
            return Optional.empty();
        }
        for (String directory : pathVariable.split(java.io.File.pathSeparator)) {
            for (String candidateName : CANDIDATE_NAMES) {
                Path candidate = Path.of(directory, candidateName);
                if (Files.isExecutable(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<String> version() {
        Optional<Path> office = locate();
        if (office.isEmpty()) {
            return Optional.empty();
        }
        try {
            Process process = new ProcessBuilder(office.get().toString(), "--version")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes()).strip();
            process.waitFor();
            return Optional.of(output);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }
}
