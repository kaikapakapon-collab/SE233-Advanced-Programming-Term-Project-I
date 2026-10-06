package se233.project1.exception;

/** Thrown when a zip archive is corrupted, unsafe (Zip Slip / zip bomb) or contains no usable images. */
public class ArchiveException extends ConversionException {

    public ArchiveException(String message) {
        super(message);
    }

    public ArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
