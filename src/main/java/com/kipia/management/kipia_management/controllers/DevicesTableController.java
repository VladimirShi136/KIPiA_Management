package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.controllers.cell.table_cell.ValidatingDoubleCell;
import com.kipia.management.kipia_management.controllers.cell.table_cell.ValidatingIntegerCell;
import com.kipia.management.kipia_management.managers.PhotoManager;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.CustomAlert;
import com.kipia.management.kipia_management.utils.ExcelImportExportUtil;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.util.*;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Контроллер, отвечающий за отображение и работу с таблицей приборов.
 *
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

    // логгер для сообщений
    private static final Logger LOGGER = LogManager.getLogger(DevicesTableController.class);

    // ---------- Сервисы ----------
    private DeviceDAO deviceDAO;

    // ---------- Контроллеры ----------
    private SchemeEditorController schemeEditorController;

    // Списки, используемые для фильтрации/сортировки
    private FilteredList<Device> filteredList;

    // Инвентарный номер — колонка, которой будем пользоваться как «default sort»
    private TableColumn<Device, String> inventoryCol;

    // Пропорции ширины колонок (в процентах) - под ваши 12 колонок
    private final double[] COLUMN_WIDTHS = {10, 12, 12, 8, 6, 10, 8, 12, 6, 8, 6, 10};

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
     * Инициализация контроллера редактирования схемы.
     *
     * @param controller - контроллер
     */
    public void setSchemeEditorController(SchemeEditorController controller) {
        this.schemeEditorController = controller;
    }

    /**
     * Метод, вызываемый после загрузки FXML.
     */
    public void init() {
        // ДОБАВЬТЕ ЭТУ ПРОВЕРКУ
        if (deviceDAO == null) {
            LOGGER.error("DeviceDAO не установлен! Вызовите setDeviceDAO() перед init()");
            CustomAlert.showError("Ошибка", "Сервис базы данных не инициализирован");
            return;
        }
        createTableColumns();
        loadDataFromDao();
        configureSearch();
        configureButtons();
        configureRowStyle();
        updateStatistics();
        setupSmartColumnResizing();
        LOGGER.info("DevicesTableController инициализирован успешно");
    }

    // -----------------------------------------------------------------
    //                     ИНИЦИАЛИЗАЦИЯ ТАБЛИЦЫ
    // -----------------------------------------------------------------

    /**
     * Создаём все колонки, используя фабричные методы.
     */
    private void createTableColumns() {
        // Применяем стили к таблице
        deviceTable.getStyleClass().add("table-view");

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
                "Место установки", "location", 120,
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
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(ComboBoxTableCell.forTableColumn(
                "Хранение", "В работе", "Утерян", "Испорчен"));
        statusCol.setOnEditCommit(event -> {
            Device dev = event.getRowValue();
            dev.setStatus(event.getNewValue());
            dev.updateTimestamp();
            deviceDAO.updateDevice(dev);
            updateStatistics();
        });

        // Фото – две кнопки «Добавить» / «Просмотр»
        TableColumn<Device, Void> photoCol = new TableColumn<>("Фото");
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

        // глобальный стиль выбора
        deviceTable.setStyle("-fx-selection-bar: #cce7ff; -fx-selection-bar-text: black;");

    }

    // -----------------------------------------------------------------
    //   Фабричные методы для колонок
    // -----------------------------------------------------------------

    /**
     * Колонка, редактируемая через ValidatingIntegerCell
     *
     * @return - колонка
     */
    private TableColumn<Device, Integer> createYearColumn() {
        TableColumn<Device, Integer> yearCol = new TableColumn<>("Год выпуска");
        yearCol.setCellValueFactory(new PropertyValueFactory<>("year"));
        yearCol.setCellFactory(_ -> {
            ValidatingIntegerCell cell = new ValidatingIntegerCell();
            cell.getStyleClass().add("numeric-cell");
            return cell;
        });
        yearCol.setEditable(true);
        yearCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setYear(event.getNewValue());
            device.updateTimestamp();
            deviceDAO.updateDevice(device);
        });
        return yearCol;
    }

    /**
     * Колонка, редактируемая через ValidatingDoubleCell
     *
     * @return - колонка
     */
    private TableColumn<Device, Double> createAccuracyClassColumn() {
        TableColumn<Device, Double> accuracyClassCol = new TableColumn<>("Класс точности");
        accuracyClassCol.setCellValueFactory(new PropertyValueFactory<>("accuracyClass"));
        accuracyClassCol.setCellFactory(_ -> {
            ValidatingDoubleCell cell = new ValidatingDoubleCell();
            cell.getStyleClass().add("numeric-cell");
            return cell;
        });
        accuracyClassCol.setEditable(true);
        accuracyClassCol.setOnEditCommit(event -> {
            Device device = event.getRowValue();
            device.setAccuracyClass(event.getNewValue());
            device.updateTimestamp();
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
        col.setCellFactory(TextFieldTableCell.forTableColumn());
        col.setOnEditCommit(event -> {
            Device dev = event.getRowValue();
            onCommit.accept(dev, event.getNewValue());
            dev.updateTimestamp();
            deviceDAO.updateDevice(dev);
            if (propertyName.equals("location")) {
                if (schemeEditorController != null) {
                    schemeEditorController.refreshSchemesAndDevices();
                }
            }
        });
        return col;
    }

    /**
     * Фабрика ячейки для колонки «Фото».
     */
    private Callback<TableColumn<Device, Void>, TableCell<Device, Void>> createPhotoCellFactory() {
        return _ -> new TableCell<>() {
            private final Button addBtn = new Button();
            private final Button viewBtn = new Button();
            private final HBox buttonContainer = new HBox(2, addBtn, viewBtn);

            // ⭐⭐ ИСПОЛЬЗУЕМ СИНГЛТОН PhotoManager ⭐⭐
            private final PhotoManager photoManager = PhotoManager.getInstance();

            {
                // Стилизация кнопок
                addBtn.getStyleClass().add("table-button-add");
                viewBtn.getStyleClass().add("table-button-view");

                StyleUtils.applyHoverAndAnimation(addBtn, "table-button-add", "table-button-add-hover");
                StyleUtils.applyHoverAndAnimation(viewBtn, "table-button-view", "table-button-view-hover");

                // Начальные размеры
                updateButtonSizes(80);

                // Слушатель изменения ширины колонки
                widthProperty().addListener((_, _, newWidth) -> {
                    if (newWidth.doubleValue() > 0) {
                        updateButtonSizes(newWidth.doubleValue());
                    }
                });

                // Tooltips
                addBtn.setTooltip(new Tooltip("Добавить фото"));
                viewBtn.setTooltip(new Tooltip("Просмотреть фото"));

                // Обработчики с использованием PhotoManager
                addBtn.setOnAction(_ -> {
                    Device device = getCurrentDevice();
                    if (device != null) {
                        Stage stage = (Stage) addBtn.getScene().getWindow();
                        photoManager.addPhotosToDevice(device, stage);
                    }
                });

                viewBtn.setOnAction(_ -> {
                    Device device = getCurrentDevice();
                    if (device != null) {
                        Stage stage = (Stage) viewBtn.getScene().getWindow();
                        photoManager.viewDevicePhotos(device, stage);
                    }
                });
            }

            private void updateButtonSizes(double columnWidth) {
                if (columnWidth <= 0) return;

                double buttonSize, iconSize;
                double spacing;

                // ⬇️ ОБНОВЛЕННЫЕ РАЗМЕРЫ ДЛЯ МИНИМУМА 70px
                if (columnWidth < 75) {
                    // МИНИМАЛЬНЫЙ РАЗМЕР - компактные кнопки, но ОБЕ видны
                    buttonSize = 24;
                    iconSize = 12;
                    spacing = 2;
                    addBtn.setVisible(true);
                    viewBtn.setVisible(true);
                } else if (columnWidth < 85) {
                    buttonSize = 30;
                    iconSize = 17;
                    spacing = 3;
                    addBtn.setVisible(true);
                    viewBtn.setVisible(true);
                } else if (columnWidth < 105) {
                    buttonSize = 32;
                    iconSize = 18;
                    spacing = 4;
                    addBtn.setVisible(true);
                    viewBtn.setVisible(true);
                } else {
                    buttonSize = 34;
                    iconSize = 20;
                    spacing = 5;
                    addBtn.setVisible(true);
                    viewBtn.setVisible(true);
                }

                // Устанавливаем размеры через inline стили
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
                    LOGGER.warn("Не удалось загрузить иконки для кнопок фото: {}", e.getMessage());
                    // Устанавливаем текстовые метки если иконки не загрузились
                    if (getTableColumn().getWidth() < 50) {
                        addBtn.setText("+");
                        viewBtn.setText("👁");
                    } else {
                        addBtn.setText("Доб");
                        viewBtn.setText("Просм");
                    }
                }
            }

            private Device getCurrentDevice() {
                return getTableView().getItems().get(getIndex());
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(buttonContainer);
                }
            }
        };
    }

    // -----------------------------------------------------------------
    //   Загрузка данных и настройка фильтрации
    // -----------------------------------------------------------------
    private void loadDataFromDao() {
        List<Device> all = deviceDAO.getAllDevices();
        filteredList = new FilteredList<>(FXCollections.observableArrayList(all), _ -> true);
        SortedList<Device> sorted = createSortedList(filteredList, deviceTable);
        deviceTable.setItems(sorted);
        deviceTable.getSortOrder().add(inventoryCol);
        deviceTable.sort();
    }

    private void configureSearch() {
        searchField.textProperty().addListener((_, _, newV) -> {
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
        deleteButton.setOnAction(_ -> deleteSelectedDevice());
        exportButton.setOnAction(_ -> exportToExcel());
        importButton.setOnAction(_ -> importFromExcel());

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
            CustomAlert.showWarning("Удаление", "Выберите прибор для удаления");
            return;
        }

        String title = "Подтверждение удаления";
        String message = "Удалить прибор \"" + selected.getName() + "\"?\n" +
                "ДА - удалить вместе с привязанными фото.\n" +
                "НЕТ - удалить только прибор.\n" +
                "Отмена - отменить действие.\n";

        Optional<ButtonType> result = CustomAlert.showConfirmationWithOptions(
                title, message,
                CustomAlert.YES_BUTTON, CustomAlert.NO_BUTTON, CustomAlert.CANCEL_BUTTON);

        if (result.isEmpty() || result.get() == CustomAlert.CANCEL_BUTTON) {
            LOGGER.info("Удаление отменено пользователем для прибора: {}", selected.getName());
            return;
        }

        boolean shouldDeletePhotos = result.get() == CustomAlert.YES_BUTTON;
        LOGGER.info("Начато удаление прибора: {} (удалять фото: {})", selected.getName(), shouldDeletePhotos);

        // Удаление фото (если выбрано)
        if (shouldDeletePhotos) {
            PhotoManager photoManager = PhotoManager.getInstance();
            List<String> photos = selected.getPhotos();
            if (photos != null && !photos.isEmpty()) {
                Iterator<String> iterator = photos.iterator();
                while (iterator.hasNext()) {
                    String photoName = iterator.next();
                    boolean deleted = photoManager.deletePhoto(selected, photoName);
                    if (deleted) {
                        iterator.remove();
                    } else {
                        LOGGER.warn("Не удалось удалить фото {} для прибора {}", photoName, selected.getId());
                    }
                }
            }
        }

        // Удаление прибора из БД
        boolean ok = deviceDAO.deleteDevice(selected.getId());
        if (ok) {
            Platform.runLater(() -> {
                try {
                    filteredList.getSource().remove(selected);
                    updateStatistics();
                    if (schemeEditorController != null) {
                        schemeEditorController.refreshSchemesAndDevices();
                    }
                    LOGGER.info("Прибор успешно удалён: {}", selected.getName());
                    CustomAlert.showSuccess("Удаление", "Прибор успешно удалён");
                } catch (Exception e) {
                    LOGGER.error("Ошибка при обновлении UI после удаления прибора: {}", e.getMessage(), e);
                    CustomAlert.showError("Ошибка", "Не удалось обновить интерфейс после удаления");
                }
            });
        } else {
            CustomAlert.showError("Удаление", "Не удалось удалить запись из БД");
            LOGGER.error("Не удалось удалить прибор: {}", selected.getName());
        }
    }


    // -----------------------------------------------------------------
    //   Экспорт / импорт Excel (используем Apache POI)
    // -----------------------------------------------------------------

    private void exportToExcel() {
        boolean success = ExcelImportExportUtil.exportDevicesToExcel(deviceTable.getScene().getWindow(), deviceTable.getItems());
        if (success) {
            CustomAlert.showInfo("Экспорт", "Экспорт завершён успешно");
            LOGGER.info("Экспорт устройств в Excel завершён успешно");
        }
    }

    private void importFromExcel() {
        String result = ExcelImportExportUtil.importDevicesFromExcel(deviceTable.getScene().getWindow(), deviceDAO,
                () -> {
                    loadDataFromDao();
                    updateStatistics();
                    if (schemeEditorController != null) {
                        schemeEditorController.refreshSchemesAndDevices();
                    }
                    LOGGER.info("Импорт устройств завершён успешно");
                }, () -> {
                    CustomAlert.showError("Импорт", "Ошибка импорта данных из Excel");
                    LOGGER.error("Ошибка импорта устройств");
                });
        if (result != null) {
            CustomAlert.showInfo("Импорт", result);
            LOGGER.info("Импорт завершён: {}", result);
        }
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
        deviceTable.setRowFactory(_ -> new TableRow<>() {
            @Override
            protected void updateItem(Device item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("even-row", "odd-row", "selected-row");
                if (empty) {
                    return;
                }
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

    /**
     * Настройка адаптивного изменения размеров колонок
     */
    private void setupSmartColumnResizing() {
        // Слушатель изменения размера таблицы
        deviceTable.widthProperty().addListener((_, _, newWidth) -> {
            if (newWidth.doubleValue() > 0) {
                updateColumnWidths(newWidth.doubleValue());
            }
        });

        // Первоначальная настройка ширины колонок
        Platform.runLater(() -> {
            if (deviceTable.getWidth() > 0) {
                updateColumnWidths(deviceTable.getWidth());
            }
        });
    }

    /**
     * Обновление ширины колонок пропорционально
     */
    private void updateColumnWidths(double tableWidth) {
        if (deviceTable.getColumns().isEmpty() || tableWidth <= 0) {
            return;
        }

        try {
            // Вычитаем ширину скроллбара и границы
            double scrollbarWidth = 18;
            double bordersAndPadding = 4;
            double availableWidth = Math.max(tableWidth - scrollbarWidth - bordersAndPadding, 400);

            List<TableColumn<Device, ?>> columns = deviceTable.getColumns();

            // Устанавливаем пропорциональные ширины
            for (int i = 0; i < Math.min(columns.size(), COLUMN_WIDTHS.length); i++) {
                double width = availableWidth * (COLUMN_WIDTHS[i] / 100);
                columns.get(i).setPrefWidth(width);

                // Устанавливаем минимальные ширины
                double minWidth;

                // Особые настройки для колонки фото (предпоследняя колонка)
                if (i == columns.size() - 2) { // Колонка "Фото"
                    minWidth = 70; // ⬅️ УВЕЛИЧИЛИ МИНИМУМ до 70px чтобы обе кнопки были видны
                    columns.get(i).setMinWidth(minWidth);
                    columns.get(i).setMaxWidth(120);
                } else if (i < columns.size() - 1) {
                    // Обычные колонки
                    minWidth = Math.max(width * 0.4, 50);
                    columns.get(i).setMinWidth(minWidth);
                    columns.get(i).setMaxWidth(400);
                } else {
                    // Последняя колонка (доп. информация)
                    minWidth = Math.max(width * 0.4, 50);
                    columns.get(i).setMinWidth(minWidth);
                    columns.get(i).setMaxWidth(600);
                }
            }

            // Если колонок больше чем predefined widths
            if (columns.size() > COLUMN_WIDTHS.length) {
                double usedPercentage = Arrays.stream(COLUMN_WIDTHS).sum();
                double remainingPercentage = Math.max(100 - usedPercentage, 0);
                if (remainingPercentage > 0) {
                    double extraWidthPerColumn = availableWidth * (remainingPercentage / 100) / (columns.size() - COLUMN_WIDTHS.length);
                    for (int i = COLUMN_WIDTHS.length; i < columns.size(); i++) {
                        columns.get(i).setPrefWidth(extraWidthPerColumn);
                        // Для колонки фото устанавливаем особый минимум
                        if (i == columns.size() - 2) {
                            columns.get(i).setMinWidth(70); // ⬅️ ТАКЖЕ ЗДЕСЬ
                        } else {
                            columns.get(i).setMinWidth(50);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка при обновлении ширины колонок: {}", e.getMessage());
        }
    }
}