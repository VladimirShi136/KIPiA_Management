package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.controllers.cell.tree_table_cell.ValidatingDoubleTreeCell;
import com.kipia.management.kipia_management.controllers.cell.tree_table_cell.ValidatingIntegerTreeCell;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.ExcelImportExportUtil;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTreeTableCell;
import javafx.scene.control.cell.TextFieldTreeTableCell;

import java.util.*;
import java.util.function.Predicate;

/**
 * Контроллер для сгруппированной таблицы приборов по локациям
 *
 * @author vladimir_shi
 * @since 13.09.2025
 */

public class DevicesGroupedController {

    @FXML
    private TreeTableView<Object> treeTable;

    @FXML
    private TextField searchField;

    @FXML
    private Label totalDevicesLabel, workingDevicesLabel, storageDevicesLabel, lostDevicesLabel, brokenDevicesLabel;

    @FXML
    private Button deleteButton;

    @FXML
    private Button exportButton;

    @FXML
    private Button importButton;

    private DeviceDAO deviceDAO;
    private FilteredList<Device> filteredList;

    public void setDeviceDAO(DeviceDAO dao) {
        this.deviceDAO = dao;
    }

    @FXML
    private void initialize() {
        configureTreeTableColumns();
        configureSearch();
        configureButtons();
        configureRowStyle();
        updateStatistics();
    }

    public void init() {
        loadData();
    }

    private void configureTreeTableColumns() {
        treeTable.setShowRoot(false);

        // Колонки по аналогии с твоей таблицей
        TreeTableColumn<Object, String> typeCol = createEditableStringColumn("Тип прибора", Device::getType, Device::setType);
        TreeTableColumn<Object, String> nameCol = createEditableStringColumn("Модель", Device::getName, Device::setName);
        TreeTableColumn<Object, String> manufacturerCol = createEditableStringColumn("Производитель", Device::getManufacturer, Device::setManufacturer);
        TreeTableColumn<Object, String> inventoryCol = createEditableStringColumn("Инвентарный №", Device::getInventoryNumber, Device::setInventoryNumber);
        TreeTableColumn<Object, String> measurementLimitCol = createEditableStringColumn("Предел измерений", Device::getMeasurementLimit, Device::setMeasurementLimit);
        TreeTableColumn<Object, String> locationCol = new TreeTableColumn<>("Место установки");
        locationCol.setPrefWidth(110);
        locationCol.setCellValueFactory(param -> {
            Object val = param.getValue().getValue();
            if (val instanceof Device d) return new ReadOnlyObjectWrapper<>(d.getLocation());
            if (val instanceof String loc) return new ReadOnlyObjectWrapper<>(loc);
            return new ReadOnlyObjectWrapper<>("");
        });
        locationCol.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTreeTableRow() == null) {
                    setText(null);
                    setStyle("");
                } else {
                    TreeItem<Object> treeItem = getTreeTableRow().getTreeItem();
                    if (treeItem != null && treeItem.getValue() instanceof String) {
                        setText(item);
                        setStyle("-fx-font-weight: bold; -fx-alignment: CENTER; -fx-font-size: 14px;");
                        getTableColumn().setStyle("-fx-alignment: CENTER;");
                    } else {
                        setText(item);
                        setStyle("");
                        getTableColumn().setStyle("");
                    }
                }
            }
        });

        TreeTableColumn<Object, String> valveNumberCol = createEditableStringColumn("Кран №", Device::getValveNumber, Device::setValveNumber);
        TreeTableColumn<Object, String> additionalInfoCol = createEditableStringColumn("Доп. информация", Device::getAdditionalInfo, Device::setAdditionalInfo);

        // Статус с ComboBox
        TreeTableColumn<Object, String> statusCol = new TreeTableColumn<>("Статус");
        statusCol.setPrefWidth(80);
        statusCol.setCellValueFactory(param -> {
            Object val = param.getValue().getValue();
            if (val instanceof Device d) return new ReadOnlyObjectWrapper<>(d.getStatus());
            return new ReadOnlyObjectWrapper<>("");
        });
        statusCol.setCellFactory(ComboBoxTreeTableCell.forTreeTableColumn("Хранение", "В работе", "Утерян", "Испорчен"));
        statusCol.setOnEditCommit(event -> {
            TreeItem<Object> item = event.getTreeTablePosition().getTreeItem();
            Object val = item.getValue();
            if (val instanceof Device d) {
                d.setStatus(event.getNewValue());
                deviceDAO.updateDevice(d);
                updateStatistics();
            }
        });

        // Год выпуска с валидацией
        TreeTableColumn<Object, Integer> yearCol = new TreeTableColumn<>("Год выпуска");
        yearCol.setPrefWidth(80);
        yearCol.setCellValueFactory(param -> {
            Object val = param.getValue().getValue();
            if (val instanceof Device d && d.getYear() != null) {
                return new ReadOnlyObjectWrapper<>(d.getYear());
            }
            return new ReadOnlyObjectWrapper<>(null);
        });
        yearCol.setCellFactory(col -> new ValidatingIntegerTreeCell());
        yearCol.setOnEditCommit(event -> {
            TreeItem<Object> item = event.getTreeTablePosition().getTreeItem();
            Object val = item.getValue();
            if (val instanceof Device d) {
                d.setYear(event.getNewValue());
                deviceDAO.updateDevice(d);
            }
        });

        // Класс точности с валидацией
        TreeTableColumn<Object, Double> accuracyClassCol = new TreeTableColumn<>("Класс точности");
        accuracyClassCol.setPrefWidth(100);
        accuracyClassCol.setCellValueFactory(param -> {
            Object val = param.getValue().getValue();
            if (val instanceof Device d && d.getAccuracyClass() != null) {
                return new ReadOnlyObjectWrapper<>(d.getAccuracyClass());
            }
            return new ReadOnlyObjectWrapper<>(null);
        });
        accuracyClassCol.setCellFactory(col -> new ValidatingDoubleTreeCell());
        accuracyClassCol.setOnEditCommit(event -> {
            TreeItem<Object> item = event.getTreeTablePosition().getTreeItem();
            Object val = item.getValue();
            if (val instanceof Device d) {
                d.setAccuracyClass(event.getNewValue());
                deviceDAO.updateDevice(d);
            }
        });

        treeTable.getColumns().addAll(
                typeCol, nameCol, manufacturerCol, inventoryCol, yearCol,
                measurementLimitCol, accuracyClassCol, locationCol,
                valveNumberCol, statusCol, additionalInfoCol
        );

        treeTable.setEditable(true);
    }

    private TreeTableColumn<Object, String> createEditableStringColumn(String title,
                                                                       java.util.function.Function<Device, String> getter,
                                                                       java.util.function.BiConsumer<Device, String> setter) {
        TreeTableColumn<Object, String> col = new TreeTableColumn<>(title);
        col.setPrefWidth(120);
        col.setCellValueFactory(param -> {
            Object val = param.getValue().getValue();
            if (val instanceof Device d) return new ReadOnlyObjectWrapper<>(getter.apply(d));
            return new ReadOnlyObjectWrapper<>("");
        });
        col.setCellFactory(TextFieldTreeTableCell.forTreeTableColumn());
        col.setOnEditCommit(event -> {
            TreeItem<Object> item = event.getTreeTablePosition().getTreeItem();
            Object val = item.getValue();
            if (val instanceof Device d) {
                setter.accept(d, event.getNewValue());
                deviceDAO.updateDevice(d);
            }
        });
        return col;
    }

    private void configureSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal.toLowerCase().trim();
            if (filteredList != null) {
                filteredList.setPredicate(createFilterPredicate(filter));
                updateTreeItems();
                updateStatistics();
            }
        });
    }

    private Predicate<Device> createFilterPredicate(String filter) {
        if (filter == null || filter.isEmpty()) return d -> true;
        return d -> (d.getName() != null && d.getName().toLowerCase().contains(filter))
                || (d.getType() != null && d.getType().toLowerCase().contains(filter))
                || (d.getLocation() != null && d.getLocation().toLowerCase().contains(filter));
    }

    private void loadData() {
        if (deviceDAO == null) {
            System.err.println("DeviceDAO не установлен");
            return;
        }
        var devices = deviceDAO.getAllDevices();
        filteredList = new FilteredList<>(FXCollections.observableArrayList(devices), d -> true);
        updateTreeItems();
        updateStatistics();
    }

    private void updateTreeItems() {
        Map<String, List<Device>> grouped = new TreeMap<>();
        for (Device d : filteredList) {
            String loc = d.getLocation() != null && !d.getLocation().isEmpty() ? d.getLocation() : "Без места";
            grouped.computeIfAbsent(loc, k -> new ArrayList<>()).add(d);
        }

        var root = new TreeItem<>();
        root.setExpanded(true);

        for (Map.Entry<String, List<Device>> entry : grouped.entrySet()) {
            TreeItem<Object> groupNode = new TreeItem<>(entry.getKey());
            groupNode.setExpanded(true);
            for (Device d : entry.getValue()) {
                TreeItem<Object> deviceNode = new TreeItem<>(d);
                groupNode.getChildren().add(deviceNode);
            }
            root.getChildren().add(groupNode);
        }
        treeTable.setRoot(root);
    }

    private void configureButtons() {
        if (deleteButton != null) {
            StyleUtils.applyHoverAndAnimation(deleteButton, "button-delete", "button-delete-hover");
            deleteButton.setOnAction(e -> deleteSelectedDevice());
        }
        if (exportButton != null) {
            StyleUtils.applyHoverAndAnimation(exportButton, "button-export", "button-export-hover");
            exportButton.setOnAction(e -> exportToExcel());
        }
        if (importButton != null) {
            StyleUtils.applyHoverAndAnimation(importButton, "button-import", "button-import-hover");
            importButton.setOnAction(e -> importFromExcel());
        }
    }

    private void deleteSelectedDevice() {
        TreeItem<Object> selectedItem = treeTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null || !(selectedItem.getValue() instanceof Device)) {
            showAlert(Alert.AlertType.WARNING, "Выберите прибор для удаления");
            return;
        }
        Device selectedDevice = (Device) selectedItem.getValue();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить прибор \"" + selectedDevice.getName() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                boolean ok = deviceDAO.deleteDevice(selectedDevice.getId());
                if (ok) {
                    filteredList.getSource().remove(selectedDevice);
                    updateTreeItems();
                    updateStatistics();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Не удалось удалить запись из БД");
                }
            }
        });
    }

    private void exportToExcel() {
        // Чтобы экспортировать приборы, соберём все устройства из filteredList
        ExcelImportExportUtil.exportDevicesToExcel(treeTable.getScene().getWindow(), filteredList);
    }

    private void importFromExcel() {
        ExcelImportExportUtil.importDevicesFromExcel(treeTable.getScene().getWindow(), deviceDAO,
                () -> {
                    loadData();
                    updateStatistics();
                },
                () -> {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Ошибка импорта");
                    errorAlert.setHeaderText("Не удалось импортировать данные из Excel");
                    errorAlert.setContentText("""
                Возможные причины:
                - Неправильный формат файла (проверьте заголовки и типы данных).
                - Ошибка доступа базе данных.
                - Инвентарный номер не указан или дублируется.
                Попробуйте проверить файл и повторить."""
                    );
                    errorAlert.showAndWait();
                });
    }

    private void configureRowStyle() {
        treeTable.setRowFactory(tv -> new TreeTableRow<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("even-row", "odd-row", "selected-row");
                if (empty || item == null) return;

                if (isSelected()) {
                    getStyleClass().add("selected-row");
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
