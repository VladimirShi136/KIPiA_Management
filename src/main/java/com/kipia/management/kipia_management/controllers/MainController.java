package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import javafx.animation.FadeTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainController {

    public Button devicesBtn;
    public Button addDeviceBtn;
    public Button reportsBtn;
    public Button exitBtn;
    @FXML
    private Label statusLabel;

    @FXML
    private VBox contentArea;

    @FXML
    private Label totalDevicesLabel;

    @FXML
    private Label workingDevicesLabel;

    @FXML
    private Label repairDevicesLabel;

    @FXML
    private TextField searchField;

    @FXML
    private Button deleteButton;

    @FXML
    private HBox searchAndDeletePane;

    @FXML
    private VBox statisticsPane;

    private DeviceDAO deviceDAO;
    private FilteredList<Device> filteredList;
    private TableView<Device> deviceTable;

    // Метод для внедрения объекта DAO из основного приложения
    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
    }

    private void applyHoverAndAnimation(Button button, String defaultColor, String hoverColor, String buttonType) {
        // Базовый стиль (адаптируйте под ваш дизайн)
        button.setStyle(
                "-fx-background-color: " + defaultColor + "; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +  // Standard шрифт для навиг./вори кнопок
                        "-fx-background-radius: 5; " +
                        "-fx-border-radius: 5; " +
                        "-fx-padding: 8 12 8 12; " +  // Padding для комфорта
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 3, 0, 0, 1); " +
                        "-fx-cursor: hand;"
        );

        // Hover-эффекты через event handlers (просто и без :hover)
        button.setOnMouseEntered(e -> {
            // Смена цвета
            button.setStyle(button.getStyle().replace(defaultColor, hoverColor));

            // Fade анимация (0.8 → 1.0)
            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), button);
            fadeIn.setFromValue(0.8);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        button.setOnMouseExited(e -> {
            // Возврат цвета
            button.setStyle(button.getStyle().replace(hoverColor, defaultColor));

            // Fade анимация (1.0 → 0.8)
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), button);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.8);
            fadeOut.play();
        });
    }

    @FXML
    private void initialize() {
        // Устанавливаем начальный текст в статусной строке
        statusLabel.setText("Готов к работе");
        // По умолчанию панели поиска и статистики видимы
        if (searchAndDeletePane != null) {
            searchAndDeletePane.setVisible(false);
            searchAndDeletePane.setManaged(false);
        }
        if (statisticsPane != null) {
            statisticsPane.setVisible(false);
            statisticsPane.setManaged(false);
        }

        // ДОБАВЛЕНО: Примени hover для навиг. кнопок (предполагая fx:id: devicesBtn, addDeviceBtn, reportsBtn, exitBtn)
        if (devicesBtn != null) applyHoverAndAnimation(devicesBtn, "#3498db", "#5dade2", "navigation");
        if (addDeviceBtn != null) applyHoverAndAnimation(addDeviceBtn, "#2ecc71", "#58d68d", "navigation");
        if (reportsBtn != null) applyHoverAndAnimation(reportsBtn, "#e67e22", "#f5a13d", "navigation");
        if (exitBtn != null) applyHoverAndAnimation(exitBtn, "#e74c3c", "#ec7063", "navigation");
    }

    @FXML
    private void showDevices() {
        statusLabel.setText("Просмотр списка приборов");

        // Очищаем содержимое contentArea, убирая previous views
        contentArea.getChildren().clear();

        // Добавляем панели поиска/удаления и статистики обратно
        searchAndDeletePane.setVisible(true);
        searchAndDeletePane.setManaged(true);
        contentArea.getChildren().add(searchAndDeletePane);

        statisticsPane.setVisible(true);
        statisticsPane.setManaged(true);
        contentArea.getChildren().add(statisticsPane);

        // Создаём новую таблицу
        deviceTable = new TableView<>();
        deviceTable.setEditable(true);

        // Создаём колонки для таблицы

        // 1. Колонка "Тип прибора"
        TableColumn<Device, String> typeCol = new TableColumn<>("Тип прибора");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(100);
        // Разрешаем редактирование текстом
        typeCol.setCellFactory(TextFieldTableCell.forTableColumn());
        typeCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setType(event.getNewValue());
            deviceDAO.updateDevice(device);
        });

        // 2. Колонка "Название/модель"
        TableColumn<Device, String> nameCol = new TableColumn<>("Название/модель");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(150);
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setName(event.getNewValue());
            deviceDAO.updateDevice(device);
        });

        // 3. Колонка "Производитель"
        TableColumn<Device, String> manufacturerCol = new TableColumn<>("Производитель");
        manufacturerCol.setCellValueFactory(new PropertyValueFactory<>("manufacturer"));
        manufacturerCol.setPrefWidth(120);

        // 4. Колонка "Инвентарный номер"
        TableColumn<Device, String> inventoryCol = new TableColumn<>("Инвентарный номер");
        inventoryCol.setCellValueFactory(new PropertyValueFactory<>("inventoryNumber"));
        inventoryCol.setPrefWidth(120);

        // 5. Колонка "Год выпуска"
        TableColumn<Device, String> yearCol = new TableColumn<>("Год выпуска");
        yearCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getYear() != null ? data.getValue().getYear().toString() : ""));
        yearCol.setPrefWidth(100);
        yearCol.setCellFactory(TextFieldTableCell.forTableColumn());
        yearCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            try {
                Integer year = event.getNewValue() != null && !event.getNewValue().isEmpty()
                        ? Integer.parseInt(event.getNewValue())
                        : null;
                device.setYear(year);
                deviceDAO.updateDevice(device);
            } catch (NumberFormatException e) {
                // Некорректный формат года — можно добавить сообщение об ошибке пользователю
            }
        });

        // 6. Колонка "Место установки"
        TableColumn<Device, String> locationCol = new TableColumn<>("Место установки");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        locationCol.setPrefWidth(120);
        locationCol.setCellFactory(TextFieldTableCell.forTableColumn());
        locationCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setLocation(event.getNewValue());
            deviceDAO.updateDevice(device);
        });

        // 7. Колонка "Состояние" — редактируемая с ComboBox
        TableColumn<Device, String> statusCol = new TableColumn<>("Состояние");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(ComboBoxTableCell.forTableColumn("хранение", "в работе", "утерян", "испорчен"));
        statusCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setStatus(event.getNewValue());
            deviceDAO.updateDevice(device);
            updateStatistics();
        });

        // 8. Колонка "Доп. информация"
        TableColumn<Device, String> additionalInfoCol = new TableColumn<>("Доп. информация");
        additionalInfoCol.setCellValueFactory(new PropertyValueFactory<>("additionalInfo"));
        additionalInfoCol.setPrefWidth(150);

        // 9. Колонка "Фото" — с кнопкой "Просмотр"
        TableColumn<Device, Void> photoCol = new TableColumn<>("Фото");
        photoCol.setPrefWidth(145);
        photoCol.setCellFactory(param -> new TableCell<>() {
            private final Button addBtn = new Button("Добавить");
            private final Button viewBtn = new Button("Просмотр");

            {
                // Стиль для "Добавить" (зеленый, позитивный)
                addBtn.setStyle(
                        "-fx-background-color: #4CAF50; " +
                                "-fx-text-fill: white; " +
                                "-fx-font-size: 11px; " +
                                "-fx-background-radius: 5; " +
                                "-fx-border-radius: 5; " +
                                "-fx-border-color: #388E3C; " +
                                "-fx-border-width: 1; " +
                                "-fx-padding: 3 6 3 6; " +
                                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 2, 0, 0, 1); " +
                                "-fx-cursor: hand;");
                addBtn.setPrefWidth(65);  // Маленькая ширина
                addBtn.setPrefHeight(22);  // Маленькая высота

                addBtn.setOnAction(event -> {
                    Device device = getTableView().getItems().get(getIndex());
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Выбрать фото для прибора");
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.gif"));
                    Stage stage = (Stage) addBtn.getScene().getWindow();
                    File file = chooser.showOpenDialog(stage);
                    if (file != null) {
                        device.addPhoto(file.getAbsolutePath());  // Добавляем в список
                        deviceDAO.updateDevice(device);  // Сохраняем
                        updateStatistics();  // Если хотите обновить статистику
                    }
                });

                // ДОБАВЛЕНО: Hover-эффекты через event handlers (программно)
                addBtn.setOnMouseEntered(e -> {
                    addBtn.setStyle(addBtn.getStyle().replace("#4CAF50", "#66BB6A"));  // Светлее зелёный при наведении
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(200), addBtn);
                    fadeIn.setFromValue(0.8);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();
                });
                addBtn.setOnMouseExited(e -> {
                    addBtn.setStyle(addBtn.getStyle().replace("#66BB6A", "#4CAF50"));  // Возврат к темному
                    FadeTransition fadeOut = new FadeTransition(Duration.millis(200), addBtn);
                    fadeOut.setFromValue(1.0);
                    fadeOut.setToValue(0.8);
                    fadeOut.play();
                });

                // Стиль для "Просмотр" (синий, нейтральный)
                viewBtn.setStyle(
                        "-fx-background-color: #2196F3; " +
                                "-fx-text-fill: white; " +
                                "-fx-font-size: 11px; " +
                                "-fx-background-radius: 5; " +
                                "-fx-border-radius: 5; " +
                                "-fx-border-color: #1976D2; " +
                                "-fx-border-width: 1; " +
                                "-fx-padding: 3 6 3 6; " +
                                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 2, 0, 0, 1); " +
                                "-fx-cursor: hand;");
                viewBtn.setPrefWidth(65);  // Маленькая ширина
                viewBtn.setPrefHeight(22);  // Маленькая высота

                viewBtn.setOnAction(event -> {
                    Device device = getTableView().getItems().get(getIndex());
                    List<String> photos = device.getPhotos();
                    if (photos == null || photos.isEmpty()) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Фото не добавлено");
                        alert.show();
                        return;
                    }

                    // Просмотр нескольких фото в новом окне
                    Stage photoStage = new Stage();
                    photoStage.setTitle("Фото прибора: " + device.getName());
                    VBox photoBox = new VBox(10);

                    for (String path : photos) {
                        try {
                            Image image = new Image("file:" + path);
                            ImageView imgView = new ImageView(image);
                            imgView.setFitWidth(250);
                            imgView.setFitHeight(250);
                            photoBox.getChildren().add(imgView);
                        } catch (Exception e) {
                            Label label = new Label("Ошибка загрузки фото: " + path);
                            photoBox.getChildren().add(label);
                        }
                    }

                    ScrollPane scroll = new ScrollPane(photoBox);
                    Scene scene = new Scene(scroll, 300, 600);
                    photoStage.setScene(scene);
                    photoStage.show();
                });

                // То же для viewBtn
                viewBtn.setOnMouseEntered(e -> {
                    viewBtn.setStyle(viewBtn.getStyle().replace("#2196F3", "#64B5F6"));  // Светлее синий
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(200), viewBtn);
                    fadeIn.setFromValue(0.8);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();
                });
                viewBtn.setOnMouseExited(e -> {
                    viewBtn.setStyle(viewBtn.getStyle().replace("#64B5F6", "#2196F3"));  // Возврат к темному
                    FadeTransition fadeOut = new FadeTransition(Duration.millis(200), viewBtn);
                    fadeOut.setFromValue(1.0);
                    fadeOut.setToValue(0.8);
                    fadeOut.play();
                });
            }

            // Отображение кнопок в клетке
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox btnBox = new HBox(8, addBtn, viewBtn);
                    setGraphic(btnBox);
                }
            }
        });

        // Добавляем все колонки в таблицу
        deviceTable.getColumns().addAll(
                typeCol,
                nameCol,
                manufacturerCol,
                inventoryCol,
                yearCol,
                locationCol,
                statusCol,
                additionalInfoCol,
                photoCol);

        // Загружаем данные из базы
        List<Device> allDevices = deviceDAO.getAllDevices();

        // Создаём FilteredList для поддержки поиска и фильтрации
        filteredList = new FilteredList<>(FXCollections.observableArrayList(allDevices), p -> true);

        // Добавляем слушатель на поле поиск для фильтрации
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String lower = newVal.toLowerCase().trim();
            filteredList.setPredicate(device -> {
                if (lower.isEmpty()) return true;
                return (device.getName() != null && device.getName().toLowerCase().contains(lower)) ||
                        (device.getType() != null && device.getType().toLowerCase().contains(lower)) ||
                        (device.getLocation() != null && device.getLocation().toLowerCase().contains(lower));
            });
            updateStatistics();
        });

        // Создаём SortedList для поддержки сортировки и связываем со списком
        SortedList<Device> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(deviceTable.comparatorProperty());

        // Устанавливаем отсортированный и отфильтрованный список в таблицу
        deviceTable.setItems(sortedList);

        // По умолчанию сортируем по месту установки
        deviceTable.getSortOrder().add(locationCol);
        deviceTable.sort();

        // Назначаем обработчик удаления на кнопку
        deleteButton.setOnAction(event -> deleteSelectedDevice());

        // Добавляем таблицу в contentArea после панелей поиска и статистики
        contentArea.getChildren().add(deviceTable);

        // Обновляем статистику
        updateStatistics();
    }

    // Метод для удаления выбранного в таблице устройства
    @FXML
    private void deleteSelectedDevice() {
        Device selected = deviceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Выберите прибор для удаления");
            alert.show();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить прибор \"" + selected.getName() + "\"?", ButtonType.YES, ButtonType.NO);

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                boolean deleted = deviceDAO.deleteDevice(selected.getId());
                if (deleted) {
                    filteredList.getSource().remove(selected);
                    updateStatistics();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Ошибка при удалении прибора из базы данных");
                    alert.show();
                }
            }
        });
    }

    // Метод обновления статистики по приборам
    private void updateStatistics() {
        int total = filteredList.size();
        long working = filteredList.stream()
                .filter(d -> "в работе".equalsIgnoreCase(d.getStatus()))
                .count();
        long repair = filteredList.stream()
                .filter(d -> "на ремонте".equalsIgnoreCase(d.getStatus()))
                .count();

        totalDevicesLabel.setText(String.valueOf(total));
        workingDevicesLabel.setText(String.valueOf(working));
        repairDevicesLabel.setText(String.valueOf(repair));
    }

    // Метод формы добавления нового устройства
    @FXML
    private void showAddDeviceForm() {
        statusLabel.setText("Добавление нового прибора");

        // Очищаем контент
        contentArea.getChildren().clear();

        // Скрываем панели поиска и статистики
        searchAndDeletePane.setVisible(false);
        searchAndDeletePane.setManaged(false);

        statisticsPane.setVisible(false);
        statisticsPane.setManaged(false);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/add-device-form.fxml"));
            Parent form = loader.load();

            AddDeviceController controller = loader.getController();
            controller.setDeviceDAO(this.deviceDAO);

            contentArea.getChildren().add(form);
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки формы: " + e.getMessage());
            contentArea.getChildren().add(new Label("Форма добавления прибора будет здесь"));
        }
    }

    // Метод показа отчетов
    @FXML
    private void showReports() {
        statusLabel.setText("Просмотр отчётов");

        // Очищаем контент
        contentArea.getChildren().clear();

        // Скрываем панели поиска и статистики
        searchAndDeletePane.setVisible(false);
        searchAndDeletePane.setManaged(false);

        statisticsPane.setVisible(false);
        statisticsPane.setManaged(false);

        contentArea.getChildren().add(new Label("Отчёты будут здесь"));
    }

    // Выход из приложения
    @FXML
    private void exitApp() {
        System.exit(0);
    }
}