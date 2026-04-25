package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.managers.PhotoManager;
import com.kipia.management.kipia_management.managers.PhotoViewer;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;
import com.kipia.management.kipia_management.utils.LoadingIndicator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Контроллер для работы с галереей фотографий
 *
 * @author vladimir_shi
 * @since 01.12.2025
 */
public class PhotoGalleryController implements SearchableController {

    private static final Logger LOGGER = LogManager.getLogger(PhotoGalleryController.class);

    // Сервисы
    private DeviceDAO deviceDAO;
    private final PhotoManager photoManager;

    // Данные
    private List<Device> allDevices;
    private Map<String, List<Device>> devicesByLocation;
    private ObservableList<LocationCardData> locationCardsData;
    private FilteredList<LocationCardData> filteredCards;
    private final Map<String, Boolean> cardExpansionState = new HashMap<>();

    // Класс для хранения данных карточки
    public static class LocationCardData {
        private final String location;
        private final List<Device> devices;
        private final int deviceCount;
        private final int photoCount;
        private final int devicesWithPhotos;

        public LocationCardData(String location, List<Device> devices) {
            this.location = location;
            this.devices = devices;
            this.deviceCount = devices.size();

            int photos = 0;
            int withPhotos = 0;
            for (Device device : devices) {
                List<String> devicePhotos = device.getPhotos();
                if (devicePhotos != null && !devicePhotos.isEmpty()) {
                    withPhotos++;
                    photos += devicePhotos.size();
                }
            }
            this.photoCount = photos;
            this.devicesWithPhotos = withPhotos;
        }

        public String getLocation() { return location; }
        public List<Device> getDevices() { return devices; }
        public int getDeviceCount() { return deviceCount; }
        public int getPhotoCount() { return photoCount; }
        public int getDevicesWithPhotos() { return devicesWithPhotos; }
        public boolean hasPhotos() { return photoCount > 0; }
    }

    // FXML элементы
    @FXML private StackPane rootPane;
    @FXML private VBox contentBox;
    @FXML private ListView<LocationCardData> cardsListView;
    @FXML private Label locationsCountLabel;
    @FXML private Label devicesCountLabel;
    @FXML private Label photosCountLabel;
    @FXML private HBox filteredStatsBox;
    @FXML private Label filteredLocationsLabel;
    @FXML private Label filteredDevicesLabel;
    @FXML private Label filteredPhotosLabel;
    @FXML private Label devicesWithPhotosLabel;

    // Индикатор загрузки
    private LoadingIndicator loadingIndicator;

    // Внешние элементы поиска (из верхней панели)
    private TextField externalSearchField;
    private ComboBox<String> externalLocationFilter;
    private CheckBox externalPhotosOnlyCheck;

    public PhotoGalleryController() {
        photoManager = PhotoManager.getInstance();
    }

    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
    }

    @FXML
    public void initialize() {
        setupListView();
    }

    /**
     * Просмотр карточек с фотографиями
     */
    private void setupListView() {
        cardsListView.setCellFactory(_ -> new ListCell<>() {
            private final VBox card = new VBox();
            private final Label locationLabel = new Label();
            private final HBox statsBox = new HBox();
            private final HBox buttonsBox = new HBox();
            private final Button viewAllBtn = new Button("Просмотреть все фото");
            private final VBox devicesList = new VBox();
            private final Button toggleBtn = new Button();
            private String currentLocation = null;
            private boolean isExpanded = false;
            private Runnable updateToggleIcon; // объявляем полем — нужен и в init-блоке, и в updateItem

            {
                // Инициализация и настройка один раз
                card.getStyleClass().add("location-card");
                card.setPrefWidth(320);
                card.setMinWidth(320);
                card.setMinHeight(160);
                card.setPadding(new Insets(15));
                card.setSpacing(10);

                locationLabel.getStyleClass().add("location-name");
                locationLabel.setWrapText(true);
                locationLabel.setMaxWidth(280);

                statsBox.getStyleClass().add("card-stats-row");
                statsBox.setAlignment(Pos.CENTER);

                viewAllBtn.getStyleClass().addAll("button", "view-all-button");
                viewAllBtn.setPrefWidth(180);
                viewAllBtn.setMinWidth(180);

                buttonsBox.getStyleClass().add("card-buttons-row");
                buttonsBox.setAlignment(Pos.CENTER);
                buttonsBox.getChildren().addAll(viewAllBtn);

                devicesList.getStyleClass().add("devices-list");
                devicesList.setVisible(false);
                devicesList.setManaged(false);

                // Кнопка раскрытия/скрытия с иконкой
                toggleBtn.getStyleClass().add("toggle-devices-button");
                toggleBtn.setText(null);

                // Вспомогательный метод установки иконки — сохраняем как final поле
                // чтобы вызывать и из onAction, и из updateItem при восстановлении состояния
                updateToggleIcon = () -> {
                    if (toggleBtn.getScene() == null) return;
                    boolean dark = toggleBtn.getScene().getStylesheets().stream()
                            .anyMatch(s -> s.contains("dark-theme.css"));
                    String iconPath = isExpanded
                            ? (dark ? "/images/arrow-up-dark.png"   : "/images/arrow-up-white.png")
                            : (dark ? "/images/arrow-down-dark.png" : "/images/arrow-down-white.png");
                    javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(
                            new javafx.scene.image.Image(
                                    getClass().getResourceAsStream(iconPath)
                            )
                    );
                    iv.setFitWidth(24);
                    iv.setFitHeight(24);
                    iv.setPreserveRatio(true);
                    toggleBtn.setGraphic(iv);
                };

                // Вешаем listener один раз когда кнопка попадёт в сцену
                toggleBtn.sceneProperty().addListener((_, _, newScene) -> {
                    if (newScene != null) {
                        updateToggleIcon.run();
                        newScene.getStylesheets().addListener(
                                (javafx.collections.ListChangeListener<String>) _ -> updateToggleIcon.run()
                        );
                    }
                });

                toggleBtn.setOnAction(_ -> {
                    isExpanded = !isExpanded;
                    devicesList.setVisible(isExpanded);
                    devicesList.setManaged(isExpanded);
                    updateToggleIcon.run();   // обновляем иконку при клике

                    if (isExpanded) {
                        toggleBtn.getStyleClass().add("expanded");
                        if (currentLocation != null) cardExpansionState.put(currentLocation, true);
                    } else {
                        toggleBtn.getStyleClass().remove("expanded");
                        if (currentLocation != null) cardExpansionState.put(currentLocation, false);
                    }
                });

                card.getChildren().addAll(locationLabel, statsBox, buttonsBox, toggleBtn, devicesList);

                // Обработчики событий
                card.setOnMouseEntered(_ -> card.getStyleClass().add("location-card-hover"));
                card.setOnMouseExited(_ -> card.getStyleClass().remove("location-card-hover"));
            }

            @Override
            protected void updateItem(LocationCardData data, boolean empty) {
                super.updateItem(data, empty);

                if (empty || data == null) {
                    setGraphic(null);
                    setText(null);
                    currentLocation = null;
                    return;
                }

                // Сохраняем текущее местоположение
                currentLocation = data.getLocation();

                // Обновляем только данные
                locationLabel.setText(data.getLocation());

                // Статистика с цветными цифрами
                statsBox.getChildren().clear();
                statsBox.getChildren().addAll(
                        createStatBox("Приборы", String.valueOf(data.getDeviceCount()),
                                data.getDevicesWithPhotos() > 0 ? "#27ae60" : "#95a5a6"),
                        createStatBox("Фото", String.valueOf(data.getPhotoCount()),
                                data.getPhotoCount() > 0 ? "#e67e22" : "#95a5a6"),
                        createStatBox("С фото", String.valueOf(data.getDevicesWithPhotos()),
                                data.getDevicesWithPhotos() > 0 ? "#3498db" : "#95a5a6")
                );

                // Кнопка просмотра всех фото
                viewAllBtn.setOnAction(_ -> viewAllPhotosAtLocation(data.getLocation(), data.getDevices()));

                // Список приборов
                devicesList.getChildren().clear();
                for (Device device : data.getDevices()) {
                    devicesList.getChildren().add(createDeviceRow(device));
                }

                // ⭐⭐ ВОССТАНАВЛИВАЕМ СОСТОЯНИЕ РАСКРЫТИЯ ⭐⭐
                Boolean wasExpanded = cardExpansionState.get(data.getLocation());
                isExpanded = wasExpanded != null && wasExpanded;

                devicesList.setVisible(isExpanded);
                devicesList.setManaged(isExpanded);

                if (isExpanded) {
                    toggleBtn.getStyleClass().add("expanded");
                } else {
                    toggleBtn.getStyleClass().remove("expanded");
                }
                // Восстанавливаем иконку по актуальному состоянию и теме
                if (updateToggleIcon != null) updateToggleIcon.run();

                setGraphic(card);
                setText(null);
            }

            private VBox createStatBox(String title, String value, String color) {
                VBox box = new VBox(2);
                box.getStyleClass().add("card-stat-item");
                box.setAlignment(Pos.CENTER);

                Label valueLabel = new Label(value);
                valueLabel.getStyleClass().add("card-stat-value");
                valueLabel.setStyle("-fx-text-fill: " + color + ";");

                Label titleLabel = new Label(title);
                titleLabel.getStyleClass().add("card-stat-title");

                box.getChildren().addAll(valueLabel, titleLabel);
                return box;
            }

            private HBox createDeviceRow(Device device) {
                HBox row = new HBox(8);
                row.getStyleClass().add("device-row");
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 4, 6, 4));

                Label nameLabel = getNameLabel(device);
                nameLabel.getStyleClass().add("device-name");
                nameLabel.setMaxWidth(150);
                nameLabel.setWrapText(true);

                int photoCount = device.getPhotos() != null ? device.getPhotos().size() : 0;
                Label photoLabel = new Label(photoCount + "/" + PhotoManager.MAX_PHOTOS_PER_DEVICE + " фото");
                photoLabel.getStyleClass().add("device-photo-count");
                photoLabel.setStyle(photoCount > 0 ?
                        "-fx-font-weight: bold; -fx-text-fill: #3498db !important;" :
                        "-fx-text-fill: #95a5a6 !important;");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                // Кнопка просмотра фото
                Button viewBtn = new Button("Просмотр фото");
                viewBtn.getStyleClass().addAll("button", "device-view-button");
                viewBtn.setPrefWidth(110);
                viewBtn.setMinWidth(110);
                viewBtn.setDisable(photoCount == 0);

                // Кнопки управления фото
                Button addPhotoBtn = new Button("Добавить фото");
                addPhotoBtn.getStyleClass().addAll("button", "device-add-photo-button");
                addPhotoBtn.setPrefWidth(110);
                addPhotoBtn.setMinWidth(110);

                Button deletePhotoBtn = new Button("Удалить фото");
                deletePhotoBtn.getStyleClass().addAll("button", "device-delete-photo-button");
                deletePhotoBtn.setPrefWidth(110);
                deletePhotoBtn.setMinWidth(110);
                deletePhotoBtn.setDisable(photoCount == 0);

                // Обработчики событий с улучшенным обновлением
                viewBtn.setOnAction(_ -> {
                    Stage stage = (Stage) viewBtn.getScene().getWindow();
                    PhotoViewer.OnPhotoDeletedCallback onDevicePhotoDeleted = (deletedDevice, deletedPhotoName) ->
                            Platform.runLater(() -> {
                                try {
                                    // Обновляем объект прибора в нашей структуре
                                    Device updatedDevice = deviceDAO.getDeviceById(deletedDevice.getId());
                                    if (updatedDevice != null) {
                                        deletedDevice.setPhotos(new ArrayList<>(updatedDevice.getPhotos()));
                                        updateDevicesByLocation(updatedDevice);
                                    }
                                    // Обновляем карточку локации (счётчик фото)
                                    updateLocationCardData(deletedDevice.getLocation());
                                    updateStatistics();
                                    cardsListView.refresh();

                                    LOGGER.info("✅ Карточка локации '{}' обновлена после удаления фото прибора '{}'",
                                            deletedDevice.getLocation(), deletedDevice.getInventoryNumber());
                                } catch (Exception e) {
                                    LOGGER.error("❌ Ошибка обновления карточки после удаления: {}", e.getMessage(), e);
                                }
                            });

                    photoManager.viewDevicePhotos(device, stage, onDevicePhotoDeleted);
                });

                addPhotoBtn.setOnAction(_ -> {
                    Stage stage = (Stage) addPhotoBtn.getScene().getWindow();
                    // Добавляем обработчик завершения добавления фото
                    addPhotosWithCallback(device, stage);
                });

                deletePhotoBtn.setOnAction(_ -> {
                    Stage stage = (Stage) deletePhotoBtn.getScene().getWindow();
                    showDeletePhotoDialog(device);
                });

                // Контейнер для кнопок
                HBox buttonsContainer = new HBox(5);
                buttonsContainer.setAlignment(Pos.CENTER_RIGHT);
                buttonsContainer.getChildren().addAll(viewBtn, addPhotoBtn, deletePhotoBtn);

                row.getChildren().addAll(nameLabel, photoLabel, spacer, buttonsContainer);
                return row;
            }

            /**
             * Вспомогаьельный метод для получения комбинированного названия прибора
             * @param device - передаваемое устройство
             * @return - текстовая метка с названием прибора
             */
            private static Label getNameLabel(Device device) {
                String deviceName = device.getName() != null && !device.getName().trim().isEmpty()
                        ? device.getName()
                        : "Без имени";
                String inventoryNumber = device.getInventoryNumber() != null && !device.getInventoryNumber().trim().isEmpty()
                        ? device.getInventoryNumber()
                        : "";

                String displayName = inventoryNumber.isEmpty() ? deviceName : deviceName + " (" + inventoryNumber + ")";

                return new Label(displayName);
            }
        });
    }

    public void init() {
        if (deviceDAO == null) {
            LOGGER.error("DeviceDAO не установлен!");
            CustomAlertDialog.showError("Ошибка", "Сервис базы данных не инициализирован");
            return;
        }

        // Инициализация индикатора загрузки
        loadingIndicator = new LoadingIndicator("Загрузка галереи...");
        if (rootPane != null) {
            rootPane.getChildren().add(loadingIndicator.getOverlay());
        }

        // Скрываем контент до загрузки
        hideContentBeforeLoad();

        // Запускаем загрузку
        loadDataAsync();
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

                // Загрузка данных
                loadData();

                // Умная задержка
                long elapsedTime = System.currentTimeMillis() - startTime;
                long minDisplayTime = 500;

                if (elapsedTime < minDisplayTime) {
                    Thread.sleep(minDisplayTime - elapsedTime);
                }

                return null;
            }
        };

        loadTask.setOnSucceeded(_ -> {
            showContentAfterLoad();
            loadingIndicator.hide();
        });

        loadTask.setOnFailed(_ -> {
            LOGGER.error("Ошибка загрузки данных: {}", loadTask.getException().getMessage());
            CustomAlertDialog.showError("Ошибка", "Не удалось загрузить данные");
            showContentAfterLoad();
            loadingIndicator.hide();
        });

        new Thread(loadTask).start();
    }

    private void loadData() {
        try {
            LOGGER.info("🔄 Загрузка данных для галереи фото...");

            // Загрузка всех приборов
            allDevices = deviceDAO.getAllDevices();

            // Группировка по местам установки
            devicesByLocation = allDevices.stream()
                    .filter(device -> device.getLocation() != null && !device.getLocation().trim().isEmpty())
                    .collect(Collectors.groupingBy(
                            Device::getLocation,
                            TreeMap::new,
                            Collectors.toList()
                    ));

            // Создание данных для карточек
            locationCardsData = FXCollections.observableArrayList();
            for (Map.Entry<String, List<Device>> entry : devicesByLocation.entrySet()) {
                locationCardsData.add(new LocationCardData(entry.getKey(), entry.getValue()));
            }

            // Настройка фильтрованного списка
            filteredCards = new FilteredList<>(locationCardsData, _ -> true);
            cardsListView.setItems(filteredCards);

            // Заполнение фильтра мест
            updateLocationFilter();

            // Обновление статистики
            updateStatistics();

            LOGGER.info("✅ Данные загружены: {} мест, {} приборов",
                    devicesByLocation.size(), allDevices.size());

        } catch (Exception e) {
            LOGGER.error("❌ Ошибка загрузки данных: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка", "Не удалось загрузить данные приборов");
        }
    }

    /**
     * Добавление фото с callback для обновления данных
     */
    private void addPhotosWithCallback(Device device, Stage stage) {
        // Сохраняем текущее состояние карточек перед изменением
        Map<String, Boolean> previousState = new HashMap<>(cardExpansionState);

        photoManager.addPhotosToDevice(device, stage);

        // Обновляем данные через таймаут
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Даем время на сохранение

                Platform.runLater(() -> {
                    try {
                        // Обновляем устройство из БД
                        Device updatedDevice = deviceDAO.getDeviceById(device.getId());
                        if (updatedDevice != null) {
                            device.setPhotos(updatedDevice.getPhotos());

                            // 1. Обновляем статистику
                            updateStatistics();

                            // 2. Обновляем данные в devicesByLocation
                            updateDevicesByLocation(updatedDevice);

                            // 3. Обновляем карточку
                            updateLocationCardData(updatedDevice.getLocation());

                            // 4. Восстанавливаем состояние раскрытия
                            cardExpansionState.clear();
                            cardExpansionState.putAll(previousState);

                            // 5. Обновляем ListView
                            cardsListView.refresh();
                        }
                    } catch (Exception e) {
                        LOGGER.error("❌ Ошибка обновления после добавления фото: {}", e.getMessage());
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Обновление данных в devicesByLocation
     */
    private void updateDevicesByLocation(Device updatedDevice) {
        String location = updatedDevice.getLocation();
        if (location == null || location.trim().isEmpty()) return;


        List<Device> devices = devicesByLocation.get(location);
        if (devices == null) return;


        for (Device d : devices) {
            if (d.getId() == updatedDevice.getId()) {
                // Полное копирование данных
                d.setPhotos(new ArrayList<>(updatedDevice.getPhotos()));
                d.setName(updatedDevice.getName());
                d.setInventoryNumber(updatedDevice.getInventoryNumber());
                // ... другие поля при необходимости
                break;
            }
        }
    }

    private void updateLocationFilter() {
        if (externalLocationFilter == null || devicesByLocation == null) return;

        List<String> locations = new ArrayList<>(devicesByLocation.keySet());
        Collections.sort(locations);

        Platform.runLater(() -> {
            externalLocationFilter.getItems().clear();
            externalLocationFilter.getItems().add("Все места");
            externalLocationFilter.getItems().addAll(locations);
            externalLocationFilter.setValue("Все места");
        });
    }

    private void applyFilters() {
        if (filteredCards == null) return;

        String searchText = externalSearchField != null ? externalSearchField.getText().toLowerCase().trim() : "";
        boolean photosOnly = externalPhotosOnlyCheck != null && externalPhotosOnlyCheck.isSelected();
        String selectedLocation = externalLocationFilter != null && externalLocationFilter.getValue() != null ?
                externalLocationFilter.getValue() : "Все места";

        // Проверяем, активны ли фильтры
        boolean hasActiveFilters = !searchText.isEmpty() ||
                photosOnly ||
                !"Все места".equals(selectedLocation);

        filteredCards.setPredicate(data -> {
            // Фильтрация по выбранному месту
            if (!"Все места".equals(selectedLocation) && !data.getLocation().equals(selectedLocation)) {
                return false;
            }

            // Фильтрация по поиску
            if (!searchText.isEmpty()) {
                boolean locationMatches = data.getLocation().toLowerCase().contains(searchText);
                boolean deviceMatches = data.getDevices().stream().anyMatch(d ->
                        (d.getName() != null && d.getName().toLowerCase().contains(searchText)) ||
                                (d.getInventoryNumber() != null && d.getInventoryNumber().toLowerCase().contains(searchText)) ||
                                (d.getType() != null && d.getType().toLowerCase().contains(searchText))
                );

                if (!locationMatches && !deviceMatches) {
                    return false;
                }
            }

            // Фильтрация "только с фото"
            return !photosOnly || data.hasPhotos();
        });

        // Показываем/скрываем фильтрованную статистику с анимацией
        if (filteredStatsBox != null) {
            if (hasActiveFilters && !filteredStatsBox.isVisible()) {
                filteredStatsBox.setVisible(true);
                filteredStatsBox.setManaged(true);

                javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
                        javafx.util.Duration.millis(300), filteredStatsBox);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            } else if (!hasActiveFilters && filteredStatsBox.isVisible()) {
                javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
                        javafx.util.Duration.millis(300), filteredStatsBox);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(_ -> {
                    filteredStatsBox.setVisible(false);
                    filteredStatsBox.setManaged(false);
                });
                fadeOut.play();
            }
        }

        updateStatistics(); // Обновляем статистику после применения фильтров
    }

    private void viewAllPhotosAtLocation(String location, List<Device> devices) {
        LOGGER.info("👁️ Просмотр всех фото в месте: {}", location);

        // Собираем PhotoEntry для каждого фото каждого прибора локации
        List<PhotoViewer.PhotoEntry> entries = new ArrayList<>();
        for (Device device : devices) {
            List<String> devicePhotos = device.getPhotos();
            if (devicePhotos == null || devicePhotos.isEmpty()) continue;
            for (String photoName : devicePhotos) {
                String fullPath = photoManager.getFullPhotoPath(device, photoName);
                if (fullPath != null) {
                    entries.add(new PhotoViewer.PhotoEntry(device, photoName, fullPath));
                }
            }
        }

        if (entries.isEmpty()) {
            CustomAlertDialog.showInfo("Просмотр фото",
                    String.format("В месте '%s' нет фотографий", location));
            return;
        }

        Stage stage = (Stage) cardsListView.getScene().getWindow();

        // Callback: после удаления фото обновляем карточку локации в галерее
        PhotoViewer.OnPhotoDeletedCallback onDeleted = (deletedDevice, deletedPhotoName) ->
                Platform.runLater(() -> {
                    try {
                        // Обновляем объект прибора в нашей структуре
                        Device updatedDevice = deviceDAO.getDeviceById(deletedDevice.getId());
                        if (updatedDevice != null) {
                            deletedDevice.setPhotos(new ArrayList<>(updatedDevice.getPhotos()));
                            updateDevicesByLocation(updatedDevice);
                        }
                        // Обновляем карточку локации (счётчик фото)
                        updateLocationCardData(location);
                        updateStatistics();
                        cardsListView.refresh();

                        LOGGER.info("✅ Карточка локации '{}' обновлена после удаления фото прибора '{}'",
                                location, deletedDevice.getInventoryNumber());
                    } catch (Exception e) {
                        LOGGER.error("❌ Ошибка обновления карточки после удаления: {}", e.getMessage(), e);
                    }
                });

        photoManager.viewLocationPhotos("Локация: " + location, entries, stage, onDeleted);
    }

    /**
     * Полное обновление данных карточки для конкретного местоположения
     */
    private void updateLocationCardData(String location) {
        try {
            int cardIndex = -1;
            for (int i = 0; i < locationCardsData.size(); i++) {
                if (locationCardsData.get(i).getLocation().equals(location)) {
                    cardIndex = i;
                    break;
                }
            }

            if (cardIndex == -1) return;


            // Получаем актуальные устройства
            List<Device> updatedDevices = devicesByLocation.get(location);
            if (updatedDevices == null) return;


            // Пересоздаём карточку с новыми данными
            LocationCardData newCardData = new LocationCardData(location, updatedDevices);
            locationCardsData.set(cardIndex, newCardData);


            // Обновляем статистику для отфильтрованных данных
            updateFilteredStatistics();

        } catch (Exception e) {
            LOGGER.error("❌ Ошибка обновления карточки местоположения: {}", e.getMessage());
        }
    }

    /**
     * Обновление только отфильтрованной статистики
     */
    private void updateFilteredStatistics() {
        if (filteredCards == null) return;

        int filteredLocations = 0;
        int filteredDevices = 0;
        int filteredPhotos = 0;
        int devicesWithPhotos = 0;

        // Пересчитываем статистику для отфильтрованных карточек
        for (LocationCardData card : filteredCards) {
            filteredLocations++;
            filteredDevices += card.getDeviceCount();
            filteredPhotos += card.getPhotoCount();
            devicesWithPhotos += card.getDevicesWithPhotos();
        }

        // Обновляем UI
        updateFilteredStatsUI(filteredLocations, filteredDevices, filteredPhotos, devicesWithPhotos);
    }

    /**
     * Обновление UI отфильтрованной статистики
     */
    private void updateFilteredStatsUI(int locations, int devices, int photos, int withPhotos) {
        Platform.runLater(() -> {
            if (filteredLocationsLabel != null) {
                filteredLocationsLabel.setText(String.valueOf(locations));
            }
            if (filteredDevicesLabel != null) {
                filteredDevicesLabel.setText(String.valueOf(devices));
            }
            if (filteredPhotosLabel != null) {
                filteredPhotosLabel.setText(String.valueOf(photos));
            }
            if (devicesWithPhotosLabel != null) {
                devicesWithPhotosLabel.setText(String.valueOf(withPhotos));
            }
        });
    }

    /**
     * Диалог для удаления фото
     */
    private void showDeletePhotoDialog(Device device) {
        List<String> photos = device.getPhotos();
        if (photos == null || photos.isEmpty()) {
            CustomAlertDialog.showInfo("Удаление фото", "У прибора нет фотографий для удаления");
            return;
        }

        // Сохраняем состояние раскрытия карточек
        Map<String, Boolean> previousState = new HashMap<>(cardExpansionState);

        // Используем кастомный диалог вместо ChoiceDialog
        Optional<String> result = CustomAlertDialog.showChoiceDialog(
                "Удаление фото",
                "Выберите фото для удаления из прибора: " + device.getInventoryNumber(),
                photos,
                photos.getFirst()
        );

        result.ifPresent(photoPath -> {
            boolean confirm = CustomAlertDialog.showConfirmation(
                    "Подтверждение удаления",
                    "Вы уверены, что хотите удалить фото?\n\n" +
                            "Файл: " + photoPath + "\n" +
                            "Это действие нельзя отменить."
            );

            if (confirm) {
                boolean deleted = photoManager.deletePhoto(device, photoPath);
                if (deleted) {
                    CustomAlertDialog.showSuccess("Удаление фото", "Фото успешно удалено");

                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            Platform.runLater(() -> {
                                try {
                                    Device updatedDevice = deviceDAO.getDeviceById(device.getId());
                                    if (updatedDevice == null) {
                                        CustomAlertDialog.showError("Ошибка", "Не удалось обновить данные прибора");
                                        return;
                                    }

                                    device.setPhotos(new ArrayList<>(updatedDevice.getPhotos()));
                                    updateDevicesByLocation(updatedDevice);

                                    String location = updatedDevice.getLocation();
                                    if (location != null && !location.trim().isEmpty()) {
                                        updateLocationCardData(location);
                                    }

                                    updateStatistics();

                                    cardExpansionState.clear();
                                    cardExpansionState.putAll(previousState);

                                    filteredCards.setPredicate(filteredCards.getPredicate());
                                    cardsListView.refresh();

                                    LOGGER.info("✅ Карточка обновлена после удаления фото для прибора: {}", device.getName());
                                } catch (Exception e) {
                                    LOGGER.error("❌ Ошибка обновления после удаления фото: {}", e.getMessage(), e);
                                    CustomAlertDialog.showError("Ошибка", "Не удалось обновить интерфейс");
                                }
                            });
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            LOGGER.error("❌ Прервано обновление после удаления фото");
                        }
                    }).start();

                } else {
                    CustomAlertDialog.showError("Ошибка", "Не удалось удалить фото. Проверьте подключение.");
                    LOGGER.error("❌ Не удалось удалить фото: {} для прибора {}", photoPath, device.getName());
                }
            }
        });
    }

    private void updateStatistics() {
        if (devicesByLocation == null || allDevices == null) return;

        int totalLocations = devicesByLocation.size();
        int totalDevices = allDevices.size();
        int totalPhotos = 0;

        // Общая статистика
        for (Device device : allDevices) {
            List<String> photos = device.getPhotos();
            if (photos != null && !photos.isEmpty()) {
                totalPhotos += photos.size();
            }
        }

        // Создаем final копии для использования в лямбда-выражении
        final int finalTotalLocations = totalLocations;
        final int finalTotalDevices = totalDevices;
        final int finalTotalPhotos = totalPhotos;

        // Обновление общей статистики в UI потоке
        Platform.runLater(() -> {
            if (locationsCountLabel != null) {
                locationsCountLabel.setText(String.valueOf(finalTotalLocations));
            }
            if (devicesCountLabel != null) {
                devicesCountLabel.setText(String.valueOf(finalTotalDevices));
            }
            if (photosCountLabel != null) {
                photosCountLabel.setText(String.valueOf(finalTotalPhotos));
            }
        });

        // Фильтрованная статистика (для отфильтрованных карточек)
        int filteredLocations = filteredCards != null ? filteredCards.size() : 0;
        int filteredDevices = 0;
        int filteredPhotos = 0;
        int devicesWithPhotos = 0;

        if (filteredCards != null) {
            for (LocationCardData card : filteredCards) {
                filteredDevices += card.getDeviceCount();
                filteredPhotos += card.getPhotoCount();
                devicesWithPhotos += card.getDevicesWithPhotos();
            }
        }

        // Обновляем отфильтрованную статистику
        updateFilteredStatsUI(filteredLocations, filteredDevices, filteredPhotos, devicesWithPhotos);
    }

    @Override
    public void bindSearchField(TextField externalSearchField) {
        this.externalSearchField = externalSearchField;
        if (externalSearchField != null) {
            externalSearchField.textProperty().addListener((_, _, _) -> applyFilters());
        }
    }

    @Override
    public void bindLocationFilter(ComboBox<String> locationFilter) {
        this.externalLocationFilter = locationFilter;
        if (locationFilter != null) {
            locationFilter.valueProperty().addListener((_, _, _) -> applyFilters());
            // Заполняем фильтр локациями после привязки (если данные уже загружены)
            if (devicesByLocation != null) {
                updateLocationFilter();
            }
        }
    }

    @Override
    public void bindPhotosOnlyCheck(CheckBox photosOnlyCheck) {
        this.externalPhotosOnlyCheck = photosOnlyCheck;
        if (photosOnlyCheck != null) {
            photosOnlyCheck.selectedProperty().addListener((_, _, _) -> applyFilters());
        }
    }

    @Override
    public void clearFilters() {
        if (externalSearchField != null) {
            externalSearchField.clear();
        }
        if (externalLocationFilter != null) {
            externalLocationFilter.setValue("Все места");
        }
        if (externalPhotosOnlyCheck != null) {
            externalPhotosOnlyCheck.setSelected(false);
        }
    }

    @Override
    public boolean hasExtendedFilters() {
        return true;
    }
}