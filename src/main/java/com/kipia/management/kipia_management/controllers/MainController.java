package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.services.*;
import com.kipia.management.kipia_management.utils.CustomAlert;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Главный контроллер – отвечает за навигацию
 */
public class MainController {
    private static final Logger LOGGER = LogManager.getLogger(MainController.class);

    // Кнопки меню
    public Button devicesBtn;
    public Button addDeviceBtn;
    public Button groupedDevicesBtn;
    public Button photoGalleryBtn;
    public Button schemesBtn;
    public Button reportsBtn;
    public Button exitBtn;

    @FXML
    private Label statusLabel;
    @FXML
    private VBox contentArea;
    @FXML
    private Button themeToggleBtn;

    // Сервисы доступа к БД (УЖЕ ИНИЦИАЛИЗИРОВАНЫ В Main)
    private DeviceDAO deviceDAO;
    private SchemeDAO schemeDAO;
    private DeviceLocationDAO deviceLocationDAO;
    private DatabaseService databaseService;

    private SchemeEditorController schemeEditorController;
    private Parent schemeEditorView;
    private Scene scene;
    private boolean isDarkTheme = false;
    private ReportsController reportsController;

    // ---------------------------------------------------------
    //  Простые сеттеры (всё уже создано в Main)
    // ---------------------------------------------------------

    /**
     * Просто сохраняем DatabaseService (он уже создан в Main)
     */
    public void setDatabaseService(DatabaseService databaseService) {
        this.databaseService = databaseService;
        LOGGER.info("✅ DatabaseService сохранен в MainController");
    }

    /**
     * Просто сохраняем DeviceDAO (он уже создан в Main)
     */
    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
        LOGGER.info("✅ DeviceDAO сохранен в MainController");
    }

    public void setSchemeDAO(SchemeDAO schemeDAO) {
        this.schemeDAO = schemeDAO;
        LOGGER.info("✅ SchemeDAO сохранен в MainController");
    }

    public void setDeviceLocationDAO(DeviceLocationDAO deviceLocationDAO) {
        this.deviceLocationDAO = deviceLocationDAO;
        LOGGER.info("✅ DeviceLocationDAO сохранен в MainController");
    }

    public Scene getScene() {
        return scene;
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }

    /**
     * Сохранение схемы при переходе из редактора схем в другой контроллер.
     */
    public void saveSchemeBeforeNavigation() {
        if (schemeEditorController == null) return;

        try {
            schemeEditorController.getSchemeSaver().saveBeforeNavigation(schemeEditorController.getCurrentScheme());
            CustomAlert.showAutoSaveNotification("Автосохранение", 1.3);
        } catch (Exception e) {
            LOGGER.error("Ошибка при сохранении схемы: {}", e.getMessage());
            CustomAlert.showError("Ошибка сохранения", "Не удалось сохранить схему: " + e.getMessage());
        }
    }

    /**
     * Сохранение схемы при выходе из приложения.
     */
    private void saveSchemeOnExit() {
        if (schemeEditorController != null) {
            try {
                SchemeSaver saver = schemeEditorController.getSchemeSaver();
                Scheme currentScheme = schemeEditorController.getCurrentScheme();
                if (saver != null && currentScheme != null) {
                    saver.saveOnExit(currentScheme);
                    CustomAlert.showAutoSaveNotification("Сохранение при выходе", 0.5);
                }
            } catch (Exception e) {
                LOGGER.error("Ошибка при автосохранении схемы при выходе: {}", e.getMessage());
                CustomAlert.showWarning("Предупреждение",
                        "Не удалось сохранить схему при выходе. Последние изменения могут быть потеряны.");
            }
        }
    }

    /**
     * Инициализация UI‑элементов (ТОЛЬКО UI, без бизнес-логики!)
     */
    @FXML
    private void initialize() {
        statusLabel.setText("Готов к работе");

        // hover‑анимация для всех кнопок меню
        StyleUtils.applyHoverAndAnimation(devicesBtn, "button-devices", "button-devices-hover");
        StyleUtils.applyHoverAndAnimation(addDeviceBtn, "button-add-device", "button-add-device-hover");
        StyleUtils.applyHoverAndAnimation(reportsBtn, "button-reports", "button-reports-hover");
        StyleUtils.applyHoverAndAnimation(themeToggleBtn, "button-theme-toggle", "button-theme-toggle-hover");
        StyleUtils.applyHoverAndAnimation(exitBtn, "button-exit", "button-exit-hover");
        StyleUtils.applyHoverAndAnimation(groupedDevicesBtn, "button-grouped", "button-grouped-hover");
        StyleUtils.applyHoverAndAnimation(schemesBtn, "button-schemes", "button-schemes-hover");
        StyleUtils.applyHoverAndAnimation(photoGalleryBtn, "button-photo-gallery", "button-photo-gallery-hover");

        // Подключаем CSS Alerts к сцене
        if (scene != null) {
            try {
                scene.getStylesheets().add(
                        Objects.requireNonNull(getClass().getResource("/styles/light-theme.css")).toExternalForm()
                );
            } catch (Exception e) {
                LOGGER.warn("Не удалось загрузить CSS Alerts: {}", e.getMessage());
            }
        }
        LOGGER.info("✅ UI инициализирован");
    }

    /**
     * Переключение светлой/тёмной темы.
     */
    @FXML
    private void toggleTheme() {
        if (scene == null) {
            CustomAlert.showError("Ошибка", "Scene не передана");
            return;
        }

        if (isDarkTheme) {
            scene.getStylesheets().clear();
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/light-theme.css"))
                    .toExternalForm());
            themeToggleBtn.setText("Тёмная тема");
            isDarkTheme = false;
        } else {
            scene.getStylesheets().clear();
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/dark-theme.css"))
                    .toExternalForm());
            themeToggleBtn.setText("Светлая тема");
            isDarkTheme = true;
        }

        // Обновляем тему отчёта, если он загружен
        if (reportsController != null) {
            reportsController.refreshTheme();
        }
    }

    /**
     * Выход из приложения.
     */
    @FXML
    private void exitApp() {
        String message = schemeEditorController == null
                ? "Вы уверены, что хотите выйти?"
                : "Вы уверены, что хотите выйти? Текущая схема будет автоматически сохранена.";

        boolean confirmExit = CustomAlert.showConfirmation("Подтверждение выхода", message);

        if (confirmExit) {
            if (schemeEditorController != null) {
                saveSchemeOnExit();
            }
            Timeline timeline = new Timeline(new KeyFrame(
                    Duration.millis(1500),
                    _ -> Platform.exit()
            ));
            timeline.play();
        }
    }

    // ---------------------------------------------------------
    //  Переходы к разным представлениям
    // ---------------------------------------------------------

    /**
     * Показать таблицу приборов.
     */
    @FXML
    private void showDevices() {
        if (schemeEditorController != null) {
            saveSchemeBeforeNavigation();
            schemeEditorView = null;
            schemeEditorController = null;
        }

        schemesBtn.setDisable(false);
        statusLabel.setText("Просмотр списка приборов");
        contentArea.getChildren().clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/devices.fxml"));
            Parent view = loader.load();

            DevicesTableController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setDeviceDAO(deviceDAO);
                LOGGER.info("DeviceDAO передан в DevicesTableController");

                if (schemeEditorController != null) {
                    ctrl.setSchemeEditorController(schemeEditorController);
                }

                ctrl.init();
            }

            contentArea.getChildren().add(view);
            LOGGER.info("Список приборов загружен успешно");
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки списка приборов: " + e.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить список приборов");
            LOGGER.error("Ошибка загрузки списка приборов: {}", e.getMessage(), e);
        }
    }

    /**
     * Показать группировку приборов.
     */
    @FXML
    private void showGroupedDevices() {
        if (schemeEditorController != null) {
            saveSchemeBeforeNavigation();
            schemeEditorView = null;
            schemeEditorController = null;
        }

        schemesBtn.setDisable(false);
        statusLabel.setText("Просмотр списка приборов по месту установки");
        contentArea.getChildren().clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/devices_grouped.fxml"));
            Parent view = loader.load();

            DevicesGroupedController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setDeviceDAO(deviceDAO);
                LOGGER.info("DeviceDAO передан в DevicesGroupedController");

                if (schemeEditorController != null) {
                    ctrl.setSchemeEditorController(schemeEditorController);
                }

                ctrl.init();
            }

            contentArea.getChildren().add(view);
            LOGGER.info("Группированный список приборов загружен успешно");
        } catch (Exception e) {
            statusLabel.setText("Ошибка загрузки списка приборов по месту установки: " + e.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить группированный список приборов");
            LOGGER.error("Ошибка загрузки группированного списка приборов: {}", e.getMessage(), e);
        }
    }

    /**
     * Метод для отображения галереи фото.
     */
    @FXML
    private void showPhotoGallery() {
        if (schemeEditorController != null) {
            saveSchemeBeforeNavigation();
            schemeEditorView = null;
            schemeEditorController = null;
        }

        schemesBtn.setDisable(false);
        statusLabel.setText("Просмотр фотографий приборов по местам установки");
        contentArea.getChildren().clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/photo-gallery.fxml"));
            Parent view = loader.load();

            PhotoGalleryController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setDeviceDAO(deviceDAO);
                LOGGER.info("DeviceDAO передан в PhotoGalleryController");
                ctrl.init();
            }

            contentArea.getChildren().add(view);
            LOGGER.info("Галерея фото загружена успешно");
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки галереи фото: " + e.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить галерею фото");
            LOGGER.error("Ошибка загрузки галереи фото: {}", e.getMessage(), e);
        }
    }

    /**
     * Показать редактор схем.
     */
    @FXML
    private void showSchemesEditor() {
        statusLabel.setText("Редактор схем");

        if (schemeEditorView != null && schemeEditorController != null) {
            saveSchemeBeforeNavigation();
            schemeEditorView = null;
            schemeEditorController = null;
            schemesBtn.setDisable(true);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/scheme-editor.fxml"));
            schemeEditorView = loader.load();
            schemeEditorController = loader.getController();

            if (schemeEditorController != null) {
                schemeEditorController.setDeviceDAO(deviceDAO);
                schemeEditorController.setSchemeDAO(schemeDAO);
                schemeEditorController.setDeviceLocationDAO(deviceLocationDAO);
                LOGGER.info("Все DAO переданы в SchemeEditorController");
                schemeEditorController.init();
            }

            contentArea.getChildren().clear();
            contentArea.getChildren().add(schemeEditorView);
            LOGGER.info("Редактор схем загружен успешно");
            schemesBtn.setDisable(true);

        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки редактора схем: " + e.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить редактор схем");
            LOGGER.error("Ошибка загрузки редактора схем: {}", e.getMessage(), e);
        }
    }

    /**
     * Показать форму добавления прибора.
     */
    @FXML
    private void showAddDeviceForm() {
        if (schemeEditorController != null) {
            saveSchemeBeforeNavigation();
            schemeEditorView = null;
            schemeEditorController = null;
        }

        schemesBtn.setDisable(false);
        statusLabel.setText("Добавление нового прибора");
        contentArea.getChildren().clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/add-device-form.fxml"));
            Parent view = loader.load();

            AddDeviceController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setDeviceDAO(deviceDAO);
                LOGGER.info("DeviceDAO передан в AddDeviceController");

                if (schemeEditorController != null) {
                    ctrl.setSchemeEditorController(schemeEditorController);
                }
            }

            contentArea.getChildren().add(view);
            LOGGER.info("Форма добавления прибора загружена успешно");
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки формы: " + e.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить форму добавления");
            LOGGER.error("Ошибка загрузки формы: {}", e.getMessage(), e);
        }
    }

    /**
     * Показать отчёты.
     */
    @FXML
    private void showReports() {
        if (schemeEditorController != null) {
            saveSchemeBeforeNavigation();
            schemeEditorView = null;
            schemeEditorController = null;
        }

        schemesBtn.setDisable(false);
        statusLabel.setText("Просмотр отчётов");
        contentArea.getChildren().clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/reports.fxml"));
            Parent view = loader.load();

            ReportsController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.init(deviceDAO, (Stage) contentArea.getScene().getWindow());
                LOGGER.info("ReportsController инициализирован");

                // Сохраняем ссылку на контроллер отчётов
                this.reportsController = ctrl;
            }

            contentArea.getChildren().add(view);
            LOGGER.info("Отчёты загружены успешно");
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки отчётов: " + e.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить отчёты");
            LOGGER.error("Ошибка загрузки отчётов: {}", e.getMessage(), e);
        }
    }
}