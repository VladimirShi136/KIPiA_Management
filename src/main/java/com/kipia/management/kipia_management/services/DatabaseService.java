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
    // URL подключения к базе данных SQLite (файл kipia_management.db)
    private static final String DB_URL = "jdbc:sqlite:kipia_management.db";
    // Объект подключения к базе данных
    private Connection connection;
    // логгер для сообщений
    private static final Logger LOGGER = Logger.getLogger(DatabaseService.class.getName());

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
            connection = DriverManager.getConnection(DB_URL);
            // Включаем автоматическое пересоздание соединения
            connection.setAutoCommit(true);
            LOGGER.info("Подключение к SQLite установлено!");
        } catch (SQLException e) {
            LOGGER.severe("Ошибка подключения: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Метод для создания таблицы devices в базе данных.
     * Используется SQL-запрос с конструкцией CREATE TABLE IF NOT EXISTS.
     */
    public void createTables() {
        StringBuilder sql1 = new StringBuilder();
        StringBuilder sql2 = new StringBuilder();
        StringBuilder sql3 = new StringBuilder();

        // 1. Создание таблицы devices с полями:
        // id - уникальный идентификатор (автоинкремент)
        // name - название устройства (обязательное)
        // type - тип устройства (обязательное)
        // manufacturer - изготовитель
        // inventory_number - уникальный инвентарный номер (обязательное)
        // year - год выпуска
        // measurement_limit - предел измерений
        // accuracy_class - класс точности
        // location - местоположение (обязательное)
        // valve_number - номер крана или узла
        // status - статус устройства, по умолчанию 'В работе'
        // additional_info - доп информация
        // photo_path - путь к фото
        // photos - список фото
        sql1.append("CREATE TABLE IF NOT EXISTS devices (")
                .append("id INTEGER PRIMARY KEY AUTOINCREMENT,")
                .append("type TEXT NOT NULL,")
                .append("name TEXT,")
                .append("manufacturer TEXT,")
                .append("inventory_number TEXT UNIQUE NOT NULL,")
                .append("year INTEGER,")
                .append("measurement_limit TEXT,")  // Новое поле: предел измерений (String)
                .append("accuracy_class REAL,")  // Новое поле: класс точности (Double)
                .append("location TEXT NOT NULL,")
                .append("valve_number TEXT,")  // Новое поле для "Кран №"
                .append("status TEXT DEFAULT 'В работе',")
                .append("additional_info TEXT,")
                .append("photo_path TEXT,")
                .append("photos TEXT")  // Новое поле: сериализированный список фото
                .append(");");

        // 2. НОВАЯ ТАБЛИЦА: schemes (схемы)
        sql2.append("CREATE TABLE IF NOT EXISTS schemes (")
                .append("id INTEGER PRIMARY KEY AUTOINCREMENT,")
                .append("name TEXT NOT NULL,")
                .append("description TEXT,")
                .append("data TEXT")  // строка для объектов схемы (линии, фигуры)
                .append("); ");

        // 3. НОВАЯ ТАБЛИЦА: device_locations (привязка приборов к схемам)
        sql3.append("CREATE TABLE IF NOT EXISTS device_locations (")
                .append("device_id INTEGER NOT NULL,")
                .append("scheme_id INTEGER NOT NULL,")
                .append("x REAL NOT NULL,")
                .append("y REAL NOT NULL,")
                .append("PRIMARY KEY (device_id, scheme_id),")  // Композитный ключ
                .append("FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,")
                .append("FOREIGN KEY (scheme_id) REFERENCES schemes(id) ON DELETE CASCADE")
                .append(");");


        // Выполнение SQL-запроса для создания таблиц
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql1 + sql2.toString() + sql3);
            LOGGER.info("Таблицы созданы успешно!");  // Замена println на logger
        } catch (SQLException e) {
            LOGGER.severe("Ошибка создания таблиц: " + e.getMessage());  // Замена println на logger
            throw new RuntimeException(e);  // Выбрасываем исключение
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
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('devices', 'schemes', 'device_locations')"
            );
            // Проверяем, что все три есть (простой счёт)
            int count = 0;
            while (rs.next()) count++;
            return count == 3;
        } catch (SQLException e) {
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

        deviceDAO.addDevice(device1);
        deviceDAO.addDevice(device2);
        deviceDAO.addDevice(device3);

        // НОВЫЕ: Добавить тестовую схему
        String serializedScheme = "RECTANGLE,50,50,200,100;LINE,10,10,50,50";
        try (PreparedStatement stmtScheme = connection.prepareStatement(
                "INSERT INTO schemes (name, description, data) VALUES (?, ?, ?)")) {
            stmtScheme.setString(1, "Схема предприятия");
            stmtScheme.setString(2, "Тестовая схема с линиями");
            stmtScheme.setString(3, serializedScheme);
            stmtScheme.executeUpdate();
            LOGGER.info("Тестовая схема добавлена");  // Замена println
        } catch (SQLException e) {
            LOGGER.severe("Ошибка добавления тестовой схемы: " + e.getMessage());  // Замена println
            // Не выбрасываем здесь, так как это не критично; но можно, если нужно
        }

        // НОВЫЕ: Добавить тестовые привязки приборов (device_locations)
        // Пример: привяжем приборы к схеме ID=1 (первая добавленная схем)
        try (PreparedStatement stmtLoc = connection.prepareStatement("INSERT INTO device_locations (device_id, scheme_id, x, y) VALUES (?, 1, ?, ?)")) {  // scheme_id=1 предполагаем
            // Получить ID добавленных приборов (предполагаем через запрос, но для простоты, используй insert-returns или жёстко)
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

            LOGGER.info("Тестовые привязки приборов добавлены");  // Замена println
        } catch (SQLException e) {
            LOGGER.severe("Ошибка добавления тестовых локаций: " + e.getMessage());  // Замена println
        }
        LOGGER.info("Тестовые данные добавлены");  // Замена println
    }

    private boolean hasData() {
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM devices");
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }
}