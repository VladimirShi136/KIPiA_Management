package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.Objects;

/**
 *
 * Контроллер для формы добавления нового прибора.
 *
 * @author vladimir_shi
 * @since 29.08.2025
 */

public class AddDeviceController {

    @FXML
    private TextField nameField;

    @FXML
    private TextField typeField;

    @FXML
    private TextField inventoryNumberField;

    @FXML
    private TextField locationField;

    @FXML
    private ComboBox<String> statusComboBox;

    @FXML
    private Label messageLabel;

    private DeviceDAO deviceDAO;

    // Метод для внедрения DAO из MainController
    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
    }

    @FXML
    private void initialize() {
        statusComboBox.setItems(FXCollections.observableArrayList("В работе", "На ремонте", "Списан"));
        statusComboBox.getSelectionModel().selectFirst();
        messageLabel.setText("");
    }

    @FXML
    private void onAddDevice() {
        String name = nameField.getText().trim();
        String type = typeField.getText().trim();
        String inventoryNumber = inventoryNumberField.getText().trim();
        String location = locationField.getText().trim();
        String status = statusComboBox.getValue();

        if (name.isEmpty() || type.isEmpty() || inventoryNumber.isEmpty() || location.isEmpty() || status == null) {
            messageLabel.setText("Пожалуйста, заполните все поля");
            return;
        }

        // Проверяем, что такого инвентарного номера еще нет
        if (deviceDAO.findDeviceByInventoryNumber(inventoryNumber) != null) {
            messageLabel.setText("Прибор с таким инвентарным номером уже существует");
            return;
        }

        Device device = new Device(name, type, inventoryNumber, location, status);
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

    @FXML
    private void onCancel() {
        // Очистка формы или закрытие формы (зависит от реализации)
        clearForm();
        messageLabel.setText("");
    }

    private void clearForm() {
        nameField.clear();
        typeField.clear();
        inventoryNumberField.clear();
        locationField.clear();
        statusComboBox.getSelectionModel().selectFirst();
    }
}

