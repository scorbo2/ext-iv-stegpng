package ca.corbett.imageviewer.extensions.stegpng;


import ca.corbett.extras.io.FileSystemUtil;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * This is the utility class containing methods for steganographic
 * encoding and decoding of messages into container PNG images.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 2004-10-17 originally! But not updated for over two decades, until 2026-03-14.
 */
public final class StegPNG {

    /** Current major version number. */
    public static final byte MAJOR_VERSION = 3;

    /** Current minor version number. */
    public static final byte MINOR_VERSION = 0;

    /** Current version in the form of a user-displayable string. */
    public static final String VERSION = MAJOR_VERSION + "." + MINOR_VERSION;

    private int dataCompactionLevel;

    /**
     * Creates a new instance of this class with all properties
     * assuming their default values.
     */
    public StegPNG() {
        this(1);
    }

    /**
     * Creates a new instance of this class with the specified
     * data compaction level (see setDataCompactionLevel()).
     *
     * @param dataCompactionLevel see setDataCompactionLevel for description.
     */
    public StegPNG(int dataCompactionLevel) {
        setDataCompactionLevel(dataCompactionLevel);
    }

    /**
     * Sets the number of bits in every byte that will be used by StegPNG
     * to store the secret message.  The default value is 1.  The maximum
     * value is 8, meaning the original image contents will be completely
     * overwritten with the secret message.  Values higher than 2 or 3
     * will start to produce visible artifacts in the image, and values
     * higher than 5 or 6 will all but destroy the original image.
     * It is generally advisable to keep this number low unless you really
     * need to ramp it up.  The advantage of increasing this number is
     * that you can fit more secret data inside the image - but it is
     * a trade-off of storage space versus image degradation.
     * <p>
     * Values lower than 1 or higher than 8 will generate an IllegalArgumentException.
     * </p>
     *
     * @param dataCompactionLevel A value between 1 and 8.
     */
    public void setDataCompactionLevel(int dataCompactionLevel) {
        if (dataCompactionLevel < 1 || dataCompactionLevel > 8) {
            throw new IllegalArgumentException("Invalid data compaction level: " + dataCompactionLevel);
        }
        this.dataCompactionLevel = dataCompactionLevel;
    }

    /**
     * Embeds the given string inside the given image and returns the resulting
     * image.
     *
     * @param containerImage The image which will contain the secret message.
     * @param secretMessage  The message to embed, in String form.
     * @return A new BufferedImage containing the results.
     * @throws InsufficientSpaceException if the container lacks the space to hold the message.
     * @throws IOException                on file read problem.
     * @throws IllegalArgumentException   if any parameter is null.
     */
    public BufferedImage embedSecretMessage(BufferedImage containerImage, String secretMessage)
            throws InsufficientSpaceException, IOException {
        byte[] secretMessageData = convertStringToByteArray(secretMessage);
        return embedSecretMessage(containerImage, secretMessageData, PayloadType.STRING);
    }


    /**
     * Embeds the contents of the given file inside the given image and returns
     * the resulting image.
     *
     * @param containerImage    The image which will contain the secret message.
     * @param secretMessageFile The file containing the secret message data.
     * @return A new BufferedImage containing the results.
     * @throws InsufficientSpaceException if the container lacks the space to hold the message.
     * @throws IOException                on file read problem.
     * @throws IllegalArgumentException   if any parameter is null.
     */
    public BufferedImage embedSecretMessage(BufferedImage containerImage, File secretMessageFile)
            throws InsufficientSpaceException, IOException {
        if (containerImage == null || secretMessageFile == null) {
            throw new IllegalArgumentException("Container image and secret message file cannot be null.");
        }
        if (!secretMessageFile.exists() || !secretMessageFile.isFile() || !secretMessageFile.canRead()) {
            throw new IOException("Invalid, missing, or unreadable input file: " + secretMessageFile.getAbsolutePath());
        }
        if (getAvailableStorageSpace(containerImage) < secretMessageFile.length()) {
            throw new InsufficientSpaceException();
        }

        // Get this file's contents:
        byte[] fileData;
        try (BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(secretMessageFile))) {
            fileData = new byte[(int)secretMessageFile.length()];
            int bytesRead = inStream.read(fileData);
            if (bytesRead != fileData.length) {
                throw new IOException("Could not read the entire file: " + secretMessageFile.getAbsolutePath());
            }
        }

        // Embed this message and return:
        return embedSecretMessage(containerImage, fileData, PayloadType.BINARY);
    }


    /**
     * Attempts to parse a StegInfo object from the given file.
     *
     * @param inputImage The candidate container file to be analyzed.
     * @return A StegInfo instance containing information about the container.
     * @throws IOException              on file read problem.
     * @throws InvalidVersionException  if the version information in the header is not recognized.
     * @throws InvalidPayloadException  if the payload type information in the header is not recognized.
     * @throws IllegalArgumentException if the given file is null, does not exist, or is not a readable file.
     */
    public StegInfo getStegInfo(File inputImage) throws IOException, InvalidVersionException, InvalidPayloadException {
        if (inputImage == null || !inputImage.exists() || !inputImage.isFile() || !inputImage.canRead()) {
            throw new IllegalArgumentException("Invalid, missing, or unreadable input file: "
                                                       + (inputImage != null ? inputImage.getAbsolutePath() : "null"));
        }
        BufferedImage containerImage = null;
        try {
            containerImage = ImageIO.read(inputImage);
            return getStegInfo(containerImage);
        }
        finally {
            if (containerImage != null) {
                containerImage.flush();
            }
        }
    }


    /**
     * Attempts to parse a StegInfo object from the given file.
     *
     * @param containerImage The candidate container image to inspect
     * @return A StegInfo instance containing information about the container.
     * @throws InvalidVersionException  if the version information in the header is not recognized.
     * @throws InvalidPayloadException  if the payload type information in the header is not recognized.
     * @throws IllegalArgumentException if the given containerImage is null.
     */
    public StegInfo getStegInfo(BufferedImage containerImage) throws InvalidPayloadException, InvalidVersionException {
        if (containerImage == null) {
            throw new IllegalArgumentException("Container image cannot be null.");
        }
        return readStegHeader(containerImage);
    }

    /**
     * Attempts to retrieve an embedded StegPNG payload from the given steggedImage,
     * and write it to the given output file (will overwrite if file exists).
     *
     * @param steggedImage The container image containing the secret.
     * @param outputFile   The file to receive the extracted secret.
     * @throws IOException              on file read/write problem.
     * @throws InvalidVersionException  if the version information in the header is not recognized.
     * @throws InvalidPayloadException  if the StegPNG header information looks incorrect.
     * @throws IllegalArgumentException if any parameter is null, or if outputFile is not a writable file.
     */
    public void retrieveSecretMessage(BufferedImage steggedImage, File outputFile)
            throws IOException, InvalidVersionException, InvalidPayloadException {
        if (steggedImage == null) {
            throw new IllegalArgumentException("Stegged image cannot be null.");
        }
        if (outputFile == null || (outputFile.exists() && (!outputFile.isFile() || !outputFile.canWrite()))) {
            throw new IllegalArgumentException("Output file must be a writable file: "
                                                       + (outputFile != null ? outputFile.getAbsolutePath() : "null"));
        }

        // Parse the header (this will validate the header and throw if it looks wonky):
        StegInfo stegInfo = readStegHeader(steggedImage);

        // Get secret message:
        byte[] secretMessage = readStegData(steggedImage, stegInfo);

        // string?
        if (stegInfo.getPayloadType() == PayloadType.STRING) {
            FileSystemUtil.writeStringToFile(convertByteArrayToString(secretMessage), outputFile);
        }

        // binary data?
        else {
            try (BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                outStream.write(secretMessage);
            }
        }
    }


    /**
     * Returns the total amount of secret data, in bytes, that can
     * be stored in the specified image using the currently configured
     * data compaction level. Use setDataCompactionLevel() to adjust the
     * data compaction level, which will affect the amount of storage space available.
     *
     * @param candidateImage The container image to check.
     * @return The available storage space, in bytes, using the current data compaction level.
     * @throws IllegalArgumentException if the given candidateImage is null.
     */
    public int getAvailableStorageSpace(BufferedImage candidateImage) {
        if (candidateImage == null) {
            throw new IllegalArgumentException("Candidate image cannot be null.");
        }
        
        // Pixels reserved for the header (always written at compaction level 1):
        int headerPixels = (int)Math.ceil(StegInfo.HEADER_SIZE * 8 / 3.0); // = 27

        // The pixels we have available are all the ones not occupied by header bytes:
        int availablePixels = candidateImage.getWidth() * candidateImage.getHeight() - headerPixels;

        // Each pixel has three bytes we can work with, and we can use dataCompactionLevel
        // bits in each of those bytes, so the total number of bits we can use is:
        return (availablePixels * 3 * dataCompactionLevel) / 8;
    }

    /**
     * Internal utility method to embed the given secret message
     * into the given image.
     *
     * @param containerImage    The container image.
     * @param secretMessageData A byte array containing the secret to embed.
     * @param payloadType       The type of data being embedded (string or binary).
     * @throws InsufficientSpaceException if the container lacks the space to hold the message.
     * @throws IllegalArgumentException   if any parameter is null.
     */
    private BufferedImage embedSecretMessage(BufferedImage containerImage,
                                             byte[] secretMessageData, PayloadType payloadType)
            throws InsufficientSpaceException {
        if (containerImage == null) {
            throw new IllegalArgumentException("Container image cannot be null.");
        }
        if (secretMessageData == null || secretMessageData.length == 0) {
            throw new IllegalArgumentException("Secret message data cannot be null or empty.");
        }
        if (payloadType == null) {
            throw new IllegalArgumentException("Payload type cannot be null.");
        }

        // Verify there's enough space for this message at our current data compaction level:
        if (getAvailableStorageSpace(containerImage) < secretMessageData.length) {
            throw new InsufficientSpaceException();
        }

        // Create a new, blank image:
        BufferedImage stegImage = new BufferedImage(containerImage.getWidth(),
                                                    containerImage.getHeight(), BufferedImage.TYPE_INT_RGB);

        // Copy the container image data to the new image:
        Graphics2D g = stegImage.createGraphics();
        try {
            g.drawImage(containerImage, 0, 0, null);
        }
        finally {
            g.dispose();
        }

        // Write the header information which we'll need later to desteg the image:
        writeStegHeader(stegImage, new StegInfo((byte)dataCompactionLevel, payloadType, secretMessageData.length));

        // Now write the actual secret message:
        writeStegData(stegImage, secretMessageData);

        // Return the newly stegged image:
        return stegImage;
    }


    /**
     * Internal utility method to write the specified StegInfo object as a header
     * into the specified image.  The header is stegged into the image using
     * a fixed data compaction level of 1, regardless of the data compaction level
     * that will be used to steg in the actual secret message.  The reason we do
     * this is so that we can safely assume that we know how to read out the header
     * when we go to desteg the image.
     *
     * @param stegImage  The destination image.
     * @param headerInfo The StegInfo object to be written out.
     * @throws InsufficientSpaceException if the image is too small to hold the header information.
     * @throws IllegalArgumentException   if any parameter is null.
     */
    private void writeStegHeader(BufferedImage stegImage, StegInfo headerInfo) throws InsufficientSpaceException {
        if (stegImage == null) {
            throw new IllegalArgumentException("Steg image cannot be null.");
        }
        if (headerInfo == null) {
            throw new IllegalArgumentException("Header info cannot be null.");
        }
        // Write the header starting at index 0:
        writeStegData(stegImage, 1, headerInfo.toByteArray(), 0);
    }

    /**
     * Steganographically encodes the given message into the given container image
     * using our currently-configured data compaction level, and returns the resulting image.
     * <B>NOTE:</B> we assume here that the header has already been written! We just write the data!
     * <p>
     * <b>Implementation note:</b>
     * We write the data starting at the end of the header information.
     * We calculate the end-of-header mark to be Math.ceil((HEADER_SIZE*8)/3);
     * We multiply by 8 because there are 8 bits per byte of header info.
     * The division by three is required because there are three bytes per
     * pixel, and the Math.ceil is required to round up to the next available
     * pixel. We are screwing ourselves out of potentially 1 or 2 bytes by
     * doing this, depending on how big the header is, but I consider
     * it an acceptable loss.  The alternative would be to calculate the exact
     * pixel index and triplet index and hand that to the writeStegData method,
     * but for an extra couple of bytes it doesn't seem worth the effort.
     * </p>
     *
     * @param containerImage    The container image.
     * @param secretMessageData A byte array containing the secret message
     * @throws InsufficientSpaceException if the container lacks the space to hold the message.
     */
    private void writeStegData(BufferedImage containerImage, byte[] secretMessageData)
            throws InsufficientSpaceException {
        writeStegData(containerImage, dataCompactionLevel, secretMessageData,
                      (int)Math.ceil(StegInfo.HEADER_SIZE * 8 / 3.0));
    }

    /**
     * Attempts to read a StegInfo header object out of the given stegged image.
     * <p>
     * The header is always stegged in with a data compaction level of 1,
     * regardless of the data compaction level of the secret message.
     * This is so that we can always safely read the header even before
     * knowing the data compaction level of the rest of the secret message.
     * </p>
     *
     * @param stegImage The stegged image to read the header from.
     * @return A StegInfo object populated with information from the image.
     * @throws InvalidVersionException  if the version information in the header is not recognized.
     * @throws InvalidPayloadException  if the payload type information in the header is not recognized.
     * @throws IllegalArgumentException if the given stegImage is null.
     */
    private StegInfo readStegHeader(BufferedImage stegImage)
            throws InvalidPayloadException, InvalidVersionException {
        if (stegImage == null) {
            throw new IllegalArgumentException("Steg image cannot be null.");
        }

        // Read from index 0 until we have HEADER_SIZE bytes.
        byte[] header = readStegData(stegImage, 1, 0, StegInfo.HEADER_SIZE);

        // Create and return the StegInfo instance, if it passes validation:
        return new StegInfo(header);
    }

    /**
     * Internal utility method to read the secret message out of the specified
     * image using the specified StegInfo object.
     * <p>
     * <b>Implementation note:</b>
     * We write the data starting at the end of the header information.
     * We calculate the end-of-header mark to be Math.ceil((HEADER_SIZE*8)/3);
     * We multiply by 8 because there are 8 bits per byte of header info.
     * The division by three is required because there are three bytes per
     * pixel, and the Math.ceil is required to round up to the next available
     * pixel. We are screwing ourselves out of potentially 1 or 2 bytes by
     * doing this, depending on how big the header is, but I consider
     * it an acceptable loss.  The alternative would be to calculate the exact
     * pixel index and triplet index and hand that to the writeStegData method,
     * but for an extra couple of bytes it doesn't seem worth the effort.
     * </p>
     *
     * @param stegImage  The container image.
     * @param headerInfo The headerInfo containing information about the image.
     * @return A byte array containing the secret message from the image.
     * @throws InvalidPayloadException  if the data segment appears corrupt.
     * @throws IllegalArgumentException if any argument is null.
     */
    private byte[] readStegData(BufferedImage stegImage, StegInfo headerInfo) throws InvalidPayloadException {
        if (stegImage == null) {
            throw new IllegalArgumentException("Steg image cannot be null.");
        }
        if (headerInfo == null) {
            throw new IllegalArgumentException("Header info cannot be null.");
        }
        return readStegData(stegImage, headerInfo.getDataCompactionLevel(),
                            (int)Math.ceil(StegInfo.HEADER_SIZE * 8 / 3.0),
                            headerInfo.getDataSegmentLength());
    }

    /**
     * Internal utility method to steganographically encode the specified data array into the specified
     * image, starting at the specified pixel index.
     *
     * @param stegImage       The container image.
     * @param compactionLevel see setDataCompactionLevel for a description.
     * @param secretMessage   A byte array containing the secret to embed.
     * @param startingIndex   An offset into the pixel array of the image.
     * @throws InsufficientSpaceException if the container lacks the space to hold the message.
     * @throws IllegalArgumentException   if the given stegImage is null, compactionLevel is wrong, or startIndex is wonky.
     */
    private void writeStegData(BufferedImage stegImage, int compactionLevel,
                               byte[] secretMessage, int startingIndex) throws InsufficientSpaceException {
        if (stegImage == null) {
            throw new IllegalArgumentException("Steg image cannot be null.");
        }
        if (secretMessage == null || secretMessage.length == 0) {
            throw new IllegalArgumentException("Secret message cannot be null or empty.");
        }
        if (compactionLevel < 1 || compactionLevel > 8) {
            throw new IllegalArgumentException("Invalid data compaction level: " + compactionLevel);
        }
        if (startingIndex < 0) {
            throw new IllegalArgumentException("Starting index cannot be negative: " + startingIndex);
        }

        // state variables:
        int currentX = 0;
        int currentY = 0;
        int targetTripletIndex = 0;
        int targetBitIndex = 0;

        // Convert starting index into an x,y pair:
        if (startingIndex > 0) {
            while (startingIndex >= stegImage.getWidth()) {
                startingIndex -= stegImage.getWidth();
                currentY++;
            }
            currentX = startingIndex;
        }

        // Get first pixel:
        int rgb = stegImage.getRGB(currentX, currentY);

        // Loop for every byte of data:
        for (byte thisData : secretMessage) {
            // Get current byte:
            // Loop for every bit of this byte:
            for (int sourceBitIndex = 0; sourceBitIndex < 8; sourceBitIndex++) {
                // Parse the current pixel into components:
                byte alpha = (byte)((rgb >> 24) & 0xFF);
                byte red = (byte)((rgb >> 16) & 0xFF);
                byte green = (byte)((rgb >> 8) & 0xFF);
                byte blue = (byte)(rgb & 0xFF);

                // Get the target byte out of the current pixel:
                byte targetByte = switch (targetTripletIndex) {
                    case 0 -> red;
                    case 1 -> green;
                    case 2 -> blue;
                    default -> 0;
                };

                // Get the exact bit we're looking at in the current source byte:
                byte sourceBit = (byte)((thisData >> (7 - sourceBitIndex)) & 0x01);

                // Overwrite the targeted area of the target byte:
                byte maskedByte = (byte)(((targetByte & 0xFF) &
                        (0xFF ^ (1 << targetBitIndex))) & 0xFF);
                byte newTargetByte = (byte)(((maskedByte & 0xFF) |
                        ((sourceBit & 0x01) << targetBitIndex)) & 0xFF);

                // Insert this new byte back into the current triplet:
                switch (targetTripletIndex) {
                    case 0: {
                        rgb = ((alpha << 24) & 0xFF000000) |
                                ((newTargetByte << 16) & 0xFF0000) |
                                ((green << 8) & 0xFF00) |
                                (blue & 0xFF);
                    }
                    break;

                    case 1: {
                        rgb = ((alpha << 24) & 0xFF000000) |
                                ((red << 16) & 0xFF0000) |
                                ((newTargetByte << 8) & 0xFF00) |
                                (blue & 0xFF);
                    }
                    break;

                    case 2: {
                        rgb = ((alpha << 24) & 0xFF000000) |
                                ((red << 16) & 0xFF0000) |
                                ((green << 8) & 0xFF00) |
                                (newTargetByte & 0xFF);
                    }
                    break;
                }

                // Move to next bit in current byte of current triplet:
                targetBitIndex++;

                // If past the maximum allowed amount of overwrite...
                // note we use the supplied compaction level, not the class property!
                if (targetBitIndex >= compactionLevel) {
                    // Move to 0th bit of next byte in this triplet.
                    targetBitIndex = 0;
                    targetTripletIndex++;

                    // If past the end of this triplet...
                    if (targetTripletIndex > 2) {
                        // Save the current pixel:
                        targetTripletIndex = 0;
                        stegImage.setRGB(currentX, currentY, rgb);
                        currentX++;
                        if (currentX >= stegImage.getWidth()) {
                            currentX = 0;
                            currentY++;
                            if (currentY >= stegImage.getHeight()) {
                                // We validate this beforehand, so it *shouldn't* ever happen, but...
                                throw new InsufficientSpaceException();
                            }
                        }

                        // Get the next pixel:
                        rgb = stegImage.getRGB(currentX, currentY);
                    }
                }
            }
        }

        // Ensure the last one gets set (not guaranteed if the above loop
        // runs out of data in the middle of a triplet... we only commit
        // at the end of each triplet)
        stegImage.setRGB(currentX, currentY, rgb);
    }


    /**
     * Internal utility method to extract an embedded secret message from
     * the given stegged image using the given StegInfo object as a guideline.
     *
     * @param stegImage       The container image
     * @param compactionLevel see setDataCompactionLevel for a description
     * @param startingIndex   An offset into the pixel array of the image.
     * @param bytesToRead     A limit to the number of bytes that will be read.
     * @return A byte array containing the secret message.
     * @throws InvalidPayloadException  if the data segment appears corrupt.
     * @throws IllegalArgumentException on null or invalid arguments.
     */
    private byte[] readStegData(BufferedImage stegImage, int compactionLevel,
                                int startingIndex, int bytesToRead) throws InvalidPayloadException {
        if (stegImage == null) {
            throw new IllegalArgumentException("Steg image cannot be null.");
        }
        if (compactionLevel < 1 || compactionLevel > 8) {
            throw new IllegalArgumentException("Invalid data compaction level: " + compactionLevel);
        }
        if (startingIndex < 0) {
            throw new IllegalArgumentException("Starting index cannot be negative: " + startingIndex);
        }
        if (bytesToRead < 1) {
            throw new IllegalArgumentException("Bytes to read must be positive: " + bytesToRead);
        }
        byte[] data = new byte[bytesToRead];
        int currentIndex = 0;

        // state variables:
        int currentX = 0;
        int currentY = 0;
        int tripletIndex = 0;
        int tripletBitIndex = 0;

        // Convert starting index into an x,y pair:
        if (startingIndex > 0) {
            while (startingIndex >= stegImage.getWidth()) {
                startingIndex -= stegImage.getWidth();
                currentY++;
            }
            currentX = startingIndex;
        }

        // Get first pixel:
        int rgb = stegImage.getRGB(currentX, currentY);

        // Loop until we've read as many bytes as we're supposed to read:
        do {
            // Allocate one byte to reconstruct:
            byte reconstructedByte = 0;

            // Reconstruct this byte one bit at a time:
            for (int reconstructedBitIndex = 0; reconstructedBitIndex < 8; reconstructedBitIndex++) {
                // Get components of current pixel:
                byte red = (byte)((rgb >> 16) & 0xFF);
                byte green = (byte)((rgb >> 8) & 0xFF);
                byte blue = (byte)(rgb & 0xFF);

                // Figure out which of the above bytes we're currently looking at:
                byte currentTripletByte = switch (tripletIndex) {
                    case 0 -> (byte)(red & 0xFF);
                    case 1 -> (byte)(green & 0xFF);
                    case 2 -> (byte)(blue & 0xFF);
                    default -> 0;
                };

                // Now figure out which bit of the above byte holds the data we need:
                byte currentTripletBit = (byte)((currentTripletByte >> tripletBitIndex) & 0x01);

                // Or this new bit into our reconstructed byte at the appropriate position:
                reconstructedByte = (byte)(reconstructedByte |
                        (currentTripletBit << (7 - reconstructedBitIndex)));

                // Move to the next bit in the current triplet byte:
                tripletBitIndex++;

                // If we have exhausted this byte...
                // note we use supplied compaction level, not class property!
                if (tripletBitIndex >= compactionLevel) {
                    // Move to next byte in the triplet:
                    tripletBitIndex = 0;
                    tripletIndex++;

                    // If we have exhausted the triplet...
                    if (tripletIndex > 2) {
                        // Move to the next pixel in this row:
                        tripletIndex = 0;
                        currentX++;

                        // If we have exhausted this row...
                        if (currentX >= stegImage.getWidth()) {
                            // Move to the next row:
                            currentX = 0;
                            currentY++;

                            // If we have exhausted the image, something is wrong.  This
                            // shouldn't be possible, as we check for this before this
                            // method is even called, but let's play safe (could be a
                            // corrupt image or something):
                            if (currentY >= stegImage.getHeight()) {
                                throw new InvalidPayloadException("The data segment proceeded past the end " +
                                                                          "of the image. This image is either corrupt " +
                                                                          "or was never stegged in the first place.");
                            }
                        }

                        // Get a handle on the newly current pixel:
                        rgb = stegImage.getRGB(currentX, currentY);
                    }
                }
            }

            // Add the newly reconstructed byte to the data array:
            data[currentIndex] = reconstructedByte;

            // Move to the next index in the data array:
            currentIndex++;

        } while (currentIndex < bytesToRead);

        return data;
    }

    /**
     * Internal utility method to convert a Java Unicode String into an
     * array of bytes.
     */
    private byte[] convertStringToByteArray(String string) {
        // dummy check:
        if (string == null) { return new byte[0]; }

        // Convert string to char array:
        char[] charArray = string.toCharArray();

        // Convert the char array to a byte array:
        byte[] byteArray = new byte[charArray.length * 2];
        for (int i = 0; i < byteArray.length; i += 2) {
            byteArray[i] = (byte)((charArray[(i / 2)] >> 8) & 0xFF);
            byteArray[i + 1] = (byte)(charArray[(i / 2)] & 0xFF);
        }

        return byteArray;
    }

    /**
     * Internal utility method to convert a byte array to a Java Unicode String.
     */
    private String convertByteArrayToString(byte[] byteArray) {
        // dummy check:
        if (byteArray == null) { return ""; }

        // Convert byte array to char array:
        char[] charArray = new char[(int)(byteArray.length / 2)];
        for (int i = 0; i < charArray.length; i++) {
            byte firstByte = byteArray[i * 2];
            byte secondByte = byteArray[i * 2 + 1];
            charArray[i] = (char)(((firstByte << 8) & 0xFF00) | (secondByte & 0x00FF));
        }

        return new String(charArray);
    }
}


