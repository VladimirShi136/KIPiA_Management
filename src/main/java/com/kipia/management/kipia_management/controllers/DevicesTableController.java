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
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

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
    private Button fabAddButton;
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
    // 11 колонок: тип, модель, завод, инв.№, год, предел, класс, место, кран, статус, доп.инфо
    private final double[] COLUMN_WIDTHS = {10, 12, 12, 8, 6, 10, 7, 12, 6, 8, 9};

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

        // -----------------------------------------------------------------
        //   Добавляем все колонки в таблицу (колонка Фото убрана)
        // -----------------------------------------------------------------
        deviceTable.getColumns().addAll(
                typeCol, nameCol, manufacturerCol, inventoryCol,
                yearCol, measurementLimitCol, accuracyClassCol,
                locationCol, valveNumberCol, statusCol,
                additionalInfoCol
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
        yearCol.setEditable(false);
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
        accuracyClassCol.setEditable(false);
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
        col.setEditable(false); // редактирование через форму, не inline
        return col;
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
        exportButton.setOnAction(_ -> exportToExcel());
        importButton.setOnAction(_ -> importFromExcel());

        exportButton.getStyleClass().add("button-export");
        importButton.getStyleClass().add("button-import");

        StyleUtils.applyHoverAndAnimation(exportButton,
                "button-export", "button-export-hover");
        StyleUtils.applyHoverAndAnimation(importButton,
                "button-import", "button-import-hover");

        // FAB — выравниваем по правому нижнему углу StackPane
        if (fabAddButton != null) {
            StackPane.setAlignment(fabAddButton, javafx.geometry.Pos.BOTTOM_RIGHT);
            StackPane.setMargin(fabAddButton, new Insets(0, 24, 24, 0));
        }
    }

    /**
     * Обработчик FAB-кнопки "+" — открывает форму добавления прибора
     * в отдельном диалоговом окне поверх таблицы.
     */
    @FXML
    private void onFabAddDevice() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/add-device-form.fxml"));
            Parent view = loader.load();

            AddDeviceController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setDeviceDAO(deviceDAO);
                // После успешного добавления — обновляем таблицу
                ctrl.setOnDeviceAdded(() -> Platform.runLater(() -> {
                    loadDataFromDao();
                    updateStatistics();
                }));
            }

            Stage dialog = new Stage();
            dialog.setTitle("Добавление нового прибора");
            dialog.setScene(new Scene(view, 560, 680));
            dialog.setResizable(false);

            // Копируем иконку и стили из главного окна
            Stage ownerStage = (Stage) deviceTable.getScene().getWindow();
            dialog.initOwner(ownerStage);
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            CustomAlert.applyIcon(dialog);
            // Переносим CSS из родительской сцены
            dialog.getScene().getStylesheets().addAll(
                    ownerStage.getScene().getStylesheets());

            dialog.showAndWait();

            LOGGER.info("Диалог добавления прибора закрыт");
        } catch (Exception e) {
            LOGGER.error("Ошибка открытия формы добавления: {}", e.getMessage(), e);
            CustomAlert.showError("Ошибка", "Не удалось открыть форму добавления прибора");
        }
    }

    /**
     * Удаление прибора — вызывается из контекстного меню.
     */
    private void deleteSelectedDevice(Device selected) {
        if (selected == null) return;

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
            int deletedCount = PhotoManager.getInstance().deleteAllDevicePhotos(selected);
            LOGGER.info("Удалено {} фото для прибора {}", deletedCount, selected.getId());
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
     * Чередующийся фон строк + двойной клик = редактирование
     * + контекстное меню (правая кнопка мыши)
     */
    private void configureRowStyle() {
        deviceTable.setRowFactory(_ -> {
            TableRow<Device> row = new TableRow<>() {
                @Override
                protected void updateItem(Device item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("even-row", "odd-row", "selected-row");
                    if (empty) return;
                    if (isSelected()) {
                        getStyleClass().add("selected-row");
                    } else {
                        getStyleClass().add(getIndex() % 2 == 0 ? "even-row" : "odd-row");
                    }
                }
            };

            // Двойной клик — открыть форму редактирования
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openEditForm(row.getItem());
                }
            });

            // Контекстное меню (правая кнопка мыши)
            ContextMenu contextMenu = new ContextMenu();

            MenuItem editItem = new MenuItem("Редактировать");
            editItem.setOnAction(_ -> {
                if (!row.isEmpty()) openEditForm(row.getItem());
            });

            MenuItem deleteItem = new MenuItem("Удалить");
            deleteItem.setOnAction(_ -> {
                if (!row.isEmpty()) deleteSelectedDevice(row.getItem());
            });

            contextMenu.getItems().addAll(editItem, new SeparatorMenuItem(), deleteItem);

            // Показываем меню только на непустых строках
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );

            return row;
        });
    }

    /**
     * Открывает форму редактирования для выбранного прибора.
     */
    private void openEditForm(Device device) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/add-device-form.fxml"));
            Parent view = loader.load();

            AddDeviceController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setDeviceDAO(deviceDAO);
                ctrl.setEditMode(device); // заполняем форму данными прибора
                ctrl.setOnDeviceAdded(() -> Platform.runLater(() -> {
                    loadDataFromDao();
                    updateStatistics();
                }));
            }

            Stage dialog = new Stage();
            dialog.setTitle("Редактирование прибора: " + device.getName());
            dialog.setScene(new Scene(view, 560, 680));
            dialog.setResizable(false);

            Stage ownerStage = (Stage) deviceTable.getScene().getWindow();
            dialog.initOwner(ownerStage);
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            CustomAlert.applyIcon(dialog);
            dialog.getScene().getStylesheets().addAll(ownerStage.getScene().getStylesheets());

            dialog.showAndWait();
        } catch (Exception e) {
            LOGGER.error("Ошибка открытия формы редактирования: {}", e.getMessage(), e);
            CustomAlert.showError("Ошибка", "Не удалось открыть форму редактирования");
        }
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

                if (i < columns.size() - 1) {
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
                        columns.get(i).setMinWidth(50);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка при обновлении ширины колонок: {}", e.getMessage());
        }
    }
}