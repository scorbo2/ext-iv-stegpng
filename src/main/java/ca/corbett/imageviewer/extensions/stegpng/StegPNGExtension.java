package ca.corbett.imageviewer.extensions.stegpng;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;

import java.util.List;
import java.util.logging.Logger;

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
    protected List<AbstractProperty> createConfigProperties() {
        return List.of();
    }

    @Override
    protected void loadJarResources() {
        // Nothing to load here.
    }
}
