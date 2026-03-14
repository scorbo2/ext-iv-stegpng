package ca.corbett.imageviewer.extensions.stegpng;

/**
 * A custom Exception type to be thrown when the given container PNG image
 * does not have sufficient space to embed the given message at the
 * requested data compaction level.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class InsufficientSpaceException extends Exception {

    public static final String MESSAGE =
            "The given container image does not have sufficient space " +
                    "to embed the message at the requested compaction level.";

    public InsufficientSpaceException() {
        super(MESSAGE);
    }

    public InsufficientSpaceException(String message) {
        super(message);
    }

    public InsufficientSpaceException(String message, Throwable cause) {
        super(message, cause);
    }

    public InsufficientSpaceException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
