package se233.project1.exception;

/** Thrown when the external tracing CLI (Potrace) cannot be located or started. */
public class ToolNotFoundException extends ConversionException {

    public ToolNotFoundException(String message) {
        super(message);
    }

    public ToolNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
