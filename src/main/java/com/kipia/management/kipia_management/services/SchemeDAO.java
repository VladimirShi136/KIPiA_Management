package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.utils.TimeValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;


/**
 * Класс SchemeDAO (Data Access Object) предоставляет методы для работы с данными схем в базе данных.
 * Реализует основные CRUD-операции для таблицы schemes.
 *
 * @author vladimir_shi
 * @since 30.09.2025
 */

public class SchemeDAO {
    private final DatabaseService databaseService;
    private static final Logger LOGGER = LogManager.getLogger(SchemeDAO.class);

    public SchemeDAO(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * Добавление новой схемы (автоматически обновляет updated_at)
     */
    public boolean addScheme(Scheme scheme) {
        return addScheme(scheme, true);
    }

    /**
     * Добавление новой схемы с контролем обновления timestamp
     * @param scheme схема для добавления
     * @param updateTimestamp если true - обновляет updated_at, если false - оставляет как есть
     */
    public boolean addScheme(Scheme scheme, boolean updateTimestamp) {
        if (!TimeValidator.getInstance().validateTimeForWrite()) {
            LOGGER.error("Добавление схемы заблокировано: проблема с системным временем");
            return false;
        }
        
        if (updateTimestamp) {
            scheme.updateTimestamp();
        }

        String sql = "INSERT INTO schemes (name, description, data, updated_at, deleted_at, last_synced_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, scheme.getName());
            stmt.setString(2, scheme.getDescription());
            stmt.setString(3, scheme.getData());
            stmt.setLong(4, scheme.getUpdatedAt());
            stmt.setLong(5, scheme.getDeletedAt());
            stmt.setLong(6, scheme.getLastSyncedAt());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    scheme.setId(keys.getInt(1));
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            LOGGER.error("Ошибка добавления схемы: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Обновление данных схемы (автоматически обновляет updated_at)
     */
    public boolean updateScheme(Scheme scheme) {
        return updateScheme(scheme, true);
    }

    /**
     * Обновление данных схемы с контролем обновления timestamp
     * @param scheme схема для обновления
     * @param updateTimestamp если true - обновляет updated_at, если false - оставляет как есть
     */
    public boolean updateScheme(Scheme scheme, boolean updateTimestamp) {
        if (!TimeValidator.getInstance().validateTimeForWrite()) {
            LOGGER.error("Обновление схемы заблокировано: проблема с системным временем");
            return false;
        }
        
        if (scheme == null || scheme.getId() <= 0) {
            LOGGER.warn("DAO updateScheme: Invalid scheme (null or id <=0)");
            return false;
        }

        if (updateTimestamp) {
            scheme.updateTimestamp();
        }

        String sql = "UPDATE schemes SET name=?, description=?, data=?, updated_at=?, deleted_at=?, last_synced_at=? WHERE id=?";

        try {
            Connection conn = databaseService.getConnection();
            if (conn == null || conn.isClosed()) {
                LOGGER.error("Не удалось получить активное соединение с БД");
                return false;
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, scheme.getName() != null ? scheme.getName() : "");
                pstmt.setString(2, scheme.getDescription() != null ? scheme.getDescription() : "");
                pstmt.setString(3, scheme.getData() != null ? scheme.getData() : "{}");
                pstmt.setLong(4, scheme.getUpdatedAt());
                pstmt.setLong(5, scheme.getDeletedAt());
                pstmt.setLong(6, scheme.getLastSyncedAt());
                pstmt.setInt(7, scheme.getId());

                int rows = pstmt.executeUpdate();
                LOGGER.info("Схема обновлена: {} (ID: {}), строк затронуто: {}", scheme.getName(), scheme.getId(), rows);
                return rows > 0;
            }
        } catch (SQLException e) {
            LOGGER.error("SQLException in updateScheme: {}", e.getMessage(), e);
            return false;
        }
    }

    public List<Scheme> getAllSchemesForExport() {
        List<Scheme> schemes = new ArrayList<>();
        String sql = "SELECT * FROM schemes ORDER BY id";
        try (Statement stmt = databaseService.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                schemes.add(createSchemeFromResultSet(rs));
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения схем для экспорта: {}", e.getMessage(), e);
        }
        return schemes;
    }

    public List<Scheme> getAllSchemes() {
        List<Scheme> schemes = new ArrayList<>();
        String sql = "SELECT * FROM schemes WHERE deleted_at = 0 ORDER BY name";
        try (Statement stmt = databaseService.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                schemes.add(createSchemeFromResultSet(rs));
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения всех схем: {}", e.getMessage(), e);
        }
        return schemes;
    }

    public Scheme getSchemeById(int id) {
        String sql = "SELECT * FROM schemes WHERE id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return createSchemeFromResultSet(rs);
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения схемы по ID: {}", e.getMessage(), e);
        }
        return null;
    }

    public Scheme findSchemeByName(String name) {
        String sql = "SELECT * FROM schemes WHERE name = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return createSchemeFromResultSet(rs);
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка поиска схемы: {}", e.getMessage(), e);
        }
        return null;
    }

    public boolean deleteScheme(int schemeId) {
        if (!TimeValidator.getInstance().validateTimeForWrite()) {
            LOGGER.error("Удаление схемы заблокировано: проблема с системным временем");
            return false;
        }
        
        String sql = "UPDATE schemes SET deleted_at = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            stmt.setLong(1, now);
            stmt.setLong(2, now);
            stmt.setInt(3, schemeId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                LOGGER.info("✅ Схема удалена (soft delete): ID={}", schemeId);
                return true;
            }
            return false;
        } catch (SQLException e) {
            LOGGER.error("❌ Ошибка удаления схемы: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean hasDevicesOnScheme(int schemeId) {
        Scheme scheme = getSchemeById(schemeId);
        if (scheme == null) {
            return false;
        }

        String sql = "SELECT COUNT(*) FROM devices WHERE location = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setString(1, scheme.getName());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка проверки приборов на схеме: {}", e.getMessage(), e);
        }
        return false;
    }

    private Scheme createSchemeFromResultSet(ResultSet rs) throws SQLException {
        Scheme scheme = new Scheme();
        scheme.setId(rs.getInt("id"));
        scheme.setName(rs.getString("name"));
        scheme.setDescription(rs.getString("description"));
        scheme.setData(rs.getString("data"));
        scheme.setUpdatedAt(rs.getLong("updated_at"));
        scheme.setDeletedAt(rs.getLong("deleted_at"));
        scheme.setLastSyncedAt(rs.getLong("last_synced_at"));
        return scheme;
    }
}