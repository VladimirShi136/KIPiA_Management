package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.DeviceLocationDAO;
import com.kipia.management.kipia_management.services.SchemeDAO;
import com.kipia.management.kipia_management.utils.CustomAlert;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.stage.Stage;

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
     * Передаём сцену, чтобы можно было менять стили.
     */
    public void setScene(Scene scene) {
        this.scene = scene;
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
        System.exit(0);
    }

    // ---------------------------------------------------------
    //  Переходы к разным представлениям
    // ---------------------------------------------------------

    /**
     * Показать таблицу приборов.
     */
    @FXML
    private void showDevices() {
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
        statusLabel.setText("Редактор схем");  // Обновляем статус

        // Если экземпляр уже создан — просто показываем сохранённый view (состояние не теряется)
        if (schemeEditorView != null && schemeEditorController != null) {
            contentArea.getChildren().clear();
            contentArea.getChildren().add(schemeEditorView);
            LOGGER.info("Показан существующий редактор схем (состояние сохранено)");
            return;  // Не перезагружаем, иконки/приборы остаются
        }

        // Первый запуск: Загружаем FXML и инициализируем
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/scheme-editor.fxml"));
            schemeEditorView = loader.load();  // Сохраняем Root Node (Parent)

            schemeEditorController = loader.getController();
            LOGGER.info("Создан новый контроллер редактора схем");

            // Внедряем DAO (только один раз)
            schemeEditorController.setDeviceDAO(deviceDAO);
            schemeEditorController.setSchemeDAO(schemeDAO);
            schemeEditorController.setDeviceLocationDAO(deviceLocationDAO);

            // Вызываем init() только один раз
            schemeEditorController.init();

            // Добавляем view в contentArea
            contentArea.getChildren().clear();
            contentArea.getChildren().add(schemeEditorView);

            LOGGER.info("Редактор схем загружен впервые успешно");
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