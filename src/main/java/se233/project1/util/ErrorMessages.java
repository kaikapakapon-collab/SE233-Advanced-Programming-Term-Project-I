package se233.project1.util;

import se233.project1.exception.ConversionException;

/** Turns exceptions into short, user-friendly messages. */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    public static String describe(Throwable t) {
        if (t == null) {
            return "Unknown error";
        }
        if (t instanceof OutOfMemoryError) {
            return "Not enough memory to process this image. Try a smaller image or a lower detail level.";
        }
        if (t instanceof ConversionException && t.getMessage() != null) {
            return t.getMessage();
        }
        String message = t.getMessage();
        String type = t.getClass().getSimpleName();
        return message == null || message.isBlank() ? type : type + ": " + message;
    }
}
