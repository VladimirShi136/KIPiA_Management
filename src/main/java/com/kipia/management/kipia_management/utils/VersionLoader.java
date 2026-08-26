package com.kipia.management.kipia_management.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Класс для установки версии (считывание из PROPERTIES_FILE)
 * @author vladimir_shi
 * @since 27.11.2025
 */

public class VersionLoader {
    private static final String PROPERTIES_FILE = "/application.properties";
    private static String cachedVersion = null;

    public static String getVersion() {
        if (cachedVersion != null) {
            return cachedVersion;
        }

        try (InputStream input = VersionLoader.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                return "unknown";
            }

            Properties props = new Properties();
            props.load(input);
            cachedVersion = props.getProperty("app.version", "unknown");
            return cachedVersion;
        } catch (IOException e) {
            return "unknown";
        }
    }
}
