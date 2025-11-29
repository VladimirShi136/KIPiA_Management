package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Scheme;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.sql.*;


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

        String sql = "UPDATE schemes SET name=?, description=?, data=? WHERE id=?";

        try {
            // Получаем соединение через getConnection() который гарантирует активное соединение
            Connection conn = databaseService.getConnection();

            // Дополнительная проверка
            if (conn == null || conn.isClosed()) {
                LOGGER.error("Не удалось получить активное соединение с БД");
                return false;
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, scheme.getName() != null ? scheme.getName() : "");
                pstmt.setString(2, scheme.getDescription() != null ? scheme.getDescription() : "");
                pstmt.setString(3, scheme.getData() != null ? scheme.getData() : "{}");
                pstmt.setInt(4, scheme.getId());

                int rows = pstmt.executeUpdate();
                boolean success = rows > 0;

                // Логирование для отладки
                LOGGER.info("Схема обновлена: {} (ID: {}), строк затронуто: {}", scheme.getName(), scheme.getId(), rows);

                return success;
            }
        } catch (SQLException e) {
            LOGGER.error("SQLException in updateScheme: {}", e.getMessage(), e);
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
        return scheme;
    }
}