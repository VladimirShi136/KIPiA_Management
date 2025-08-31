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
     * Метод для создания таблиц devices и maintenance в базе данных.
     * Используется SQL-запрос с конструкцией CREATE TABLE IF NOT EXISTS.
     */
    public void createTables() {
        StringBuilder sql1 = new StringBuilder();

        // Создание таблицы devices с полями:
        // id - уникальный идентификатор (автоинкремент)
        // name - название устройства (обязательное)
        // type - тип устройства (обязательное)
        // inventory_number - уникальный инвентарный номер (обязательное)
        // location - местоположение (обязательное)
        // status - статус устройства, по умолчанию 'В работе'
        // last_maintenance - дата последнего обслуживания
        // next_maintenance - дата следующего обслуживания
        // created_at - дата и время создания записи, по умолчанию текущее время
        sql1.append("CREATE TABLE IF NOT EXISTS devices (")
                .append("id INTEGER PRIMARY KEY AUTOINCREMENT,")
                .append("type TEXT NOT NULL,")
                .append("name TEXT NOT NULL,")
                .append("manufacturer TEXT NOT NULL,")
                .append("inventory_number TEXT UNIQUE NOT NULL,")
                .append("year INTEGER,")
                .append("location TEXT NOT NULL,")
                .append("status TEXT DEFAULT 'В работе',")
                .append("additional_info TEXT,")
                .append("photo_path TEXT")
                .append(");");

        StringBuilder sql2 = new StringBuilder();
        // Создание таблицы maintenance с полями:
        // id - уникальный идентификатор (автоинкремент)
        // device_id - внешний ключ на таблицу devices
        // maintenance_date - дата обслуживания (обязательное)
        // description - описание работы
        // technician - имя техника
        // result - результат обслуживания, по умолчанию 'Успешно'
        sql2.append("CREATE TABLE IF NOT EXISTS maintenance (")
                .append("id INTEGER PRIMARY KEY AUTOINCREMENT,")
                .append("device_id INTEGER,")
                .append("maintenance_date DATE NOT NULL,")
                .append("description TEXT,")
                .append("technician TEXT,")
                .append("result TEXT DEFAULT 'Успешно',")
                .append("FOREIGN KEY (device_id) REFERENCES devices (id)")
                .append(");");

        // Выполнение SQL-запроса для создания таблиц
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql1.toString()); // Здесь может возникнуть ошибка из-за нескольких CREATE TABLE
            stmt.executeUpdate(sql2.toString());
            System.out.println("Таблицы созданы успешно!");
        } catch (SQLException e) {
            System.out.println("Ошибка создания таблиц: " + e.getMessage());
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
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('devices', 'maintenance')"
            );
            int count = 0;
            while (rs.next()) count++;
            return count == 2;
        } catch (SQLException e) {
            return false;
        }
    }

    public void addTestData() {
        if (hasData()) {
            System.out.println("Тестовые данные уже существуют");
            return;
        }

        Device device1 = new Device();
        Device device2 = new Device();
        Device device3 = new Device();

        DeviceDAO deviceDAO = new DeviceDAO(this);
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