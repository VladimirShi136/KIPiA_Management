package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Scheme;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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
     * Получение списка всех схем из базы данных
     * @return список объектов Scheme, отсортированный по названию
     */
    public List<Scheme> getAllSchemes() {
        List<Scheme> schemes = new ArrayList<>();
        String sql = "SELECT * FROM schemes ORDER BY name";
        try (Statement stmt = databaseService.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                schemes.add(createSchemeFromResultSet(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Ошибка получения схем: " + e.getMessage());
        }
        return schemes;
    }

    /**
     * Обновление данных схемы в базе данных
     *
     * @param scheme объект схемы с обновленными данными
     */
    public void updateScheme(Scheme scheme) {
        String sql = "UPDATE schemes SET name = ?, description = ?, data = ? WHERE id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            stmt.setString(1, scheme.getName());
            stmt.setString(2, scheme.getDescription());
            stmt.setString(3, scheme.getData());  // ОБЯЗАТЕЛЬНО: сохраняем data
            stmt.setInt(4, scheme.getId());
            int rowsAffected = stmt.executeUpdate();  // НОВОЕ: выполнение запроса
            LOGGER.log(Level.INFO, "Update scheme rows affected: " + rowsAffected);  // НОВОЕ: логирование
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Ошибка обновления схемы: " + e.getMessage());
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
     * Получение схемы по ID
     * @param id идентификатор схемы
     * @return объект Scheme если найден, null - иначе
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
            LOGGER.log(Level.SEVERE, "Ошибка получения схемы по ID: " + e.getMessage());
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