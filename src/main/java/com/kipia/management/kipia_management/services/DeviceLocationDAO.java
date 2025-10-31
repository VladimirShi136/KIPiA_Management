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
    // Сервис для работы с БД
    private final DatabaseService databaseService;
    private static final Logger LOGGER = Logger.getLogger(DeviceLocationDAO.class.getName());

    /**
     * Конструктор класса DeviceLocationDAO
     *
     * @param databaseService экземпляр сервиса для работы с БД
     */
    public DeviceLocationDAO(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * Добавление новой привязки прибора к схеме
     *
     * @param location объект привязки для добавления
     */
    public void addDeviceLocation(DeviceLocation location) {
        String sql = "INSERT INTO device_locations (device_id, scheme_id, x, y) VALUES (?, ?, ?, ?) ON CONFLICT(device_id, scheme_id) DO UPDATE SET x = excluded.x, y = excluded.y";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, location.getDeviceId());
            stmt.setInt(2, location.getSchemeId());
            stmt.setDouble(3, location.getX());
            stmt.setDouble(4, location.getY());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка добавления локации: " + e.getMessage());
        }
    }

    /**
     * Обновление привязки прибора к схеме (аналогично добавлению, так как ON CONFLICT обновляет)
     *
     * @param location объект привязки с обновленными данными
     */
    public void updateDeviceLocation(DeviceLocation location) {
        addDeviceLocation(location);
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
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка удаления локации: " + e.getMessage());
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
                locations.add(loc);
            }
        } catch (SQLException e) {
            System.out.println("Ошибка получения локаций: " + e.getMessage());
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
                locations.add(loc);
            }
        } catch (SQLException e) {
            System.out.println("Ошибка получения всех локаций: " + e.getMessage());
        }
        return locations;
    }
}
