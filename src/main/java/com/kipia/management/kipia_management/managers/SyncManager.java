package com.kipia.management.kipia_management.managers;

import com.kipia.management.kipia_management.controllers.ConflictResolutionDialog;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.models.DeviceLocation;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.services.DatabaseService;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.DeviceLocationDAO;
import com.kipia.management.kipia_management.services.SchemeDAO;
import com.kipia.management.kipia_management.utils.LoadingIndicator;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/**
 * Менеджер синхронизации БД между устройствами.
 * Упаковывает БД + папку device_photos в ZIP,
 * при импорте выполняет merge с проверкой updated_at.
 * <p>
 * Поддерживает {@link LoadingIndicator} — передайте экземпляр в методы
 * {@link #exportToZip(Window, LoadingIndicator)} и
 * {@link #importFromZip(Window, LoadingIndicator)}, чтобы индикатор
 * отображался во время длительных операций.
 * Используйте перегрузки без индикатора для обратной совместимости.
 *
 * @author vladimir_shi
 * @since 09.03.2026
 */
public class SyncManager {

    private static final Logger LOGGER = LogManager.getLogger(SyncManager.class);

    // Имена внутри ZIP
    private static final String ZIP_DB_ENTRY   = "kipia_management.db";
    private static final String ZIP_PHOTOS_DIR = "device_photos/";

    private final DatabaseService     databaseService;
    private final DeviceDAO           deviceDAO;
    private final SchemeDAO           schemeDAO;
    private final DeviceLocationDAO   deviceLocationDAO;
    private final String              photosBasePath; // путь к папке device_photos

    /**
     * Внутренний класс для хранения информации о конфликтах при трёхстороннем merge
     */
    public static class ConflictInfo {
        public final Object local;
        public final Object remote;
        public final Object base;
        public final String type;
        public final String key;
        
        public ConflictInfo(String type, String key, Object local, Object remote, Object base) {
            this.type = type;
            this.key = key;
            this.local = local;
            this.remote = remote;
            this.base = base;
        }
    }

    public SyncManager(DatabaseService databaseService,
                       DeviceDAO deviceDAO,
                       SchemeDAO schemeDAO,
                       DeviceLocationDAO deviceLocationDAO,
                       String photosBasePath) {
        this.databaseService   = databaseService;
        this.deviceDAO         = deviceDAO;
        this.schemeDAO         = schemeDAO;
        this.deviceLocationDAO = deviceLocationDAO;
        this.photosBasePath    = photosBasePath;
    }

    // ============================================================
    // ПУБЛИЧНЫЕ МЕТОДЫ ДЛЯ КОНТРОЛЛЕРА
    // ============================================================

    /**
     * Создает и настраивает FileChooser для экспорта.
     */
    private FileChooser createExportFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт базы данных");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("ZIP архив", "*.zip"));
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date());
        chooser.setInitialFileName("kipia_backup_" + timestamp + ".zip");
        return chooser;
    }

    /**
     * Создает и настраивает FileChooser для импорта.
     */
    private FileChooser createImportFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Импорт базы данных");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("ZIP архив", "*.zip"));
        return chooser;
    }

    /**
     * Проверяет достаточно ли свободного места для экспорта.
     */
    private void checkFreeSpaceForExport() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        long freeSpace     = tempDir.getFreeSpace();
        long estimatedSize = estimateExportSize();
        if (freeSpace < estimatedSize * 1.5) {
            throw new RuntimeException("Недостаточно места для экспорта. Нужно: " +
                    (estimatedSize / 1024 / 1024) + "MB, доступно: " + (freeSpace / 1024 / 1024) + "MB");
        }
    }

    /**
     * Открывает диалог выбора файла для экспорта.
     * Вызывать только из JavaFX-потока.
     *
     * @return выбранный файл или {@code null} если пользователь отменил
     */
    public File showExportDialog(Window ownerWindow) {
        checkFreeSpaceForExport();
        return createExportFileChooser().showSaveDialog(ownerWindow);
    }

    /**
     * Открывает диалог выбора файла для импорта.
     * Вызывать только из JavaFX-потока.
     *
     * @return выбранный файл или {@code null} если пользователь отменил
     */
    public File showImportDialog(Window ownerWindow) {
        return createImportFileChooser().showOpenDialog(ownerWindow);
    }

    /**
     * Создаёт ZIP из уже выбранного файла. Безопасно вызывать из фонового потока.
     *
     * @param file целевой файл (получен через {@link #showExportDialog})
     * @return абсолютный путь к созданному ZIP или {@code null} при ошибке
     */
    public String exportToZipFile(File file) {
        try {
            String dbPath = getDatabaseFilePath();
            createZip(file.getAbsolutePath(), dbPath, photosBasePath);
            LOGGER.info("✅ Экспорт завершён: {}", file.getAbsolutePath());
            return file.getAbsolutePath();
        } catch (Exception e) {
            LOGGER.error("❌ Ошибка экспорта: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка экспорта: " + e.getMessage(), e);
        }
    }

    /**
     * Выполняет merge из уже выбранного ZIP-файла. Безопасно вызывать из фонового потока.
     *
     * @param file исходный ZIP (получен через {@link #showImportDialog})
     * @return результат: [добавлено устройств, обновлено устройств, добавлено схем, обновлено схем]
     */
    public int[] importFromZipFile(File file) {
        long fileSize = file.length();
        LOGGER.info("🔄 Начало импорта ZIP: {} ({} MB)", file.getName(), fileSize / 1024 / 1024);
        try {
            Path tempDir = Files.createTempDirectory("kipia_import_");
            extractZip(file.getAbsolutePath(), tempDir.toString());

            Path importedDb = tempDir.resolve(ZIP_DB_ENTRY);
            if (!Files.exists(importedDb)) {
                throw new RuntimeException("В архиве не найден файл базы данных");
            }

            int[] result = mergeDatabase(importedDb.toString());

            Path importedPhotos = tempDir.resolve("device_photos");
            if (Files.exists(importedPhotos)) {
                mergePhotos(importedPhotos.toString());
            }

            deleteDirectory(tempDir);

            LOGGER.info("✅ Импорт завершён: +{}dev, ~{}dev, +{}sch, ~{}sch",
                    result[0], result[1], result[2], result[3]);
            return result;
        } catch (Exception e) {
            LOGGER.error("❌ Ошибка импорта: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка импорта: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // ЭКСПОРТ
    // ============================================================

    /**
     * Экспортирует БД + фото в ZIP файл, выбранный пользователем.
     * Показывает {@code loadingIndicator} на время создания архива.
     *
     * @param ownerWindow      окно, вызвавшее экспорт
     * @param loadingIndicator индикатор загрузки (может быть {@code null})
     * @return путь к созданному ZIP или null при отмене/ошибке
     */
    public String exportToZip(Window ownerWindow, LoadingIndicator loadingIndicator) {
        checkFreeSpaceForExport();
        
        File file = createExportFileChooser().showSaveDialog(ownerWindow);
        if (file == null) return null;

        showLoading(loadingIndicator, "Создание архива...");
        try {
            String dbPath = getDatabaseFilePath();
            createZip(file.getAbsolutePath(), dbPath, photosBasePath);
            LOGGER.info("✅ Экспорт завершён: {}", file.getAbsolutePath());
            return file.getAbsolutePath();
        } catch (Exception e) {
            LOGGER.error("❌ Ошибка экспорта: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка экспорта: " + e.getMessage(), e);
        } finally {
            hideLoading(loadingIndicator);
        }
    }

    /**
     * Вспомогательный метод для оценки общего размера экспорта.
     *
     * @return итоговый размер в байтах (БД + фото + 50MB запас)
     */
    private long estimateExportSize() {
        long dbSize     = new File(getDatabaseFilePath()).length();
        long photosSize = estimateDirectorySize(new File(photosBasePath));
        return dbSize + photosSize + 50 * 1024 * 1024; // +50MB запас
    }

    /**
     * Рекурсивно вычисляет размер директории со всеми файлами.
     *
     * @param dir директория для анализа
     * @return общий размер в байтах
     */
    private long estimateDirectorySize(File dir) {
        if (!dir.exists()) return 0;
        long size  = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                size += f.isDirectory() ? estimateDirectorySize(f) : f.length();
            }
        }
        return size;
    }

    // ============================================================
    // ИМПОРТ + MERGE
    // ============================================================

    /**
     * Импортирует ZIP и выполняет merge с текущей БД по updated_at.
     * Показывает {@code loadingIndicator} на время распаковки и merge.
     *
     * @param ownerWindow      окно, вызвавшее импорт
     * @param loadingIndicator индикатор загрузки (может быть {@code null})
     * @return результат: [добавлено устройств, обновлено устройств, добавлено схем, обновлено схем]
     */
    public int[] importFromZip(Window ownerWindow, LoadingIndicator loadingIndicator) {
        File file = createImportFileChooser().showOpenDialog(ownerWindow);
        if (file == null) return null;

        long fileSize = file.length();
        LOGGER.info("🔄 Начало импорта ZIP: {} ({} MB)", file.getName(), fileSize / 1024 / 1024);

        showLoading(loadingIndicator, "Распаковка архива...");
        try {
            // Распаковываем во временную директорию
            Path tempDir = Files.createTempDirectory("kipia_import_");
            extractZip(file.getAbsolutePath(), tempDir.toString());

            // Путь к импортированной БД
            Path importedDb = tempDir.resolve(ZIP_DB_ENTRY);
            if (!Files.exists(importedDb)) {
                throw new RuntimeException("В архиве не найден файл базы данных");
            }

            setLoadingMessage(loadingIndicator, "Объединение данных...");

            // Выполняем merge БД
            int[] result = mergeDatabase(importedDb.toString());

            // Merge фотографий — только аддитивно
            Path importedPhotos = tempDir.resolve("device_photos");
            if (Files.exists(importedPhotos)) {
                setLoadingMessage(loadingIndicator, "Синхронизация фотографий...");
                mergePhotos(importedPhotos.toString());
            }

            // Удаляем temp
            deleteDirectory(tempDir);

            LOGGER.info("✅ Импорт завершён: +{}dev, ~{}dev, +{}sch, ~{}sch",
                    result[0], result[1], result[2], result[3]);
            return result;

        } catch (Exception e) {
            LOGGER.error("❌ Ошибка импорта: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка импорта: " + e.getMessage(), e);
        } finally {
            hideLoading(loadingIndicator);
        }
    }


    // ============================================================
    // MERGE ЛОГИКА
    // ============================================================

    /**
     * Трёхсторонний merge импортированной БД с текущей.
     * Использует last_synced_at для обнаружения конфликтов.
     */
    private int[] mergeDatabase(String importedDbPath) {
        // Проверка существования и размера БД
        File importedDb = new File(importedDbPath);
        if (!importedDb.exists() || importedDb.length() == 0) {
            throw new RuntimeException("Импортированная БД пуста или не существует");
        }

        // Подключаемся к импортированной БД
        DatabaseService importedService = new DatabaseService(importedDbPath);
        DeviceDAO importedDeviceDAO = new DeviceDAO(importedService);
        SchemeDAO importedSchemeDAO = new SchemeDAO(importedService);
        DeviceLocationDAO importedLocationDAO = new DeviceLocationDAO(importedService);

        int[] result = {0, 0, 0, 0};
        List<ConflictInfo> conflicts = new ArrayList<>();

        // Three-way merge для устройств
        mergeDevices(importedDeviceDAO, conflicts, result);

        // Three-way merge для схем
        mergeSchemes(importedSchemeDAO, conflicts, result);

        // Three-way merge для локаций
        mergeDeviceLocations(importedLocationDAO, conflicts, result);

        // Обработка конфликтов
        if (!conflicts.isEmpty()) {
            resolveConflicts(conflicts);
        }

        // Обновление last_synced_at после успешного merge
        updateLastSyncedTimestamps();

        importedService.closeConnection();
        return result;
    }

    /**
     * Three-way merge для устройств
     */
    private void mergeDevices(DeviceDAO importedDeviceDAO, List<ConflictInfo> conflicts, int[] result) {
        List<Device> importedDevices = importedDeviceDAO.getAllDevicesForExport();
        List<Device> currentDevices = deviceDAO.getAllDevicesForExport();

        Map<String, Device> currentMap = new HashMap<>();
        for (Device d : currentDevices) {
            if (d.getInventoryNumber() != null) {
                currentMap.put(d.getInventoryNumber(), d);
            }
        }

        for (Device imported : importedDevices) {
            String inv = imported.getInventoryNumber();
            Device current = currentMap.get(inv);

            if (current == null) {
                // Новое устройство
                if (!imported.isDeleted()) {
                    imported.setId(0);
                    deviceDAO.addDevice(imported);
                    result[0]++;
                }
            } else {
                // Существующее устройство - проверяем конфликты
                boolean localChanged = current.getUpdatedAt() > current.getLastSyncedAt();
                boolean remoteChanged = imported.getUpdatedAt() > imported.getLastSyncedAt();

                if (localChanged && remoteChanged) {
                    // КОНФЛИКТ!
                    conflicts.add(new ConflictInfo("device", inv, current, imported, null));
                    result[1]++;
                } else if (remoteChanged && !localChanged) {
                    // Только remote изменился
                    syncRemovedPhotos(current, imported);
                    imported.setId(current.getId());
                    deviceDAO.updateDevice(imported);
                    result[1]++;
                }
            }
        }
    }

    /**
     * Three-way merge для схем
     */
    private void mergeSchemes(SchemeDAO importedSchemeDAO, List<ConflictInfo> conflicts, int[] result) {
        List<Scheme> importedSchemes = importedSchemeDAO.getAllSchemesForExport();
        List<Scheme> currentSchemes = schemeDAO.getAllSchemesForExport();

        Map<String, Scheme> currentSchemeMap = new HashMap<>();
        for (Scheme s : currentSchemes) {
            if (s.getName() != null) {
                currentSchemeMap.put(s.getName(), s);
            }
        }

        for (Scheme imported : importedSchemes) {
            Scheme current = currentSchemeMap.get(imported.getName());

            if (current == null) {
                // Новая схема
                if (!imported.isDeleted()) {
                    schemeDAO.addScheme(imported);
                    result[2]++;
                }
            } else {
                // Существующая схема - проверяем конфликты
                boolean localChanged = current.getUpdatedAt() > current.getLastSyncedAt();
                boolean remoteChanged = imported.getUpdatedAt() > imported.getLastSyncedAt();

                if (localChanged && remoteChanged) {
                    // КОНФЛИКТ!
                    conflicts.add(new ConflictInfo("scheme", imported.getName(), current, imported, null));
                    result[3]++;
                } else if (remoteChanged && !localChanged) {
                    // Только remote изменился
                    imported.setId(current.getId());
                    schemeDAO.updateScheme(imported);
                    result[3]++;
                }
            }
        }
    }

    /**
     * Three-way merge для локаций устройств
     */
    private void mergeDeviceLocations(DeviceLocationDAO importedLocationDAO, List<ConflictInfo> conflicts, int[] result) {
        List<DeviceLocation> importedLocations = importedLocationDAO.getAllLocations();
        List<DeviceLocation> currentLocations = deviceLocationDAO.getAllLocations();

        Map<String, DeviceLocation> currentLocMap = new HashMap<>();
        for (DeviceLocation loc : currentLocations) {
            String key = loc.getDeviceId() + "_" + loc.getSchemeId();
            currentLocMap.put(key, loc);
        }

        for (DeviceLocation imported : importedLocations) {
            String key = imported.getDeviceId() + "_" + imported.getSchemeId();
            DeviceLocation current = currentLocMap.get(key);

            if (current == null) {
                // Новая локация
                if (!imported.isDeleted()) {
                    deviceLocationDAO.addDeviceLocation(imported);
                }
            } else {
                // Существующая локация - проверяем конфликты
                boolean localChanged = current.getUpdatedAt() > current.getLastSyncedAt();
                boolean remoteChanged = imported.getUpdatedAt() > imported.getLastSyncedAt();

                if (localChanged && remoteChanged) {
                    // КОНФЛИКТ!
                    conflicts.add(new ConflictInfo("device_location", key, current, imported, null));
                } else if (remoteChanged && !localChanged) {
                    // Только remote изменился
                    current.setX(imported.getX());
                    current.setY(imported.getY());
                    current.setRotation(imported.getRotation());
                    current.setUpdatedAt(imported.getUpdatedAt());
                    deviceLocationDAO.addDeviceLocation(current);
                }
            }
        }
    }

    /**
     * Обработка конфликтов с использованием UI
     */
    private void resolveConflicts(List<ConflictInfo> conflicts) {
        LOGGER.warn("Обнаружено {} конфликтов при синхронизации:", conflicts.size());

        if (conflicts.isEmpty()) {
            return;
        }

        // Создаем список для хранения решений пользователя
        List<ConflictResolutionDialog.ConflictResolution> resolutions = new ArrayList<>();

        // Показываем диалог разрешения конфликтов
        boolean applied = ConflictResolutionDialog.showConflictResolutionDialog(conflicts, resolutions);

        if (!applied) {
            LOGGER.warn("Пользователь отменил разрешение конфликтов");
            return;
        }

        // Применяем выбранные решения
        for (int i = 0; i < conflicts.size(); i++) {
            ConflictInfo conflict = conflicts.get(i);
            ConflictResolutionDialog.ConflictResolution resolution = resolutions.get(i);

            LOGGER.info("Конфликт: {} '{}' - выбрано решение: {}",
                    conflict.type, conflict.key, resolution);

            switch (resolution) {
                case LOCAL:
                    applyConflictResolution(conflict, conflict.local);
                    break;
                case REMOTE:
                    applyConflictResolution(conflict, conflict.remote);
                    break;
                case SKIP:
                    // Пропускаем, ничего не делаем
                    LOGGER.info("Конфликт {} '{}' пропущен", conflict.type, conflict.key);
                    break;
                case UNRESOLVED:
                    // Не должно произойти, но на всякий случай
                    LOGGER.warn("Конфликт {} '{}' остался неразрешенным", conflict.type, conflict.key);
                    break;
            }
        }
    }

    /**
     * Применяет выбранное разрешение конфликта
     */
    private void applyConflictResolution(ConflictInfo conflict, Object resolvedVersion) {
        if (conflict.type.equals("device")) {
            Device resolved = (Device) resolvedVersion;
            resolved.setId(((Device) conflict.local).getId());
            deviceDAO.updateDevice(resolved);
        } else if (conflict.type.equals("scheme")) {
            Scheme resolved = (Scheme) resolvedVersion;
            resolved.setId(((Scheme) conflict.local).getId());
            schemeDAO.updateScheme(resolved);
        } else if (conflict.type.equals("device_location")) {
            DeviceLocation resolved = (DeviceLocation) resolvedVersion;
            // Для локаций ID не меняем, только данные
            resolved.setUpdatedAt(System.currentTimeMillis());
            deviceLocationDAO.addDeviceLocation(resolved);
        }
    }


    /**
     * Обновление last_synced_at после успешного merge
     */
    private void updateLastSyncedTimestamps() {
        long now = System.currentTimeMillis();

        // Обновляем все устройства
        List<Device> devices = deviceDAO.getAllDevicesForExport();
        for (Device device : devices) {
            device.setLastSyncedAt(now);
            deviceDAO.updateDevice(device);
        }

        // Обновляем все схемы
        List<Scheme> schemes = schemeDAO.getAllSchemesForExport();
        for (Scheme scheme : schemes) {
            scheme.setLastSyncedAt(now);
            schemeDAO.updateScheme(scheme);
        }

        // Обновляем все локации
        List<DeviceLocation> locations = deviceLocationDAO.getAllLocations();
        for (DeviceLocation location : locations) {
            location.setLastSyncedAt(now);
            deviceLocationDAO.addDeviceLocation(location);
        }

        LOGGER.info("Обновлены временные метки синхронизации для {} устройств, {} схем, {} локаций",
                devices.size(), schemes.size(), locations.size());
    }

    /**
     * Удаляет физические файлы фото, которые присутствуют у {@code localDevice},
     * но отсутствуют у {@code importedDevice}.
     *
     * <p>Вызывается в {@link #mergeDatabase} перед {@code deviceDAO.updateDevice(imported)},
     * только когда импортируемая версия новее. Это единственное место, где файлы фото
     * могут быть удалены при импорте — все остальные операции только добавляют файлы.</p>
     *
     * <p>Пример: на Android удалили photo_2.jpg → в импортируемой БД его нет →
     * здесь удаляем физический файл на JavaFX, иначе он остался бы мусором на диске.</p>
     *
     * @param localDevice    текущая запись устройства в локальной БД
     * @param importedDevice запись устройства из импортируемой БД (более новая)
     */
    private void syncRemovedPhotos(Device localDevice, Device importedDevice) {
        List<String> localPhotos    = localDevice.getPhotos();
        List<String> importedPhotos = importedDevice.getPhotos();

        if (localPhotos == null || localPhotos.isEmpty()) return;

        Set<String> importedSet = importedPhotos != null
                ? new HashSet<>(importedPhotos)
                : Collections.emptySet();

        // Файлы, которые были в локальной записи, но пропали в импортируемой
        for (String fileName : localPhotos) {
            if (importedSet.contains(fileName)) continue;

            // Используем location локального устройства: файлы лежат именно там.
            // Если location тоже изменился — старые файлы всё равно удалятся корректно.
            String location = localDevice.getLocation();
            if (location == null || location.isEmpty()) continue;

            File file = new File(photosBasePath, location + File.separator + fileName);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    LOGGER.info("🗑️ Удалён устаревший файл фото: {}", fileName);
                } else {
                    LOGGER.warn("⚠️ Не удалось удалить устаревший файл фото: {}", fileName);
                }
            }
        }

        // Если папка локации опустела после удаления — убираем и её
        if (localDevice.getLocation() != null && !localDevice.getLocation().isEmpty()) {
            File locationDir = new File(photosBasePath, localDevice.getLocation());
            if (locationDir.exists()) {
                String[] remaining = locationDir.list();
                if (remaining != null && remaining.length == 0) {
                    locationDir.delete();
                    LOGGER.debug("🗑️ Удалена пустая папка локации: {}", localDevice.getLocation());
                }
            }
        }
    }

    /**
     * Копирует новые фото из импортированной папки (не перезаписывает существующие).
     *
     * <p>Намеренно только аддитивна: удаление устаревших файлов выполняется
     * в {@link #syncRemovedPhotos} до вызова этого метода.</p>
     */
    private void mergePhotos(String importedPhotosPath) {
        try {
            Path source = Paths.get(importedPhotosPath);
            Path target = Paths.get(photosBasePath);

            if (!Files.exists(target)) {
                Files.createDirectories(target);
            }

            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    Path targetDir = target.resolve(source.relativize(dir));
                    if (!Files.exists(targetDir)) {
                        Files.createDirectories(targetDir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Path targetFile = target.resolve(source.relativize(file));
                    // Копируем только если файла нет — не перезаписываем
                    if (!Files.exists(targetFile)) {
                        Files.copy(file, targetFile);
                        LOGGER.debug("📷 Скопировано фото: {}", targetFile);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            LOGGER.info("✅ Merge фотографий завершён");
        } catch (Exception e) {
            LOGGER.error("❌ Критическая ошибка merge фотографий: {}", e.getMessage(), e);
            throw new RuntimeException(
                    "Не удалось объединить фотографии. Проверьте права доступа и место на диске.", e);
        }
    }

    // ============================================================
    // ZIP УТИЛИТЫ
    // ============================================================

    /**
     * Создаёт ZIP из файла БД и папки фото.
     */
    private void createZip(String zipPath, String dbFilePath, String photosDirPath)
            throws IOException {
        File targetFile = new File(zipPath);
        File parentDir  = targetFile.getParentFile();
        if (parentDir != null && parentDir.getFreeSpace() < estimateExportSize()) {
            throw new IOException("Недостаточно места для создания ZIP файла. Нужно: " +
                    (estimateExportSize() / 1024 / 1024) + "MB");
        }

        LOGGER.info("🔄 Начало создания ZIP архива...");

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipPath)))) {

            File dbFile = new File(dbFilePath);
            if (dbFile.exists()) {
                addFileToZip(zos, dbFile, ZIP_DB_ENTRY);
                LOGGER.info("📦 БД добавлена в ZIP: {}", dbFilePath);
            } else {
                throw new FileNotFoundException("Файл БД не найден: " + dbFilePath);
            }

            File photosDir = new File(photosDirPath);
            if (photosDir.exists() && photosDir.isDirectory()) {
                addDirectoryToZip(zos, photosDir, ZIP_PHOTOS_DIR);
                LOGGER.info("📦 Фото добавлены в ZIP: {}", photosDirPath);
            } else {
                LOGGER.warn("⚠️ Папка фото не найдена, экспортируем только БД");
            }
        }
    }

    private void addFileToZip(ZipOutputStream zos, File file, String entryName)
            throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = bis.read(buffer)) > 0) zos.write(buffer, 0, len);
        }
        zos.closeEntry();
    }

    private void addDirectoryToZip(ZipOutputStream zos, File dir, String zipPrefix)
            throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String entryName = zipPrefix + file.getName();
            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, entryName + "/");
            } else {
                addFileToZip(zos, file, entryName);
            }
        }
    }

    /**
     * Распаковывает ZIP во временную директорию.
     */
    private void extractZip(String zipPath, String destDir) throws IOException {
        File zipFile = getZipFile(zipPath);
        if (!zipFile.canRead()) {
            throw new IOException("Нет прав на чтение ZIP файла: " + zipPath);
        }

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipPath)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = Paths.get(destDir, entry.getName());
                if (!outPath.normalize().startsWith(Paths.get(destDir).normalize())) {
                    throw new IOException("Небезопасный ZIP entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    try (BufferedOutputStream bos = new BufferedOutputStream(
                            new FileOutputStream(outPath.toFile()))) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) bos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static File getZipFile(String zipPath) throws IOException {
        File zipFile = new File(zipPath);
        if (!zipFile.exists())
            throw new IOException("ZIP файл не существует: " + zipPath);
        if (zipFile.length() == 0)
            throw new IOException("ZIP файл пуст: " + zipPath);
        long maxSize = 10L * 1024L * 1024L * 1024L; // 10GB
        if (zipFile.length() > maxSize)
            throw new IOException("ZIP файл слишком большой: " +
                    (zipFile.length() / 1024 / 1024) + "MB (максимум: 10GB)");
        return zipFile;
    }

    // ============================================================
    // МЕТОДЫ РАБОТЫ С LoadingIndicator
    // ============================================================

    private static void showLoading(LoadingIndicator indicator, String message) {
        if (indicator != null) {
            indicator.setMessage(message);
            indicator.show();
        }
    }

    private static void hideLoading(LoadingIndicator indicator) {
        if (indicator != null) {
            indicator.hide();
        }
    }

    private static void setLoadingMessage(LoadingIndicator indicator, String message) {
        if (indicator != null) {
            indicator.setMessage(message);
        }
    }

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================================

    /**
     * Определяет путь к файлу БД — делегирует в DatabaseService.
     */
    private String getDatabaseFilePath() {
        return databaseService.getDatabasePath();
    }

    /**
     * Удаляет temp директорию.
     */
    private void deleteDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc)
                        throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });

            for (int i = 0; i < 3 && Files.exists(dir); i++) {
                Thread.sleep(100);
                try { Files.deleteIfExists(dir); }
                catch (IOException e) {
                    LOGGER.debug("Попытка {} удаления папки не удалась: {}", i + 1, e.getMessage());
                }
            }

            if (Files.exists(dir)) {
                dir.toFile().deleteOnExit();
                LOGGER.warn("⚠️ Папка не удалена, поставлена в очередь на удаление при выходе: {}", dir);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("❌ Не удалось удалить temp директорию: {}", e.getMessage(), e);
            dir.toFile().deleteOnExit();
        }
    }
}