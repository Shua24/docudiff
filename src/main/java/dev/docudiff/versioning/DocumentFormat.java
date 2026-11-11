package dev.docudiff.versioning;

import java.util.Optional;

/** The document formats the app accepts. */
public enum DocumentFormat {
    DOCX("docx"),
    XLSX("xlsx"),
    MARKDOWN("md", "markdown");

    private final String[] fileExtensions;

    DocumentFormat(String... fileExtensions) {
        this.fileExtensions = fileExtensions;
    }

    /** Returns the format for a file name, or empty when the format is not supported. */
    public static Optional<DocumentFormat> fromFileName(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return Optional.empty();
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(java.util.Locale.ROOT);
        for (DocumentFormat format : values()) {
            for (String candidate : format.fileExtensions) {
                if (candidate.equals(extension)) {
                    return Optional.of(format);
                }
            }
        }
        return Optional.empty();
    }

    /** The extension used when writing this format into the library folder. */
    public String canonicalExtension() {
        return fileExtensions[0];
    }
}
