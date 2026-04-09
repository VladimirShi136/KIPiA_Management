package com.kipia.management.kipia_management.services;

import java.io.File;
import java.sql.*;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Класс DatabaseService предоставляет функционал для подключения к SQLite базе данных,
 * создания необходимых таблиц и управления соединением.
 *
 * @author vladimir_shi
 * @since 29.08.2025
 */
public class DatabaseService {
    // логгер для сообщений
    private static final Logger LOGGER = LogManager.getLogger(DatabaseService.class);

    // Объект подключения к базе данных
    private Connection connection;

    // Статический блок для регистрации драйвера SQLite
    static {
        try {
            Class.forName("org.sqlite.JDBC");
            LOGGER.info("SQLite драйвер успешно зарегистрирован");
        } catch (ClassNotFoundException e) {
            LOGGER.error("SQLite драйвер не найден: {}", e.getMessage(), e);
            throw new RuntimeException("SQLite драйвер не найден", e);
        }
    }

    // Конструктор класса: устанавливает соединение
    public DatabaseService() {
        connect();
    }

    /**
     * Конструктор для подключения к произвольному файлу БД (для merge при импорте)
     */
    public DatabaseService(String dbPath) {
        try {
            String dbUrl = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(dbUrl);
            LOGGER.info("Подключение к внешней БД: {}", dbPath);
        } catch (SQLException e) {
            LOGGER.error("Ошибка подключения к внешней БД: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось подключиться к БД: " + dbPath, e);
        }
    }

    /**
     * Метод для установления соединения с базой данных SQLite.
     * В случае успеха сохраняет объект Connection в поле connection.
     * В случае ошибки выводит сообщение об ошибке.
     */
    private void connect() {
        try {
            String dbPath = getDatabasePath();
            String dbUrl = "jdbc:sqlite:" + dbPath;

            LOGGER.info("Подключение к базе данных: {}", dbPath);
            connection = DriverManager.getConnection(dbUrl);

            // Проверяем что соединение установлено и драйвер работает
            if (connection != null && !connection.isClosed()) {
                DatabaseMetaData meta = connection.getMetaData();
                LOGGER.info("Подключение к SQLite установлено! Драйвер: {} версия: {}", meta.getDriverName(), meta.getDriverVersion());

                // Создаем таблицы после успешного подключения
                createTables();
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка подключения к базе данных: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось подключиться к базе данных", e);
        }
    }

    /**
     * Определяет путь к базе данных в зависимости от режима запуска.
     * Публичный — используется в SyncManager чтобы не дублировать логику.
     */
    public String getDatabasePath() {
        if (isDevelopmentMode()) {
            // Режим разработки - база в resources/data
            String projectDir = System.getProperty("user.dir");
            String dataDir = projectDir + "/src/main/resources/data";

            File dataFolder = new File(dataDir);
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
                LOGGER.info("Создана папка для данных разработки: {}", dataFolder.getAbsolutePath());
            }

            return dataDir + "/kipia_management.db";
        } else {
            // Режим продакшена - база в AppData
            String userDataDir = System.getenv("APPDATA") + "/KIPiA_Management/data";
            File dataFolder = new File(userDataDir);
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
                LOGGER.info("Создана папка для данных пользователя: {}", dataFolder.getAbsolutePath());
            }
            return userDataDir + "/kipia_management.db";
        }
    }

    /**
     * Проверяет, запущено ли приложение в режиме разработки
     */
    private boolean isDevelopmentMode() {
        // ПРИОРИТЕТ 1: Принудительный продакшен режим через VM option
        if ("true".equals(System.getProperty("production"))) {
            LOGGER.info("Режим: ПРОДАКШЕН (принудительно через -Dproduction=true)");
            return false;
        }

        // ПРИОРИТЕТ 2: Принудительный режим разработки
        if ("true".equals(System.getProperty("development"))) {
            LOGGER.info("Режим: РАЗРАБОТКА (принудительно через -Ddevelopment=true)");
            return true;
        }

        String classPath = System.getProperty("java.class.path");
        String javaHome = System.getProperty("java.home");

        // Автоопределение по окружению
        boolean isDev = classPath.contains("target/classes") ||
                classPath.contains("idea_rt.jar") ||
                javaHome.contains("IntelliJ") ||
                classPath.contains(".idea") ||
                !isRunningFromJAR();

        if (isDev) {
            LOGGER.info("Режим: РАЗРАБОТКА (автоопределение)");
        } else {
            LOGGER.info("Режим: ПРОДАКШЕН (автоопределение)");
        }
        return isDev;
    }

    /**
     * Проверяет, запущено ли приложение из JAR файла
     */
    private boolean isRunningFromJAR() {
        String className = this.getClass().getName().replace('.', '/');
        String classJar = Objects.requireNonNull(this.getClass().getResource("/" + className + ".class")).toString();
        return classJar.startsWith("jar:");
    }

    /**
     * Метод для создания трех таблиц в базе данных.
     * Используется SQL-запрос с конструкцией CREATE TABLE IF NOT EXISTS.
     */
    public void createTables() {
        String sqlDevices = """
                CREATE TABLE IF NOT EXISTS devices (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL,
                    name TEXT,
                    manufacturer TEXT,
                    inventory_number TEXT UNIQUE NOT NULL,
                    year INTEGER,
                    measurement_limit TEXT,
                    accuracy_class REAL,
                    location TEXT NOT NULL,
                    valve_number TEXT,
                    status TEXT DEFAULT 'В работе',
                    additional_info TEXT,
                    photos TEXT,
                    updated_at INTEGER DEFAULT (strftime('%%s','now') * 1000)
                );""";

        String sqlSchemes = """
                CREATE TABLE IF NOT EXISTS schemes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT,
                    data TEXT,
                    updated_at INTEGER DEFAULT (strftime('%%s','now') * 1000)
                );""";

        String sqlDeviceLocations = """
                CREATE TABLE IF NOT EXISTS device_locations (
                    device_id INTEGER NOT NULL,
                    scheme_id INTEGER NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    rotation REAL DEFAULT 0.0,
                    PRIMARY KEY (device_id, scheme_id),
                    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
                    FOREIGN KEY (scheme_id) REFERENCES schemes(id) ON DELETE CASCADE
                );""";

        // Выполнение SQL-запросов для создания таблиц
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(sqlDevices);
            stmt.executeUpdate(sqlSchemes);
            stmt.executeUpdate(sqlDeviceLocations);
            LOGGER.info("Таблицы созданы успешно!");
        } catch (SQLException e) {
            LOGGER.error("Ошибка создания таблиц: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка создания таблиц базы данных", e);
        }
    }

    /**
     * Геттер для получения активного соединения с базой данных.
     * Может использоваться для выполнения других запросов вне класса.
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                LOGGER.warn("Соединение с БД закрыто, пересоздаем...");
                connect();
            }
            return connection;
        } catch (SQLException e) {
            LOGGER.error("Ошибка при проверке соединения: {}", e.getMessage(), e);
            // Пытаемся пересоздать соединение
            connect();
            return connection;
        }
    }

    /**
     * Метод для корректного закрытия соединения с базой данных.
     * Проверяет, что соединение не равно null, и закрывает его.
     * В случае ошибки выводит сообщение.
     */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LOGGER.info("Подключение закрыто.");
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка закрытия подключения: {}", e.getMessage(), e);
        }
    }
}