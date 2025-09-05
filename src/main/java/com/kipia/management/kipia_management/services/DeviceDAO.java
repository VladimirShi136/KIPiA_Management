package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
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
     * Вспомогательный метод: сериализация списка фото в строку.
     */
    private String photosToString(List<String> photos) {
        if (photos == null || photos.isEmpty()) return "";
        return String.join(";", photos);
    }

    /**
     * Вспомогательный метод: десериализация строки в список фото.
     */
    private List<String> stringToPhotos(String photosStr) {
        if (photosStr == null || photosStr.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(photosStr.split(";")));
    }

    /**
     * Добавление нового прибора в базу данных
     * @param device объект прибора для добавления
     * @return true - если добавление прошло успешно, false - в случае ошибки
     */
    public boolean addDevice(Device device) {
        // Обновлено: добавлен плейсхолдер для photos
        String sql = "INSERT INTO devices (type, name, manufacturer, inventory_number, year, location, status, additional_info, photo_path, photos) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            // Устанавливаем параметры (исправлен порядок для соответствия полям таблицы)
            stmt.setString(1, device.getType());
            stmt.setString(2, device.getName());
            stmt.setString(3, device.getManufacturer());
            stmt.setString(4, device.getInventoryNumber());
            if (device.getYear() != null) {
                stmt.setInt(5, device.getYear()); // Год
            } else {
                stmt.setNull(5, Types.INTEGER);
            }
            stmt.setString(6, device.getLocation());
            stmt.setString(7, device.getStatus());
            stmt.setString(8, device.getAdditionalInfo());  // Новое дополнительная
            stmt.setString(9, device.getPhotoPath());  // Старое поле для первого фото
            stmt.setString(10, photosToString(device.getPhotos()));  // Новое поле для списка фото
            // Выполнение запроса
            stmt.executeUpdate();
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
        // SQL с новым полем photos
        String sql = "SELECT * FROM devices ORDER BY name";
        try (Statement stmt = databaseService.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Device device = new Device();
                device.setId(rs.getInt("id"));
                device.setType(rs.getString("type"));
                device.setName(rs.getString("name"));
                device.setManufacturer(rs.getString("manufacturer"));
                device.setInventoryNumber(rs.getString("inventory_number"));
                // Десериализация года (если null, оставляем null)
                Object yearObj = rs.getObject("year");
                device.setYear(yearObj != null ? (Integer) yearObj : null);
                device.setLocation(rs.getString("location"));
                device.setStatus(rs.getString("status"));
                device.setAdditionalInfo(rs.getString("additional_info"));
                device.setPhotoPath(rs.getString("photo_path"));
                device.setPhotos(stringToPhotos(rs.getString("photos")));  // Новое
                devices.add(device);
            }
        } catch (SQLException e) {
            System.out.println("Ошибка получения приборов: " + e.getMessage());
        }
        return devices;
    }

    /**
     * Обновление данных прибора в базе данных
     *
     * @param device объект прибора с обновленными данными
     */
    public void updateDevice(Device device) {
        // Обновлено: добавлен photos = ? в SET
        String sql = "UPDATE devices SET type = ?, name = ?, manufacturer = ?, inventory_number = ?, year = ?, location = ?, status = ?, additional_info = ?, photo_path = ?, photos = ? WHERE id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            // Устанавливаем параметры (порядок должен соответствовать плейсхолдерам)
            stmt.setString(1, device.getType());
            stmt.setString(2, device.getName());
            stmt.setString(3, device.getManufacturer());
            stmt.setString(4, device.getInventoryNumber());
            if (device.getYear() != null) {
                stmt.setInt(5, device.getYear()); // Год
            } else {
                stmt.setNull(5, Types.INTEGER);
            }
            stmt.setString(6, device.getLocation());
            stmt.setString(7, device.getStatus());
            stmt.setString(8, device.getAdditionalInfo());
            stmt.setString(9, device.getPhotoPath());
            stmt.setString(10, photosToString(device.getPhotos()));  // Список фото
            stmt.setInt(11, device.getId());  // ID для WHERE
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка обновления прибора: " + e.getMessage());
        }
    }

    /**
     * Удаление прибора из базы данных по идентификатору
     * @param id идентификатор прибора для удаления
     * @return true - если удаление прошло успешно, false - в случае ошибки
     */
    public boolean deleteDevice(int id) {
        String sql = "DELETE FROM devices WHERE id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
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
        String sql = "SELECT * FROM devices WHERE inventory_number = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setString(1, inventoryNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Device device = new Device();
                device.setId(rs.getInt("id"));
                device.setType(rs.getString("type"));
                device.setName(rs.getString("name"));
                device.setManufacturer(rs.getString("manufacturer"));
                device.setInventoryNumber(rs.getString("inventory_number"));
                Object yearObj = rs.getObject("year");
                device.setYear(yearObj != null ? (Integer) yearObj : null);
                device.setLocation(rs.getString("location"));
                device.setStatus(rs.getString("status"));
                device.setAdditionalInfo(rs.getString("additional_info"));
                device.setPhotoPath(rs.getString("photo_path"));
                device.setPhotos(stringToPhotos(rs.getString("photos")));  // Новое
                return device;
            }
        } catch (SQLException e) {
            System.out.println("Ошибка поиска прибора: " + e.getMessage());
        }
        return null;
    }
}