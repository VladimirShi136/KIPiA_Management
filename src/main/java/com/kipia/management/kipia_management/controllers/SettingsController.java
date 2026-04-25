package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.managers.SyncManager;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;
import com.kipia.management.kipia_management.utils.ExcelImportExportUtil;
import com.kipia.management.kipia_management.utils.LoadingIndicator;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Контроллер экрана настроек
 *
 * @author vladimir_shi
 * @since 15.01.2025
 */
public class SettingsController {
    private static final Logger LOGGER = LogManager.getLogger(SettingsController.class);
    private static final String SETTINGS_FILE = "settings.properties";

    @FXML private StackPane rootPane;   // Корневой контейнер для индикатора загрузки
    @FXML private VBox      contentBox; // Контейнер с контентом
    @FXML private Button    exportDbBtn;
    @FXML private Button    importDbBtn;
    @FXML private Button    exportExcelBtn;
    @FXML private Button    importExcelBtn;
    @FXML private Label     lastExportTimeLabel;
    @FXML private Label     lastImportTimeLabel;

    private SyncManager    syncManager;
    private DeviceDAO      deviceDAO;
    private Runnable       onDataChanged;
    private MainController mainController;

    // Индикатор загрузки
    private LoadingIndicator loadingIndicator;
    
    // Флаг активной операции
    private boolean operationInProgress = false;

    // ---------------------------------------------------------
    //  Инициализация
    // ---------------------------------------------------------

    public void setSyncManager(SyncManager syncManager) {
        this.syncManager = syncManager;
        LOGGER.info("✅ SyncManager установлен в SettingsController");
    }

    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
        LOGGER.info("✅ DeviceDAO установлен в SettingsController");
    }

    public void setOnDataChanged(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public boolean isOperationInProgress() {
        return operationInProgress;
    }

    public void init() {
        loadingIndicator = new LoadingIndicator("Загрузка...");
        if (rootPane != null) {
            rootPane.getChildren().add(loadingIndicator.getOverlay());
        }

        hideContentBeforeLoad();
        loadDataAsync();
        loadTimestamps();
    }

    private void hideContentBeforeLoad() {
        if (contentBox != null) {
            contentBox.setVisible(false);
            contentBox.setManaged(false);
        }
    }

    private void showContentAfterLoad() {
        if (contentBox != null) {
            contentBox.setVisible(true);
            contentBox.setManaged(true);
        }
    }

    private void loadDataAsync() {
        Platform.runLater(() -> loadingIndicator.show());

        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long startTime = System.currentTimeMillis();
                long elapsedTime = System.currentTimeMillis() - startTime;
                long minDisplayTime = 300;
                if (elapsedTime < minDisplayTime) {
                    Thread.sleep(minDisplayTime - elapsedTime);
                }
                return null;
            }
        };

        loadTask.setOnSucceeded(_ -> {
            showContentAfterLoad();
            loadingIndicator.hide();
            LOGGER.info("SettingsController инициализирован");
        });

        loadTask.setOnFailed(_ -> {
            LOGGER.error("Ошибка загрузки настроек: {}", loadTask.getException().getMessage());
            CustomAlertDialog.showError("Ошибка", "Не удалось загрузить настройки");
            showContentAfterLoad();
            loadingIndicator.hide();
        });

        new Thread(loadTask).start();
    }

    // ---------------------------------------------------------
    //  Синхронизация БД
    // ---------------------------------------------------------

    /**
     * Экспорт базы данных в ZIP-архив.
     * FileChooser открывается в JavaFX-потоке, создание архива — в фоновом.
     */
    @FXML
    private void exportDatabase() {
        if (syncManager == null) {
            CustomAlertDialog.showError("Ошибка", "SyncManager не инициализирован");
            LOGGER.error("SyncManager не установлен");
            return;
        }

        // FileChooser обязан вызываться в JavaFX-потоке (мы уже в нём — это @FXML handler)
        java.io.File file = syncManager.showExportDialog(exportDbBtn.getScene().getWindow());
        if (file == null) return; // пользователь отменил

        setButtonsDisabled(true);
        if (mainController != null) mainController.setNavigationDisabled(true);
        operationInProgress = true;
        loadingIndicator.setMessage("Создание архива...");
        loadingIndicator.show();

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return syncManager.exportToZipFile(file);
            }
        };

        task.setOnSucceeded(_ -> {
            loadingIndicator.hide();
            setButtonsDisabled(false);
            if (mainController != null) mainController.setNavigationDisabled(false);
            operationInProgress = false;
            String path = task.getValue();
            if (path != null) {
                CustomAlertDialog.showSuccess("Экспорт БД",
                        "База данных успешно экспортирована:\n" + path);
                saveLastExportTime();
                lastExportTimeLabel.setText("Последний экспорт: " +
                        formatTimestamp(System.currentTimeMillis()));
                LOGGER.info("✅ Экспорт БД завершён: {}", path);
            }
        });

        task.setOnFailed(_ -> {
            loadingIndicator.hide();
            setButtonsDisabled(false);
            if (mainController != null) mainController.setNavigationDisabled(false);
            operationInProgress = false;
            Throwable e = task.getException();
            LOGGER.error("Ошибка экспорта БД: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка экспорта", e.getMessage());
        });

        new Thread(task).start();
    }

    /**
     * Импорт базы данных из ZIP-архива.
     * FileChooser открывается в JavaFX-потоке, merge — в фоновом.
     */
    @FXML
    private void importDatabase() {
        if (syncManager == null) {
            CustomAlertDialog.showError("Ошибка", "SyncManager не инициализирован");
            LOGGER.error("SyncManager не установлен");
            return;
        }

        boolean confirm = CustomAlertDialog.showConfirmation("Импорт БД",
                """
                Данные из архива будут объединены с текущей БД.
                Победит запись с более поздней датой обновления.
                Продолжить?""");
        if (!confirm) return;

        // FileChooser обязан вызываться в JavaFX-потоке
        java.io.File file = syncManager.showImportDialog(importDbBtn.getScene().getWindow());
        if (file == null) return; // пользователь отменил

        setButtonsDisabled(true);
        if (mainController != null) mainController.setNavigationDisabled(true);
        operationInProgress = true;
        loadingIndicator.setMessage("Распаковка архива...");
        loadingIndicator.show();

        Task<int[]> task = new Task<>() {
            @Override
            protected int[] call() {
                return syncManager.importFromZipFile(file);
            }
        };

        task.setOnSucceeded(_ -> {
            loadingIndicator.hide();
            setButtonsDisabled(false);
            if (mainController != null) mainController.setNavigationDisabled(false);
            operationInProgress = false;
            int[] result = task.getValue();
            if (result != null) {
                String msg = String.format(
                        "Приборы: добавлено %d, обновлено %d\nСхемы: добавлено %d, обновлено %d",
                        result[0], result[1], result[2], result[3]);
                CustomAlertDialog.showSuccess("Импорт завершён", msg);
                saveLastImportTime();
                lastImportTimeLabel.setText("Последний импорт: " +
                        formatTimestamp(System.currentTimeMillis()));
                LOGGER.info("✅ Импорт БД завершён: {}", msg);
                if (onDataChanged != null) onDataChanged.run();
            }
        });

        task.setOnFailed(_ -> {
            loadingIndicator.hide();
            setButtonsDisabled(false);
            if (mainController != null) mainController.setNavigationDisabled(false);
            operationInProgress = false;
            Throwable e = task.getException();
            LOGGER.error("Ошибка импорта БД: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка импорта", e.getMessage());
        });

        new Thread(task).start();
    }

    // ---------------------------------------------------------
    //  Импорт/Экспорт Excel
    // ---------------------------------------------------------

    /**
     * Экспорт данных приборов в Excel.
     * FileChooser открывается в JavaFX-потоке, запись файла — в фоновом.
     */
    @FXML
    private void exportToExcel() {
        if (deviceDAO == null) {
            CustomAlertDialog.showError("Ошибка", "DeviceDAO не инициализирован");
            LOGGER.error("DeviceDAO не установлен");
            return;
        }

        // FileChooser обязан вызываться в JavaFX-потоке (мы уже в нём — это @FXML handler)
        java.io.File file = ExcelImportExportUtil.showSaveDialogPublic(
                exportExcelBtn.getScene().getWindow());
        if (file == null) return; // пользователь отменил

        setButtonsDisabled(true);
        if (mainController != null) mainController.setNavigationDisabled(true);
        operationInProgress = true;
        loadingIndicator.setMessage("Экспорт в Excel...");
        loadingIndicator.show();

        java.io.File finalFile = file;
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                List<Device> devices = deviceDAO.getAllDevices();
                return ExcelImportExportUtil.exportDevicesToFile(finalFile, devices);
            }
        };

        task.setOnSucceeded(_ -> {
            loadingIndicator.hide();
            setButtonsDisabled(false);
            if (mainController != null) mainController.setNavigationDisabled(false);
            operationInProgress = false;
            if (Boolean.TRUE.equals(task.getValue())) {
                CustomAlertDialog.showInfo("Экспорт", "Экспорт в Excel завершён успешно");
                LOGGER.info("✅ Экспорт в Excel завершён успешно");
            }
        });

        task.setOnFailed(_ -> {
            loadingIndicator.hide();
            setButtonsDisabled(false);
            if (mainController != null) mainController.setNavigationDisabled(false);
            operationInProgress = false;
            Throwable e = task.getException();
            LOGGER.error("Ошибка экспорта в Excel: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка экспорта",
                    "Не удалось экспортировать данные в Excel");
        });

        new Thread(task).start();
    }

    /**
     * Импорт данных приборов из Excel.
     * FileChooser открывается в JavaFX-потоке, чтение и запись в БД — в фоновом.
     */
    @FXML
    private void importFromExcel() {
        if (deviceDAO == null) {
            CustomAlertDialog.showError("Ошибка", "DeviceDAO не инициализирован");
            LOGGER.error("DeviceDAO не установлен");
            return;
        }

        // FileChooser обязан вызываться в JavaFX-потоке
        java.io.File file = ExcelImportExportUtil.showOpenDialogPublic(
                importExcelBtn.getScene().getWindow());
        if (file == null) return; // пользователь отменил

        setButtonsDisabled(true);
        if (mainController != null) mainController.setNavigationDisabled(true);
        operationInProgress = true;
        loadingIndicator.setMessage("Импорт из Excel...");
        loadingIndicator.show();

        java.io.File finalFile = file;
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return ExcelImportExportUtil.importDevicesFromFile(
                        finalFile,
                        deviceDAO,
                        () -> {
                            LOGGER.info("✅ Импорт из Excel завершён успешно");
                            Platform.runLater(() -> {
                                if (onDataChanged != null) onDataChanged.run();
                            });
                        },
                        () -> {
                            Platform.runLater(() -> {
                                loadingIndicator.hide();
                                setButtonsDisabled(false);
                                if (mainController != null) mainController.setNavigationDisabled(false);
                                operationInProgress = false;
                                CustomAlertDialog.showError("Ошибка импорта",
                                        "Проверьте обязательные поля: Тип, Модель, Инв.№, Место установки, Статус. Все они должны быть заполнены.");
                            });
                            LOGGER.error("Ошибка импорта из Excel");
                        }
                );
            }
        };

        task.setOnSucceeded(_ -> {
            loadingIndicator.hide();
            setButtonsDisabled(false);
            if (mainController != null) mainController.setNavigationDisabled(false);
            operationInProgress = false;
            String result = task.getValue();
            if (result != null) {
                CustomAlertDialog.showInfo("Импорт", result);
                LOGGER.info("Импорт завершён: {}", result);
            }
        });

        task.setOnFailed(_ -> {
            loadingIndicator.hide();
            setButtonsDisabled(false);
            if (mainController != null) mainController.setNavigationDisabled(false);
            operationInProgress = false;
            Throwable e = task.getException();
            LOGGER.error("Ошибка импорта из Excel: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка импорта",
                    "Не удалось импортировать данные из Excel");
        });

        new Thread(task).start();
    }

    // ---------------------------------------------------------
    //  Утилиты UI
    // ---------------------------------------------------------

    /**
     * Блокирует / разблокирует все кнопки операций во время выполнения задачи.
     * Предотвращает запуск нескольких операций одновременно.
     */
    private void setButtonsDisabled(boolean disabled) {
        Platform.runLater(() -> {
            exportDbBtn.setDisable(disabled);
            importDbBtn.setDisable(disabled);
            exportExcelBtn.setDisable(disabled);
            importExcelBtn.setDisable(disabled);
        });
    }

    // ---------------------------------------------------------
    //  Время записи БД
    // ---------------------------------------------------------

    private void saveLastExportTime() {
        Properties prop = new Properties();
        // Считываем существующие свойства
        try (InputStream input = new FileInputStream(SETTINGS_FILE)) {
            prop.load(input);
        } catch (IOException e) {
            // Файл может не существовать при первом запуске
        }
        // Обновляем время экспорта
        prop.setProperty("last.export.time", String.valueOf(System.currentTimeMillis()));
        // Сохраняем все свойства
        try (OutputStream output = new FileOutputStream(SETTINGS_FILE)) {
            prop.store(output, "Last export/import times");
        } catch (IOException e) {
            LOGGER.error("Ошибка сохранения времени экспорта: {}", e.getMessage());
        }
    }

    private void saveLastImportTime() {
        Properties prop = new Properties();
        // Считываем существующие свойства
        try (InputStream input = new FileInputStream(SETTINGS_FILE)) {
            prop.load(input);
        } catch (IOException e) {
            // Файл может не существовать при первом запуске
        }
        // Обновляем время импорта
        prop.setProperty("last.import.time", String.valueOf(System.currentTimeMillis()));
        // Сохраняем все свойства
        try (OutputStream output = new FileOutputStream(SETTINGS_FILE)) {
            prop.store(output, "Last export/import times");
        } catch (IOException e) {
            LOGGER.error("Ошибка сохранения времени импорта: {}", e.getMessage());
        }
    }

    private void loadTimestamps() {
        try (InputStream input = new FileInputStream(SETTINGS_FILE)) {
            Properties prop = new Properties();
            prop.load(input);

            String exportTime = prop.getProperty("last.export.time");
            if (exportTime != null) {
                lastExportTimeLabel.setText("Последний экспорт: " +
                        formatTimestamp(Long.parseLong(exportTime)));
            }

            String importTime = prop.getProperty("last.import.time");
            if (importTime != null) {
                lastImportTimeLabel.setText("Последний импорт: " +
                        formatTimestamp(Long.parseLong(importTime)));
            }
        } catch (IOException e) {
            // Файл может не существовать при первом запуске
        }
    }

    private String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp));
    }
}