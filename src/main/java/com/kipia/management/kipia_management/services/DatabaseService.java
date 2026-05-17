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

    // Путь к внешней БД (если используется конструктор с путём для импорта)
    private String externalDbPath;

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
        this.externalDbPath = dbPath;
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
                    updated_at INTEGER DEFAULT (strftime('%%s','now') * 1000),
                    deleted_at INTEGER DEFAULT 0,
                    last_synced_at INTEGER DEFAULT 0
                );""";

        String sqlSchemes = """
                CREATE TABLE IF NOT EXISTS schemes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL,
                    description TEXT,
                    data TEXT,
                    updated_at INTEGER DEFAULT (strftime('%%s','now') * 1000),
                    deleted_at INTEGER DEFAULT 0,
                    last_synced_at INTEGER DEFAULT 0
                );""";

        String sqlDeviceLocations = """
                CREATE TABLE IF NOT EXISTS device_locations (
                    device_id INTEGER NOT NULL,
                    scheme_id INTEGER NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    rotation REAL DEFAULT 0.0,
                    updated_at INTEGER DEFAULT (strftime('%%s','now') * 1000),
                    deleted_at INTEGER DEFAULT 0,
                    last_synced_at INTEGER DEFAULT 0,
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

        // Запуск миграции для существующих БД
        migrateToSoftDelete();
    }

    /**
     * Запускает миграции схемы БД (soft delete и two-way merge).
     * Публичный метод для использования при импорте внешней БД.
     */
    public void runMigrations() {
        migrateToSoftDelete();
    }

    /**
     * Миграция существующей БД для поддержки soft delete и two-way merge.
     * Добавляет поля deleted_at, updated_at и last_synced_at если они отсутствуют.
     * Безопасен для повторного запуска.
     */
    private void migrateToSoftDelete() {
        try {
            // Проверяем и добавляем deleted_at для devices
            addColumnIfNotExists("devices", "deleted_at", "INTEGER DEFAULT 0");

            // Проверяем и добавляем deleted_at для schemes
            addColumnIfNotExists("schemes", "deleted_at", "INTEGER DEFAULT 0");

            // Проверяем и добавляем deleted_at для device_locations
            addColumnIfNotExists("device_locations", "deleted_at", "INTEGER DEFAULT 0");

            // Проверяем и добавляем updated_at для device_locations (если старая БД)
            // SQLite не позволяет добавить колонку с неконстантным дефолтом через ALTER TABLE
            // Поэтому добавляем с дефолтом 0, затем обновляем существующие записи
            addColumnIfNotExists("device_locations", "updated_at", "INTEGER DEFAULT 0");
            updateColumnIfZero("device_locations", "updated_at");

            // Проверяем и добавляем last_synced_at для трёхстороннего merge
            addColumnIfNotExists("devices", "last_synced_at", "INTEGER DEFAULT 0");
            addColumnIfNotExists("schemes", "last_synced_at", "INTEGER DEFAULT 0");
            addColumnIfNotExists("device_locations", "last_synced_at", "INTEGER DEFAULT 0");

            LOGGER.info("Миграция soft delete и two-way merge завершена успешно");
        } catch (SQLException e) {
            LOGGER.error("Ошибка миграции soft delete: {}", e.getMessage(), e);
            // Не выбрасываем исключение, чтобы приложение могло продолжить работу
        }
    }

    /**
     * Добавляет колонку в таблицу, если она ещё не существует.
     * SQLite не поддерживает IF NOT EXISTS для ALTER TABLE, поэтому проверяем вручную.
     */
    private void addColumnIfNotExists(String tableName, String columnName, String columnDefinition) throws SQLException {
        // Проверяем существование колонки
        String checkSql = "PRAGMA table_info(" + tableName + ")";
        boolean columnExists = false;

        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            while (rs.next()) {
                if (columnName.equals(rs.getString("name"))) {
                    columnExists = true;
                    break;
                }
            }
        }

        // Если колонки нет - добавляем
        if (!columnExists) {
            String alterSql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
            try (Statement stmt = getConnection().createStatement()) {
                stmt.executeUpdate(alterSql);
                LOGGER.info("Добавлена колонка {}.{}", tableName, columnName);
            }
        } else {
            LOGGER.debug("Колонка {}.{} уже существует", tableName, columnName);
        }
    }

    /**
     * Обновляет значения колонки, которые равны 0, на текущее время в миллисекундах.
     * Используется для миграции updated_at после добавления колонки с дефолтом 0.
     */
    private void updateColumnIfZero(String tableName, String columnName) throws SQLException {
        String updateSql = "UPDATE " + tableName + " SET " + columnName + " = (strftime('%s','now') * 1000) WHERE " + columnName + " = 0";
        try (Statement stmt = getConnection().createStatement()) {
            int rowsUpdated = stmt.executeUpdate(updateSql);
            if (rowsUpdated > 0) {
                LOGGER.info("Обновлено {} записей в {}.{}", rowsUpdated, tableName, columnName);
            }
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
                if (externalDbPath != null) {
                    // Для внешней БД используем сохранённый путь
                    String dbUrl = "jdbc:sqlite:" + externalDbPath;
                    connection = DriverManager.getConnection(dbUrl);
                    LOGGER.info("Переподключение к внешней БД: {}", externalDbPath);
                } else {
                    // Для основной БД используем стандартный connect()
                    connect();
                }
            }
            return connection;
        } catch (SQLException e) {
            LOGGER.error("Ошибка при проверке соединения: {}", e.getMessage(), e);
            // Пытаемся пересоздать соединение
            if (externalDbPath != null) {
                throw new RuntimeException("Не удалось переподключиться к внешней БД: " + externalDbPath, e);
            }
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