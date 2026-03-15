package ca.corbett.imageviewer.extensions.stegpng;

/**
 * A custom Exception type to be thrown when a candidate PNG container image does not
 * appear to contain a valid StegPNG message. Most commonly, this is because the
 * embedded "magic number" is missing or incorrect, but there are other internal
 * checks that may trigger this exception.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class InvalidPayloadException extends Exception {

    /**
     * A technically accurate but deliberately vague message.
     */
    public static final String MESSAGE = "The given PNG image does not appear to contain a valid StegPNG message.";

    public InvalidPayloadException() {
        super(MESSAGE);
    }

    public InvalidPayloadException(String message) {
        super(message);
    }

    public InvalidPayloadException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidPayloadException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
