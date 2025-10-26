package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Scheme;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final Logger LOGGER = Logger.getLogger(SchemeDAO.class.getName());

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
        String sql = "INSERT INTO schemes (name, description, data) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, scheme.getName());
            stmt.setString(2, scheme.getDescription());
            stmt.setString(3, scheme.getData());
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                // Получить сгенерированный ID и установить в объект
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    scheme.setId(keys.getInt(1));
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Ошибка добавления схемы: " + e.getMessage());
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
            LOGGER.warning("DAO updateScheme: Invalid scheme (null or id <=0)");
            return false;
        }

        // Фикс: Таблица 'schemes' (как в INSERT/SELECT), + description в SET
        String sql = "UPDATE schemes SET name=?, description=?, data=? WHERE id=?";

        try (PreparedStatement pstmt = databaseService.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, scheme.getName() != null ? scheme.getName() : "");
            pstmt.setString(2, scheme.getDescription() != null ? scheme.getDescription() : "");  // Добавлено: description
            pstmt.setString(3, scheme.getData() != null ? scheme.getData() : "{}");
            pstmt.setInt(4, scheme.getId());

            int rows = pstmt.executeUpdate();
            boolean success = rows > 0;

            // Улучшенный лог: Безопасный (escape для data, если long), всегда выводится
            String dataPreview = scheme.getData() != null && scheme.getData().length() > 50
                    ? scheme.getData().substring(0, 47) + "..."
                    : (scheme.getData() != null ? scheme.getData() : "{}");
            System.out.println("DAO updateScheme: Executing UPDATE schemes SET name='" + scheme.getName() +
                    "', description len=" + (scheme.getDescription() != null ? scheme.getDescription().length() : 0) +
                    "', data preview='" + dataPreview + "' WHERE id=" + scheme.getId() +
                    " → rows affected=" + rows + ", success=" + success);

            if (!success) {
                // Warning: Если rows=0 — id не найден (но таблица OK)
                System.out.println("WARNING: No rows updated for scheme ID=" + scheme.getId() +
                        " — check if exists (SELECT * FROM schemes WHERE id=" + scheme.getId() + ")");
                LOGGER.warning("Update failed: 0 rows for scheme ID=" + scheme.getId());
            }

            return success;
        } catch (SQLException e) {
            // Полный лог: Класс, message, state/code
            System.err.println("DAO updateScheme ERROR for scheme ID=" + scheme.getId() + " (name='" + scheme.getName() + "'):");
            System.err.println("SQL: " + sql);
            System.err.println("Exception: " + e.getClass().getSimpleName() + " - Message: " + e.getMessage());
            if (e.getSQLState() != null) System.err.println("SQL State: " + e.getSQLState() + ", Error Code: " + e.getErrorCode());
            e.printStackTrace();
            LOGGER.log(Level.SEVERE, "SQLException in updateScheme: " + e.getMessage(), e);

            // Если table-related — подсказка
            if (e.getMessage().toLowerCase().contains("scheme") || e.getMessage().toLowerCase().contains("table")) {
                System.err.println("Likely table name issue — ensure 'schemes' table exists (run CREATE in DB tool)");
            }

            return false;
        }
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
            LOGGER.log(Level.SEVERE, "Ошибка поиска схемы: " + e.getMessage());
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
        return scheme;
    }
}