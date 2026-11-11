package dev.docudiff.compare;

/**
 * Wraps a failure while reading or diffing documents, so the UI can show one clear message
 * instead of a stack trace. A corrupt file that POI cannot open lands here.
 */
public final class ComparisonFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ComparisonFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
