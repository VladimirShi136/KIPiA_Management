package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.managers.ShapeManager;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.models.DeviceLocation;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.SchemeDAO;
import com.kipia.management.kipia_management.services.DeviceLocationDAO;
import com.kipia.management.kipia_management.utils.CustomAlert;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Ellipse;
import javafx.scene.text.Text;
import javafx.scene.Cursor;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;

/**
 * Контроллер для редактора схем
 *
 * @author vladimir_shi
 * @since 30.09.2025
 */
public class SchemeEditorController {
    // FXML элементы
    @FXML
    private ComboBox<Scheme> schemeComboBox;
    @FXML
    private ComboBox<Device> deviceComboBox;
    @FXML
    private Button newSchemeBtn, saveSchemeBtn, deleteSchemeBtn, selectToolBtn, lineToolBtn, rectToolBtn, addDeviceToolBtn;
    @FXML
    private Button undoBtn, redoBtn, ellipseToolBtn, textToolBtn;
    @FXML
    private AnchorPane schemePane;
    @FXML
    private Label statusLabel;
    // Логгер
    private static final Logger LOGGER = Logger.getLogger(SchemeEditorController.class.getName());
    // DAO (внедряются из MainController)
    private DeviceDAO deviceDAO;
    private SchemeDAO schemeDAO;
    private DeviceLocationDAO deviceLocationDAO;
    // Данные
    private Scheme currentScheme;
    private ObservableList<Device> deviceList;
    // Режимы работы с фигурами
    private ShapeManager.Tool currentTool = ShapeManager.Tool.SELECT;
    // НОВОЕ: менеджер фигур
    private ShapeManager shapeManager;

    // -----------------------------------------------------------------
    // PUBLIC API (внедрение из MainController)
    // -----------------------------------------------------------------
    public void setDeviceDAO(DeviceDAO dao) {
        this.deviceDAO = dao;
    }

    public void setSchemeDAO(SchemeDAO dao) {
        this.schemeDAO = dao;
    }

    public void setDeviceLocationDAO(DeviceLocationDAO dao) {
        this.deviceLocationDAO = dao;
    }

    // -----------------------------------------------------------------
    // ИНИЦИАЛИЗАЦИЯ
    // -----------------------------------------------------------------
    @FXML
    private void initialize() {
        // Настройка ComboBox
        schemeComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Scheme s) {
                return s != null ? s.getName() : "";
            }

            @Override
            public Scheme fromString(String s) {
                return null; // Не используется
            }
        });
        schemeComboBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.equals(oldV)) {
                loadScheme(newV);
            }
        });
        deviceComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Device d) {
                return d != null ? d.getInventoryNumber() + " - " + d.getName() : "";
            }

            @Override
            public Device fromString(String s) {
                return null;
            }
        });
        schemePane.setOnMouseClicked(e -> schemePane.requestFocus());
        schemePane.setOnKeyPressed(event -> {
            if (shapeManager != null && shapeManager.getSelectedNode() != null && currentTool == ShapeManager.Tool.SELECT) {
                switch (event.getCode()) {
                    case DELETE, BACK_SPACE -> deleteSelectedShape();
                }
            }
        });
        schemePane.setFocusTraversable(true);
        shapeManager = new ShapeManager(schemePane);  // НОВОЕ: инициализация менеджера
        setupToolButtons();
        // кнопки не видны и не управляются
        newSchemeBtn.setVisible(false);
        deleteSchemeBtn.setVisible(false);
        newSchemeBtn.setManaged(false);
        deleteSchemeBtn.setManaged(false);
        saveSchemeBtn.setOnAction(e -> saveCurrentScheme());
        applyButtonStyles();
        LOGGER.info("Контроллер редактора схемы инициализирован");
    }

    public void init() {
        if (deviceDAO == null) {
            LOGGER.severe("deviceDAO is null — init() called before setDeviceDAO!");
            CustomAlert.showError("Инициализация", "DAO для устройств не настроено!");
            return;
        }
        if (schemeDAO == null || deviceLocationDAO == null) {
            String err = "Missing DAO: schemeDAO=" + (schemeDAO == null) + ", deviceLocationDAO=" + (deviceLocationDAO == null);
            LOGGER.severe(err);
            CustomAlert.showError("Инициализация", "Не все DAO настроены!");
            return;
        }
        loadDevices();
        loadSchemes();
        if (!schemeComboBox.getItems().isEmpty() && schemeComboBox.getValue() == null) {
            Scheme firstScheme = schemeComboBox.getItems().getFirst();
            schemeComboBox.setValue(firstScheme);
            currentScheme = firstScheme;
            loadScheme(firstScheme);
        }
    }

    private void loadSchemes() {
        LOGGER.info("Начата загрузка схем на основе уникальных расположений устройств");
        List<String> locations = deviceDAO.getDistinctLocations();
        LOGGER.fine("Получено " + locations.size() + " уникальных расположений: " + locations);
        List<Scheme> schemesFromLocations = new ArrayList<>();
        for (String location : locations) {
            Scheme scheme = schemeDAO.findSchemeByName(location);
            if (scheme == null) {
                LOGGER.info("Схема для расположения '" + location + "' не найдена — создаём новую автоматически");
                scheme = new Scheme(0, location, "Автоматически созданная схема", "{}");
                boolean created = schemeDAO.addScheme(scheme);
                if (!created) {
                    LOGGER.warning("Не удалось создать схему для расположения '" + location + "' — пропускаем");
                    CustomAlert.showError("Ошибка создания схемы", "Не удалось создать схему для расположения '" + location + "'. Проверьте подключение к базе данных.");
                    continue;
                } else {
                    LOGGER.info("Схема для '" + location + "' успешно создана с ID=" + scheme.getId());
                }
            } else {
                LOGGER.fine("Найдена существующая схема для расположения '" + location + "' с ID=" + scheme.getId());
            }
            schemesFromLocations.add(scheme);
        }
        LOGGER.info("Всего схем создано/найдено: " + schemesFromLocations.size());
        if (schemesFromLocations.isEmpty()) {
            LOGGER.warning("Список схем пуст — ни одна схема не была загружена или создана");
            CustomAlert.showWarning("Загрузка схем", "Не удалось загрузить или создать схемы. Возможно, отсутствуют устройства с расположениями.");
        }
        ObservableList<Scheme> schemeList = FXCollections.observableArrayList(schemesFromLocations);
        schemeComboBox.setItems(schemeList);
        LOGGER.info("Список схем установлен в ComboBox, количество: " + schemeList.size());
    }

    private void loadDevices() {
        if (deviceDAO == null) {
            LOGGER.severe("deviceDAO является null в loadDevices");
            return;
        }
        deviceList = FXCollections.observableArrayList(deviceDAO.getAllDevices());
        LOGGER.info("Загружено " + deviceList.size() + " приборов");
        deviceComboBox.setItems(deviceList);
    }

    private void refreshAvailableDevices() {
        ObservableList<Device> availableDevicesList;
        if (deviceList == null || deviceList.isEmpty()) {
            LOGGER.warning("deviceList является null или пустой — ошибка фильтрации");
            availableDevicesList = FXCollections.observableArrayList();
            deviceComboBox.setItems(availableDevicesList);
            statusLabel.setText("Нет доступных приборов");
            return;
        }
        if (currentScheme == null) {
            LOGGER.warning("currentScheme является null — ошибка фильтрации по location");
            availableDevicesList = FXCollections.observableArrayList();
            deviceComboBox.setItems(availableDevicesList);
            statusLabel.setText("Схема не выбрана");
            return;
        }
        String selectedSchemeName = currentScheme.getName();
        LOGGER.fine("Фильтрация по схеме: '" + selectedSchemeName + "'");
        List<Integer> currentSchemeDeviceIds = new ArrayList<>();
        List<DeviceLocation> currentLocations = deviceLocationDAO.getLocationsBySchemeId(currentScheme.getId());
        for (DeviceLocation loc : currentLocations) {
            currentSchemeDeviceIds.add(loc.getDeviceId());
        }
        LOGGER.fine("Приборы на текущей схеме: " + currentSchemeDeviceIds);
        List<Integer> usedDeviceIds = new ArrayList<>();
        List<DeviceLocation> allLocations = deviceLocationDAO.getAllLocations();
        LOGGER.fine("Всего локаций в БД: " + allLocations.size());
        for (DeviceLocation loc : allLocations) {
            if (loc.getSchemeId() != currentScheme.getId()) {
                if (!usedDeviceIds.contains(loc.getDeviceId())) {
                    usedDeviceIds.add(loc.getDeviceId());
                }
            }
        }
        availableDevicesList = FXCollections.observableArrayList();
        int addedCount = 0;
        for (Device device : deviceList) {
            int devId = device.getId();
            if (selectedSchemeName.equals(device.getLocation())) {
                boolean isUsedElsewhere = usedDeviceIds.contains(devId);
                boolean isOnCurrentScheme = currentSchemeDeviceIds.contains(devId);
                if (!isUsedElsewhere && !isOnCurrentScheme) {
                    availableDevicesList.add(device);
                    addedCount++;
                    LOGGER.fine("Устройство '" + device.getName() + "' (ID=" + devId + ") добавлено в список доступных: соответствует расположению и не используется в других схемах");
                } else if (isOnCurrentScheme) {
                    LOGGER.warning("Устройство '" + device.getName() + "' (ID=" + devId + ") исключено из списка доступных: уже находится на текущей схеме (дубликаты запрещены)");
                } else {
                    LOGGER.warning("Устройство '" + device.getName() + "' (ID=" + devId + ") исключено из списка доступных: используется в другой схеме");
                }
            } else {
                LOGGER.fine("Устройство ID=" + devId + " ('" + device.getName() + "') пропущено: расположение '" + device.getLocation() + "' не соответствует схеме '" + selectedSchemeName + "'");
            }
        }
        deviceComboBox.setItems(availableDevicesList);
        if (!availableDevicesList.isEmpty()) {
            deviceComboBox.getSelectionModel().selectFirst();
            LOGGER.fine("Выбран первый доступный прибор: '" + availableDevicesList.getFirst().getName() + "' (ID=" + availableDevicesList.getFirst().getId() + ")");
        } else {
            deviceComboBox.getSelectionModel().clearSelection();
            LOGGER.warning("Список доступных устройств пуст — выбор приборов сброшен");
        }
        statusLabel.setText("Доступных приборов: " + addedCount);
    }

    private void setupToolButtons() {
        selectToolBtn.setOnAction(e -> {
            currentTool = ShapeManager.Tool.SELECT;
            statusLabel.setText("Инструмент: Выбор");
        });
        undoBtn.setOnAction(e -> {
            shapeManager.undo();
            statusLabel.setText("Отменено");
        });
        redoBtn.setOnAction(e -> {
            shapeManager.redo();
            statusLabel.setText("Повторено");
        });
        lineToolBtn.setOnAction(e -> {
            currentTool = ShapeManager.Tool.LINE;
            statusLabel.setText("Инструмент: Линия");
        });
        rectToolBtn.setOnAction(e -> {
            currentTool = ShapeManager.Tool.RECTANGLE;
            statusLabel.setText("Инструмент: Прямоугольник");
        });
        ellipseToolBtn.setOnAction(e -> {
            currentTool = ShapeManager.Tool.ELLIPSE;
            statusLabel.setText("Инструмент: Эллипс");
        });
        textToolBtn.setOnAction(e -> {
            currentTool = ShapeManager.Tool.TEXT;
            statusLabel.setText("Инструмент: Текст");
        });
        addDeviceToolBtn.setOnAction(e -> {
            currentTool = ShapeManager.Tool.ADD_DEVICE;
            statusLabel.setText("Инструмент: Добавить прибор");
        });
    }

    private void applyButtonStyles() {
        for (Button btn : List.of(selectToolBtn, undoBtn, redoBtn, lineToolBtn, rectToolBtn, ellipseToolBtn, textToolBtn, addDeviceToolBtn)) {
            StyleUtils.applyHoverAndAnimation(btn, "tool-button", "tool-button-hover");
        }
        StyleUtils.applyHoverAndAnimation(newSchemeBtn, "new-scheme-button", "new-scheme-button-hover");
        StyleUtils.applyHoverAndAnimation(saveSchemeBtn, "save-scheme-button", "save-scheme-button-hover");
        StyleUtils.applyHoverAndAnimation(deleteSchemeBtn, "delete-scheme-button", "delete-scheme-button-hover");
    }

    // -----------------------------------------------------------------
    // РАБОТА С СХЕМАМИ
    // -----------------------------------------------------------------
    private void loadScheme(Scheme scheme) {
        LOGGER.info("Начата загрузка схемы: \"" + scheme.getName() + "\" (ID=" + scheme.getId() + ")");
        try {
            currentScheme = scheme;
            LOGGER.info("Текущая схема установлена: \"" + currentScheme.getName() + "\"");
            schemePane.getChildren().clear();
            shapeManager.deselectShape();
            shapeManager.clearUndoRedo();  // Сброс стеков
            List<SchemeObject> objects = deserializeSchemeData(scheme.getData());
            LOGGER.info("Scheme data from DB: " + (scheme.getData() != null ? scheme.getData() : "null"));
            LOGGER.fine("Parsed " + objects.size() + " objects");
            for (SchemeObject obj : objects) {
                LOGGER.fine("Loaded shape: " + obj.toStringSegment());  // ОТЛАДКА: что загружается
            }
            LOGGER.info("Десериализовано " + objects.size() + " объектов схемы из данных");
            for (SchemeObject obj : objects) {
                Node node = obj.toNode();
                schemePane.getChildren().add(node);
            }
            List<DeviceLocation> locations = deviceLocationDAO.getLocationsBySchemeId(scheme.getId());
            LOGGER.info("Найдено " + locations.size() + " позиций устройств для этой схемы");
            for (DeviceLocation loc : locations) {
                Device device = deviceDAO.getDeviceById(loc.getDeviceId());
                if (device != null) {
                    Node deviceNode = createDeviceIcon(loc.getX(), loc.getY(), device);
                    schemePane.getChildren().add(deviceNode);
                    LOGGER.fine("Восстановлена иконка устройства \"" + device.getName() + "\" (ID=" + device.getId() + ") на позиции (" + loc.getX() + ", " + loc.getY() + ")");
                } else {
                    LOGGER.warning("Устройство с ID=" + loc.getDeviceId() + " на схеме \"" + scheme.getName() + "\" не найдено в БД — игнорируем позицию");
                    CustomAlert.showWarning("Загрузка схемы", "Устройство с ID=" + loc.getDeviceId() + " не найдено в базе данных. Возможно, оно было удалено.");
                }
            }
            refreshAvailableDevices();
            statusLabel.setText("Загружена схема: " + scheme.getName());
            LOGGER.info("Загрузка схемы \"" + scheme.getName() + "\" завершена успешно");
        } catch (Exception e) {
            LOGGER.severe("Ошибка при загрузке схемы \"" + scheme.getName() + "\": " + e.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить схему: " + e.getMessage() + ". Проверьте данные схемы или подключение к базе.");
            statusLabel.setText("Ошибка загрузки схемы");
        }
    }

    private void saveCurrentScheme() {
        if (currentScheme == null) return;
        shapeManager.deselectShape();
        List<SchemeObject> objects = new ArrayList<>();
        for (Node node : schemePane.getChildren()) {
            if (!(node instanceof Circle c && c.getFill() == Color.BLUE) &&
                    (node instanceof Line || node instanceof Rectangle || node instanceof Ellipse || node instanceof Text)) {
                SchemeObject obj = SchemeObject.fromNode(node);
                if (obj != null) {
                    objects.add(obj);
                    LOGGER.fine("Serialized shape: " + obj.toStringSegment());  // ОТЛАДКА
                }
            }
        }
        String dataToSave = serializeSchemeData(objects);
        LOGGER.info("Final scheme data to save: " + (dataToSave.isEmpty() ? "empty" : dataToSave));  // ОТЛАДКА
        currentScheme.setData(dataToSave);
        schemeDAO.updateScheme(currentScheme);  // Просто вызов, без присваивания (поскольку void)
        LOGGER.info("Scheme update performed");  // ОТЛАДКА на успех
        for (Node node : schemePane.getChildren()) {
            if (node instanceof Circle circle && circle.getFill() == Color.BLUE) {
                Device device = (Device) circle.getUserData();
                if (device != null) {
                    DeviceLocation loc = new DeviceLocation(device.getId(), currentScheme.getId(), circle.getCenterX(), circle.getCenterY());
                    deviceLocationDAO.updateDeviceLocation(loc);
                }
            }
        }
        statusLabel.setText("Схема сохранена: " + currentScheme.getName());
    }

    public void refreshSchemesAndDevices() {
        loadSchemes();
        loadDevices();
        refreshAvailableDevices();
    }

    // -----------------------------------------------------------------
    // ИНТЕРАКТИВНОСТЬ НА ПАНЕ
    // -----------------------------------------------------------------
    @FXML
    private void onPaneMousePressed(MouseEvent event) {
        double x = event.getX(), y = event.getY();
        if (currentTool == ShapeManager.Tool.SELECT) {
            shapeManager.onMousePressedForTool(ShapeManager.Tool.SELECT, x, y);
        } else if (currentTool == ShapeManager.Tool.ADD_DEVICE) {
            addDeviceAt(x, y);
        } else if (currentTool == ShapeManager.Tool.TEXT) {
            TextInputDialog dialog = new TextInputDialog("Текст");
            dialog.setTitle("Введите текст");
            dialog.setHeaderText("Введите текст для добавления:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(text -> {
                shapeManager.onMousePressedForTool(ShapeManager.Tool.TEXT, x, y);
                shapeManager.setPreviewText(text);
            });
        } else {
            shapeManager.onMousePressedForTool(currentTool, x, y);
        }
    }

    @FXML
    private void onPaneMouseDragged(MouseEvent event) {
        if (currentTool != ShapeManager.Tool.ADD_DEVICE) {
            shapeManager.onMouseDraggedForTool(currentTool, event.getX(), event.getY());
        }
    }

    @FXML
    private void onPaneMouseReleased(MouseEvent event) {
        if (currentTool != ShapeManager.Tool.ADD_DEVICE && currentTool != ShapeManager.Tool.SELECT) {
            shapeManager.onMouseReleasedForTool(currentTool, event.getX(), event.getY());
            statusLabel.setText("Фигура добавлена");
        } else if (currentTool == ShapeManager.Tool.SELECT) {
            statusLabel.setText(shapeManager.getSelectedNode() != null ? "Фигура перемещена" : "");
        }
    }

    private void addDeviceAt(double x, double y) {
        Device selected = deviceComboBox.getValue();
        if (selected == null) {
            CustomAlert.showWarning("Добавление прибора", "Выберите прибор из списка!");
            LOGGER.warning("Попытка добавить прибор, но он не выбран");
            return;
        }
        try {
            Node deviceNode = createDeviceIcon(x, y, selected);
            schemePane.getChildren().add(deviceNode);
            if (currentScheme != null) {
                DeviceLocation loc = new DeviceLocation(selected.getId(), currentScheme.getId(), x, y);
                deviceLocationDAO.addDeviceLocation(loc);
                LOGGER.info("Added location to DB for device " + selected.getName() + " at (" + x + ", " + y + ")");
            }
            refreshAvailableDevices();
            statusLabel.setText("Прибор добавлен: " + selected.getName());
            LOGGER.info("Прибор добавлен на схему: " + selected.getName());
            CustomAlert.showInfo("Добавление", "Прибор '" + selected.getName() + "' добавлен на схему");
        } catch (Exception e) {
            LOGGER.severe("Error adding device: " + e.getMessage());
            CustomAlert.showError("Ошибка", "Не удалось добавить прибор: " + e.getMessage());
        }
    }

    private Node createDeviceIcon(double x, double y, Device device) {
        LOGGER.fine("Начато создание иконки для устройства \"" + device.getName() + "\" (ID=" + device.getId() + ") на позиции (" + x + ", " + y + ")");
        Node iconNode;
        try {
            Image iconImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/manometer.png")));
            if (iconImage.isError()) {
                throw new Exception("Ошибка загрузки изображения: " + (iconImage.getException() != null ? iconImage.getException().getMessage() : "неизвестная ошибка"));
            }
            ImageView deviceIcon = new ImageView(iconImage);
            deviceIcon.setFitWidth(24);
            deviceIcon.setFitHeight(24);
            deviceIcon.setPreserveRatio(true);
            deviceIcon.setSmooth(true);
            deviceIcon.setCursor(Cursor.HAND);
            iconNode = deviceIcon;
            LOGGER.info("Успешно создана ImageView иконка для устройства \"" + device.getName() + "\"");
        } catch (Exception e) {
            LOGGER.warning("Не удалось загрузить иконку для прибора \"" + device.getName() + "\": " + e.getMessage() + " — используется резервный Circle");
            CustomAlert.showWarning("Загрузка иконки", "Не удалось загрузить изображение для \"" + device.getName() + "\". Используется резервная круглая иконка.");
            Circle fallbackCircle = new Circle(10, Color.BLUE);
            fallbackCircle.setCursor(Cursor.HAND);
            fallbackCircle.setStroke(Color.GRAY);
            fallbackCircle.setStrokeWidth(1);
            iconNode = fallbackCircle;
            LOGGER.info("Создана резервная Circle иконка для устройства \"" + device.getName() + "\"");
        }
        iconNode.setLayoutX(x);
        iconNode.setLayoutY(y);
        iconNode.setUserData(device);
        addContextMenuToNode(iconNode, device);
        addMovementHandlersToNode(iconNode);
        LOGGER.info("Иконка для устройства \"" + device.getName() + "\" создана и настроена (тип: " + iconNode.getClass().getSimpleName() + ")");
        return iconNode;  // Фикс: возврат Node вместо void
    }

    private void addContextMenuToNode(Node node, Device device) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Удалить прибор");
        MenuItem infoItem = new MenuItem("Показать информацию");
        deleteItem.setOnAction(event -> {
            boolean confirmed = CustomAlert.showConfirmation("Подтверждение удаления", "Вы уверены, что хотите удалить прибор \"" + device.getName() + "\"? Это действие нельзя будет отменить.");
            if (confirmed) {
                try {
                    deviceLocationDAO.deleteDeviceLocation(device.getId(), currentScheme.getId());
                    schemePane.getChildren().remove(node);
                    refreshAvailableDevices();
                    statusLabel.setText("Прибор удалён");
                    LOGGER.info("Прибор \"" + device.getName() + "\" (ID=" + device.getId() + ") успешно удалён со схемы \"" + currentScheme.getName() + "\"");
                    CustomAlert.showInfo("Удаление", "Прибор \"" + device.getName() + "\" удалён со схемы");
                } catch (Exception e) {
                    LOGGER.warning("Ошибка при удалении прибора \"" + device.getName() + "\" (ID=" + device.getId() + "): " + e.getMessage());
                    CustomAlert.showError("Ошибка удаления", "Не удалось удалить прибор: " + e.getMessage());
                }
            } else {
                LOGGER.info("Удаление прибора \"" + device.getName() + "\" отменено пользователем");
            }
        });
        infoItem.setOnAction(event -> {
            String infoText = "Место: " + device.getLocation() + "\nСтатус: " + device.getStatus() + "\nДополнительно: " + (device.getAdditionalInfo() != null ? device.getAdditionalInfo() : "нет");
            CustomAlert.showInfo("Информация о приборе", "\"" + device.getName() + "\" (" + device.getInventoryNumber() + ")\n" + infoText);
            LOGGER.fine("Показана информация о приборе \"" + device.getName() + "\"");
        });
        contextMenu.getItems().addAll(deleteItem, infoItem);
        node.setOnContextMenuRequested(event -> {
            contextMenu.show(node, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        LOGGER.info("Контекстное меню добавлено к узлу для прибора \"" + device.getName() + "\" (тип узла: " + node.getClass().getSimpleName() + ")");
    }

    private void addMovementHandlersToNode(Node node) {
        final boolean[] dragged = {false};
        final double[] offsetX = new double[1];
        final double[] offsetY = new double[1];
        node.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                double centerOffsetX = node instanceof ImageView ? 12 : 10;  // Примерно половина размера
                double centerOffsetY = node instanceof ImageView ? 12 : 10;
                offsetX[0] = event.getSceneX() - (node.getLayoutX() + centerOffsetX);
                offsetY[0] = event.getSceneY() - (node.getLayoutY() + centerOffsetY);
                dragged[0] = false;
                node.setCursor(Cursor.MOVE);
                LOGGER.fine("Начато перемещение узла типа " + node.getClass().getSimpleName() + " с позиции (" + node.getLayoutX() + ", " + node.getLayoutY() + ")");
            }
        });
        node.setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) {
                double newLayoutX = event.getSceneX() - offsetX[0];
                double newLayoutY = event.getSceneY() - offsetY[0];
                newLayoutX = Math.max(0, Math.min(newLayoutX, schemePane.getWidth() - 50));
                newLayoutY = Math.max(0, Math.min(newLayoutY, schemePane.getHeight() - 50));
                node.setLayoutX(newLayoutX);
                node.setLayoutY(newLayoutY);
                dragged[0] = true;
                LOGGER.fine("Узел перемещён к позиции (" + newLayoutX + ", " + newLayoutY + ")");
            }
        });
        node.setOnMouseReleased(event -> {
            node.setCursor(Cursor.HAND);
            if (currentScheme != null && dragged[0]) {
                Device device = (Device) node.getUserData();
                if (device != null) {
                    try {
                        DeviceLocation loc = new DeviceLocation(device.getId(), currentScheme.getId(), node.getLayoutX(), node.getLayoutY());
                        deviceLocationDAO.updateDeviceLocation(loc);
                        refreshAvailableDevices();
                    } catch (Exception e) {
                        LOGGER.warning("Ошибка при обновлении позиции устройства \"" + device.getName() + "\" (ID=" + device.getId() + "): " + e.getMessage());
                        CustomAlert.showError("Ошибка перемещения", "Не удалось сохранить новую позицию: " + e.getMessage());
                    }
                } else {
                    LOGGER.warning("Отсутствует устройство в userData для этого узла — перемещение не сохранено в БД");
                }
            } else if (!dragged[0]) {
                LOGGER.fine("Клик на узле без перемещения");
            }
            dragged[0] = false;
        });
        LOGGER.info("Обработчики перемещения добавлены к узлу типа " + node.getClass().getSimpleName());
    }

    private void deleteSelectedShape() {
        Node selected = shapeManager.getSelectedNode();
        if (selected != null) {
            shapeManager.removeShape(selected);
            shapeManager.deselectShape();
            statusLabel.setText("Фигура удалена");
        }
    }

    // -----------------------------------------------------------------
// ВНУТРЕННИЙ КЛАСС ДЛЯ ОБЪЕКТОВ СХЕМЫ
// -----------------------------------------------------------------
    private static class SchemeObject {
        enum Type {LINE, RECTANGLE, ELLIPSE, TEXT}

        Type type;
        double x1, y1, x2, y2, width, height, radiusX, radiusY;
        String text;

        // Конструктор для геометрических фигур
        SchemeObject(Type t, double... coords) {
            this.type = t;
            switch (t) {
                case LINE:
                    x1 = coords[0];
                    y1 = coords[1];
                    x2 = coords[2];
                    y2 = coords[3];
                    break;
                case RECTANGLE:
                    x1 = coords[0];
                    y1 = coords[1];
                    width = coords[2];
                    height = coords[3];
                    break;
                case ELLIPSE:
                    x1 = coords[0];
                    y1 = coords[1];
                    radiusX = coords[2];
                    radiusY = coords[3];
                    break;
                case TEXT:
                    x1 = coords[0];
                    y1 = coords[1];
                    break;
                default:
                    throw new IllegalArgumentException("Unknown type: " + t);
            }
        }

        // Конструктор для TEXT
        SchemeObject(Type t, double x1, double y1, String text) {
            this.type = t;
            this.x1 = x1;
            this.y1 = y1;
            this.text = text;
        }

        Node toNode() {
            switch (type) {
                case LINE:
                    Line line = new Line(x1, y1, x2, y2);
                    line.setStroke(Color.BLACK);
                    return line;
                case RECTANGLE:
                    Rectangle rect = new Rectangle(x1, y1, width, height);
                    rect.setFill(Color.TRANSPARENT);
                    rect.setStroke(Color.BLACK);
                    return rect;
                case ELLIPSE:
                    Ellipse ellipse = new Ellipse(x1, y1, radiusX, radiusY);
                    ellipse.setFill(Color.TRANSPARENT);
                    ellipse.setStroke(Color.BLACK);
                    return ellipse;
                case TEXT:
                    return new Text(x1, y1, text != null ? text : "Текст");
                default:
                    return null;
            }
        }

        String toStringSegment() {
            switch (type) {
                case LINE:
                    return type.name() + "|" + x1 + "|" + y1 + "|" + x2 + "|" + y2;  // Используем "|" вместо ","
                case RECTANGLE:
                    return type.name() + "|" + x1 + "|" + y1 + "|" + width + "|" + height;
                case ELLIPSE:
                    return type.name() + "|" + x1 + "|" + y1 + "|" + radiusX + "|" + radiusY;
                case TEXT:
                    return type.name() + "|" + x1 + "|" + y1 + "|" + (text != null ? text : "");  // Текст может содержать запятые
                default:
                    return "";
            }
        }

        static SchemeObject fromString(String str) {
            String[] parts = str.split("\\|");  // Разделитель "|"
            if (parts.length < 3) return null;
            Type t = Type.valueOf(parts[0]);
            try {
                double x1 = Double.parseDouble(parts[1]);
                double y1 = Double.parseDouble(parts[2]);
                switch (t) {
                    case LINE:
                        double x2 = Double.parseDouble(parts[3]);
                        double y2 = Double.parseDouble(parts[4]);
                        return new SchemeObject(t, x1, y1, x2, y2);
                    case RECTANGLE:
                        double width = Double.parseDouble(parts[3]);
                        double height = Double.parseDouble(parts[4]);
                        return new SchemeObject(t, x1, y1, width, height);
                    case ELLIPSE:
                        double radiusX = Double.parseDouble(parts[3]);
                        double radiusY = Double.parseDouble(parts[4]);
                        return new SchemeObject(t, x1, y1, radiusX, radiusY);
                    case TEXT:
                        String text = parts.length > 3 ? parts[3] : "Текст";  // Текст может быть пустым или содержать ","
                        return new SchemeObject(t, x1, y1, text);
                    default:
                        return null;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        static SchemeObject fromNode(Node node) {
            if (node instanceof Line line) {
                return new SchemeObject(Type.LINE, line.getStartX(), line.getStartY(), line.getEndX(), line.getEndY());
            } else if (node instanceof Rectangle rect) {
                return new SchemeObject(Type.RECTANGLE, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
            } else if (node instanceof Ellipse ellipse) {
                return new SchemeObject(Type.ELLIPSE, ellipse.getCenterX(), ellipse.getCenterY(), ellipse.getRadiusX(), ellipse.getRadiusY());
            } else if (node instanceof Text text) {
                return new SchemeObject(Type.TEXT, text.getX(), text.getY(), text.getText());
            }
            return null;
        }
    }

    // -----------------------------------------------------------------
// СЕРИАЛИЗАЦИЯ
// -----------------------------------------------------------------
    private String serializeSchemeData(List<SchemeObject> objects) {
        if (objects.isEmpty()) return "";
        List<String> segs = new ArrayList<>();
        for (SchemeObject obj : objects) {
            segs.add(obj.toStringSegment());
        }
        return String.join(";", segs);  // Разделитель объектов ";"
    }

    private List<SchemeObject> deserializeSchemeData(String data) {
        List<SchemeObject> objects = new ArrayList<>();
        if (data == null || data.trim().isEmpty()) return objects;
        String[] segments = data.split(";");  // Разделяем объекты
        for (String seg : segments) {
            if (!seg.trim().isEmpty()) {
                SchemeObject obj = SchemeObject.fromString(seg.trim());
                if (obj != null) objects.add(obj);
            }
        }
        return objects;
    }
// -----------------------------------------------------------------
// КОНЕЦ
// -----------------------------------------------------------------
}