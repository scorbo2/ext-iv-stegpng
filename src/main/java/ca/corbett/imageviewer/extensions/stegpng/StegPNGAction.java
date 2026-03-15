package ca.corbett.imageviewer.extensions.stegpng;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import java.awt.event.ActionEvent;

/**
 * A simple action to open our StegPNGDialog with the currently selected image.
 * The selected image must be a PNG image.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class StegPNGAction extends EnhancedAction {

    private static final String NAME = "StegPNG";

    public StegPNGAction() {
        super(NAME);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ImageInstance image = MainWindow.getInstance().getSelectedImage();
        if (image.isEmpty()) {
            MainWindow.getInstance().showMessageDialog("Image information", "No image selected.");
            return;
        }
        if (!image.isRegularImage() || !image.getImageFile().getName().toLowerCase().endsWith(".png")) {
            MainWindow.getInstance().showMessageDialog(NAME, "StegPNG only supports PNG images.");
            return;
        }

        StegPNGDialog dialog = new StegPNGDialog(MainWindow.getInstance(), image);
        dialog.setVisible(true);
    }
}
