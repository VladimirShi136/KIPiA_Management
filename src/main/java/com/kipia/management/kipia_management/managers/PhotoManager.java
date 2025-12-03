package com.kipia.management.kipia_management.managers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
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
import java.io.FileInputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Утилитный класс для управления фотографиями приборов
 */
public class PhotoManager {
    private static final Logger LOGGER = LogManager.getLogger(PhotoManager.class);
    private static final String LAST_PHOTO_DIR_KEY = "last_photo_directory";
    private static final String PHOTOS_DIR_NAME = "device_photos";

    // ⭐⭐ Статический экземпляр  ⭐⭐
    private static final PhotoManager INSTANCE = new PhotoManager();

    private File lastPhotoDirectory;
    private String basePhotosPath;
    private DeviceDAO deviceDAO;

    // Приватный конструктор
    private PhotoManager() {
        LOGGER.debug("🔄 Создание PhotoManager...");
        restoreLastDirectoryFromPreferences();
        initPhotosDirectory(); // ⭐⭐ Сразу инициализируем папку ⭐⭐
        LOGGER.info("✅ PhotoManager создан (eager initialization)");
    }

    /**
     * Получение экземпляра PhotoManager
     */
    public static PhotoManager getInstance() {
        return INSTANCE;
    }

    /**
     * Инициализация с DeviceDAO (опционально, для автосохранения)
     */
    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
        LOGGER.info("✅ DeviceDAO установлен в PhotoManager");
    }

    /**
     * Добавление фото к прибору с простой проверкой дубликатов
     */
    public void addPhotosToDevice(Device device, Stage ownerStage) {
        FileChooser chooser = createFileChooser();
        List<File> files = chooser.showOpenMultipleDialog(ownerStage);

        if (files == null || files.isEmpty()) {
            return;
        }

        saveLastDirectory(files.getFirst());

        int addedCount = 0;
        int duplicateCount = 0;
        int errorCount = 0;

        List<String> existingPhotos = device.getPhotos();
        if (existingPhotos == null) {
            existingPhotos = new ArrayList<>();
            device.setPhotos(existingPhotos);
        }

        // Обработка файлов
        for (File file : files) {
            try {
                // Базовая проверка файла
                if (!file.exists() || file.length() == 0) {
                    LOGGER.warn("⚠️ Пропущен невалидный файл: {}", file.getName());
                    errorCount++;
                    continue;
                }

                // Проверка на дубликат
                if (isFileDuplicate(file, device)) {
                    LOGGER.info("⚠️ Пропущен дубликат: {}", file.getName());
                    duplicateCount++;
                    continue;
                }

                // Копирование файла
                String storedFileName = copyPhotoToStorage(file, device);
                if (storedFileName == null) {
                    LOGGER.warn("⚠️ Ошибка копирования: {}", file.getName());
                    errorCount++;
                    continue;
                }

                // Проверка, что файл создан
                File savedFile = new File(getFullPhotoPath(storedFileName));
                if (!savedFile.exists()) {
                    LOGGER.error("❌ Скопированный файл не найден: {}", storedFileName);
                    errorCount++;
                    continue;
                }

                // Добавление в список
                device.addPhoto(storedFileName);
                addedCount++;
                LOGGER.info("✅ Фото добавлено: {} -> {}", file.getName(), storedFileName);

            } catch (Exception ex) {
                LOGGER.error("❌ Ошибка обработки файла {}: {}", file.getName(), ex.getMessage());
                errorCount++;
            }
        }

        // Сохранение в БД
        if (addedCount > 0 && deviceDAO != null) {
            try {
                deviceDAO.updateDevice(device);
                LOGGER.info("✅ Устройство обновлено в БД (+{} фото)", addedCount);
            } catch (Exception e) {
                LOGGER.error("❌ Ошибка сохранения в БД: {}", e.getMessage());
            }
        }

        // Показ результата
        showSimpleResult(addedCount, duplicateCount, errorCount, files.size());
    }

    /**
     * Простой показ результата
     */
    private void showSimpleResult(int added, int duplicates, int errors, int total) {
        StringBuilder message = new StringBuilder();

        if (added > 0) {
            message.append(String.format("✅ Добавлено: %d фото\n", added));
        }

        if (duplicates > 0) {
            message.append(String.format("⚠️ Пропущено дубликатов: %d фото\n", duplicates));
        }

        if (errors > 0) {
            message.append(String.format("❌ Ошибок: %d фото\n", errors));
        }

        if (message.isEmpty()) {
            message.append("Ничего не добавлено\n");
        }

        message.append(String.format("\nВсего выбрано: %d файлов", total));

        CustomAlert.showInfo("Добавление фото", message.toString());
    }

    /**
     * Удалить одно фото устройства
     */
    public boolean deletePhoto(Device device, String photoFileName) {
        try {
            LOGGER.info("🗑️ Начато удаление фото: {} для устройства ID={}",
                    photoFileName, device.getId());

            // 1. Удалить из списка фото устройства
            List<String> photos = device.getPhotos();
            if (photos == null || !photos.contains(photoFileName)) {
                LOGGER.warn("⚠️ Фото не найдено в списке устройства: {}", photoFileName);
                return false;
            }

            boolean removed = photos.remove(photoFileName);
            if (!removed) {
                LOGGER.error("❌ Не удалось удалить фото из списка: {}", photoFileName);
                return false;
            }

            // 2. Удалить файл из файловой системы
            String fullPath = getFullPhotoPath(photoFileName);
            if (fullPath != null) {
                File file = new File(fullPath);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        LOGGER.info("✅ Файл удален с диска: {}", fullPath);
                    } else {
                        LOGGER.warn("⚠️ Не удалось удалить файл с диска: {}", fullPath);
                        // Продолжаем, даже если файл не удален, так как запись уже удалена из списка
                    }
                } else {
                    LOGGER.warn("⚠️ Файл не найден на диске: {}", fullPath);
                }
            }

            // 3. Обновить устройство в БД
            if (deviceDAO != null) {
                deviceDAO.updateDevice(device);
                LOGGER.info("✅ Устройство обновлено в БД");
            } else {
                LOGGER.warn("⚠️ DeviceDAO не установлен, обновление БД пропущено");
            }

            return true;

        } catch (Exception e) {
            LOGGER.error("❌ Ошибка удаления фото: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Получение полного пути к файлу фото
     */
    public String getFullPhotoPath(String storedFileName) {
        if (storedFileName == null || storedFileName.isEmpty()) {
            return null;
        }

        // Если уже полный путь
        if (storedFileName.startsWith("/") || storedFileName.contains(":\\") || storedFileName.contains(":/")) {
            return storedFileName;
        }

        // Иначе добавляем базовый путь
        return basePhotosPath + File.separator + storedFileName;
    }

    /**
     * Просмотр фотографий
     */
    public void viewDevicePhotos(Device device, Stage ownerStage) {
        LOGGER.info("👁️ Просмотр фото для устройства ID={}, Name={}",
                device.getId(), device.getName());
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
    public void openInSystemViewer(String storedFileName) {
        try {
            String fullPath = getFullPhotoPath(storedFileName);
            if (fullPath == null) {
                CustomAlert.showWarning("Просмотр фото", "Не удалось определить путь к файлу: " + storedFileName);
                return;
            }

            File photoFile = new File(fullPath);

            if (photoFile.exists()) {
                java.awt.Desktop.getDesktop().open(photoFile);
                LOGGER.info("✅ Фото открыто в системном приложении: {}", storedFileName);
            } else {
                LOGGER.warn("⚠️ Файл не найден: {}", fullPath);

                // Предлагаем удалить несуществующее фото
                boolean removeBroken = CustomAlert.showConfirmation("Битое фото",
                        "Файл не найден на диске: " + storedFileName +
                                "\n\nУдалить эту запись из списка фото?");

                if (removeBroken) {
                    // Нужно получить доступ к Device для удаления
                    // Это нужно будет реализовать в вызывающем коде
                    CustomAlert.showInfo("Информация",
                            "Удаление битого фото должно быть выполнено через интерфейс просмотра фото");
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Ошибка при открытии фото в системном приложении: {}", ex.getMessage(), ex);
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
        Button deleteBtn = createDeleteButton(); // ⭐⭐ НОВАЯ КНОПКА УДАЛЕНИЯ ⭐⭐

        // Информация о приборе
        VBox deviceInfoBox = createDeviceInfoBox(device);

        // ⭐⭐ ОБНОВЛЕННАЯ СТРУКТУРА: вся навигация и кнопки в одном центральном контейнере ⭐⭐
        VBox centerContainer = createCenterContainer(prevBtn, counterLabel, nextBtn,
                openSystemBtn, deleteBtn, photos.size());

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
        setupNavigation(photos, imageView, counterLabel, prevBtn, nextBtn,
                currentIndex, scene, device, stage, deleteBtn);

        // ⭐⭐ НАСТРОЙКА КНОПКИ УДАЛЕНИЯ ⭐⭐
        setupDeleteButton(deleteBtn, device, photos, currentIndex, imageView,
                counterLabel, openSystemBtn, scene, stage);

        // Настройка зума по зажатию ЛКМ
        setupDragZoom(imageView);

        stage.setScene(scene);

        // Инициализация первого фото
        showPhotoAtIndex(photos, 0, imageView, counterLabel, openSystemBtn, scene,
                stage, deleteBtn, device);

        if (ownerStage != null) {
            stage.initOwner(ownerStage);
        }

        return stage;
    }

    /**
     * Создание центрального контейнера с навигацией и кнопками
     */
    private VBox createCenterContainer(Button prevBtn, Label counterLabel,
                                       Button nextBtn, Button openSystemBtn,
                                       Button deleteBtn, int totalPhotos) {
        VBox centerContainer = new VBox();
        centerContainer.getStyleClass().add("photo-viewer-center-container");
        centerContainer.setAlignment(Pos.CENTER);
        centerContainer.setSpacing(15);

        // Контейнер навигации (кнопки назад/вперед + счетчик)
        HBox navContainer = new HBox(10);
        navContainer.getStyleClass().add("photo-viewer-nav-container");
        navContainer.setAlignment(Pos.CENTER);
        navContainer.getChildren().addAll(prevBtn, counterLabel, nextBtn);

        // Контейнер для кнопок действий
        HBox actionContainer = new HBox(15);
        actionContainer.getStyleClass().add("photo-viewer-action-container");
        actionContainer.setAlignment(Pos.CENTER);

        // ⭐⭐ АКТИВИРУЕМ КНОПКУ УДАЛЕНИЯ ТОЛЬКО ЕСЛИ ЕСТЬ ФОТО ⭐⭐
        if (totalPhotos > 0) {
            actionContainer.getChildren().addAll(openSystemBtn, deleteBtn);
        } else {
            actionContainer.getChildren().add(openSystemBtn);
        }

        // Добавляем навигацию и кнопки действий в вертикальный контейнер
        centerContainer.getChildren().addAll(navContainer, actionContainer);

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

        // Центральная часть - навигация и кнопки действий
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
        VBox infoBox = new VBox(4);
        infoBox.getStyleClass().add("photo-viewer-device-info");
        infoBox.setAlignment(Pos.CENTER_LEFT);

        infoBox.setMaxWidth(320);
        infoBox.setPrefWidth(320);

        // Создаем элементы
        Label inventoryLabel = new Label("Инв. №: " + (device.getInventoryNumber() != null ? device.getInventoryNumber() : "не указан"));
        Label locationLabel = new Label("Место: " + (device.getLocation() != null ? device.getLocation() : "не указано"));
        Label valveLabel = new Label("Кран №: " + (device.getValveNumber() != null ? device.getValveNumber() : "не указан"));

        // Применяем стиль
        inventoryLabel.getStyleClass().add("photo-viewer-device-text");
        locationLabel.getStyleClass().add("photo-viewer-device-text");
        valveLabel.getStyleClass().add("photo-viewer-device-text");

        inventoryLabel.setWrapText(true);
        locationLabel.setWrapText(true);
        valveLabel.setWrapText(true);

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
                                 Button prevBtn, Button nextBtn, int[] currentIndex,
                                 javafx.scene.Scene scene, Device device, Stage stage,
                                 Button deleteBtn) { // ⭐⭐ ДОБАВЛЯЕМ ПАРАМЕТРЫ ⭐⭐

        prevBtn.setOnAction(_ -> {
            if (currentIndex[0] > 0) {
                currentIndex[0]--;
                showPhotoAtIndex(photos, currentIndex[0], imageView, counterLabel,
                        null, scene, stage, deleteBtn, device);
            }
        });

        nextBtn.setOnAction(_ -> {
            if (currentIndex[0] < photos.size() - 1) {
                currentIndex[0]++;
                showPhotoAtIndex(photos, currentIndex[0], imageView, counterLabel,
                        null, scene, stage, deleteBtn, device);
            }
        });

        // ⭐⭐ СОЗДАЕМ FINAL КОПИИ ДЛЯ ИСПОЛЬЗОВАНИЯ В ЛЯМБДЕ ⭐⭐
        final Device finalDevice = device;
        final Button finalDeleteBtn = deleteBtn;
        final Stage finalStage = stage;

        // Управление с клавиатуры
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case LEFT, A -> {
                    if (currentIndex[0] > 0) {
                        currentIndex[0]--;
                        showPhotoAtIndex(photos, currentIndex[0], imageView, counterLabel,
                                null, scene, finalStage, finalDeleteBtn, finalDevice);
                    }
                }
                case RIGHT, D -> {
                    if (currentIndex[0] < photos.size() - 1) {
                        currentIndex[0]++;
                        showPhotoAtIndex(photos, currentIndex[0], imageView, counterLabel,
                                null, scene, finalStage, finalDeleteBtn, finalDevice);
                    }
                }
                case DELETE, BACK_SPACE -> {
                    // Удаление по клавише Delete
                    if (currentIndex[0] >= 0 && currentIndex[0] < photos.size()) {
                        deleteCurrentPhoto(finalStage, photos, currentIndex, imageView,
                                counterLabel, scene, finalDevice, finalStage, finalDeleteBtn);
                    }
                }
                case ESCAPE -> finalStage.close();
            }
        });
    }

    /**
     * Настройка кнопки удаления
     */
    private void setupDeleteButton(Button deleteBtn, Device device, List<String> photos,
                                   int[] currentIndex, ImageView imageView, Label counterLabel,
                                   Button openSystemBtn, javafx.scene.Scene scene, Stage stage) {
        deleteBtn.setOnAction(_ -> deleteCurrentPhoto(stage, photos, currentIndex, imageView,
                counterLabel, scene, device, stage, deleteBtn));
    }

    /**
     * Удаление текущего фото
     */
    private void deleteCurrentPhoto(Stage ownerStage, List<String> photos, int[] currentIndex,
                                    ImageView imageView, Label counterLabel,
                                    javafx.scene.Scene scene, Device device, Stage stage, Button deleteBtn) {
        if (photos == null || photos.isEmpty()) {
            LOGGER.warn("⚠️ Список фото пуст, нечего удалять");
            return;
        }

        // ⭐⭐ ПРОВЕРЯЕМ ГРАНИЦЫ ПЕРЕД УДАЛЕНИЕМ ⭐⭐
        if (currentIndex[0] >= 0 && currentIndex[0] < photos.size()) {
            String photoToDelete = photos.get(currentIndex[0]);

            boolean confirm = CustomAlert.showConfirmation(
                    "Удаление фото",
                    "Удалить текущее фото?\n\n" +
                            "Имя файла: " + photoToDelete + "\n" +
                            "Файл будет удален с диска."
            );

            if (confirm) {
                // ⭐⭐ СОХРАНЯЕМ ИНДЕКС ПЕРЕД УДАЛЕНИЕМ ⭐⭐
                int indexToDelete = currentIndex[0];

                boolean deleted = deletePhoto(device, photoToDelete);
                if (deleted) {
                    // Удаляем из локального списка по сохраненному индексу
                    if (indexToDelete < photos.size()) {
                        photos.remove(indexToDelete);
                    }

                    if (photos.isEmpty()) {
                        // Если фото больше нет, закрываем окно
                        CustomAlert.showInfo("Удаление фото", "Все фото удалены");
                        stage.close();
                    } else {
                        // Переходим к следующему фото, корректируем индекс
                        if (indexToDelete >= photos.size()) {
                            currentIndex[0] = photos.size() - 1;
                        } else {
                            currentIndex[0] = indexToDelete; // Остаемся на том же индексе (следующий фото сдвинулся)
                        }

                        // Обновляем интерфейс
                        showPhotoAtIndex(photos, currentIndex[0], imageView, counterLabel,
                                null, scene, stage, deleteBtn, device);
                    }
                } else {
                    CustomAlert.showError("Ошибка", "Не удалось удалить фото");
                }
            }
        } else {
            LOGGER.warn("⚠️ Текущий индекс вне границ списка фото: {} (размер: {})",
                    currentIndex[0], photos.size());
        }
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
        imageView.getStyleClass().add("photo-viewer-image");
        return imageView;
    }

    /**
     * Создание кнопок навигации
     */
    private Button createNavigationButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().addAll("button", "photo-viewer-nav-button");
        button.setPrefSize(100, 35);
        return button;
    }

    /**
     * Создание кнопки системного приложения
     */
    private Button createSystemViewerButton() {
        Button button = new Button("📷 Открыть в системном приложении");
        button.getStyleClass().addAll("button", "photo-viewer-system-button");
        button.setPrefWidth(280);
        return button;
    }

    /**
     * Создание кнопки удаления
     */
    private Button createDeleteButton() {
        Button button = new Button("🗑 Удалить фото");
        button.getStyleClass().addAll("button", "photo-viewer-delete-button");
        button.setPrefWidth(180);
        return button;
    }

    private void showPhotoAtIndex(List<String> photos, int index, ImageView imageView,
                                  Label counterLabel, Button openSystemBtn,
                                  javafx.scene.Scene scene, Stage stage,
                                  Button deleteBtn, Device device) {
        if (index >= 0 && index < photos.size()) {
            String storedFileName = photos.get(index);

            String fullPath = getFullPhotoPath(storedFileName);

            if (fullPath == null) {
                LOGGER.error("❌ Не удалось найти фото: {}", storedFileName);
                CustomAlert.showWarning("Просмотр фото",
                        "Файл не найден: " + storedFileName +
                                "\nПроверьте папку: " + basePhotosPath);
                return;
            }

            // ⭐⭐ ПРОВЕРЯЕМ СУЩЕСТВОВАНИЕ ФАЙЛА ⭐⭐
            File photoFile = new File(fullPath);
            if (!photoFile.exists()) {
                LOGGER.error("❌ Файл не существует: {}", fullPath);
                CustomAlert.showWarning("Просмотр фото",
                        "Файл не найден: " + storedFileName +
                                "\nПуть: " + fullPath);

                // ⭐⭐ ПРЕДЛАГАЕМ УДАЛИТЬ НЕСУЩЕСТВУЮЩЕЕ ФОТО ⭐⭐
                boolean removeBroken = CustomAlert.showConfirmation("Битое фото",
                        "Файл не найден на диске: " + storedFileName +
                                "\n\nУдалить эту запись из списка фото?");

                if (removeBroken) {
                    photos.remove(index);
                    if (deviceDAO != null) {
                        // Обновляем устройство в БД
                        List<String> devicePhotos = device.getPhotos();
                        devicePhotos.remove(storedFileName);
                        deviceDAO.updateDevice(device);
                    }

                    // Если фото остались, показываем следующее
                    if (!photos.isEmpty()) {
                        int newIndex = Math.min(index, photos.size() - 1);
                        showPhotoAtIndex(photos, newIndex, imageView, counterLabel,
                                openSystemBtn, scene, stage, deleteBtn, device);
                    } else {
                        CustomAlert.showInfo("Информация", "Все фото удалены");
                        stage.close();
                    }
                }
                return;
            }

            try {
                // ⭐⭐ ИСПРАВЛЯЕМ ПУТЬ ДЛЯ Image ⭐⭐
                // Для Windows нужно экранировать обратные слеши
                String imagePath = photoFile.toURI().toString();
                LOGGER.debug("🖼️ Загрузка изображения по пути: {}", imagePath);

                Image image = new Image(imagePath, false);

                if (image.isError()) {
                    LOGGER.error("❌ Ошибка загрузки изображения: {}", image.getException().getMessage());
                    CustomAlert.showError("Ошибка", "Не удалось загрузить фото: " + storedFileName +
                            "\nОшибка: " + image.getException().getMessage());
                    return;
                }

                // Ждем загрузки изображения
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
                    Platform.runLater(() -> scaleImageToFitScreen(imageView, image, scene));
                }

                counterLabel.setText(String.format("Фото %d из %d", index + 1, photos.size()));

                if (openSystemBtn != null) {
                    openSystemBtn.setOnAction(_ -> openInSystemViewer(storedFileName));
                }

                // ⭐⭐ ОБНОВЛЯЕМ ТЕКСТ КНОПКИ УДАЛЕНИЯ ⭐⭐
                if (deleteBtn != null) {
                    deleteBtn.setText("🗑 Удалить (" + (index + 1) + "/" + photos.size() + ")");
                }

            } catch (Exception ex) {
                LOGGER.error("❌ Ошибка при загрузке фото {}: {}", fullPath, ex.getMessage(), ex);
                imageView.setImage(null);
                CustomAlert.showError("Ошибка", "Не удалось загрузить фото: " + storedFileName);
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
                maxWidth = sceneWidth * 0.9;
                maxHeight = (sceneHeight - 150) * 0.9;
            } else {
                maxWidth = Math.max(imageWidth, 800);
                maxHeight = Math.max(imageHeight, 600);
            }

            double widthRatio = maxWidth / imageWidth;
            double heightRatio = maxHeight / imageHeight;

            double scale = Math.min(widthRatio, heightRatio);
            scale = Math.min(scale, 1.0);

            imageView.setFitWidth(imageWidth * scale);
            imageView.setFitHeight(imageHeight * scale);
        }
    }

    /**
     * Инициализация директории для фото
     */
    private void initPhotosDirectory() {
        try {
            basePhotosPath = getPhotosDirectoryPath();
            File photosDir = new File(basePhotosPath);

            if (!photosDir.exists()) {
                boolean created = photosDir.mkdirs();
                if (created) {
                    LOGGER.info("✅ Создана папка для фото: {}", basePhotosPath);
                } else {
                    LOGGER.error("❌ Не удалось создать папку для фото: {}", basePhotosPath);
                }
            }

            LOGGER.info("📁 Базовая директория фото: {}", basePhotosPath);

        } catch (Exception e) {
            LOGGER.error("❌ Ошибка при создании папки для фото: {}", e.getMessage());
            basePhotosPath = System.getProperty("java.io.tmpdir") + File.separator + PHOTOS_DIR_NAME;
        }
    }

    /**
     * Определение пути к папке фото
     */
    private String getPhotosDirectoryPath() {
        if ("true".equals(System.getProperty("production"))) {
            LOGGER.info("📁 Режим фото: ПРОДАКШЕН (принудительно через -Dproduction=true)");
            return getProductionPhotosPath();
        }

        if ("true".equals(System.getProperty("development"))) {
            LOGGER.info("📁 Режим фото: РАЗРАБОТКА (принудительно через -Ddevelopment=true)");
            return getDevelopmentPhotosPath();
        }

        String classPath = System.getProperty("java.class.path");
        String javaHome = System.getProperty("java.home");

        boolean isDev = classPath.contains("target/classes") ||
                classPath.contains("idea_rt.jar") ||
                javaHome.contains("IntelliJ") ||
                classPath.contains(".idea") ||
                !isRunningFromJAR();

        if (isDev) {
            LOGGER.info("📁 Режим фото: РАЗРАБОТКА (автоопределение)");
            return getDevelopmentPhotosPath();
        } else {
            LOGGER.info("📁 Режим фото: ПРОДАКШЕН (автоопределение)");
            return getProductionPhotosPath();
        }
    }

    /**
     * Путь для разработки - рядом с проектом
     */
    private String getDevelopmentPhotosPath() {
        String projectDir = System.getProperty("user.dir");
        return projectDir + File.separator + PHOTOS_DIR_NAME;
    }

    /**
     * Путь для продакшена - в AppData
     */
    private String getProductionPhotosPath() {
        String userDataDir = System.getenv("APPDATA") + File.separator + "KIPiA_Management";
        return userDataDir + File.separator + PHOTOS_DIR_NAME;
    }

    /**
     * Проверяем, запущено ли приложение из JAR файла
     */
    private boolean isRunningFromJAR() {
        String className = this.getClass().getName().replace('.', '/');
        String classJar = Objects.requireNonNull(this.getClass().getResource("/" + className + ".class")).toString();
        return classJar.startsWith("jar:");
    }

    /**
     * Упрощенное копирование фото в хранилище
     */
    private String copyPhotoToStorage(File originalFile, Device device) {
        try {
            if (basePhotosPath == null) return null;
            if (!originalFile.exists() || !originalFile.canRead()) return null;

            String originalName = originalFile.getName();
            int dotIndex = originalName.lastIndexOf('.');

            if (dotIndex <= 0) {
                originalName = originalName + ".jpg";
                dotIndex = originalName.lastIndexOf('.');
            }

            String baseName = originalName.substring(0, dotIndex);
            String extension = originalName.substring(dotIndex);

            // Очистка имени
            baseName = baseName.replaceAll("[\\\\/:*?\"<>|]", "_");

            // Уникальное имя с timestamp
            String timestamp = String.valueOf(System.currentTimeMillis());
            String newFileName = String.format("device_%d_%s_%s%s",
                    device.getId(), baseName, timestamp, extension);

            Path destinationPath = Paths.get(basePhotosPath, newFileName);

            // Создаем директорию если нужно
            File photosDir = new File(basePhotosPath);
            if (!photosDir.exists()) {
                photosDir.mkdirs();
            }

            // Копируем
            Files.copy(originalFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);

            // Проверяем
            File copiedFile = destinationPath.toFile();
            if (!copiedFile.exists()) return null;

            LOGGER.info("📸 Фото сохранено: {} ({} байт)", newFileName, copiedFile.length());
            return newFileName;

        } catch (Exception e) {
            LOGGER.error("❌ Ошибка копирования: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Компактная проверка дубликатов по хэшу MD5
     * @return true если файл уже существует в фото устройства
     */
    private boolean isFileDuplicate(File newFile, Device device) {
        try {
            List<String> existingPhotos = device.getPhotos();
            if (existingPhotos == null || existingPhotos.isEmpty()) {
                return false;
            }

            // Вычисляем хэш нового файла
            byte[] newFileHash = calculateMD5Hash(newFile);

            // Сравниваем с существующими фото
            for (String existingPhoto : existingPhotos) {
                String fullPath = getFullPhotoPath(existingPhoto);
                File existingFile = new File(fullPath);

                if (!existingFile.exists()) continue;

                // Быстрая проверка по размеру
                if (existingFile.length() != newFile.length()) continue;

                // Проверка по хэшу
                byte[] existingHash = calculateMD5Hash(existingFile);
                if (Arrays.equals(newFileHash, existingHash)) {
                    LOGGER.info("⚠️ Фото уже существует: {} (дубликат {})",
                            newFile.getName(), existingPhoto);
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            LOGGER.warn("⚠️ Ошибка проверки дубликата, считаем не дубликатом: {}", e.getMessage());
            return false; // При ошибке разрешаем загрузку
        }
    }

    /**
     * Вычисляет MD5 хэш файла (компактная версия)
     */
    private byte[] calculateMD5Hash(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            return md.digest();
        }
    }
}