package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.DeviceLocation;
import com.kipia.management.kipia_management.utils.TimeValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Класс DeviceLocationDAO (Data Access Object) предоставляет методы для работы с данными
 * привязок приборов к схемам в таблице device_locations.
 * Реализует основные CRUD-операции.
 *
 * @author vladimir_shi
 * @since 30.09.2025
 */
public class DeviceLocationDAO {
    private final DatabaseService databaseService;
    private static final Logger LOGGER = LogManager.getLogger(DeviceLocationDAO.class);

    public DeviceLocationDAO(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * Добавление/обновление координат прибора (автоматически обновляет updated_at)
     */
    public boolean addDeviceLocation(DeviceLocation location) {
        return addDeviceLocation(location, true);
    }

    /**
     * Добавление/обновление координат прибора с контролем обновления timestamp
     *
     * @param location        координаты для добавления/обновления
     * @param updateTimestamp если true — обновляет updated_at, иначе оставляет как есть
     */
    public boolean addDeviceLocation(DeviceLocation location, boolean updateTimestamp) {
        if (!TimeValidator.getInstance().validateTimeForWrite()) {
            LOGGER.error("Добавление приборов заблокировано: проблема с системным временем");
            return false;
        }

        if (updateTimestamp) {
            location.updateTimestamp();
        }

        String sql = "INSERT INTO device_locations (device_id, scheme_id, x, y, rotation, updated_at, deleted_at, last_synced_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(device_id, scheme_id) DO UPDATE SET " +
                "x = excluded.x, y = excluded.y, rotation = excluded.rotation, " +
                "updated_at = excluded.updated_at, deleted_at = excluded.deleted_at, last_synced_at = excluded.last_synced_at";

        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, location.getDeviceId());
            stmt.setInt(2, location.getSchemeId());
            stmt.setDouble(3, location.getX());
            stmt.setDouble(4, location.getY());
            stmt.setDouble(5, location.getRotation());
            stmt.setLong(6, location.getUpdatedAt());
            stmt.setLong(7, location.getDeletedAt());
            stmt.setLong(8, location.getLastSyncedAt());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                LOGGER.debug("Успешно добавлены/обновлены координаты: device_id={}, scheme_id={}",
                        location.getDeviceId(), location.getSchemeId());
            }
            return rowsAffected > 0;
        } catch (SQLException e) {
            LOGGER.error("Ошибка добавления прибора: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Мягкое удаление координат прибора (soft delete).
     */
    public void deleteDeviceLocation(int deviceId, int schemeId) {
        if (!TimeValidator.getInstance().validateTimeForWrite()) {
            LOGGER.error("Удаление приборов заблокировано: проблема с системным временем");
            return;
        }

        String sql = "UPDATE device_locations SET deleted_at = ?, updated_at = ? WHERE device_id = ? AND scheme_id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            stmt.setLong(1, now);
            stmt.setLong(2, now);
            stmt.setInt(3, deviceId);
            stmt.setInt(4, schemeId);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                LOGGER.info("Успешно удален прибор (soft delete): device_id={}, scheme_id={}", deviceId, schemeId);
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка удаления прибора: {}", e.getMessage());
        }
    }

    /**
     * Полное физическое удаление всех локаций схемы.
     * Защищено валидацией времени.
     */
    public void deleteAllLocationsForScheme(int schemeId) {
        if (!TimeValidator.getInstance().validateTimeForWrite()) {
            LOGGER.error("Удаление всех приборов из схемы {} заблокировано: проблема с системным временем", schemeId);
            return;
        }

        String sql = "DELETE FROM device_locations WHERE scheme_id = ?";
        try {
            Connection conn = databaseService.getConnection();
            if (conn == null || conn.isClosed()) {
                LOGGER.error("Нет соединения с БД для удаления приборов схемы {}", schemeId);
                return;
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, schemeId);
                int deletedCount = pstmt.executeUpdate();
                LOGGER.info("Удалено приборов для схемы {}: {}", schemeId, deletedCount);
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка удаления приборов для схемы {}: {}", schemeId, e.getMessage());
        }
    }

    public List<DeviceLocation> getLocationsBySchemeId(int schemeId) {
        List<DeviceLocation> locations = new ArrayList<>();
        String sql = "SELECT * FROM device_locations WHERE scheme_id = ? AND deleted_at = 0";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, schemeId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                locations.add(createDeviceLocationFromResultSet(rs));
            }
            LOGGER.debug("Загружено приборов для схемы {}: {}", schemeId, locations.size());
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения приборов: {}", e.getMessage());
        }
        return locations;
    }

    public List<DeviceLocation> getAllLocations() {
        List<DeviceLocation> locations = new ArrayList<>();
        String sql = "SELECT * FROM device_locations WHERE deleted_at = 0";
        try (Statement stmt = databaseService.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                locations.add(createDeviceLocationFromResultSet(rs));
            }
            LOGGER.debug("Загружено всех приборов: {}", locations.size());
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения всех приборов: {}", e.getMessage());
        }
        return locations;
    }

    private DeviceLocation createDeviceLocationFromResultSet(ResultSet rs) throws SQLException {
        DeviceLocation location = new DeviceLocation();
        location.setDeviceId(rs.getInt("device_id"));
        location.setSchemeId(rs.getInt("scheme_id"));
        location.setX(rs.getDouble("x"));
        location.setY(rs.getDouble("y"));
        location.setRotation(rs.getDouble("rotation"));
        location.setUpdatedAt(rs.getLong("updated_at"));
        location.setDeletedAt(rs.getLong("deleted_at"));
        location.setLastSyncedAt(rs.getLong("last_synced_at"));
        return location;
    }
}