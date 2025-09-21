package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.controllers.cell.table_cell.ValidatingDoubleCell;
import com.kipia.management.kipia_management.controllers.cell.table_cell.ValidatingIntegerCell;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.ExcelImportExportUtil;
import com.kipia.management.kipia_management.utils.StyleUtils;
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

import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Контроллер, отвечающий за отображение и работу с таблицей приборов.
 * @author vladimir_shi
 * @since 11.09.2025
 */
public class DevicesTableController {

    // ---------- FXML‑элементы ----------
    @FXML
    private TableView<Device> deviceTable;
    @FXML
    private TextField searchField;
    @FXML
    private Button deleteButton;
    @FXML
    private Button exportButton;
    @FXML
    private Button importButton;

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

    // ---------- Сервисы ----------
    private DeviceDAO deviceDAO;

    // Списки, используемые для фильтрации/сортировки
    private FilteredList<Device> filteredList;

    // Инвентарный номер — колонка, которой будем пользоваться как «default sort»
    private TableColumn<Device, String> inventoryCol;

    // -----------------------------------------------------------------
    //                     PUBLIC API (вызывается из MainController)
    // -----------------------------------------------------------------

    /**
     * Передаём DAO, получаемый из главного окна.
     */
    public void setDeviceDAO(DeviceDAO dao) {
        this.deviceDAO = dao;
    }

    /**
     * Метод, вызываемый после загрузки FXML.
     */
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

    /**
     * Создаём все колонки, используя фабричные методы.
     */
    private void createTableColumns() {

        //  Текстовые колонки
        TableColumn<Device, String> typeCol = createEditableStringColumn(
                "Тип прибора", "type", 100,
                Device::setType);

        TableColumn<Device, String> nameCol = createEditableStringColumn(
                "Модель", "name", 75,
                Device::setName);

        TableColumn<Device, String> manufacturerCol = createEditableStringColumn(
                "Завод изготовитель", "manufacturer", 115,
                Device::setManufacturer);

        inventoryCol = createEditableStringColumn(
                "Инв. №", "inventoryNumber", 70,
                Device::setInventoryNumber);

        TableColumn<Device, String> measurementLimitCol = createEditableStringColumn(
                "Предел измерений", "measurementLimit", 100,
                Device::setMeasurementLimit);

        TableColumn<Device, String> locationCol = createEditableStringColumn(
                "Место установки", "location", 110,
                Device::setLocation);

        TableColumn<Device, String> valveNumberCol = createEditableStringColumn(
                "Кран №", "valveNumber", 70,
                Device::setValveNumber);

        TableColumn<Device, String> additionalInfoCol = createEditableStringColumn(
                "Доп. информация", "additionalInfo", 150,
                Device::setAdditionalInfo);

        //  Числовые колонки
        TableColumn<Device, Integer> yearCol = createYearColumn();
        TableColumn<Device, Double> accuracyClassCol = createAccuracyClassColumn();

        // Статус – ComboBox
        TableColumn<Device, String> statusCol = new TableColumn<>("Статус");
        statusCol.setPrefWidth(70);
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(ComboBoxTableCell.forTableColumn(
                "Хранение", "В работе", "Утерян", "Испорчен"));
        statusCol.setOnEditCommit(event -> {
            Device dev = event.getRowValue();
            dev.setStatus(event.getNewValue());
            deviceDAO.updateDevice(dev);
            updateStatistics();
        });

        // Фото – две кнопки «Добавить» / «Просмотр»
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

    /**
     * Колонка, редактируемая через ValidatingIntegerCell
     * @return - колонка
     */
    private TableColumn<Device, Integer> createYearColumn() {
        TableColumn<Device, Integer> yearCol = new TableColumn<>("Год выпуска");
        yearCol.setCellValueFactory(new PropertyValueFactory<>("year"));
        yearCol.setPrefWidth(90);
        yearCol.setCellFactory(col -> {
            ValidatingIntegerCell cell = new ValidatingIntegerCell();
            cell.getStyleClass().add("numeric-cell");
            return cell;
        });
        yearCol.setEditable(true);
        yearCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setYear(event.getNewValue());
            deviceDAO.updateDevice(device);
        });
        return yearCol;
    }

    /**
     * Колонка, редактируемая через ValidatingDoubleCell
     * @return - колонка
     */
    private TableColumn<Device, Double> createAccuracyClassColumn() {
        TableColumn<Device, Double> accuracyClassCol = new TableColumn<>("Класс точности");
        accuracyClassCol.setCellValueFactory(new PropertyValueFactory<>("accuracyClass"));
        accuracyClassCol.setPrefWidth(90);
        accuracyClassCol.setCellFactory(col -> {
            ValidatingDoubleCell cell = new ValidatingDoubleCell();
            cell.getStyleClass().add("numeric-cell");
            return cell;
        });
        accuracyClassCol.setEditable(true);
        accuracyClassCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setAccuracyClass(event.getNewValue());
            deviceDAO.updateDevice(device);
        });
        return accuracyClassCol;
    }

    /**
     * Текстовая колонка, редактируемая через TextFieldTableCell.
     */
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

    /**
     * Фабрика ячейки для колонки «Фото».
     */
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
        SortedList<Device> sorted = createSortedList(filteredList, deviceTable);
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
                        || (dev.getManufacturer() != null && dev.getManufacturer().toLowerCase().contains(lower))
                        || (dev.getLocation() != null && dev.getLocation().toLowerCase().contains(lower))
                        || (dev.getInventoryNumber() != null && dev.getInventoryNumber().toLowerCase().contains(lower))
                        || (dev.getYear() != null && String.valueOf(dev.getYear()).contains(lower))
                        || (dev.getMeasurementLimit() != null && dev.getMeasurementLimit().toLowerCase().contains(lower))
                        || (dev.getAccuracyClass() != null && String.valueOf(dev.getAccuracyClass()).contains(lower))
                        || (dev.getValveNumber() != null && dev.getValveNumber().toLowerCase().contains(lower))
                        || (dev.getStatus() != null && dev.getStatus().toLowerCase().contains(lower))
                        || (dev.getAdditionalInfo() != null && dev.getAdditionalInfo().toLowerCase().contains(lower));

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

    /**
     * Удаление выбранного прибора.
     */
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
    //   Экспорт / импорт Excel (используем Apache POI)
    // -----------------------------------------------------------------
    private void exportToExcel() {
        ExcelImportExportUtil.exportDevicesToExcel(deviceTable.getScene().getWindow(), deviceTable.getItems());
    }

    private void importFromExcel() {
        ExcelImportExportUtil.importDevicesFromExcel(deviceTable.getScene().getWindow(), deviceDAO,
                () -> {
                    loadDataFromDao();
                    updateStatistics();
                },
                () -> {
                    // Ошибка при импорте
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Ошибка импорта");
                    errorAlert.setHeaderText("Не удалось импортировать данные из Excel");
                    errorAlert.setContentText("""
                            Возможные причины:
                            - Неправильный формат файла (проверьте заголовки и типы данных).
                            - Ошибка доступа базе данных.
                            - Инвентарный номер не указан или дублируется.
                            
                            Попробуйте проверить файл и повторить.""");
                    errorAlert.showAndWait();
                }
        );
    }

    // -----------------------------------------------------------------
    //   Вспомогательные методы
    // -----------------------------------------------------------------

    /**
     * Метод для создания отсортированного списка
     *
     * @param filtered - отфильтрованный список
     * @param table    - таблица
     * @return - отсортированный список
     */
    private SortedList<Device> createSortedList(FilteredList<Device> filtered,
                                                TableView<Device> table) {
        SortedList<Device> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        return sorted;
    }

    /**
     * Вызывается после каждой фильтрации/добавления/удаления.
     */
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

    /**
     * Устанавливаем чередующийся фон строк (чётные/нечётные).
     */
    private void configureRowStyle() {
        deviceTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Device item, boolean empty) {
                super.updateItem(item, empty);
                // Очищаем старые подобные классы
                getStyleClass().removeAll("even-row", "odd-row", "selected-row");
                if (empty) {
                    // Пустая строка — ничего не добавляем
                    return;
                }
                if (isSelected()) {
                    getStyleClass().add("selected-row");
                } else {
                    // Чередующиеся цвета
                    if (getIndex() % 2 == 0) {
                        getStyleClass().add("even-row");
                    } else {
                        getStyleClass().add("odd-row");
                    }
                }
            }
        });
    }

    /**
     * Метод для вывода уведомления
     *
     * @param type - тип уведомления
     * @param text - текст уведомления
     */
    private void showAlert(Alert.AlertType type, String text) {
        Alert a = new Alert(type, text);
        a.show();
    }
}