package ca.corbett.imageviewer.extensions.stegpng;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for {@link StegInfo}.
 */
class StegInfoTest {

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Builds a syntactically correct HEADER_SIZE byte array using the supplied values.
     */
    private byte[] buildValidByteArray(byte compactionLevel, PayloadType payloadType, int dataLength) {
        byte[] data = new byte[StegInfo.HEADER_SIZE];
        data[0] = (byte) ((StegInfo.MAGIC_NUMBER >> 8) & 0xFF);
        data[1] = (byte) (StegInfo.MAGIC_NUMBER & 0xFF);
        data[2] = StegPNG.MAJOR_VERSION;
        data[3] = StegPNG.MINOR_VERSION;
        data[4] = compactionLevel;
        data[5] = payloadType.getValue();
        data[6] = (byte) ((dataLength >> 24) & 0xFF);
        data[7] = (byte) ((dataLength >> 16) & 0xFF);
        data[8] = (byte) ((dataLength >> 8) & 0xFF);
        data[9] = (byte) (dataLength & 0xFF);
        return data;
    }

    // =========================================================================
    // Constants
    // =========================================================================

    @Test
    void headerSize_isExpectedValue() {
        assertEquals(10, StegInfo.HEADER_SIZE);
    }

    @Test
    void magicNumber_isExpectedValue() {
        assertEquals((short) 0x5350, StegInfo.MAGIC_NUMBER);
    }

    // =========================================================================
    // Constructor: StegInfo(byte compactionLevel, PayloadType, int messageLength)
    // =========================================================================

    @Test
    void paramConstructor_validStringPayload_succeeds() {
        StegInfo info = new StegInfo((byte) 1, PayloadType.STRING, 100);

        assertEquals(StegPNG.MAJOR_VERSION, info.getMajorVersion());
        assertEquals(StegPNG.MINOR_VERSION, info.getMinorVersion());
        assertEquals(1, info.getDataCompactionLevel());
        assertEquals(PayloadType.STRING, info.getPayloadType());
        assertEquals(100, info.getDataSegmentLength());
    }

    @Test
    void paramConstructor_validBinaryPayload_succeeds() {
        StegInfo info = new StegInfo((byte) 4, PayloadType.BINARY, 512);

        assertEquals(4, info.getDataCompactionLevel());
        assertEquals(PayloadType.BINARY, info.getPayloadType());
        assertEquals(512, info.getDataSegmentLength());
    }

    @Test
    void paramConstructor_compactionLevelMin_succeeds() {
        assertDoesNotThrow(() -> new StegInfo((byte) 1, PayloadType.STRING, 1));
    }

    @Test
    void paramConstructor_compactionLevelMax_succeeds() {
        assertDoesNotThrow(() -> new StegInfo((byte) 8, PayloadType.BINARY, 1));
    }

    @Test
    void paramConstructor_allCompactionLevels_succeed() {
        for (byte level = 1; level <= 8; level++) {
            final byte l = level;
            assertDoesNotThrow(
                    () -> new StegInfo(l, PayloadType.STRING, 42),
                    "Compaction level " + level + " should be valid");
        }
    }

    @Test
    void paramConstructor_compactionLevelZero_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new StegInfo((byte) 0, PayloadType.STRING, 100));
    }

    @Test
    void paramConstructor_compactionLevelNine_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new StegInfo((byte) 9, PayloadType.STRING, 100));
    }

    @Test
    void paramConstructor_compactionLevelNegative_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new StegInfo((byte) -1, PayloadType.STRING, 100));
    }

    @Test
    void paramConstructor_nullPayloadType_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new StegInfo((byte) 1, null, 100));
    }

    // =========================================================================
    // Constructor: StegInfo(byte[] data)
    // =========================================================================

    @Test
    void byteArrayConstructor_validData_succeeds() throws Exception {
        byte[] data = buildValidByteArray((byte) 3, PayloadType.BINARY, 256);
        StegInfo info = new StegInfo(data);

        assertEquals(StegPNG.MAJOR_VERSION, info.getMajorVersion());
        assertEquals(StegPNG.MINOR_VERSION, info.getMinorVersion());
        assertEquals(3, info.getDataCompactionLevel());
        assertEquals(PayloadType.BINARY, info.getPayloadType());
        assertEquals(256, info.getDataSegmentLength());
    }

    @Test
    void byteArrayConstructor_nullData_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new StegInfo((byte[]) null));
    }

    @Test
    void byteArrayConstructor_emptyArray_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new StegInfo(new byte[0]));
    }

    @Test
    void byteArrayConstructor_arrayTooShort_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new StegInfo(new byte[StegInfo.HEADER_SIZE - 1]));
    }

    @Test
    void byteArrayConstructor_exactHeaderSizeButBadMagic_throwsInvalidPayload() {
        byte[] data = buildValidByteArray((byte) 1, PayloadType.STRING, 10);
        // Corrupt the magic number
        data[0] = 0x00;
        data[1] = 0x00;

        assertThrows(InvalidPayloadException.class, () -> new StegInfo(data));
    }

    @Test
    void byteArrayConstructor_firstMagicByteWrong_throwsInvalidPayload() {
        byte[] data = buildValidByteArray((byte) 1, PayloadType.STRING, 10);
        data[0] = (byte) ~data[0]; // flip all bits of first magic byte

        assertThrows(InvalidPayloadException.class, () -> new StegInfo(data));
    }

    @Test
    void byteArrayConstructor_secondMagicByteWrong_throwsInvalidPayload() {
        byte[] data = buildValidByteArray((byte) 1, PayloadType.STRING, 10);
        data[1] = (byte) ~data[1]; // flip all bits of second magic byte

        assertThrows(InvalidPayloadException.class, () -> new StegInfo(data));
    }

    @Test
    void byteArrayConstructor_wrongMajorVersion_throwsInvalidVersion() {
        byte[] data = buildValidByteArray((byte) 1, PayloadType.STRING, 10);
        data[2] = (byte) (StegPNG.MAJOR_VERSION + 1); // bump major version

        assertThrows(InvalidVersionException.class, () -> new StegInfo(data));
    }

    @Test
    void byteArrayConstructor_wrongMinorVersion_throwsInvalidVersion() {
        byte[] data = buildValidByteArray((byte) 1, PayloadType.STRING, 10);
        data[3] = (byte) (StegPNG.MINOR_VERSION + 1); // bump minor version

        assertThrows(InvalidVersionException.class, () -> new StegInfo(data));
    }

    @Test
    void byteArrayConstructor_bothVersionsWrong_throwsInvalidVersion() {
        byte[] data = buildValidByteArray((byte) 1, PayloadType.STRING, 10);
        data[2] = 0;
        data[3] = 0;

        assertThrows(InvalidVersionException.class, () -> new StegInfo(data));
    }

    @Test
    void byteArrayConstructor_compactionLevelZero_throwsInvalidPayload() {
        byte[] data = buildValidByteArray((byte) 0, PayloadType.STRING, 10);

        assertThrows(InvalidPayloadException.class, () -> new StegInfo(data));
    }

    @Test
    void byteArrayConstructor_compactionLevelNine_throwsInvalidPayload() {
        byte[] data = buildValidByteArray((byte) 9, PayloadType.STRING, 10);

        assertThrows(InvalidPayloadException.class, () -> new StegInfo(data));
    }

    @Test
    void byteArrayConstructor_invalidPayloadTypeByte_throwsInvalidPayload() {
        byte[] data = buildValidByteArray((byte) 1, PayloadType.STRING, 10);
        data[5] = (byte) 0xFF; // no PayloadType has this value

        assertThrows(InvalidPayloadException.class, () -> new StegInfo(data));
    }

    @Test
    void byteArrayConstructor_dataSegmentLengthZero_throwsInvalidPayload() {
        byte[] data = buildValidByteArray((byte) 1, PayloadType.STRING, 0);

        assertThrows(InvalidPayloadException.class, () -> new StegInfo(data));
    }

    @Test
    void byteArrayConstructor_dataSegmentLengthNegative_throwsInvalidPayload() {
        byte[] data = buildValidByteArray((byte) 1, PayloadType.STRING, -1);

        assertThrows(InvalidPayloadException.class, () -> new StegInfo(data));
    }

    // =========================================================================
    // toByteArray()
    // =========================================================================

    @Test
    void toByteArray_hasMagicNumberInFirstTwoBytes() {
        StegInfo info = new StegInfo((byte) 1, PayloadType.STRING, 100);
        byte[] bytes = info.toByteArray();

        byte expectedHigh = (byte) ((StegInfo.MAGIC_NUMBER >> 8) & 0xFF);
        byte expectedLow  = (byte) (StegInfo.MAGIC_NUMBER & 0xFF);
        assertEquals(expectedHigh, bytes[0], "Magic number high byte");
        assertEquals(expectedLow,  bytes[1], "Magic number low byte");
    }

    @Test
    void toByteArray_hasCorrectVersionBytes() {
        StegInfo info = new StegInfo((byte) 1, PayloadType.STRING, 100);
        byte[] bytes = info.toByteArray();

        assertEquals(StegPNG.MAJOR_VERSION, bytes[2], "Major version byte");
        assertEquals(StegPNG.MINOR_VERSION, bytes[3], "Minor version byte");
    }

    @Test
    void toByteArray_hasCorrectCompactionLevel() {
        StegInfo info = new StegInfo((byte) 5, PayloadType.BINARY, 200);
        byte[] bytes = info.toByteArray();

        assertEquals(5, bytes[4], "Compaction level byte");
    }

    @Test
    void toByteArray_hasCorrectPayloadType_String() {
        StegInfo info = new StegInfo((byte) 1, PayloadType.STRING, 50);
        byte[] bytes = info.toByteArray();

        assertEquals(PayloadType.STRING.getValue(), bytes[5], "Payload type byte for STRING");
    }

    @Test
    void toByteArray_hasCorrectPayloadType_Binary() {
        StegInfo info = new StegInfo((byte) 1, PayloadType.BINARY, 50);
        byte[] bytes = info.toByteArray();

        assertEquals(PayloadType.BINARY.getValue(), bytes[5], "Payload type byte for BINARY");
    }

    @Test
    void toByteArray_hasCorrectDataSegmentLength() {
        int length = 0x01A2B3C4;
        StegInfo info = new StegInfo((byte) 1, PayloadType.STRING, length);
        byte[] bytes = info.toByteArray();

        int reconstructed = ((bytes[6] << 24) & 0xFF000000)
                | ((bytes[7] << 16) & 0xFF0000)
                | ((bytes[8] << 8)  & 0xFF00)
                | (bytes[9] & 0xFF);
        assertEquals(length, reconstructed, "Reconstructed data segment length");
    }

    @Test
    void toByteArray_returnsExactlyHeaderSizeBytes() {
        StegInfo info = new StegInfo((byte) 3, PayloadType.BINARY, 1024);
        assertEquals(StegInfo.HEADER_SIZE, info.toByteArray().length);
    }

    // =========================================================================
    // Round-trip tests (param constructor → toByteArray → byte[] constructor)
    // =========================================================================

    @Test
    void roundTrip_stringPayload_preservesAllFields() throws Exception {
        StegInfo original = new StegInfo((byte) 2, PayloadType.STRING, 999);
        StegInfo restored = new StegInfo(original.toByteArray());

        assertEquals(original.getMajorVersion(),        restored.getMajorVersion());
        assertEquals(original.getMinorVersion(),        restored.getMinorVersion());
        assertEquals(original.getDataCompactionLevel(), restored.getDataCompactionLevel());
        assertEquals(original.getPayloadType(),         restored.getPayloadType());
        assertEquals(original.getDataSegmentLength(),   restored.getDataSegmentLength());
    }

    @Test
    void roundTrip_binaryPayload_preservesAllFields() throws Exception {
        StegInfo original = new StegInfo((byte) 7, PayloadType.BINARY, 65535);
        StegInfo restored = new StegInfo(original.toByteArray());

        assertEquals(original.getMajorVersion(),        restored.getMajorVersion());
        assertEquals(original.getMinorVersion(),        restored.getMinorVersion());
        assertEquals(original.getDataCompactionLevel(), restored.getDataCompactionLevel());
        assertEquals(original.getPayloadType(),         restored.getPayloadType());
        assertEquals(original.getDataSegmentLength(),   restored.getDataSegmentLength());
    }

    @Test
    void roundTrip_allCompactionLevels_preserveCompactionLevel() throws Exception {
        for (byte level = 1; level <= 8; level++) {
            StegInfo original = new StegInfo(level, PayloadType.BINARY, 42);
            StegInfo restored = new StegInfo(original.toByteArray());
            assertEquals(level, restored.getDataCompactionLevel(),
                    "Compaction level " + level + " should survive round-trip");
        }
    }

    @Test
    void roundTrip_largeDataSegmentLength_preserved() throws Exception {
        int bigLength = Integer.MAX_VALUE;
        StegInfo original = new StegInfo((byte) 1, PayloadType.BINARY, bigLength);
        StegInfo restored = new StegInfo(original.toByteArray());

        assertEquals(bigLength, restored.getDataSegmentLength());
    }

    @Test
    void roundTrip_minimumDataSegmentLength_preserved() throws Exception {
        StegInfo original = new StegInfo((byte) 1, PayloadType.STRING, 1);
        StegInfo restored = new StegInfo(original.toByteArray());

        assertEquals(1, restored.getDataSegmentLength());
    }

    // =========================================================================
    // Getter tests
    // =========================================================================

    @Test
    void getters_reflectCurrentVersionConstants() {
        StegInfo info = new StegInfo((byte) 1, PayloadType.STRING, 1);

        assertEquals(StegPNG.MAJOR_VERSION, info.getMajorVersion());
        assertEquals(StegPNG.MINOR_VERSION, info.getMinorVersion());
    }

    @Test
    void getPayloadType_returnsString() {
        StegInfo info = new StegInfo((byte) 1, PayloadType.STRING, 1);
        assertEquals(PayloadType.STRING, info.getPayloadType());
    }

    @Test
    void getPayloadType_returnsBinary() {
        StegInfo info = new StegInfo((byte) 1, PayloadType.BINARY, 1);
        assertEquals(PayloadType.BINARY, info.getPayloadType());
    }

    @Test
    void getDataCompactionLevel_returnsSuppliedLevel() {
        for (byte level = 1; level <= 8; level++) {
            StegInfo info = new StegInfo(level, PayloadType.STRING, 10);
            assertEquals(level, info.getDataCompactionLevel());
        }
    }

    @Test
    void getDataSegmentLength_returnsSuppliedLength() {
        int[] lengths = {1, 100, 1024, 1_000_000, Integer.MAX_VALUE};
        for (int length : lengths) {
            StegInfo info = new StegInfo((byte) 1, PayloadType.STRING, length);
            assertEquals(length, info.getDataSegmentLength(),
                    "Expected data segment length " + length);
        }
    }
}

