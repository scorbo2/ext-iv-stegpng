package ca.corbett.imageviewer.extensions.stegpng;

import ca.corbett.extras.MessageUtil;
import ca.corbett.extras.PopupTextDialog;
import ca.corbett.extras.TextInputDialog;
import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.ComboField;
import ca.corbett.forms.fields.ImageListField;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.forms.fields.NumberField;
import ca.corbett.forms.fields.PanelField;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

public class StegPNGDialog extends JDialog {

    private static final Logger log = Logger.getLogger(StegPNGDialog.class.getName());

    //  The source image, which we will treat as read-only until OK is clicked:
    private final ImageInstance sourceImageInstance;
    private final BufferedImage sourceImage;
    private final StegInfo sourceHeader;

    // The container image and header that we will modify as the user makes changes in the form.
    private BufferedImage currentImage;
    private StegInfo currentHeader;

    private final StegPNG steg;
    private MessageUtil messageUtil;
    private FormPanel formPanel;
    private ImageListField imageField;
    private LabelField versionField;
    private NumberField dataCompactionField;
    private ComboField<PayloadType> payloadTypeField;
    private LabelField payloadSizeField;
    private LabelField availableSpaceField;
    private PanelField embedMessageField;
    private PanelField retrieveMessageField;
    private JButton embedButton;
    private JButton retrieveButton;
    private boolean isModified;

    public StegPNGDialog(Window owner, ImageInstance image) {
        super(owner, "StegPNG", ModalityType.APPLICATION_MODAL);
        this.sourceImageInstance = image;
        this.sourceImage = image.getRegularImage(); // This is validated by the action that triggers us.
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(400, 600);
        setLocationRelativeTo(owner);
        steg = new StegPNG();
        StegInfo header;
        try {
            header = steg.getStegInfo(sourceImage);
        }
        catch (Exception e) {
            // This is not necessarily an error.
            // Almost certainly, it just means that our source image
            // does not contain a StegPNG payload, which is fine.
            header = null;
        }
        this.sourceHeader = header;
        copySourceImage();
        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
        isModified = false;
    }

    private FormPanel buildForm() {
        formPanel = new FormPanel(Alignment.TOP_CENTER);
        formPanel.setBorderMargin(12);

        formPanel.add(LabelField.createBoldHeaderLabel("StegPNG", 16));
        imageField = new ImageListField("", 1, 200)
                .addImage(sourceImage);
        imageField.getMargins().setLeft(32); // Indent it a bit
        imageField.setEnabled(false); // disables drag+drop and image removal options - this is a read-only preview
        imageField.getImageListPanel().setOwnerWindow(this); // to prevent wonky modal behavior
        formPanel.add(imageField);

        versionField = new LabelField("StegPNG version:",
                                      sourceHeader != null ? sourceHeader.getVersionString() : "(no payload found)");
        if (sourceHeader == null) {
            versionField.setEnabled(false);
        }
        formPanel.add(versionField);

        dataCompactionField = new NumberField("Data compaction level:",
                                              sourceHeader != null ? sourceHeader.getDataCompactionLevel() : 1,
                                              1, 8, 1);
        if (sourceHeader != null) {
            // If we have a valid header, disable editing of the compaction level,
            // since this is a read-only preview of an existing payload.
            dataCompactionField.setEnabled(false);
        }
        dataCompactionField.addValueChangedListener(f -> dataCompactionChanged());
        dataCompactionField.setHelpText("<html>How many bits per byte to use for storing the payload." +
                                                "<br><br>A value of 1 is safest, as it has only a very slight" +
                                                "<br>effect on the container image. But, it means that the" +
                                                "<br>payload will consume more pixels in the container." +
                                                "<br><br>A value of 8 will destroy all occupied pixels" +
                                                "<br>in the container image, but allows the payload" +
                                                "<br>to be stored in the smallest possible area of the container.</html>");
        formPanel.add(dataCompactionField);

        int index = sourceHeader != null ? sourceHeader.getPayloadType().ordinal() : 0;
        payloadTypeField = new ComboField<>("Payload type:", Arrays.asList(PayloadType.values()), index);
        if (sourceHeader != null) {
            // In read-only mode for an existing payload, prevent changing the payload type.
            payloadTypeField.setEnabled(false);
        }
        formPanel.add(payloadTypeField);

        payloadSizeField = new LabelField("Payload size:",
                                          sourceHeader != null
                                                  ? FileSystemUtil.getPrintableSize(sourceHeader.getDataSegmentLength())
                                                  : "(no payload found)");
        formPanel.add(payloadSizeField);

        long availableSpace = steg.getAvailableStorageSpace(sourceImage);
        availableSpaceField = new LabelField("Available space:",
                                             FileSystemUtil.getPrintableSize(availableSpace));
        formPanel.add(availableSpaceField);

        embedMessageField = new PanelField(new FlowLayout(FlowLayout.LEFT));
        JPanel panel = embedMessageField.getPanel();
        embedButton = new JButton("Embed secret message...");
        embedButton.addActionListener(e -> embedSecretMessage());
        panel.add(embedButton);
        if (currentHeader != null) {
            // If we have a valid header, hide the embed button,
            // because we are in a read-only mode for an existing payload.
            embedMessageField.setVisible(false);
        }
        formPanel.add(embedMessageField);

        retrieveMessageField = new PanelField(new FlowLayout(FlowLayout.LEFT));
        panel = retrieveMessageField.getPanel();
        retrieveButton = new JButton("Retrieve secret message...");
        retrieveButton.addActionListener(e -> retrieveSecretMessage());
        panel.add(retrieveButton);
        if (currentHeader == null) {
            // If we don't have a valid header, hide the retrieve button,
            // because there is no payload to retrieve.
            // Note that if we started with a stegged image, there's
            // no way to ever show the "embed" button. That's deliberate -
            // we don't offer a way to "un-steg" an image once it's stegged.
            // (although you could... just by overwriting the previous payload...)
            retrieveMessageField.setVisible(false);
        }
        formPanel.add(retrieveMessageField);

        return formPanel;
    }

    /**
     * Prompts the user for either simple String input, or to choose a file.
     * Will then encode and embed that message into our container image and
     * mark the form as having been modified. The "embed" button will be
     * hidden and the "retrieve" button will be shown.
     */
    private void embedSecretMessage() {
        try {
            BufferedImage newImage;
            if (payloadTypeField.getSelectedIndex() == 0) { // String contents
                String payload = TextInputDialog.showDialog(
                        this,
                        "Enter the secret message to embed:",
                        TextInputDialog.InputType.MultiLine,
                        false);
                if (payload == null) {
                    return; // user canceled the input dialog
                }

                // Validate the message and the container, and do the embed:
                newImage = steg.embedSecretMessage(currentImage, payload);
            }
            else { // Binary contents
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                fileChooser.setApproveButtonText("Embed");
                fileChooser.showOpenDialog(this);
                if (fileChooser.getSelectedFile() == null) {
                    return; // user canceled the file chooser
                }
                newImage = steg.embedSecretMessage(currentImage, fileChooser.getSelectedFile());
            }

            // Point currentImage to the newImage, and show it in our preview field:
            currentImage.flush();
            currentImage = newImage;
            imageField.clear();
            imageField.addImage(currentImage);

            // Try to read the header we just embedded:
            currentHeader = steg.getStegInfo(currentImage);

            // If we get here, no exceptions were raised:
            isModified = true;
            embedMessageField.setVisible(false);
            retrieveMessageField.setVisible(true);

            // Update the form to show the new header info:
            versionField.setText(currentHeader.getVersionString());
            dataCompactionField.setCurrentValue(currentHeader.getDataCompactionLevel());
            int index = currentHeader.getPayloadType().ordinal();
            payloadTypeField.setSelectedIndex(index);
            payloadSizeField.setText(FileSystemUtil.getPrintableSize(currentHeader.getDataSegmentLength()));
            dataCompactionField.setEnabled(false); // we're now read-only
            payloadTypeField.setEnabled(false); // we're now read-only
            dataCompactionChanged(); // force refresh of available space field
        }
        catch (Exception e) {
            // Generic catch-all handler:
            getMessageUtil().error("Error embedding message: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the secret message from our container image, and either
     * shows it in a text popup, or prompts the user to choose a save location
     * for it (if it was binary data). The form is NOT marked as modified,
     * since nothing changes as a result of this action. The "retrieve"
     * button remains visible, and the "embed" button remains hidden.
     */
    private void retrieveSecretMessage() {
        try {
            if (currentHeader.getPayloadType() == PayloadType.STRING) {
                String message = steg.retrieveSecretMessageAsString(currentImage);
                PopupTextDialog popup = new PopupTextDialog(this, "Secret message", message, true);
                popup.setReadOnly(true);
                popup.setVisible(true);
            }
            else { // Binary contents
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fileChooser.setApproveButtonText("Save");
                fileChooser.showSaveDialog(this);
                if (fileChooser.getSelectedFile() == null) {
                    return; // user canceled the file chooser
                }
                steg.retrieveSecretMessage(currentImage, fileChooser.getSelectedFile());
            }
        }
        catch (Exception e) {
            // Generic catch-all handler:
            getMessageUtil().error("Error retrieving message: " + e.getMessage(), e);
        }
    }

    /**
     * When the user changes the data compaction field, we need to update
     * the "available space" field, because the value there will change.
     */
    private void dataCompactionChanged() {
        int compactionLevel = dataCompactionField.getCurrentValue().intValue();
        steg.setDataCompactionLevel(compactionLevel);
        long availableSpace = steg.getAvailableStorageSpace(currentImage);
        availableSpaceField.setText(FileSystemUtil.getPrintableSize(availableSpace));
    }

    /**
     * Makes a copy of the source image so that we can manipulate it freely
     * as the user makes changes in the form, without affecting the original
     * source image until the user clicks OK.
     */
    private void copySourceImage() {
        if (currentImage != null) {
            currentImage.flush();
        }
        int imageType = sourceImage.getColorModel()
                                   .hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        currentImage = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), imageType);
        Graphics2D g = currentImage.createGraphics();
        try {
            g.drawImage(sourceImage, 0, 0, null);
        }
        finally {
            g.dispose();
        }
        try {
            currentHeader = steg.getStegInfo(currentImage);
        }
        catch (Exception e) {
            // This is not necessarily an error.
            // Almost certainly, it just means that our source image
            // does not contain a StegPNG payload, which is fine.
            currentHeader = null;
        }
    }

    /**
     * Resets the form back to the initial state, discarding any change
     * we have made so far. Has no effect on our sourceImage, which is read-only
     * until the user clicks OK.
     */
    private void reset() {
        copySourceImage(); // Make a fresh copy from our untouched source image
        imageField.clear();
        imageField.addImage(currentImage);
        versionField.setText(currentHeader != null ? currentHeader.getVersionString() : "(no payload found)");
        dataCompactionField.setCurrentValue(currentHeader != null ? currentHeader.getDataCompactionLevel() : 1);
        int index = currentHeader != null ? currentHeader.getPayloadType().ordinal() : 0;
        payloadTypeField.setSelectedIndex(index);
        payloadSizeField.setText(currentHeader != null
                                         ? FileSystemUtil.getPrintableSize(currentHeader.getDataSegmentLength())
                                         : "(no payload found)");
        dataCompactionChanged(); // force refresh of available space field

        // Some fields may have been marked as disabled if we embedded a message.
        // In that case, if we're reverting back to a state where there is no embedded
        // message, then we want to re-enable those fields:
        boolean hasMessage = currentHeader != null;
        dataCompactionField.setEnabled(!hasMessage);
        payloadTypeField.setEnabled(!hasMessage);

        // Also set our action buttons to visible or invisible as needed:
        embedMessageField.setVisible(!hasMessage);
        retrieveMessageField.setVisible(hasMessage);

        // Un-mark ourselves as modified so we don't prompt to save changes now that there are none:
        isModified = false;
    }

    /**
     * Invoked from Ok or Cancel. Will handle confirming with the user if there are unsaved
     * changes, and will save the current image overtop of our source image if so.
     * Closes and disposes the dialog if all goes well.
     *
     * @param isOkay Was this an "okay" or a "cancel" action (cancel just disposes).
     */
    private void buttonHandler(boolean isOkay) {
        if (isOkay) {
            if (!formPanel.isFormValid()) {
                // formPanel is showing validation errors, user must deal with them.
                return;
            }
            if (isModified) {
                if (getMessageUtil().askYesNo("Confirm", "Save changes and close?") == MessageUtil.NO) {
                    return; // Form stays open until user confirms or cancels
                }

                // If we get here, the form is modified, and the user is okay with the changes.
                // So, we will save the changes, and overwrite our source image.
                try {
                    ImageUtil.savePngImage(currentImage, sourceImageInstance.getImageFile());
                    sourceImage.flush();
                    currentImage.flush();
                    MainWindow.getInstance().reloadCurrentImage();
                }
                catch (IOException ioe) {
                    getMessageUtil().error("Error saving image: " + ioe.getMessage());
                    return; // Form stays open so user can try again or cancel.
                }
            }
        }

        else {
            if (isModified) {
                if (getMessageUtil().askYesNo("Confirm", "Discard changes and close?") == MessageUtil.NO) {
                    return; // Form stays open until user confirms
                }
            }
        }

        dispose();
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        panel.setBorder(BorderFactory.createRaisedBevelBorder());
        panel.add(buildButton("Reset", e -> reset()));
        panel.add(buildButton("OK", e -> buttonHandler(true)));
        panel.add(buildButton("Cancel", e -> buttonHandler(false)));
        return panel;
    }

    private JButton buildButton(String label, ActionListener handler) {
        JButton button = new JButton(label);
        button.setPreferredSize(new Dimension(100, 24));
        button.addActionListener(handler);
        return button;
    }

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(this, log);
        }
        return messageUtil;
    }
}
