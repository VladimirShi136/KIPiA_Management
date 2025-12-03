package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.managers.PhotoManager;
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
    private PhotoManager photoManager;

    /**
     * Конструктор класса DeviceDAO
     * @param databaseService экземпляр сервиса для работы с базой данных
     */
    public DeviceDAO(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    // Установить PhotoManager
    public void setPhotoManager(PhotoManager photoManager) {
        this.photoManager = photoManager;
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
        // SQL соответствует полям таблицы (13 параметров)
        String sql = "INSERT INTO devices (type, name, manufacturer, inventory_number, year, measurement_limit, accuracy_class, location, valve_number, status, additional_info, photo_path, photos) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            installParameters(device, stmt);
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
     */
    public void updateDevice(Device device) {
        String sql = "UPDATE devices SET type = ?, name = ?, manufacturer = ?, inventory_number = ?, year = ?, measurement_limit = ?, accuracy_class = ?, location = ?, valve_number = ?, status = ?, additional_info = ?, photo_path = ?, photos = ? WHERE id = ?";
        try (PreparedStatement stmt = databaseService.getConnection().prepareStatement(sql)) {
            installParameters(device, stmt);
            stmt.setInt(14, device.getId());

            // ВЫПОЛНЯЕМ МИГРАЦИЮ ПЕРЕД СОХРАНЕНИЕМ
            if (photoManager != null && device.getPhotoPath() != null && !device.getPhotoPath().isEmpty()) {
                migrateOldPhoto(device);
            }

            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Ошибка обновления прибора: {}", e.getMessage(), e);
        }
    }

    /**
     * ⭐⭐ ИСПРАВЛЕННЫЙ МЕТОД: Миграция старого фото в новую структуру ⭐⭐
     */
    private void migrateOldPhoto(Device device) {
        try {
            String oldPhotoPath = device.getPhotoPath();
            if (oldPhotoPath != null && !oldPhotoPath.trim().isEmpty()) {
                LOGGER.info("🔄 Мигрируем старое фото для устройства {}: {}", device.getId(), oldPhotoPath);

                // ⭐⭐ ИСПРАВЛЕНИЕ: Проверяем список photos ⭐⭐
                List<String> photos = device.getPhotos();
                if (photos == null) {
                    photos = new ArrayList<>();
                    device.setPhotos(photos);
                }

                // Проверяем, не мигрировали ли уже
                boolean alreadyMigrated = photos.stream()
                        .anyMatch(photo -> {
                            File photoFile = new File(photo);
                            return photoFile.getName().contains("device_" + device.getId() + "_");
                        });

                if (!alreadyMigrated && photos.isEmpty()) {
                    // Добавляем метку, что фото нужно мигрировать
                    device.addPhoto("[MIGRATE]" + oldPhotoPath);

                    LOGGER.info("⚠️  Отмечено для миграции: {}", oldPhotoPath);
                }
            }
        } catch (Exception e) {
            LOGGER.error("❌ Ошибка миграции фото для устройства {}: {}", device.getId(), e.getMessage());
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
        device.setPhotoPath(rs.getString("photo_path"));

        // Загружаем список фото
        String photosStr = rs.getString("photos");
        List<String> photos = stringToPhotos(photosStr);
        device.setPhotos(photos);

        return device;
    }

    /**
     * Вспомогательный метод для установки параметров PreparedStatement
     * Порядок: 1-13 для полей (соответствует addDevice и updateDevice)
     */
    private void installParameters(Device device, PreparedStatement stmt) throws SQLException {
        stmt.setString(1, device.getType());
        stmt.setString(2, device.getName());
        stmt.setString(3, device.getManufacturer());
        stmt.setString(4, device.getInventoryNumber());
        if (device.getYear() != null) {
            stmt.setInt(5, device.getYear());
        } else {
            stmt.setNull(5, Types.INTEGER);
        }
        stmt.setString(6, device.getMeasurementLimit());
        if (device.getAccuracyClass() != null) {
            stmt.setDouble(7, device.getAccuracyClass());
        } else {
            stmt.setNull(7, Types.DOUBLE);
        }
        stmt.setString(8, device.getLocation());
        stmt.setString(9, device.getValveNumber());  // Добавлено
        stmt.setString(10, device.getStatus());
        stmt.setString(11, device.getAdditionalInfo());
        stmt.setString(12, device.getPhotoPath());
        stmt.setString(13, photosToString(device.getPhotos() != null ? device.getPhotos() : new ArrayList<>()));  // Безопасность от NPE
    }
}