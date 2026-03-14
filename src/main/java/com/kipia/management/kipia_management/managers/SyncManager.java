package com.kipia.management.kipia_management.managers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.models.DeviceLocation;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.services.DatabaseService;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.DeviceLocationDAO;
import com.kipia.management.kipia_management.services.SchemeDAO;
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
 *
 * @author vladimir_shi
 * @since 09.03.2026
 */
public class SyncManager {

    private static final Logger LOGGER = LogManager.getLogger(SyncManager.class);

    // Имена внутри ZIP
    private static final String ZIP_DB_ENTRY    = "kipia_management.db";
    private static final String ZIP_PHOTOS_DIR  = "device_photos/";

    private final DatabaseService databaseService;
    private final DeviceDAO deviceDAO;
    private final SchemeDAO schemeDAO;
    private final DeviceLocationDAO deviceLocationDAO;
    private final String photosBasePath; // путь к папке device_photos

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
    // ЭКСПОРТ
    // ============================================================

    /**
     * Экспортирует БД + фото в ZIP файл, выбранный пользователем.
     * @return путь к созданному ZIP или null при отмене/ошибке
     */
    public String exportToZip(Window ownerWindow) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт базы данных");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("ZIP архив", "*.zip"));

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date());
        chooser.setInitialFileName("kipia_backup_" + timestamp + ".zip");

        File file = chooser.showSaveDialog(ownerWindow);
        if (file == null) return null;

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

    // ============================================================
    // ИМПОРТ + MERGE
    // ============================================================

    /**
     * Импортирует ZIP и выполняет merge с текущей БД по updated_at.
     * @return результат: [добавлено устройств, обновлено устройств, добавлено схем, обновлено схем]
     */
    public int[] importFromZip(Window ownerWindow) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Импорт базы данных");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("ZIP архив", "*.zip"));

        File file = chooser.showOpenDialog(ownerWindow);
        if (file == null) return null;

        try {
            // Распаковываем во временную директорию
            Path tempDir = Files.createTempDirectory("kipia_import_");
            extractZip(file.getAbsolutePath(), tempDir.toString());

            // Путь к импортированной БД
            Path importedDb = tempDir.resolve(ZIP_DB_ENTRY);
            if (!Files.exists(importedDb)) {
                throw new RuntimeException("В архиве не найден файл базы данных");
            }

            // Выполняем merge
            int[] result = mergeDatabase(importedDb.toString());

            // Merge фотографий
            Path importedPhotos = tempDir.resolve("device_photos");
            if (Files.exists(importedPhotos)) {
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
        }
    }

    // ============================================================
    // MERGE ЛОГИКА
    // ============================================================

    /**
     * Merge импортированной БД с текущей по updated_at.
     * Побеждает запись с большим updated_at.
     */
    private int[] mergeDatabase(String importedDbPath) {
        // Подключаемся к импортированной БД
        DatabaseService importedService = new DatabaseService(importedDbPath);
        DeviceDAO importedDeviceDAO             = new DeviceDAO(importedService);
        SchemeDAO importedSchemeDAO             = new SchemeDAO(importedService);
        DeviceLocationDAO importedLocationDAO   = new DeviceLocationDAO(importedService);

        int[] result = {0, 0, 0, 0}; // [добавлено dev, обновлено dev, добавлено sch, обновлено sch]

        // --- Merge устройств ---
        List<Device> importedDevices = importedDeviceDAO.getAllDevicesForExport();
        List<Device> currentDevices  = deviceDAO.getAllDevicesForExport();

        // Строим map текущих по inventoryNumber (уникальный бизнес-ключ)
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
                // Новое устройство — добавляем
                imported.setId(0); // сброс id, БД назначит новый
                deviceDAO.addDevice(imported);
                result[0]++;
            } else if (imported.getUpdatedAt() > current.getUpdatedAt()) {
                // Импортированная запись новее — обновляем
                imported.setId(current.getId()); // сохраняем текущий id
                deviceDAO.updateDevice(imported);
                result[1]++;
            }
            // иначе — текущая запись новее, пропускаем
        }

        // --- Merge схем ---
        List<Scheme> importedSchemes = importedSchemeDAO.getAllSchemesForExport();
        List<Scheme> currentSchemes  = schemeDAO.getAllSchemesForExport();

        Map<String, Scheme> currentSchemeMap = new HashMap<>();
        for (Scheme s : currentSchemes) {
            if (s.getName() != null) {
                currentSchemeMap.put(s.getName(), s);
            }
        }

        // Map: старый scheme_id из импорта -> новый scheme_id в текущей БД
        Map<Integer, Integer> schemeIdMap = new HashMap<>();

        for (Scheme imported : importedSchemes) {
            Scheme current = currentSchemeMap.get(imported.getName());

            if (current == null) {
                int oldImportedId = imported.getId();
                schemeDAO.addScheme(imported); // он сам обновит imported.getId()
                schemeIdMap.put(oldImportedId, imported.getId());
                result[2]++;
            } else {
                schemeIdMap.put(imported.getId(), current.getId());

                boolean existingIsEmpty = current.getData() == null || current.getData().length() <= 2; // "{}" = 2 символа
                boolean importedHasData = imported.getData() != null && imported.getData().length() > 2;

                if (imported.getUpdatedAt() > current.getUpdatedAt()
                        || (importedHasData && existingIsEmpty)) {
                    imported.setId(current.getId());
                    schemeDAO.updateScheme(imported);
                    result[3]++;
                }
            }
        }

        // --- Merge device_locations ---
        // Добавляем только те локации, которых нет в текущей БД
        List<DeviceLocation> importedLocations = importedLocationDAO.getAllLocations();

        for (DeviceLocation loc : importedLocations) {
            // Переводим scheme_id через map
            Integer newSchemeId = schemeIdMap.get(loc.getSchemeId());
            if (newSchemeId == null) continue;

            // Ищем device_id по inventoryNumber через импортированную БД
            Device importedDevice = importedDeviceDAO.getDeviceById(loc.getDeviceId());
            if (importedDevice == null) continue;

            // Находим соответствующий device в текущей БД
            Device currentDevice = deviceDAO.findDeviceByInventoryNumber(
                    importedDevice.getInventoryNumber());
            if (currentDevice == null) continue;

            // Проверяем что такой локации нет
            if (!deviceLocationDAO.locationExists(currentDevice.getId(), newSchemeId)) {
                DeviceLocation newLoc = new DeviceLocation(
                        currentDevice.getId(), newSchemeId,
                        loc.getX(), loc.getY()
                );
                newLoc.setRotation(loc.getRotation());
                deviceLocationDAO.addDeviceLocation(newLoc);
            }
        }

        importedService.closeConnection();
        return result;
    }

    /**
     * Копирует новые фото из импортированной папки (не перезаписывает существующие)
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
            LOGGER.warn("⚠️ Ошибка merge фотографий: {}", e.getMessage());
        }
    }

    // ============================================================
    // ZIP УТИЛИТЫ
    // ============================================================

    /**
     * Создаёт ZIP из файла БД и папки фото
     */
    private void createZip(String zipPath, String dbFilePath, String photosDirPath)
            throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipPath)))) {

            // Добавляем файл БД
            File dbFile = new File(dbFilePath);
            if (dbFile.exists()) {
                addFileToZip(zos, dbFile, ZIP_DB_ENTRY);
                LOGGER.info("📦 БД добавлена в ZIP: {}", dbFilePath);
            } else {
                throw new FileNotFoundException("Файл БД не найден: " + dbFilePath);
            }

            // Добавляем папку фото
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
            while ((len = bis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
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
     * Распаковывает ZIP во временную директорию
     */
    private void extractZip(String zipPath, String destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipPath)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = Paths.get(destDir, entry.getName());

                // Защита от zip slip
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
                        while ((len = zis.read(buffer)) > 0) {
                            bos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================================

    /**
     * Определяет путь к файлу БД — делегирует в DatabaseService,
     * чтобы логика определения режима (dev/prod) была в одном месте.
     */
    private String getDatabaseFilePath() {
        return databaseService.getDatabasePath();
    }

    private void deleteDirectory(Path dir) {
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
        } catch (IOException e) {
            LOGGER.warn("⚠️ Не удалось удалить temp директорию: {}", e.getMessage());
        }
    }
}