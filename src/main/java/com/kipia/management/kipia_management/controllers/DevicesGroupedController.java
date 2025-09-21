package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.ExcelImportExportUtil;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTreeTableCell;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.converter.DefaultStringConverter;

import java.io.File;
import java.util.*;

public class DevicesGroupedController {

    @FXML
    private TreeTableView<TreeRowItem> treeTable;
    @FXML
    private TextField searchField;
    @FXML
    private Label totalDevicesLabel, workingDevicesLabel, storageDevicesLabel, lostDevicesLabel, brokenDevicesLabel;
    @FXML
    private Button deleteButton, exportButton, importButton;

    private DeviceDAO deviceDAO;
    private FilteredList<Device> filteredList;

    public void setDeviceDAO(DeviceDAO dao) {
        this.deviceDAO = dao;
    }

    @FXML
    private void initialize() {
        treeTable.setEditable(true);
        treeTable.setShowRoot(false);
        configureColumns();
        configureSearch();
        configureButtons();
        configureRowFactory();
    }

    public void init() {
        loadData();
    }

    // --- Типы строк дерева ---

    public sealed interface TreeRowItem permits GroupItem, DeviceItem {
    }

    public static final class GroupItem implements TreeRowItem {
        private final String location;

        public GroupItem(String location) {
            this.location = location;
        }

        public String getLocation() {
            return location;
        }
    }

    public static final class DeviceItem implements TreeRowItem {
        private final Device device;

        public DeviceItem(Device device) {
            this.device = device;
        }

        public Device getDevice() {
            return device;
        }
    }

    // --- Колонки с фабриками ячеек с проверкой редактирования ---

    private void configureColumns() {
        treeTable.getColumns().clear();

        // Колонка "Тип прибора" - групповая строка "Местоположение"
        TreeTableColumn<TreeRowItem, String> locationCol = getTreeRowTableForLocation();

        // Колонка "Модель"
        TreeTableColumn<TreeRowItem, String> nameCol = createEditableStringColumn("Модель", 75,
                Device::getName,
                Device::setName);

        //Колонка "Производитель"
        TreeTableColumn<TreeRowItem, String> manufacturerCol = createEditableStringColumn("Завод изготовитель", 115,
                Device::getManufacturer,
                Device::setManufacturer);

        //Колонка "Инвентарный номер"
        TreeTableColumn<TreeRowItem, String> inventoryCol = createEditableStringColumn("Инв. №", 70,
                Device::getInventoryNumber,
                Device::setInventoryNumber);

        // Год выпуска - числовая колонка с кастомной ячейкой и проверкой
        TreeTableColumn<TreeRowItem, Integer> yearCol = new TreeTableColumn<>("Год выпуска");
        yearCol.setPrefWidth(90);
        yearCol.setCellValueFactory(param -> {
            TreeRowItem val = param.getValue().getValue();
            if (val instanceof DeviceItem deviceItem) {
                return new ReadOnlyObjectWrapper<>(deviceItem.getDevice().getYear());
            } else {
                return new ReadOnlyObjectWrapper<>(null);
            }
        });
        yearCol.setCellFactory(col -> new com.kipia.management.kipia_management.controllers.cell.tree_table_cell.ValidatingIntegerTreeCell() {
            {
                getStyleClass().add("numeric-cell");
            }

            @Override
            public void updateItem(Integer item, boolean empty) {
                TreeRowItem val = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                if (!(val instanceof DeviceItem)) {
                    setEditable(false);
                    setText(null);
                    setGraphic(null);
                } else {
                    setEditable(true);
                    super.updateItem(item, empty);
                }
            }

            @Override
            public void startEdit() {
                TreeRowItem val = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                if (!(val instanceof DeviceItem)) {
                    cancelEdit();
                } else {
                    super.startEdit();
                }
            }
        });
        yearCol.setEditable(true);
        yearCol.setOnEditCommit(event -> {
            TreeRowItem val = event.getRowValue().getValue();
            if (val instanceof DeviceItem deviceItem) {
                deviceItem.getDevice().setYear(event.getNewValue());
                deviceDAO.updateDevice(deviceItem.getDevice());
                treeTable.refresh();
            }
        });

        // Колонка "Предел измерений"
        TreeTableColumn<TreeRowItem, String> measurementLimitCol = createEditableStringColumn("Предел измерений", 100,
                Device::getMeasurementLimit,
                Device::setMeasurementLimit);

        // Колонка "Класс точности" - числовая колонка с валидацией
        TreeTableColumn<TreeRowItem, Double> accuracyClassCol = new TreeTableColumn<>("Класс точности");
        accuracyClassCol.setPrefWidth(90);
        accuracyClassCol.setCellValueFactory(param -> {
            TreeRowItem val = param.getValue().getValue();
            if (val instanceof DeviceItem deviceItem) {
                return new ReadOnlyObjectWrapper<>(deviceItem.getDevice().getAccuracyClass());
            } else {
                return new ReadOnlyObjectWrapper<>(null);
            }
        });
        accuracyClassCol.setCellFactory(col -> new com.kipia.management.kipia_management.controllers.cell.tree_table_cell.ValidatingDoubleTreeCell() {
            {
                getStyleClass().add("numeric-cell");
            }

            @Override
            public void updateItem(Double item, boolean empty) {
                TreeRowItem val = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                if (!(val instanceof DeviceItem)) {
                    setEditable(false);
                    setText(null);
                    setGraphic(null);
                } else {
                    setEditable(true);
                    super.updateItem(item, empty);
                }
            }

            @Override
            public void startEdit() {
                TreeRowItem val = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                if (!(val instanceof DeviceItem)) {
                    cancelEdit();
                } else {
                    super.startEdit();
                }
            }
        });
        accuracyClassCol.setEditable(true);
        accuracyClassCol.setOnEditCommit(event -> {
            TreeRowItem val = event.getRowValue().getValue();
            if (val instanceof DeviceItem deviceItem) {
                deviceItem.getDevice().setAccuracyClass(event.getNewValue());
                deviceDAO.updateDevice(deviceItem.getDevice());
                treeTable.refresh();
            }
        });

        // Колонка "Кран №"
        TreeTableColumn<TreeRowItem, String> valveNumberCol = createEditableStringColumn("Кран №", 70,
                Device::getValveNumber,
                Device::setValveNumber);

        // Колонка Статус с ComboBox и запретом редактирования для групп
        TreeTableColumn<TreeRowItem, String> statusCol = new TreeTableColumn<>("Статус");
        statusCol.setPrefWidth(70);
        statusCol.setCellValueFactory(param -> {
            TreeRowItem val = param.getValue().getValue();
            if (val instanceof DeviceItem deviceItem)
                return new ReadOnlyObjectWrapper<>(deviceItem.getDevice().getStatus());
            else
                return new ReadOnlyObjectWrapper<>("");
        });

        statusCol.setCellFactory(column -> new ComboBoxTreeTableCell<TreeRowItem, String>("Хранение", "В работе", "Утерян", "Испорчен") {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setEditable(false);
                } else {
                    TreeRowItem val = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                    if (val instanceof GroupItem) {
                        setEditable(false);
                        setText(item);
                        // Убираем комбобокс для групповых строк
                        setGraphic(null);
                        setStyle("-fx-alignment: center; -fx-font-weight: bold;");
                    } else {
                        setEditable(true);
                        setStyle("");
                    }
                }
            }

            @Override
            public void startEdit() {
                TreeRowItem val = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                if (val instanceof GroupItem) {
                    cancelEdit();
                } else {
                    super.startEdit();
                }
            }
        });

        statusCol.setEditable(true);
        statusCol.setOnEditCommit(event -> {
            TreeRowItem val = event.getRowValue().getValue();
            if (val instanceof DeviceItem deviceItem) {
                deviceItem.getDevice().setStatus(event.getNewValue());
                deviceDAO.updateDevice(deviceItem.getDevice());
                updateStatistics();
                treeTable.refresh();
            }
        });

        //Колонка "Фото"
        TreeTableColumn<TreeRowItem, Void> photoCol = new TreeTableColumn<>("Фото");
        photoCol.setPrefWidth(145);
        photoCol.setCellFactory(createPhotoCellFactory());

        // Колонка "Доп. Информация."
        TreeTableColumn<TreeRowItem, String> additionalInfoCol = createEditableStringColumn("Доп. информация", 205,
                Device::getAdditionalInfo,
                Device::setAdditionalInfo);

        // Добавление колонок в таблицу
        treeTable.getColumns().addAll(locationCol, nameCol, manufacturerCol, inventoryCol, yearCol, measurementLimitCol, accuracyClassCol, valveNumberCol, statusCol, photoCol, additionalInfoCol);
    }

    private Callback<TreeTableColumn<TreeRowItem, Void>, TreeTableCell<TreeRowItem, Void>> createPhotoCellFactory() {
        return col -> new TreeTableCell<>() {
            private final Button addBtn = new Button("Добавить");
            private final Button viewBtn = new Button("Просмотр");

            {
                addBtn.getStyleClass().addAll("table-button-add");
                viewBtn.getStyleClass().addAll("table-button-view");
                StyleUtils.applyHoverAndAnimation(addBtn,
                        "table-button-add", "table-button-add-hover");
                StyleUtils.applyHoverAndAnimation(viewBtn,
                        "table-button-view", "table-button-view-hover");
                addBtn.setPrefSize(65, 22);
                viewBtn.setPrefSize(65, 22);

                addBtn.setOnAction(e -> {
                    Device device = getCurrentDevice();
                    if (device != null) {
                        addPhoto(device);
                    }
                });

                viewBtn.setOnAction(e -> {
                    Device device = getCurrentDevice();
                    if (device != null) {
                        viewPhotos(device);
                    }
                });
            }

            private Device getCurrentDevice() {
                TreeRowItem rowItem = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                if (rowItem instanceof DeviceItem deviceItem) {
                    return deviceItem.getDevice();
                }
                return null;
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
                    treeTable.refresh();
                }
            }

            private void viewPhotos(Device device) {
                List<String> photos = device.getPhotos();
                if (photos == null || photos.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Фотографии не добавлены");
                    alert.show();
                    return;
                }
                Stage stage = new Stage();
                stage.setTitle("Фото прибора: " + device.getName());
                VBox vbox = new VBox(10);
                for (String path : photos) {
                    try {
                        Image img = new Image("file:" + path);
                        ImageView iv = new ImageView(img);
                        iv.setFitWidth(250);
                        iv.setFitHeight(250);
                        iv.setPreserveRatio(true);
                        vbox.getChildren().add(iv);
                    } catch (Exception ex) {
                        vbox.getChildren().add(new Label("Ошибка загрузки: " + path));
                    }
                }
                stage.setScene(new javafx.scene.Scene(new ScrollPane(vbox), 300, 600));
                stage.show();
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    // Показываем кнопки только для DeviceItem, для GroupItem пусто
                    if (getCurrentDevice() != null) {
                        setGraphic(new HBox(8, addBtn, viewBtn));
                    } else {
                        setGraphic(null);
                    }
                }
            }
        };
    }

    private TreeTableColumn<TreeRowItem, String> getTreeRowTableForLocation() {
        TreeTableColumn<TreeRowItem, String> locationCol = new TreeTableColumn<>("Тип прибора");
        locationCol.setPrefWidth(100);
        locationCol.setCellValueFactory(param -> {
            TreeRowItem val = param.getValue().getValue();
            if (val instanceof GroupItem g) return new ReadOnlyObjectWrapper<>(g.getLocation());
            else if (val instanceof DeviceItem d) return new ReadOnlyObjectWrapper<>(d.getDevice().getType());
            else return new ReadOnlyObjectWrapper<>("");
        });
        locationCol.setCellFactory(col -> new TextFieldTreeTableCell<>(new DefaultStringConverter()) {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    setEditable(false);
                } else {
                    TreeRowItem val = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                    if (val instanceof GroupItem) {
                        setText(item);
                        setEditable(false);
                        setAlignment(Pos.CENTER_LEFT); // Левое выравнивание для групповых строк
                        setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 8 5 0;");
                    } else {
                        setText(item);
                        setEditable(true);
                        setStyle("");
                    }
                }
            }

            @Override
            public void startEdit() {
                TreeRowItem val = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                if (val instanceof GroupItem) cancelEdit();
                else super.startEdit();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                TreeRowItem val = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                if (val instanceof GroupItem) {
                    setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-background-color: #e3f2fd; " +
                            "-fx-padding: 8 8 5 0;");
                    setAlignment(Pos.CENTER_LEFT); // Устанавливаем заново при отмене редактирования
                } else {
                    setStyle("");
                }
            }
        });
        locationCol.setEditable(true);
        locationCol.setOnEditCommit(event -> {
            TreeRowItem val = event.getRowValue().getValue();
            if (val instanceof DeviceItem deviceItem) {
                deviceItem.getDevice().setType(event.getNewValue());
                deviceDAO.updateDevice(deviceItem.getDevice());
                treeTable.refresh();
            }
        });
        return locationCol;
    }

    // Метод создания текстовых колонок с блокировкой редактирования групп
    private TreeTableColumn<TreeRowItem, String> createEditableStringColumn(String title, double width,
                                                                            java.util.function.Function<Device, String> getter,
                                                                            java.util.function.BiConsumer<Device, String> setter) {
        TreeTableColumn<TreeRowItem, String> col = new TreeTableColumn<>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(param -> {
            TreeRowItem val = param.getValue().getValue();
            if (val instanceof DeviceItem deviceItem) {
                return new ReadOnlyObjectWrapper<>(getter.apply(deviceItem.getDevice()));
            }
            return new ReadOnlyObjectWrapper<>("");
        });
        col.setCellFactory(column -> new TextFieldTreeTableCell<>(new DefaultStringConverter()) {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setEditable(false);
                } else {
                    TreeRowItem val = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                    if (val instanceof GroupItem) {
                        setEditable(false);
                        setText(item);
                    } else {
                        setEditable(true);
                    }
                }
            }

            @Override
            public void startEdit() {
                TreeRowItem val = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                if (val instanceof GroupItem) {
                    cancelEdit();
                } else {
                    super.startEdit();
                }
            }
        });
        col.setEditable(true);
        col.setOnEditCommit(event -> {
            TreeRowItem val = event.getRowValue().getValue();
            if (val instanceof DeviceItem deviceItem) {
                setter.accept(deviceItem.getDevice(), event.getNewValue());
                deviceDAO.updateDevice(deviceItem.getDevice());
                treeTable.refresh();
            }
        });
        return col;
    }

    // --- Загрузка и обработка данных ---

    private void loadData() {
        if (deviceDAO == null) return;
        List<Device> devices = deviceDAO.getAllDevices();
        filteredList = new FilteredList<>(FXCollections.observableArrayList(devices), d -> true);
        updateTreeItems();
        updateStatistics();
    }

    private void updateTreeItems() {
        if (filteredList == null) return;

        Map<String, List<Device>> grouped = new TreeMap<>();
        for (Device d : filteredList) {
            String location = d.getLocation();
            if (location == null || location.isEmpty()) location = "Без места";
            grouped.computeIfAbsent(location, k -> new ArrayList<>()).add(d);
        }

        TreeItem<TreeRowItem> root = new TreeItem<>();
        root.setExpanded(true);
        for (Map.Entry<String, List<Device>> entry : grouped.entrySet()) {
            TreeItem<TreeRowItem> groupNode = new TreeItem<>(new GroupItem(entry.getKey()));
            groupNode.setExpanded(true);
            for (Device dev : entry.getValue()) {
                groupNode.getChildren().add(new TreeItem<>(new DeviceItem(dev)));
            }
            root.getChildren().add(groupNode);
        }
        treeTable.setRoot(root);
    }

    private void configureSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal.toLowerCase().trim();
            if (filteredList != null) {
                if (filter.isEmpty()) filteredList.setPredicate(d -> true);
                else filteredList.setPredicate(d ->
                        (d.getName() != null && d.getName().toLowerCase().contains(filter)) ||
                                (d.getType() != null && d.getType().toLowerCase().contains(filter)) ||
                                (d.getLocation() != null && d.getLocation().toLowerCase().contains(filter) ||
                                        (d.getYear() != null && String.valueOf(d.getYear()).contains(filter)) ||
                                        (d.getManufacturer() != null && d.getManufacturer().toLowerCase().contains(filter)) ||
                                        (d.getInventoryNumber() != null && d.getInventoryNumber().toLowerCase().contains(filter)) ||
                                        (d.getValveNumber() != null && d.getValveNumber().toLowerCase().contains(filter)) ||
                                        (d.getStatus() != null && d.getStatus().toLowerCase().contains(filter)) ||
                                        (d.getAdditionalInfo() != null && d.getAdditionalInfo().toLowerCase().contains(filter)) ||
                                        (d.getMeasurementLimit() != null && d.getMeasurementLimit().toLowerCase().contains(filter)) ||
                                        (d.getAccuracyClass() != null && String.valueOf(d.getAccuracyClass()).contains(filter)))
                );
                updateTreeItems();
                updateStatistics();
            }
        });
    }

    private void configureButtons() {
        if (deleteButton != null) {
            StyleUtils.applyHoverAndAnimation(deleteButton, "button-delete", "button-delete-hover");
            deleteButton.setOnAction(e -> deleteSelectedDevice());
        }
        if (exportButton != null) {
            StyleUtils.applyHoverAndAnimation(exportButton, "button-export", "button-export-hover");
            exportButton.setOnAction(e ->
                    ExcelImportExportUtil.exportGroupedTreeTableToExcel(treeTable.getScene().getWindow(), treeTable));
        }
        if (importButton != null) {
            StyleUtils.applyHoverAndAnimation(importButton, "button-import", "button-import-hover");
            importButton.setOnAction(e ->
                    ExcelImportExportUtil.importGroupedTreeTableFromExcel(treeTable.getScene().getWindow(), deviceDAO, treeTable,
                            () -> {
                                loadData();
                                updateStatistics();
                            },
                            () -> {
                                Alert error = new Alert(Alert.AlertType.ERROR);
                                error.setTitle("Ошибка импорта");
                                error.setHeaderText(null);
                                error.setContentText("Не удалось импортировать данные из Excel");
                                error.showAndWait();
                            }));
        }
    }

    private void deleteSelectedDevice() {
        TreeItem<TreeRowItem> selected = treeTable.getSelectionModel().getSelectedItem();
        if (selected == null || !(selected.getValue() instanceof DeviceItem)) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Выберите прибор для удаления");
            alert.show();
            return;
        }
        Device dev = ((DeviceItem) selected.getValue()).getDevice();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить прибор \"" + dev.getName() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(b -> {
            if (b == ButtonType.YES) {
                boolean success = deviceDAO.deleteDevice(dev.getId());
                if (success) {
                    filteredList.getSource().remove(dev);
                    updateTreeItems();
                    updateStatistics();
                } else {
                    Alert error = new Alert(Alert.AlertType.ERROR, "Ошибка удаления из базы");
                    error.show();
                }
            }
        });
    }

    private void configureRowFactory() {
        treeTable.setRowFactory(tv -> new TreeTableRow<>() {
            @Override
            protected void updateItem(TreeRowItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("even-row", "odd-row", "selected-row", "group-row");
                if (empty || item == null) return;
                if (isSelected()) {
                    getStyleClass().add("selected-row");
                } else if (item instanceof GroupItem) {
                    getStyleClass().add("group-row");
                    // Убрали setAlignment — теперь в ячейках
                } else {
                    if (getIndex() % 2 == 0) {
                        getStyleClass().add("even-row");
                    } else {
                        getStyleClass().add("odd-row");
                    }
                }
            }
        });
    }

    private void updateStatistics() {
        if (filteredList == null) return;
        int total = filteredList.size();
        long working = filteredList.stream().filter(d -> "В работе".equalsIgnoreCase(d.getStatus())).count();
        long storage = filteredList.stream().filter(d -> "Хранение".equalsIgnoreCase(d.getStatus())).count();
        long lost = filteredList.stream().filter(d -> "Утерян".equalsIgnoreCase(d.getStatus())).count();
        long broken = filteredList.stream().filter(d -> "Испорчен".equalsIgnoreCase(d.getStatus())).count();

        totalDevicesLabel.setText(String.valueOf(total));
        workingDevicesLabel.setText(String.valueOf(working));
        storageDevicesLabel.setText(String.valueOf(storage));
        lostDevicesLabel.setText(String.valueOf(lost));
        brokenDevicesLabel.setText(String.valueOf(broken));
    }
}