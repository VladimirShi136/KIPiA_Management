package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.controllers.cell.table_cell.ValidatingDoubleCell;
import com.kipia.management.kipia_management.controllers.cell.table_cell.ValidatingIntegerCell;
import com.kipia.management.kipia_management.managers.PhotoManager;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;
import com.kipia.management.kipia_management.utils.LoadingIndicator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Контроллер, отвечающий за отображение и работу с таблицей приборов.
 *
 * @author vladimir_shi
 * @since 11.09.2025
 */
public class DevicesTableController implements SearchableController {

    // ---------- FXML‑элементы ----------
    @FXML
    private StackPane rootPane;
    @FXML
    private VBox contentBox;
    @FXML
    private TableView<Device> deviceTable;
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

    // Индикатор загрузки
    private LoadingIndicator loadingIndicator;

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
        if (deviceDAO == null) {
            LOGGER.error("DeviceDAO не установлен! Вызовите setDeviceDAO() перед init()");
            CustomAlertDialog.showError("Ошибка", "Сервис базы данных не инициализирован");
            return;
        }

        // Инициализация индикатора загрузки
        loadingIndicator = new LoadingIndicator("Загрузка данных...");
        if (rootPane != null) {
            rootPane.getChildren().add(loadingIndicator.getOverlay());
        }

        // Скрываем таблицу и статистику до загрузки данных
        hideContentBeforeLoad();

        createTableColumns();
        configureButtons();
        configureRowStyle();
        setupSmartColumnResizing();

        // Запускаем загрузку данных
        loadDataFromDaoAsync();

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
                "Тип прибора", "type"
        );

        TableColumn<Device, String> nameCol = createEditableStringColumn(
                "Модель", "name"
        );

        TableColumn<Device, String> manufacturerCol = createEditableStringColumn(
                "Завод изготовитель", "manufacturer"
        );

        inventoryCol = createEditableStringColumn(
                "Инв. №", "inventoryNumber"
        );

        TableColumn<Device, String> measurementLimitCol = createEditableStringColumn(
                "Предел измерений", "measurementLimit"
        );

        TableColumn<Device, String> locationCol = createEditableStringColumn(
                "Место установки", "location"
        );

        TableColumn<Device, String> valveNumberCol = createEditableStringColumn(
                "Кран №", "valveNumber"
        );

        TableColumn<Device, String> additionalInfoCol = createEditableStringColumn(
                "Доп. информация", "additionalInfo"
        );

        //  Числовые колонки
        TableColumn<Device, Integer> yearCol = createYearColumn();
        TableColumn<Device, Double> accuracyClassCol = createAccuracyClassColumn();

        // Статус — ComboBox
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
        //   Добавляем все колонки в таблицу
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
            String propertyName) {

        TableColumn<Device, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(propertyName));
        col.setEditable(false); // редактирование через форму, не inline
        return col;
    }

    // -----------------------------------------------------------------
    //   Загрузка данных и настройка фильтрации
    // -----------------------------------------------------------------

    /**
     * Скрывает контент до загрузки данных
     */
    private void hideContentBeforeLoad() {
        // Скрываем весь контейнер с таблицей и статистикой
        if (contentBox != null) {
            contentBox.setVisible(false);
            contentBox.setManaged(false);
        }
        // Скрываем FAB кнопку
        if (fabAddButton != null) {
            fabAddButton.setVisible(false);
            fabAddButton.setManaged(false);
        }
    }

    /**
     * Показывает контент после загрузки данных
     */
    private void showContentAfterLoad() {
        // Показываем весь контейнер с таблицей и статистикой
        if (contentBox != null) {
            contentBox.setVisible(true);
            contentBox.setManaged(true);
        }
        // Показываем FAB кнопку
        if (fabAddButton != null) {
            fabAddButton.setVisible(true);
            fabAddButton.setManaged(true);
        }
    }

    /**
     * Асинхронная загрузка данных с индикатором загрузки
     */
    private void loadDataFromDaoAsync() {
        // Показываем индикатор сразу
        Platform.runLater(() -> loadingIndicator.show());

        Task<List<Device>> loadTask = new Task<>() {
            @Override
            protected List<Device> call() throws Exception {
                long startTime = System.currentTimeMillis();

                // Загрузка данных
                List<Device> devices = deviceDAO.getAllDevices();

                // Умная задержка: показываем индикатор минимум 0.5 сек
                long elapsedTime = System.currentTimeMillis() - startTime;
                long minDisplayTime = 500; // минимальное время показа индикатора (мс)

                if (elapsedTime < minDisplayTime) {
                    Thread.sleep(minDisplayTime - elapsedTime);
                }

                return devices;
            }
        };

        loadTask.setOnSucceeded(_ -> {
            List<Device> devices = loadTask.getValue();
            filteredList = new FilteredList<>(FXCollections.observableArrayList(devices), _ -> true);
            SortedList<Device> sorted = createSortedList(filteredList, deviceTable);
            deviceTable.setItems(sorted);
            deviceTable.getSortOrder().add(inventoryCol);
            deviceTable.sort();
            updateStatistics();

            // Показываем контент после загрузки
            showContentAfterLoad();
            loadingIndicator.hide();
        });

        loadTask.setOnFailed(_ -> {
            LOGGER.error("Ошибка загрузки данных: {}", loadTask.getException().getMessage());
            CustomAlertDialog.showError("Ошибка", "Не удалось загрузить данные из базы");
            showContentAfterLoad(); // Показываем контент даже при ошибке
            loadingIndicator.hide();
        });

        new Thread(loadTask).start();
    }

    /**
     * Синхронная загрузка данных (для обновления после изменений)
     */
    private void loadDataFromDao() {
        List<Device> all = deviceDAO.getAllDevices();
        filteredList = new FilteredList<>(FXCollections.observableArrayList(all), _ -> true);
        SortedList<Device> sorted = createSortedList(filteredList, deviceTable);
        deviceTable.setItems(sorted);
        deviceTable.getSortOrder().add(inventoryCol);
        deviceTable.sort();
    }

    /**
     * Н
     */
    private void configureButtons() {
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
                ctrl.setOnDeviceAdded(() -> Platform.runLater(() -> {
                    loadDataFromDao();
                    updateStatistics();
                }));
            }

            openStyledDialog(view, "Добавление нового прибора");
            LOGGER.info("Диалог добавления прибора закрыт");
        } catch (Exception e) {
            LOGGER.error("Ошибка открытия формы добавления: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка", "Не удалось открыть форму добавления прибора");
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

        Optional<ButtonType> result = CustomAlertDialog.showConfirmationWithOptions(
                title, message,
                CustomAlertDialog.YES_BUTTON, CustomAlertDialog.NO_BUTTON, CustomAlertDialog.CANCEL_BUTTON);

        if (result.isEmpty() || result.get() == CustomAlertDialog.CANCEL_BUTTON) {
            LOGGER.info("Удаление отменено пользователем для прибора: {}", selected.getName());
            return;
        }

        boolean shouldDeletePhotos = result.get() == CustomAlertDialog.YES_BUTTON;
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
                    CustomAlertDialog.showSuccess("Удаление", "Прибор успешно удалён");
                } catch (Exception e) {
                    LOGGER.error("Ошибка при обновлении UI после удаления прибора: {}", e.getMessage(), e);
                    CustomAlertDialog.showError("Ошибка", "Не удалось обновить интерфейс после удаления");
                }
            });
        } else {
            CustomAlertDialog.showError("Удаление", "Не удалось удалить запись из БД");
            LOGGER.error("Не удалось удалить прибор: {}", selected.getName());
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
                ctrl.setEditMode(device);
                ctrl.setOnDeviceAdded(() -> Platform.runLater(() -> {
                    loadDataFromDao();
                    updateStatistics();
                }));
            }

            openStyledDialog(view, "Редактирование: " + device.getName());
        } catch (Exception e) {
            LOGGER.error("Ошибка открытия формы редактирования: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка", "Не удалось открыть форму редактирования");
        }
    }

    /**
     * Открывает FXML-форму в кастомном диалоге без системного titlebar.
     * Стиль соответствует CustomAlertDialog — скругление, тень, тема.
     */
    private void openStyledDialog(Parent formContent, String titleText) {
        boolean dark = com.kipia.management.kipia_management.utils.StyleUtils
                .getCurrentTheme().contains("dark");

        Stage ownerStage = (Stage) deviceTable.getScene().getWindow();

        // ===== TITLEBAR =====
        javafx.scene.shape.SVGPath formIcon = new javafx.scene.shape.SVGPath();
        formIcon.setContent(
                // Внешний корпус (основной круг)
                "M12,2 C6.48,2 2,6.48 2,12 s4.48,10 10,10 s10,-4.48 10,-10 S17.52,2 12,2 z " +

                        // Внутренний круг (циферблат)
                        "M12,3.5 C7.31,3.5 3.5,7.31 3.5,12 s3.81,8.5 8.5,8.5 s8.5,-3.81 8.5,-8.5 S16.69,3.5 12,3.5 z " +

                        // Деления: длинные (0, 90, 180, 270 градусов)
                        "M12,4.2 L12,5 M12,19 L12,19.8 M4.2,12 L5,12 M19,12 L19.8,12 " +

                        // Деления: короткие (45, 135, 225, 315 градусов)
                        "M17.66,6.34 L17.17,6.83 M6.83,17.17 L6.34,17.66 " +
                        "M6.34,6.34 L6.83,6.83 M17.17,17.17 L17.66,17.66 " +

                        // Центральная точка оси
                        "M12,12 m-0.4,0 a0.4,0.4 0 1,0 0.8,0 a0.4,0.4 0 1,0 -0.8,0 " +

                        // Стрелка (от центра вверх-влево, как в 10 часов)
                        "M12,12 L9.2,8.8 " +

                        // Треугольное острие стрелки
                        "M9.2,8.8 L8.8,9.5 L9.6,9.2 Z"
        );

        formIcon.setFill(Color.TRANSPARENT);
        formIcon.setStroke(Color.web(dark ? "#7090b0" : "#ecf0f1"));
        formIcon.setStrokeWidth(1.5);
        formIcon.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        formIcon.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        javafx.scene.layout.StackPane iconWrap = new javafx.scene.layout.StackPane(formIcon);
        iconWrap.setPrefSize(40, 40);
        iconWrap.setMinSize(40, 40);
        iconWrap.setStyle("-fx-background-color: transparent;");

        Label typeLabel = new Label("Управление приборами");
        typeLabel.setStyle(
                "-fx-font-size:10px;-fx-font-weight:bold;" +
                        "-fx-text-fill:" + (dark ? "#5a6a7a" : "rgba(255,255,255,0.65)") + ";"
        );
        Label titleLabel = new Label(titleText);
        titleLabel.setStyle(
                "-fx-font-size:13px;-fx-font-weight:bold;" +
                        "-fx-text-fill:" + (dark ? "#aec6de" : "#ffffff") + ";"
        );

        javafx.scene.layout.VBox titleBox = new javafx.scene.layout.VBox(1, typeLabel, titleLabel);
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.HBox.setHgrow(titleBox, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
                "-fx-background-color:transparent;-fx-text-fill:" +
                        (dark ? "#aec6de" : "white") +
                        ";-fx-font-size:15px;-fx-cursor:hand;-fx-padding:4 10;-fx-background-radius:4px;"
        );
        closeBtn.setOnMouseEntered(_ -> closeBtn.setStyle(
                "-fx-background-color:#e74c3c;-fx-text-fill:white;" +
                        "-fx-font-size:15px;-fx-cursor:hand;-fx-padding:4 10;-fx-background-radius:4px;"
        ));
        closeBtn.setOnMouseExited(_ -> closeBtn.setStyle(
                "-fx-background-color:transparent;-fx-text-fill:" +
                        (dark ? "#aec6de" : "white") +
                        ";-fx-font-size:15px;-fx-cursor:hand;-fx-padding:4 10;-fx-background-radius:4px;"
        ));

        Stage dialog = new Stage();
        closeBtn.setOnAction(_ -> dialog.close());

        javafx.scene.layout.HBox titleBar = new javafx.scene.layout.HBox(12, iconWrap, titleBox, closeBtn);
        titleBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        titleBar.setPadding(new javafx.geometry.Insets(10, 12, 10, 16));
        titleBar.setStyle(
                "-fx-background-color:" + (dark ? "#1a2330" : "#465261") + ";" +
                        "-fx-background-radius:12px 12px 0 0;"
        );

        // Drag на titlebar
        final double[] drag = new double[2];
        titleBar.setOnMousePressed(e -> { drag[0] = e.getScreenX() - dialog.getX(); drag[1] = e.getScreenY() - dialog.getY(); });
        titleBar.setOnMouseDragged(e -> { dialog.setX(e.getScreenX() - drag[0]); dialog.setY(e.getScreenY() - drag[1]); });

        // ===== ROOT =====
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(titleBar, formContent);
        javafx.scene.layout.VBox.setVgrow(formContent, Priority.ALWAYS);
        root.setStyle(
                "-fx-background-color:" + (dark ? "#252d38" : "#ffffff") + ";" +
                        "-fx-background-radius:12px;" +
                        "-fx-border-color:" + (dark ? "#2d3e50" : "#d0d4d8") + ";" +
                        "-fx-border-width:1px;-fx-border-radius:12px;"
        );
        root.setEffect(new javafx.scene.effect.DropShadow(
                20, 0, 5,
                dark ? javafx.scene.paint.Color.rgb(0,0,0,0.6)
                        : javafx.scene.paint.Color.rgb(0,0,0,0.22)
        ));

        // Clip для скругления
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);

        // ===== SCENE =====
        Scene scene = new Scene(root, 580, 720);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        Scene ownerScene = ownerStage.getScene();
        if (ownerScene != null) {
            scene.getStylesheets().addAll(ownerScene.getStylesheets());
        }

        dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        dialog.initOwner(ownerStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(false);
        dialog.setScene(scene);
        dialog.showAndWait();
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

    // -----------------------------------------------------------------
    //   Поиск
    // -----------------------------------------------------------------

    public void bindSearchField(TextField externalSearchField) {
        if (externalSearchField == null) return;

        externalSearchField.textProperty().addListener((_, _, newV) -> {
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

    @Override
    public void clearFilters() {
        if (filteredList != null) {
            filteredList.setPredicate(_ -> true);
            updateStatistics();
        }
    }
}