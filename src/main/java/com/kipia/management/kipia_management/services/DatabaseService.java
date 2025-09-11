package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;

import java.sql.*;

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
            System.out.println("Подключение к SQLite установлено!");
        } catch (SQLException e) {
            System.out.println("Ошибка подключения: " + e.getMessage());
        }
    }

    /**
     * Метод для создания таблицы devices в базе данных.
     * Используется SQL-запрос с конструкцией CREATE TABLE IF NOT EXISTS.
     */
    public void createTables() {
        StringBuilder sql = new StringBuilder();

        // Создание таблицы devices с полями:
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
        sql.append("CREATE TABLE IF NOT EXISTS devices (")
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


        // Выполнение SQL-запроса для создания таблиц
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql.toString());
            System.out.println("Таблица создана успешно!");
        } catch (SQLException e) {
            System.out.println("Ошибка создания таблицы: " + e.getMessage());
        }
    }

    /**
     * Геттер для получения активного соединения с базой данных.
     * Может использоваться для выполнения других запросов вне класса.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Метод для корректного закрытия соединения с базой данных.
     * Проверяет, что соединение не равно null, и закрывает его.
     * В случае ошибки выводит сообщение.
     */
    public void closeConnection() {
        try {
            if (connection != null) {
                connection.close();
                System.out.println("Подключение закрыто.");
            }
        } catch (SQLException e) {
            System.out.println("Ошибка закрытия подключения: " + e.getMessage());
        }
    }

    public boolean tablesExist() {
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='devices'"
            );
            return rs.next();  // Возвращает true, если таблица найдена
        } catch (SQLException e) {
            return false;
        }
    }

    public void addTestData() {
        if (hasData()) {
            System.out.println("Тестовые данные уже существуют");
            return;
        }

        DeviceDAO deviceDAO = new DeviceDAO(this);

        Device device1 = new Device();
        device1.setType("Измерительный");
        device1.setName("Термометр ТР-1");
        device1.setManufacturer("ТехноСервис");
        device1.setInventoryNumber("INV-001");
        device1.setYear(2020);
        device1.setMeasurementLimit("0-100°C");  // Новое
        device1.setAccuracyClass(0.5);  // Новое
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
        device2.setMeasurementLimit("0-10 MPa");  // Новое
        device2.setAccuracyClass(0.1);  // Новое
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
        device3.setMeasurementLimit("0-5 м");  // Новое
        device3.setAccuracyClass(0.05);  // Новое
        device3.setLocation("Станция 3");
        device3.setValveNumber("№ 2Б-ф");
        device3.setStatus("Испорчен");
        device3.setAdditionalInfo("Уровнемер жидкости");


        deviceDAO.addDevice(device1);
        deviceDAO.addDevice(device2);
        deviceDAO.addDevice(device3);

        System.out.println("Тестовые данные добавлены");
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