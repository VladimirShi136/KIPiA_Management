package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

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
    public Button cancelBtn;
    public Button addBtn;
    @FXML
    private TextField nameField;
    @FXML
    private TextField typeField;
    @FXML
    private TextField manufacturerField;  // Из предыдущих обновлений
    @FXML
    private TextField inventoryNumberField;
    @FXML
    private TextField yearField;
    @FXML
    private TextField locationField;
    @FXML
    private ComboBox<String> statusComboBox;
    @FXML
    private TextArea additionalInfoField;
    @FXML
    private TextField photoPathField;  // Текст для одного фото (опционально)
    @FXML
    private ListView<String> selectedPhotosListView;  // Новое: список выбранных фото
    @FXML
    private Button photoChooseBtn;  // Кнопка выбора файла
    @FXML
    private Label messageLabel;

    private DeviceDAO deviceDAO;
    private List<String> selectedPhotos = new ArrayList<>();  // Список путей выбранных фото

    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
    }

    private void applyHoverAndAnimation(Button button, String defaultColor, String hoverColor, String buttonType) {
        // Аналогично методу в дерево MainController
        button.setStyle(
                "-fx-background-color: " + defaultColor + "; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-background-radius: 5; " +
                        "-fx-border-radius: 5; " +
                        "-fx-padding: 10 20 10 20; " +  // Чуть больше padding для формы
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 3, 0, 0, 1); " +
                        "-fx-cursor: hand;"
        );

        button.setOnMouseEntered(e -> {
            button.setStyle(button.getStyle().replace(defaultColor, hoverColor));
            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), button);
            fadeIn.setFromValue(0.8);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        button.setOnMouseExited(e -> {
            button.setStyle(button.getStyle().replace(hoverColor, defaultColor));
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), button);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.8);
            fadeOut.play();
        });
    }

    @FXML
    private void initialize() {
        statusComboBox.setItems(FXCollections.observableArrayList("В работе", "На ремонте", "Списан"));
        statusComboBox.getSelectionModel().selectFirst();
        messageLabel.setText("");

        if (addBtn != null) applyHoverAndAnimation(addBtn, "#2ecc71", "#58d68d", "form");  // Зеленый для "Добавить"
        if (cancelBtn != null) applyHoverAndAnimation(cancelBtn, "#e74c3c", "#ec7063", "form");  // Красный для "Отмена"

        // Настройка списка фото (без редактирования)
        selectedPhotosListView.setItems(FXCollections.observableArrayList(selectedPhotos));
        selectedPhotosListView.setCellFactory(param -> new ListCell<String>() {
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

    // Новый метод: Обработчик кнопки выбора фото
    @FXML
    private void onChooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выбрать фото для прибора");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.gif"));
        Stage stage = (Stage) messageLabel.getScene().getWindow();
        chooser.setSelectedExtensionFilter(chooser.getExtensionFilters().get(0));  // Значение по умолчанию

        // Включить выбор нескольких файлов
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            for (File file : files) {
                selectedPhotos.add(file.getAbsolutePath());
            }
            selectedPhotosListView.setItems(FXCollections.observableArrayList(selectedPhotos));  // Обновить список
        }
    }

    @FXML
    private void onAddDevice() {
        // Получаем данные из полей
        String name = nameField.getText().trim();
        String type = typeField.getText().trim();
        String inventoryNumber = inventoryNumberField.getText().trim();
        String location = locationField.getText().trim();
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
        device.setName(name);
        device.setType(type);
        device.setInventoryNumber(inventoryNumber);
        device.setLocation(location);
        device.setStatus(status);

        // Заполняем дополнительные поля
        if (!manufacturerField.getText().trim().isEmpty()) {
            device.setManufacturer(manufacturerField.getText().trim());
        }

        if (!yearField.getText().trim().isEmpty()) {
            try {
                device.setYear(Integer.parseInt(yearField.getText()));
            } catch (NumberFormatException e) {
                messageLabel.setText("Год выпуска должен быть числом");
                return;
            }
        }

        device.setAdditionalInfo(additionalInfoField.getText());

        // Добавляем фото из списка (новая функция)
        for (String photoPath : selectedPhotos) {
            device.addPhoto(photoPath);
        }

        // Сохраняем первое фото в старое поле для совместимости (опционально)
        if (!selectedPhotos.isEmpty()) {
            device.setPhotoPath(selectedPhotos.get(0));
        }

        // Сохраняем в DAO
        boolean success = deviceDAO.addDevice(device);
        if (success) {
            messageLabel.setStyle("-fx-text-fill: green;");
            messageLabel.setText("Прибор успешно добавлен");
            clearForm();
        } else {
            messageLabel.setStyle("-fx-text-fill: red;");
            messageLabel.setText("Ошибка при добавлении прибора");
        }
    }

    // Метод очистки списка фото (добавь в clearForm)
    private void clearPhotos() {
        selectedPhotos.clear();
        selectedPhotosListView.setItems(FXCollections.observableArrayList());
    }

    private void clearForm() {
        nameField.clear();
        typeField.clear();
        inventoryNumberField.clear();
        locationField.clear();
        yearField.clear();
        additionalInfoField.clear();
        statusComboBox.getSelectionModel().selectFirst();
        photoPathField.clear();
        clearPhotos();  // Очищаем список фото
    }

    @FXML
    private void onCancel() {
        clearForm();
        messageLabel.setText("");
        // Опционально: Закрыть окно (если диалог) ((Stage) messageLabel.getScene().getWindow()).close();
    }
}