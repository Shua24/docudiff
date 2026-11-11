package dev.docudiff.compare;

import dev.docudiff.versioning.DocumentFormat;

/**
 * Thrown when the two sides of a comparison are not the same kind of document.
 *
 * <p>Diffing a spreadsheet against a word processor document has no meaning — the blocks do not
 * correspond and the row aligner has nothing to align — so the comparer refuses rather than
 * inventing an output. The message names both formats because the user picked them.
 */
public final class UnsupportedComparisonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final DocumentFormat leftFormat;
    private final DocumentFormat rightFormat;

    public UnsupportedComparisonException(DocumentFormat leftFormat, DocumentFormat rightFormat) {
        super("Cannot compare " + leftFormat + " with " + rightFormat
                + ": both documents must be the same format.");
        this.leftFormat = leftFormat;
        this.rightFormat = rightFormat;
    }

    public DocumentFormat leftFormat() {
        return leftFormat;
    }

    public DocumentFormat rightFormat() {
        return rightFormat;
    }
}
