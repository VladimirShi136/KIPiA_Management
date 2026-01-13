package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.CustomAlert;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.beans.binding.Bindings;
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

    // ---------- Кнопки -----------
    public Button cancelBtn;
    public Button addBtn;
    public Button photoRemoveBtn;

    // ---------- Список выбранных фото (имена файлов) ----------
    private final ObservableList<String> selectedPhotoFiles = FXCollections.observableArrayList();

    // ---------- Сервисы ----------
    private DeviceDAO deviceDAO;

    // ---------- Контроллеры ----------
    private SchemeEditorController schemeEditorController;

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
     * Метод для добавления нового прибора.
     */
    @FXML
    private void onAddDevice() {
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

            LOGGER.info("Прибор успешно добавлен: {}", name);
        } else {
            CustomAlert.showError("Ошибка добавления", "Не удалось добавить прибор в базу данных");
            LOGGER.error("Ошибка при добавлении прибора: {}", name);
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