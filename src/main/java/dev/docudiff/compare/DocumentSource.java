package dev.docudiff.compare;

import dev.docudiff.versioning.DocumentFormat;

import java.util.Arrays;

/**
 * One side of a comparison: a label for the reader, the bytes, and the format.
 *
 * <p>Deliberately knows nothing about versions, commits or the library. A source can come from a
 * git blob, a file on disk, or a byte array in a test — the comparer cannot tell the difference,
 * which is what makes "compare any two documents" one code path instead of four.
 *
 * @param label  what to call this side on screen, for example a snapshot name or a file name
 * @param bytes  the document's bytes
 * @param format the document format, which decides the extractor
 */
public record DocumentSource(String label, byte[] bytes, DocumentFormat format) {

    public DocumentSource {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("A document source needs a label");
        }
        if (bytes == null) {
            throw new IllegalArgumentException("A document source needs bytes");
        }
        if (format == null) {
            throw new IllegalArgumentException("A document source needs a format");
        }
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    public static DocumentSource ofBytes(String label, String text, DocumentFormat format) {
        return new DocumentSource(label, text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                format);
    }

    public static DocumentSource ofBytes(String label, byte[] bytes, DocumentFormat format) {
        return new DocumentSource(label, bytes, format);
    }

    /** The bytes, copied so a caller cannot mutate the source after construction. */
    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
