package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.managers.SyncManager;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.services.*;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
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
 *
 * @author vladimir_shi
 * @since 11.09.2025
 */
public class MainController {
    private static final Logger LOGGER = LogManager.getLogger(MainController.class);

    // ─── Длительности анимаций (мс) ───────────────────────────────
    private static final int ANIM_MINIMIZE_MS   = 180;
    private static final int ANIM_RESTORE_MS    = 180;
    private static final int ANIM_MAXIMIZE_MS   = 150;
    private static final int ANIM_UNMAXIMIZE_MS = 150;
    private static final int ANIM_LAUNCH_MS     = 300;

    // ─── Кнопки меню ──────────────────────────────────────────────
    public Button devicesBtn;
    public Button photoGalleryBtn;
    public Button schemesBtn;
    public Button reportsBtn;
    public Button settingsBtn;
    public Button exitBtn;

    @FXML private Label statusLabel;
    @FXML private VBox contentArea;
    @FXML private Button themeToggleBtn;
    @FXML private ImageView themeToggleIcon;
    @FXML private HBox topBar;
    @FXML private Button minimizeBtn;
    @FXML private Button maximizeBtn;
    @FXML private Button closeBtn;

    // Элементы поиска в верхней панели
    @FXML private HBox topSearchPanel;
    @FXML private Button topSearchToggleButton;
    @FXML private HBox topSearchFieldContainer;
    @FXML private TextField topSearchField;
    @FXML private ComboBox<String> topLocationFilter;
    @FXML private CheckBox topPhotosOnlyCheck;
    @FXML private Button topClearSearchButton;

    // Сервисы
    private DeviceDAO deviceDAO;
    private SchemeDAO schemeDAO;
    private DeviceLocationDAO deviceLocationDAO;
    private SyncManager syncManager;

    private SchemeEditorController schemeEditorController;
    private Parent schemeEditorView;
    private Scene scene;
    private boolean isDarkTheme = false;
    private ReportsController reportsController;
    private String currentActiveSection = null;
    private SearchableController currentSearchableController = null;
    private boolean isTopSearchExpanded = false;

    // ─── Управление окном ─────────────────────────────────────────
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isMaximized = false;
    private double restoreWidth  = 1200;
    private double restoreHeight = 800;
    private double restoreX = 0;
    private double restoreY = 0;
    private boolean isDraggingMaximized = false;
    private boolean isAnimating = false;

    // =============================================================
    //  Сеттеры сервисов
    // =============================================================

    public void setDatabaseService() {
        LOGGER.info("✅ DatabaseService сохранен в MainController");
    }

    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
        LOGGER.info("✅ DeviceDAO сохранен");
    }

    public void setSchemeDAO(SchemeDAO schemeDAO) {
        this.schemeDAO = schemeDAO;
        LOGGER.info("✅ SchemeDAO сохранен");
    }

    public void setDeviceLocationDAO(DeviceLocationDAO deviceLocationDAO) {
        this.deviceLocationDAO = deviceLocationDAO;
        LOGGER.info("✅ DeviceLocationDAO сохранен");
    }

    public void setSyncManager(SyncManager syncManager) {
        this.syncManager = syncManager;
        LOGGER.info("✅ SyncManager сохранён");
    }

    public Scene getScene() { return scene; }

    public void setScene(Scene scene) { this.scene = scene; }

    // =============================================================
    //  Сохранение схемы
    // =============================================================

    public void saveSchemeBeforeNavigation() {
        if (schemeEditorController == null) return;
        try {
            SchemeSaver saver = schemeEditorController.getSchemeSaver();
            if (saver == null) return;
            boolean hadChanges = saver.isDirty();
            saver.saveBeforeNavigation(schemeEditorController.getCurrentScheme());
            if (hadChanges) CustomAlertDialog.showAutoSaveNotification("Автосохранение", 1.3);
        } catch (Exception e) {
            LOGGER.error("Ошибка сохранения схемы: {}", e.getMessage());
            CustomAlertDialog.showError("Ошибка сохранения", "Не удалось сохранить схему: " + e.getMessage());
        }
    }

    private void saveSchemeOnExit() {
        if (schemeEditorController == null) return;
        try {
            SchemeSaver saver = schemeEditorController.getSchemeSaver();
            Scheme currentScheme = schemeEditorController.getCurrentScheme();
            if (saver == null || currentScheme == null) return;
            boolean hadChanges = saver.isDirty();
            saver.saveOnExit(currentScheme);
            if (hadChanges) CustomAlertDialog.showAutoSaveNotification("Сохранение при выходе", 0.5);
        } catch (Exception e) {
            LOGGER.error("Ошибка автосохранения при выходе: {}", e.getMessage());
            CustomAlertDialog.showWarning("Предупреждение",
                    "Не удалось сохранить схему при выходе. Последние изменения могут быть потеряны.");
        }
    }

    // =============================================================
    //  initialize
    // =============================================================

    @FXML
    private void initialize() {
        statusLabel.setText("Готов к работе");

        devicesBtn.getStyleClass().add("button-devices");
        photoGalleryBtn.getStyleClass().add("button-photo-gallery");
        schemesBtn.getStyleClass().add("button-schemes");
        reportsBtn.getStyleClass().add("button-reports");
        settingsBtn.getStyleClass().add("button-settings");
        themeToggleBtn.getStyleClass().add("button-theme-toggle");
        exitBtn.getStyleClass().add("button-exit");

        if (scene != null) {
            try {
                scene.getStylesheets().add(
                        Objects.requireNonNull(getClass().getResource("/styles/light-theme.css")).toExternalForm());
            } catch (Exception e) {
                LOGGER.warn("Не удалось загрузить CSS: {}", e.getMessage());
            }
        }

        currentActiveSection = null;
        updateNavigationButtonsState();
        setupTopSearch();
        setupWindowControls();

        LOGGER.info("✅ UI инициализирован");
    }

    // =============================================================
    //  Управление окном
    // =============================================================

    private void setupWindowControls() {
        if (topBar == null) return;

        // ── Перетаскивание окна ──────────────────────────────────
        topBar.setOnMousePressed(event -> {
            if (isAnimating) return;
            Stage stage = (Stage) topBar.getScene().getWindow();
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
            isDraggingMaximized = false;
            if (!isMaximized) {
                restoreWidth  = stage.getWidth();
                restoreHeight = stage.getHeight();
            }
        });

        topBar.setOnMouseDragged(event -> {
            if (isAnimating) return;
            Stage stage = (Stage) topBar.getScene().getWindow();
            if (isMaximized && !isDraggingMaximized) {
                isDraggingMaximized = true;
                double mouseXRatio = event.getSceneX() / stage.getWidth();
                restoreWindowOnDrag(stage, event.getScreenX(), mouseXRatio);
                xOffset = restoreWidth * mouseXRatio;
                yOffset = event.getSceneY();
            } else if (!isMaximized) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });

        // Двойной клик — развернуть / восстановить
        topBar.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) toggleMaximize();
        });

        // ── Кнопка сворачивания ──────────────────────────────────
        if (minimizeBtn != null) {
            minimizeBtn.setOnAction(_ -> animateMinimize());
        }

        // ── Кнопка разворачивания / восстановления ───────────────
        if (maximizeBtn != null) {
            maximizeBtn.setOnAction(_ -> toggleMaximize());
        }

        // ── Кнопка закрытия ─────────────────────────────────────
        if (closeBtn != null) {
            closeBtn.setOnAction(_ -> exitApp());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Анимация появления при запуске
    // ─────────────────────────────────────────────────────────────

    public void playLaunchAnimation(Stage stage) {
        Parent root = stage.getScene().getRoot();
        root.setOpacity(0);
        root.setScaleX(0.96);
        root.setScaleY(0.96);

        FadeTransition fade = new FadeTransition(Duration.millis(ANIM_LAUNCH_MS), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(ANIM_LAUNCH_MS), root);
        scale.setFromX(0.96);
        scale.setFromY(0.96);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, scale).play();
    }

    // ─────────────────────────────────────────────────────────────
    //  Анимация сворачивания / восстановления из таскбара
    // ─────────────────────────────────────────────────────────────

    private void animateMinimize() {
        if (isAnimating) return;
        Stage stage = (Stage) minimizeBtn.getScene().getWindow();

        if (!isMaximized) {
            restoreWidth  = stage.getWidth();
            restoreHeight = stage.getHeight();
            restoreX      = stage.getX();
            restoreY      = stage.getY();
        }

        isAnimating = true;
        Parent root = stage.getScene().getRoot();

        FadeTransition fade = new FadeTransition(Duration.millis(ANIM_MINIMIZE_MS), root);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_IN);

        ScaleTransition scale = new ScaleTransition(Duration.millis(ANIM_MINIMIZE_MS), root);
        scale.setToX(0.92);
        scale.setToY(0.92);
        scale.setInterpolator(Interpolator.EASE_IN);

        ParallelTransition anim = new ParallelTransition(fade, scale);
        anim.setOnFinished(_ -> {
            root.setOpacity(0);
            root.setScaleX(0.92);
            root.setScaleY(0.92);
            stage.setIconified(true);
            isAnimating = false;
        });
        anim.play();

        // При восстановлении из таскбара — плавное появление
        stage.iconifiedProperty().addListener((obs, wasIconified, nowIconified) -> {
            if (wasIconified && !nowIconified) {
                obs.removeListener((_, _, _) -> {});
                Platform.runLater(() -> animateRestore(stage));
            }
        });
    }

    private void animateRestore(Stage stage) {
        Parent root = stage.getScene().getRoot();

        FadeTransition fade = new FadeTransition(Duration.millis(ANIM_RESTORE_MS), root);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(ANIM_RESTORE_MS), root);
        scale.setFromX(0.92);
        scale.setFromY(0.92);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, scale).play();
    }

    // ─────────────────────────────────────────────────────────────
    //  Развернуть / восстановить из максимизации
    // ─────────────────────────────────────────────────────────────

    private void toggleMaximize() {
        if (isAnimating) return;
        Stage stage = (Stage) maximizeBtn.getScene().getWindow();

        if (isMaximized) {
            animateUnmaximize(stage);
        } else {
            restoreWidth  = stage.getWidth();
            restoreHeight = stage.getHeight();
            restoreX      = stage.getX();
            restoreY      = stage.getY();
            animateMaximize(stage);
        }
    }

    private void animateMaximize(Stage stage) {
        isAnimating = true;
        Parent root = stage.getScene().getRoot();
        
        // Сначала анимация, потом максимизация
        root.setOpacity(0.85);
        root.setScaleX(0.98);
        root.setScaleY(0.98);

        FadeTransition fade = new FadeTransition(Duration.millis(ANIM_MAXIMIZE_MS), root);
        fade.setFromValue(0.85);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition scale = new ScaleTransition(Duration.millis(ANIM_MAXIMIZE_MS), root);
        scale.setFromX(0.98);
        scale.setFromY(0.98);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition anim = new ParallelTransition(fade, scale);
        anim.setOnFinished(_ -> {
            stage.setMaximized(true);
            isMaximized = true;
            maximizeBtn.setText("▢");
            isAnimating = false;
        });
        anim.play();
    }

    private void animateUnmaximize(Stage stage) {
        isAnimating = true;
        Parent root = stage.getScene().getRoot();

        // Сначала меняем геометрию, потом анимация
        stage.setMaximized(false);
        stage.setWidth(restoreWidth);
        stage.setHeight(restoreHeight);
        stage.setX(restoreX);
        stage.setY(restoreY);
        isMaximized = false;
        maximizeBtn.setText("□");

        root.setOpacity(0.85);
        root.setScaleX(0.98);
        root.setScaleY(0.98);

        FadeTransition fade = new FadeTransition(Duration.millis(ANIM_UNMAXIMIZE_MS), root);
        fade.setFromValue(0.85);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition scale = new ScaleTransition(Duration.millis(ANIM_UNMAXIMIZE_MS), root);
        scale.setFromX(0.98);
        scale.setFromY(0.98);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition anim = new ParallelTransition(fade, scale);
        anim.setOnFinished(_ -> isAnimating = false);
        anim.play();
    }

    private void restoreWindowOnDrag(Stage stage, double mouseScreenX, double mouseXRatio) {
        isAnimating = true;
        Parent root = stage.getScene().getRoot();
        
        stage.setMaximized(false);
        stage.setWidth(restoreWidth);
        stage.setHeight(restoreHeight);
        stage.setX(mouseScreenX - (restoreWidth * mouseXRatio));
        stage.setY(0);
        isMaximized = false;
        maximizeBtn.setText("□");

        root.setOpacity(0.9);
        root.setScaleX(0.98);
        root.setScaleY(0.98);

        FadeTransition fade = new FadeTransition(Duration.millis(120), root);
        fade.setFromValue(0.9);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition scale = new ScaleTransition(Duration.millis(120), root);
        scale.setFromX(0.98);
        scale.setFromY(0.98);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition anim = new ParallelTransition(fade, scale);
        anim.setOnFinished(_ -> isAnimating = false);
        anim.play();
    }

    // =============================================================
    //  Поиск
    // =============================================================

    private void setupTopSearch() {
        if (topSearchToggleButton != null) topSearchToggleButton.setOnAction(_ -> toggleTopSearch());
        if (topClearSearchButton  != null) topClearSearchButton.setOnAction(_  -> clearTopSearch());
        if (topSearchField        != null) topSearchField.textProperty().addListener((_, _, _) -> updateClearButtonVisibility());
        if (topLocationFilter     != null) topLocationFilter.valueProperty().addListener((_, _, _) -> updateClearButtonVisibility());
        if (topPhotosOnlyCheck    != null) topPhotosOnlyCheck.selectedProperty().addListener((_, _, _) -> updateClearButtonVisibility());
    }

    private void toggleTopSearch() {
        if (topSearchFieldContainer == null) return;
        isTopSearchExpanded = !isTopSearchExpanded;

        if (isTopSearchExpanded) {
            topSearchFieldContainer.setVisible(true);
            topSearchFieldContainer.setManaged(true);
            topSearchFieldContainer.setOpacity(1.0);

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), topSearchFieldContainer);
            slideIn.setFromX(50);
            slideIn.setToX(0);
            slideIn.play();
        } else {
            if (topSearchField     != null) topSearchField.clear();
            if (topLocationFilter  != null) topLocationFilter.setValue("Все места");
            if (topPhotosOnlyCheck != null) topPhotosOnlyCheck.setSelected(false);

            TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), topSearchFieldContainer);
            slideOut.setFromX(0);
            slideOut.setToX(50);
            slideOut.setOnFinished(_ -> {
                topSearchFieldContainer.setVisible(false);
                topSearchFieldContainer.setManaged(false);
            });
            slideOut.play();
        }
    }

    private void clearTopSearch() {
        if (topSearchField != null) topSearchField.clear();
        if (currentSearchableController != null) currentSearchableController.clearFilters();
        if (isTopSearchExpanded) toggleTopSearch();
    }

    private void resetSearchOnNavigation() {
        if (topSearchField != null) topSearchField.clear();
        if (isTopSearchExpanded) {
            isTopSearchExpanded = false;
            if (topSearchFieldContainer != null) {
                topSearchFieldContainer.setVisible(false);
                topSearchFieldContainer.setManaged(false);
            }
        }
        currentSearchableController = null;
    }

    private void updateClearButtonVisibility() {
        if (topClearSearchButton == null) return;
        boolean hasText   = topSearchField    != null && topSearchField.getText() != null && !topSearchField.getText().isEmpty();
        boolean hasFilter = topLocationFilter  != null && topLocationFilter.isVisible()
                && topLocationFilter.getValue() != null && !"Все места".equals(topLocationFilter.getValue());
        boolean hasCheck  = topPhotosOnlyCheck != null && topPhotosOnlyCheck.isVisible() && topPhotosOnlyCheck.isSelected();
        topClearSearchButton.setVisible(hasText || hasFilter || hasCheck);
    }

    private void showTopSearchPanel(boolean show, boolean hasExtendedFilters) {
        if (topSearchPanel == null) return;
        topSearchPanel.setVisible(show);
        topSearchPanel.setManaged(show);
        if (topLocationFilter  != null) { topLocationFilter.setVisible(hasExtendedFilters);  topLocationFilter.setManaged(hasExtendedFilters); }
        if (topPhotosOnlyCheck != null) { topPhotosOnlyCheck.setVisible(hasExtendedFilters); topPhotosOnlyCheck.setManaged(hasExtendedFilters); }
        if (!show && isTopSearchExpanded) {
            isTopSearchExpanded = false;
            if (topSearchFieldContainer != null) { topSearchFieldContainer.setVisible(false); topSearchFieldContainer.setManaged(false); }
        }
    }

    // =============================================================
    //  Навигация
    // =============================================================

    private void updateNavigationButtonsState() {
        StyleUtils.setNavigationButtonActive(devicesBtn,      false, "button-devices",       "button-devices-hover",       "button-devices-active");
        StyleUtils.setNavigationButtonActive(photoGalleryBtn, false, "button-photo-gallery", "button-photo-gallery-hover", "button-photo-gallery-active");
        StyleUtils.setNavigationButtonActive(schemesBtn,      false, "button-schemes",       "button-schemes-hover",       "button-schemes-active");
        StyleUtils.setNavigationButtonActive(reportsBtn,      false, "button-reports",       "button-reports-hover",       "button-reports-active");

        if (currentActiveSection == null) return;

        switch (currentActiveSection) {
            case "devices"      -> StyleUtils.setNavigationButtonActive(devicesBtn,      true, "button-devices",       "button-devices-hover",       "button-devices-active");
            case "photoGallery" -> StyleUtils.setNavigationButtonActive(photoGalleryBtn, true, "button-photo-gallery", "button-photo-gallery-hover", "button-photo-gallery-active");
            case "schemes"      -> StyleUtils.setNavigationButtonActive(schemesBtn,      true, "button-schemes",       "button-schemes-hover",       "button-schemes-active");
            case "reports"      -> StyleUtils.setNavigationButtonActive(reportsBtn,      true, "button-reports",       "button-reports-hover",       "button-reports-active");
        }
    }

    @FXML
    private void toggleTheme() {
        if (scene == null) { CustomAlertDialog.showError("Ошибка", "Scene не передана"); return; }

        if (isDarkTheme) {
            scene.getStylesheets().clear();
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/light-theme.css")).toExternalForm());
            isDarkTheme = false;
            StyleUtils.setCurrentTheme("/styles/light-theme.css");
            if (themeToggleIcon != null)
                themeToggleIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/theme-white.png"))));
        } else {
            scene.getStylesheets().clear();
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/dark-theme.css")).toExternalForm());
            isDarkTheme = true;
            StyleUtils.setCurrentTheme("/styles/dark-theme.css");
            if (themeToggleIcon != null)
                themeToggleIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/theme-dark.png"))));
        }

        if (reportsController != null) reportsController.refreshTheme();
    }

    @FXML
    private void exitApp() {
        String message = schemeEditorController == null
                ? "Вы уверены, что хотите выйти?"
                : "Вы уверены, что хотите выйти? Текущая схема будет автоматически сохранена.";

        boolean confirmExit = CustomAlertDialog.showConfirmation("Подтверждение выхода", message);
        if (!confirmExit) return;

        if (schemeEditorController != null) saveSchemeOnExit();

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(1500), _ -> Platform.exit()));
        timeline.play();
    }

    private void refreshCurrentView() {
        if      ("devices".equals(currentActiveSection))  showDevices();
        else if ("schemes".equals(currentActiveSection))  showSchemesEditor();
        else if ("settings".equals(currentActiveSection)) showSettings();
    }

    // =============================================================
    //  Переходы к представлениям
    // =============================================================

    @FXML
    private void showDevices() {
        if ("devices".equals(currentActiveSection)) return;
        resetSearchOnNavigation();
        currentActiveSection = "devices";
        if (schemeEditorController != null) { saveSchemeBeforeNavigation(); schemeEditorView = null; schemeEditorController = null; }
        statusLabel.setText("Просмотр списка приборов");
        contentArea.getChildren().clear();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/devices.fxml"));
            Parent view = loader.load();
            DevicesTableController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setDeviceDAO(deviceDAO);
                if (schemeEditorController != null) ctrl.setSchemeEditorController(schemeEditorController);
                ctrl.init();
                currentSearchableController = ctrl;
                if (topSearchField != null) ctrl.bindSearchField(topSearchField);
                showTopSearchPanel(true, false);
            }
            contentArea.getChildren().add(view);
            updateNavigationButtonsState();
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки списка приборов: " + e.getMessage());
            CustomAlertDialog.showError("Ошибка загрузки", "Не удалось загрузить список приборов");
            LOGGER.error("Ошибка загрузки списка приборов: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void showPhotoGallery() {
        if ("photoGallery".equals(currentActiveSection)) return;
        resetSearchOnNavigation();
        currentActiveSection = "photoGallery";
        if (schemeEditorController != null) { saveSchemeBeforeNavigation(); schemeEditorView = null; schemeEditorController = null; }
        schemesBtn.setDisable(false);
        statusLabel.setText("Просмотр фотографий приборов по местам установки");
        contentArea.getChildren().clear();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/photo-gallery.fxml"));
            Parent view = loader.load();
            PhotoGalleryController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setDeviceDAO(deviceDAO);
                ctrl.init();
                currentSearchableController = ctrl;
                if (topSearchField     != null) ctrl.bindSearchField(topSearchField);
                if (topLocationFilter  != null) ctrl.bindLocationFilter(topLocationFilter);
                if (topPhotosOnlyCheck != null) ctrl.bindPhotosOnlyCheck(topPhotosOnlyCheck);
                showTopSearchPanel(true, true);
            }
            contentArea.getChildren().add(view);
            updateNavigationButtonsState();
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки галереи фото: " + e.getMessage());
            CustomAlertDialog.showError("Ошибка загрузки", "Не удалось загрузить галерею фото");
            LOGGER.error("Ошибка загрузки галереи фото: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void showSchemesEditor() {
        if ("schemes".equals(currentActiveSection)) return;
        resetSearchOnNavigation();
        currentActiveSection = "schemes";
        statusLabel.setText("Редактор схем");
        if (schemeEditorView != null && schemeEditorController != null) {
            saveSchemeBeforeNavigation(); schemeEditorView = null; schemeEditorController = null;
            updateNavigationButtonsState(); return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/scheme-editor.fxml"));
            schemeEditorView = loader.load();
            schemeEditorController = loader.getController();
            if (schemeEditorController != null) {
                schemeEditorController.setDeviceDAO(deviceDAO);
                schemeEditorController.setSchemeDAO(schemeDAO);
                schemeEditorController.setDeviceLocationDAO(deviceLocationDAO);
                schemeEditorController.init();
            }
            contentArea.getChildren().clear();
            contentArea.getChildren().add(schemeEditorView);
            currentSearchableController = null;
            showTopSearchPanel(false, false);
            updateNavigationButtonsState();
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки редактора схем: " + e.getMessage());
            CustomAlertDialog.showError("Ошибка загрузки", "Не удалось загрузить редактор схем");
            LOGGER.error("Ошибка загрузки редактора схем: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void showReports() {
        if ("reports".equals(currentActiveSection)) return;
        resetSearchOnNavigation();
        currentActiveSection = "reports";
        if (schemeEditorController != null) { saveSchemeBeforeNavigation(); schemeEditorView = null; schemeEditorController = null; }
        schemesBtn.setDisable(false);
        statusLabel.setText("Просмотр отчётов");
        contentArea.getChildren().clear();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/reports.fxml"));
            Parent view = loader.load();
            ReportsController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.init(deviceDAO, (Stage) contentArea.getScene().getWindow());
                this.reportsController = ctrl;
            }
            contentArea.getChildren().add(view);
            currentSearchableController = null;
            showTopSearchPanel(false, false);
            updateNavigationButtonsState();
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки отчётов: " + e.getMessage());
            CustomAlertDialog.showError("Ошибка загрузки", "Не удалось загрузить отчёты");
            LOGGER.error("Ошибка загрузки отчётов: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void showSettings() {
        if ("settings".equals(currentActiveSection)) return;
        resetSearchOnNavigation();
        currentActiveSection = "settings";
        if (schemeEditorController != null) { saveSchemeBeforeNavigation(); schemeEditorView = null; schemeEditorController = null; }
        statusLabel.setText("Настройки");
        contentArea.getChildren().clear();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/settings.fxml"));
            Parent view = loader.load();
            SettingsController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setSyncManager(syncManager);
                ctrl.setDeviceDAO(deviceDAO);
                ctrl.setOnDataChanged(this::refreshCurrentView);
                ctrl.init();
            }
            contentArea.getChildren().add(view);
            currentSearchableController = null;
            showTopSearchPanel(false, false);
            updateNavigationButtonsState();
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки настроек: " + e.getMessage());
            CustomAlertDialog.showError("Ошибка загрузки", "Не удалось загрузить настройки");
            LOGGER.error("Ошибка загрузки настроек: {}", e.getMessage(), e);
        }
    }
}