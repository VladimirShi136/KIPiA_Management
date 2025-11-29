package com.kipia.management.kipia_management.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Класс LoggingConfig предоставляет методы для настройки логирования.
 *
 * @author vladimir_shi
 * @since 17.11.2025
 */
public class LoggingConfig {
    private static final Logger logger = LogManager.getLogger(LoggingConfig.class);
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) {
            return;
        }

        try {
            // Создаем директорию для логов если её нет
            createLogDirectory();

            String configPath = getLoggingConfigPath();
            File configFile = new File(configPath);

            if (configFile.exists()) {
                // Используем файловую конфигурацию
                try (FileInputStream inputStream = new FileInputStream(configFile)) {
                    ConfigurationSource source = new ConfigurationSource(inputStream, configFile);
                    Configurator.initialize(null, source);
                    logger.info("Log4j2 сконфигурирован из файла: {}", configPath);
                }
            } else {
                // Используем встроенную конфигурацию из ресурсов
                try (InputStream resourceStream = LoggingConfig.class.getResourceAsStream("/logs/log4j2.xml")) {
                    if (resourceStream != null) {
                        ConfigurationSource source = new ConfigurationSource(resourceStream);
                        Configurator.initialize(null, source);
                        logger.info("Log4j2 сконфигурирован из ресурсов: /logs/log4j2.xml");
                    } else {
                        // Фолбэк на базовую конфигурацию
                        logger.info("Используется базовая конфигурация Log4j2");
                    }
                }
            }

            initialized = true;

        } catch (Exception e) {
            System.err.println("Ошибка инициализации логгера: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Определяет путь к файлу конфигурации в зависимости от режима запуска
     */
    private static String getLoggingConfigPath() {
        // ПРИОРИТЕТ 1: Принудительный путь через системное свойство
        String customConfigPath = System.getProperty("log4j.configurationFile");
        if (customConfigPath != null && !customConfigPath.trim().isEmpty()) {
            logger.info("Используется пользовательский путь к конфигурации: {}", customConfigPath);
            return customConfigPath;
        }

        // ПРИОРИТЕТ 2: Режим разработки
        if (isDevelopmentMode()) {
            // Режим разработки - конфиг из ресурсов
            String projectDir = System.getProperty("user.dir");
            String devConfigPath = projectDir + "/src/main/resources/logs/log4j2.xml";
            logger.info("Режим разработки - конфиг из: {}", devConfigPath);
            return devConfigPath;
        } else {
            // Режим продакшена - конфиг в AppData или рядом с EXE
            String appDataPath = System.getenv("APPDATA") + "/KIPiA_Management/log4j2.xml";
            String currentDirPath = getCurrentDir() + "/log4j2.xml";

            // Проверяем сначала в AppData, потом рядом с EXE
            File appDataConfig = new File(appDataPath);
            if (appDataConfig.exists()) {
                logger.info("Режим продакшена - конфиг из AppData: {}", appDataPath);
                return appDataPath;
            } else {
                logger.info("Режим продакшена - конфиг из текущей директории: {}", currentDirPath);
                return currentDirPath;
            }
        }
    }

    /**
     * Получает текущую директорию приложения
     */
    private static String getCurrentDir() {
        try {
            // Для продакшена получаем путь к директории с JAR/EXE
            String jarPath = LoggingConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            return jarFile.getParent();
        } catch (Exception e) {
            return System.getProperty("user.dir");
        }
    }

    /**
     * Проверяет, запущено ли приложение в режиме разработки
     */
    private static boolean isDevelopmentMode() {
        // ПРИОРИТЕТ 1: Принудительный продакшен режим через VM option
        if ("true".equals(System.getProperty("production"))) {
            return false;
        }

        // ПРИОРИТЕТ 2: Принудительный режим разработки
        if ("true".equals(System.getProperty("development"))) {
            return true;
        }

        String classPath = System.getProperty("java.class.path");

        // Автоопределение по окружению
        return classPath.contains("target/classes") ||
                classPath.contains("idea_rt.jar") ||
                classPath.contains(".idea") ||
                !isRunningFromJAR();
    }

    /**
     * Проверяет, запущено ли приложение из JAR файла
     */
    private static boolean isRunningFromJAR() {
        try {
            String className = LoggingConfig.class.getName().replace('.', '/');
            java.net.URL classJar = LoggingConfig.class.getResource("/" + className + ".class");
            return classJar != null && classJar.toString().startsWith("jar:");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Создает директорию для логов
     */
    private static void createLogDirectory() {
        try {
            String logDirPath = getLogDirectoryPath();
            File logDir = new File(logDirPath);
            if (!logDir.exists()) {
                boolean created = logDir.mkdirs();
                if (created) {
                    logger.debug("Создана папка для логов: {}", logDirPath);
                }
            }
        } catch (Exception e) {
            System.err.println("Не удалось создать папку для логов: " + e.getMessage());
        }
    }

    /**
     * Получает путь к директории логов
     */
    private static String getLogDirectoryPath() {
        if (isDevelopmentMode()) {
            // Режим разработки - логи в папке проекта
            String projectDir = System.getProperty("user.dir");
            return projectDir + "/src/main/resources/logs";
        } else {
            // Режим продакшена - логи в AppData
            return System.getenv("APPDATA") + "/KIPiA_Management/logs";
        }
    }
}