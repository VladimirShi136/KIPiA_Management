package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.managers.PhotoManager;
import com.kipia.management.kipia_management.managers.PhotoViewer;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.application.Platform;
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
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

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
    private Label photoSectionLabel;
    @FXML
    private Label selectedPhotosLabel;
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

    // ---------- Отслеживание изменений формы ----------
    private String initialFormData;
    
    // ---------- Stage для закрытия через крестик ----------
    private Stage dialogStage;

    /**
     * Установка обратного вызова
     * @param onDeviceAdded - колбек
     */
    public void setOnDeviceAdded(Runnable onDeviceAdded) {
        this.onDeviceAdded = onDeviceAdded;
    }
    
    /**
     * Установка Stage диалога для обработки закрытия
     * @param stage - Stage диалога
     */
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
        
        // Добавляем обработчик закрытия окна через крестик
        stage.setOnCloseRequest(event -> {
            if (hasChanges()) {
                // Создаем кнопки для диалога
                ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.YES);
                ButtonType dontSaveButton = new ButtonType("Не сохранять", ButtonBar.ButtonData.NO);
                ButtonType cancelButton = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
                
                String message = editingDevice != null 
                    ? "В форме есть несохраненные изменения. Сохранить изменения перед закрытием?"
                    : "В форме есть несохраненные данные. Сохранить новый прибор перед закрытием?";
                
                Optional<ButtonType> result = CustomAlertDialog.showConfirmationWithOptions(
                    "Подтверждение закрытия",
                    message,
                    saveButton, dontSaveButton, cancelButton
                );
                
                if (result.isEmpty() || result.get() == cancelButton) {
                    // Пользователь отменил закрытие - предотвращаем закрытие окна
                    event.consume();
                    LOGGER.info("Пользователь отменил закрытие формы через крестик");
                } else if (result.get() == saveButton) {
                    // Пользователь выбрал сохранить
                    if (editingDevice != null) {
                        onSaveDevice();
                    } else {
                        onAddDevice();
                    }
                    // Если сохранение прошло успешно, окно закроется автоматически
                    // Если сохранение не удалось, предотвращаем закрытие
                    event.consume();
                } else if (result.get() == dontSaveButton) {
                    // Пользователь выбрал не сохранять - позволяем закрытие
                    LOGGER.info("Пользователь выбрал не сохранять изменения при закрытии через крестик");
                }
            }
        });
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

        // Включаем элементы для работы с фото при редактировании
        disablePhotoControls(false);

        // Сохраняем начальное состояние формы для отслеживания изменений
        saveInitialFormData();

        LOGGER.info("Форма переведена в режим редактирования: {}", device.getName());
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }

    /**
     * Включает или отключает элементы управления фото.
     * @param disable - true для отключения, false для включения
     */
    private void disablePhotoControls(boolean disable) {
        if (photoChooseBtn != null) {
            photoChooseBtn.setDisable(disable);
            photoChooseBtn.setVisible(!disable);
            photoChooseBtn.setManaged(!disable);
        }
        if (photoRemoveBtn != null) {
            photoRemoveBtn.setDisable(disable);
            photoRemoveBtn.setVisible(!disable);
            photoRemoveBtn.setManaged(!disable);
        }
        if (selectedPhotosListView != null) {
            selectedPhotosListView.setDisable(disable);
            selectedPhotosListView.setVisible(!disable);
            selectedPhotosListView.setManaged(!disable);
        }
        if (photoCounterLabel != null) {
            photoCounterLabel.setVisible(!disable);
            photoCounterLabel.setManaged(!disable);
        }
        if (photoSectionLabel != null) {
            photoSectionLabel.setVisible(!disable);
            photoSectionLabel.setManaged(!disable);
        }
        if (selectedPhotosLabel != null) {
            selectedPhotosLabel.setVisible(!disable);
            selectedPhotosLabel.setManaged(!disable);
        }
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

        // Включаем плавную анимацию стрелки и открытия popup для комбобоксов в форме
        StyleUtils.setupComboBoxArrowAnimation(statusComboBox);
        StyleUtils.setupComboBoxPopupAnimation(statusComboBox);
        StyleUtils.setupComboBoxArrowAnimation(locationField);
        StyleUtils.setupComboBoxPopupAnimation(locationField);

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
                    setStyle(null);
                } else {
                    setText((getIndex() + 1) + ". " + item);
                    setStyle("-fx-cursor: hand;");
                    
                    // Обработчик клика: одиночный - выделение, двойной - просмотр
                    setOnMouseClicked(event -> {
                        if (event.getClickCount() == 2 && editingDevice != null) {
                            // Двойной клик - просмотр фото
                            viewPhoto(item);
                        }
                        // Одиночный клик - стандартное поведение ListView (выделение)
                    });
                }
            }
        });

        // Настройка обработчиков
        photoChooseBtn.setOnAction(_ -> onChooseFiles());
        photoRemoveBtn.setOnAction(_ -> onRemovePhoto());

        // Отключаем элементы для работы с фото при создании прибора
        disablePhotoControls(true);

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
            if (editingDevice != null) {
                // Режим редактирования: сохраняем фото сразу через PhotoManager
                PhotoManager photoManager = PhotoManager.getInstance();
                
                for (File file : files) {
                    String fileName = file.getName();
                    if (!selectedPhotoFiles.contains(fileName)) {
                        try {
                            String storedFileName = photoManager.copyPhotoToStorageManual(file, editingDevice);
                            if (storedFileName != null) {
                                editingDevice.addPhoto(storedFileName);
                                selectedPhotoFiles.add(storedFileName);
                                LOGGER.info("Фото сохранено: {}", storedFileName);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Ошибка сохранения фото: {}", e.getMessage(), e);
                            CustomAlertDialog.showError("Ошибка", "Не удалось сохранить фото: " + fileName);
                        }
                    } else {
                        LOGGER.info("Файл уже в списке: {}", fileName);
                    }
                }
                
                // Обновляем прибор в БД
                try {
                    deviceDAO.updateDevice(editingDevice);
                    LOGGER.info("Прибор обновлён в БД после добавления фото");
                } catch (Exception e) {
                    LOGGER.error("Ошибка обновления прибора в БД: {}", e.getMessage(), e);
                }
            } else {
                // Режим создания: сохраняем временно
                for (File file : files) {
                    String fileName = file.getName();
                    if (!selectedPhotoFiles.contains(fileName)) {
                        selectedPhotoFiles.add(fileName);
                        pendingPhotoFiles.add(file);
                    } else {
                        LOGGER.info("Файл уже в списке: {}", fileName);
                    }
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

        // Проверка на занятость инвентарного номера мягко-удаленным прибором
        if (deviceDAO.findDeviceByInventoryNumberIncludingDeleted(data.inventoryNumber) != null) {
            CustomAlertDialog.showError("Ошибка", "Инвентарный номер занят ранее удаленным прибором. Используйте другой номер.");
            LOGGER.warn("Инвентарный номер занят мягко-удаленным прибором: {}", data.inventoryNumber);
            return;
        }

        // Создаём новый прибор
        Device device = new Device();
        createOrUpdateDevice(data.type, data.name, data.manufacturer, data.inventoryNumber, data.year, data.measurementLimit, data.accuracyClass, data.location, data.valveNumber, data.status, device);

        LOGGER.info("Попытка добавить прибор: {} (инв.: {})", data.name, data.inventoryNumber);

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

            // Проверка на занятость инвентарного номера мягко-удаленным прибором
            if (deviceDAO.findDeviceByInventoryNumberIncludingDeleted(data.inventoryNumber) != null) {
                CustomAlertDialog.showError("Ошибка", "Инвентарный номер занят ранее удаленным прибором. Используйте другой номер.");
                LOGGER.warn("Инвентарный номер занят мягко-удаленным прибором: {}", data.inventoryNumber);
                return;
            }
        }

        // Сохраняем старую локацию ДО изменения
        String oldLocation = editingDevice.getLocation();

        // Обновляем поля существующего прибора
        createOrUpdateDevice(data.type, data.name, data.manufacturer, data.inventoryNumber, data.year, data.measurementLimit, data.accuracyClass, data.location, data.valveNumber, data.status, editingDevice);

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
     * Просмотр выбранного фото в PhotoViewer.
     */
    private void viewPhoto(String photoFileName) {
        if (editingDevice == null) {
            LOGGER.warn("Попытка просмотра фото без редактируемого устройства");
            return;
        }
        
        try {
            PhotoManager photoManager = PhotoManager.getInstance();
            Stage stage = (Stage) selectedPhotosListView.getScene().getWindow();
            
            // Callback для обновления списка после удаления фото
            PhotoViewer.OnPhotoDeletedCallback onDeleted = (deletedDevice, deletedPhotoName) ->
                    Platform.runLater(() -> {
                        if (deletedDevice.getId() == editingDevice.getId()) {
                            selectedPhotoFiles.remove(deletedPhotoName);
                            LOGGER.info("Фото удалено из списка: {}", deletedPhotoName);
                        }
                    });
            
            photoManager.viewDevicePhotos(editingDevice, stage, onDeleted);
            
        } catch (Exception e) {
            LOGGER.error("Ошибка при просмотре фото: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка", "Не удалось открыть просмотр фото");
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
        
        // Сбрасываем начальное состояние
        initialFormData = null;
    }

    /**
     * Метод для отмены добавления прибора.
     */
    @FXML
    private void onCancel() {
        // Проверяем, были ли изменения в форме
        if (hasChanges()) {
            // Создаем кнопки для диалога
            ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.YES);
            ButtonType dontSaveButton = new ButtonType("Не сохранять", ButtonBar.ButtonData.NO);
            ButtonType cancelButton = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
            
            String message = editingDevice != null 
                ? "В форме есть несохраненные изменения. Сохранить изменения перед закрытием?"
                : "В форме есть несохраненные данные. Сохранить новый прибор перед закрытием?";
            
            Optional<ButtonType> result = CustomAlertDialog.showConfirmationWithOptions(
                "Подтверждение закрытия",
                message,
                saveButton, dontSaveButton, cancelButton
            );
            
            if (result.isEmpty() || result.get() == cancelButton) {
                // Пользователь отменил закрытие
                LOGGER.info("Пользователь отменил закрытие формы");
                return;
            } else if (result.get() == saveButton) {
                // Пользователь выбрал сохранить
                if (editingDevice != null) {
                    onSaveDevice();
                } else {
                    onAddDevice();
                }
                // Если сохранение прошло успешно, форма закроется автоматически
                return;
            } else if (result.get() == dontSaveButton) {
                // Пользователь выбрал не сохранять - просто закрываем
                LOGGER.info("Пользователь выбрал не сохранять изменения");
            }
        }
        
        // Закрываем форму
        Stage stage = (Stage) cancelBtn.getScene().getWindow();
        stage.close();
        LOGGER.info("Форма закрыта пользователем");
    }
    
    /**
     * Обновление иконок кнопок в зависимости от темы.
     */
    private void updateButtonIcons() {
        if (cancelBtn.getScene() == null) return;
        
        // Определяем текущую тему через StyleUtils
        boolean isDarkTheme = com.kipia.management.kipia_management.utils.StyleUtils.isDarkTheme();
        
        // Выбираем иконки в зависимости от темы
        String stopIcon = isDarkTheme ? "/images/stop-white.png" : "/images/stop-dark.png";
        String saveIcon = isDarkTheme ? "/images/save-white.png" : "/images/save-dark.png";
        
        LOGGER.debug("Обновление иконок: isDarkTheme={}, stopIcon={}, saveIcon={}", isDarkTheme, stopIcon, saveIcon);
        
        // Устанавливаем иконки с проверкой существования файлов
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
            try {
                InputStream iconStream = getClass().getResourceAsStream(icon);
                if (iconStream == null) {
                    LOGGER.warn("Иконка не найдена: {}", icon);
                    return;
                }
                javafx.scene.image.ImageView installIcon = new javafx.scene.image.ImageView(
                    new javafx.scene.image.Image(iconStream)
                );
                installIcon.setFitWidth(20);
            installIcon.setFitHeight(20);
            installIcon.setPreserveRatio(true);
            button.setGraphic(installIcon);
            } catch (Exception e) {
                LOGGER.error("Ошибка при установке иконки {}: {}", icon, e.getMessage());
            }
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

    /**
     * Сохраняет начальное состояние формы для отслеживания изменений.
     */
    private void saveInitialFormData() {
        initialFormData = getCurrentFormDataAsString();
    }

    /**
     * Проверяет, были ли изменения в форме.
     * @return true если есть изменения, false если нет
     */
    private boolean hasChanges() {
        if (initialFormData == null) {
            // Если начальное состояние не сохранено, проверяем есть ли данные
            return !getCurrentFormDataAsString().isEmpty();
        }
        return !initialFormData.equals(getCurrentFormDataAsString());
    }

    /**
     * Возвращает текущее состояние формы в виде строки для сравнения.
     * @return строковое представление данных формы
     */
    private String getCurrentFormDataAsString() {
        return String.join("|",
            nvl(typeField.getText()),
            nvl(nameField.getText()),
            nvl(manufacturerField.getText()),
            nvl(inventoryNumberField.getText()),
            nvl(yearField.getText()),
            nvl(measurementLimitField.getText()),
            nvl(accuracyClassField.getText()),
            nvl(locationField.getValue()),
            nvl(valveNumberField.getText()),
            nvl(statusComboBox.getValue()),
            nvl(additionalInfoField.getText()),
            String.join(",", selectedPhotoFiles)
        );
    }
}