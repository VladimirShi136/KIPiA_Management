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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Контроллер экрана настроек
 * 
 * @author vladimir_shi
 * @since 15.01.2025
 */
public class SettingsController {
    private static final Logger LOGGER = LogManager.getLogger(SettingsController.class);

    @FXML
    private StackPane rootPane;  // Корневой контейнер для индикатора загрузки
    @FXML
    private VBox contentBox;  // Контейнер с контентом
    @FXML
    private Button exportDbBtn;
    @FXML
    private Button importDbBtn;
    @FXML
    private Button exportExcelBtn;
    @FXML
    private Button importExcelBtn;

    private SyncManager syncManager;
    private DeviceDAO deviceDAO;
    private Runnable onDataChanged;
    
    // Индикатор загрузки
    private LoadingIndicator loadingIndicator;

    /**
     * Установка SyncManager
     */
    public void setSyncManager(SyncManager syncManager) {
        this.syncManager = syncManager;
        LOGGER.info("✅ SyncManager установлен в SettingsController");
    }

    /**
     * Установка DeviceDAO
     */
    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
        LOGGER.info("✅ DeviceDAO установлен в SettingsController");
    }

    /**
     * Установка callback для обновления данных
     */
    public void setOnDataChanged(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
    }

    /**
     * Инициализация контроллера
     */
    public void init() {
        // Инициализация индикатора загрузки
        loadingIndicator = new LoadingIndicator("Загрузка настроек...");
        if (rootPane != null) {
            rootPane.getChildren().add(loadingIndicator.getOverlay());
        }
        
        // Скрываем контент до загрузки
        hideContentBeforeLoad();
        
        // Запускаем асинхронную загрузку
        loadDataAsync();
    }
    
    /**
     * Скрывает контент до загрузки данных
     */
    private void hideContentBeforeLoad() {
        if (contentBox != null) {
            contentBox.setVisible(false);
            contentBox.setManaged(false);
        }
    }
    
    /**
     * Показывает контент после загрузки данных
     */
    private void showContentAfterLoad() {
        if (contentBox != null) {
            contentBox.setVisible(true);
            contentBox.setManaged(true);
        }
    }
    
    /**
     * Асинхронная загрузка данных с индикатором загрузки
     */
    private void loadDataAsync() {
        Platform.runLater(() -> loadingIndicator.show());
        
        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long startTime = System.currentTimeMillis();
                
                // Настройки загружаются мгновенно, но показываем индикатор для единообразия
                // Здесь можно добавить инициализацию настроек если нужно
                
                // Умная задержка (минимум 300 мс для настроек)
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
     * Экспорт базы данных в ZIP-архив
     */
    @FXML
    private void exportDatabase() {
        if (syncManager == null) {
            CustomAlertDialog.showError("Ошибка", "SyncManager не инициализирован");
            LOGGER.error("SyncManager не установлен");
            return;
        }

        try {
            String path = syncManager.exportToZip(
                    exportDbBtn.getScene().getWindow());
            if (path != null) {
                CustomAlertDialog.showSuccess("Экспорт БД",
                    "База данных успешно экспортирована:\n" + path);
                LOGGER.info("✅ Экспорт БД завершён: {}", path);
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка экспорта БД: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка экспорта", e.getMessage());
        }
    }

    /**
     * Импорт базы данных из ZIP-архива
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

        try {
            int[] result = syncManager.importFromZip(
                    importDbBtn.getScene().getWindow());
            if (result != null) {
                String msg = String.format(
                        "Приборы: добавлено %d, обновлено %d\nСхемы: добавлено %d, обновлено %d",
                        result[0], result[1], result[2], result[3]);
                CustomAlertDialog.showSuccess("Импорт завершён", msg);
                LOGGER.info("✅ Импорт БД завершён: {}", msg);
                
                // Уведомляем об изменении данных
                if (onDataChanged != null) {
                    onDataChanged.run();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка импорта БД: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка импорта", e.getMessage());
        }
    }

    // ---------------------------------------------------------
    //  Импорт/Экспорт Excel
    // ---------------------------------------------------------

    /**
     * Экспорт данных приборов в Excel
     */
    @FXML
    private void exportToExcel() {
        if (deviceDAO == null) {
            CustomAlertDialog.showError("Ошибка", "DeviceDAO не инициализирован");
            LOGGER.error("DeviceDAO не установлен");
            return;
        }

        try {
            List<Device> devices =
                deviceDAO.getAllDevices();
            
            boolean success = ExcelImportExportUtil.exportDevicesToExcel(
                exportExcelBtn.getScene().getWindow(), devices);
            
            if (success) {
                CustomAlertDialog.showInfo("Экспорт", "Экспорт в Excel завершён успешно");
                LOGGER.info("✅ Экспорт в Excel завершён успешно");
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка экспорта в Excel: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка экспорта", "Не удалось экспортировать данные в Excel");
        }
    }

    /**
     * Импорт данных приборов из Excel
     */
    @FXML
    private void importFromExcel() {
        if (deviceDAO == null) {
            CustomAlertDialog.showError("Ошибка", "DeviceDAO не инициализирован");
            LOGGER.error("DeviceDAO не установлен");
            return;
        }

        String result = ExcelImportExportUtil.importDevicesFromExcel(
            importExcelBtn.getScene().getWindow(),
            deviceDAO,
            () -> {
                LOGGER.info("✅ Импорт из Excel завершён успешно");
                // Уведомляем об изменении данных
                if (onDataChanged != null) {
                    onDataChanged.run();
                }
            },
            () -> {
                CustomAlertDialog.showError("Импорт", "Ошибка импорта данных из Excel");
                LOGGER.error("Ошибка импорта из Excel");
            }
        );
        
        if (result != null) {
            CustomAlertDialog.showInfo("Импорт", result);
            LOGGER.info("Импорт завершён: {}", result);
        }
    }
}
