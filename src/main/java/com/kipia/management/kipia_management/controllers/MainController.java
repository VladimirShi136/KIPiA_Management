package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import javafx.animation.FadeTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
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
import javafx.util.converter.DefaultStringConverter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MainController {

    public Button devicesBtn;
    public Button addDeviceBtn;
    public Button reportsBtn;
    public Button exitBtn;
    public Button exportButton;
    public Button importButton;
    @FXML
    private Label statusLabel;

    @FXML
    private VBox contentArea;

    @FXML
    private Label totalDevicesLabel;

    @FXML
    private Label workingDevicesLabel;

    @FXML
    private Label storageDevicesLabel;

    @FXML
    private Label lostDevicesLabel;

    @FXML
    private Label brokenDevicesLabel;

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
    private TableColumn<Device, String> inventoryCol;

    // Метод для внедрения объекта DAO из основного приложения
    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
    }

    private void applyHoverAndAnimation(Button button, String defaultColor, String hoverColor) {
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

        // ДОБАВЛЕНО: Примени hover для навиг. кнопок
        if (devicesBtn != null) applyHoverAndAnimation(devicesBtn, "#3498db", "#5dade2");
        if (addDeviceBtn != null) applyHoverAndAnimation(addDeviceBtn, "#2ecc71", "#58d68d");
        if (reportsBtn != null) applyHoverAndAnimation(reportsBtn, "#e67e22", "#f5a13d");
        if (exitBtn != null) applyHoverAndAnimation(exitBtn, "#e74c3c", "#ec7063");
        if (deleteButton != null) applyHoverAndAnimation(deleteButton, "#e74c3c", "#ec7063");
    }

    @FXML
    private void showDevices() {
        statusLabel.setText("Просмотр списка приборов");


        // Очищаем содержимое contentArea, убирая previous views
        contentArea.getChildren().clear();

        // Создаём кнопки экспорта/импорта в новой HBox (под панелью поиска)
        HBox exportImportPane = new HBox(10);
        exportButton = new Button("Экспорт в Excel");
        exportButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 5 10;");
        exportButton.setOnAction(event -> exportToExcel());
        if (exportButton != null) applyHoverAndAnimation(exportButton, "#2ecc71", "#58d68d");
        importButton = new Button("Импорт из Excel");
        importButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 5 10;");
        importButton.setOnAction(event -> importFromExcel());
        if (importButton != null) applyHoverAndAnimation(importButton, "#3498db", "#5dade2");
        exportImportPane.getChildren().addAll(exportButton, importButton);

        // Добавляем панели в contentArea: поиск, экспорт/импорт, статистика
        searchAndDeletePane.setVisible(true);
        searchAndDeletePane.setManaged(true);
        contentArea.getChildren().add(searchAndDeletePane);  // Панель поиска (уже содержит свои элементы)

        contentArea.getChildren().add(exportImportPane);     // Панель экспорта/импорта (новая)

        statisticsPane.setVisible(true);
        statisticsPane.setManaged(true);
        contentArea.getChildren().add(statisticsPane);       // Панель статистики

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
        TableColumn<Device, String> nameCol = new TableColumn<>("Модель");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(90);
        // Разрешаем редактирование текстом
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
        // Разрешаем редактирование текстом
        manufacturerCol.setCellFactory(TextFieldTableCell.forTableColumn());
        manufacturerCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setManufacturer(event.getNewValue());
            deviceDAO.updateDevice(device);
        });

        // 4. Колонка "Инвентарный номер"
        inventoryCol = new TableColumn<>("Инвентарный №");
        inventoryCol.setCellValueFactory(new PropertyValueFactory<>("inventoryNumber"));
        inventoryCol.setPrefWidth(120);
        // Разрешаем редактирование текстом
        inventoryCol.setCellFactory(TextFieldTableCell.forTableColumn());
        inventoryCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setInventoryNumber(event.getNewValue());
            deviceDAO.updateDevice(device);
        });

        // 5. Колонка "Год выпуска"
        TableColumn<Device, String> yearCol = new TableColumn<>("Год выпуска");
        yearCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getYear() != null ? data.getValue().getYear().toString() : ""));
        yearCol.setPrefWidth(100);
        // Разрешаем редактирование текстом
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
        // Разрешаем редактирование текстом
        locationCol.setCellFactory(TextFieldTableCell.forTableColumn());
        locationCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setLocation(event.getNewValue());
            deviceDAO.updateDevice(device);
        });

        // 7. Колонка "Состояние" — редактируемая с ComboBox
        TableColumn<Device, String> statusCol = new TableColumn<>("Статус");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(80);
        // Разрешаем изменять комбо-бокс в рамках 4-х параметров
        statusCol.setCellFactory(ComboBoxTableCell.forTableColumn("Хранение", "В работе", "Утерян", "Испорчен"));
        statusCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setStatus(event.getNewValue());
            deviceDAO.updateDevice(device);
            updateStatistics();
        });

        // 9. Колонка "Доп.информация"
        TableColumn<Device, String> additionalInfoCol = new TableColumn<>("Дополнительная информация");
        additionalInfoCol.setCellValueFactory(new PropertyValueFactory<>("additionalInfo"));
        additionalInfoCol.setPrefWidth(200);

        // Используем TextFieldTableCell с конвертером для String
        additionalInfoCol.setCellFactory(col -> {
            TextFieldTableCell<Device, String> cell = new TextFieldTableCell<>(new DefaultStringConverter());

            cell.itemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.trim().isEmpty()) {
                    if (newVal.length() > 50) {
                        Tooltip tooltip = new Tooltip(newVal);
                        tooltip.setWrapText(true);
                        tooltip.setMaxWidth(300);
                        cell.setTooltip(tooltip);
                    } else {
                        cell.setTooltip(null);
                    }
                } else {
                    cell.setTooltip(null);
                }
            });
            return cell;
        });

        // Обработка редактирования
        additionalInfoCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setAdditionalInfo(event.getNewValue());
            deviceDAO.updateDevice(device);
        });


        // 8. Колонка "Фото" — с кнопкой "Просмотр"
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

        // ДОБАВЛЕНО: Глобальный стиль для селекции (голубой фон, чёрный текст)
        deviceTable.setStyle("-fx-selection-bar: #cce7ff; -fx-selection-bar-text: black; -fx-selection-bar-non-focused: #cce7ff;");

        // Настройка цвета строк таблицы
        deviceTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Device item, boolean empty) {
                super.updateItem(item, empty);

                // Если строка пуста — сброс
                if (empty) {
                    setStyle("");
                    return;
                }

                // Если строка НЕ выбранная
                if (!isSelected()) {
                    if (getIndex() % 2 == 0) {
                        setStyle("-fx-background-color: #b8b8b8;");
                    } else {
                        setStyle("-fx-background-color: white;");
                    }
                }
                // Если строка ВЫБРАННАЯ
                else {
                    setStyle("-fx-background-color: #7abcff;");
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
                photoCol,
                additionalInfoCol
        );

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
        SortedList<Device> sortedList = createSortedList(filteredList, deviceTable, inventoryCol);

        // Устанавливаем отсортированный и отфильтрованный список в таблицу
        deviceTable.setItems(sortedList);

        // По умолчанию сортируем по инвентарному номеру
        deviceTable.getSortOrder().add(inventoryCol);
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
                .filter(d -> "В работе".equalsIgnoreCase(d.getStatus()))
                .count();
        long storage = filteredList.stream()
                .filter(d -> "Хранение".equalsIgnoreCase(d.getStatus()))
                .count();
        long lost = filteredList.stream()
                .filter(d -> "Утерян".equalsIgnoreCase(d.getStatus()))
                .count();
        long broken = filteredList.stream()
                .filter(d -> "Испорчен".equalsIgnoreCase(d.getStatus()))
                .count();


        totalDevicesLabel.setText(String.valueOf(total));
        workingDevicesLabel.setText(String.valueOf(working));
        storageDevicesLabel.setText(String.valueOf(storage));
        lostDevicesLabel.setText(String.valueOf(lost));
        brokenDevicesLabel.setText(String.valueOf(broken));
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
        // Скрываем другие панели
        searchAndDeletePane.setVisible(false);
        searchAndDeletePane.setManaged(false);
        statisticsPane.setVisible(false);
        statisticsPane.setManaged(false);

        // Получаем все устройства для отчёта
        List<Device> allDevices = deviceDAO.getAllDevices();

        // Панель выбора типа отчёта
        ToggleGroup reportTypeGroup = new ToggleGroup();
        HBox reportTypeBox = new HBox(20);
        reportTypeBox.setPadding(new Insets(10));

        RadioButton statusReportBtn = new RadioButton("По статусу");
        statusReportBtn.setToggleGroup(reportTypeGroup);
        statusReportBtn.setSelected(true);  // По умолчанию

        RadioButton typeReportBtn = new RadioButton("По типам приборов");
        typeReportBtn.setToggleGroup(reportTypeGroup);

        RadioButton manufacturerReportBtn = new RadioButton("По производителям");
        manufacturerReportBtn.setToggleGroup(reportTypeGroup);

        RadioButton locationReportBtn = new RadioButton("По местоположению");  // Новый радиобаттон
        locationReportBtn.setToggleGroup(reportTypeGroup);

        RadioButton yearReportBtn = new RadioButton("По годам выпуска");
        yearReportBtn.setToggleGroup(reportTypeGroup);

        reportTypeBox.getChildren().addAll(statusReportBtn, typeReportBtn, manufacturerReportBtn, locationReportBtn, yearReportBtn);

        // Элементы отчёта (обновляемые)
        Label titleLabel = new Label("Отчёт по устройтвам — По статусу");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label statsLabel = new Label("");
        statsLabel.setStyle("-fx-font-size: 16px;");

        PieChart chart = new PieChart();
        chart.setPrefSize(400, 300);

        // Кнопка экспорта
        Button exportReportButton = new Button("Экспортировать отчёт в Excel");
        exportReportButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 5 10;");

        // Функция обновления отчёта
        Runnable updateReport = () -> {
            String selectedType = ((RadioButton) reportTypeGroup.getSelectedToggle()).getText();
            titleLabel.setText("Отчёт по устройствоам — " + selectedType);
            if (selectedType.equals("По статусу")) {
                buildReport(allDevices, Device::getStatus, "Распределение по статусам", statsLabel, chart);
                exportReportButton.setOnAction(event -> exportReportToExcel(allDevices, "Status"));
            } else if (selectedType.equals("По типам приборов")) {
                buildReport(allDevices, Device::getType, "Распределение по типам", statsLabel, chart);
                exportReportButton.setOnAction(event -> exportReportToExcel(allDevices, "Type"));
            } else if (selectedType.equals("По производителям")) {
                buildReport(allDevices, Device::getManufacturer, "Распределение по производителям", statsLabel, chart);
                exportReportButton.setOnAction(event -> exportReportToExcel(allDevices, "Manufacturer"));
            } else if (selectedType.equals("По местоположению")) {
                buildReport(allDevices, Device::getLocation, "Распределение по местоположениям", statsLabel, chart);
                exportReportButton.setOnAction(event -> exportReportToExcel(allDevices, "Location"));
            } else if (selectedType.equals("По годам выпуска")) {
                buildReportByYear(allDevices, statsLabel, chart);
                exportReportButton.setOnAction(event -> exportReportToExcel(allDevices, "Year"));
            }
        };

        // Обработчики для радиобаттонов
        statusReportBtn.setOnAction(event -> updateReport.run());
        typeReportBtn.setOnAction(event -> updateReport.run());
        manufacturerReportBtn.setOnAction(event -> updateReport.run());
        locationReportBtn.setOnAction(event -> updateReport.run());  // Добавлено для нового
        yearReportBtn.setOnAction(event -> updateReport.run());

        // Инициализация отчёта (по умолчанию "По статусу")
        updateReport.run();

        // Контейнер для отчёта
        VBox reportsBox = new VBox(20);
        reportsBox.setPadding(new Insets(20));
        reportsBox.getChildren().addAll(reportTypeBox, titleLabel, statsLabel, chart, exportReportButton);

        // Добавляем в contentArea
        contentArea.getChildren().add(reportsBox);
    }

    private void exportToExcel() {
        // Выбираем файл для сохранения
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт устройств в Excel");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        File file = chooser.showSaveDialog(contentArea.getScene().getWindow());
        if (file == null) return;

        try (Workbook workbook = new XSSFWorkbook()) {
            // Создаём лист "Devices"
            Sheet sheet = workbook.createSheet("Devices");

            // Заголовки столбцов (соответствуем полям Device)
            String[] headers = {"Тип прибора", "Модель", "Производитель", "Инвентарный №", "Год выпуска", "Место установки", "Статус", "Дополнительная информация"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // Данные: проходим по видимым устройствам в таблице (учитываем фильтры/сортинг)
            int rowNum = 1;
            for (Device device : deviceTable.getItems()) {  // Берём из TableView (с учётом FilteredList/SortedList)
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(device.getType() != null ? device.getType() : "");
                row.createCell(1).setCellValue(device.getName() != null ? device.getName() : "");
                row.createCell(2).setCellValue(device.getManufacturer() != null ? device.getManufacturer() : "");
                row.createCell(3).setCellValue(device.getInventoryNumber() != null ? device.getInventoryNumber() : "");
                row.createCell(4).setCellValue(device.getYear() != null ? device.getYear().toString() : "");  // Число как строка для корректности
                row.createCell(5).setCellValue(device.getLocation() != null ? device.getLocation() : "");
                row.createCell(6).setCellValue(device.getStatus() != null ? device.getStatus() : "");
                row.createCell(7).setCellValue(device.getAdditionalInfo() != null ? device.getAdditionalInfo() : "");
            }

            // Автоподстройка ширины колонок для лучшей читабельности
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Записываем файл
            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }

            // Уведомляем пользователя
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Экспорт завершён! Файл сохранён: " + file.getAbsolutePath());
            alert.show();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Ошибка экспорта: " + e.getMessage());
            alert.show();
        }
    }

    private void importFromExcel() {
        // Выбираем файл для импорта
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Импорт устройств из Excel");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        File file = chooser.showOpenDialog(contentArea.getScene().getWindow());
        if (file == null) return;

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            // Предполагаем, что данные на листе "Devices" (как в экспорте)
            Sheet sheet = workbook.getSheet("Devices");
            if (sheet == null) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Лист 'Devices' не найден в файле. Убедитесь, что файл корректный.");
                alert.show();
                return;
            }

            int importedCount = 0;  // Счётчик импортированных
            int updatedCount = 0;   // Счётчик обновлённых

            // Цикл по строкам (пропускаем заголовок row 0)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;  // Пропускаем пустые строки

                // Парсим ячейки в поля Device
                Device device = new Device();
                device.setType(getCellValue(row, 0));
                device.setName(getCellValue(row, 1));
                device.setManufacturer(getCellValue(row, 2));
                device.setInventoryNumber(getCellValue(row, 3));
                // Год: пытаемся парсить как целое
                String yearStr = getCellValue(row, 4);
                if (!yearStr.isEmpty()) {
                    try {
                        device.setYear(Integer.parseInt(yearStr));
                    } catch (NumberFormatException e) {
                        device.setYear(null);  // Если не число, ставим null
                    }
                }
                device.setLocation(getCellValue(row, 5));
                device.setStatus(getCellValue(row, 6));
                device.setAdditionalInfo(getCellValue(row, 7));

                // Валидация: инвентарный номер обязателен
                if (device.getInventoryNumber() == null || device.getInventoryNumber().isEmpty()) {
                    continue;  // Пропускаем, если нет номера
                }

                // Проверяем, есть ли уже устройство с таким номером (используем findDeviceByInventoryNumber из DAO)
                Device existing = deviceDAO.findDeviceByInventoryNumber(device.getInventoryNumber());
                if (existing != null) {
                    // Обновляем существующий (остальные поля, но фото оставляем старыми)
                    existing.setType(device.getType());
                    existing.setName(device.getName());
                    existing.setManufacturer(device.getManufacturer());
                    // Год уже обработан
                    existing.setLocation(device.getLocation());
                    existing.setStatus(device.getStatus());
                    existing.setAdditionalInfo(device.getAdditionalInfo());
                    existing.setYear(device.getYear());  // Явно устанавливаем, так как null-safe
                    // Фото оставляем existing (не обновляем)
                    deviceDAO.updateDevice(existing);
                    updatedCount++;
                } else {
                    // Добавляем новое (фото пустой список)
                    device.setPhotos(new ArrayList<>());  // Пусто, если нет существующих
                    deviceDAO.addDevice(device);
                    importedCount++;
                }
            }

            // Обновляем таблицу данных (reload из DAO)
            List<Device> allDevices = deviceDAO.getAllDevices();
            filteredList = new FilteredList<>(FXCollections.observableArrayList(allDevices), p -> true);
            // Добавить слушатель поиска (если не было)
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

            // Создаём новую SortedList и привязываем к TableView
            SortedList<Device> sortedList = createSortedList(filteredList, deviceTable, inventoryCol);
            deviceTable.setItems(sortedList);

            // По умолчанию сортируем по инвентарному номеру (если надо)
            deviceTable.getSortOrder().add(inventoryCol);
            deviceTable.sort();

            updateStatistics();

            // Уведомление пользователя
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Импорт завершён!" +
                            "\nДобавлено: " + importedCount +
                            "\nОбновлено: " + updatedCount +
                            "\nФото НЕ обновлены (остались старыми, если были)");
            alert.show();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Ошибка импорта: " + e.getMessage());
            alert.show();
        }
    }

    // Экспорт отчета в Excel файл
    private void exportReportToExcel(List<Device> devices, String reportType) {
        // Выбираем файл
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт отчёта " + reportType + " в Excel");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        File file = chooser.showSaveDialog(contentArea.getScene().getWindow());
        if (file == null) return;

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Report");

            // Заголовки
            Row headerRow = sheet.createRow(0);
            if (reportType.equals("Status")) {
                headerRow.createCell(0).setCellValue("Статус");
            } else if (reportType.equals("Type")) {
                headerRow.createCell(0).setCellValue("Тип прибора");
            } else if (reportType.equals("Manufacturer")) {
                headerRow.createCell(0).setCellValue("Производитель");
            } else if (reportType.equals("Location")) {  // Добавлено для местоположения
                headerRow.createCell(0).setCellValue("Местоположение");
            } else if (reportType.equals("Year")) {
                headerRow.createCell(0).setCellValue("Год выпуска");
            }
            headerRow.createCell(1).setCellValue("Количество");

            // Данные
            Map<String, Long> countMap;
            if (reportType.equals("Status")) {
                countMap = devices.stream().collect(Collectors.groupingBy(Device::getStatus, Collectors.counting()));
            } else if (reportType.equals("Type")) {
                countMap = devices.stream().collect(Collectors.groupingBy(Device::getType, Collectors.counting()));
            } else if (reportType.equals("Manufacturer")) {
                countMap = devices.stream().collect(Collectors.groupingBy(Device::getManufacturer, Collectors.counting()));
            } else if (reportType.equals("Location")) {  // Группировка по местоположению
                countMap = devices.stream().collect(Collectors.groupingBy(Device::getLocation, Collectors.counting()));
            } else {  // Year
                countMap = devices.stream().filter(d -> d.getYear() != null)
                        .collect(Collectors.groupingBy(d -> d.getYear().toString(), Collectors.counting()));
            }

            int rowNum = 1;
            for (Map.Entry<String, Long> entry : countMap.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty()) continue;  // Пропускаем пустые
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }

            // Запись
            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Отчёт " + reportType + " экспортирован: " + file.getAbsolutePath());
            alert.show();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Ошибка: " + e.getMessage());
        }
    }

    // Сборщик данных и отчёта для категориальных полей (статус, тип, производитель)
    private void buildReport(List<Device> devices, Function<Device, String> fieldGetter, String chartTitle, Label statsLabel, PieChart chart) {
        Map<String, Long> countMap = devices.stream()
                .filter(d -> fieldGetter.apply(d) != null && !fieldGetter.apply(d).isEmpty())
                .collect(Collectors.groupingBy(fieldGetter, Collectors.counting()));

        // Текстовая статистика
        StringBuilder statsText = new StringBuilder("Общая статистика:\n");
        countMap.forEach((key, count) -> statsText.append(key).append(": ").append(count).append("\n"));
        statsLabel.setText(statsText.toString());

        // Диаграмма
        ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList();
        countMap.forEach((key, count) -> chartData.add(new PieChart.Data(key + " (" + count + ")", count)));
        chart.setData(chartData);
        chart.setTitle(chartTitle);  // Динамический заголовок
    }

    // Специальный сборщик для года (Integer -> String)
    private void buildReportByYear(List<Device> devices, Label statsLabel, PieChart chart) {
        Map<String, Long> countMap = devices.stream()
                .filter(d -> d.getYear() != null)
                .collect(Collectors.groupingBy(d -> d.getYear().toString(), Collectors.counting()));

        StringBuilder statsText = new StringBuilder("Общая статистика:\n");
        countMap.forEach((key, count) -> statsText.append(key).append(": ").append(count).append("\n"));
        statsLabel.setText(statsText.toString());

        ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList();
        countMap.forEach((key, count) -> chartData.add(new PieChart.Data("Год " + key + " (" + count + ")", count)));
        chart.setData(chartData);
        chart.setTitle("Распределение по годам");
    }

    // Вспомогательный метод для безопасного чтения ячейки (возвращает строку)
    private String getCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);  // Принудительно в строку
        return cell.getStringCellValue().trim();
    }

    // Новый method в MainController
    private SortedList<Device> createSortedList(FilteredList<Device> filteredList, TableView<Device> table, TableColumn<Device, String> defaultSortColumn) {
        SortedList<Device> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedList);
        // По умолчанию сортировка
        table.getSortOrder().add(defaultSortColumn);
        table.sort();
        return sortedList;
    }

    // Выход из приложения
    @FXML
    private void exitApp() {
        System.exit(0);
    }
}