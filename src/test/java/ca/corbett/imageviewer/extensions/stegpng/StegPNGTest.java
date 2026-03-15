package ca.corbett.imageviewer.extensions.stegpng;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Comprehensive unit tests for {@link StegPNG}.
 */
class StegPNGTest {

    @TempDir
    Path tempDir;

    @Test
    void constructor_defaultCompactionLevel_roundTripsStringSuccessfully() throws Exception {
        StegPNG stegPNG = new StegPNG();
        BufferedImage container = createContainerImage(150, 150);
        String secret = "StegPNG default ctor round trip.";

        BufferedImage stegged = stegPNG.embedSecretMessage(container, secret);
        StegInfo info = stegPNG.getStegInfo(stegged);

        assertEquals(1, info.getDataCompactionLevel());
        assertEquals(PayloadType.STRING, info.getPayloadType());
        assertEquals(secret.length() * 2, info.getDataSegmentLength());

        File output = tempDir.resolve("roundtrip-default.txt").toFile();
        stegPNG.retrieveSecretMessage(stegged, output);
        assertEquals(secret, Files.readString(output.toPath()));
    }

    @Test
    void isSteggedImage_shouldReportCorrectly() throws Exception {
        BufferedImage container = createContainerImage(150, 150);
        assertFalse(StegPNG.isSteggedImage(container));
        BufferedImage stegged = new StegPNG().embedSecretMessage(container, "hi there");
        assertTrue(StegPNG.isSteggedImage(stegged));
        Graphics2D g = stegged.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, 10, 10);
        }
        finally {
            g.dispose();
        }
        assertFalse(StegPNG.isSteggedImage(stegged), "Modifying the image should have destroyed our header");
    }

    @Test
    void constructor_withValidCompactionLevel_roundTripsBinarySuccessfully() throws Exception {
        StegPNG stegPNG = new StegPNG(4);
        BufferedImage container = createContainerImage(180, 180);
        byte[] secretBytes = buildBinaryPayload(2048);

        File inputSecret = tempDir.resolve("secret.bin").toFile();
        Files.write(inputSecret.toPath(), secretBytes);

        BufferedImage stegged = stegPNG.embedSecretMessage(container, inputSecret);
        StegInfo info = stegPNG.getStegInfo(stegged);

        assertEquals(4, info.getDataCompactionLevel());
        assertEquals(PayloadType.BINARY, info.getPayloadType());
        assertEquals(secretBytes.length, info.getDataSegmentLength());

        File outputSecret = tempDir.resolve("retrieved.bin").toFile();
        stegPNG.retrieveSecretMessage(stegged, outputSecret);

        assertArrayEquals(secretBytes, Files.readAllBytes(outputSecret.toPath()));
    }

    @Test
    void constructor_withInvalidCompactionLevel_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new StegPNG(0));
        assertThrows(IllegalArgumentException.class, () -> new StegPNG(9));
    }

    @Test
    void setDataCompactionLevel_outOfRange_throwsIllegalArgumentException() {
        StegPNG stegPNG = new StegPNG();

        assertThrows(IllegalArgumentException.class, () -> stegPNG.setDataCompactionLevel(0));
        assertThrows(IllegalArgumentException.class, () -> stegPNG.setDataCompactionLevel(9));
    }

    @Test
    void getAvailableStorageSpace_nullContainer_throwsIllegalArgumentException() {
        StegPNG stegPNG = new StegPNG();
        assertThrows(IllegalArgumentException.class, () -> stegPNG.getAvailableStorageSpace((BufferedImage)null));
    }

    @Test
    void getAvailableStorageSpace_higherCompactionIncreasesCapacity() {
        BufferedImage container = createContainerImage(200, 120);
        StegPNG low = new StegPNG(1);
        StegPNG high = new StegPNG(6);

        int lowCapacity = low.getAvailableStorageSpace(container);
        int highCapacity = high.getAvailableStorageSpace(container);

        assertTrue(lowCapacity > 0);
        assertTrue(highCapacity > lowCapacity);
    }

    @Test
    void embedSecretMessage_stringNullContainer_throwsIllegalArgumentException() {
        StegPNG stegPNG = new StegPNG();
        assertThrows(IllegalArgumentException.class, () -> stegPNG.embedSecretMessage(null, "secret"));
    }

    @Test
    void embedSecretMessage_nullString_throwsIllegalArgumentException() {
        StegPNG stegPNG = new StegPNG();
        BufferedImage container = createContainerImage(100, 100);

        assertThrows(IllegalArgumentException.class, () -> stegPNG.embedSecretMessage(container, (String)null));
    }

    @Test
    void embedSecretMessage_fileNullArguments_throwsIllegalArgumentException() {
        StegPNG stegPNG = new StegPNG();
        BufferedImage container = createContainerImage(100, 100);
        File someFile = tempDir.resolve("some.dat").toFile();

        assertThrows(IllegalArgumentException.class, () -> stegPNG.embedSecretMessage(null, someFile));
        assertThrows(IllegalArgumentException.class, () -> stegPNG.embedSecretMessage(container, (File)null));
    }

    @Test
    void embedSecretMessage_fileMissing_throwsIOException() {
        StegPNG stegPNG = new StegPNG();
        BufferedImage container = createContainerImage(100, 100);
        File missing = tempDir.resolve("missing.dat").toFile();

        assertThrows(IOException.class, () -> stegPNG.embedSecretMessage(container, missing));
    }

    @Test
    void embedSecretMessage_stringInsufficientSpace_throwsInsufficientSpaceException() {
        StegPNG stegPNG = new StegPNG(1);
        BufferedImage tiny = createContainerImage(10, 10);
        String tooLarge = "This secret text is too large for a 10x10 image.";

        assertThrows(InsufficientSpaceException.class, () -> stegPNG.embedSecretMessage(tiny, tooLarge));
    }

    @Test
    void embedSecretMessage_fileInsufficientSpace_throwsInsufficientSpaceException() throws IOException {
        StegPNG stegPNG = new StegPNG(1);
        BufferedImage tiny = createContainerImage(10, 10);

        File secretFile = tempDir.resolve("too-large.bin").toFile();
        Files.write(secretFile.toPath(), buildBinaryPayload(128));

        assertThrows(InsufficientSpaceException.class, () -> stegPNG.embedSecretMessage(tiny, secretFile));
    }

    @Test
    void getStegInfo_nullImage_throwsIllegalArgumentException() {
        StegPNG stegPNG = new StegPNG();
        assertThrows(IllegalArgumentException.class, () -> stegPNG.getStegInfo((BufferedImage)null));
    }

    @Test
    void getStegInfo_nullOrMissingFile_throwsIllegalArgumentException() {
        StegPNG stegPNG = new StegPNG();

        assertThrows(IllegalArgumentException.class, () -> stegPNG.getStegInfo((File)null));
        assertThrows(IllegalArgumentException.class,
                     () -> stegPNG.getStegInfo(tempDir.resolve("missing.png").toFile()));
    }

    @Test
    void getStegInfo_cleanImage_throwsInvalidPayloadException() {
        StegPNG stegPNG = new StegPNG();
        BufferedImage cleanImage = createContainerImage(80, 80);

        assertThrows(InvalidPayloadException.class, () -> stegPNG.getStegInfo(cleanImage));
    }

    @Test
    void getStegInfo_headerWithWrongVersion_throwsInvalidVersionException() {
        StegPNG stegPNG = new StegPNG();
        BufferedImage image = createContainerImage(80, 80);

        byte[] header = buildHeaderBytes((byte)(StegPNG.MAJOR_VERSION + 1), StegPNG.MINOR_VERSION,
                                         (byte)2, PayloadType.BINARY, 16);
        writeHeaderWithCompactionLevelOne(image, header);

        assertThrows(InvalidVersionException.class, () -> stegPNG.getStegInfo(image));
    }

    @Test
    void getStegInfo_filePathReadsStegHeaderSuccessfully() throws Exception {
        StegPNG stegPNG = new StegPNG(2);
        BufferedImage container = createContainerImage(140, 140);
        String secret = "File based steg-info read.";

        BufferedImage stegged = stegPNG.embedSecretMessage(container, secret);
        File steggedFile = tempDir.resolve("stegged.png").toFile();
        ImageIO.write(stegged, "png", steggedFile);

        StegInfo info = stegPNG.getStegInfo(steggedFile);
        assertEquals(PayloadType.STRING, info.getPayloadType());
        assertEquals(secret.length() * 2, info.getDataSegmentLength());
        assertEquals(2, info.getDataCompactionLevel());
    }

    @Test
    void retrieveSecretMessage_nullArguments_throwsIllegalArgumentException() {
        StegPNG stegPNG = new StegPNG();
        BufferedImage container = createContainerImage(100, 100);
        File outputFile = tempDir.resolve("out.txt").toFile();

        assertThrows(IllegalArgumentException.class, () -> stegPNG.retrieveSecretMessage(null, outputFile));
        assertThrows(IllegalArgumentException.class, () -> stegPNG.retrieveSecretMessage(container, null));
    }

    @Test
    void retrieveSecretMessage_outputIsDirectory_throwsIllegalArgumentException() {
        StegPNG stegPNG = new StegPNG();
        BufferedImage stegged = createContainerImage(100, 100);

        assertThrows(IllegalArgumentException.class,
                     () -> stegPNG.retrieveSecretMessage(stegged, tempDir.toFile()));
    }

    @Test
    void retrieveSecretMessage_onCleanImage_throwsInvalidPayloadException() {
        StegPNG stegPNG = new StegPNG();
        BufferedImage cleanImage = createContainerImage(100, 100);
        File output = tempDir.resolve("clean-out.bin").toFile();

        assertThrows(InvalidPayloadException.class, () -> stegPNG.retrieveSecretMessage(cleanImage, output));
    }

    private byte[] buildBinaryPayload(int length) {
        byte[] payload = new byte[length];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte)((i * 31 + 7) & 0xFF);
        }
        return payload;
    }

    private BufferedImage createContainerImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = (x * 13 + y * 3) & 0xFF;
                int green = (x * 5 + y * 11) & 0xFF;
                int blue = (x * 17 + y * 7) & 0xFF;
                int rgb = (red << 16) | (green << 8) | blue;
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private byte[] buildHeaderBytes(byte majorVersion, byte minorVersion,
                                    byte compactionLevel, PayloadType payloadType,
                                    int messageLength) {
        byte[] data = new byte[StegInfo.HEADER_SIZE];
        data[0] = (byte)((StegInfo.MAGIC_NUMBER >> 8) & 0xFF);
        data[1] = (byte)(StegInfo.MAGIC_NUMBER & 0xFF);
        data[2] = majorVersion;
        data[3] = minorVersion;
        data[4] = compactionLevel;
        data[5] = payloadType.getValue();
        data[6] = (byte)((messageLength >> 24) & 0xFF);
        data[7] = (byte)((messageLength >> 16) & 0xFF);
        data[8] = (byte)((messageLength >> 8) & 0xFF);
        data[9] = (byte)(messageLength & 0xFF);
        return data;
    }

    /**
     * Mirrors StegPNG's fixed-level header encoding behavior so tests can inject specific header bytes.
     */
    private void writeHeaderWithCompactionLevelOne(BufferedImage image, byte[] headerBytes) {
        int x = 0;
        int y = 0;
        int tripletIndex = 0;
        int rgb = image.getRGB(x, y);

        for (byte currentByte : headerBytes) {
            for (int sourceBitIndex = 0; sourceBitIndex < 8; sourceBitIndex++) {
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                int sourceBit = (currentByte >> (7 - sourceBitIndex)) & 0x01;

                switch (tripletIndex) {
                    case 0 -> red = (red & 0xFE) | sourceBit;
                    case 1 -> green = (green & 0xFE) | sourceBit;
                    case 2 -> blue = (blue & 0xFE) | sourceBit;
                    default -> throw new IllegalStateException("Unexpected triplet index: " + tripletIndex);
                }

                rgb = ((alpha << 24) & 0xFF000000)
                        | ((red << 16) & 0xFF0000)
                        | ((green << 8) & 0xFF00)
                        | (blue & 0xFF);

                tripletIndex++;
                if (tripletIndex > 2) {
                    image.setRGB(x, y, rgb);
                    tripletIndex = 0;
                    x++;
                    if (x >= image.getWidth()) {
                        x = 0;
                        y++;
                        if (y >= image.getHeight()) {
                            fail("Test image too small to inject header bytes.");
                        }
                    }
                    rgb = image.getRGB(x, y);
                }
            }
        }

        image.setRGB(x, y, rgb);
    }
}