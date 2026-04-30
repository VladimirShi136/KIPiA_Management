package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.managers.PhotoManager;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Контроллер для формы добавления нового прибора.
 *
 * @author vladimir_shi
 * @since 29.08.2025
 */
public class AddDeviceController {
    // ----- логгер для сообщений --------
    private static final Logger LOGGER = LogManager.getLogger(AddDeviceController.class);

    // ---------- FXML‑элементы ----------
    @FXML
    private TextField nameField;
    @FXML
    private TextField typeField;
    @FXML
    private TextField manufacturerField;
    @FXML
    private TextField inventoryNumberField;
    @FXML
    private TextField yearField;
    @FXML
    private TextField measurementLimitField;
    @FXML
    private TextField accuracyClassField;
    @FXML
    private ComboBox<String> locationField;
    @FXML
    private TextField valveNumberField;
    @FXML
    private ComboBox<String> statusComboBox;
    @FXML
    private TextArea additionalInfoField;
    @FXML
    private ListView<String> selectedPhotosListView;
    @FXML
    private Button photoChooseBtn;
    @FXML
    private Label photoCounterLabel;
    @FXML
    private Label updatedAtLabel;

    @FXML
    private Label formTitleLabel;
    @FXML
    private Button deleteBtn;

    // ---------- Кнопки -----------
    public Button cancelBtn;
    public Button addBtn;
    public Button photoRemoveBtn;

    // ---------- Список выбранных фото (имена файлов) ----------
    private final ObservableList<String> selectedPhotoFiles = FXCollections.observableArrayList();
    
    // ---------- Список выбранных файлов для копирования ---------
    private final java.util.List<File> pendingPhotoFiles = new java.util.ArrayList<>();

    // ---------- Сервисы ----------
    private DeviceDAO deviceDAO;

    // ---------- Режим редактирования ----------
    // null = режим добавления, non-null = режим редактирования
    private Device editingDevice = null;

    // ---------- Колбэк после добавления (для обновления таблицы) ----------
    private Runnable onDeviceAdded;

    /**
     * Установка обратного вызова
     * @param onDeviceAdded - колбек
     */
    public void setOnDeviceAdded(Runnable onDeviceAdded) {
        this.onDeviceAdded = onDeviceAdded;
    }

    /**
     * Переводит форму в режим редактирования: заполняет поля данными
     * существующего прибора и показывает кнопку "Удалить".
     */
    public void setEditMode(Device device) {
        this.editingDevice = device;

        // Меняем заголовок и текст кнопки
        if (formTitleLabel != null) formTitleLabel.setText("Редактирование прибора");
        if (addBtn != null) addBtn.setText("Сохранить");

        // Показываем кнопку удаления
        if (deleteBtn != null) {
            deleteBtn.setVisible(true);
            deleteBtn.setManaged(true);
        }

        // Заполняем поля
        typeField.setText(nvl(device.getType()));
        nameField.setText(nvl(device.getName()));
        manufacturerField.setText(nvl(device.getManufacturer()));
        inventoryNumberField.setText(nvl(device.getInventoryNumber()));
        yearField.setText(device.getYear() != null ? String.valueOf(device.getYear()) : "");
        measurementLimitField.setText(nvl(device.getMeasurementLimit()));
        accuracyClassField.setText(device.getAccuracyClass() != null ? String.valueOf(device.getAccuracyClass()) : "");
        locationField.setValue(nvl(device.getLocation()));
        valveNumberField.setText(nvl(device.getValveNumber()));
        additionalInfoField.setText(nvl(device.getAdditionalInfo()));

        if (device.getStatus() != null) {
            statusComboBox.setValue(device.getStatus());
        }

        // Заполняем список фото
        if (device.getPhotos() != null) {
            selectedPhotoFiles.setAll(device.getPhotos());
        }

        if (device.getUpdatedAt() > 0) {
            updatedAtLabel.setText(new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                    .format(new Date(device.getUpdatedAt())));
        }

        LOGGER.info("Форма переведена в режим редактирования: {}", device.getName());
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }

    /**
     * Инициализация сервиса DAO.
     *
     * @param deviceDAO - сервис DAO
     */
    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
        // Загружаем список локаций
        loadLocations();
    }
    
    /**
     * Загрузка списка уникальных локаций из БД
     */
    private void loadLocations() {
        if (deviceDAO != null && locationField != null) {
            try {
                List<String> locations = deviceDAO.getDistinctLocations();
                locationField.setItems(FXCollections.observableArrayList(locations));
                LOGGER.info("✅ Загружено {} уникальных локаций", locations.size());
            } catch (Exception e) {
                LOGGER.error("❌ Ошибка загрузки локаций: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Метод инициализации контроллера.
     */
    @FXML
    private void initialize() {
        // Инициализация ComboBox статусов
        statusComboBox.setItems(FXCollections.observableArrayList("Хранение", "В работе", "Утерян", "Испорчен"));
        statusComboBox.getSelectionModel().selectFirst();

        // Инициализация ComboBox локаций (загрузка позже через setDeviceDAO)
        locationField.setEditable(true);
        
        // Установка иконок в зависимости от темы (после добавления в сцену)
        cancelBtn.sceneProperty().addListener((_, _, newScene) -> {
            if (newScene != null) {
                updateButtonIcons();
                // Добавляем listener на изменение стилей сцены для автоматического обновления иконок
                newScene.getStylesheets().addListener((javafx.collections.ListChangeListener<String>) _ -> updateButtonIcons());
            }
        });

        // Настройка ListView для отображения выбранных фото
        selectedPhotosListView.setItems(selectedPhotoFiles);

        // Настройка счетчика фото через binding
        if (photoCounterLabel != null) {
            photoCounterLabel.textProperty().bind(
                    Bindings.createStringBinding(() ->
                                    "Выбрано файлов: " + selectedPhotoFiles.size() + "/" + PhotoManager.MAX_PHOTOS_PER_DEVICE,
                            selectedPhotoFiles
                    )
            );
        }

        selectedPhotosListView.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText((getIndex() + 1) + ". " + item);
                }
            }
        });

        // Настройка обработчиков
        photoChooseBtn.setOnAction(_ -> onChooseFiles());
        photoRemoveBtn.setOnAction(_ -> onRemovePhoto());

        LOGGER.info("Форма добавления прибора инициализирована");
    }

    /**
     * Обработчик нажатия на кнопку выбора фото.
     */
    @FXML
    private void onChooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выбрать фото для прибора");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp")
        );

        Stage stage = (Stage) photoChooseBtn.getScene().getWindow();
        List<File> files = chooser.showOpenMultipleDialog(stage);

        if (files != null && !files.isEmpty()) {
            for (File file : files) {
                String fileName = file.getName();
                if (!selectedPhotoFiles.contains(fileName)) {
                    selectedPhotoFiles.add(fileName);
                    // Сохраняем физический файл для последующего копирования
                    pendingPhotoFiles.add(file);
                } else {
                    LOGGER.info("Файл уже в списке: {}", fileName);
                }
            }
            LOGGER.info("Выбрано {} фото для прибора", files.size());
        } else {
            LOGGER.info("Пользователь отменил выбор фото");
        }
    }

    /**
     * Добавление нового прибора или сохранение изменений (зависит от editingDevice).
     */
    @FXML
    private void onAddDevice() {
        if (editingDevice != null) {
            onSaveDevice();
            return;
        }
        // Получаем данные из полей
        DeviceFormData data = extractFormData();
        if (data == null) {
            return;
        }

        // Валидация обязательных полей
        if (data.name.isEmpty() || data.type.isEmpty() || data.inventoryNumber.isEmpty() || data.location.isEmpty() || data.status == null) {
            CustomAlertDialog.showWarning("Валидация", "Пожалуйста, заполните все обязательные поля");
            LOGGER.warn("Ошибка валидации: не все поля заполнены");
            return;
        }

        // Проверка уникальности инвентарного номера
        if (deviceDAO.findDeviceByInventoryNumber(data.inventoryNumber) != null) {
            CustomAlertDialog.showError("Ошибка", "Прибор с таким инвентарным номером уже существует");
            LOGGER.warn("Инвентарный номер уже существует: {}", data.inventoryNumber);
            return;
        }

        // Создаём новый прибор
        Device device = new Device();
        createOrUpdateDevice(data.type, data.name, data.manufacturer, data.inventoryNumber, data.year, data.measurementLimit, data.accuracyClass, data.location, data.valveNumber, data.status, device);

        // Добавляем выбранные фото
        for (String photoFileName : selectedPhotoFiles) {
            device.addPhoto(photoFileName);
        }

        LOGGER.info("Попытка добавить прибор: {} (инв.: {}), фото: {}", data.name, data.inventoryNumber, selectedPhotoFiles.size());

        // Сохраняем в DAO
        boolean success = deviceDAO.addDevice(device);
        if (success) {
            CustomAlertDialog.showInfo("Добавление", "Прибор успешно добавлен!");
            clearForm();

            // Уведомляем таблицу об обновлении и закрываем диалог
            if (onDeviceAdded != null) {
                onDeviceAdded.run();
            }

            // Закрываем диалог если открыт через FAB
            Stage stage = (Stage) addBtn.getScene().getWindow();
            if (stage.getOwner() != null) {
                stage.close();
            }

            LOGGER.info("Прибор успешно добавлен: {}", data.name);
        } else {
            CustomAlertDialog.showError("Ошибка добавления", "Не удалось добавить прибор в базу данных");
            LOGGER.error("Ошибка при добавлении прибора: {}", data.name);
        }
    }

    /**
     * Сохранение изменений в режиме редактирования.
     */
    private void onSaveDevice() {
        DeviceFormData data = extractFormData();
        if (data == null) {
            return;
        }

        if (data.name.isEmpty() || data.type.isEmpty() || data.inventoryNumber.isEmpty() || data.location.isEmpty() || data.status == null) {
            CustomAlertDialog.showWarning("Валидация", "Пожалуйста, заполните все обязательные поля");
            return;
        }

        // Проверка уникальности инвентарного номера (только если изменился)
        if (!data.inventoryNumber.equals(editingDevice.getInventoryNumber())) {
            if (deviceDAO.findDeviceByInventoryNumber(data.inventoryNumber) != null) {
                CustomAlertDialog.showError("Ошибка", "Прибор с таким инвентарным номером уже существует");
                return;
            }
        }

        // Сохраняем старую локацию ДО изменения
        String oldLocation = editingDevice.getLocation();

        // Обновляем поля существующего прибора
        createOrUpdateDevice(data.type, data.name, data.manufacturer, data.inventoryNumber, data.year, data.measurementLimit, data.accuracyClass, data.location, data.valveNumber, data.status, editingDevice);

        // Копируем новые фото через PhotoManager
        if (!pendingPhotoFiles.isEmpty()) {
            LOGGER.info("📸 Копирование {} новых фото в локацию '{}'", pendingPhotoFiles.size(), data.location);
            
            for (File photoFile : pendingPhotoFiles) {
                try {
                    String storedFileName = PhotoManager.getInstance()
                            .copyPhotoToStorageManual(photoFile, editingDevice);
                    
                    if (storedFileName != null) {
                        editingDevice.addPhoto(storedFileName);
                        LOGGER.info("✅ Фото скопировано: {}", storedFileName);
                    } else {
                        LOGGER.warn("⚠️ Не удалось скопировать фото: {}", photoFile.getName());
                    }
                } catch (Exception e) {
                    LOGGER.error("❌ Ошибка копирования фото: {}", e.getMessage(), e);
                }
            }
            
            pendingPhotoFiles.clear();
        }

        // Мигрируем фото если локация изменилась
        if (!data.location.equals(oldLocation)) {
            int migratedCount = PhotoManager.getInstance()
                    .migratePhotosToNewLocation(editingDevice, oldLocation);

            if (migratedCount > 0) {
                LOGGER.info("📸 Перемещено {} фото в новую локацию '{}'", migratedCount, data.location);
            }
        }

        boolean success = deviceDAO.updateDevice(editingDevice);
        if (success) {
            CustomAlertDialog.showInfo("Сохранение", "Изменения успешно сохранены!");

            if (onDeviceAdded != null) onDeviceAdded.run();

            Stage stage = (Stage) addBtn.getScene().getWindow();
            stage.close();

            LOGGER.info("Прибор успешно обновлён: {}", editingDevice.getName());
        } else {
            CustomAlertDialog.showError("Ошибка", "Не удалось сохранить изменения");
            LOGGER.error("Ошибка при сохранении прибора: {}", editingDevice.getName());
        }
    }

    /**
     * Удаление прибора из формы редактирования.
     */
    @FXML
    private void onDeleteDevice() {
        if (editingDevice == null) return;

        String title = "Подтверждение удаления";
        String message = "Удалить прибор \"" + editingDevice.getName() + "\"?\n" +
                "ДА - удалить вместе с привязанными фото.\n" +
                "НЕТ - удалить только прибор.\n" +
                "Отмена - отменить действие.";

        Optional<ButtonType> result =
                CustomAlertDialog.showConfirmationWithOptions(title, message,
                        CustomAlertDialog.YES_BUTTON, CustomAlertDialog.NO_BUTTON, CustomAlertDialog.CANCEL_BUTTON);

        if (result.isEmpty() || result.get() == CustomAlertDialog.CANCEL_BUTTON) return;

        boolean shouldDeletePhotos = result.get() == CustomAlertDialog.YES_BUTTON;

        if (shouldDeletePhotos) {
            PhotoManager.getInstance()
                    .deleteAllDevicePhotos(editingDevice);
        }

        boolean ok = deviceDAO.deleteDevice(editingDevice.getId());
        if (ok) {
            CustomAlertDialog.showInfo("Удаление", "Прибор успешно удалён");

            if (onDeviceAdded != null) onDeviceAdded.run();

            Stage stage = (Stage) addBtn.getScene().getWindow();
            stage.close();

            LOGGER.info("Прибор удалён из формы редактирования: {}", editingDevice.getName());
        } else {
            CustomAlertDialog.showError("Ошибка", "Не удалось удалить прибор");
        }
    }

    /**
     * Удалить выбранное фото из списка.
     */
    @FXML
    private void onRemovePhoto() {
        int selectedIndex = selectedPhotosListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= selectedPhotoFiles.size()) {
            CustomAlertDialog.showInfo("Удаление фото", "Выберите фото для удаления из списка");
            return;
        }
        
        String removedPhoto = selectedPhotoFiles.get(selectedIndex);
        
        // ⭐⭐ НОВОЕ: Если в режиме редактирования - удаляем физический файл ⭐⭐
        if (editingDevice != null) {
            boolean confirm = CustomAlertDialog.showConfirmation(
                "Удаление фото",
                "Удалить фото \"" + removedPhoto + "\"?\n\nФайл будет удалён с диска."
            );
            
            if (!confirm) {
                return;
            }
            
            // Удаляем физический файл через PhotoManager
            boolean deleted = PhotoManager.getInstance()
                    .deletePhoto(editingDevice, removedPhoto);
            
            if (deleted) {
                selectedPhotoFiles.remove(selectedIndex);
                LOGGER.info("✅ Фото удалено: {}", removedPhoto);
                CustomAlertDialog.showSuccess("Удаление", "Фото успешно удалено");
            } else {
                CustomAlertDialog.showError("Ошибка", "Не удалось удалить фото");
                LOGGER.error("❌ Не удалось удалить фото: {}", removedPhoto);
            }
        } else {
            // Режим добавления - просто удаляем из списков
            selectedPhotoFiles.remove(selectedIndex);
            pendingPhotoFiles.removeIf(file -> file.getName().equals(removedPhoto));
            LOGGER.info("Удалено фото из списка: {}", removedPhoto);
        }
    }

    /**
     * Внутренний класс для хранения данных из формы.
     */
    private static class DeviceFormData {
        String type;
        String name;
        String manufacturer;
        String inventoryNumber;
        Integer year;
        String measurementLimit;
        Double accuracyClass;
        String location;
        String valveNumber;
        String status;
    }

    /**
     * Извлекает данные из полей формы.
     * @return DeviceFormData с данными из формы или null при ошибке валидации
     */
    private DeviceFormData extractFormData() {
        DeviceFormData data = new DeviceFormData();
        data.type = typeField.getText().trim();
        data.name = nameField.getText().trim();
        data.manufacturer = manufacturerField.getText().trim();
        data.inventoryNumber = inventoryNumberField.getText().trim();
        data.year = null;

        String yearStr = yearField.getText().trim();
        if (!yearStr.isEmpty()) {
            try {
                data.year = Integer.parseInt(yearStr);
            } catch (NumberFormatException e) {
                CustomAlertDialog.showWarning("Валидация", "Год должен быть числом");
                LOGGER.warn("Ошибка валидации: год должен быть числом");
                return null;
            }
        }

        data.measurementLimit = measurementLimitField.getText().trim();
        data.accuracyClass = null;

        String accuracyClassStr = accuracyClassField.getText().trim();
        if (!accuracyClassStr.isEmpty()) {
            try {
                data.accuracyClass = Double.parseDouble(accuracyClassStr);
            } catch (NumberFormatException e) {
                CustomAlertDialog.showWarning("Валидация", "Класс точности должен быть числом");
                LOGGER.warn("Ошибка валидации: класс точности должен быть числом");
                return null;
            }
        }

        data.location = locationField.getValue() != null ? locationField.getValue().trim() : "";
        data.valveNumber = valveNumberField.getText().trim();
        data.status = statusComboBox.getValue();

        return data;
    }

    /**
     * Метод для очистки формы.
     */
    private void clearForm() {
        nameField.clear();
        typeField.clear();
        inventoryNumberField.clear();
        locationField.setValue(null);
        valveNumberField.clear();
        manufacturerField.clear();
        yearField.clear();
        accuracyClassField.clear();
        measurementLimitField.clear();
        additionalInfoField.clear();
        statusComboBox.getSelectionModel().selectFirst();
        selectedPhotoFiles.clear();
        pendingPhotoFiles.clear();
    }

    /**
     * Метод для отмены добавления прибора.
     */
    @FXML
    private void onCancel() {
        // Просто закрываем форму без очистки
        Stage stage = (Stage) cancelBtn.getScene().getWindow();
        stage.close();
        LOGGER.info("Форма закрыта пользователем");
    }
    
    /**
     * Обновление иконок кнопок в зависимости от темы.
     */
    private void updateButtonIcons() {
        if (cancelBtn.getScene() == null) return;
        
        // Определяем текущую тему - проверяем стили СЦЕНЫ, а не корневого элемента
        boolean isDarkTheme = cancelBtn.getScene().getStylesheets().stream()
                .anyMatch(s -> s.contains("dark-theme.css"));
        
        // Выбираем иконки в зависимости от темы
        String stopIcon = isDarkTheme ? "/images/stop-white.png" : "/images/stop-dark.png";
        String saveIcon = isDarkTheme ? "/images/save-white.png" : "/images/save-dark.png";
        
        LOGGER.debug("Обновление иконок: isDarkTheme={}, stopIcon={}, saveIcon={}", isDarkTheme, stopIcon, saveIcon);
        
        // Устанавливаем иконки
        installSuitableIcon(stopIcon, cancelBtn);
        installSuitableIcon(saveIcon, addBtn);
    }

    /**
     * Вспомогательный метод для установки подходящей иконки
     * @param icon - иконка
     * @param button - кнопка
     */
    private void installSuitableIcon(String icon, Button button) {
        if (button != null) {
            javafx.scene.image.ImageView installIcon = new javafx.scene.image.ImageView(
                new javafx.scene.image.Image(Objects.requireNonNull(getClass().getResourceAsStream(icon)))
            );
            installIcon.setFitWidth(20);
            installIcon.setFitHeight(20);
            installIcon.setPreserveRatio(true);
            button.setGraphic(installIcon);
        }
    }

    /**
     * Вспомогательный метод для создания или обновления прибора.
     *
     * @param type - тип
     * @param name - название
     * @param manufacturer - производитель
     * @param inventoryNumber - инв.№
     * @param year - год выпуска
     * @param measurementLimit - предел измерений
     * @param accuracyClass - класс точности
     * @param location - локация
     * @param valveNumber - № крана
     * @param status - статус
     * @param device - устройство
     */
    private void createOrUpdateDevice(String type, String name, String manufacturer, String inventoryNumber, Integer year, String measurementLimit, Double accuracyClass, String location, String valveNumber, String status, Device device) {
        device.setType(type);
        device.setName(name);
        device.setManufacturer(manufacturer);
        device.setInventoryNumber(inventoryNumber);
        device.setMeasurementLimit(measurementLimit);
        device.setAccuracyClass(accuracyClass);
        device.setYear(year);
        device.setLocation(location);
        device.setValveNumber(valveNumber);
        device.setStatus(status);
        device.setAdditionalInfo(additionalInfoField.getText());
        device.updateTimestamp();
    }
}