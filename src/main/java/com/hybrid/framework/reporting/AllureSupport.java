package com.hybrid.framework.reporting;

import com.hybrid.framework.config.FrameworkConfig;
import io.qameta.allure.Allure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AllureSupport {
    private static final Logger LOGGER = LogManager.getLogger(AllureSupport.class);

    private AllureSupport() {
    }

    public static void attachScreenshot(String name, Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (InputStream stream = Files.newInputStream(path)) {
            Allure.addAttachment(name, "image/png", stream, ".png");
        } catch (Exception exception) {
            LOGGER.error("Unable to attach screenshot to Allure", exception);
        }
    }

    public static void attachText(String name, String text) {
        Allure.addAttachment(name, "text/plain", text == null ? "" : text, ".txt");
    }

    public static void writeEnvironmentProperties() {
        Path directory = FrameworkConfig.allureResultsDirectory();
        try {
            Files.createDirectories(directory);
            Properties properties = new Properties();
            properties.setProperty("Automation Engine", FrameworkConfig.engine().name());
            properties.setProperty("Browser", FrameworkConfig.browser().name());
            properties.setProperty("Data Source", FrameworkConfig.dataSource());
            properties.setProperty("Headless", String.valueOf(FrameworkConfig.headless()));
            properties.setProperty("Parallel Mode", FrameworkConfig.parallelMode());
            properties.setProperty("Thread Count", String.valueOf(FrameworkConfig.threadCount()));
            properties.setProperty("Self Healing", String.valueOf(FrameworkConfig.selfHealingEnabled()));
            properties.setProperty("Operating System", System.getProperty("os.name"));
            properties.setProperty("Java Version", System.getProperty("java.version"));
            try (var writer = Files.newBufferedWriter(directory.resolve("environment.properties"))) {
                properties.store(writer, "Hybrid framework environment");
            }
        } catch (Exception exception) {
            LOGGER.error("Unable to write Allure environment properties", exception);
        }
    }
}
