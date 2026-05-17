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
import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
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
     * При конфликтах автоматически предпочитает локальные данные (не перезаписывает).
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

            // Merge фотографий выполняется внутри performMerge, если передан путь
            Path importedPhotos = tempDir.resolve("device_photos");

            // Выполняем merge без UI
            MergeResult result = performMerge(
                importedDb.toString(),
                Files.exists(importedPhotos) ? importedPhotos : null,
                tempDir
            );

            // Обрабатываем конфликты автоматически: предпочитаем локальные данные,
            // но обязательно обновляем last_synced_at чтобы они не всплыли снова
            List<Device> resolvedDevices = new ArrayList<>(result.changedDevices());
            List<Scheme> resolvedSchemes = new ArrayList<>(result.changedSchemes());
            List<DeviceLocation> resolvedLocations = new ArrayList<>(result.changedLocations());

            if (result.hasConflicts()) {
                LOGGER.info("Авто-разрешение {} конфликтов (предпочтение локальным данным)",
                        result.conflicts().size());
                for (ConflictInfo conflict : result.conflicts()) {
                    LOGGER.info("Конфликт {} '{}' - оставлена локальная версия", conflict.type, conflict.key);
                    switch (conflict.type) {
                        case "device" -> {
                            Device local = (Device) conflict.local;
                            local.setLastSyncedAt(System.currentTimeMillis());
                            deviceDAO.updateDevice(local, false);
                            resolvedDevices.add(local);
                        }
                        case "scheme" -> {
                            Scheme local = (Scheme) conflict.local;
                            local.setLastSyncedAt(System.currentTimeMillis());
                            schemeDAO.updateScheme(local, false);
                            resolvedSchemes.add(local);
                        }
                        case "device_location" -> {
                            DeviceLocation local = (DeviceLocation) conflict.local;
                            local.setLastSyncedAt(System.currentTimeMillis());
                            deviceLocationDAO.addDeviceLocation(local, false);
                            resolvedLocations.add(local);
                        }
                    }
                }
            }

            deleteDirectory(tempDir);
            updateLastSyncedTimestamps(resolvedDevices, resolvedSchemes, resolvedLocations);

            LOGGER.info("✅ Импорт завершён: {}", result);
            return result.stats();
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
    // КОЛЛБЭКИ ДЛЯ АСИНХРОННОГО ИМПОРТА
    // ============================================================

    /**
     * Коллбэк для асинхронного импорта.
     * Вызывается в JavaFX-потоке после завершения merge.
     */
    @FunctionalInterface
    public interface ImportCallback {
        /**
         * @param result результат merge (null если пользователь отменил выбор файла)
         * @param tempDirectory временная директория (нужно удалить после применения решений)
         * @param error ошибка (null если успех)
         */
        void onImportCompleted(MergeResult result, Path tempDirectory, Throwable error);
    }

    /**
     * Коллбэк для применения решений конфликтов.
     * Вызывается в JavaFX-потоке после того как пользователь разрешил конфликты.
     */
    @FunctionalInterface
    public interface ConflictResolutionCallback {
        /**
         * @param success true если решения применены успешно
         * @param stats итоговая статистика [added, updated devices, added, updated schemes]
         */
        void onResolutionsApplied(boolean success, int[] stats);
    }

    // ============================================================
    // ИМПОРТ + MERGE (асинхронный с UI)
    // ============================================================

    /**
     * Импортирует ZIP асинхронно.
     * Merge выполняется в фоновом потоке, диалог конфликтов показывается в UI-потоке.
     *
     * @param ownerWindow окно-владелец
     * @param loadingIndicator индикатор загрузки
     * @param callback коллбэк с результатом (вызывается в JavaFX-потоке)
     */
    public void importFromZipAsync(Window ownerWindow, LoadingIndicator loadingIndicator,
                                   ImportCallback callback) {
        File file = createImportFileChooser().showOpenDialog(ownerWindow);
        if (file == null) {
            callback.onImportCompleted(null, null, null);
            return;
        }

        long fileSize = file.length();
        LOGGER.info("🔄 Начало импорта ZIP: {} ({} MB)", file.getName(), fileSize / 1024 / 1024);

        showLoading(loadingIndicator, "Распаковка архива...");

        // Переменная для tempDir — нужна для cleanup при ошибке
        final Path[] tempDirHolder = new Path[1];

        // Фоновая задача: распаковка и merge
        Task<MergeResult> mergeTask = new Task<>() {
            @Override
            protected MergeResult call() throws Exception {
                updateMessage("Распаковка архива...");
                Path tempDir = Files.createTempDirectory("kipia_import_");
                tempDirHolder[0] = tempDir;
                extractZip(file.getAbsolutePath(), tempDir.toString());

                // Логируем структуру распакованных файлов
                LOGGER.info("Распаковано во временную папку: {}", tempDir);
                logDirectoryStructure(tempDir, "  ");

                Path importedDb = tempDir.resolve(ZIP_DB_ENTRY);
                if (!Files.exists(importedDb)) {
                    throw new RuntimeException("В архиве не найден файл базы данных: " + ZIP_DB_ENTRY);
                }
                LOGGER.info("Найдена БД для импорта: {} (размер: {} байт)", importedDb, Files.size(importedDb));

                updateMessage("Объединение данных...");
                Path importedPhotos = tempDir.resolve("device_photos");
                LOGGER.info("Путь к импортированным фото: {} (exists={})", importedPhotos, Files.exists(importedPhotos));

                return performMerge(
                    importedDb.toString(),
                    Files.exists(importedPhotos) ? importedPhotos : null,
                    tempDir
                );
            }
        };

        mergeTask.setOnSucceeded(_ -> {
            MergeResult result = mergeTask.getValue();
            hideLoading(loadingIndicator);

            if (result.hasConflicts()) {
                LOGGER.info("Обнаружено {} конфликтов, ожидание разрешения пользователя",
                        result.conflicts().size());
                // НЕ обновляем timestamps здесь — сделаем после разрешения конфликтов
            } else {
                // Нет конфликтов — обновляем timestamps ТОЛЬКО для изменённых записей
                updateLastSyncedTimestamps(
                        result.changedDevices(),
                        result.changedSchemes(),
                        result.changedLocations()
                );
                LOGGER.info("✅ Импорт завершён без конфликтов: {}", result);
            }

            callback.onImportCompleted(result, result.tempDirectory(), null);
        });

        mergeTask.setOnFailed(_ -> {
            hideLoading(loadingIndicator);
            Throwable error = mergeTask.getException();
            LOGGER.error("❌ Ошибка импорта: {}", error.getMessage(), error);

            // Очищаем temp директорию при ошибке (включая ошибку валидации БД)
            if (tempDirHolder[0] != null) {
                deleteDirectory(tempDirHolder[0]);
                LOGGER.info("Временная директория удалена после ошибки");
            }

            callback.onImportCompleted(null, null, error);
        });

        new Thread(mergeTask).start();
    }

    /**
     * Применяет решения пользователя по конфликтам.
     * Можно вызывать из JavaFX-потока ПОСЛЕ показа диалога.
     *
     * @param conflicts список конфликтов из MergeResult
     * @param resolutions решения пользователя (того же размера)
     * @param tempDirectory временная директория (будет удалена)
     * @param callback коллбэк с результатом
     */
    public void applyConflictResolutions(List<ConflictInfo> conflicts,
                                          List<ConflictResolutionDialog.ConflictResolution> resolutions,
                                          Path tempDirectory,
                                          ConflictResolutionCallback callback) {
        if (conflicts == null || conflicts.isEmpty()) {
            deleteDirectory(tempDirectory);
            callback.onResolutionsApplied(true, new int[4]);
            return;
        }

        Task<int[]> applyTask = new Task<>() {
            @Override
            protected int[] call() {
                int updatedDevices = 0;
                int updatedSchemes = 0;
                int updatedLocations = 0;

                // СОБИРАЕМ СПИСКИ ИЗМЕНЁННЫХ ЗАПИСЕЙ
                List<Device> changedDevices = new ArrayList<>();
                List<Scheme> changedSchemes = new ArrayList<>();
                List<DeviceLocation> changedLocations = new ArrayList<>();

                for (int i = 0; i < conflicts.size(); i++) {
                    ConflictInfo conflict = conflicts.get(i);
                    ConflictResolutionDialog.ConflictResolution resolution = resolutions.get(i);

                    LOGGER.info("Конфликт: {} '{}' - выбрано решение: {}",
                            conflict.type, conflict.key, resolution);

                    switch (resolution) {
                        case LOCAL:
                            // Оставляем локальную версию - она уже в БД, но её last_synced_at нужно обновить
                            // Так как мы её синхронизировали (выбрали local как правильную)
                            // ВАЖНО: НЕ обновляем updated_at, чтобы запись не считалась изменённой локально!
                            if ("device".equals(conflict.type)) {
                                Device localDevice = (Device) conflict.local;
                                localDevice.setLastSyncedAt(System.currentTimeMillis());
                                deviceDAO.updateDevice(localDevice, false);
                                changedDevices.add(localDevice);
                            } else if ("scheme".equals(conflict.type)) {
                                Scheme localScheme = (Scheme) conflict.local;
                                localScheme.setLastSyncedAt(System.currentTimeMillis());
                                schemeDAO.updateScheme(localScheme, false);
                                changedSchemes.add(localScheme);
                            } else if ("device_location".equals(conflict.type)) {
                                DeviceLocation localLoc = (DeviceLocation) conflict.local;
                                localLoc.setLastSyncedAt(System.currentTimeMillis());
                                deviceLocationDAO.addDeviceLocation(localLoc, false);
                                changedLocations.add(localLoc);
                            }
                            LOGGER.info("Конфликт {} '{}' - оставлена локальная версия", conflict.type, conflict.key);
                            break;

                        case REMOTE:
                            // Применяем remote версию
                            applyConflictResolution(conflict, conflict.remote);
                            if ("device".equals(conflict.type)) {
                                updatedDevices++;
                                Device resolved = (Device) conflict.remote;
                                resolved.setId(((Device) conflict.local).getId());
                                resolved.setLastSyncedAt(System.currentTimeMillis());
                                changedDevices.add(resolved);
                            } else if ("scheme".equals(conflict.type)) {
                                updatedSchemes++;
                                Scheme resolved = (Scheme) conflict.remote;
                                resolved.setId(((Scheme) conflict.local).getId());
                                resolved.setLastSyncedAt(System.currentTimeMillis());
                                changedSchemes.add(resolved);
                            } else if ("device_location".equals(conflict.type)) {
                                updatedLocations++;
                                DeviceLocation resolved = (DeviceLocation) conflict.remote;
                                resolved.setLastSyncedAt(System.currentTimeMillis());
                                changedLocations.add(resolved);
                            }
                            break;

                        case SKIP:
                            LOGGER.info("Конфликт {} '{}' пропущен", conflict.type, conflict.key);
                            break;

                        case UNRESOLVED:
                            LOGGER.warn("Конфликт {} '{}' остался неразрешенным", conflict.type, conflict.key);
                            break;
                    }
                }

                // Обновляем timestamps ТОЛЬКО для изменённых записей
                updateLastSyncedTimestamps(changedDevices, changedSchemes, changedLocations);

                // Удаляем временную директорию
                deleteDirectory(tempDirectory);

                return new int[]{0, updatedDevices, 0, updatedSchemes, updatedLocations};
            }
        };

        applyTask.setOnSucceeded(_ -> {
            int[] stats = applyTask.getValue();
            LOGGER.info("✅ Разрешение конфликтов завершено");
            callback.onResolutionsApplied(true, stats);
        });

        applyTask.setOnFailed(_ -> {
            Throwable error = applyTask.getException();
            LOGGER.error("❌ Ошибка применения решений: {}", error.getMessage(), error);
            deleteDirectory(tempDirectory);
            callback.onResolutionsApplied(false, new int[4]);
        });

        new Thread(applyTask).start();
    }

    /**
     * Импортирует ZIP и выполняет merge с текущей БД по updated_at.
     * Показывает {@code loadingIndicator} на время распаковки и merge.
     *
     * <p><b>Устаревший метод</b> — используйте {@link #importFromZipAsync} для UI
     * или {@link #importFromZipFile(File)} для фонового импорта.
     *
     * @param ownerWindow      окно, вызвавшее импорт
     * @param loadingIndicator индикатор загрузки (может быть {@code null})
     * @return результат: [добавлено устройств, обновлено устройств, добавлено схем, обновлено схем]
     * @throws IllegalStateException если обнаружены конфликты (требуется UI-поток)
     */
    @Deprecated
    public int[] importFromZip(Window ownerWindow, LoadingIndicator loadingIndicator) {
        File file = createImportFileChooser().showOpenDialog(ownerWindow);
        if (file == null) return null;

        long fileSize = file.length();
        LOGGER.info("🔄 Начало импорта ZIP: {} ({} MB)", file.getName(), fileSize / 1024 / 1024);

        showLoading(loadingIndicator, "Распаковка архива...");
        try {
            Path tempDir = Files.createTempDirectory("kipia_import_");
            extractZip(file.getAbsolutePath(), tempDir.toString());

            Path importedDb = tempDir.resolve(ZIP_DB_ENTRY);
            if (!Files.exists(importedDb)) {
                throw new RuntimeException("В архиве не найден файл базы данных");
            }

            // Merge фотографий выполняется внутри performMerge, если передан путь
            Path importedPhotos = tempDir.resolve("device_photos");

            setLoadingMessage(loadingIndicator, "Объединение данных...");

            MergeResult result = performMerge(
                importedDb.toString(),
                Files.exists(importedPhotos) ? importedPhotos : null,
                tempDir
            );

            if (result.hasConflicts()) {
                // Синхронный вызов — только если в UI-потоке
                if (!javafx.application.Platform.isFxApplicationThread()) {
                    throw new IllegalStateException(
                        "Обнаружены конфликты при импорте: " + result.conflicts().size() +
                        ". Требуется ручное разрешение в UI-потоке через importFromZipAsync()");
                }
                resolveConflictsSync(result.conflicts());
            }

            deleteDirectory(tempDir);
            updateLastSyncedTimestamps();

            LOGGER.info("✅ Импорт завершён: {}", result);
            return result.stats();

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
     * НЕ вызывает UI, возвращает MergeResult с конфликтами для последующего разрешения.
     * <p>
     * Безопасно вызывать из фонового потока.
     *
     * @param importedDbPath путь к импортированной БД
     * @param importedPhotosPath путь к импортированным фото (null если нет)
     * @param tempDirectory временная директория (сохраняется в результате для удаления позже)
     * @return MergeResult со статистикой и списком конфликтов
     */
    private MergeResult performMerge(String importedDbPath, Path importedPhotosPath, Path tempDirectory) {
        // Проверка существования и размера БД
        File importedDb = new File(importedDbPath);
        if (!importedDb.exists() || importedDb.length() == 0) {
            throw new RuntimeException("Импортированная БД пуста или не существует");
        }

        // Подключаемся к импортированной БД
        DatabaseService importedService = new DatabaseService(importedDbPath);

        // Проверяем совместимость схемы импортированной БД
        validateImportedSchema(importedService);

        DeviceDAO importedDeviceDAO = new DeviceDAO(importedService);
        SchemeDAO importedSchemeDAO = new SchemeDAO(importedService);
        DeviceLocationDAO importedLocationDAO = new DeviceLocationDAO(importedService);

        int[] result = {0, 0, 0, 0};
        List<ConflictInfo> conflicts = new ArrayList<>();
        int photosCount = 0;
        
        // Списки изменённых записей для точечного обновления timestamps
        List<Device> changedDevices = new ArrayList<>();
        List<Scheme> changedSchemes = new ArrayList<>();
        List<DeviceLocation> changedLocations = new ArrayList<>();

        try {
            // Загружаем данные из импортированной БД для логирования
            List<Device> importedDevices = importedDeviceDAO.getAllDevicesForExport();
            List<Scheme> importedSchemes = importedSchemeDAO.getAllSchemesForExport();
            List<DeviceLocation> importedLocations = importedLocationDAO.getAllLocations();
            LOGGER.info("Из импортированной БД загружено: {} устройств, {} схем, {} локаций",
                    importedDevices.size(), importedSchemes.size(), importedLocations.size());

            // Two-way merge для устройств
            mergeDevices(importedDeviceDAO, conflicts, result, changedDevices);

            // Two-way merge для схем
            mergeSchemes(importedSchemeDAO, conflicts, result, changedSchemes);

            // Two-way merge для локаций
            mergeDeviceLocations(importedLocationDAO, importedDeviceDAO, importedSchemeDAO, conflicts, changedLocations);

            // Merge фотографий — только аддитивно (после успешного merge БД)
            if (importedPhotosPath != null && Files.exists(importedPhotosPath)) {
                // Подсчитываем файлы для логирования
                int filesInImportedPhotos = countFilesInDirectory(importedPhotosPath);
                LOGGER.info("Найдена папка фото: {}, файлов в ней: {}", importedPhotosPath, filesInImportedPhotos);

                photosCount = mergePhotosCount(importedPhotosPath.toString());

                // Обновляем поля photos в устройствах после копирования файлов
                updateDevicePhotosAfterImport(importedPhotosPath, importedDevices);
            } else {
                LOGGER.warn("Папка фото не найдена или путь null: {}", importedPhotosPath);
            }

            // НЕ обрабатываем конфликты здесь — возвращаем их для разрешения в UI-потоке
            // НЕ обновляем timestamps — это делается после разрешения конфликтов

            return new MergeResult(result, photosCount, conflicts, tempDirectory, changedDevices, changedSchemes, changedLocations);
        } finally {
            importedService.closeConnection();
        }
    }

    /**
     * Синхронное разрешение конфликтов (для использования в UI-потоке).
     * Вызывает диалог и применяет решения немедленно.
     */
    private void resolveConflictsSync(List<ConflictInfo> conflicts) {
        LOGGER.warn("Обнаружено {} конфликтов при синхронизации:", conflicts.size());

        if (conflicts.isEmpty()) {
            return;
        }

        // Создаем список для хранения решений пользователя
        List<ConflictResolutionDialog.ConflictResolution> resolutions = new ArrayList<>();

        // Показываем диалог разрешения конфликтов (только в UI-потоке!)
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
                    // Ничего не делаем, оставляем локальную версию
                    break;
                case REMOTE:
                    applyConflictResolution(conflict, conflict.remote);
                    break;
                case SKIP:
                    LOGGER.info("Конфликт {} '{}' пропущен", conflict.type, conflict.key);
                    break;
                case UNRESOLVED:
                    LOGGER.warn("Конфликт {} '{}' остался неразрешенным", conflict.type, conflict.key);
                    break;
            }
        }
    }

    /**
     * Проверяет совместимость схемы импортированной БД.
     * Выбрасывает исключение с понятным сообщением для пользователя при несовместимости.
     */
    private void validateImportedSchema(DatabaseService service) {
        try (java.sql.Connection conn = service.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            // Проверяем существование таблиц
            String[] requiredTables = {"devices", "schemes", "device_locations"};
            for (String table : requiredTables) {
                if (!tableExists(stmt, table)) {
                    throw new RuntimeException(
                        "База данных повреждена или имеет неправильный формат.\n" +
                        "Отсутствует обязательная таблица: " + table + "\n" +
                        "Убедитесь, что вы выбрали корректный файл бэкапа KIPiA Management.");
                }
            }

            // Проверяем наличие колонок для синхронизации
            String[][] requiredColumns = {
                {"devices", "deleted_at"},
                {"devices", "updated_at"},
                {"devices", "last_synced_at"},
                {"schemes", "deleted_at"},
                {"schemes", "updated_at"},
                {"schemes", "last_synced_at"},
                {"device_locations", "deleted_at"},
                {"device_locations", "updated_at"},
                {"device_locations", "last_synced_at"}
            };

            java.util.List<String> missingColumns = new java.util.ArrayList<>();
            for (String[] col : requiredColumns) {
                if (!columnExists(stmt, col[0], col[1])) {
                    missingColumns.add(col[0] + "." + col[1]);
                }
            }

            if (!missingColumns.isEmpty()) {
                throw new RuntimeException(
                    "База данных устарела и не поддерживает синхронизацию.\n" +
                    "Отсутствуют обязательные поля: " + String.join(", ", missingColumns) + "\n" +
                    "Необходимо обновить исходное приложение до актуальной версии " +
                    "и создать новый бэкап.");
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException(
                "Ошибка проверки совместимости базы данных: " + e.getMessage() + "\n" +
                "Возможно, файл не является бэкапом KIPiA Management или повреждён.", e);
        }
    }

    /**
     * Проверяет существование таблицы в БД.
     */
    private boolean tableExists(java.sql.Statement stmt, String tableName) throws java.sql.SQLException {
        try (java.sql.ResultSet rs = stmt.executeQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'")) {
            return rs.next();
        }
    }

    /**
     * Проверяет существование колонки в таблице.
     */
    private boolean columnExists(java.sql.Statement stmt, String tableName, String columnName)
            throws java.sql.SQLException {
        try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equals(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Сравнивает два устройства по бизнес-полям (без id, timestamps, deleted).
     * @return true если все бизнес-поля идентичны
     */
    private boolean devicesEqual(Device a, Device b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getType(), b.getType())
            && Objects.equals(a.getName(), b.getName())
            && Objects.equals(a.getManufacturer(), b.getManufacturer())
            && Objects.equals(a.getInventoryNumber(), b.getInventoryNumber())
            && Objects.equals(a.getYear(), b.getYear())
            && Objects.equals(a.getMeasurementLimit(), b.getMeasurementLimit())
            && Objects.equals(a.getAccuracyClass(), b.getAccuracyClass())
            && Objects.equals(a.getLocation(), b.getLocation())
            && Objects.equals(a.getValveNumber(), b.getValveNumber())
            && Objects.equals(a.getStatus(), b.getStatus())
            && Objects.equals(a.getAdditionalInfo(), b.getAdditionalInfo())
            && Objects.equals(a.getPhotos(), b.getPhotos());
    }

    /**
     * Сравнивает две схемы по бизнес-полям (без id, timestamps, deleted).
     * @return true если все бизнес-поля идентичны
     */
    private boolean schemesEqual(Scheme a, Scheme b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getName(), b.getName())
            && Objects.equals(a.getDescription(), b.getDescription())
            && Objects.equals(a.getData(), b.getData());
    }

    /**
     * Сравнивает две локации по бизнес-полям (без id, timestamps, deleted).
     * @return true если все бизнес-поля идентичны
     */
    private boolean deviceLocationsEqual(DeviceLocation a, DeviceLocation b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Double.compare(a.getX(), b.getX()) == 0
            && Double.compare(a.getY(), b.getY()) == 0
            && Double.compare(a.getRotation(), b.getRotation()) == 0;
    }

    /**
     * Two-way merge для устройств
     */
    private void mergeDevices(DeviceDAO importedDeviceDAO, List<ConflictInfo> conflicts, int[] result, List<Device> changedDevices) {
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
                    imported.setLastSyncedAt(System.currentTimeMillis());
                    // ВАЖНО: НЕ обновляем updated_at при импорте!
                    deviceDAO.addDevice(imported, false);
                    result[0]++;
                    changedDevices.add(imported);
                    LOGGER.debug("Добавлено новое устройство: {}", inv);
                }
            } else {
                // Существующее устройство - проверяем конфликты
                boolean localChanged = current.getUpdatedAt() > current.getLastSyncedAt();
                boolean remoteChanged = imported.getUpdatedAt() > imported.getLastSyncedAt();

                if (localChanged && remoteChanged) {
                    // Проверяем, действительно ли данные отличаются
                    if (devicesEqual(current, imported)) {
                        LOGGER.debug("Устройство {}: идентичные данные, пропускаем", inv);
                        // Данные одинаковые, но обновляем last_synced_at
                        current.setLastSyncedAt(System.currentTimeMillis());
                        // ВАЖНО: НЕ обновляем updated_at!
                        deviceDAO.updateDevice(current, false);
                        changedDevices.add(current);
                        continue;
                    }
                    // КОНФЛИКТ!
                    conflicts.add(new ConflictInfo("device", inv, current, imported, null));
                    result[1]++;
                    LOGGER.debug("Конфликт устройства {}: local='{}' remote='{}'",
                            inv, current.getName(), imported.getName());
                } else if (remoteChanged && !localChanged) {
                    // Только remote изменился
                    syncRemovedPhotos(current, imported);
                    String oldLocation = current.getLocation();
                    imported.setId(current.getId());
                    imported.setLastSyncedAt(System.currentTimeMillis());
                    // ВАЖНО: НЕ обновляем updated_at!
                    if (oldLocation != null && !oldLocation.equals(imported.getLocation())) {
                        PhotoManager.getInstance().migratePhotosToNewLocation(imported, oldLocation);
                    }
                    deviceDAO.updateDevice(imported, false);
                    result[1]++;
                    changedDevices.add(imported);
                    LOGGER.debug("Устройство {} обновлено из remote (updated_at: {} -> {})",
                            inv, current.getUpdatedAt(), imported.getUpdatedAt());
                } else if (!remoteChanged && localChanged) {
                    // Только local изменился - сохраняем local, обновляем last_synced_at
                    current.setLastSyncedAt(System.currentTimeMillis());
                    // ВАЖНО: НЕ обновляем updated_at!
                    deviceDAO.updateDevice(current, false);
                    changedDevices.add(current);
                    LOGGER.debug("Устройство {} оставлено local, обновлён last_synced_at", inv);
                } else {
                    // Ничего не менялось с обеих сторон - просто обновляем last_synced_at
                    current.setLastSyncedAt(System.currentTimeMillis());
                    // ВАЖНО: НЕ обновляем updated_at!
                    deviceDAO.updateDevice(current, false);
                    changedDevices.add(current);
                    LOGGER.debug("Устройство {} не менялось, обновлён last_synced_at", inv);
                }
            }
        }
    }

    /**
     * Two-way merge для схем
     */
    private void mergeSchemes(SchemeDAO importedSchemeDAO, List<ConflictInfo> conflicts, int[] result, List<Scheme> changedSchemes) {
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
                    imported.setLastSyncedAt(System.currentTimeMillis());
                    // ВАЖНО: НЕ обновляем updated_at при импорте!
                    schemeDAO.addScheme(imported, false);
                    result[2]++;
                    changedSchemes.add(imported);
                    LOGGER.debug("Добавлена новая схема: {}", imported.getName());
                }
            } else {
                // Существующая схема - проверяем конфликты
                boolean localChanged = current.getUpdatedAt() > current.getLastSyncedAt();
                boolean remoteChanged = imported.getUpdatedAt() > imported.getLastSyncedAt();

                if (localChanged && remoteChanged) {
                    // Проверяем, действительно ли данные отличаются
                    if (schemesEqual(current, imported)) {
                        LOGGER.debug("Схема {}: идентичные данные, пропускаем", imported.getName());
                        // Данные одинаковые, но обновляем last_synced_at
                        current.setLastSyncedAt(System.currentTimeMillis());
                        // ВАЖНО: НЕ обновляем updated_at!
                        schemeDAO.updateScheme(current, false);
                        changedSchemes.add(current);
                        continue;
                    }
                    // КОНФЛИКТ!
                    conflicts.add(new ConflictInfo("scheme", imported.getName(), current, imported, null));
                    result[3]++;
                } else if (remoteChanged && !localChanged) {
                    // Только remote изменился
                    imported.setId(current.getId());
                    imported.setLastSyncedAt(System.currentTimeMillis());
                    // ВАЖНО: НЕ обновляем updated_at!
                    schemeDAO.updateScheme(imported, false);
                    result[3]++;
                    changedSchemes.add(imported);
                    LOGGER.debug("Схема {} обновлена из remote (updated_at: {} -> {})",
                            imported.getName(), current.getUpdatedAt(), imported.getUpdatedAt());
                } else if (!remoteChanged && localChanged) {
                    // Только local изменился - сохраняем local, но обновляем last_synced_at
                    current.setLastSyncedAt(System.currentTimeMillis());
                    // ВАЖНО: НЕ обновляем updated_at!
                    schemeDAO.updateScheme(current, false);
                    changedSchemes.add(current);
                    LOGGER.debug("Схема {} оставлена local, обновлён last_synced_at", imported.getName());
                } else {
                    // Ничего не менялось с обеих сторон - просто обновляем last_synced_at
                    current.setLastSyncedAt(System.currentTimeMillis());
                    // ВАЖНО: НЕ обновляем updated_at!
                    schemeDAO.updateScheme(current, false);
                    changedSchemes.add(current);
                    LOGGER.debug("Схема {} не менялась, обновлён last_synced_at", imported.getName());
                }
            }
        }
    }

    /**
     * Two-way merge для локаций устройств.
     * Использует stable keys (inventory_number + scheme_name) вместо сырых ID,
     * так как автоинкрементные ID различаются между базами данных.
     */
    private void mergeDeviceLocations(DeviceLocationDAO importedLocationDAO, DeviceDAO importedDeviceDAO, SchemeDAO importedSchemeDAO,
                                      List<ConflictInfo> conflicts, List<DeviceLocation> changedLocations) {
        List<DeviceLocation> importedLocations = importedLocationDAO.getAllLocations();
        List<DeviceLocation> currentLocations = deviceLocationDAO.getAllLocations();
        List<Device> importedDevices = importedDeviceDAO.getAllDevicesForExport();
        List<Scheme> importedSchemes = importedSchemeDAO.getAllSchemesForExport();
        List<Device> localDevices = deviceDAO.getAllDevicesForExport();
        List<Scheme> localSchemes = schemeDAO.getAllSchemesForExport();

        // Maps для импортированной стороны: ID -> stable key
        Map<Integer, String> importedDeviceIdToInv = importedDevices.stream()
            .filter(d -> d.getInventoryNumber() != null)
            .collect(Collectors.toMap(Device::getId, Device::getInventoryNumber));
        Map<Integer, String> importedSchemeIdToName = importedSchemes.stream()
            .filter(s -> s.getName() != null)
            .collect(Collectors.toMap(Scheme::getId, Scheme::getName));

        // Maps для локальной стороны: stable key -> ID
        Map<String, Integer> localInvToDeviceId = localDevices.stream()
            .filter(d -> d.getInventoryNumber() != null)
            .collect(Collectors.toMap(Device::getInventoryNumber, Device::getId));
        Map<String, Integer> localNameToSchemeId = localSchemes.stream()
            .filter(s -> s.getName() != null)
            .collect(Collectors.toMap(Scheme::getName, Scheme::getId));

        // Build local location map by stable key
        Map<String, DeviceLocation> currentLocMap = new HashMap<>();
        for (DeviceLocation loc : currentLocations) {
            String deviceInv = localDevices.stream()
                .filter(d -> d.getId() == loc.getDeviceId())
                .findFirst()
                .map(Device::getInventoryNumber)
                .orElse(null);
            String schemeName = localSchemes.stream()
                .filter(s -> s.getId() == loc.getSchemeId())
                .findFirst()
                .map(Scheme::getName)
                .orElse(null);
            if (deviceInv != null && schemeName != null) {
                String stableKey = deviceInv + "|" + schemeName;
                currentLocMap.put(stableKey, loc);
            }
        }

        for (DeviceLocation imported : importedLocations) {
            String invNum = importedDeviceIdToInv.get(imported.getDeviceId());
            String schemeName = importedSchemeIdToName.get(imported.getSchemeId());
            if (invNum == null || schemeName == null) {
                LOGGER.warn("Импортированная локация ссылается на отсутствующее устройство/схему: deviceId={}, schemeId={}",
                    imported.getDeviceId(), imported.getSchemeId());
                continue;
            }
            String stableKey = invNum + "|" + schemeName;

            DeviceLocation current = currentLocMap.get(stableKey);

            if (current == null) {
                // Новая локация
                if (!imported.isDeleted()) {
                    Integer localDeviceId = localInvToDeviceId.get(invNum);
                    Integer localSchemeId = localNameToSchemeId.get(schemeName);
                    if (localDeviceId == null || localSchemeId == null) {
                        LOGGER.warn("Невозможно добавить локацию: устройство/схема не найдены локально: {}|{}", invNum, schemeName);
                        continue;
                    }
                    imported.setDeviceId(localDeviceId);
                    imported.setSchemeId(localSchemeId);
                    imported.setLastSyncedAt(System.currentTimeMillis());
                    // ВАЖНО: НЕ обновляем updated_at при импорте!
                    deviceLocationDAO.addDeviceLocation(imported, false);
                    changedLocations.add(imported);
                    LOGGER.debug("Добавлена новая локация: {}", stableKey);
                }
            } else {
                // Существующая локация - проверяем конфликты
                boolean localChanged = current.getUpdatedAt() > current.getLastSyncedAt();
                boolean remoteChanged = imported.getUpdatedAt() > imported.getLastSyncedAt();

                if (localChanged && remoteChanged) {
                    // Проверяем, действительно ли данные отличаются
                    if (deviceLocationsEqual(current, imported)) {
                        LOGGER.debug("Локация {}: идентичные данные, пропускаем", stableKey);
                        // Данные одинаковые, но обновляем last_synced_at
                        current.setLastSyncedAt(System.currentTimeMillis());
                        // ВАЖНО: НЕ обновляем updated_at!
                        deviceLocationDAO.addDeviceLocation(current, false);
                        changedLocations.add(current);
                        continue;
                    }
                    // КОНФЛИКТ!
                    conflicts.add(new ConflictInfo("device_location", stableKey, current, imported, null));
                    LOGGER.debug("Конфликт локации {}: local=({},{}) remote=({},{})",
                            stableKey, current.getX(), current.getY(), imported.getX(), imported.getY());
                } else if (remoteChanged && !localChanged) {
                    // Только remote изменился
                    current.setX(imported.getX());
                    current.setY(imported.getY());
                    current.setRotation(imported.getRotation());
                    current.setUpdatedAt(imported.getUpdatedAt());
                    current.setLastSyncedAt(System.currentTimeMillis());
                    // ВАЖНО: НЕ обновляем updated_at!
                    deviceLocationDAO.addDeviceLocation(current, false);
                    changedLocations.add(current);
                    LOGGER.debug("Локация {} обновлена из remote: new pos=({},{})",
                            stableKey, current.getX(), current.getY());
                } else if (!remoteChanged && localChanged) {
                    // Только local изменился - сохраняем local, обновляем last_synced_at
                    current.setLastSyncedAt(System.currentTimeMillis());
                    // ВАЖНО: НЕ обновляем updated_at!
                    deviceLocationDAO.addDeviceLocation(current, false);
                    changedLocations.add(current);
                    LOGGER.debug("Локация {} оставлена local, обновлён last_synced_at", stableKey);
                } else {
                    // Ничего не менялось - просто обновляем last_synced_at
                    current.setLastSyncedAt(System.currentTimeMillis());
                    // ВАЖНО: НЕ обновляем updated_at!
                    deviceLocationDAO.addDeviceLocation(current, false);
                    changedLocations.add(current);
                    LOGGER.debug("Локация {} не менялась, обновлён last_synced_at", stableKey);
                }
            }
        }
    }

    /**
     * Применяет выбранное разрешение конфликта
     */
    private void applyConflictResolution(ConflictInfo conflict, Object resolvedVersion) {
        long now = System.currentTimeMillis();

        switch (conflict.type) {
            case "device" -> {
                Device resolved = (Device) resolvedVersion;
                Device local = (Device) conflict.local;
                // Миграция фото при изменении локации
                String oldLocation = local.getLocation();
                resolved.setId(local.getId());
                if (oldLocation != null && !oldLocation.equals(resolved.getLocation())) {
                    PhotoManager.getInstance().migratePhotosToNewLocation(resolved, oldLocation);
                }
                // Сохраняем оригинальный updated_at из локальной версии
                resolved.setUpdatedAt(local.getUpdatedAt());
                resolved.setLastSyncedAt(now);
                // ВАЖНО: НЕ обновляем updated_at!
                deviceDAO.updateDevice(resolved, false);
            }
            case "scheme" -> {
                Scheme resolved = (Scheme) resolvedVersion;
                Scheme local = (Scheme) conflict.local;
                resolved.setId(local.getId());
                resolved.setUpdatedAt(local.getUpdatedAt());
                resolved.setLastSyncedAt(now);
                // ВАЖНО: НЕ обновляем updated_at!
                schemeDAO.updateScheme(resolved, false);
            }
            case "device_location" -> {
                DeviceLocation resolved = (DeviceLocation) resolvedVersion;
                DeviceLocation local = (DeviceLocation) conflict.local;
                // Используем локальные ID, так как resolvedVersion пришёл из импортированной БД
                resolved.setDeviceId(local.getDeviceId());
                resolved.setSchemeId(local.getSchemeId());
                resolved.setUpdatedAt(local.getUpdatedAt());
                resolved.setLastSyncedAt(now);
                // ВАЖНО: НЕ обновляем updated_at!
                deviceLocationDAO.addDeviceLocation(resolved, false);
            }
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
     * Обновление last_synced_at только для изменённых записей.
     * Используется при импорте чтобы не трогать записи которые не менялись.
     */
    private void updateLastSyncedTimestamps(List<Device> changedDevices, List<Scheme> changedSchemes,
                                            List<DeviceLocation> changedLocations) {
        long now = System.currentTimeMillis();
        int updatedCount = 0;

        // Обновляем только изменённые устройства
        for (Device device : changedDevices) {
            device.setLastSyncedAt(now);
            // ВАЖНО: НЕ обновляем updated_at!
            deviceDAO.updateDevice(device, false);
            updatedCount++;
        }

        // Обновляем только изменённые схемы
        for (Scheme scheme : changedSchemes) {
            scheme.setLastSyncedAt(now);
            // ВАЖНО: НЕ обновляем updated_at!
            schemeDAO.updateScheme(scheme, false);
            updatedCount++;
        }

        // Обновляем только изменённые локации
        for (DeviceLocation location : changedLocations) {
            location.setLastSyncedAt(now);
            // ВАЖНО: НЕ обновляем updated_at!
            deviceLocationDAO.addDeviceLocation(location, false);
            updatedCount++;
        }

        LOGGER.info("Обновлены временные метки синхронизации для {} изменённых записей", updatedCount);
    }
    /**
     * Удаляет физические файлы фото, которые присутствуют у {@code localDevice},
     * но отсутствуют у {@code importedDevice}.
     *
     * <p>Вызывается в {@link #performMerge} перед {@code deviceDAO.updateDevice(imported)},
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

    /**
     * Копирует новые фото из импортированной папки и возвращает количество скопированных файлов.
     *
     * @param importedPhotosPath путь к импортированным фото
     * @return количество скопированных файлов
     */
    private int mergePhotosCount(String importedPhotosPath) {
        final int[] count = {0};
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
                        count[0]++;
                        LOGGER.debug("📷 Скопировано фото: {}", targetFile);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            LOGGER.info("✅ Merge фотографий завершён: скопировано {} файлов", count[0]);
            return count[0];
        } catch (Exception e) {
            LOGGER.error("❌ Критическая ошибка merge фотографий: {}", e.getMessage(), e);
            throw new RuntimeException(
                    "Не удалось объединить фотографии. Проверьте права доступа и место на диске.", e);
        }
    }

    /**
     * Обновляет поля photos в устройствах после импорта фотографий.
     * Использует списки фото из импортированных устройств (каждое устройство имеет свои фото).
     */
    private void updateDevicePhotosAfterImport(Path importedPhotosPath, List<Device> importedDevices) {
        try {
            LOGGER.info("Обновление photos для {} импортированных устройств", importedDevices.size());
            int updatedCount = 0;
            int devicesWithPhotos = 0;

            // Создаем мапу импортированных устройств по inventoryNumber для быстрого поиска
            Map<String, Device> importedByInvNum = importedDevices.stream()
                    .filter(d -> d.getInventoryNumber() != null)
                    .collect(Collectors.toMap(Device::getInventoryNumber, d -> d, (a, _) -> a));

            // Получаем все локальные устройства и обновляем фото
            List<Device> localDevices = deviceDAO.getAllDevices();

            for (Device localDevice : localDevices) {
                String invNum = localDevice.getInventoryNumber();
                if (invNum == null) continue;

                Device importedDevice = importedByInvNum.get(invNum);
                if (importedDevice == null) continue; // Устройство не было импортировано

                List<String> importedPhotos = importedDevice.getPhotos();
                if (importedPhotos == null || importedPhotos.isEmpty()) continue;

                devicesWithPhotos++;

                // Проверяем какие фото реально существуют в локальной папке
                List<String> existingPhotos = importedPhotos.stream()
                        .filter(photoName -> photoExistsInLocalFolder(localDevice.getLocation(), photoName))
                        .collect(Collectors.toList());

                if (existingPhotos.isEmpty()) continue;

                // Обновляем если список изменился
                if (!Objects.equals(localDevice.getPhotos(), existingPhotos)) {
                    localDevice.setPhotos(existingPhotos);
                    // ВАЖНО: НЕ обновляем updated_at — это техническая синхронизация файлов
                    deviceDAO.updateDevice(localDevice, false);
                    updatedCount++;
                    LOGGER.debug("📷 Обновлено поле photos для устройства {}: {} фото",
                            invNum, existingPhotos.size());
                }
            }

            LOGGER.info("Найдены фото для {} устройств, обновлено {} записей в БД",
                    devicesWithPhotos, updatedCount);
            if (updatedCount > 0) {
                LOGGER.info("✅ Поля photos обновлены для {} устройств после импорта фотографий", updatedCount);
            }
        } catch (Exception e) {
            LOGGER.error("❌ Ошибка обновления полей photos: {}", e.getMessage(), e);
            // Не прерываем импорт, просто логируем ошибку
        }
    }

    /**
     * Проверяет существование файла фото в локальной папке устройства.
     */
    private boolean photoExistsInLocalFolder(String location, String photoName) {
        if (location == null || photoName == null) return false;
        Path photoPath = Paths.get(photosBasePath, location, photoName);
        return Files.exists(photoPath);
    }

    /**
     * Сканирует папку устройства по location и возвращает список имён файлов фото.
     * Для импортированных фото из Android используется location как имя папки.
     */
    private List<String> scanDevicePhotosByLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            return new ArrayList<>();
        }

        Path deviceDir = Paths.get(photosBasePath, location);
        if (!Files.exists(deviceDir) || !Files.isDirectory(deviceDir)) {
            return new ArrayList<>();
        }

        try {
            List<String> photoFiles = new ArrayList<>();
            Files.list(deviceDir)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.getFileName().toString().toLowerCase();
                    return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || 
                           fileName.endsWith(".png") || fileName.endsWith(".bmp") ||
                           fileName.endsWith(".gif");
                })
                .sorted()
                .forEach(path -> photoFiles.add(path.getFileName().toString()));
            
            return photoFiles;
        } catch (IOException e) {
            LOGGER.warn("Ошибка сканирования папки фото для устройства {}: {}",  location, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Подсчитывает количество файлов в директории (рекурсивно).
     */
    private int countFilesInDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return 0;
        }
        try {
            int[] count = {0};
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (Files.isRegularFile(file)) {
                        count[0]++;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return count[0];
        } catch (IOException e) {
            LOGGER.warn("Ошибка подсчета файлов в {}: {}", directory, e.getMessage());
            return 0;
        }
    }

    /**
     * Логирует структуру директории для отладки.
     */
    private void logDirectoryStructure(Path directory, String indent) {
        if (directory == null || !Files.exists(directory)) {
            LOGGER.info("{}[не существует]", indent);
            return;
        }
        try {
            Files.list(directory).forEach(path -> {
                if (Files.isDirectory(path)) {
                    LOGGER.info("{}{}/", indent, path.getFileName());
                    logDirectoryStructure(path, indent + "  ");
                } else {
                    try {
                        long size = Files.size(path);
                        LOGGER.info("{}{} ({} байт)", indent, path.getFileName(), size);
                    } catch (IOException e) {
                        LOGGER.info("{}{}", indent, path.getFileName());
                    }
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Ошибка логирования структуры {}: {}", directory, e.getMessage());
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
     * Публичный — используется из контроллеров для очистки при отмене операций.
     */
    public void deleteDirectory(Path dir) {
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