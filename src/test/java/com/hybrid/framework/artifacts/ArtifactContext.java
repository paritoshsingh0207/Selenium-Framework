package com.hybrid.framework.artifacts;

import java.nio.file.Path;

public final class ArtifactContext {
    private static final ThreadLocal<Path> SCREENSHOT = new ThreadLocal<>();

    private ArtifactContext() {
    }

    public static void setScreenshot(Path path) { SCREENSHOT.set(path); }
    public static Path screenshot() { return SCREENSHOT.get(); }
    public static void clear() { SCREENSHOT.remove(); }
}
