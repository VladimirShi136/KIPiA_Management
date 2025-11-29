package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.ExcelImportExportUtil;
import com.kipia.management.kipia_management.utils.StyleUtils;
import com.kipia.management.kipia_management.utils.CustomAlert;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;

public class DevicesGroupedController {

    // логгер для сообщений
    private static final Logger LOGGER = LogManager.getLogger(DevicesGroupedController.class);

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
    private SchemeEditorController schemeEditorController;

    // Пропорции ширины колонок для древовидной таблицы (в процентах) - под ваши 11 колонок
    private final double[] TREE_COLUMN_WIDTHS = {15, 12, 12, 8, 6, 10, 8, 6, 8, 6, 15};

    /**
     * Устанавливает DAO для работы с устройствами
     *
     * @param dao - DAO для устройств
     */
    public void setDeviceDAO(DeviceDAO dao) {
        this.deviceDAO = dao;
    }

    /**
     * Инициализация контроллера редактирования схемы.
     *
     * @param controller - контроллер
     */
    public void setSchemeEditorController(SchemeEditorController controller) {
        this.schemeEditorController = controller;
    }

    @FXML
    private void initialize() {
        // Применяем стили к таблице
        treeTable.getStyleClass().add("tree-table-view");
        treeTable.setEditable(true);
        treeTable.setShowRoot(false);
        configureColumns();
        configureSearch();
        configureButtons();
        configureRowFactory();
    }

    public void init() {
        loadData();
        setupSmartTreeColumnResizing();
        LOGGER.info("Контроллер группированных устройств инициализирован");
    }

    // --- Типы строк дерева ---

    public sealed interface TreeRowItem permits GroupItem, DeviceItem {
    }

    public record GroupItem(String location) implements TreeRowItem {
    }

    public record DeviceItem(Device device) implements TreeRowItem {
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

        // Колонка "Производитель"
        TreeTableColumn<TreeRowItem, String> manufacturerCol = createEditableStringColumn("Завод изготовитель", 115,
                Device::getManufacturer,
                Device::setManufacturer);

        // Колонка "Инвентарный номер"
        TreeTableColumn<TreeRowItem, String> inventoryCol = createEditableStringColumn("Инв. №", 70,
                Device::getInventoryNumber,
                Device::setInventoryNumber);

        // Год выпуска - числовая колонка с custom ячейкой и проверкой
        TreeTableColumn<TreeRowItem, Integer> yearCol = getTreeRowItemIntegerTreeTableColumn();

        // Колонка "Предел измерений"
        TreeTableColumn<TreeRowItem, String> measurementLimitCol = createEditableStringColumn("Предел измерений", 100,
                Device::getMeasurementLimit,
                Device::setMeasurementLimit);

        // Колонка "Класс точности" - числовая колонка с валидацией
        TreeTableColumn<TreeRowItem, Double> accuracyClassCol = getTreeRowItemDoubleTreeTableColumn();

        // Колонка "Кран №"
        TreeTableColumn<TreeRowItem, String> valveNumberCol = createEditableStringColumn("Кран №", 70,
                Device::getValveNumber,
                Device::setValveNumber);

        // Колонка Статус с ComboBox и запретом редактирования для групп
        TreeTableColumn<TreeRowItem, String> statusCol = getTreeRowItemStringTreeTableColumn();

        //Колонка "Фото"
        TreeTableColumn<TreeRowItem, Void> photoCol = new TreeTableColumn<>("Фото");
        //photoCol.setPrefWidth(100);
        photoCol.setStyle("-fx-alignment: CENTER;");
        photoCol.setCellFactory(createPhotoCellFactory());

        // Колонка "Доп. Информация."
        TreeTableColumn<TreeRowItem, String> additionalInfoCol = createEditableStringColumn("Доп. информация", 240,
                Device::getAdditionalInfo,
                Device::setAdditionalInfo);

        // Добавление колонок в таблицу
        treeTable.getColumns().addAll(locationCol, nameCol, manufacturerCol, inventoryCol, yearCol, measurementLimitCol, accuracyClassCol, valveNumberCol, statusCol, photoCol, additionalInfoCol);
    }

    private TreeTableColumn<TreeRowItem, String> getTreeRowItemStringTreeTableColumn() {
        TreeTableColumn<TreeRowItem, String> statusCol = new TreeTableColumn<>("Статус");
        //statusCol.setPrefWidth(90);
        statusCol.setCellValueFactory(param -> {
            TreeRowItem val = param.getValue().getValue();
            if (val instanceof DeviceItem(Device device))
                return new ReadOnlyObjectWrapper<>(device.getStatus());
            else
                return new ReadOnlyObjectWrapper<>("");
        });

        statusCol.setCellFactory(_ -> new ComboBoxTreeTableCell<>("Хранение", "В работе", "Утерян", "Испорчен") {
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
                        // Убираем combobox для групповых строк
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
            if (val instanceof DeviceItem(Device device)) {
                device.setStatus(event.getNewValue());
                deviceDAO.updateDevice(device);
                updateStatistics();
                treeTable.refresh();
            }
        });
        return statusCol;
    }

    private TreeTableColumn<TreeRowItem, Double> getTreeRowItemDoubleTreeTableColumn() {
        TreeTableColumn<TreeRowItem, Double> accuracyClassCol = new TreeTableColumn<>("Класс точности");
        //accuracyClassCol.setPrefWidth(90);
        accuracyClassCol.setCellValueFactory(param -> {
            TreeRowItem val = param.getValue().getValue();
            if (val instanceof DeviceItem(Device device)) {
                return new ReadOnlyObjectWrapper<>(device.getAccuracyClass());
            } else {
                return new ReadOnlyObjectWrapper<>(null);
            }
        });
        accuracyClassCol.setCellFactory(_ -> new com.kipia.management.kipia_management.controllers.cell.tree_table_cell.ValidatingDoubleTreeCell() {
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
            if (val instanceof DeviceItem(Device device)) {
                device.setAccuracyClass(event.getNewValue());
                deviceDAO.updateDevice(device);
                treeTable.refresh();
            }
        });
        return accuracyClassCol;
    }

    private TreeTableColumn<TreeRowItem, Integer> getTreeRowItemIntegerTreeTableColumn() {
        TreeTableColumn<TreeRowItem, Integer> yearCol = new TreeTableColumn<>("Год выпуска");
        //yearCol.setPrefWidth(90);
        yearCol.setCellValueFactory(param -> {
            TreeRowItem val = param.getValue().getValue();
            if (val instanceof DeviceItem(Device device)) {
                return new ReadOnlyObjectWrapper<>(device.getYear());
            } else {
                return new ReadOnlyObjectWrapper<>(null);
            }
        });
        yearCol.setCellFactory(_ -> new com.kipia.management.kipia_management.controllers.cell.tree_table_cell.ValidatingIntegerTreeCell() {
            {
                getStyleClass().add("numeric-cell");
            }

            /**
             * Обновление ячейки
             * @param item - новое значение
             * @param empty - является ли ячейка пустой
             */
            @Override
            public void updateItem(Integer item, boolean empty) {
                // Проверка ячейки
                TreeRowItem val = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                // Если ячейка не является прибором
                if (!(val instanceof DeviceItem)) {
                    // Запрет редактирования
                    setEditable(false);
                    // Очистка
                    setText(null);
                    // Удаление графического представления
                    setGraphic(null);
                    // Если ячейка является прибором
                } else {
                    // Разрешение редактирования
                    setEditable(true);
                    // Обновление
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
            if (val instanceof DeviceItem(Device device)) {
                device.setYear(event.getNewValue());
                deviceDAO.updateDevice(device);
                treeTable.refresh();
            }
        });
        return yearCol;
    }

    private Callback<TreeTableColumn<TreeRowItem, Void>, TreeTableCell<TreeRowItem, Void>> createPhotoCellFactory() {
        return _ -> new TreeTableCell<>() {
            private final Button addBtn = new Button();
            private final Button viewBtn = new Button();
            private final HBox buttonContainer = new HBox(2, addBtn, viewBtn);

            {
                // ПЕРВОЕ: применяем CSS классы для цветов
                addBtn.getStyleClass().add("table-button-add");
                viewBtn.getStyleClass().add("table-button-view");

                // ВТОРОЕ: применяем hover стили через StyleUtils
                StyleUtils.applyHoverAndAnimation(addBtn, "table-button-add", "table-button-add-hover");
                StyleUtils.applyHoverAndAnimation(viewBtn, "table-button-view", "table-button-view-hover");

                // ТРЕТЬЕ: устанавливаем начальные размеры
                updateButtonSizes(80);

                // Слушатель изменения ширины колонки
                widthProperty().addListener((_, _, newWidth) -> updateButtonSizes(newWidth.doubleValue()));

                // Tooltips
                addBtn.setTooltip(new Tooltip("Добавить фото"));
                viewBtn.setTooltip(new Tooltip("Просмотреть фото"));

                addBtn.setOnAction(_ -> {
                    Device device = getCurrentDevice();
                    if (device != null) {
                        addPhoto(device);
                    }
                });

                viewBtn.setOnAction(_ -> {
                    Device device = getCurrentDevice();
                    if (device != null) {
                        viewPhotos(device);
                    }
                });
                setAlignment(Pos.CENTER);
            }

            private void updateButtonSizes(double columnWidth) {
                if (columnWidth <= 0) return;

                double buttonSize, iconSize;
                double spacing;

                if (columnWidth < 60) {
                    buttonSize = 20; iconSize = 14; spacing = 1;
                } else if (columnWidth < 80) {
                    buttonSize = 24; iconSize = 16; spacing = 2;
                } else if (columnWidth < 100) {
                    buttonSize = 28; iconSize = 18; spacing = 3;
                } else {
                    buttonSize = 32; iconSize = 20; spacing = 4;
                }

                // Устанавливаем размеры через inline стили, но оставляем CSS классы для цветов
                String sizeStyle = String.format(
                        "-fx-min-width: %fpx; -fx-pref-width: %fpx; -fx-max-width: %fpx; " +
                                "-fx-min-height: %fpx; -fx-pref-height: %fpx; -fx-max-height: %fpx; " +
                                "-fx-padding: 0px;",
                        buttonSize, buttonSize, buttonSize, buttonSize, buttonSize, buttonSize
                );

                addBtn.setStyle(sizeStyle);
                viewBtn.setStyle(sizeStyle);

                buttonContainer.setSpacing(spacing);
                buttonContainer.setMaxWidth(columnWidth - 4);
                buttonContainer.setPrefWidth((buttonSize * 2) + spacing);
                buttonContainer.setAlignment(Pos.CENTER);

                updateIcons(iconSize);
                buttonContainer.requestLayout();
            }

            private void updateIcons(double iconSize) {
                try {
                    Image addImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/add_photo.png")));
                    ImageView addIcon = new ImageView(addImage);
                    addIcon.setFitWidth(iconSize);
                    addIcon.setFitHeight(iconSize);
                    addIcon.setPreserveRatio(true);

                    Image viewImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/view.png")));
                    ImageView viewIcon = new ImageView(viewImage);
                    viewIcon.setFitWidth(iconSize);
                    viewIcon.setFitHeight(iconSize);
                    viewIcon.setPreserveRatio(true);

                    addBtn.setGraphic(addIcon);
                    viewBtn.setGraphic(viewIcon);

                } catch (Exception e) {
                    LOGGER.warn("Не удалось загрузить иконки для кнопок фото");
                }
            }

            private Device getCurrentDevice() {
                TreeRowItem rowItem = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                if (rowItem instanceof DeviceItem(Device device)) {
                    return device;
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
                    CustomAlert.showInfo("Просмотр фото", "Фотографии не добавлены");
                    LOGGER.warn("Просмотр фото: нет фото для устройства {}", device.getName());
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
                stage.setScene(new Scene(new ScrollPane(vbox), 300, 600));
                stage.show();
                LOGGER.info("Показ фото для устройства: {}", device.getName());
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null); // ВАЖНО для TreeTableCell
                } else {
                    setGraphic(buttonContainer);
                    setText(null); // ВАЖНО для TreeTableCell - убираем любой текст
                }
            }
        };
    }

    private TreeTableColumn<TreeRowItem, String> getTreeRowTableForLocation() {
        TreeTableColumn<TreeRowItem, String> locationCol = new TreeTableColumn<>("Тип прибора");
        //locationCol.setPrefWidth(120);
        locationCol.setCellValueFactory(param -> {
            TreeRowItem val = param.getValue().getValue();
            if (val instanceof GroupItem(String location)) return new ReadOnlyObjectWrapper<>(location);
            else if (val instanceof DeviceItem(Device device)) return new ReadOnlyObjectWrapper<>(device.getType());
            else return new ReadOnlyObjectWrapper<>("");
        });
        locationCol.setCellFactory(_ -> new TextFieldTreeTableCell<>(new DefaultStringConverter()) {
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
                    setText(item);
                    if (val instanceof GroupItem) {
                        setEditable(false);
                        setAlignment(Pos.CENTER_LEFT); // Левое выравнивание для групповых строк
                        setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 8 5 0;");
                    } else {
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
            if (val instanceof DeviceItem(Device device)) {
                device.setType(event.getNewValue());
                deviceDAO.updateDevice(device);
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
        //col.setPrefWidth(width);
        col.setCellValueFactory(param -> {
            TreeRowItem val = param.getValue().getValue();
            if (val instanceof DeviceItem(Device device)) {
                return new ReadOnlyObjectWrapper<>(getter.apply(device));
            }
            return new ReadOnlyObjectWrapper<>("");
        });
        col.setCellFactory(_ -> new TextFieldTreeTableCell<>(new DefaultStringConverter()) {
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
            if (val instanceof DeviceItem(Device device)) {
                setter.accept(device, event.getNewValue());
                deviceDAO.updateDevice(device);
                treeTable.refresh();
            }
        });
        return col;
    }

    // --- Методы для адаптации размеров колонок ---

    /**
     * Настройка адаптивного изменения размеров колонок для TreeTableView
     */
    private void setupSmartTreeColumnResizing() {
        // Слушатель изменения размера таблицы
        treeTable.widthProperty().addListener((_, _, newWidth) -> updateTreeColumnWidths(newWidth.doubleValue()));

        // Первоначальная настройка ширины колонок
        Platform.runLater(() -> updateTreeColumnWidths(treeTable.getWidth()));
    }

    /**
     * Обновление ширины колонок пропорционально для TreeTableView
     */
    private void updateTreeColumnWidths(double tableWidth) {
        if (treeTable.getColumns().isEmpty() || tableWidth <= 0) return;

        // Вычитаем ширину скроллбара, границы и отступ для дерева
        double availableWidth = tableWidth - 40; // Больше отступ из-за иерархии

        List<TreeTableColumn<TreeRowItem, ?>> columns = treeTable.getColumns();

        // Устанавливаем пропорциональные ширины
        for (int i = 0; i < Math.min(columns.size(), TREE_COLUMN_WIDTHS.length); i++) {
            double width = availableWidth * (TREE_COLUMN_WIDTHS[i] / 100);
            columns.get(i).setPrefWidth(width);

            // Для древовидной таблицы первая колонка (с иерархией) должна быть шире
            double minWidth = (i == 0) ? 120 : 60;

            // Особые настройки для колонки фото (предпоследняя колонка)
            if (i == columns.size() - 2) { // Колонка "Фото"
                minWidth = 55; // Гарантируем что обе кнопки поместятся
                columns.get(i).setMinWidth(minWidth);
                columns.get(i).setMaxWidth(120);
            } else if (i == 0) {
                // Первая колонка с иерархией
                columns.get(i).setMinWidth(minWidth);
                columns.get(i).setMaxWidth(500);
            } else if (i < columns.size() - 1) {
                // Обычные колонки
                columns.get(i).setMinWidth(minWidth);
                columns.get(i).setMaxWidth(300);
            } else {
                // Последняя колонка
                columns.get(i).setMinWidth(minWidth);
                columns.get(i).setMaxWidth(400);
            }
        }

        // Обработка случая когда колонок больше чем predefined widths
        if (columns.size() > TREE_COLUMN_WIDTHS.length) {
            double remainingPercentage = 108 - Arrays.stream(TREE_COLUMN_WIDTHS).sum();
            double extraWidthPerColumn = availableWidth * (remainingPercentage / 100) / (columns.size() - TREE_COLUMN_WIDTHS.length);

            for (int i = TREE_COLUMN_WIDTHS.length; i < columns.size(); i++) {
                columns.get(i).setPrefWidth(extraWidthPerColumn);
                // Для колонки фото устанавливаем особый минимум
                if (i == columns.size() - 2) {
                    columns.get(i).setMinWidth(55);
                } else {
                    columns.get(i).setMinWidth(60);
                }
            }
        }
    }

    // --- Загрузка и обработка данных ---

    private void loadData() {
        if (deviceDAO == null) return;
        List<Device> devices = deviceDAO.getAllDevices();
        filteredList = new FilteredList<>(FXCollections.observableArrayList(devices), _ -> true);
        updateTreeItems();
        updateStatistics();
    }

    private void updateTreeItems() {
        if (filteredList == null) return;

        Map<String, List<Device>> grouped = new TreeMap<>();
        for (Device d : filteredList) {
            String location = d.getLocation();
            if (location == null || location.isEmpty()) location = "Без места";
            grouped.computeIfAbsent(location, _ -> new ArrayList<>()).add(d);
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
        searchField.textProperty().addListener((_, _, newVal) -> {
            String filter = newVal.toLowerCase().trim();
            if (filteredList != null) {
                if (filter.isEmpty()) filteredList.setPredicate(_ -> true);
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
            deleteButton.setOnAction(_ -> deleteSelectedDevice());
        }
        if (exportButton != null) {
            StyleUtils.applyHoverAndAnimation(exportButton, "button-export", "button-export-hover");
            exportButton.setOnAction(_ -> {
                boolean success = ExcelImportExportUtil.exportGroupedTreeTableToExcel(treeTable.getScene().getWindow(), treeTable);
                if (success) {
                    CustomAlert.showInfo("Экспорт", "Группированный экспорт завершён");
                    LOGGER.info("Экспорт группированной таблицы успешен");
                }
            });
        }
        if (importButton != null) {
            StyleUtils.applyHoverAndAnimation(importButton, "button-import", "button-import-hover");
            importButton.setOnAction(_ ->
                    ExcelImportExportUtil.importGroupedTreeTableFromExcel(treeTable.getScene().getWindow(), deviceDAO, treeTable,
                            () -> {
                                loadData();
                                updateStatistics();
                                if (schemeEditorController != null) {
                                    schemeEditorController.refreshSchemesAndDevices();
                                }
                                LOGGER.info("Импорт группированной таблицы успешен");
                            },
                            () -> {
                                CustomAlert.showError("Импорт", "Не удалось импортировать данные из Excel");
                                LOGGER.error("Ошибка импорта группированной таблицы");
                            })
            );
        }
    }

    private void deleteSelectedDevice() {
        TreeItem<TreeRowItem> selected = treeTable.getSelectionModel().getSelectedItem();
        if (selected == null || !(selected.getValue() instanceof DeviceItem)) {
            CustomAlert.showWarning("Удаление", "Выберите прибор для удаления");
            return;
        }
        Device dev = ((DeviceItem) selected.getValue()).device();
        boolean confirmed = CustomAlert.showConfirmation("Подтверждение", "Удалить прибор \"" + dev.getName() + "\"?");
        if (confirmed) {  // True = YES, false = NO/CANCEL
            LOGGER.info("Начато удаление прибора: {}", dev.getName());
            boolean success = deviceDAO.deleteDevice(dev.getId());
            if (success) {
                filteredList.getSource().remove(dev);
                updateTreeItems();
                updateStatistics();
                if (schemeEditorController != null) {
                    schemeEditorController.refreshSchemesAndDevices();
                }
                LOGGER.info("Прибор удалён: {}", dev.getName());
            } else {
                CustomAlert.showError("Ошибка удаления", "Не удалось удалить прибор из базы данных");
                LOGGER.error("Ошибка удаления прибора: {}", dev.getName());
            }
        } else LOGGER.info("Удаление отменено пользователем: {}", dev.getName());
    }

    private void configureRowFactory() {
        treeTable.setRowFactory(_ -> new TreeTableRow<>() {
            @Override
            protected void updateItem(TreeRowItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("even-row", "odd-row", "selected-row", "group-row");
                if (empty || item == null) return;
                if (isSelected()) {
                    getStyleClass().add("selected-row");
                } else if (item instanceof GroupItem) {
                    getStyleClass().add("group-row");
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