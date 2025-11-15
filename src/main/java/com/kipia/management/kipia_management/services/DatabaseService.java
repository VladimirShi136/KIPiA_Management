package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import java.sql.*;
import java.util.logging.Logger;

/**
 * Класс DatabaseService предоставляет функционал для подключения к SQLite базе данных,
 * создания необходимых таблиц и управления соединением.
 *
 * @author vladimir_shi
 * @since 29.08.2025
 */
public class DatabaseService {
    // логгер для сообщений
    private static final Logger LOGGER = Logger.getLogger(DatabaseService.class.getName());

    // Объект подключения к базе данных
    private Connection connection;

    // Статический блок для регистрации драйвера SQLite
    static {
        try {
            Class.forName("org.sqlite.JDBC");
            LOGGER.info("SQLite драйвер успешно зарегистрирован");
        } catch (ClassNotFoundException e) {
            LOGGER.severe("SQLite драйвер не найден: " + e.getMessage());
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
            // Используем абсолютный путь к базе данных в папке пользователя
            String userHome = System.getProperty("user.home");
            String dbPath = userHome + "/kipia_management.db";
            String dbUrl = "jdbc:sqlite:" + dbPath;

            LOGGER.info("Подключение к базе данных: " + dbUrl);

            connection = DriverManager.getConnection(dbUrl);

            // Проверяем что соединение установлено и драйвер работает
            if (connection != null && !connection.isClosed()) {
                DatabaseMetaData meta = connection.getMetaData();
                LOGGER.info("Подключение к SQLite установлено! Драйвер: " + meta.getDriverName() + " версия: " + meta.getDriverVersion());

                // Создаем таблицы после успешного подключения
                createTables();

                // Добавляем тестовые данные если таблицы пустые
                if (!hasData()) {
                    addTestData();
                }
            }

        } catch (SQLException e) {
            LOGGER.severe("Ошибка подключения к базе данных: " + e.getMessage());
            throw new RuntimeException("Не удалось подключиться к базе данных", e);
        }
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
            LOGGER.severe("Ошибка создания таблиц: " + e.getMessage());
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
                LOGGER.warning("Соединение с БД закрыто, пересоздаем...");
                connect();
            }
            return connection;
        } catch (SQLException e) {
            LOGGER.severe("Ошибка при проверке соединения: " + e.getMessage());
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
            LOGGER.severe("Ошибка закрытия подключения: " + e.getMessage());
        }
    }

    public boolean tablesExist() {
        try (Statement stmt = getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('devices', 'schemes', 'device_locations')"
            );
            int count = 0;
            while (rs.next()) count++;
            return count == 3;
        } catch (SQLException e) {
            LOGGER.severe("Ошибка проверки существования таблиц: " + e.getMessage());
            return false;
        }
    }

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
            LOGGER.severe("Ошибка добавления тестовых устройств: " + e.getMessage());
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
            LOGGER.severe("Ошибка добавления тестовой схемы: " + e.getMessage());
        }

        // НОВЫЕ: Добавить тестовые привязки приборов (device_locations)
        try (PreparedStatement stmtLoc = getConnection().prepareStatement(
                "INSERT INTO device_locations (device_id, scheme_id, x, y) VALUES (?, 1, ?, ?)")) {

            stmtLoc.setInt(1, 1);  // Устройство ID=1 (device1)
            stmtLoc.setDouble(2, 100);
            stmtLoc.setDouble(3, 150);
            stmtLoc.executeUpdate();

            stmtLoc.setInt(1, 2);  // Устройство ID=2 (device2)
            stmtLoc.setDouble(2, 150);
            stmtLoc.setDouble(3, 250);
            stmtLoc.executeUpdate();

            stmtLoc.setInt(1, 3);  // Устройство ID=3 (device3)
            stmtLoc.setDouble(2, 200);
            stmtLoc.setDouble(3, 350);
            stmtLoc.executeUpdate();

            LOGGER.info("Тестовые привязки приборов добавлены");
        } catch (SQLException e) {
            LOGGER.severe("Ошибка добавления тестовых локаций: " + e.getMessage());
        }

        LOGGER.info("Тестовые данные добавлены успешно");
    }

    private boolean hasData() {
        try (Statement stmt = getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM devices");
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            LOGGER.severe("Ошибка проверки наличия данных: " + e.getMessage());
            return false;
        }
    }
}