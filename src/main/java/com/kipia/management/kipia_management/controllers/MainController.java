package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.DeviceLocationDAO;
import com.kipia.management.kipia_management.services.SchemeDAO;
import com.kipia.management.kipia_management.services.SchemeSaver;
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
import java.util.logging.Logger;

/**
 * Главный контроллер – отвечает за навигацию
 * (переключение представлений, переключение темы, выход из приложения).
 */
public class MainController {
    // ── Логгер для сообщений ────────
    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());
    // ── Кнопки меню ─────────────────────────────────────
    public Button devicesBtn;
    public Button addDeviceBtn;
    public Button groupedDevicesBtn;
    public Button schemesBtn;
    public Button reportsBtn;
    public Button exitBtn;
    // ── UI‑элементы из main.fxml ────────
    @FXML
    private Label statusLabel;
    @FXML
    private VBox contentArea;
    @FXML
    private Button themeToggleBtn;
    // ── Сервисы доступа к БД ───────────────────────────────
    private DeviceDAO deviceDAO;
    private SchemeDAO schemeDAO;
    private DeviceLocationDAO deviceLocationDAO;
    // ── Экземпляр редактора схем для передачи в другие контроллеры ─
    private SchemeEditorController schemeEditorController;
    private Parent schemeEditorView;  // Сохранённый Root Node из FXML
    // ── Сцена ─────────────────────────────────────────────
    private Scene scene;
    // ── Тема ─────────────────────────────────────────────
    private boolean isDarkTheme = false;

    // ---------------------------------------------------------
    //  Методы, вызываемые из главного окна
    // ---------------------------------------------------------

    /**
     * Внедряем DAO из главного приложения (MainApp).
     */
    public void setDeviceDAO(DeviceDAO dao) {
        this.deviceDAO = dao;
    }

    public void setSchemeDAO(SchemeDAO dao) {
        this.schemeDAO = dao;
    }

    public void setDeviceLocationDAO(DeviceLocationDAO dao) {
        this.deviceLocationDAO = dao;
    }

    /**
     * Получение сцены
     * @return - сцена
     */
    public Scene getScene () {
        return scene;
    }

    /**
     * Передаём сцену, чтобы можно было менять стили.
     */
    public void setScene(Scene scene) {
        this.scene = scene;
    }

    /**
     * Сохранение схемы при переходе из редактора схем в другой контроллер.
     * Показывает уведомление «Автосохранение» на <b>durationSec</b> секунд.
     */
    public void saveSchemeBeforeNavigation() {
        if (schemeEditorController == null) {
            return;
        }

        try {
            System.out.println("DEBUG: Saving scheme before navigation from editor");
            schemeEditorController.getSchemeSaver().saveBeforeNavigation(schemeEditorController.getCurrentScheme());

            // Показываем уведомление на n секунд
            CustomAlert.showAutoSaveNotification("Автосохранение", 1.3);

        } catch (Exception e) {
            System.err.println("Ошибка при сохранении схемы: " + e.getMessage());
            CustomAlert.showError("Ошибка сохранения", "Не удалось сохранить схему: " + e.getMessage());
        }
    }

    /**
     * Сохранение схемы при выходе из приложения.
     */
    private void saveSchemeOnExit() {
        if (schemeEditorController != null) {
            try {
                System.out.println("DEBUG: Auto-saving scheme on application exit");

                // Используем SchemeSaver из SchemeEditorController
                SchemeSaver saver = schemeEditorController.getSchemeSaver();
                Scheme currentScheme = schemeEditorController.getCurrentScheme();

                if (saver != null && currentScheme != null) {
                    saver.saveOnExit(currentScheme);
                    CustomAlert.showAutoSaveNotification("Сохранение при выходе", 1.3);
                } else {
                    System.out.println("DEBUG: No active scheme to save on exit");
                }
            } catch (Exception e) {
                System.err.println("Ошибка при автосохранении схемы при выходе: " + e.getMessage());
                CustomAlert.showWarning("Предупреждение",
                        "Не удалось сохранить схему при выходе. Последние изменения могут быть потеряны.");
            }
        } else {
            System.out.println("DEBUG: No active scheme editor - nothing to save on exit");
        }
    }

    /**
     * Инициализация UI‑элементов (hover‑эффекты, стили).
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

        // Подключаем CSS Alerts к сцене, если она уже доступна
        if (scene != null) {
            try {
                scene.getStylesheets().add(
                        Objects.requireNonNull(getClass().getResource("/styles/light-theme.css")).toExternalForm()
                );
            } catch (Exception e) {
                System.err.println("Не удалось загрузить CSS Alerts: " + e.getMessage());
            }
        }
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
    }

    /**
     * Выход из приложения.
     */
    @FXML
    private void exitApp() {
        // Определяем сообщение в зависимости от наличия активного редактора
        String message;
        if (schemeEditorController == null) {
            message = "Вы уверены, что хотите выйти?";
        } else {
            message = "Вы уверены, что хотите выйти? Текущая схема будет автоматически сохранена.";
        }

        // Всегда показываем подтверждение, но с разным текстом
        boolean confirmExit = CustomAlert.showConfirmation("Подтверждение выхода", message);

        if (confirmExit) {
            // Сохраняем только если есть активный редактор
            if (schemeEditorController != null) {
                saveSchemeOnExit();
            }
            // Используем Timeline для задержки перед выходом
            Timeline timeline = new Timeline(new KeyFrame(
                    Duration.millis(1500), // задержка закрытия
                    e -> Platform.exit()
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
        // Сохраняем, если редактор был открыт
        if (schemeEditorController != null) {
            saveSchemeBeforeNavigation();
            // Очищаем ссылки (важно!)
            schemeEditorView = null;
            schemeEditorController = null;
        }

        // Разблокируем кнопку
        schemesBtn.setDisable(false);

        statusLabel.setText("Просмотр списка приборов");
        contentArea.getChildren().clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/devices.fxml"));
            Parent view = loader.load();

            // Получаем контроллер и передаём ему DAO
            DevicesTableController ctrl = loader.getController();
            ctrl.setDeviceDAO(deviceDAO);
            if (schemeEditorController != null) {
                ctrl.setSchemeEditorController(schemeEditorController);
            }
            ctrl.init();  // инициализируем таблицу
            contentArea.getChildren().add(view);
            LOGGER.info("Список приборов загружен успешно");
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки списка приборов: " + e.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить список приборов");
            LOGGER.severe("Ошибка загрузки списка приборов: " + e.getMessage());
        }
    }

    /**
     * Показать группировку приборов.
     */
    @FXML
    private void showGroupedDevices() {
        // Сохраняем, если редактор был открыт
        if (schemeEditorController != null) {
            saveSchemeBeforeNavigation();
            // Очищаем ссылки (важно!)
            schemeEditorView = null;
            schemeEditorController = null;
        }

        // Разблокируем кнопку
        schemesBtn.setDisable(false);

        statusLabel.setText("Просмотр списка приборов по месту установки");
        contentArea.getChildren().clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/devices_grouped.fxml"));
            Parent view = loader.load();

            // Получаем контроллер и передаём ему DAO
            DevicesGroupedController ctrl = loader.getController();
            ctrl.setDeviceDAO(deviceDAO);
            if (schemeEditorController != null) {
                ctrl.setSchemeEditorController(schemeEditorController);
            }
            ctrl.init();
            contentArea.getChildren().add(view);
            LOGGER.info("Группированный список приборов загружен успешно");
        } catch (Exception ex) {
            System.err.println("Ошибка загрузки списка приборов по месту установки: " + ex.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить группированный список приборов");
            LOGGER.severe("Ошибка загрузки группированного списка приборов: " + ex.getMessage());
        }
    }

    /**
     * Показать редактор схем.
     */
    @FXML
    private void showSchemesEditor() {
        statusLabel.setText("Редактор схем");

        // Если редактор уже загружен → это попытка уйти → сохраняем и очищаем ссылки
        if (schemeEditorView != null && schemeEditorController != null) {
            saveSchemeBeforeNavigation(); // Автосохранение

            // Очищаем ссылки → редактор считается закрытым
            schemeEditorView = null;
            schemeEditorController = null;

            // Блокируем кнопку (если нужно)
            schemesBtn.setDisable(true);
            return;
        }

        // Загружаем редактор (первый раз или после ухода)
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/scheme-editor.fxml"));
            schemeEditorView = loader.load();
            schemeEditorController = loader.getController();

            schemeEditorController.setDeviceDAO(deviceDAO);
            schemeEditorController.setSchemeDAO(schemeDAO);
            schemeEditorController.setDeviceLocationDAO(deviceLocationDAO);
            schemeEditorController.init();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(schemeEditorView);
            LOGGER.info("Редактор схем загружен успешно");

            // Блокируем кнопку, пока редактор открыт
            schemesBtn.setDisable(true);

        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки редактора схем: " + e.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить редактор схем");
            LOGGER.severe("Ошибка загрузки редактора схем: " + e.getMessage());
        }
    }

    /**
     * Показать форму добавления прибора.
     */
    @FXML
    private void showAddDeviceForm() {
        // Сохраняем, если редактор был открыт
        if (schemeEditorController != null) {
            saveSchemeBeforeNavigation();
            // Очищаем ссылки (важно!)
            schemeEditorView = null;
            schemeEditorController = null;
        }

        // Разблокируем кнопку
        schemesBtn.setDisable(false);

        statusLabel.setText("Добавление нового прибора");
        contentArea.getChildren().clear();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/add-device-form.fxml"));
            Parent view = loader.load();

            AddDeviceController ctrl = loader.getController();
            ctrl.setDeviceDAO(deviceDAO);
            if (schemeEditorController != null) {
                ctrl.setSchemeEditorController(schemeEditorController);
            }
            contentArea.getChildren().add(view);
            LOGGER.info("Форма добавления прибора загружена успешно");
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки формы: " + e.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить форму добавления");
            LOGGER.severe("Ошибка загрузки формы: " + e.getMessage());
        }
    }

    /**
     * Показать отчёты.
     */
    @FXML
    private void showReports() {
        // Сохраняем, если редактор был открыт
        if (schemeEditorController != null) {
            saveSchemeBeforeNavigation();
            // Очищаем ссылки (важно!)
            schemeEditorView = null;
            schemeEditorController = null;
        }

        // Разблокируем кнопку
        schemesBtn.setDisable(false);

        statusLabel.setText("Просмотр отчётов");
        contentArea.getChildren().clear();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/reports.fxml"));
            Parent view = loader.load();

            ReportsController ctrl = loader.getController();
            ctrl.init(deviceDAO, (Stage) contentArea.getScene().getWindow());

            contentArea.getChildren().add(view);
            LOGGER.info("Отчёты загружены успешно");
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки отчётов: " + e.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить отчёты");
            LOGGER.severe("Ошибка загрузки отчётов: " + e.getMessage());
        }
    }
}