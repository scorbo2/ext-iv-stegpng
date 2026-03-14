package ca.corbett.imageviewer.extensions.stegpng;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;

import java.util.List;
import java.util.logging.Logger;

/**
 * This is an ImageViewer extension that contains code ported from the very old (2004 or so) StegPNG
 * application that I wrote a long time ago. It allows you to steganographically embed messages into
 * a PNG container image, without making it visually obvious that the image has been tampered with.
 * This was a fun academic project long ago, and I thought it would make for a neat extension to
 * ImageViewer. It is not intended for any practical use, and is really just a fun demonstration of
 * steganographic techniques. StegPNG does not come with any warrantee, nor guarantee of applicability
 * for any particular purpose. It is not intended for serious use - more of an academic exercise.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 2004-10-17 originally! But not updated for over two decades, until 2026-03-14.
 */
public class StegPNGExtension extends ImageViewerExtension {

    private static final Logger log = Logger.getLogger(StegPNGExtension.class.getName());
    private static final String extInfoLocation = "/ca/corbett/imageviewer/extensions/stegpng/extInfo.json";

    private final AppExtensionInfo extInfo;

    public StegPNGExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(), extInfoLocation);
        if (extInfo == null) {
            throw new RuntimeException("StegPNGExtension: can't parse extInfo.json!");
        }

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
        // TODO a keyboard shortcut for launching our dialog would be nice.
        // TODO any other general options?
        return List.of();
    }

    @Override
    protected void loadJarResources() {
        // Nothing to load here yet.
    }
}
