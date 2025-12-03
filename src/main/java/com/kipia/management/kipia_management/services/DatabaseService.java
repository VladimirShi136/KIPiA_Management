package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;

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

                // Добавляем тестовые данные если таблицы пустые
                if (!hasData()) {
                    addTestData();
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка подключения к базе данных: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось подключиться к базе данных", e);
        }
    }

    /**
     * Определяет путь к базе данных в зависимости от режима запуска
     */
    private String getDatabasePath() {
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
     * Метод для создания таблицы devices в базе данных.
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
                    photo_path TEXT,
                    photos TEXT
                );""";

        String sqlSchemes = """
                CREATE TABLE IF NOT EXISTS schemes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT,
                    data TEXT
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

    /**
     * Метод для добавления тестовых данных в базу данных.
     */
    public void addTestData() {
        if (hasData()) {
            LOGGER.info("Тестовые данные уже существуют");
            return;
        }

        DeviceDAO deviceDAO = new DeviceDAO(this);

        // Существующие тестовые приборы (без изменений)
        Device device1 = new Device();
        device1.setType("Измерительный");
        device1.setName("Термометр ТР-1");
        device1.setManufacturer("ТехноСервис");
        device1.setInventoryNumber("INV-001");
        device1.setYear(2020);
        device1.setMeasurementLimit("0-100°C");
        device1.setAccuracyClass(0.5);
        device1.setLocation("Лаборатория 1");
        device1.setValveNumber("№ 2Б");
        device1.setStatus("В работе");
        device1.setAdditionalInfo("Термометр для измерения температуры");

        Device device2 = new Device();
        device2.setType("Контрольный");
        device2.setName("Манометр МП-2");
        device2.setManufacturer("ИнжСервис");
        device2.setInventoryNumber("INV-002");
        device2.setYear(2018);
        device2.setMeasurementLimit("0-10 MPa");
        device2.setAccuracyClass(0.1);
        device2.setLocation("Цех 2");
        device2.setValveNumber("№ 165");
        device2.setStatus("Хранение");
        device2.setAdditionalInfo("Манометр для давления");

        Device device3 = new Device();
        device3.setType("Контрольный");
        device3.setName("Уровнемер УР-3");
        device3.setManufacturer("ПриборСум");
        device3.setInventoryNumber("INV-003");
        device3.setYear(2022);
        device3.setMeasurementLimit("0-5 м");
        device3.setAccuracyClass(0.05);
        device3.setLocation("Станция 3");
        device3.setValveNumber("№ 2Б-ф");
        device3.setStatus("Испорчен");
        device3.setAdditionalInfo("Уровнемер жидкости");

        try {
            deviceDAO.addDevice(device1);
            deviceDAO.addDevice(device2);
            deviceDAO.addDevice(device3);
            LOGGER.info("Тестовые устройства добавлены");
        } catch (Exception e) {
            LOGGER.error("Ошибка добавления тестовых устройств: {}", e.getMessage(), e);
        }

        // НОВЫЕ: Добавить тестовую схему
        String serializedScheme = "RECTANGLE,50,50,200,100;LINE,10,10,50,50";
        try (PreparedStatement stmtScheme = getConnection().prepareStatement(
                "INSERT INTO schemes (name, description, data) VALUES (?, ?, ?)")) {
            stmtScheme.setString(1, "Схема предприятия");
            stmtScheme.setString(2, "Тестовая схема с линиями");
            stmtScheme.setString(3, serializedScheme);
            stmtScheme.executeUpdate();
            LOGGER.info("Тестовая схема добавлена");
        } catch (SQLException e) {
            LOGGER.error("Ошибка добавления тестовой схемы: {}", e.getMessage(), e);
        }

        // НОВЫЕ: Добавить тестовые привязки приборов (device_locations)
        try (PreparedStatement stmtLoc = getConnection().prepareStatement(
                "INSERT INTO device_locations (device_id, scheme_id, x, y, rotation) VALUES (?, 1, ?, ?, ?)")) {

            stmtLoc.setInt(1, 1);  // Устройство ID=1 (device1)
            stmtLoc.setDouble(2, 100);
            stmtLoc.setDouble(3, 150);
            stmtLoc.setDouble(4, 0.0);  // ← ДОБАВЛЕНО значение rotation
            stmtLoc.executeUpdate();

            stmtLoc.setInt(1, 2);  // Устройство ID=2 (device2)
            stmtLoc.setDouble(2, 150);
            stmtLoc.setDouble(3, 250);
            stmtLoc.setDouble(4, 0.0);  // ← ДОБАВЛЕНО значение rotation
            stmtLoc.executeUpdate();

            stmtLoc.setInt(1, 3);  // Устройство ID=3 (device3)
            stmtLoc.setDouble(2, 200);
            stmtLoc.setDouble(3, 350);
            stmtLoc.setDouble(4, 0.0);  // ← ДОБАВЛЕНО значение rotation
            stmtLoc.executeUpdate();

            LOGGER.info("Тестовые привязки приборов добавлены");
        } catch (SQLException e) {
            LOGGER.error("Ошибка добавления тестовых локаций: {}", e.getMessage(), e);
        }

        LOGGER.info("Тестовые данные добавлены успешно");
    }

    private boolean hasData() {
        try (Statement stmt = getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM devices");
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            LOGGER.error("Ошибка проверки наличия данных: {}", e.getMessage(), e);
            return false;
        }
    }
}