package dev.docudiff.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Finds the LibreOffice executable. The app degrades to a placeholder document view when this
 * returns empty, so a missing Office install never prevents diffing.
 *
 * <p>The candidate names are per-platform on purpose: Windows resolves executables by their
 * extension, so {@code Files.isExecutable(Path.of(directory, "soffice"))} is false there even
 * though {@code soffice.exe} sits in that directory. Without the {@code .exe} names the renderer
 * tests skip on Windows and the page-view path is never exercised.
 */
public final class OfficeLocator {

    private static final String[] UNIX_CANDIDATE_NAMES = {"soffice", "libreoffice"};
    private static final String[] WINDOWS_CANDIDATE_NAMES = {"soffice.exe", "libreoffice.exe"};

    private static final String[] DEFAULT_INSTALL_PATHS = {
            "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
            "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe",
            "/Applications/LibreOffice.app/Contents/MacOS/soffice"
    };

    private OfficeLocator() {
    }

    public static Optional<Path> locate() {
        Optional<Path> fromSettings = dev.docudiff.settings.AppSettings.load().libreOfficePath()
                .filter(Files::isExecutable);
        if (fromSettings.isPresent()) {
            return fromSettings;
        }
        String configured = System.getenv("DOCUDIFF_LIBREOFFICE");
        if (configured != null && !configured.isBlank() && Files.isExecutable(Path.of(configured))) {
            return Optional.of(Path.of(configured));
        }
        Optional<Path> onPath = locateOnPath();
        if (onPath.isPresent()) {
            return onPath;
        }
        return locateInDefaultInstallDirectory();
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

    private static Optional<Path> locateOnPath() {
        String pathVariable = System.getenv("PATH");
        if (pathVariable == null) {
            return Optional.empty();
        }
        for (String directory : pathVariable.split(java.io.File.pathSeparator)) {
            for (String candidateName : candidateNames()) {
                Path candidate = Path.of(directory, candidateName);
                if (Files.isExecutable(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> locateInDefaultInstallDirectory() {
        for (String installPath : DEFAULT_INSTALL_PATHS) {
            Path candidate = Path.of(installPath);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static String[] candidateNames() {
        return isWindows() ? WINDOWS_CANDIDATE_NAMES : UNIX_CANDIDATE_NAMES;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
