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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.File;
import java.net.URL;
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

    private final PhotoManager photoManager;
    private final Device device;
    private final List<String> photos;
    private final Stage ownerStage;

    private Stage stage;
    private ImageView imageView;
    private Label counterLabel;
    private Button prevBtn;
    private Button nextBtn;
    private Button deleteBtn;
    private Button openSystemBtn;

    private int currentIndex = 0;
    private final boolean darkTheme;

    // Состояния для зума
    private final double ZOOM_FACTOR = 3.0;
    private double initialScale = 1.0;
    private double initialTranslateX = 0.0;
    private double initialTranslateY = 0.0;
    private boolean isZoomed = false;
    private Pane zoomOverlay;
    private VBox mainContainer;
    private StackPane imageContainer;
    private HBox topPanel;

    public PhotoViewer(PhotoManager photoManager, Device device, List<String> photos, Stage ownerStage) {
        this.photoManager = photoManager;
        this.device = device;
        this.photos = photos.stream()
                .filter(photoName -> {
                    String fullPath = photoManager.getFullPhotoPath(device, photoName);
                    if (fullPath == null) return false;
                    File file = new File(fullPath);
                    return file.exists() && !file.isDirectory();
                })
                .collect(Collectors.toList());
        this.ownerStage = ownerStage;
        this.darkTheme = StyleUtils.getCurrentTheme().contains("dark");
    }

    /**
     * Показать окно просмотра фото
     */
    public void show() {
        if (photos.isEmpty()) {
            CustomAlertDialog.showInfo("Просмотр фото", "Нет доступных фотографий");
            return;
        }

        createAndShowStage();
    }

    /**
     * Создание и настройка окна
     */
    private void createAndShowStage() {
        stage = new Stage();
        stage.setTitle("Просмотр фото прибора: " + device.getName());
        stage.initOwner(ownerStage);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setMaximized(true);

        // Создаем UI компоненты
        createUIComponents();

        // Собираем layout
        VBox root = buildLayout();

        // Настраиваем сцену
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        applyStylesheet(scene);

        stage.setScene(scene);
        stage.show();

        // Загружаем первое фото
        loadPhotoAtIndex(currentIndex);
    }

    /**
     * Создание UI компонентов
     */
    private void createUIComponents() {
        // ImageView для фото
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);

        // Счетчик фото
        counterLabel = new Label();
        counterLabel.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 14px; -fx-font-weight: bold;",
                darkTheme ? "#ecf0f1" : "#2c3e50"
        ));

        // Кнопки навигации
        prevBtn = createNavButton("← Назад");
        nextBtn = createNavButton("Вперёд →");

        // Кнопки действий
        openSystemBtn = createActionButton("📷 Открыть в системном приложении", darkTheme ? "#4A5568" : "#465261");
        deleteBtn = createActionButton("🗑 Удалить фото", darkTheme ? "#c0392b" : "#e74c3c");

        // Оверлей для затемнения при зуме
        zoomOverlay = new Pane();
        zoomOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.7);");
        zoomOverlay.setVisible(false);
        zoomOverlay.setMouseTransparent(true);
    }

    /**
     * Создание кнопки навигации
     */
    private Button createNavButton(String text) {
        Button btn = new Button(text);
        String normalStyle = String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 13px; " +
                        "-fx-padding: 8 16; -fx-background-radius: 6px; -fx-cursor: hand;",
                darkTheme ? "#4A5568" : "#e0e0e0",
                darkTheme ? "#ecf0f1" : "#2c3e50"
        );
        String hoverStyle = String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 13px; " +
                        "-fx-padding: 8 16; -fx-background-radius: 6px; -fx-cursor: hand;",
                darkTheme ? "#6A7D90" : "#d0d0d0",
                darkTheme ? "#ffffff" : "#1a252f"
        );

        btn.setStyle(normalStyle);
        btn.setOnMouseEntered(_ -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(_ -> btn.setStyle(normalStyle));

        return btn;
    }

    /**
     * Создание кнопки действия
     */
    private Button createActionButton(String text, String bgColor) {
        Button btn = new Button(text);
        btn.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-size: 12px; " +
                        "-fx-padding: 8 20; -fx-background-radius: 6px; -fx-cursor: hand;",
                bgColor
        ));

        btn.setOnMouseEntered(_ -> btn.setStyle(btn.getStyle() + "-fx-opacity: 0.85;"));
        btn.setOnMouseExited(_ -> btn.setStyle(btn.getStyle().replace("-fx-opacity: 0.85;", "")));

        return btn;
    }

    /**
     * Сборка основного layout
     */
    private VBox buildLayout() {
        // Кастомный titlebar
        HBox titleBar = createTitleBar();

        // Информация о приборе
        VBox deviceInfoBox = createDeviceInfoBox();

        // Центральный контейнер с навигацией
        VBox centerContainer = createCenterContainer();

        // Верхняя панель
        topPanel = createTopPanel(deviceInfoBox, centerContainer);

        // Контейнер для фото
        imageContainer = createImageContainer();

        // Основной контейнер
        mainContainer = new VBox();
        mainContainer.setStyle(String.format(
                "-fx-background-color: %s; -fx-background-radius: 10px; -fx-border-radius: 10px;" +
                        "-fx-border-color: %s; -fx-border-width: 1px;" +
                        "-fx-effect: dropshadow(gaussian, %s, 20, 0, 0, 5);",
                darkTheme ? "#252d38" : "#ffffff",
                darkTheme ? "#2d3e50" : "#d0d4d8",
                darkTheme ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.22)"
        ));
        VBox.setVgrow(imageContainer, Priority.ALWAYS);

        mainContainer.getChildren().addAll(titleBar, topPanel, imageContainer);

        return mainContainer;
    }

    /**
     * Создание кастомного titlebar
     */
    private HBox createTitleBar() {
        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(8, 12, 8, 16));
        titleBar.setStyle(String.format(
                "-fx-background-color: %s; -fx-background-radius: 10px 10px 0 0;",
                darkTheme ? "#1a1f26" : "#465261"
        ));

        Label titleLabel = new Label(device.getName() != null ? device.getName() : "Просмотр фото");
        titleLabel.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 14px; -fx-font-weight: bold;",
                darkTheme ? "#ecf0f1" : "#ffffff"
        ));
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(String.format(
                "-fx-background-color: transparent; -fx-text-fill: %s; -fx-font-size: 16px; " +
                        "-fx-cursor: hand; -fx-padding: 4 12;",
                darkTheme ? "#ecf0f1" : "#ffffff"
        ));
        closeBtn.setOnMouseEntered(_ -> closeBtn.setStyle(
                "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 16px; " +
                        "-fx-cursor: hand; -fx-padding: 4 12; -fx-background-radius: 4px;"
        ));
        closeBtn.setOnMouseExited(_ -> closeBtn.setStyle(String.format(
                "-fx-background-color: transparent; -fx-text-fill: %s; -fx-font-size: 16px; " +
                        "-fx-cursor: hand; -fx-padding: 4 12;",
                darkTheme ? "#ecf0f1" : "#ffffff"
        )));
        closeBtn.setOnAction(_ -> stage.close());

        titleBar.getChildren().addAll(titleLabel, closeBtn);

        // Добавляем возможность перетаскивания окна
        setupWindowDrag(titleBar);

        return titleBar;
    }

    /**
     * Настройка перетаскивания окна
     */
    private void setupWindowDrag(HBox titleBar) {
        final double[] dragDelta = new double[2];
        titleBar.setOnMousePressed(e -> {
            dragDelta[0] = stage.getX() - e.getScreenX();
            dragDelta[1] = stage.getY() - e.getScreenY();
        });
        titleBar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() + dragDelta[0]);
            stage.setY(e.getScreenY() + dragDelta[1]);
        });
    }

    /**
     * Создание информационного блока о приборе
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

        String textColor = darkTheme ? "#95a5a6" : "#555";
        String labelColor = darkTheme ? "#aec6de" : "#2c3a47";

        Label nameLabel = createInfoLabel("Прибор: " + getValueOrDefault(device.getName()), labelColor, true);
        Label inventoryLabel = createInfoLabel("Инв. №: " + getValueOrDefault(device.getInventoryNumber()), textColor, false);
        Label locationLabel = createInfoLabel("Место: " + getValueOrDefault(device.getLocation()), textColor, false);
        Label valveLabel = createInfoLabel("Кран №: " + getValueOrDefault(device.getValveNumber()), textColor, false);

        infoBox.getChildren().addAll(nameLabel, inventoryLabel, locationLabel, valveLabel);
        return infoBox;
    }

    /**
     * Создание информационной метки
     */
    private Label createInfoLabel(String text, String color, boolean bold) {
        Label label = new Label(text);
        String style = String.format(
                "-fx-font-size: %s; -fx-text-fill: %s;",
                bold ? "13px" : "11px",
                color
        );
        if (bold) {
            style += "-fx-font-weight: bold;";
        }
        label.setStyle(style);
        label.setWrapText(true);
        return label;
    }

    /**
     * Получение значения или строки "не указан"
     */
    private String getValueOrDefault(String value) {
        return value != null && !value.trim().isEmpty() ? value : "не указан";
    }

    /**
     * Создание центрального контейнера с навигацией
     */
    private VBox createCenterContainer() {
        VBox centerContainer = new VBox();
        centerContainer.setAlignment(Pos.CENTER);
        centerContainer.setSpacing(15);

        // Контейнер навигации
        HBox navContainer = new HBox(10);
        navContainer.setAlignment(Pos.CENTER);
        navContainer.getChildren().addAll(prevBtn, counterLabel, nextBtn);

        // Контейнер для кнопок действий
        HBox actionContainer = new HBox(15);
        actionContainer.setAlignment(Pos.CENTER);
        actionContainer.getChildren().addAll(openSystemBtn, deleteBtn);

        centerContainer.getChildren().addAll(navContainer, actionContainer);

        // Настройка обработчиков навигации
        setupNavigationHandlers();

        return centerContainer;
    }

    /**
     * Настройка обработчиков навигации
     */
    private void setupNavigationHandlers() {
        prevBtn.setOnAction(_ -> navigateTo(-1));
        nextBtn.setOnAction(_ -> navigateTo(1));

        // Клавиатурная навигация
        stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case LEFT, A -> navigateTo(-1);
                case RIGHT, D -> navigateTo(1);
                case DELETE, BACK_SPACE -> deleteCurrentPhoto();
                case ESCAPE -> stage.close();
            }
        });

        // Обработчик удаления
        deleteBtn.setOnAction(_ -> deleteCurrentPhoto());

        // Обработчик открытия в системном приложении
        openSystemBtn.setOnAction(_ -> openInSystemViewer());
    }

    /**
     * Навигация по фото
     */
    private void navigateTo(int delta) {
        int newIndex = currentIndex + delta;
        if (newIndex >= 0 && newIndex < photos.size()) {
            currentIndex = newIndex;
            loadPhotoAtIndex(currentIndex);
        }
    }

    /**
     * Удаление текущего фото
     */
    private void deleteCurrentPhoto() {
        if (photos.isEmpty() || currentIndex < 0 || currentIndex >= photos.size()) {
            return;
        }

        String photoToDelete = photos.get(currentIndex);

        boolean confirm = CustomAlertDialog.showConfirmation(
                "Удаление фото",
                "Удалить текущее фото?\n\nИмя файла: " + photoToDelete + "\nФайл будет удалён с диска."
        );

        if (!confirm) return;

        boolean deleted = photoManager.deletePhoto(device, photoToDelete);
        if (deleted) {
            photos.remove(currentIndex);

            if (photos.isEmpty()) {
                CustomAlertDialog.showInfo("Удаление фото", "Все фото удалены");
                stage.close();
            } else {
                if (currentIndex >= photos.size()) {
                    currentIndex = photos.size() - 1;
                }
                loadPhotoAtIndex(currentIndex);
            }
        } else {
            CustomAlertDialog.showError("Ошибка", "Не удалось удалить фото. Проверьте права доступа.");
        }
    }

    /**
     * Открытие фото в системном приложении
     */
    private void openInSystemViewer() {
        if (currentIndex < 0 || currentIndex >= photos.size()) return;

        String photoName = photos.get(currentIndex);
        String fullPath = photoManager.getFullPhotoPath(device, photoName);

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

    /**
     * Создание верхней панели
     */
    private HBox createTopPanel(VBox deviceInfoBox, VBox centerContainer) {
        HBox panel = new HBox();
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(15, 20, 10, 20));
        panel.setStyle(String.format(
                "-fx-background-color: %s;",
                darkTheme ? "#252d38" : "#ffffff"
        ));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        panel.getChildren().addAll(deviceInfoBox, centerContainer, spacer);

        HBox.setHgrow(deviceInfoBox, Priority.ALWAYS);
        HBox.setHgrow(centerContainer, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return panel;
    }

    /**
     * Создание контейнера для изображения
     */
    private StackPane createImageContainer() {
        StackPane container = new StackPane();
        container.setAlignment(Pos.CENTER);
        container.setStyle(String.format(
                "-fx-background-color: %s;",
                darkTheme ? "#1a1f26" : "#f0f0f0"
        ));

        container.getChildren().addAll(zoomOverlay, imageView);

        setupZoomHandlers();

        return container;
    }

    /**
     * Настройка зума и перемещения
     */
    private void setupZoomHandlers() {
        imageView.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !isZoomed) {
                enableZoom();
                event.consume();
            }
        });

        imageView.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY && isZoomed) {
                disableZoom();
                event.consume();
            }
        });

        imageView.setOnMouseDragged(event -> {
            if (isZoomed && event.getButton() == MouseButton.PRIMARY) {
                double deltaX = event.getX() - (imageView.getBoundsInLocal().getWidth() / 2);
                double deltaY = event.getY() - (imageView.getBoundsInLocal().getHeight() / 2);

                double maxTranslate = 500;
                double newTranslateX = imageView.getTranslateX() + deltaX * 0.1;
                double newTranslateY = imageView.getTranslateY() + deltaY * 0.1;

                newTranslateX = Math.max(-maxTranslate, Math.min(maxTranslate, newTranslateX));
                newTranslateY = Math.max(-maxTranslate, Math.min(maxTranslate, newTranslateY));

                imageView.setTranslateX(newTranslateX);
                imageView.setTranslateY(newTranslateY);
                event.consume();
            }
        });

        imageView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && isZoomed) {
                disableZoom();
                event.consume();
            }
        });
    }

    /**
     * Включение режима зума
     */
    private void enableZoom() {
        initialScale = imageView.getScaleX();
        initialTranslateX = imageView.getTranslateX();
        initialTranslateY = imageView.getTranslateY();

        imageView.setScaleX(ZOOM_FACTOR);
        imageView.setScaleY(ZOOM_FACTOR);

        zoomOverlay.setVisible(true);
        mainContainer.setStyle("-fx-background-color: #000000;");
        imageContainer.setStyle("-fx-background-color: #000000;");
        if (topPanel != null) {
            topPanel.setStyle("-fx-background-color: #000000;");
        }

        isZoomed = true;
    }

    /**
     * Выключение режима зума
     */
    private void disableZoom() {
        imageView.setScaleX(initialScale);
        imageView.setScaleY(initialScale);
        imageView.setTranslateX(initialTranslateX);
        imageView.setTranslateY(initialTranslateY);

        zoomOverlay.setVisible(false);

        String mainBg = String.format("-fx-background-color: %s;", darkTheme ? "#252d38" : "#ffffff");
        String containerBg = String.format("-fx-background-color: %s;", darkTheme ? "#1a1f26" : "#f0f0f0");
        String topBg = String.format("-fx-background-color: %s;", darkTheme ? "#252d38" : "#ffffff");

        mainContainer.setStyle(mainBg);
        imageContainer.setStyle(containerBg);
        if (topPanel != null) {
            topPanel.setStyle(topBg);
        }

        isZoomed = false;
    }

    /**
     * Загрузка фото по индексу
     */
    private void loadPhotoAtIndex(int index) {
        if (index < 0 || index >= photos.size()) {
            LOGGER.warn("⚠️ Индекс вне диапазона: {}", index);
            return;
        }

        String photoName = photos.get(index);
        String fullPath = photoManager.getFullPhotoPath(device, photoName);

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

            counterLabel.setText(String.format("Фото %d из %d", index + 1, photos.size()));
            deleteBtn.setText(String.format("🗑 Удалить (%d/%d)", index + 1, photos.size()));

        } catch (Exception ex) {
            LOGGER.error("❌ Ошибка при загрузке фото: {}", ex.getMessage(), ex);
            imageView.setImage(null);
            CustomAlertDialog.showError("Ошибка", "Не удалось отобразить фото: " + photoName);
        }
    }

    /**
     * Обработка битого фото
     */
    private void handleBrokenPhoto(int index) {
        boolean remove = CustomAlertDialog.showConfirmation(
                "Битое фото",
                "Файл не найден на диске.\n\nУдалить запись из списка?"
        );

        if (remove) {
            photos.remove(index);
            if (!photos.isEmpty()) {
                int newIndex = Math.max(0, Math.min(index, photos.size() - 1));
                currentIndex = newIndex;
                loadPhotoAtIndex(currentIndex);
            } else {
                CustomAlertDialog.showInfo("Информация", "Все фото удалены");
                stage.close();
            }
        }
    }

    /**
     * Масштабирование фото под размер окна
     */
    private void scaleImageToFit(Image image) {
        if (image == null || image.isError()) return;

        imageView.setScaleX(1.0);
        imageView.setScaleY(1.0);
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);

        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();

        double sceneWidth = stage.getScene().getWidth();
        double sceneHeight = stage.getScene().getHeight();

        double maxWidth = sceneWidth > 0 ? sceneWidth * 0.85 : 800;
        double maxHeight = sceneHeight > 0 ? (sceneHeight - 150) * 0.85 : 600;

        double widthRatio = maxWidth / imageWidth;
        double heightRatio = maxHeight / imageHeight;
        double scale = Math.min(widthRatio, heightRatio);
        scale = Math.min(scale, 1.0);

        imageView.setFitWidth(imageWidth * scale);
        imageView.setFitHeight(imageHeight * scale);
    }

    /**
     * Применение CSS стилей к сцене
     */
    private void applyStylesheet(Scene scene) {
        try {
            String[] possiblePaths = {
                    "/styles/light-theme.css",
                    "/styles/dark-theme.css",
                    "/css/light-theme.css",
                    "/com/kipia/management/kipia_management/styles/light-theme.css"
            };

            for (String cssPath : possiblePaths) {
                URL cssUrl = getClass().getResource(cssPath);
                if (cssUrl != null) {
                    scene.getStylesheets().add(cssUrl.toExternalForm());
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Не удалось загрузить CSS: {}", e.getMessage());
        }
    }
}
