package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.CustomAlert;
import com.kipia.management.kipia_management.utils.StyleUtils;
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
import java.util.List;

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
    private TextField locationField;
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
    private Label formTitleLabel;
    @FXML
    private Button deleteBtn;

    // ---------- Кнопки -----------
    public Button cancelBtn;
    public Button addBtn;
    public Button photoRemoveBtn;

    // ---------- Список выбранных фото (имена файлов) ----------
    private final ObservableList<String> selectedPhotoFiles = FXCollections.observableArrayList();

    // ---------- Сервисы ----------
    private DeviceDAO deviceDAO;

    // ---------- Режим редактирования ----------
    // null = режим добавления, non-null = режим редактирования
    private Device editingDevice = null;

    // ---------- Контроллеры ----------
    private SchemeEditorController schemeEditorController;

    // ---------- Колбэк после добавления (для обновления таблицы) ----------
    private Runnable onDeviceAdded;

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
            StyleUtils.applyHoverAndAnimation(deleteBtn, "button-delete", "button-delete-hover");
        }

        // Заполняем поля
        typeField.setText(nvl(device.getType()));
        nameField.setText(nvl(device.getName()));
        manufacturerField.setText(nvl(device.getManufacturer()));
        inventoryNumberField.setText(nvl(device.getInventoryNumber()));
        yearField.setText(device.getYear() != null ? String.valueOf(device.getYear()) : "");
        measurementLimitField.setText(nvl(device.getMeasurementLimit()));
        accuracyClassField.setText(device.getAccuracyClass() != null ? String.valueOf(device.getAccuracyClass()) : "");
        locationField.setText(nvl(device.getLocation()));
        valveNumberField.setText(nvl(device.getValveNumber()));
        additionalInfoField.setText(nvl(device.getAdditionalInfo()));

        if (device.getStatus() != null) {
            statusComboBox.setValue(device.getStatus());
        }

        // Заполняем список фото
        if (device.getPhotos() != null) {
            selectedPhotoFiles.setAll(device.getPhotos());
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
    }

    /**
     * Инициализация контроллера редактирования схемы.
     *
     * @param controller - контроллер
     */
    public void setSchemeEditorController(SchemeEditorController controller) {
        this.schemeEditorController = controller;
    }

    /**
     * Метод инициализации контроллера.
     */
    @FXML
    private void initialize() {
        // Инициализация ComboBox статусов
        statusComboBox.setItems(FXCollections.observableArrayList("Хранение", "В работе", "Утерян", "Испорчен"));
        statusComboBox.getSelectionModel().selectFirst();

        // Применение стилей к кнопкам
        if (addBtn != null) {
            StyleUtils.applyHoverAndAnimation(addBtn, "button-add", "button-add-hover");
        }
        if (cancelBtn != null) {
            StyleUtils.applyHoverAndAnimation(cancelBtn, "button-cancel", "button-cancel-hover");
        }
        if (photoChooseBtn != null) {
            StyleUtils.applyHoverAndAnimation(photoChooseBtn, "photo-choose-btn", "photo-choose-btn-hover");
        }
        if (photoRemoveBtn != null) {
            StyleUtils.applyHoverAndAnimation(photoRemoveBtn, "button-remove", "button-remove-hover");
        }

        // Настройка ListView для отображения выбранных фото
        selectedPhotosListView.setItems(selectedPhotoFiles);

        // Настройка счетчика фото через binding
        if (photoCounterLabel != null) {
            photoCounterLabel.textProperty().bind(
                    Bindings.createStringBinding(() ->
                                    "Выбрано файлов: " + selectedPhotoFiles.size(),
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
        String type = typeField.getText().trim();
        String name = nameField.getText().trim();
        String manufacturer = manufacturerField.getText().trim();
        String inventoryNumber = inventoryNumberField.getText().trim();
        String yearStr = yearField.getText().trim();
        Integer year = null;

        if (!yearStr.isEmpty()) {
            try {
                year = Integer.parseInt(yearStr);
            } catch (NumberFormatException e) {
                CustomAlert.showWarning("Валидация", "Год должен быть числом");
                LOGGER.warn("Ошибка валидации: год должен быть числом");
                return;
            }
        }

        String measurementLimit = measurementLimitField.getText().trim();
        String accuracyClassStr = accuracyClassField.getText().trim();
        Double accuracyClass = null;

        if (!accuracyClassStr.isEmpty()) {
            try {
                accuracyClass = Double.parseDouble(accuracyClassStr);
            } catch (NumberFormatException e) {
                CustomAlert.showWarning("Валидация", "Класс точности должен быть числом");
                LOGGER.warn("Ошибка валидации: класс точности должен быть числом");
                return;
            }
        }

        String location = locationField.getText().trim();
        String valveNumber = valveNumberField.getText().trim();
        String status = statusComboBox.getValue();

        // Валидация обязательных полей
        if (name.isEmpty() || type.isEmpty() || inventoryNumber.isEmpty() || location.isEmpty() || status == null) {
            CustomAlert.showWarning("Валидация", "Пожалуйста, заполните все обязательные поля");
            LOGGER.warn("Ошибка валидации: не все поля заполнены");
            return;
        }

        // Проверка уникальности инвентарного номера
        if (deviceDAO.findDeviceByInventoryNumber(inventoryNumber) != null) {
            CustomAlert.showError("Ошибка", "Прибор с таким инвентарным номером уже существует");
            LOGGER.warn("Инвентарный номер уже существует: {}", inventoryNumber);
            return;
        }

        // Создаём новый прибор
        Device device = new Device();
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

        // Добавляем выбранные фото
        for (String photoFileName : selectedPhotoFiles) {
            device.addPhoto(photoFileName);
        }

        LOGGER.info("Попытка добавить прибор: {} (инв.: {}), фото: {}", name, inventoryNumber, selectedPhotoFiles.size());

        // Сохраняем в DAO
        boolean success = deviceDAO.addDevice(device);
        if (success) {
            CustomAlert.showInfo("Добавление", "Прибор успешно добавлен!");
            clearForm();

            if (schemeEditorController != null) {
                schemeEditorController.refreshSchemesAndDevices();
            }

            // Уведомляем таблицу об обновлении и закрываем диалог
            if (onDeviceAdded != null) {
                onDeviceAdded.run();
            }

            // Закрываем диалог если открыт через FAB
            Stage stage = (Stage) addBtn.getScene().getWindow();
            if (stage.getOwner() != null) {
                stage.close();
            }

            LOGGER.info("Прибор успешно добавлен: {}", name);
        } else {
            CustomAlert.showError("Ошибка добавления", "Не удалось добавить прибор в базу данных");
            LOGGER.error("Ошибка при добавлении прибора: {}", name);
        }
    }

    /**
     * Сохранение изменений в режиме редактирования.
     */
    private void onSaveDevice() {
        String type = typeField.getText().trim();
        String name = nameField.getText().trim();
        String manufacturer = manufacturerField.getText().trim();
        String inventoryNumber = inventoryNumberField.getText().trim();
        String yearStr = yearField.getText().trim();
        Integer year = null;

        if (!yearStr.isEmpty()) {
            try {
                year = Integer.parseInt(yearStr);
            } catch (NumberFormatException e) {
                CustomAlert.showWarning("Валидация", "Год должен быть числом");
                return;
            }
        }

        String measurementLimit = measurementLimitField.getText().trim();
        String accuracyClassStr = accuracyClassField.getText().trim();
        Double accuracyClass = null;

        if (!accuracyClassStr.isEmpty()) {
            try {
                accuracyClass = Double.parseDouble(accuracyClassStr);
            } catch (NumberFormatException e) {
                CustomAlert.showWarning("Валидация", "Класс точности должен быть числом");
                return;
            }
        }

        String location = locationField.getText().trim();
        String valveNumber = valveNumberField.getText().trim();
        String status = statusComboBox.getValue();

        if (name.isEmpty() || type.isEmpty() || inventoryNumber.isEmpty() || location.isEmpty() || status == null) {
            CustomAlert.showWarning("Валидация", "Пожалуйста, заполните все обязательные поля");
            return;
        }

        // Проверка уникальности инвентарного номера (только если изменился)
        if (!inventoryNumber.equals(editingDevice.getInventoryNumber())) {
            if (deviceDAO.findDeviceByInventoryNumber(inventoryNumber) != null) {
                CustomAlert.showError("Ошибка", "Прибор с таким инвентарным номером уже существует");
                return;
            }
        }

        // Обновляем поля существующего прибора
        editingDevice.setType(type);
        editingDevice.setName(name);
        editingDevice.setManufacturer(manufacturer);
        editingDevice.setInventoryNumber(inventoryNumber);
        editingDevice.setMeasurementLimit(measurementLimit);
        editingDevice.setAccuracyClass(accuracyClass);
        editingDevice.setYear(year);
        editingDevice.setLocation(location);
        editingDevice.setValveNumber(valveNumber);
        editingDevice.setStatus(status);
        editingDevice.setAdditionalInfo(additionalInfoField.getText());
        editingDevice.setPhotos(new java.util.ArrayList<>(selectedPhotoFiles));
        editingDevice.updateTimestamp();

        boolean success = deviceDAO.updateDevice(editingDevice);
        if (success) {
            CustomAlert.showInfo("Сохранение", "Изменения успешно сохранены!");

            if (onDeviceAdded != null) onDeviceAdded.run();

            Stage stage = (Stage) addBtn.getScene().getWindow();
            stage.close();

            LOGGER.info("Прибор успешно обновлён: {}", editingDevice.getName());
        } else {
            CustomAlert.showError("Ошибка", "Не удалось сохранить изменения");
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

        java.util.Optional<javafx.scene.control.ButtonType> result =
                CustomAlert.showConfirmationWithOptions(title, message,
                        CustomAlert.YES_BUTTON, CustomAlert.NO_BUTTON, CustomAlert.CANCEL_BUTTON);

        if (result.isEmpty() || result.get() == CustomAlert.CANCEL_BUTTON) return;

        boolean shouldDeletePhotos = result.get() == CustomAlert.YES_BUTTON;

        if (shouldDeletePhotos) {
            com.kipia.management.kipia_management.managers.PhotoManager.getInstance()
                    .deleteAllDevicePhotos(editingDevice);
        }

        boolean ok = deviceDAO.deleteDevice(editingDevice.getId());
        if (ok) {
            CustomAlert.showInfo("Удаление", "Прибор успешно удалён");

            if (onDeviceAdded != null) onDeviceAdded.run();

            Stage stage = (Stage) addBtn.getScene().getWindow();
            stage.close();

            LOGGER.info("Прибор удалён из формы редактирования: {}", editingDevice.getName());
        } else {
            CustomAlert.showError("Ошибка", "Не удалось удалить прибор");
        }
    }

    /**
     * Удалить выбранное фото из списка.
     */
    @FXML
    private void onRemovePhoto() {
        int selectedIndex = selectedPhotosListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < selectedPhotoFiles.size()) {
            String removedPhoto = selectedPhotoFiles.remove(selectedIndex);
            LOGGER.info("Удалено фото из списка: {}", removedPhoto);
        } else {
            CustomAlert.showInfo("Удаление фото", "Выберите фото для удаления из списка");
        }
    }

    /**
     * Метод для очистки формы.
     */
    private void clearForm() {
        nameField.clear();
        typeField.clear();
        inventoryNumberField.clear();
        locationField.clear();
        valveNumberField.clear();
        manufacturerField.clear();
        yearField.clear();
        accuracyClassField.clear();
        measurementLimitField.clear();
        additionalInfoField.clear();
        statusComboBox.getSelectionModel().selectFirst();
        selectedPhotoFiles.clear();
    }

    /**
     * Метод для отмены добавления прибора.
     */
    @FXML
    private void onCancel() {
        clearForm();
        LOGGER.info("Добавление прибора отменено, форма очищена");
    }
}