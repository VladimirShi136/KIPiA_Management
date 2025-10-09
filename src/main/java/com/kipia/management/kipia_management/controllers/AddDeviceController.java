package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Контроллер для формы добавления нового прибора.
 *
 * @author vladimir_shi
 * @since 29.08.2025
 */

public class AddDeviceController {

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
    private TextField valveNumberField;  // Добавлено поле для "Кран №"
    @FXML
    private ComboBox<String> statusComboBox;
    @FXML
    private TextArea additionalInfoField;
    @FXML
    private TextField photoPathField;  // Текст для одного фото (опционально)
    @FXML
    private ListView<String> selectedPhotosListView;  // Список выбранных фото
    @FXML
    private Button photoChooseBtn;  // Кнопка выбора файла
    @FXML
    private Label messageLabel;

    // ---------- Кнопки -----------
    public Button cancelBtn;
    public Button addBtn;

    // ---------- Список выбранных фото ----------
    private final List<String> selectedPhotos = new ArrayList<>();

    // ---------- Сервисы ----------
    private DeviceDAO deviceDAO;

    // ---------- Контроллеры ----------
    private SchemeEditorController schemeEditorController;

    /**
     * Инициализация сервиса DAO.
     * @param deviceDAO - сервис DAO
     */
    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
    }

    /**
     *  Инициализация контроллера редактирования схемы.
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
        statusComboBox.setItems(FXCollections.observableArrayList("Хранение", "В работе", "Утерян", "Испорчен"));
        statusComboBox.getSelectionModel().selectFirst();
        messageLabel.setText("");

        if (addBtn != null) {
            StyleUtils.applyHoverAndAnimation(addBtn, "button-add", "button-add-hover");
        }
        if (cancelBtn != null) {
            StyleUtils.applyHoverAndAnimation(cancelBtn, "button-cancel", "button-cancel-hover");
        }
        if (photoChooseBtn != null) {
            StyleUtils.applyHoverAndAnimation(photoChooseBtn, "photo-choose-btn", "photo-choose-btn-hover");
        }

        // Настройка списка фото (без редактирования)
        selectedPhotosListView.setItems(FXCollections.observableArrayList(selectedPhotos));
        selectedPhotosListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText("Фото: " + new File(item).getName());  // Краткое название файла
                }
            }
        });

        // ИСПОЛЬЗОВАНИЕ photoChooseBtn: Установка обработчика на кнопку для выбора фото
        photoChooseBtn.setOnAction(event -> onChooseFiles());  // Убираем @FXML из метода, используем прямой вызов
    }

    /**
     * Обработчик нажатия на кнопку выбора фото.
     */
    @FXML
    private void onChooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выбрать фото для прибора");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.gif"));
        Stage stage = (Stage) messageLabel.getScene().getWindow();
        chooser.setSelectedExtensionFilter(chooser.getExtensionFilters().getFirst());  // Значение по умолчанию

        // Включить выбор нескольких файлов
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            for (File file : files) {
                selectedPhotos.add(file.getAbsolutePath());
            }
            selectedPhotosListView.setItems(FXCollections.observableArrayList(selectedPhotos));  // Обновить список
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
                messageLabel.setText("Год должен быть числом");
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
                messageLabel.setText("Класс точности должен быть числом");
                return;
            }
        }
        String location = locationField.getText().trim();
        String valveNumber = valveNumberField.getText().trim();
        String status = statusComboBox.getValue();

        if (name.isEmpty() || type.isEmpty() || inventoryNumber.isEmpty() || location.isEmpty() || status == null) {
            messageLabel.setText("Пожалуйста, заполните все поля");
            return;
        }

        // Проверка уникальности инвентарного номера
        if (deviceDAO.findDeviceByInventoryNumber(inventoryNumber) != null) {
            messageLabel.setText("Прибор с таким инвентарным номером уже существует");
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

        // Добавляем фото из списка
        for (String photoPath : selectedPhotos) {
            device.addPhoto(photoPath);
        }

        // Сохраняем первое фото в старое поле для совместимости (опционально)
        if (!selectedPhotos.isEmpty()) {
            device.setPhotoPath(selectedPhotos.getFirst());
        }

        // Сохраняем в DAO
        boolean success = deviceDAO.addDevice(device);
        if (success) {
            messageLabel.setStyle("-fx-text-fill: green;");
            messageLabel.setText("Прибор успешно добавлен");
            clearForm();
            if (schemeEditorController != null) {
                schemeEditorController.refreshSchemesAndDevices();
            }
        } else {
            messageLabel.setStyle("-fx-text-fill: red;");
            messageLabel.setText("Ошибка при добавлении прибора");
        }
    }

    /**
     * Метод для очистки списка фото.
     */
    private void clearPhotos() {
        selectedPhotos.clear();
        selectedPhotosListView.setItems(FXCollections.observableArrayList());
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
        photoPathField.clear();
        clearPhotos();  // Очищаем список фото
    }

    /**
     * Метод для отмены добавления прибора.
     */
    @FXML
    private void onCancel() {
        clearForm();
        messageLabel.setText("");
    }
}