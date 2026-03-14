package ca.corbett.imageviewer.extensions.stegpng;

/**
 * A custom Exception type to be thrown when a candidate PNG container image appears to contain a StegPNG message,
 * but the version number in the header is not recognized. This may occur if the message was created with a newer
 * version of StegPNG that is not compatible with this version of the extension.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class InvalidVersionException extends Exception {

    public static final String MESSAGE =
            "The given PNG image appears to contain a StegPNG message, but the version number is not recognized.";

    public InvalidVersionException() {
        super(MESSAGE);
    }

    public InvalidVersionException(String message) {
        super(message);
    }

    public InvalidVersionException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidVersionException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
