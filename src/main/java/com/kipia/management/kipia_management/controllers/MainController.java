package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.StyleUtils;
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
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.DoubleStringConverter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    @FXML
    private Button themeToggleBtn;

    private DeviceDAO deviceDAO;
    private FilteredList<Device> filteredList;
    private TableView<Device> deviceTable;
    private TableColumn<Device, String> inventoryCol;
    private Scene scene;
    private boolean isDarkTheme = false;

    // Метод для внедрения объекта DAO из основного приложения
    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
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

        // Присваиваем CSS классы кнопкам (через applyHoverAndAnimation или напрямую)
        if (devicesBtn != null) StyleUtils.applyHoverAndAnimation(devicesBtn, "button-devices", "button-devices-hover");
        if (addDeviceBtn != null)
            StyleUtils.applyHoverAndAnimation(addDeviceBtn, "button-add-device", "button-add-device-hover");
        if (reportsBtn != null) StyleUtils.applyHoverAndAnimation(reportsBtn, "button-reports", "button-reports-hover");
        if (themeToggleBtn != null)
            StyleUtils.applyHoverAndAnimation(themeToggleBtn, "button-theme-toggle", "button-theme-toggle-hover");
        if (exitBtn != null) StyleUtils.applyHoverAndAnimation(exitBtn, "button-exit", "button-exit-hover");
        if (deleteButton != null)
            StyleUtils.applyHoverAndAnimation(deleteButton, "button-delete", "button-delete-hover");

        // Добавляем CSS классы к статистике
        if (totalDevicesLabel != null) {
            totalDevicesLabel.getStyleClass().addAll("stat-number", "stat-total");
        }
        if (workingDevicesLabel != null) {
            workingDevicesLabel.getStyleClass().addAll("stat-number", "stat-working");
        }
        if (storageDevicesLabel != null) {
            storageDevicesLabel.getStyleClass().addAll("stat-number", "stat-storage");
        }
        if (lostDevicesLabel != null) {
            lostDevicesLabel.getStyleClass().addAll("stat-number", "stat-lost");
        }
        if (brokenDevicesLabel != null) {
            brokenDevicesLabel.getStyleClass().addAll("stat-number", "stat-broken");
        }

        if (statisticsPane != null) {
            statisticsPane.getStyleClass().add("statistics-pane");
        }
    }

    @FXML
    private void toggleTheme() {
        if (scene == null) {
            System.out.println("Ошибка: Scene не передана");
            return;
        }

        if (isDarkTheme) {
            // Светлая тема (дефолт или лёгкий CSS, если есть)
            scene.getStylesheets().clear();
            scene.getStylesheets().add(getClass().getResource("/styles/light-theme.css").toExternalForm());  // светлый CSS
            if (themeToggleBtn != null) themeToggleBtn.setText("Тёмная тема");
            isDarkTheme = false;
            System.out.println("Светлая тема активирована");
        } else {
            // Тёмная тема
            try {
                scene.getStylesheets().clear();
                scene.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm()); // темный CSS
                if (themeToggleBtn != null) themeToggleBtn.setText("Светлая тема");
                isDarkTheme = true;
                System.out.println("Тёмная тема активирована");
            } catch (Exception e) {
                System.out.println("Ошибка загрузки CSS: " + e.getMessage());
                Alert alert = new Alert(Alert.AlertType.ERROR, "Не удалось загрузить тёмную тему: " + e.getMessage());
                alert.show();
            }
        }
    }

    @FXML
    private void showDevices() {
        statusLabel.setText("Просмотр списка приборов");

        // Очищаем содержимое contentArea, убирая previous views
        contentArea.getChildren().clear();

        // Создаём кнопки экспорта/импорта в новой HBox (под панелью поиска)
        HBox exportImportPane = new HBox(10);
        exportButton = new Button("Экспорт в Excel");
        exportButton.getStyleClass().addAll("button-export");
        exportButton.setOnAction(event -> exportToExcel());
        StyleUtils.applyHoverAndAnimation(exportButton, "button-export", "button-export-hover");

        importButton = new Button("Импорт из Excel");
        importButton.getStyleClass().addAll("button-import");
        importButton.setOnAction(event -> importFromExcel());
        StyleUtils.applyHoverAndAnimation(importButton, "button-import", "button-import-hover");
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

        // 7. Колонка "Предел измерений"
        TableColumn<Device, String> measurementLimitCol = new TableColumn<>("Предел измерений");
        measurementLimitCol.setCellValueFactory(new PropertyValueFactory<>("measurementLimit"));
        measurementLimitCol.setPrefWidth(120);
        measurementLimitCol.setCellFactory(TextFieldTableCell.forTableColumn());
        measurementLimitCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setMeasurementLimit(event.getNewValue());
            deviceDAO.updateDevice(device);
        });

        // 8. Колонка "Класс точности" (Double)
        TableColumn<Device, Double> accuracyClassCol = new TableColumn<>("Класс точности");
        accuracyClassCol.setCellValueFactory(new PropertyValueFactory<>("accuracyClass"));
        accuracyClassCol.setPrefWidth(110);
        accuracyClassCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        accuracyClassCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            try {
                device.setAccuracyClass(event.getNewValue());
                deviceDAO.updateDevice(device);
            } catch (NumberFormatException e) {
                // Некорректный формат Double — можно добавить alert или сброс
                updateStatistics();
            }
        });

        // 10. Колонка "Состояние" — редактируемая с ComboBox
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

        // 11. Колонка "Доп.информация"
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


        // 9. Колонка "Фото" — с кнопкой "Просмотр"
        TableColumn<Device, Void> photoCol = new TableColumn<>("Фото");
        photoCol.setPrefWidth(145);
        photoCol.setCellFactory(param -> new TableCell<>() {
            private final Button addBtn = new Button("Добавить");
            private final Button viewBtn = new Button("Просмотр");

            {
                // Присваиваем CSS классы
                addBtn.getStyleClass().add("table-button-add");
                viewBtn.getStyleClass().add("table-button-view");

                // Подключаем hover-эффекты через метод applyHoverAndAnimation
                StyleUtils.applyHoverAndAnimation(addBtn, "table-button-add", "table-button-add-hover");
                StyleUtils.applyHoverAndAnimation(viewBtn, "table-button-view", "table-button-view-hover");

                // Фиксируем размеры кнопок
                addBtn.setPrefWidth(65);
                addBtn.setPrefHeight(22);
                viewBtn.setPrefWidth(65);
                viewBtn.setPrefHeight(22);

                // Обработчик кнопки "Добавить"
                addBtn.setOnAction(event -> {
                    Device device = getTableView().getItems().get(getIndex());
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Выбрать фото для прибора");
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.gif"));
                    Stage stage = (Stage) addBtn.getScene().getWindow();
                    File file = chooser.showOpenDialog(stage);
                    if (file != null) {
                        device.addPhoto(file.getAbsolutePath());
                        deviceDAO.updateDevice(device);
                        updateStatistics();
                    }
                });

                // Обработчик кнопки "Просмотр"
                viewBtn.setOnAction(event -> {
                    Device device = getTableView().getItems().get(getIndex());
                    List<String> photos = device.getPhotos();
                    if (photos == null || photos.isEmpty()) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Фото не добавлено");
                        alert.show();
                        return;
                    }
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
                measurementLimitCol,
                accuracyClassCol,
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

    @FXML
    private void showReports() {
        statusLabel.setText("Просмотр отчётов");
        try {
            // Загружаем новый FXML для отчётов
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/reports.fxml"));
            VBox reportsView = loader.load();
            ReportsController reportsController = loader.getController();

            // Передаём данные в ReportsController через init()
            reportsController.init(deviceDAO, (Stage) contentArea.getScene().getWindow());

            // Добавляем отчёты в contentArea
            contentArea.getChildren().clear();
            contentArea.getChildren().add(reportsView);

            // Скрываем другие панели (как в исходном)
            searchAndDeletePane.setVisible(false);
            statisticsPane.setVisible(false);
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка загрузки отчётов: " + e.getMessage());
        }
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
            String[] headers = {"Тип прибора", "Модель", "Производитель", "Инвентарный №", "Год выпуска", "Предел измерений", "Класс точности", "Место установки", "Статус", "Дополнительная информация"};
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
                row.createCell(5).setCellValue(device.getMeasurementLimit() != null ? device.getMeasurementLimit() : "");
                row.createCell(6).setCellValue(device.getAccuracyClass() != null ? device.getAccuracyClass() : 0.0);
                row.createCell(7).setCellValue(device.getLocation() != null ? device.getLocation() : "");
                row.createCell(8).setCellValue(device.getStatus() != null ? device.getStatus() : "");
                row.createCell(9).setCellValue(device.getAdditionalInfo() != null ? device.getAdditionalInfo() : "");
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
                String accuracyStr = getCellValue(row, 5);
                if (!accuracyStr.isEmpty()) {
                    try {
                        device.setAccuracyClass(Double.parseDouble(accuracyStr));
                    } catch (NumberFormatException e) {
                        device.setAccuracyClass(null);
                    }
                }
                device.setMeasurementLimit(getCellValue(row, 6));
                device.setLocation(getCellValue(row, 7));
                device.setStatus(getCellValue(row, 8));
                device.setAdditionalInfo(getCellValue(row, 9));

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
                    existing.setAccuracyClass((device.getAccuracyClass()));
                    existing.setMeasurementLimit(device.getMeasurementLimit());
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
                            (device.getLocation() != null && device.getLocation().toLowerCase().contains(lower)) ||
                            (device.getMeasurementLimit() != null && device.getMeasurementLimit().toLowerCase().contains(lower));
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

    // Вспомогательный метод для безопасного чтения ячейки (возвращает строку)
    private String getCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);  // Принудительно в строку
        return cell.getStringCellValue().trim();
    }

    // Выход из приложения
    @FXML
    private void exitApp() {
        System.exit(0);
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }
}