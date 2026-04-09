package com.kipia.management.kipia_management.managers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Утилитный класс-менеджер для управления фотографиями приборов
 *
 * @author vladimir_shi
 * @since 30.11.2025
 */
public class PhotoManager {
    private static final Logger LOGGER = LogManager.getLogger(PhotoManager.class);
    private static final String LAST_PHOTO_DIR_KEY = "last_photo_directory";
    private static final String PHOTOS_DIR_NAME = "device_photos";

    // ⭐⭐ Статический экземпляр  ⭐⭐
    private static final PhotoManager INSTANCE = new PhotoManager();

    private File lastPhotoDirectory;
    private final String basePhotosPath;
    private DeviceDAO deviceDAO;

    // Приватный конструктор
    private PhotoManager() {
        LOGGER.info("🔄 Создание PhotoManager...");
        this.basePhotosPath = getPhotosDirectoryPath();
        restoreLastDirectoryFromPreferences();
        initPhotosDirectory(); // ⭐⭐ Сразу инициализируем папку ⭐⭐
        LOGGER.info("✅ PhotoManager создан (eager initialization)");
    }

    /**
     * Получение экземпляра PhotoManager
     */
    public static PhotoManager getInstance() {
        return INSTANCE;
    }

    public String getBasePhotosPath() {
        return basePhotosPath;
    }

    /**
     * Инициализация с DeviceDAO (опционально, для автосохранения)
     */
    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
        LOGGER.info("✅ DeviceDAO установлен в PhotoManager");
    }

    /**
     * Добавление фото к прибору с простой проверкой дубликатов
     */
    public void addPhotosToDevice(Device device, Stage ownerStage) {
        FileChooser chooser = createFileChooser();
        List<File> files = chooser.showOpenMultipleDialog(ownerStage);

        if (files == null || files.isEmpty()) {
            return;
        }

        saveLastDirectory(files.getFirst());

        int addedCount = 0;
        int duplicateCount = 0;
        int errorCount = 0;

        List<String> existingPhotos = device.getPhotos();
        if (existingPhotos == null) {
            existingPhotos = new ArrayList<>();
            device.setPhotos(existingPhotos);
        }

        for (File file : files) {
            try {
                if (!file.exists() || file.length() == 0) {
                    LOGGER.warn("⚠️ Пропущен невалидный файл: {}", file.getName());
                    errorCount++;
                    continue;
                }

                // Проверка на дубликат по содержимому
                if (isFileDuplicate(file, device)) {
                    LOGGER.info("⚠️ Пропущен дубликат: {}", file.getName());
                    duplicateCount++;
                    continue;
                }

                String storedFileName = copyPhotoToStorage(file, device);
                if (storedFileName == null) {
                    LOGGER.warn("⚠️ Ошибка копирования: {}", file.getName());
                    errorCount++;
                    continue;
                }

                File savedFile = new File(getFullPhotoPath(device, storedFileName));
                if (!savedFile.exists()) {
                    LOGGER.error("❌ Скопированный файл не найден: {}", storedFileName);
                    errorCount++;
                    continue;
                }

                device.addPhoto(storedFileName);
                addedCount++;
                LOGGER.info("✅ Фото добавлено: {} -> {}", file.getName(), storedFileName);

            } catch (Exception ex) {
                LOGGER.error("❌ Ошибка обработки файла {}: {}", file.getName(), ex.getMessage(), ex);
                errorCount++;
            }
        }

        // Сохранение в БД только при успешном добавлении
        if (addedCount > 0 && deviceDAO != null) {
            try {
                deviceDAO.updateDevice(device);
                LOGGER.info("✅ Устройство обновлено в БД (+{} фото)", addedCount);
            } catch (Exception e) {
                LOGGER.error("❌ Ошибка сохранения в БД: {}", e.getMessage(), e);
                CustomAlertDialog.showError("Ошибка БД", "Не удалось сохранить изменения в базе данных.");
            }
        }

        showSimpleResult(addedCount, duplicateCount, errorCount, files.size());
    }

    /**
     * Простой показ результата
     */
    private void showSimpleResult(int added, int duplicates, int errors, int total) {
        StringBuilder message = new StringBuilder();

        if (added > 0) {
            message.append(String.format("✅ Добавлено: %d фото\n", added));
        }

        if (duplicates > 0) {
            message.append(String.format("⚠️ Пропущено дубликатов: %d фото\n", duplicates));
        }

        if (errors > 0) {
            message.append(String.format("❌ Ошибок: %d фото\n", errors));
        }

        if (message.isEmpty()) {
            message.append("Ничего не добавлено\n");
        }

        message.append(String.format("\nВсего выбрано: %d файлов", total));

        CustomAlertDialog.showInfo("Добавление фото", message.toString());
    }

    /**
     * Удалить одно фото устройства
     */
    public boolean deletePhoto(Device device, String photoFileName) {
        try {
            LOGGER.info("🗑️ Начато удаление фото: {} для устройства ID={}", photoFileName, device.getId());

            // 1. Получаем полный путь
            String fullPath = getFullPhotoPath(device, photoFileName);
            if (fullPath == null) {
                LOGGER.warn("⚠️ Не удалось определить путь для фото: {}", photoFileName);
                return false;
            }

            File file = new File(fullPath);
            if (!file.exists()) {
                LOGGER.warn("⚠️ Файл не найден на диске: {}", fullPath);
                // Удаляем запись из списка, даже если файла нет
                device.getPhotos().remove(photoFileName);
                if (deviceDAO != null) {
                    deviceDAO.updateDevice(device);
                }
                return true;
            }

            // 2. Удаляем файл
            boolean deleted = file.delete();
            if (!deleted) {
                LOGGER.error("❌ Не удалось удалить файл: {}", fullPath);
                return false;
            }
            LOGGER.info("✅ Файл удалён с диска: {}", fullPath);

            // 3. Удаляем из списка фото устройства
            device.getPhotos().remove(photoFileName);

            // 4. Обновляем БД
            if (deviceDAO != null) {
                try {
                    deviceDAO.updateDevice(device);
                    LOGGER.info("✅ Устройство обновлено в БД после удаления фото");
                } catch (Exception e) {
                    LOGGER.error("❌ Ошибка сохранения в БД: {}", e.getMessage(), e);
                    return false;
                }
            } else {
                LOGGER.warn("⚠️ DeviceDAO не установлен, обновление БД пропущено");
            }

            // 5. Проверяем и удаляем пустую папку локации
            if (device.getLocation() != null && !device.getLocation().isEmpty()) {
                Path locationDir = Paths.get(basePhotosPath, device.getLocation());
                if (Files.exists(locationDir) && Files.isDirectory(locationDir)) {
                    try (var stream = Files.list(locationDir)) {
                        if (stream.findAny().isEmpty()) {
                            Files.delete(locationDir);
                            LOGGER.info("✅ Папка локации удалена (пустая): {}", locationDir.toAbsolutePath());
                        }
                    } catch (IOException e) {
                        LOGGER.error("❌ Ошибка при проверке папки локации: {}", e.getMessage(), e);
                    }
                }
            }

            return true;

        } catch (Exception e) {
            LOGGER.error("❌ Критическая ошибка при удалении фото: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Перемещает все фото прибора из старой локации в новую.
     * Вызывается при изменении поля location у прибора.
     *
     * @param device прибор с НОВОЙ локацией
     * @param oldLocation старая локация (до изменения)
     * @return количество успешно перемещённых файлов
     */
    public int migratePhotosToNewLocation(Device device, String oldLocation) {
        if (device == null || oldLocation == null || oldLocation.isEmpty()) {
            LOGGER.warn("⚠️ Некорректные параметры для миграции фото");
            return 0;
        }

        String newLocation = device.getLocation();
        if (newLocation == null || newLocation.isEmpty()) {
            LOGGER.warn("⚠️ Новая локация не указана");
            return 0;
        }

        // Если локация не изменилась - ничего не делаем
        if (oldLocation.equals(newLocation)) {
            return 0;
        }

        List<String> photos = device.getPhotos();
        if (photos == null || photos.isEmpty()) {
            LOGGER.info("📸 Нет фото для миграции");
            return 0;
        }

        int migratedCount = 0;
        Path oldLocationDir = Paths.get(basePhotosPath, oldLocation);
        Path newLocationDir = Paths.get(basePhotosPath, newLocation);

        try {
            // Создаём новую папку локации если не существует
            Files.createDirectories(newLocationDir);
            LOGGER.info("📁 Создана папка новой локации: {}", newLocationDir);

            for (String photoFileName : photos) {
                try {
                    Path oldPath = oldLocationDir.resolve(photoFileName);
                    Path newPath = newLocationDir.resolve(photoFileName);

                    if (!Files.exists(oldPath)) {
                        LOGGER.warn("⚠️ Файл не найден для миграции: {}", oldPath);
                        continue;
                    }

                    // Перемещаем файл
                    Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                    migratedCount++;
                    LOGGER.info("✅ Фото перемещено: {} -> {}", oldPath.getFileName(), newLocation);

                } catch (IOException e) {
                    LOGGER.error("❌ Ошибка миграции фото {}: {}", photoFileName, e.getMessage());
                }
            }

            // Удаляем старую папку если она пустая
            if (Files.exists(oldLocationDir) && Files.isDirectory(oldLocationDir)) {
                try (var stream = Files.list(oldLocationDir)) {
                    if (stream.findAny().isEmpty()) {
                        Files.delete(oldLocationDir);
                        LOGGER.info("🗑️ Удалена пустая папка старой локации: {}", oldLocationDir);
                    }
                } catch (IOException e) {
                    LOGGER.warn("⚠️ Не удалось удалить старую папку: {}", e.getMessage());
                }
            }

            LOGGER.info("✅ Миграция завершена: {} фото перемещено из '{}' в '{}'",
                    migratedCount, oldLocation, newLocation);

        } catch (IOException e) {
            LOGGER.error("❌ Ошибка создания папки новой локации: {}", e.getMessage());
        }

        return migratedCount;
    }

    /**
     * Удаляет ВСЕ физические файлы фото устройства.
     * Вызывается при удалении устройства чтобы не оставлять мусор на диске.
     *
     * @return количество успешно удалённых файлов
     */
    public int deleteAllDevicePhotos(Device device) {
        if (device == null) return 0;

        List<String> photos = device.getPhotos();
        if (photos == null || photos.isEmpty()) return 0;

        int deletedCount = 0;

        for (String fileName : photos) {
            try {
                String fullPath = getFullPhotoPath(device, fileName);
                if (fullPath == null) continue;

                File file = new File(fullPath);
                if (file.exists() && file.delete()) {
                    deletedCount++;
                    LOGGER.debug("🗑️ Удалено фото: {}", fileName);
                }
            } catch (Exception e) {
                LOGGER.warn("⚠️ Не удалось удалить фото {}: {}", fileName, e.getMessage());
            }
        }

        // Удаляем папку локации если она стала пустой
        if (device.getLocation() != null && !device.getLocation().isEmpty()) {
            Path locationDir = Paths.get(basePhotosPath, device.getLocation());
            if (Files.exists(locationDir) && Files.isDirectory(locationDir)) {
                try (var stream = Files.list(locationDir)) {
                    if (stream.findAny().isEmpty()) {
                        Files.delete(locationDir);
                        LOGGER.info("✅ Папка локации удалена (пустая): {}", locationDir);
                    }
                } catch (IOException e) {
                    LOGGER.warn("⚠️ Не удалось удалить папку локации: {}", e.getMessage());
                }
            }
        }

        LOGGER.info("🗑️ Удалено {} фото для устройства ID={}", deletedCount, device.getId());
        return deletedCount;
    }

    /**
     * Получение полного пути к файлу фото
     */
    public String getFullPhotoPath(Device device, String fileName) {
        if (device == null || fileName == null || fileName.isEmpty()) {
            return null;
        }

        Path path = Paths.get(basePhotosPath, device.getLocation(), fileName);
        return path.toString();
    }


    /**
     * Просмотр фотографий (упрощенный метод, делегирует PhotoViewer)
     */
    public void viewDevicePhotos(Device device, Stage ownerStage) {
        LOGGER.info("👁️ Просмотр фото для устройства ID={}, Name={}", device.getId(), device.getName());

        List<String> photos = device.getPhotos();
        if (photos == null || photos.isEmpty()) {
            CustomAlertDialog.showInfo("Просмотр фото", "Фотографии не добавлены");
            return;
        }

        new PhotoViewer(this, device, photos, ownerStage).show();
    }

    // ========== PRIVATE METHODS ==========

    private FileChooser createFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите фотографии прибора");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Изображения",
                        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));

        restoreLastDirectoryToChooser(chooser);
        return chooser;
    }

    private void saveLastDirectory(File selectedFile) {
        if (selectedFile != null) {
            lastPhotoDirectory = selectedFile.getParentFile();
            try {
                Preferences.userRoot().put(LAST_PHOTO_DIR_KEY, lastPhotoDirectory.getAbsolutePath());
            } catch (SecurityException e) {
                LOGGER.warn("Не удалось сохранить настройки директории: {}", e.getMessage());
            }
        }
    }

    private void restoreLastDirectoryFromPreferences() {
        try {
            String lastDir = Preferences.userRoot().get(LAST_PHOTO_DIR_KEY, null);
            if (lastDir != null) {
                File dir = new File(lastDir);
                if (dir.exists()) {
                    lastPhotoDirectory = dir;
                }
            }
        } catch (SecurityException e) {
            LOGGER.warn("Не удалось восстановить настройки директории: {}", e.getMessage());
        }
    }

    private void restoreLastDirectoryToChooser(FileChooser chooser) {
        if (lastPhotoDirectory != null && lastPhotoDirectory.exists()) {
            chooser.setInitialDirectory(lastPhotoDirectory);
        } else if (chooser.getInitialDirectory() == null) {
            chooser.setInitialDirectory(new File(System.getProperty("user.home")));
        }
    }

    /**
     * Инициализация директории для фото
     */
    private void initPhotosDirectory() {
        try {
            Path basePath = Paths.get(basePhotosPath);
            Files.createDirectories(basePath);
            LOGGER.info("✅ Корневая папка фото создана: {}", basePath.toAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("❌ Ошибка создания корневой папки фото: {}", e.getMessage(), e);
        }
    }

    /**
     * Определение пути к папке фото
     */
    private String getPhotosDirectoryPath() {
        if ("true".equals(System.getProperty("production"))) {
            LOGGER.info("📁 Режим фото: ПРОДАКШЕН (принудительно через -Dproduction=true)");
            return getProductionPhotosPath();
        }

        if ("true".equals(System.getProperty("development"))) {
            LOGGER.info("📁 Режим фото: РАЗРАБОТКА (принудительно через -Ddevelopment=true)");
            return getDevelopmentPhotosPath();
        }

        String classPath = System.getProperty("java.class.path");
        String javaHome = System.getProperty("java.home");

        boolean isDev = classPath.contains("target/classes") ||
                classPath.contains("idea_rt.jar") ||
                javaHome.contains("IntelliJ") ||
                classPath.contains(".idea") ||
                !isRunningFromJAR();

        if (isDev) {
            LOGGER.info("📁 Режим фото: РАЗРАБОТКА (автоопределение)");
            return getDevelopmentPhotosPath();
        } else {
            LOGGER.info("📁 Режим фото: ПРОДАКШЕН (автоопределение)");
            return getProductionPhotosPath();
        }
    }

    /**
     * Путь для разработки - рядом с проектом
     */
    private String getDevelopmentPhotosPath() {
        String projectDir = System.getProperty("user.dir");
        return projectDir + File.separator + PHOTOS_DIR_NAME;
    }

    /**
     * Путь для продакшена - в AppData
     */
    private String getProductionPhotosPath() {
        String userDataDir = System.getenv("APPDATA") + File.separator + "KIPiA_Management";
        return userDataDir + File.separator + PHOTOS_DIR_NAME;
    }

    /**
     * Проверяем, запущено ли приложение из JAR файла
     */
    private boolean isRunningFromJAR() {
        String className = this.getClass().getName().replace('.', '/');
        String classJar = Objects.requireNonNull(this.getClass().getResource("/" + className + ".class")).toString();
        return classJar.startsWith("jar:");
    }

    /**
     * Упрощенное копирование фото в хранилище
     */
    private String copyPhotoToStorage(File originalFile, Device device) {
        return copyPhotoToStorageManual(originalFile, device);
    }

    /**
     * Публичный метод для копирования фото в хранилище (используется из контроллеров)
     */
    public String copyPhotoToStorageManual(File originalFile, Device device) {
        try {
            if (basePhotosPath == null) {
                LOGGER.error("❌ basePhotosPath не инициализирован");
                return null;
            }
            if (!originalFile.exists() || !originalFile.canRead()) {
                LOGGER.warn("⚠️ Исходный файл не существует или недоступен: {}", originalFile.getName());
                return null;
            }

            if (device.getLocation() == null || device.getLocation().isEmpty()) {
                LOGGER.error("❌ location не указан для устройства ID={}", device.getId());
                return null;
            }

            String originalName = originalFile.getName();
            int dotIndex = originalName.lastIndexOf('.');

            if (dotIndex <= 0) {
                originalName = originalName + ".jpg";
                dotIndex = originalName.lastIndexOf('.');
            }

            String baseName = originalName.substring(0, dotIndex);
            String extension = originalName.substring(dotIndex);

            baseName = baseName.replaceAll("[\\\\/:*?\"<>|]", "_");


            String timestamp = String.valueOf(System.currentTimeMillis());
            String newFileName = String.format("device_%d_%s_%s%s",
                    device.getId(), baseName, timestamp, extension);

            Path destinationPath = Paths.get(basePhotosPath, device.getLocation(), newFileName);
            Files.createDirectories(destinationPath.getParent());
            Files.copy(originalFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);

            File copiedFile = destinationPath.toFile();
            if (!copiedFile.exists()) {
                LOGGER.error("❌ Скопированный файл не найден: {}", destinationPath);
                return null;
            }

            LOGGER.info("📸 Фото сохранено: {} ({} байт)", newFileName, copiedFile.length());
            return newFileName;

        } catch (Exception e) {
            LOGGER.error("❌ Ошибка копирования фото: {}", e.getMessage(), e);
            return null;
        }
    }


    /**
     * Компактная проверка дубликатов по хэшу MD5
     *
     * @return true если файл уже существует в фото устройства
     */
    private boolean isFileDuplicate(File newFile, Device device) {
        try {
            List<String> existingPhotos = device.getPhotos();
            if (existingPhotos == null || existingPhotos.isEmpty()) {
                return false;
            }

            // Вычисляем хэш нового файла
            byte[] newFileHash = calculateMD5Hash(newFile);

            // Сравниваем с существующими фото
            for (String existingPhoto : existingPhotos) {
                String fullPath = getFullPhotoPath(device, existingPhoto);
                File existingFile = new File(fullPath);

                if (!existingFile.exists()) continue;

                // Быстрая проверка по размеру
                if (existingFile.length() != newFile.length()) continue;

                // Проверка по хэшу
                byte[] existingHash = calculateMD5Hash(existingFile);
                if (Arrays.equals(newFileHash, existingHash)) {
                    LOGGER.info("⚠️ Фото уже существует: {} (дубликат {})",
                            newFile.getName(), existingPhoto);
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            LOGGER.warn("⚠️ Ошибка проверки дубликата, считаем не дубликатом: {}", e.getMessage());
            return false; // При ошибке разрешаем загрузку
        }
    }

    /**
     * Вычисляет MD5 хэш файла (компактная версия)
     */
    private byte[] calculateMD5Hash(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            return md.digest();
        }
    }
}