package ca.corbett.imageviewer.extensions.stegpng;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.EnhancedAction;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.KeyStrokeProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.ui.MainWindow;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * This is an ImageViewer extension that contains code ported from the very old (2004 or so) StegPNG
 * application that I wrote a long time ago. It allows you to steganographically embed messages into
 * a PNG container image, without making it visually obvious that the image has been tampered with.
 * This was a fun academic project long ago, and I thought it would make for a neat extension to
 * ImageViewer. It is not intended for any practical use, and is really just a fun demonstration of
 * steganographic techniques. StegPNG does not come with any warranty, nor guarantee of applicability
 * for any particular purpose. It is not intended for serious use - more of an academic exercise.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 2004-10-17 originally! But not updated for over two decades, until 2026-03-14.
 */
public class StegPNGExtension extends ImageViewerExtension {

    private static final Logger log = Logger.getLogger(StegPNGExtension.class.getName());
    private static final String extInfoLocation = "/ca/corbett/imageviewer/extensions/stegpng/extInfo.json";
    private static final String KEY_PROP = AppConfig.KEYSTROKE_MISC_PREFIX + "stegPNG";

    private final AppExtensionInfo extInfo;
    private final StegPNGAction stegAction;

    public StegPNGExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(), extInfoLocation);
        if (extInfo == null) {
            throw new RuntimeException("StegPNGExtension: can't parse extInfo.json!");
        }
        this.stegAction = new StegPNGAction();
    }

    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
    }

    @Override
    public void onActivate() {
        // Nothing to do here yet.
    }

    @Override
    public void onDeactivate() {
        // Any cleanup will go here...
    }

    @Override
    protected List<AbstractProperty> createConfigProperties() {
        List<AbstractProperty> props = new ArrayList<>();
        props.add(new KeyStrokeProperty(KEY_PROP,
                                        "StegPNG:",
                                        KeyStrokeManager.parseKeyStroke("Ctrl+Alt+S"),
                                        stegAction)
                          .setAllowBlank(true)
                          .setReservedKeyStrokes(AppConfig.RESERVED_KEYSTROKES)
                          .setHelpText("Show the StegPNG dialog for the current image"));
        return props;
    }

    @Override
    protected void loadJarResources() {
        // Nothing to load here yet.
    }

    @Override
    public List<EnhancedAction> getMenuActions(String menu, MainWindow.BrowseMode browseMode) {
        if (!"Edit".equals(menu)) {
            return null;
        }

        List<EnhancedAction> actions = new ArrayList<>();
        actions.add(stegAction);
        return actions;
    }

    @Override
    public List<EnhancedAction> getPopupMenuActions(MainWindow.BrowseMode browseMode) {
        List<EnhancedAction> items = new ArrayList<>();
        items.add(stegAction);
        return items;
    }
}
