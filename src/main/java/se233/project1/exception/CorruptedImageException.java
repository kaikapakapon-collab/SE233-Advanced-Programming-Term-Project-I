package se233.project1.exception;

/** Thrown when an input file is not a readable JPG/PNG image. */
public class CorruptedImageException extends ConversionException {

    public CorruptedImageException(String message) {
        super(message);
    }

    public CorruptedImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
