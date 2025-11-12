package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.DeviceLocation;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

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
    private static final Logger LOGGER = Logger.getLogger(DeviceLocationDAO.class.getName());

    public DeviceLocationDAO(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * Добавление новой привязки прибора к схеме
     *
     * @param location объект привязки для добавления
     * @return true если успешно, false если ошибка
     */
    public boolean addDeviceLocation(DeviceLocation location) {
        String sql = "INSERT INTO device_locations (device_id, scheme_id, x, y, rotation) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(device_id, scheme_id) DO UPDATE SET x = excluded.x, y = excluded.y, rotation = excluded.rotation";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, location.getDeviceId());
            stmt.setInt(2, location.getSchemeId());
            stmt.setDouble(3, location.getX());
            stmt.setDouble(4, location.getY());
            stmt.setDouble(5, location.getRotation()); // Добавлено

            int rowsAffected = stmt.executeUpdate();

            boolean success = rowsAffected > 0;
            if (success) {
                LOGGER.info("Успешно добавлена/обновлена локация: device_id=" + location.getDeviceId() +
                        ", scheme_id=" + location.getSchemeId() + ", x=" + location.getX() +
                        ", y=" + location.getY() + ", rotation=" + location.getRotation());
            }
            return success;
        } catch (SQLException e) {
            LOGGER.severe("Ошибка добавления локации: " + e.getMessage());
            return false;
        }
    }

    /**
     * Удаление привязки прибора к схеме по device_id и scheme_id
     *
     * @param deviceId ID прибора
     * @param schemeId ID схемы
     */
    public void deleteDeviceLocation(int deviceId, int schemeId) {
        String sql = "DELETE FROM device_locations WHERE device_id = ? AND scheme_id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, deviceId);
            stmt.setInt(2, schemeId);
            int rowsAffected = stmt.executeUpdate();
            boolean success = rowsAffected > 0;
            if (success) {
                LOGGER.info("Успешно удалена локация: device_id=" + deviceId + ", scheme_id=" + schemeId);
            } else {
                LOGGER.warning("Локация не найдена для удаления: device_id=" + deviceId + ", scheme_id=" + schemeId);
            }
        } catch (SQLException e) {
            LOGGER.severe("Ошибка удаления локации: " + e.getMessage());
        }
    }

    /**
     * Удаление всех привязок для конкретной схемы
     * @param schemeId - ID схемы
     * @return - количество удаленных записей
     */
    public int deleteAllLocationsForScheme(int schemeId) {
        String sql = "DELETE FROM device_locations WHERE scheme_id = ?";
        try {
            Connection conn = databaseService.getConnection();
            if (conn == null || conn.isClosed()) {
                LOGGER.severe("Нет соединения с БД для удаления приборов схемы " + schemeId);
                return 0;
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, schemeId);
                int deletedCount = pstmt.executeUpdate();
                LOGGER.info("Удалено приборов для схемы " + schemeId + ": " + deletedCount);
                return deletedCount;
            }
        } catch (SQLException e) {
            LOGGER.severe("Ошибка удаления приборов для схемы " + schemeId + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Получение списка привязок для конкретной схемы
     *
     * @param schemeId ID схемы
     * @return список объектов DeviceLocation для схемы
     */
    public List<DeviceLocation> getLocationsBySchemeId(int schemeId) {
        List<DeviceLocation> locations = new ArrayList<>();
        String sql = "SELECT * FROM device_locations WHERE scheme_id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, schemeId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                DeviceLocation loc = new DeviceLocation();
                loc.setDeviceId(rs.getInt("device_id"));
                loc.setSchemeId(rs.getInt("scheme_id"));
                loc.setX(rs.getDouble("x"));
                loc.setY(rs.getDouble("y"));
                loc.setRotation(rs.getDouble("rotation"));
                locations.add(loc);
            }
            LOGGER.fine("Загружено локаций для схемы " + schemeId + ": " + locations.size());
        } catch (SQLException e) {
            LOGGER.severe("Ошибка получения локаций: " + e.getMessage());
        }
        return locations;
    }

    /**
     * Получение списка всех привязок приборов к схемам.
     *
     * @return список всех объектов DeviceLocation
     */
    public List<DeviceLocation> getAllLocations() {
        List<DeviceLocation> locations = new ArrayList<>();
        String sql = "SELECT * FROM device_locations";
        try (Statement stmt = databaseService.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                DeviceLocation loc = new DeviceLocation();
                loc.setDeviceId(rs.getInt("device_id"));
                loc.setSchemeId(rs.getInt("scheme_id"));
                loc.setX(rs.getDouble("x"));
                loc.setY(rs.getDouble("y"));
                loc.setRotation(rs.getDouble("rotation"));
                locations.add(loc);
            }
            LOGGER.fine("Загружено всех локаций: " + locations.size());
        } catch (SQLException e) {
            LOGGER.severe("Ошибка получения всех локаций: " + e.getMessage());
        }
        return locations;
    }
}
