package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
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
    // Логгер для сообщений
    private static final Logger LOGGER = LogManager.getLogger(DeviceDAO.class);
    // Получаем PhotoManager для миграции фото

    /**
     * Конструктор класса DeviceDAO
     * @param databaseService экземпляр сервиса для работы с базой данных
     */
    public DeviceDAO(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * Вспомогательный метод: сериализация списка фото в строку.
     * Теперь храним только имена файлов!
     */
    private String photosToString(List<String> photos) {
        if (photos == null || photos.isEmpty()) return "";

        // Фильтруем только имена файлов (без путей)
        List<String> fileNames = new ArrayList<>();
        for (String photo : photos) {
            if (photo != null && !photo.trim().isEmpty()) {
                File file = new File(photo);
                fileNames.add(file.getName()); // ⭐⭐ ТОЛЬКО ИМЯ ФАЙЛА! ⭐⭐
            }
        }

        return String.join(";", fileNames);
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
        // Обновляем timestamp перед сохранением
        device.updateTimestamp();

        // Добавлено поле updated_at (13-й параметр)
        String sql = "INSERT INTO devices (type, name, manufacturer, inventory_number, year, measurement_limit, " +
                "accuracy_class, location, valve_number, status, additional_info, photos, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            installParameters(device, stmt); // Устанавливаем первые 12 параметров
            stmt.setLong(13, device.getUpdatedAt()); // 13-й - updated_at
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Ошибка добавления прибора: {}", e.getMessage(), e);
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
                devices.add(createDeviceSQL(rs));
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения приборов: {}", e.getMessage(), e);  // Замена println
        }
        return devices;
    }

    /**
     * Обновление данных прибора в базе данных
     *
     * @return - результат обновления
     */
    public boolean updateDevice(Device device) {
        // Обновляем timestamp перед сохранением
        device.updateTimestamp();

        // Добавлено поле updated_at (13-й параметр в SET, 14-й WHERE id)
        String sql = "UPDATE devices SET type = ?, name = ?, manufacturer = ?, inventory_number = ?, " +
                "year = ?, measurement_limit = ?, accuracy_class = ?, location = ?, valve_number = ?, " +
                "status = ?, additional_info = ?, photos = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            installParameters(device, stmt); // Устанавливаем первые 12 параметров
            stmt.setLong(13, device.getUpdatedAt()); // 13-й - updated_at
            stmt.setInt(14, device.getId()); // 14-й - id для WHERE
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Ошибка обновления прибора: {}", e.getMessage(), e);
            return false;
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
            LOGGER.error("Ошибка удаления прибора: {}", e.getMessage(), e);
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
                return createDeviceSQL(rs);
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка поиска прибора: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Получение прибора по ID (для SchemeEditor)
     */
    public Device getDeviceById(int id) {
        String sql = "SELECT * FROM devices WHERE id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return createDeviceSQL(rs);
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения прибора по ID: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Получение списка уникальных локаций
     * @return - список уникальных локаций
     */
    public List<String> getDistinctLocations() {
        List<String> locations = new ArrayList<>();
        String sql = "SELECT DISTINCT location FROM devices WHERE location IS NOT NULL AND location <> '' ORDER BY location";
        try (Statement stmt = databaseService.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                locations.add(rs.getString("location"));
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения уникальных локаций: {}", e.getMessage(), e);
        }
        return locations;
    }

    /**
     * ★★★ НОВЫЙ: Получение всех приборов для экспорта (с сортировкой)
     */
    public List<Device> getAllDevicesForExport() {
        List<Device> devices = new ArrayList<>();
        String sql = "SELECT * FROM devices ORDER BY id";
        try (Statement stmt = databaseService.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                devices.add(createDeviceSQL(rs));
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения приборов для экспорта: {}", e.getMessage(), e);
        }
        return devices;
    }

    /**
     * Вспомогательный метод для создания объекта Device из ResultSet
     */
    private Device createDeviceSQL(ResultSet rs) throws SQLException {
        Device device = new Device();
        device.setId(rs.getInt("id"));
        device.setType(rs.getString("type"));
        device.setName(rs.getString("name"));
        device.setManufacturer(rs.getString("manufacturer"));
        device.setInventoryNumber(rs.getString("inventory_number"));

        Object yearObj = rs.getObject("year");
        device.setYear(yearObj != null ? (Integer) yearObj : null);

        device.setMeasurementLimit(rs.getString("measurement_limit"));

        Object accuracyObj = rs.getObject("accuracy_class");
        device.setAccuracyClass(accuracyObj != null ? (Double) accuracyObj : null);

        device.setLocation(rs.getString("location"));
        device.setValveNumber(rs.getString("valve_number"));
        device.setStatus(rs.getString("status"));
        device.setAdditionalInfo(rs.getString("additional_info"));

        String photosStr = rs.getString("photos");
        List<String> photos = stringToPhotos(photosStr);
        device.setPhotos(photos);

        // ★★★ НОВОЕ: читаем updated_at
        device.setUpdatedAt(rs.getLong("updated_at"));

        return device;
    }

    /**
     * Вспомогательный метод для установки параметров PreparedStatement
     * Порядок: 1-12 для полей (соответствует addDevice и updateDevice)
     */
    private void installParameters(Device device, PreparedStatement stmt) throws SQLException {
        int idx = 1;
        stmt.setString(idx++, device.getType());
        stmt.setString(idx++, device.getName());
        stmt.setString(idx++, device.getManufacturer());
        stmt.setString(idx++, device.getInventoryNumber());

        if (device.getYear() != null) {
            stmt.setInt(idx++, device.getYear());
        } else {
            stmt.setNull(idx++, Types.INTEGER);
        }

        stmt.setString(idx++, device.getMeasurementLimit());

        if (device.getAccuracyClass() != null) {
            stmt.setDouble(idx++, device.getAccuracyClass());
        } else {
            stmt.setNull(idx++, Types.DOUBLE);
        }

        stmt.setString(idx++, device.getLocation());
        stmt.setString(idx++, device.getValveNumber());
        stmt.setString(idx++, device.getStatus());
        stmt.setString(idx++, device.getAdditionalInfo());

        List<String> photos = device.getPhotos() != null ? device.getPhotos() : new ArrayList<>();
        stmt.setString(idx++, photosToString(photos));
    }
}