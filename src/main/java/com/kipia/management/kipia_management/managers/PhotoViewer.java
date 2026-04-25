package com.kipia.management.kipia_management.managers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;
import com.kipia.management.kipia_management.utils.ImageUtils;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Кастомное окно просмотра фотографий
 *
 * @author vladimir_shi
 * @since 09.04.2026
 */
public class PhotoViewer {
    private static final Logger LOGGER = LogManager.getLogger(PhotoViewer.class);

    private static final String ICON_BACK        = "/images/back.png";
    private static final String ICON_FORWARD     = "/images/forward.png";
    private static final String ICON_DELETE      = "/images/delete.png";
    private static final String ICON_OPEN_SYSTEM = "/images/open-system.png";

    // ─── Запись об одном фото (прибор + имя файла + абсолютный путь) ─────────
    public record PhotoEntry(Device device, String photoName, String absolutePath) {}

    // ─── Callback, вызываемый после удаления фото в multiDeviceMode ──────────
    public interface OnPhotoDeletedCallback {
        void onDeleted(Device device, String photoName);
    }

    // ─── Основные поля ────────────────────────────────────────────────────────
    private final PhotoManager photoManager;
    private final Stage        ownerStage;
    private final boolean      darkTheme;
    private final boolean      multiDeviceMode;
    private final String       locationTitle;

    /** Режим одного прибора: device + список имён файлов */
    private final Device       device;   // null в multiDeviceMode
    private final List<String> photos;   // null в multiDeviceMode

    /** Режим нескольких приборов: изменяемый список записей */
    private final List<PhotoEntry> photoEntries; // null в одиночном режиме

    /** Callback для уведомления внешнего контроллера об удалении */
    private OnPhotoDeletedCallback onPhotoDeletedCallback;

    // ─── UI ──────────────────────────────────────────────────────────────────
    private Stage      stage;
    private ImageView  imageView;
    private Label      counterLabel;
    private Button     prevBtn;
    private Button     nextBtn;
    private Button     deleteBtn;
    private Button     openSystemBtn;

    // Блок информации о приборе (обновляется при навигации в multiDeviceMode)
    private Label deviceNameLabel;
    private Label deviceInventoryLabel;
    private Label deviceLocationLabel;
    private Label deviceValveLabel;

    private int currentIndex = 0;

    // ─── Зум ─────────────────────────────────────────────────────────────────
    private final double ZOOM_FACTOR    = 3.0;
    private double initialScale         = 1.0;
    private double initialTranslateX    = 0.0;
    private double initialTranslateY    = 0.0;
    private boolean isZoomed            = false;
    private Pane       zoomOverlay;
    private VBox       mainContainer;
    private StackPane  imageContainer;
    private HBox       topPanel;

    // ═════════════════════════════════════════════════════════════════════════
    // Конструктор — одиночный прибор
    // ═════════════════════════════════════════════════════════════════════════
    public PhotoViewer(PhotoManager photoManager, Device device,
                       List<String> photos, Stage ownerStage) {
        this.photoManager    = photoManager;
        this.device          = device;
        this.ownerStage      = ownerStage;
        this.darkTheme       = StyleUtils.getCurrentTheme().contains("dark");
        this.locationTitle   = null;
        this.multiDeviceMode = false;
        this.photoEntries    = null;

        this.photos = photos.stream()
                .filter(photoName -> {
                    String fullPath = photoManager.getFullPhotoPath(device, photoName);
                    if (fullPath == null) return false;
                    File file = new File(fullPath);
                    return file.exists() && !file.isDirectory();
                })
                .collect(Collectors.toList());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Конструктор — несколько приборов (режим локации)
    // ═════════════════════════════════════════════════════════════════════════
    public PhotoViewer(PhotoManager photoManager, String locationTitle,
                       List<PhotoEntry> entries, Stage ownerStage) {
        this.photoManager    = photoManager;
        this.device          = null;
        this.photos          = null;
        this.ownerStage      = ownerStage;
        this.darkTheme       = StyleUtils.getCurrentTheme().contains("dark");
        this.locationTitle   = locationTitle;
        this.multiDeviceMode = true;

        // Фильтруем только существующие файлы
        this.photoEntries = entries.stream()
                .filter(e -> {
                    File f = new File(e.absolutePath());
                    return f.exists() && !f.isDirectory();
                })
                .collect(Collectors.toCollection(ArrayList::new)); // изменяемый список
    }

    /** Установить callback на удаление фото (для обновления карточки в галерее) */
    public void setOnPhotoDeletedCallback(OnPhotoDeletedCallback callback) {
        this.onPhotoDeletedCallback = callback;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Публичный метод показа
    // ═════════════════════════════════════════════════════════════════════════
    public void show() {
        boolean empty = multiDeviceMode ? photoEntries.isEmpty() : photos.isEmpty();
        if (empty) {
            CustomAlertDialog.showInfo("Просмотр фото", "Нет доступных фотографий");
            return;
        }
        createAndShowStage();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Создание окна
    // ═════════════════════════════════════════════════════════════════════════
    private void createAndShowStage() {
        stage = new Stage();
        stage.setTitle(locationTitle != null ? locationTitle
                : "Просмотр фото: " + device.getName());
        stage.initOwner(ownerStage);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setMaximized(true);

        createUIComponents();
        VBox root = buildLayout();

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.BLACK);

        stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case LEFT,  A               -> navigateTo(-1);
                case RIGHT, D               -> navigateTo(1);
                case DELETE, BACK_SPACE     -> deleteCurrentPhoto();
                case ESCAPE                 -> stage.close();
            }
        });

        stage.setScene(scene);
        stage.show();
        loadPhotoAtIndex(currentIndex);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UI компоненты
    // ═════════════════════════════════════════════════════════════════════════
    private void createUIComponents() {
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);

        counterLabel = new Label();
        counterLabel.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 14px; -fx-font-weight: bold;",
                darkTheme ? "#ecf0f1" : "#2c3e50"
        ));

        prevBtn       = createNavAndActButton(ICON_BACK,        "Назад");
        nextBtn       = createNavAndActButton(ICON_FORWARD,     "Вперёд");
        openSystemBtn = createNavAndActButton(ICON_OPEN_SYSTEM, "Открыть в системном приложении");
        deleteBtn     = createNavAndActButton(ICON_DELETE,       "Удалить фото");

        zoomOverlay = new Pane();
        zoomOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.7);");
        zoomOverlay.setVisible(false);
        zoomOverlay.setMouseTransparent(true);
    }

    /**
     * Создание кнопок навигации, сист. приложения и удаления
     * @param iconPath - путь к иконке
     * @param tooltipText - всплывающий текст
     * @return - готовая кнопка
     */
    private Button createNavAndActButton(String iconPath, String tooltipText) {
        Button btn = new Button();
        ImageView iconView = new ImageView(
                new Image(Objects.requireNonNull(getClass().getResourceAsStream(iconPath))));
        iconView.setFitWidth(24);
        iconView.setFitHeight(24);
        btn.setGraphic(iconView);
        btn.setTooltip(new Tooltip(tooltipText));

        String normal = "-fx-background-color: transparent; -fx-padding: 8; -fx-cursor: hand;";
        String hover  = "-fx-background-color: rgba(255,255,255,0.1); -fx-padding: 8; -fx-cursor: hand;";

        btn.setStyle(normal);
        btn.setOnMouseEntered(_ -> { btn.setStyle(hover);   iconView.setScaleX(1.1); iconView.setScaleY(1.1); });
        btn.setOnMouseExited (_ -> { btn.setStyle(normal);  iconView.setScaleX(1.0); iconView.setScaleY(1.0); });
        return btn;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Layout
    // ═════════════════════════════════════════════════════════════════════════
    private VBox buildLayout() {
        HBox titleBar        = createTitleBar();
        VBox deviceInfoBox   = createDeviceInfoBox();
        VBox centerContainer = createCenterContainer();
        topPanel             = createTopPanel(deviceInfoBox, centerContainer);
        imageContainer       = createImageContainer();

        mainContainer = new VBox();
        mainContainer.setStyle("-fx-background-color:" + (darkTheme ? "#1a1f26" : "#f0f0f0") + ";");
        VBox.setVgrow(imageContainer, Priority.ALWAYS);
        mainContainer.getChildren().addAll(titleBar, topPanel, imageContainer);
        return mainContainer;
    }

    private HBox createTitleBar() {
        javafx.scene.shape.SVGPath photoIcon = new javafx.scene.shape.SVGPath();
        photoIcon.setContent(
                "M14,2H2C0.9,2,0,2.9,0,4v8c0,1.1,0.9,2,2,2h12c1.1,0,2-0.9,2-2V4C16,2.9,15.1,2,14,2z" +
                        "M5.5,6A1.5,1.5,0,1,1,4,7.5A1.5,1.5,0,0,1,5.5,6z" +
                        "M13,12H3l3-4l2,2.5l2.5-3.5z"
        );
        photoIcon.setFill(javafx.scene.paint.Color.web(darkTheme ? "#7090b0" : "#ecf0f1"));

        javafx.scene.layout.StackPane iconWrap = new javafx.scene.layout.StackPane(photoIcon);
        iconWrap.setPrefSize(40, 40);
        iconWrap.setMinSize(40, 40);
        iconWrap.setStyle("-fx-background-color: transparent;");

        Label typeLabel = new Label("Просмотр фотографий");
        typeLabel.setStyle("-fx-font-size:10px;-fx-font-weight:bold;" +
                "-fx-text-fill:" + (darkTheme ? "#5a6a7a" : "rgba(255,255,255,0.6)") + ";");

        String titleText = device != null && device.getName() != null
                ? device.getName()
                : (locationTitle != null ? locationTitle : "Прибор");
        Label titleLabel = new Label(titleText);
        titleLabel.setStyle("-fx-font-size:13px;-fx-font-weight:bold;" +
                "-fx-text-fill:" + (darkTheme ? "#aec6de" : "#ffffff") + ";");

        Label hintLabel = new Label("← → для навигации  •  Esc для закрытия  •  ЛКМ для зума");
        hintLabel.setStyle("-fx-font-size:10px;" +
                "-fx-text-fill:" + (darkTheme ? "#4a5a6a" : "rgba(255,255,255,0.45)") + ";");

        javafx.scene.layout.VBox titleBox = new javafx.scene.layout.VBox(1, typeLabel, titleLabel, hintLabel);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:" +
                (darkTheme ? "#aec6de" : "white") +
                ";-fx-font-size:16px;-fx-cursor:hand;-fx-padding:4 12;-fx-background-radius:10px;");
        closeBtn.setOnMouseEntered(_ -> closeBtn.setStyle(
                "-fx-background-color:#e74c3c;-fx-text-fill:white;" +
                        ";-fx-font-size:16px;-fx-cursor:hand;-fx-padding:4 12;-fx-background-radius:4px;"));
        closeBtn.setOnMouseExited(_ -> closeBtn.setStyle(
                "-fx-background-color:transparent;-fx-text-fill:" +
                        (darkTheme ? "#aec6de" : "white") +
                        ";-fx-font-size:16px;-fx-cursor:hand;-fx-padding:4 12;-fx-background-radius:4px;"));
        closeBtn.setOnAction(_ -> stage.close());

        HBox titleBar = new HBox(14, iconWrap, titleBox, spacer, closeBtn);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(10, 16, 10, 16));
        titleBar.setStyle("-fx-background-color:" + (darkTheme ? "#1a2330" : "#465261") + ";" +
                "-fx-border-color:" + (darkTheme ? "#2d3e50" : "#3a4552") + ";" +
                "-fx-border-width:0 0 1 0;");
        return titleBar;
    }

    /**
     * Создаёт инфо-блок и сохраняет ссылки на Label-ы,
     * чтобы можно было обновлять их при навигации в multiDeviceMode.
     */
    private VBox createDeviceInfoBox() {
        VBox infoBox = new VBox(6);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setMaxWidth(320);
        infoBox.setPrefWidth(320);
        infoBox.setStyle(String.format(
                "-fx-background-color: %s; -fx-background-radius: 8px; -fx-padding: 10 15;",
                darkTheme ? "#1a1f26" : "#f5f5f5"
        ));

        String textColor  = darkTheme ? "#95a5a6" : "#555";
        String labelColor = darkTheme ? "#aec6de" : "#2c3a47";

        // Создаём Label-ы и сохраняем ссылки для дальнейшего обновления
        deviceNameLabel      = createInfoLabel(labelColor, true);
        deviceInventoryLabel = createInfoLabel(textColor,  false);
        deviceLocationLabel  = createInfoLabel(textColor,  false);
        deviceValveLabel     = createInfoLabel(textColor,  false);

        // Заполним начальными данными через общий метод
        fillDeviceInfoLabels(device);

        infoBox.getChildren().addAll(
                deviceNameLabel, deviceInventoryLabel,
                deviceLocationLabel, deviceValveLabel);
        return infoBox;
    }

    /**
     * Заполняет Label-ы инфоблока данными переданного прибора.
     * device == null — отображается информация о локации в целом.
     */
    private void fillDeviceInfoLabels(Device d) {
        if (d != null) {
            deviceNameLabel     .setText("Прибор: "  + getValueOrDefault(d.getName()));
            deviceInventoryLabel.setText("Инв. №: "  + getValueOrDefault(d.getInventoryNumber()));
            deviceLocationLabel .setText("Место: "   + getValueOrDefault(d.getLocation()));
            deviceValveLabel    .setText("Кран №: "  + getValueOrDefault(d.getValveNumber()));
        } else {
            // Начальное состояние в multiDeviceMode (до первой навигации)
            deviceNameLabel     .setText(locationTitle != null ? locationTitle : "Просмотр локации");
            deviceInventoryLabel.setText("Все приборы локации");
            int total = (photoEntries != null) ? photoEntries.size() : 0;
            deviceLocationLabel .setText("Фото: " + total);
            deviceValveLabel    .setText("");
        }
    }

    private Label createInfoLabel(String color, boolean bold) {
        Label label = new Label("");
        String style = String.format("-fx-font-size: %s; -fx-text-fill: %s;",
                bold ? "13px" : "11px", color);
        if (bold) style += "-fx-font-weight: bold;";
        label.setStyle(style);
        label.setWrapText(true);
        return label;
    }

    private String getValueOrDefault(String value) {
        return value != null && !value.trim().isEmpty() ? value : "не указан";
    }

    private VBox createCenterContainer() {
        VBox centerContainer = new VBox();
        centerContainer.setAlignment(Pos.CENTER);
        centerContainer.setSpacing(15);

        HBox mainControlsContainer = new HBox(20);
        mainControlsContainer.setAlignment(Pos.CENTER);
        mainControlsContainer.setStyle(String.format(
                "-fx-background-color: %s; -fx-background-radius: 8px; -fx-padding: 10 15;",
                darkTheme ? "#1a1f26" : "#f5f5f5"
        ));
        mainControlsContainer.setMaxWidth(400);
        mainControlsContainer.setPrefWidth(400);

        HBox navSection = new HBox(10);
        navSection.setAlignment(Pos.CENTER);
        navSection.getChildren().addAll(prevBtn, counterLabel, nextBtn);

        HBox actionSection = new HBox(15);
        actionSection.setAlignment(Pos.CENTER);
        actionSection.getChildren().addAll(openSystemBtn, deleteBtn);

        mainControlsContainer.getChildren().addAll(navSection, actionSection);
        centerContainer.getChildren().add(mainControlsContainer);

        setupNavigationHandlers();
        return centerContainer;
    }

    private HBox createTopPanel(VBox deviceInfoBox, VBox centerContainer) {
        HBox panel = new HBox();
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(15, 20, 10, 20));
        panel.setStyle(String.format("-fx-background-color: %s;",
                darkTheme ? "#252d38" : "#ffffff"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        panel.getChildren().addAll(deviceInfoBox, centerContainer, spacer);
        HBox.setHgrow(deviceInfoBox,   Priority.ALWAYS);
        HBox.setHgrow(centerContainer, Priority.ALWAYS);
        HBox.setHgrow(spacer,          Priority.ALWAYS);
        return panel;
    }

    private StackPane createImageContainer() {
        StackPane container = new StackPane();
        container.setAlignment(Pos.CENTER);
        container.setStyle(String.format("-fx-background-color: %s;",
                darkTheme ? "#1a1f26" : "#f0f0f0"));
        container.getChildren().addAll(zoomOverlay, imageView);
        setupZoomHandlers();
        return container;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Навигация
    // ═════════════════════════════════════════════════════════════════════════
    private void setupNavigationHandlers() {
        prevBtn.setOnAction(_ -> navigateTo(-1));
        nextBtn.setOnAction(_ -> navigateTo(1));

        stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case LEFT,  A           -> navigateTo(-1);
                case RIGHT, D           -> navigateTo(1);
                case DELETE, BACK_SPACE -> deleteCurrentPhoto();
                case ESCAPE             -> stage.close();
            }
        });

        deleteBtn   .setOnAction(_ -> deleteCurrentPhoto());
        openSystemBtn.setOnAction(_ -> openInSystemViewer());
    }

    private void navigateTo(int delta) {
        int size = multiDeviceMode ? photoEntries.size() : photos.size();
        int newIndex = currentIndex + delta;
        if (newIndex >= 0 && newIndex < size) {
            currentIndex = newIndex;
            loadPhotoAtIndex(currentIndex);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Загрузка фото
    // ═════════════════════════════════════════════════════════════════════════
    private void loadPhotoAtIndex(int index) {
        int size = multiDeviceMode ? photoEntries.size() : photos.size();
        if (index < 0 || index >= size) {
            LOGGER.warn("⚠️ Индекс вне диапазона: {}", index);
            return;
        }

        String fullPath;
        String photoName;

        if (multiDeviceMode) {
            PhotoEntry entry = photoEntries.get(index);
            fullPath  = entry.absolutePath();
            photoName = entry.photoName();
            // Обновляем инфоблок данными текущего прибора
            Platform.runLater(() -> fillDeviceInfoLabels(entry.device()));
        } else {
            photoName = photos.get(index);
            fullPath  = photoManager.getFullPhotoPath(device, photoName);
        }

        if (fullPath == null) {
            LOGGER.error("❌ Не удалось получить путь для фото: {}", photoName);
            handleBrokenPhoto(index);
            return;
        }

        File photoFile = new File(fullPath);
        if (!photoFile.exists()) {
            LOGGER.error("❌ Файл не существует: {}", fullPath);
            handleBrokenPhoto(index);
            return;
        }

        try {
            Image image = ImageUtils.loadImageWithCorrectOrientation(photoFile);

            if (image.isError()) {
                LOGGER.error("❌ Ошибка загрузки изображения: {}", image.getException().getMessage());
                CustomAlertDialog.showError("Ошибка", "Не удалось загрузить фото: " + photoName);
                return;
            }

            imageView.setImage(image);
            Platform.runLater(() -> scaleImageToFit(image));

            counterLabel.setText(String.format("Фото %d из %d", index + 1, size));

        } catch (Exception ex) {
            LOGGER.error("❌ Ошибка при загрузке фото: {}", ex.getMessage(), ex);
            imageView.setImage(null);
            CustomAlertDialog.showError("Ошибка", "Не удалось отобразить фото: " + photoName);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Удаление фото
    // ═════════════════════════════════════════════════════════════════════════
    private void deleteCurrentPhoto() {
        int size = multiDeviceMode ? photoEntries.size() : photos.size();
        if (size == 0 || currentIndex < 0 || currentIndex >= size) return;

        if (multiDeviceMode) {
            deleteCurrentPhotoMultiDevice();
        } else {
            deleteCurrentPhotoSingleDevice();
        }
    }

    /** Удаление в режиме одного прибора (оригинальная логика) */
    private void deleteCurrentPhotoSingleDevice() {
        String photoToDelete = photos.get(currentIndex);

        boolean confirm = CustomAlertDialog.showConfirmation(
                "Удаление фото",
                "Удалить текущее фото?\n\nИмя файла: " + photoToDelete +
                        "\nФайл будет удалён с диска."
        );
        if (!confirm) return;

        boolean deleted = photoManager.deletePhoto(device, photoToDelete);
        if (deleted) {
            photos.remove(currentIndex);
            if (onPhotoDeletedCallback != null) {
                onPhotoDeletedCallback.onDeleted(device, photoToDelete);
            }
            afterDelete();
        } else {
            CustomAlertDialog.showError("Ошибка", "Не удалось удалить фото. Проверьте права доступа.");
        }
    }

    /** Удаление в режиме нескольких приборов */
    private void deleteCurrentPhotoMultiDevice() {
        PhotoEntry entry = photoEntries.get(currentIndex);
        Device entryDevice = entry.device();

        boolean confirm = CustomAlertDialog.showConfirmation(
                "Удаление фото",
                "Удалить текущее фото?\n\n" +
                        "Прибор: " + getValueOrDefault(entryDevice.getName()) + "\n" +
                        "Файл: "   + entry.photoName() + "\n" +
                        "Файл будет удалён с диска."
        );
        if (!confirm) return;

        boolean deleted = photoManager.deletePhoto(entryDevice, entry.photoName());
        if (deleted) {
            photoEntries.remove(currentIndex);

            // Уведомляем внешний контроллер (обновить карточку в галерее)
            if (onPhotoDeletedCallback != null) {
                onPhotoDeletedCallback.onDeleted(entryDevice, entry.photoName());
            }

            afterDelete();
        } else {
            CustomAlertDialog.showError("Ошибка", "Не удалось удалить фото. Проверьте права доступа.");
        }
    }

    /** Общая логика после успешного удаления */
    private void afterDelete() {
        int size = multiDeviceMode ? photoEntries.size() : photos.size();
        if (size == 0) {
            CustomAlertDialog.showInfo("Удаление фото", "Все фото удалены");
            stage.close();
        } else {
            if (currentIndex >= size) currentIndex = size - 1;
            loadPhotoAtIndex(currentIndex);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Битое фото
    // ═════════════════════════════════════════════════════════════════════════
    private void handleBrokenPhoto(int index) {
        boolean remove = CustomAlertDialog.showConfirmation(
                "Битое фото",
                "Файл не найден на диске.\n\nУдалить запись из списка?"
        );
        if (remove) {
            if (multiDeviceMode) {
                photoEntries.remove(index);
            } else {
                photos.remove(index);
            }
            int size = multiDeviceMode ? photoEntries.size() : photos.size();
            if (!photos.isEmpty() || (multiDeviceMode && !photoEntries.isEmpty())) {
                currentIndex = Math.max(0, Math.min(index, size - 1));
                loadPhotoAtIndex(currentIndex);
            } else {
                CustomAlertDialog.showInfo("Информация", "Все фото удалены");
                stage.close();
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Открытие в системном приложении
    // ═════════════════════════════════════════════════════════════════════════
    private void openInSystemViewer() {
        int size = multiDeviceMode ? photoEntries.size() : photos.size();
        if (currentIndex < 0 || currentIndex >= size) return;

        String fullPath;
        String photoName;

        if (multiDeviceMode) {
            PhotoEntry entry = photoEntries.get(currentIndex);
            fullPath  = entry.absolutePath();
            photoName = entry.photoName();
        } else {
            photoName = photos.get(currentIndex);
            fullPath  = photoManager.getFullPhotoPath(device, photoName);
        }

        if (fullPath == null) {
            CustomAlertDialog.showWarning("Ошибка", "Не удалось определить путь к файлу");
            return;
        }

        File photoFile = new File(fullPath);
        if (!photoFile.exists()) {
            CustomAlertDialog.showWarning("Ошибка", "Файл не найден на диске");
            return;
        }

        try {
            Desktop.getDesktop().open(photoFile);
            LOGGER.info("✅ Фото открыто в системном приложении: {}", photoName);
        } catch (Exception ex) {
            LOGGER.error("❌ Ошибка открытия файла: {}", ex.getMessage());
            CustomAlertDialog.showError("Ошибка", "Не удалось открыть фото: " + ex.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Зум
    // ═════════════════════════════════════════════════════════════════════════
    private void setupZoomHandlers() {
        imageView.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !isZoomed) {
                enableZoom(); event.consume();
            }
        });
        imageView.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY && isZoomed) {
                disableZoom(); event.consume();
            }
        });
        imageView.setOnMouseDragged(event -> {
            if (isZoomed && event.getButton() == MouseButton.PRIMARY) {
                double deltaX = event.getX() - (imageView.getBoundsInLocal().getWidth()  / 2);
                double deltaY = event.getY() - (imageView.getBoundsInLocal().getHeight() / 2);
                double maxTranslate = 500;
                double newX = Math.max(-maxTranslate, Math.min(maxTranslate, imageView.getTranslateX() + deltaX * 0.1));
                double newY = Math.max(-maxTranslate, Math.min(maxTranslate, imageView.getTranslateY() + deltaY * 0.1));
                imageView.setTranslateX(newX);
                imageView.setTranslateY(newY);
                event.consume();
            }
        });
        imageView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && isZoomed) {
                disableZoom(); event.consume();
            }
        });
    }

    private void enableZoom() {
        initialScale     = imageView.getScaleX();
        initialTranslateX = imageView.getTranslateX();
        initialTranslateY = imageView.getTranslateY();
        imageView.setScaleX(ZOOM_FACTOR);
        imageView.setScaleY(ZOOM_FACTOR);
        zoomOverlay.setVisible(true);
        mainContainer.setStyle("-fx-background-color: #000000;");
        imageContainer.setStyle("-fx-background-color: #000000;");
        if (topPanel != null) topPanel.setStyle("-fx-background-color: #000000;");
        isZoomed = true;
    }

    private void disableZoom() {
        imageView.setScaleX(initialScale);
        imageView.setScaleY(initialScale);
        imageView.setTranslateX(initialTranslateX);
        imageView.setTranslateY(initialTranslateY);
        zoomOverlay.setVisible(false);
        String mainBg = "-fx-background-color:" + (darkTheme ? "#1a1f26" : "#f0f0f0") + ";";
        mainContainer.setStyle(mainBg);
        imageContainer.setStyle(mainBg);
        if (topPanel != null)
            topPanel.setStyle("-fx-background-color:" + (darkTheme ? "#252d38" : "#ffffff") + ";");
        isZoomed = false;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Масштабирование
    // ═════════════════════════════════════════════════════════════════════════
    private void scaleImageToFit(Image image) {
        if (image == null || image.isError()) return;
        imageView.setScaleX(1.0); imageView.setScaleY(1.0);
        imageView.setTranslateX(0); imageView.setTranslateY(0);

        double sceneWidth  = stage.getScene().getWidth();
        double sceneHeight = stage.getScene().getHeight();

        double maxWidth  = sceneWidth  > 0 ? sceneWidth  * 0.85       : 800;
        double maxHeight = sceneHeight > 0 ? (sceneHeight - 150) * 0.85 : 600;

        double scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
        scale = Math.min(scale, 1.0);

        imageView.setFitWidth (image.getWidth()  * scale);
        imageView.setFitHeight(image.getHeight() * scale);
    }
}