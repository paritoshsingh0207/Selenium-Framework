package com.hybrid.framework.artifacts;

import com.hybrid.framework.config.FrameworkConfig;
import com.hybrid.framework.core.UiDriver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ScreenshotManager {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private ScreenshotManager() {
    }

    public static Path capture(String className, String testName, int attempt, UiDriver driver) {
        Path path = FrameworkConfig.artifactsDirectory().resolve("screenshots")
                .resolve(FrameworkConfig.engine().name().toLowerCase())
                .resolve(FrameworkConfig.browser().name().toLowerCase())
                .resolve(safe(className)).resolve(safe(testName))
                .resolve("attempt-" + attempt + "-" + LocalDateTime.now().format(FORMAT) + ".png");
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, driver.takeScreenshot());
            return path;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to capture screenshot at " + path, exception);
        }
    }

    private static String safe(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
