package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.converter.DoubleStringConverter;

import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Контроллер, отвечающий **только** за отображение и работу с таблицей
 * приборов.  В нём сосредоточена логика:
 *      создание колонок (без дублирования кода);
 *      фильтрации/сортировки;
 *      редактирования ячеек;
 *      экспорта/импорта в Excel;
 *      удаления выбранного прибора;
 *      подсчёта статистики.
 *   В `MainController` остаётся лишь переключать представления
 *   (загружать FXML‑файл этого контроллера и передавать в него DAO).
 * </p>
 */
public class DevicesTableController {

    // ---------- FXML‑элементы ----------
    @FXML private TableView<Device> deviceTable;
    @FXML private TextField searchField;
    @FXML private Button deleteButton;
    @FXML private Button exportButton;
    @FXML private Button importButton;

    @FXML private Label totalDevicesLabel;
    @FXML private Label workingDevicesLabel;
    @FXML private Label storageDevicesLabel;
    @FXML private Label lostDevicesLabel;
    @FXML private Label brokenDevicesLabel;

    // ---------- Сервисы ----------
    private DeviceDAO deviceDAO;

    // Списки, используемые для фильтрации/сортировки
    private FilteredList<Device> filteredList;

    // Инвентарный номер — колонка, которой будем пользоваться как «default sort»
    private TableColumn<Device, String> inventoryCol;

    // -----------------------------------------------------------------
    //                     PUBLIC API (вызывается из MainController)
    // -----------------------------------------------------------------
    /** Передаём DAO, получаемый из главного окна. */
    public void setDeviceDAO(DeviceDAO dao) {
        this.deviceDAO = dao;
    }

    /** Метод, вызываемый после загрузки FXML. */
    public void init() {
        createTableColumns();
        loadDataFromDao();
        configureSearch();
        configureButtons();
        configureRowStyle();
        updateStatistics();
    }

    // -----------------------------------------------------------------
    //                     ИНИЦИАЛИЗАЦИЯ ТАБЛИЦЫ
    // -----------------------------------------------------------------
    /** Создаём все колонки, используя фабричные методы. */
    private void createTableColumns() {

        // 1. Текстовые колонки (Type, Name, Manufacturer, Inventory, …)
        TableColumn<Device, String> typeCol = createEditableStringColumn(
                "Тип прибора", "type", 100,
                Device::setType);

        TableColumn<Device, String> nameCol = createEditableStringColumn(
                "Модель", "name", 90,
                Device::setName);

        TableColumn<Device, String> manufacturerCol = createEditableStringColumn(
                "Производитель", "manufacturer", 120,
                Device::setManufacturer);

        inventoryCol = createEditableStringColumn(
                "Инвентарный №", "inventoryNumber", 120,
                Device::setInventoryNumber);

        // 2. Год выпуска – особая колонка (Integer → String в UI)
        TableColumn<Device, String> yearCol = new TableColumn<>("Год выпуска");
        yearCol.setPrefWidth(100);
        yearCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getYear() == null ?
                                "" : data.getValue().getYear().toString()));
        yearCol.setCellFactory(TextFieldTableCell.forTableColumn());
        yearCol.setOnEditCommit(event -> {
            Device dev = event.getRowValue();
            try {
                Integer y = event.getNewValue().isBlank() ? null
                        : Integer.valueOf(event.getNewValue());
                dev.setYear(y);
                deviceDAO.updateDevice(dev);
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.WARNING,
                        "Год должен быть целым числом");
                // откатываем изменение – перерисуем таблицу
                deviceTable.refresh();
            }
        });

        // 3. Обычные строковые колонки
        TableColumn<Device, String> measurementLimitCol = createEditableStringColumn(
                "Предел измерений", "measurementLimit", 120,
                Device::setMeasurementLimit);

        TableColumn<Device, String> locationCol = createEditableStringColumn(
                "Место установки", "location", 120,
                Device::setLocation);

        TableColumn<Device, String> valveNumberCol = createEditableStringColumn(
                "Кран №", "valveNumber", 90,
                Device::setValveNumber);

        TableColumn<Device, String> additionalInfoCol = createEditableStringColumn(
                "Доп. информация", "additionalInfo", 200,
                Device::setAdditionalInfo);

        // 4. Числовая колонка – Double (класс точности)
        TableColumn<Device, Double> accuracyClassCol = createEditableDoubleColumn(
                "Класс точности", "accuracyClass", 110,
                Device::setAccuracyClass);

        // 5. Статус – ComboBox
        TableColumn<Device, String> statusCol = new TableColumn<>("Статус");
        statusCol.setPrefWidth(80);
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(ComboBoxTableCell.forTableColumn(
                "Хранение", "В работе", "Утерян", "Испорчен"));
        statusCol.setOnEditCommit(event -> {
            Device dev = event.getRowValue();
            dev.setStatus(event.getNewValue());
            deviceDAO.updateDevice(dev);
            updateStatistics();
        });

        // 6. Фото – две кнопки «Добавить» / «Просмотр»
        TableColumn<Device, Void> photoCol = new TableColumn<>("Фото");
        photoCol.setPrefWidth(145);
        photoCol.setCellFactory(createPhotoCellFactory());

        // -----------------------------------------------------------------
        //   Добавляем все колонки в таблицу
        // -----------------------------------------------------------------
        deviceTable.getColumns().addAll(
                typeCol, nameCol, manufacturerCol, inventoryCol,
                yearCol, measurementLimitCol, accuracyClassCol,
                locationCol, valveNumberCol, statusCol,
                photoCol, additionalInfoCol
        );

        // глобальный стиль выбора (можно вынести в CSS, но пока так)
        deviceTable.setStyle("-fx-selection-bar: #cce7ff; -fx-selection-bar-text: black;");
    }

    // -----------------------------------------------------------------
    //   Фабричные методы для колонок
    // -----------------------------------------------------------------
    /** Текстовая колонка, редактируемая через TextFieldTableCell. */
    private TableColumn<Device, String> createEditableStringColumn(
            String title,
            String propertyName,
            double prefWidth,
            BiConsumer<Device, String> onCommit) {

        TableColumn<Device, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(propertyName));
        col.setPrefWidth(prefWidth);
        col.setCellFactory(TextFieldTableCell.forTableColumn());
        col.setOnEditCommit(event -> {
            Device dev = event.getRowValue();
            onCommit.accept(dev, event.getNewValue());
            deviceDAO.updateDevice(dev);
        });
        return col;
    }

    /** Числовая колонка (Double) с DoubleStringConverter. */
    private TableColumn<Device, Double> createEditableDoubleColumn(
            String title,
            String propertyName,
            double prefWidth,
            BiConsumer<Device, Double> onCommit) {

        TableColumn<Device, Double> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(propertyName));
        col.setPrefWidth(prefWidth);
        col.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        col.setOnEditCommit(event -> {
            Device dev = event.getRowValue();
            onCommit.accept(dev, event.getNewValue());
            deviceDAO.updateDevice(dev);
        });
        return col;
    }

    /** Фабрика ячейки для колонки «Фото». */
    private Callback<TableColumn<Device, Void>, TableCell<Device, Void>> createPhotoCellFactory() {
        return param -> new TableCell<>() {

            private final Button addBtn = new Button("Добавить");
            private final Button viewBtn = new Button("Просмотр");

            {
                // стили и размеры
                addBtn.getStyleClass().addAll("table-button-add");
                viewBtn.getStyleClass().addAll("table-button-view");
                StyleUtils.applyHoverAndAnimation(addBtn,
                        "table-button-add", "table-button-add-hover");
                StyleUtils.applyHoverAndAnimation(viewBtn,
                        "table-button-view", "table-button-view-hover");
                addBtn.setPrefSize(65, 22);
                viewBtn.setPrefSize(65, 22);

                // обработчики
                addBtn.setOnAction(e -> addPhoto(getCurrentDevice()));
                viewBtn.setOnAction(e -> viewPhotos(getCurrentDevice()));
            }

            private Device getCurrentDevice() {
                return getTableView().getItems().get(getIndex());
            }

            private void addPhoto(Device device) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Выберите фото прибора");
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.gif"));
                Stage stage = (Stage) addBtn.getScene().getWindow();
                File file = chooser.showOpenDialog(stage);
                if (file != null) {
                    device.addPhoto(file.getAbsolutePath());
                    deviceDAO.updateDevice(device);
                    updateStatistics();
                }
            }

            private void viewPhotos(Device device) {
                List<String> list = device.getPhotos();
                if (list == null || list.isEmpty()) {
                    showAlert(Alert.AlertType.INFORMATION, "Фотографии не добавлены");
                    return;
                }
                Stage stage = new Stage();
                stage.setTitle("Фото прибора: " + device.getName());
                VBox box = new VBox(10);
                for (String path : list) {
                    try {
                        Image img = new Image("file:" + path);
                        ImageView iv = new ImageView(img);
                        iv.setFitWidth(250);
                        iv.setFitHeight(250);
                        box.getChildren().add(iv);
                    } catch (Exception ex) {
                        box.getChildren().add(new Label("Ошибка загрузки: " + path));
                    }
                }
                stage.setScene(new javafx.scene.Scene(new ScrollPane(box), 300, 600));
                stage.show();
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(new HBox(8, addBtn, viewBtn));
                }
            }
        };
    }

    // -----------------------------------------------------------------
    //   Загрузка данных и настройка фильтрации
    // -----------------------------------------------------------------
    private void loadDataFromDao() {
        List<Device> all = deviceDAO.getAllDevices();
        filteredList = new FilteredList<>(FXCollections.observableArrayList(all), p -> true);
        SortedList<Device> sorted = createSortedList(filteredList, deviceTable, inventoryCol);
        deviceTable.setItems(sorted);
        // Сортируем по инвентарному номеру сразу
        deviceTable.getSortOrder().add(inventoryCol);
        deviceTable.sort();
    }

    private void configureSearch() {
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            String lower = newV.toLowerCase().trim();
            filteredList.setPredicate(dev -> {
                if (lower.isEmpty()) return true;
                return (dev.getName() != null && dev.getName().toLowerCase().contains(lower))
                        || (dev.getType() != null && dev.getType().toLowerCase().contains(lower))
                        || (dev.getLocation() != null && dev.getLocation().toLowerCase().contains(lower));
            });
            updateStatistics();
        });
    }

    // -----------------------------------------------------------------
    //   Кнопки (удалить, экспорт, импорт)
    // -----------------------------------------------------------------
    private void configureButtons() {
        deleteButton.setOnAction(e -> deleteSelectedDevice());
        exportButton.setOnAction(e -> exportToExcel());
        importButton.setOnAction(e -> importFromExcel());

        // Добавляем базовые CSS‑классы (уже заданы в FXML, но оставляем на всякий случай)
        deleteButton.getStyleClass().add("button-delete");
        exportButton.getStyleClass().add("button-export");
        importButton.getStyleClass().add("button-import");

        // Добавляем hover‑анимацию через ваш утилитный класс
        StyleUtils.applyHoverAndAnimation(deleteButton,
                "button-delete", "button-delete-hover");
        StyleUtils.applyHoverAndAnimation(exportButton,
                "button-export", "button-export-hover");
        StyleUtils.applyHoverAndAnimation(importButton,
                "button-import", "button-import-hover");
    }

    /** Удаление выбранного прибора. */
    private void deleteSelectedDevice() {
        Device selected = deviceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Выберите прибор для удаления");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить прибор \"" + selected.getName() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.YES) {
                boolean ok = deviceDAO.deleteDevice(selected.getId());
                if (ok) {
                    filteredList.getSource().remove(selected);
                    updateStatistics();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Не удалось удалить запись из БД");
                }
            }
        });
    }

    // -----------------------------------------------------------------
    //   Экспорт / импорт Excel (используем Apache POI, как в MainController)
    // -----------------------------------------------------------------
    private void exportToExcel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт в Excel");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        File file = chooser.showSaveDialog(deviceTable.getScene().getWindow());
        if (file == null) return;

        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = wb.createSheet("Devices");
            String[] headers = {"Тип прибора", "Модель", "Производитель",
                    "Инвентарный №", "Год выпуска", "Предел измерений",
                    "Класс точности", "Место установки", "Кран №",
                    "Статус", "Доп. информация"};
            var headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowNum = 1;
            for (Device d : deviceTable.getItems()) {
                var row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(nullToEmpty(d.getType()));
                row.createCell(1).setCellValue(nullToEmpty(d.getName()));
                row.createCell(2).setCellValue(nullToEmpty(d.getManufacturer()));
                row.createCell(3).setCellValue(nullToEmpty(d.getInventoryNumber()));
                row.createCell(4).setCellValue(d.getYear() == null ? "" : d.getYear().toString());
                row.createCell(5).setCellValue(nullToEmpty(d.getMeasurementLimit()));
                row.createCell(6).setCellValue(d.getAccuracyClass() == null ? 0.0 : d.getAccuracyClass());
                row.createCell(7).setCellValue(nullToEmpty(d.getLocation()));
                row.createCell(8).setCellValue(nullToEmpty(d.getValveNumber()));
                row.createCell(9).setCellValue(nullToEmpty(d.getStatus()));
                row.createCell(10).setCellValue(nullToEmpty(d.getAdditionalInfo()));
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            try (var out = new java.io.FileOutputStream(file)) {
                wb.write(out);
            }
            showAlert(Alert.AlertType.INFORMATION,
                    "Экспорт завершён: " + file.getAbsolutePath());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка экспорта: " + e.getMessage());
        }
    }

    private void importFromExcel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Импорт из Excel");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        File file = chooser.showOpenDialog(deviceTable.getScene().getWindow());
        if (file == null) return;

        try (var fis = new java.io.FileInputStream(file);
             var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(fis)) {

            var sheet = wb.getSheet("Devices");
            if (sheet == null) {
                showAlert(Alert.AlertType.ERROR,
                        "Лист 'Devices' не найден в файле");
                return;
            }

            int imported = 0, updated = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                if (row == null) continue;

                Device d = new Device();
                d.setType(getStringCell(row, 0));
                d.setName(getStringCell(row, 1));
                d.setManufacturer(getStringCell(row, 2));
                d.setInventoryNumber(getStringCell(row, 3));

                String yearStr = getStringCell(row, 4);
                if (!yearStr.isBlank()) {
                    try {
                        d.setYear(Integer.valueOf(yearStr));
                    } catch (NumberFormatException ignored) { }
                }

                d.setMeasurementLimit(getStringCell(row, 5));

                String accStr = getStringCell(row, 6);
                if (!accStr.isBlank()) {
                    try {
                        d.setAccuracyClass(Double.valueOf(accStr));
                    } catch (NumberFormatException ignored) { }
                }

                d.setLocation(getStringCell(row, 7));
                d.setValveNumber(getStringCell(row, 8));
                d.setStatus(getStringCell(row, 9));
                d.setAdditionalInfo(getStringCell(row, 10));
                d.setPhotos(new java.util.ArrayList<>()); // пустой список фото

                // Инвентарный номер обязателен
                if (d.getInventoryNumber() == null || d.getInventoryNumber().isBlank())
                    continue;

                Device existing = deviceDAO.findDeviceByInventoryNumber(d.getInventoryNumber());
                if (existing != null) {
                    // Обновляем существующее
                    existing.setType(d.getType());
                    existing.setName(d.getName());
                    existing.setManufacturer(d.getManufacturer());
                    existing.setYear(d.getYear());
                    existing.setMeasurementLimit(d.getMeasurementLimit());
                    existing.setAccuracyClass(d.getAccuracyClass());
                    existing.setLocation(d.getLocation());
                    existing.setValveNumber(d.getValveNumber());
                    existing.setStatus(d.getStatus());
                    existing.setAdditionalInfo(d.getAdditionalInfo());
                    deviceDAO.updateDevice(existing);
                    updated++;
                } else {
                    // Добавляем новое
                    deviceDAO.addDevice(d);
                    imported++;
                }
            }

            // Перезаполняем список в таблице
            loadDataFromDao();
            updateStatistics();

            showAlert(Alert.AlertType.INFORMATION,
                    "Импорт завершён!\nДобавлено: " + imported +
                            "\nОбновлено: " + updated);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка импорта: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    //   Вспомогательные методы
    // -----------------------------------------------------------------
    private SortedList<Device> createSortedList(FilteredList<Device> filtered,
                                                TableView<Device> table,
                                                TableColumn<Device, String> defaultCol) {
        SortedList<Device> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        return sorted;
    }

    /** Вызывается после каждой фильтрации/добавления/удаления. */
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

    /** Устанавливаем чередующийся фон строк (чётные/нечётные). */
    private void configureRowStyle() {
        deviceTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Device item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setStyle("");
                } else {
                    if (!isSelected()) {
                        setStyle(getIndex() % 2 == 0 ?
                                "-fx-background-color: #b8b8b8;" :
                                "-fx-background-color: white;");
                    } else {
                        setStyle("-fx-background-color: #7abcff;");
                    }
                }
            }
        });
    }

    /** Простейший способ получить строку из ячейки (всегда String). */
    private String getStringCell(org.apache.poi.ss.usermodel.Row row, int colIdx) {
        var cell = row.getCell(colIdx);
        if (cell == null) return "";
        cell.setCellType(org.apache.poi.ss.usermodel.CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void showAlert(Alert.AlertType type, String text) {
        Alert a = new Alert(type, text);
        a.show();
    }
}