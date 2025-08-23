package com.kipia.management.kipia_management.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * @author vladimir_shi
 * @since 23.08.2025
 */

public class MainController {

    @FXML
    private Label statusLabel;

    @FXML
    private VBox contentArea;

    @FXML
    private void initialize() {
        // Инициализация контроллера
        statusLabel.setText("Система инициализирована");
    }

    @FXML
    private void showDevices() {
        statusLabel.setText("Просмотр списка приборов");
        // Здесь будет логика отображения списка приборов
        contentArea.getChildren().clear();
        contentArea.getChildren().add(new Label("Список приборов будет здесь"));
    }

    @FXML
    private void showAddDeviceForm() {
        statusLabel.setText("Добавление нового прибора");
        // Здесь будет форма добавления прибора
        contentArea.getChildren().clear();
        contentArea.getChildren().add(new Label("Форма добавления прибора будет здесь"));
    }

    @FXML
    private void showReports() {
        statusLabel.setText("Просмотр отчётов");
        // Здесь будут отчёты
        contentArea.getChildren().clear();
        contentArea.getChildren().add(new Label("Отчёты будут здесь"));
    }

    @FXML
    private void exitApp() {
        System.exit(0);
    }
}