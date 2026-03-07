package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Scheme;
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
    // Сервис для работы с базой данных
    private final DatabaseService databaseService;
    // логгер для сообщений
    private static final Logger LOGGER = LogManager.getLogger(SchemeDAO.class);

    /**
     * Конструктор класса SchemeDAO
     * @param databaseService экземпляр сервиса для работы с БД
     */
    public SchemeDAO(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * Добавление новой схемы в базу данных
     * @param scheme объект схемы для добавления
     * @return true - если добавление прошло успешно, false - в случае ошибки
     */
    public boolean addScheme(Scheme scheme) {
        scheme.updateTimestamp(); // Обновляем timestamp

        String sql = "INSERT INTO schemes (name, description, data, updated_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, scheme.getName());
            stmt.setString(2, scheme.getDescription());
            stmt.setString(3, scheme.getData());
            stmt.setLong(4, scheme.getUpdatedAt()); // Новое поле

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
     * Обновление данных схемы в базе данных
     *
     * @param scheme объект схемы с обновленными данными
     * @return true если обновлено (rows >0), false - ошибка
     */
    public boolean updateScheme(Scheme scheme) {
        if (scheme == null || scheme.getId() <= 0) {
            LOGGER.warn("DAO updateScheme: Invalid scheme (null or id <=0)");
            return false;
        }

        scheme.updateTimestamp(); // Обновляем timestamp

        String sql = "UPDATE schemes SET name=?, description=?, data=?, updated_at=? WHERE id=?";

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
                pstmt.setLong(4, scheme.getUpdatedAt()); // Новое поле
                pstmt.setInt(5, scheme.getId());

                int rows = pstmt.executeUpdate();
                boolean success = rows > 0;

                LOGGER.info("Схема обновлена: {} (ID: {}), строк затронуто: {}",
                        scheme.getName(), scheme.getId(), rows);

                return success;
            }
        } catch (SQLException e) {
            LOGGER.error("SQLException in updateScheme: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * ★★★ НОВЫЙ: Получение всех схем для экспорта
     */
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

    /**
     * ★★★ НОВЫЙ: Массовое добавление/обновление схем с проверкой updated_at
     */
    public void insertOrUpdateSchemes(List<Scheme> schemes) {
        String checkSql = "SELECT updated_at FROM schemes WHERE id = ?";
        String updateSql = "UPDATE schemes SET name=?, description=?, data=?, updated_at=? WHERE id=?";
        String insertSql = "INSERT INTO schemes (id, name, description, data, updated_at) VALUES (?, ?, ?, ?, ?)";

        try {
            Connection conn = databaseService.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                 PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                for (Scheme scheme : schemes) {
                    checkStmt.setInt(1, scheme.getId());
                    ResultSet rs = checkStmt.executeQuery();

                    if (rs.next()) {
                        long existingUpdatedAt = rs.getLong("updated_at");
                        if (scheme.getUpdatedAt() > existingUpdatedAt) {
                            updateStmt.setString(1, scheme.getName());
                            updateStmt.setString(2, scheme.getDescription());
                            updateStmt.setString(3, scheme.getData());
                            updateStmt.setLong(4, scheme.getUpdatedAt());
                            updateStmt.setInt(5, scheme.getId());
                            updateStmt.addBatch();
                        }
                    } else {
                        insertStmt.setInt(1, scheme.getId());
                        insertStmt.setString(2, scheme.getName());
                        insertStmt.setString(3, scheme.getDescription());
                        insertStmt.setString(4, scheme.getData());
                        insertStmt.setLong(5, scheme.getUpdatedAt());
                        insertStmt.addBatch();
                    }
                }

                updateStmt.executeBatch();
                insertStmt.executeBatch();

                conn.commit();
                LOGGER.info("Импорт схем завершён, обновлено/добавлено: {}", schemes.size());

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            LOGGER.error("Ошибка при массовом импорте схем: {}", e.getMessage(), e);
        }
    }

    /**
     * ★★★ НОВЫЙ: Получить максимальную дату обновления схем
     */
    public Long getMaxUpdatedAt() {
        String sql = "SELECT MAX(updated_at) FROM schemes";
        try (Statement stmt = databaseService.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения MAX(updated_at) схем: {}", e.getMessage(), e);
        }
        return 0L;
    }

    /**
     * ★★★ НОВЫЙ: Получение схемы по ID
     */
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

    /**
     * Поиск схемы по названию
     * @param name название схемы для поиска
     * @return объект Scheme если найден, null - если не найден или произошла ошибка
     */
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

    /**
     * Вспомогательный метод для создания объекта Scheme из ResultSet
     * @param rs ResultSet из запроса
     * @return объект Scheme
     * @throws SQLException при ошибке чтения
     */
    private Scheme createSchemeFromResultSet(ResultSet rs) throws SQLException {
        Scheme scheme = new Scheme();
        scheme.setId(rs.getInt("id"));
        scheme.setName(rs.getString("name"));
        scheme.setDescription(rs.getString("description"));
        scheme.setData(rs.getString("data"));
        scheme.setUpdatedAt(rs.getLong("updated_at")); // Новое поле
        return scheme;
    }
}