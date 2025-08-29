package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Класс DeviceDAO (Data Access Object) предоставляет методы для работы с данными приборов
 * в базе данных. Реализует основные CRUD-операции (Create, Read, Update, Delete).
 *
 * @author vladimir_shi
 * @since 29.08.2025
 */

public class DeviceDAO {
    // Сервис для работы с базой данных
    private final DatabaseService databaseService;

    /**
     * Конструктор класса DeviceDAO
     * @param databaseService экземпляр сервиса для работы с базой данных
     */
    public DeviceDAO(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * Добавление нового прибора в базу данных
     * @param device объект прибора для добавления
     * @return true - если добавление прошло успешно, false - в случае ошибки
     */
    public boolean addDevice(Device device) {
        // SQL-запрос для вставки данных прибора
        String sql = "INSERT INTO devices(name, type, inventory_number, location, status) VALUES(?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = databaseService.getConnection().prepareStatement(sql)) {
            // Установка параметров запроса из объекта device
            pstmt.setString(1, device.getName());
            pstmt.setString(2, device.getType());
            pstmt.setString(3, device.getInventoryNumber());
            pstmt.setString(4, device.getLocation());
            pstmt.setString(5, device.getStatus());

            // Выполнение запроса
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Ошибка добавления прибора: " + e.getMessage());
            return false;
        }
    }

    /**
     * Получение списка всех приборов из базы данных
     * @return список объектов Device, отсортированный по названию
     */
    public List<Device> getAllDevices() {
        List<Device> devices = new ArrayList<>();
        // SQL-запрос для получения всех записей из таблицы devices
        String sql = "SELECT * FROM devices ORDER BY name";

        try (Statement stmt = databaseService.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            // Обработка результатов запроса
            while (rs.next()) {
                Device device = new Device();
                // Заполнение объекта Device данными из ResultSet
                device.setId(rs.getInt("id"));
                device.setName(rs.getString("name"));
                device.setType(rs.getString("type"));
                device.setInventoryNumber(rs.getString("inventory_number"));
                device.setLocation(rs.getString("location"));
                device.setStatus(rs.getString("status"));

                // Добавление прибора в список
                devices.add(device);
            }
        } catch (SQLException e) {
            System.out.println("Ошибка получения приборов: " + e.getMessage());
        }
        return devices;
    }

    /**
     * Обновление данных прибора в базе данных
     * @param device объект прибора с обновленными данными
     * @return true - если обновление прошло успешно, false - в случае ошибки
     */
    public boolean updateDevice(Device device) {
        // SQL-запрос для обновления данных прибора
        String sql = "UPDATE devices SET name = ?, type = ?, inventory_number = ?, location = ?, status = ? WHERE id = ?";

        try (PreparedStatement pstmt = databaseService.getConnection().prepareStatement(sql)) {
            // Установка параметров запроса из объекта device
            pstmt.setString(1, device.getName());
            pstmt.setString(2, device.getType());
            pstmt.setString(3, device.getInventoryNumber());
            pstmt.setString(4, device.getLocation());
            pstmt.setString(5, device.getStatus());
            pstmt.setInt(6, device.getId());

            // Выполнение запроса
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Ошибка обновления прибора: " + e.getMessage());
            return false;
        }
    }

    /**
     * Удаление прибора из базы данных по идентификатору
     * @param id идентификатор прибора для удаления
     * @return true - если удаление прошло успешно, false - в случае ошибки
     */
    public boolean deleteDevice(int id) {
        // SQL-запрос для удаления прибора
        String sql = "DELETE FROM devices WHERE id = ?";

        try (PreparedStatement pstmt = databaseService.getConnection().prepareStatement(sql)) {
            // Установка параметра ID для удаления
            pstmt.setInt(1, id);

            // Выполнение запроса
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Ошибка удаления прибора: " + e.getMessage());
            return false;
        }
    }

    /**
     * Поиск прибора по инвентарному номеру
     * @param inventoryNumber инвентарный номер для поиска
     * @return объект Device если найден, null - если не найден или произошла ошибка
     */
    public Device findDeviceByInventoryNumber(String inventoryNumber) {
        // SQL-запрос для поиска прибора по инвентарному номеру
        String sql = "SELECT * FROM devices WHERE inventory_number = ?";

        try (PreparedStatement pstmt = databaseService.getConnection().prepareStatement(sql)) {
            // Установка параметра инвентарного номера для поиска
            pstmt.setString(1, inventoryNumber);

            // Выполнение запроса и получение результатов
            ResultSet rs = pstmt.executeQuery();

            // Если прибор найден, создаем и возвращаем объект Device
            if (rs.next()) {
                Device device = new Device();
                device.setId(rs.getInt("id"));
                device.setName(rs.getString("name"));
                device.setType(rs.getString("type"));
                device.setInventoryNumber(rs.getString("inventory_number"));
                device.setLocation(rs.getString("location"));
                device.setStatus(rs.getString("status"));
                return device;
            }
        } catch (SQLException e) {
            System.out.println("Ошибка поиска прибора: " + e.getMessage());
        }
        return null;
    }
}
