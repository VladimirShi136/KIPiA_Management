package com.kipia.management.kipia_management.managers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.utils.CustomAlert;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Утилитный класс для управления фотографиями приборов
 */
public class PhotoManager {
    private static final Logger LOGGER = LogManager.getLogger(PhotoManager.class);
    private static final String LAST_PHOTO_DIR_KEY = "last_photo_directory";

    private File lastPhotoDirectory;

    public PhotoManager() {
        restoreLastDirectoryFromPreferences();
    }

    /**
     * Добавление нескольких фотографий к прибору
     */
    public void addPhotosToDevice(Device device, Stage ownerStage) {
        FileChooser chooser = createFileChooser();
        List<File> files = chooser.showOpenMultipleDialog(ownerStage);

        if (files != null && !files.isEmpty()) {
            saveLastDirectory(files.getFirst());

            int addedCount = 0;
            List<String> existingPhotos = device.getPhotos();

            for (File file : files) {
                String filePath = file.getAbsolutePath();

                if (existingPhotos == null || !existingPhotos.contains(filePath)) {
                    device.addPhoto(filePath);
                    addedCount++;
                }
            }

            if (addedCount > 0) {
                showPhotoAddResult(addedCount, files.size());
            } else {
                CustomAlert.showInfo("Добавление фото", "Все выбранные фото уже были добавлены ранее");
            }
        }
    }

    /**
     * Просмотр фотографий с улучшенным интерфейсом
     */
    public void viewDevicePhotos(Device device, Stage ownerStage) {
        List<String> photos = device.getPhotos();
        if (photos == null || photos.isEmpty()) {
            CustomAlert.showInfo("Просмотр фото", "Фотографии не добавлены");
            return;
        }

        Stage viewStage = createPhotoViewStage(device, photos, ownerStage);
        viewStage.show();
    }

    /**
     * Открытие фото в системном приложении
     */
    public void openInSystemViewer(String photoPath) {
        try {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                java.awt.Desktop.getDesktop().open(photoFile);
            } else {
                CustomAlert.showWarning("Просмотр фото", "Файл не найден: " + photoPath);
            }
        } catch (Exception ex) {
            LOGGER.error("Ошибка при открытии фото в системном приложении: {}", ex.getMessage());
            CustomAlert.showError("Ошибка", "Не удалось открыть фото в системном приложении");
        }
    }

    // ========== PRIVATE METHODS ==========

    private FileChooser createFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите фотографии прибора");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Изображения",
                        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));

        restoreLastDirectoryToChooser(chooser);
        return chooser;
    }

    private void saveLastDirectory(File selectedFile) {
        if (selectedFile != null) {
            lastPhotoDirectory = selectedFile.getParentFile();
            try {
                Preferences.userRoot().put(LAST_PHOTO_DIR_KEY, lastPhotoDirectory.getAbsolutePath());
            } catch (SecurityException e) {
                LOGGER.warn("Не удалось сохранить настройки директории: {}", e.getMessage());
            }
        }
    }

    private void restoreLastDirectoryFromPreferences() {
        try {
            String lastDir = Preferences.userRoot().get(LAST_PHOTO_DIR_KEY, null);
            if (lastDir != null) {
                File dir = new File(lastDir);
                if (dir.exists()) {
                    lastPhotoDirectory = dir;
                }
            }
        } catch (SecurityException e) {
            LOGGER.warn("Не удалось восстановить настройки директории: {}", e.getMessage());
        }
    }

    private void restoreLastDirectoryToChooser(FileChooser chooser) {
        if (lastPhotoDirectory != null && lastPhotoDirectory.exists()) {
            chooser.setInitialDirectory(lastPhotoDirectory);
        } else if (chooser.getInitialDirectory() == null) {
            chooser.setInitialDirectory(new File(System.getProperty("user.home")));
        }
    }

    private void showPhotoAddResult(int addedCount, int totalCount) {
        if (addedCount == totalCount) {
            CustomAlert.showInfo("Добавление фото",
                    String.format("Успешно добавлено %d фотографий", addedCount));
        } else {
            CustomAlert.showInfo("Добавление фото",
                    String.format("Добавлено %d из %d фотографий\n\n%d фото уже были добавлены ранее",
                            addedCount, totalCount, totalCount - addedCount));
        }
    }

    private Stage createPhotoViewStage(Device device, List<String> photos, Stage ownerStage) {
        Stage stage = new Stage();
        stage.setTitle("Просмотр фото прибора: " + device.getName());
        stage.setMaximized(true);

        // Добавляем иконку окна
        try {
            Image appIcon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/iconApp.png")));
            stage.getIcons().add(appIcon);
        } catch (Exception e) {
            LOGGER.warn("Не удалось загрузить иконку для окна приложения: {}", e.getMessage());
        }

        // Инициализация компонентов
        ImageView imageView = createImageView();
        Label counterLabel = new Label();
        counterLabel.getStyleClass().add("photo-viewer-counter");

        Button openSystemBtn = createSystemViewerButton();
        Button prevBtn = createNavigationButton("← Назад");
        Button nextBtn = createNavigationButton("Вперёд →");

        // Информация о приборе
        VBox deviceInfoBox = createDeviceInfoBox(device);

        // ⭐⭐ ОБНОВЛЕННАЯ СТРУКТУРА: вся навигация и кнопка в одном центральном контейнере ⭐⭐
        VBox centerContainer = createCenterContainer(prevBtn, counterLabel, nextBtn, openSystemBtn);

        // Контейнер для фото
        StackPane photoContainer = createSimplePhotoContainer(imageView);

        // Основной layout
        VBox mainBox = new VBox();
        mainBox.getStyleClass().add("photo-viewer-container");
        VBox.setVgrow(photoContainer, Priority.ALWAYS);

        // ⭐⭐ НОВАЯ СТРУКТУРА: верхняя панель с информацией и центральным блоком ⭐⭐
        HBox topPanel = createTopPanel(deviceInfoBox, centerContainer);
        mainBox.getChildren().addAll(topPanel, photoContainer);

        // Создаем сцену
        javafx.scene.Scene scene = new javafx.scene.Scene(mainBox);
        applyStylesToScene(scene);

        // Управление состоянием
        int[] currentIndex = {0};

        // Настройка навигации
        setupNavigation(photos, imageView, counterLabel, prevBtn, nextBtn, currentIndex, scene);

        // Настройка зума по зажатию ЛКМ
        setupDragZoom(imageView);

        stage.setScene(scene);

        // Инициализация первого фото
        showPhotoAtIndex(photos, 0, imageView, counterLabel, openSystemBtn, scene);

        if (ownerStage != null) {
            stage.initOwner(ownerStage);
        }

        return stage;
    }

    /**
     * Создание центрального контейнера с навигацией и кнопкой системного приложения
     */
    private VBox createCenterContainer(Button prevBtn, Label counterLabel,
                                       Button nextBtn, Button openSystemBtn) {
        VBox centerContainer = new VBox();
        centerContainer.getStyleClass().add("photo-viewer-center-container");
        centerContainer.setAlignment(Pos.CENTER);
        centerContainer.setSpacing(15);

        // Контейнер навигации (кнопки назад/вперед + счетчик)
        HBox navContainer = new HBox(10);
        navContainer.getStyleClass().add("photo-viewer-nav-container");
        navContainer.setAlignment(Pos.CENTER);
        navContainer.getChildren().addAll(prevBtn, counterLabel, nextBtn);

        // Добавляем навигацию и кнопку системного приложения в вертикальный контейнер
        centerContainer.getChildren().addAll(navContainer, openSystemBtn);

        return centerContainer;
    }

    /**
     * Создание верхней панели с информацией о приборе и центральным блоком
     */
    private HBox createTopPanel(VBox deviceInfoBox, VBox centerContainer) {
        HBox topPanel = new HBox();
        topPanel.getStyleClass().add("photo-viewer-top-panel");
        topPanel.setAlignment(Pos.CENTER);
        topPanel.setPadding(new javafx.geometry.Insets(15));

        // Левая часть - информация о приборе
        VBox leftBox = new VBox();
        leftBox.setAlignment(Pos.CENTER_LEFT);
        leftBox.getStyleClass().add("photo-viewer-device-info");
        leftBox.getChildren().add(deviceInfoBox);

        // Центральная часть - навигация и кнопка системного приложения
        centerContainer.setAlignment(Pos.CENTER);

        // Правая часть - пустая для баланса
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topPanel.getChildren().addAll(leftBox, centerContainer, spacer);

        // Распределение пространства
        HBox.setHgrow(leftBox, Priority.ALWAYS);
        HBox.setHgrow(centerContainer, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return topPanel;
    }

    /**
     * Улучшенный контейнер для фото с зумом и перемещением
     */
    private StackPane createSimplePhotoContainer(ImageView imageView) {
        StackPane stackPane = new StackPane();
        stackPane.setAlignment(Pos.CENTER);
        stackPane.getStyleClass().add("photo-viewer-image-pane");

        // Создаем оверлей для затемнения
        Pane zoomOverlay = new Pane();
        zoomOverlay.getStyleClass().add("zoom-overlay");
        zoomOverlay.setVisible(false); // Изначально скрыт
        zoomOverlay.setMouseTransparent(true); // Пропускает события мыши

        stackPane.getChildren().addAll(zoomOverlay, imageView);
        return stackPane;
    }

    /**
     * Создание информационного блока с минимальной шириной
     */
    private VBox createDeviceInfoBox(Device device) {
        VBox infoBox = new VBox(4); // ⭐⭐ УМЕНЬШИЛИ МЕЖСТРОЧНЫЙ ИНТЕРВАЛ ⭐⭐
        infoBox.getStyleClass().add("photo-viewer-device-info");
        infoBox.setAlignment(Pos.CENTER_LEFT);

        infoBox.setMaxWidth(320);
        infoBox.setPrefWidth(320);

        // Создаем элементы с БЕЛЫМ текстом и МЕЛКИМ ШРИФТОМ
        Label inventoryLabel = new Label("Инв. №: " + (device.getInventoryNumber() != null ? device.getInventoryNumber() : "не указан"));
        Label locationLabel = new Label("Место: " + (device.getLocation() != null ? device.getLocation() : "не указано"));
        Label valveLabel = new Label("Кран №: " + (device.getValveNumber() != null ? device.getValveNumber() : "не указан"));

        // Применяем стиль с белым текстом и мелким шрифтом ко всем лейблам
        inventoryLabel.getStyleClass().add("photo-viewer-device-text");
        locationLabel.getStyleClass().add("photo-viewer-device-text");
        valveLabel.getStyleClass().add("photo-viewer-device-text");

        // ⭐⭐ ВКЛЮЧАЕМ ПЕРЕНАС ТЕКСТА ДЛЯ КАЖДОГО ЛЕЙБЛА ⭐⭐
        inventoryLabel.setWrapText(true);
        locationLabel.setWrapText(true);
        valveLabel.setWrapText(true);

        // ⭐⭐ ЯВНО УСТАНАВЛИВАЕМ МЕЛКИЙ ШРИФТ ⭐⭐
        inventoryLabel.setStyle("-fx-font-size: 11px;");
        locationLabel.setStyle("-fx-font-size: 11px;");
        valveLabel.setStyle("-fx-font-size: 11px;");

        infoBox.getChildren().addAll(inventoryLabel, locationLabel, valveLabel);
        return infoBox;
    }

    /**
     * Настройка зума с полностью черным интерфейсом
     */
    private void setupDragZoom(ImageView imageView) {
        final double ZOOM_FACTOR = 3.0;
        final double[] initialScale = {1.0};
        final double[] initialTranslateX = {0.0};
        final double[] initialTranslateY = {0.0};
        final boolean[] isZoomed = {false};

        // Получаем все контейнеры
        StackPane imageContainer = (StackPane) imageView.getParent();
        Pane zoomOverlay = (Pane) imageContainer.getChildren().getFirst();
        VBox mainContainer = (VBox) imageContainer.getParent();

        // Находим верхнюю панель
        HBox topPanel = null;
        for (javafx.scene.Node node : mainContainer.getChildren()) {
            if (node instanceof HBox && node.getStyleClass().contains("photo-viewer-top-panel")) {
                topPanel = (HBox) node;
                break;
            }
        }

        final HBox finalTopPanel = topPanel;

        imageView.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                if (!isZoomed[0]) {
                    // Включаем зум
                    initialScale[0] = imageView.getScaleX();
                    initialTranslateX[0] = imageView.getTranslateX();
                    initialTranslateY[0] = imageView.getTranslateY();

                    // Увеличиваем изображение
                    imageView.setScaleX(ZOOM_FACTOR);
                    imageView.setScaleY(ZOOM_FACTOR);

                    // ⭐⭐ ДЕЛАЕМ ВСЕ ЧЕРНЫМ ⭐⭐
                    zoomOverlay.setVisible(true);

                    // Основной контейнер
                    mainContainer.getStyleClass().add("zoomed");
                    mainContainer.setStyle("-fx-background-color: #000000;");

                    // Контейнер изображения
                    imageContainer.getStyleClass().add("zoomed");
                    imageContainer.setStyle("-fx-background-color: #000000;");

                    // ImageView
                    imageView.getStyleClass().add("zoomed");

                    // ⭐⭐ ВЕРХНЯЯ ПАНЕЛЬ - ПОЛНОСТЬЮ ЧЕРНАЯ ⭐⭐
                    if (finalTopPanel != null) {
                        finalTopPanel.getStyleClass().add("zoomed");
                        finalTopPanel.setStyle("-fx-background-color: #000000;");
                    }

                    isZoomed[0] = true;

                    LOGGER.debug("Зум включен, полностью черный интерфейс");
                }
                event.consume();
            }
        });

        imageView.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY && isZoomed[0]) {
                // Выключаем зум
                imageView.setScaleX(initialScale[0]);
                imageView.setScaleY(initialScale[0]);
                imageView.setTranslateX(initialTranslateX[0]);
                imageView.setTranslateY(initialTranslateY[0]);

                // ⭐⭐ ВОССТАНАВЛИВАЕМ СТАНДАРТНЫЕ ЦВЕТА ⭐⭐
                zoomOverlay.setVisible(false);

                // Основной контейнер
                mainContainer.getStyleClass().remove("zoomed");
                mainContainer.setStyle(""); // Сбрасываем inline стиль

                // Контейнер изображения
                imageContainer.getStyleClass().remove("zoomed");
                imageContainer.setStyle(""); // Сбрасываем inline стиль

                // ImageView
                imageView.getStyleClass().remove("zoomed");

                // ⭐⭐ ВЕРХНЯЯ ПАНЕЛЬ - ВОССТАНАВЛИВАЕМ ⭐⭐
                if (finalTopPanel != null) {
                    finalTopPanel.getStyleClass().remove("zoomed");
                    finalTopPanel.setStyle(""); // Сбрасываем inline стиль
                }

                isZoomed[0] = false;
                event.consume();
            }
        });

        // Перемещение изображения при зуме
        imageView.setOnMouseDragged(event -> {
            if (isZoomed[0] && event.getButton() == MouseButton.PRIMARY) {
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

        // Сброс зума при двойном клике
        imageView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && isZoomed[0]) {
                // Сбрасываем зум при двойном клике
                imageView.setScaleX(initialScale[0]);
                imageView.setScaleY(initialScale[0]);
                imageView.setTranslateX(initialTranslateX[0]);
                imageView.setTranslateY(initialTranslateY[0]);

                // ⭐⭐ ВОССТАНАВЛИВАЕМ СТАНДАРТНЫЕ ЦВЕТА ⭐⭐
                zoomOverlay.setVisible(false);

                mainContainer.getStyleClass().remove("zoomed");
                mainContainer.setStyle("");

                imageContainer.getStyleClass().remove("zoomed");
                imageContainer.setStyle("");

                imageView.getStyleClass().remove("zoomed");

                if (finalTopPanel != null) {
                    finalTopPanel.getStyleClass().remove("zoomed");
                    finalTopPanel.setStyle("");
                }

                isZoomed[0] = false;
                event.consume();
            }
        });
    }

    /**
     * Настройка навигации
     */
    private void setupNavigation(List<String> photos, ImageView imageView, Label counterLabel,
                                 Button prevBtn, Button nextBtn, int[] currentIndex, javafx.scene.Scene scene) {
        prevBtn.setOnAction(_ -> {
            if (currentIndex[0] > 0) {
                currentIndex[0]--;
                showPhotoAtIndex(photos, currentIndex[0], imageView, counterLabel, null, scene);
            }
        });

        nextBtn.setOnAction(_ -> {
            if (currentIndex[0] < photos.size() - 1) {
                currentIndex[0]++;
                showPhotoAtIndex(photos, currentIndex[0], imageView, counterLabel, null, scene);
            }
        });

        // Управление с клавиатуры
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case LEFT, A -> {
                    if (currentIndex[0] > 0) {
                        currentIndex[0]--;
                        showPhotoAtIndex(photos, currentIndex[0], imageView, counterLabel, null, scene);
                    }
                }
                case RIGHT, D -> {
                    if (currentIndex[0] < photos.size() - 1) {
                        currentIndex[0]++;
                        showPhotoAtIndex(photos, currentIndex[0], imageView, counterLabel, null, scene);
                    }
                }
                case ESCAPE -> {
                    Stage stage = (Stage) scene.getWindow();
                    stage.close();
                }
            }
        });
    }

    /**
     * Применяет CSS стили к сцене
     */
    private void applyStylesToScene(javafx.scene.Scene scene) {
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
                    return;
                }
            }

            LOGGER.warn("Не удалось найти CSS файл по стандартным путям");

        } catch (Exception e) {
            LOGGER.error("Ошибка загрузки CSS: {}", e.getMessage());
        }
    }

    private ImageView createImageView() {
        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);
        // ⭐⭐ ДОБАВЛЯЕМ СТИЛЬ ДЛЯ ЦЕНТРИРОВАНИЯ ⭐⭐
        imageView.getStyleClass().add("photo-viewer-image");
        return imageView;
    }

    /**
     * Создание кнопок навигации с зеленым цветом
     */
    private Button createNavigationButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().addAll("button", "photo-viewer-nav-button");
        button.setPrefSize(100, 35);
        return button;
    }

    /**
     * Создание кнопки системного приложении
     */
    private Button createSystemViewerButton() {
        Button button = new Button("📷 Открыть в системном приложении");
        button.getStyleClass().addAll("button", "photo-viewer-system-button");
        button.setPrefWidth(280);
        return button;
    }

    private void showPhotoAtIndex(List<String> photos, int index, ImageView imageView,
                                  Label counterLabel, Button openSystemBtn, javafx.scene.Scene scene) {
        if (index >= 0 && index < photos.size()) {
            String photoPath = photos.get(index);

            try {
                Image image = new Image("file:" + photoPath, false);

                // Ждем загрузки изображения перед масштабированием
                if (image.isBackgroundLoading()) {
                    image.progressProperty().addListener((_, _, newVal) -> {
                        if (newVal.doubleValue() == 1.0) {
                            Platform.runLater(() -> {
                                imageView.setImage(image);
                                scaleImageToFitScreen(imageView, image, scene);
                            });
                        }
                    });
                } else {
                    imageView.setImage(image);
                    // Небольшая задержка для гарантии инициализации сцены
                    Platform.runLater(() -> scaleImageToFitScreen(imageView, image, scene));
                }

                counterLabel.setText(String.format("Фото %d из %d", index + 1, photos.size()));

                if (openSystemBtn != null) {
                    openSystemBtn.setOnAction(_ -> openInSystemViewer(photoPath));
                }

            } catch (Exception ex) {
                LOGGER.error("Ошибка при загрузке фото {}: {}", photoPath, ex.getMessage());
                imageView.setImage(null);
                CustomAlert.showError("Ошибка", "Не удалось загрузить фото: " + photoPath);
            }
        }
    }

    /**
     * Улучшенное масштабирование фото под экран
     */
    private void scaleImageToFitScreen(ImageView imageView, Image image, javafx.scene.Scene scene) {
        if (image != null && !image.isError()) {
            // Сбрасываем трансформации перед масштабированием
            imageView.setScaleX(1.0);
            imageView.setScaleY(1.0);
            imageView.setTranslateX(0);
            imageView.setTranslateY(0);

            double imageWidth = image.getWidth();
            double imageHeight = image.getHeight();

            double sceneWidth = scene.getWidth();
            double sceneHeight = scene.getHeight();

            double maxWidth, maxHeight;

            if (sceneWidth > 0 && sceneHeight > 0) {
                maxWidth = sceneWidth * 0.9; // Немного уменьшили для лучшего вида
                maxHeight = (sceneHeight - 150) * 0.9; // Учитываем верхнюю панель
            } else {
                maxWidth = Math.max(imageWidth, 800);
                maxHeight = Math.max(imageHeight, 600);
            }

            double widthRatio = maxWidth / imageWidth;
            double heightRatio = maxHeight / imageHeight;

            double scale = Math.min(widthRatio, heightRatio);
            scale = Math.min(scale, 1.0); // Не увеличиваем маленькие изображения

            imageView.setFitWidth(imageWidth * scale);
            imageView.setFitHeight(imageHeight * scale);
        }
    }
}
