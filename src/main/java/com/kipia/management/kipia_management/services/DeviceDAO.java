package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.utils.TimeValidator;
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
    private final DatabaseService databaseService;
    private static final Logger LOGGER = LogManager.getLogger(DeviceDAO.class);

    public DeviceDAO(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    private String photosToString(List<String> photos) {
        if (photos == null || photos.isEmpty()) return "";
        List<String> fileNames = new ArrayList<>();
        for (String photo : photos) {
            if (photo != null && !photo.trim().isEmpty()) {
                File file = new File(photo);
                fileNames.add(file.getName());
            }
        }
        return String.join(";", fileNames);
    }

    private List<String> stringToPhotos(String photosStr) {
        if (photosStr == null || photosStr.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(photosStr.split(";")));
    }

    /**
     * Добавление нового прибора (автоматически обновляет updated_at)
     */
    public boolean addDevice(Device device) {
        return addDevice(device, true);
    }

    /**
     * Добавление нового прибора с контролем обновления timestamp
     * @param device прибор для добавления
     * @param updateTimestamp если true - обновляет updated_at, если false - оставляет как есть
     */
    public boolean addDevice(Device device, boolean updateTimestamp) {
        if (!TimeValidator.getInstance().validateTimeForWrite()) {
            LOGGER.error("Добавление прибора заблокировано: проблема с системным временем");
            return false;
        }
        
        if (updateTimestamp) {
            device.updateTimestamp();
        }

        String sql = "INSERT INTO devices (type, name, manufacturer, inventory_number, year, measurement_limit, " +
                "accuracy_class, location, valve_number, status, additional_info, photos, updated_at, deleted_at, last_synced_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            installParameters(device, stmt);
            stmt.setLong(13, device.getUpdatedAt());
            stmt.setLong(14, device.getDeletedAt());
            stmt.setLong(15, device.getLastSyncedAt());
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        device.setId(keys.getInt(1));
                    }
                }
            }
            return rowsAffected > 0;
        } catch (SQLException e) {
            LOGGER.error("Ошибка добавления прибора: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Обновление данных прибора (автоматически обновляет updated_at)
     */
    public boolean updateDevice(Device device) {
        return updateDevice(device, true);
    }

    /**
     * Обновление данных прибора с контролем обновления timestamp
     * @param device прибор для обновления
     * @param updateTimestamp если true - обновляет updated_at, если false - оставляет как есть
     */
    public boolean updateDevice(Device device, boolean updateTimestamp) {
        if (!TimeValidator.getInstance().validateTimeForWrite()) {
            LOGGER.error("Обновление прибора заблокировано: проблема с системным временем");
            return false;
        }
        
        if (updateTimestamp) {
            device.updateTimestamp();
        }

        String sql = "UPDATE devices SET type = ?, name = ?, manufacturer = ?, inventory_number = ?, " +
                "year = ?, measurement_limit = ?, accuracy_class = ?, location = ?, valve_number = ?, " +
                "status = ?, additional_info = ?, photos = ?, updated_at = ?, deleted_at = ?, last_synced_at = ? WHERE id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            installParameters(device, stmt);
            stmt.setLong(13, device.getUpdatedAt());
            stmt.setLong(14, device.getDeletedAt());
            stmt.setLong(15, device.getLastSyncedAt());
            stmt.setInt(16, device.getId());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Ошибка обновления прибора: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Удаление прибора (soft delete)
     */
    public boolean deleteDevice(int id) {
        if (!TimeValidator.getInstance().validateTimeForWrite()) {
            LOGGER.error("Удаление прибора заблокировано: проблема с системным временем");
            return false;
        }
        
        String sql = "UPDATE devices SET deleted_at = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            stmt.setLong(1, now);
            stmt.setLong(2, now);
            stmt.setInt(3, id);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Ошибка удаления прибора: {}", e.getMessage(), e);
            return false;
        }
    }

    public List<Device> getAllDevices() {
        List<Device> devices = new ArrayList<>();
        String sql = "SELECT * FROM devices WHERE deleted_at = 0 ORDER BY name";
        try (Statement stmt = databaseService.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                devices.add(createDeviceSQL(rs));
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения приборов: {}", e.getMessage(), e);
        }
        return devices;
    }

    public Device findDeviceByInventoryNumber(String inventoryNumber) {
        String sql = "SELECT * FROM devices WHERE inventory_number = ? AND deleted_at = 0";
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

    public Device getDeviceById(int id) {
        String sql = "SELECT * FROM devices WHERE id = ? AND deleted_at = 0";
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

    public List<String> getDistinctLocations() {
        List<String> locations = new ArrayList<>();
        String sql = "SELECT DISTINCT location FROM devices WHERE location IS NOT NULL AND location <> '' AND deleted_at = 0 ORDER BY location";
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

        device.setUpdatedAt(rs.getLong("updated_at"));
        device.setDeletedAt(rs.getLong("deleted_at"));
        device.setLastSyncedAt(rs.getLong("last_synced_at"));

        return device;
    }

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