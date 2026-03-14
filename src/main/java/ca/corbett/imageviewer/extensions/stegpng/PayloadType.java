package ca.corbett.imageviewer.extensions.stegpng;

/**
 * An enum to represent the type of data being embedded in a stegged image - either a string or binary data.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public enum PayloadType {
    STRING((byte)0), BINARY((byte)1);

    private final byte value;

    PayloadType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    /**
     * A helper method to convert a byte value back into a PayloadType enum constant.
     * Will return null for any invalid value.
     *
     * @param value a candidate byte value.
     * @return The corresponding PayloadType constant if the value is valid, or null if it is not.
     */
    public static PayloadType ofValue(byte value) {
        for (PayloadType type : values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }
}
