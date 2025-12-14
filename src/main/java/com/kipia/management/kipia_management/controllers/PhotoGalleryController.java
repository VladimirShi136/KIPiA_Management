package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.managers.PhotoManager;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.CustomAlert;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
 * Контроллер для работы с галереей фотографий
 *
 * @author vladimir_shi
 * @since 01.12.2025
 */
public class PhotoGalleryController {

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
    @FXML private TextField searchField;
    @FXML private ComboBox<String> locationFilter;
    @FXML private CheckBox photosOnlyCheck;
    @FXML private ListView<LocationCardData> cardsListView;
    @FXML private Label locationsCountLabel;
    @FXML private Label devicesCountLabel;
    @FXML private Label photosCountLabel;
    @FXML private Label filteredLocationsLabel;
    @FXML private Label filteredDevicesLabel;
    @FXML private Label filteredPhotosLabel;
    @FXML private Label filteredWithPhotosLabel;
    @FXML private Label filteredStatsTitle;

    public PhotoGalleryController() {
        photoManager = PhotoManager.getInstance();
    }

    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
    }

    @FXML
    public void initialize() {
        setupListeners();
        setupListView();
    }

    private void setupListeners() {
        // Поиск и фильтры
        if (searchField != null) {
            searchField.textProperty().addListener((_, _, _) -> applyFilters());
        }
        if (photosOnlyCheck != null) {
            photosOnlyCheck.selectedProperty().addListener((_, _, _) -> applyFilters());
        }
        if (locationFilter != null) {
            locationFilter.valueProperty().addListener((_, _, _) -> applyFilters());
        }
    }

    private void setupListView() {
        cardsListView.setCellFactory(_ -> new ListCell<>() {
            private final VBox card = new VBox();
            private final Label locationLabel = new Label();
            private final HBox statsBox = new HBox();
            private final HBox buttonsBox = new HBox();
            private final Button viewAllBtn = new Button("Просмотреть все фото");
            private final VBox devicesList = new VBox();
            private final Button toggleBtn = new Button();
            private String currentLocation = null; // ⭐⭐ Храним текущее местоположение
            private boolean isExpanded = false;

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
                toggleBtn.setText("Приборы");
                toggleBtn.setOnAction(_ -> {
                    isExpanded = !isExpanded;
                    devicesList.setVisible(isExpanded);
                    devicesList.setManaged(isExpanded);

                    if (isExpanded) {
                        toggleBtn.setText("Скрыть");
                        toggleBtn.getStyleClass().add("expanded");
                        // ⭐⭐ СОХРАНЯЕМ СОСТОЯНИЕ ⭐⭐
                        if (currentLocation != null) {
                            cardExpansionState.put(currentLocation, true);
                        }
                    } else {
                        toggleBtn.setText("Приборы");
                        toggleBtn.getStyleClass().remove("expanded");
                        // ⭐⭐ СОХРАНЯЕМ СОСТОЯНИЕ ⭐⭐
                        if (currentLocation != null) {
                            cardExpansionState.put(currentLocation, false);
                        }
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
                    toggleBtn.setText("Скрыть");
                    toggleBtn.getStyleClass().add("expanded");
                } else {
                    toggleBtn.setText("Приборы");
                    toggleBtn.getStyleClass().remove("expanded");
                }

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

                String deviceName = device.getName() != null && !device.getName().trim().isEmpty()
                        ? device.getName()
                        : "Без имени";
                Label nameLabel = new Label(deviceName);
                nameLabel.getStyleClass().add("device-name");
                nameLabel.setMaxWidth(150);
                nameLabel.setWrapText(true);

                int photoCount = device.getPhotos() != null ? device.getPhotos().size() : 0;
                Label photoLabel = new Label(photoCount + " фото");
                photoLabel.getStyleClass().add("device-photo-count");
                photoLabel.setStyle(photoCount > 0 ?
                        "-fx-font-weight: bold;" :
                        "-fx-text-fill: #7f8c8d;");

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
                    photoManager.viewDevicePhotos(device, stage);
                });

                addPhotoBtn.setOnAction(_ -> {
                    Stage stage = (Stage) addPhotoBtn.getScene().getWindow();
                    // Добавляем обработчик завершения добавления фото
                    addPhotosWithCallback(device, stage);
                });

                deletePhotoBtn.setOnAction(_ -> {
                    Stage stage = (Stage) deletePhotoBtn.getScene().getWindow();
                    showDeletePhotoDialog(device, stage);
                });

                // Контейнер для кнопок
                HBox buttonsContainer = new HBox(5);
                buttonsContainer.setAlignment(Pos.CENTER_RIGHT);
                buttonsContainer.getChildren().addAll(viewBtn, addPhotoBtn, deletePhotoBtn);

                row.getChildren().addAll(nameLabel, photoLabel, spacer, buttonsContainer);
                return row;
            }
        });
    }

    public void init() {
        if (deviceDAO == null) {
            LOGGER.error("DeviceDAO не установлен!");
            CustomAlert.showError("Ошибка", "Сервис базы данных не инициализирован");
            return;
        }
        loadData();
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
            CustomAlert.showError("Ошибка", "Не удалось загрузить данные приборов");
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
        if (locationFilter == null) return;

        List<String> locations = new ArrayList<>(devicesByLocation.keySet());
        Collections.sort(locations);

        locationFilter.getItems().clear();
        locationFilter.getItems().add("Все места");
        locationFilter.getItems().addAll(locations);
        locationFilter.setValue("Все места");
    }

    private void applyFilters() {
        if (filteredCards == null) return;

        String searchText = searchField != null ? searchField.getText().toLowerCase().trim() : "";
        boolean photosOnly = photosOnlyCheck != null && photosOnlyCheck.isSelected();
        String selectedLocation = locationFilter != null && locationFilter.getValue() != null ?
                locationFilter.getValue() : "Все места";

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

        updateStatistics(); // Обновляем статистику после применения фильтров
    }

    private void viewAllPhotosAtLocation(String location, List<Device> devices) {
        LOGGER.info("👁️ Просмотр всех фото в месте: {}", location);

        List<Device> devicesWithPhotos = devices.stream()
                .filter(d -> d.getPhotos() != null && !d.getPhotos().isEmpty())
                .toList();

        if (devicesWithPhotos.isEmpty()) {
            CustomAlert.showInfo("Просмотр фото",
                    String.format("В месте '%s' нет фотографий", location));
            return;
        }

        Stage stage = (Stage) cardsListView.getScene().getWindow();
        photoManager.viewDevicePhotos(devicesWithPhotos.getFirst(), stage);

        int totalPhotos = devicesWithPhotos.stream()
                .mapToInt(d -> d.getPhotos().size())
                .sum();

        if (devicesWithPhotos.size() > 1) {
            CustomAlert.showInfo("Информация",
                    String.format("""
                                    Всего фото в месте '%s': %d (в %d приборах)
                                    
                                    Показаны фото первого прибора.""",
                            location, totalPhotos, devicesWithPhotos.size()));
        }
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
     * Обновление только отфильтрованной статистики ("Показано:")
     */
    private void updateFilteredStatistics() {
        if (filteredCards == null) return;

        int filteredLocations = 0;
        int filteredDevices = 0;
        int filteredPhotos = 0;
        int filteredDevicesWithPhotos = 0;

        // Пересчитываем статистику для отфильтрованных карточек
        for (LocationCardData card : filteredCards) {
            filteredLocations++;
            filteredDevices += card.getDeviceCount();
            filteredPhotos += card.getPhotoCount();
            filteredDevicesWithPhotos += card.getDevicesWithPhotos();
        }

        // Обновляем UI через отдельный метод
        updateFilteredStatsUI(filteredLocations, filteredDevices, filteredPhotos, filteredDevicesWithPhotos);
    }

    /**
     * Обновление UI статистики "Показано:"
     */
    private void updateFilteredStatsUI(int locations, int devices, int photos, int devicesWithPhotos) {
        Platform.runLater(() -> {
            filteredLocationsLabel.setText(String.valueOf(locations));
            filteredDevicesLabel.setText(String.valueOf(devices));
            filteredPhotosLabel.setText(String.valueOf(photos));
            filteredWithPhotosLabel.setText(String.valueOf(devicesWithPhotos));
        });
    }

    /**
     * Диалог для удаления фото
     */
    private void showDeletePhotoDialog(Device device, Stage ownerStage) {
        List<String> photos = device.getPhotos();
        if (photos == null || photos.isEmpty()) {
            CustomAlert.showInfo("Удаление фото", "У прибора нет фотографий для удаления");
            return;
        }

        // Сохраняем состояние раскрытия карточек
        Map<String, Boolean> previousState = new HashMap<>(cardExpansionState);

        ChoiceDialog<String> dialog = new ChoiceDialog<>(photos.getFirst(), photos);
        dialog.setTitle("Удаление фото");
        dialog.setHeaderText("Выберите фото для удаления из прибора: " + device.getName());
        dialog.setContentText("Фото:");

        Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
        dialogStage.getIcons().addAll(ownerStage.getIcons());

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(photoPath -> {
            boolean confirm = CustomAlert.showConfirmation(
                    "Подтверждение удаления",
                    "Вы уверены, что хотите удалить фото?\n\n" +
                            "Файл: " + photoPath + "\n" +
                            "Это действие нельзя отменить."
            );

            if (confirm) {
                boolean deleted = photoManager.deletePhoto(device, photoPath);
                if (deleted) {
                    CustomAlert.showInfo("Удаление фото", "Фото успешно удалено");


                    new Thread(() -> {
                        try {
                            Thread.sleep(500); // Задержка для завершения операций с ФС/БД


                            Platform.runLater(() -> {
                                try {
                                    // 1. Получаем актуальное устройство из БД
                                    Device updatedDevice = deviceDAO.getDeviceById(device.getId());
                                    if (updatedDevice == null) {
                                        CustomAlert.showError("Ошибка", "Не удалось обновить данные прибора");
                                        return;
                                    }

                                    // 2. Обновляем локальный объект
                                    device.setPhotos(new ArrayList<>(updatedDevice.getPhotos()));


                                    // 3. Обновляем хранилище устройств по локациям
                                    updateDevicesByLocation(updatedDevice);


                                    // 4. Пересоздаём карточку местоположения
                                    String location = updatedDevice.getLocation();
                                    if (location != null && !location.trim().isEmpty()) {
                                        updateLocationCardData(location);
                                    }

                                    // 5. Обновляем общую статистику
                                    updateStatistics();


                                    // 6. Восстанавливаем состояние раскрытия
                                    cardExpansionState.clear();
                                    cardExpansionState.putAll(previousState);


                                    // 7. Принудительно обновляем фильтр и ListView
                                    filteredCards.setPredicate(filteredCards.getPredicate());
                                    cardsListView.refresh();


                                    LOGGER.info("✅ Карточка обновлена после удаления фото для прибора: {}", device.getName());


                                } catch (Exception e) {
                                    LOGGER.error("❌ Ошибка обновления после удаления фото: {}", e.getMessage(), e);
                                    CustomAlert.showError("Ошибка", "Не удалось обновить интерфейс");
                                }
                            });
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            LOGGER.error("❌ Прервано обновление после удаления фото");
                        }
                    }).start();

                } else {
                    CustomAlert.showError("Ошибка", "Не удалось удалить фото. Проверьте подключение.");
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

        // Обновление общей статистики
        locationsCountLabel.setText(String.valueOf(totalLocations));
        devicesCountLabel.setText(String.valueOf(totalDevices));
        photosCountLabel.setText(String.valueOf(totalPhotos));

        // Фильтрованная статистика (для отфильтрованных карточек)
        int filteredLocations = filteredCards != null ? filteredCards.size() : 0;
        int filteredDevices = 0;
        int filteredPhotos = 0;
        int filteredDevicesWithPhotos = 0;

        if (filteredCards != null) {
            for (LocationCardData card : filteredCards) {
                filteredDevices += card.getDeviceCount();
                filteredPhotos += card.getPhotoCount();
                filteredDevicesWithPhotos += card.getDevicesWithPhotos();
            }
        }

        // ⭐⭐ ИСПРАВЛЕНИЕ: Вызываем метод с финальными параметрами ⭐⭐
        updateFilteredStatsUI(filteredLocations, filteredDevices, filteredPhotos, filteredDevicesWithPhotos);

        // Обновление заголовка отфильтрованной статистики
        String selectedLocation = locationFilter != null && locationFilter.getValue() != null
                ? locationFilter.getValue() : "Все места";
        boolean photosOnly = photosOnlyCheck != null && photosOnlyCheck.isSelected();

        StringBuilder title = new StringBuilder("Показано:");

        if (!"Все места".equals(selectedLocation)) {
            title.append(" место '").append(selectedLocation).append("'");
        } else if (photosOnly) {
            title.append(" только с фото");
        } else if (filteredCards != null && filteredCards.size() < totalLocations) {
            title.append(" (отфильтровано)");
        }

        filteredStatsTitle.setText(title.toString());
    }
}