package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.managers.ShapeManager;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.models.DeviceLocation;
import com.kipia.management.kipia_management.models.SchemeObject;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.SchemeDAO;
import com.kipia.management.kipia_management.services.DeviceLocationDAO;
import com.kipia.management.kipia_management.shapes.ShapeHandler;
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
import javafx.scene.shape.*;
import javafx.scene.Cursor;
import javafx.util.StringConverter;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    private Button saveSchemeBtn, selectToolBtn, lineToolBtn, rectToolBtn, addDeviceToolBtn, rhombusToolBtn;
    @FXML
    private Button undoBtn, redoBtn, ellipseToolBtn;
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
    // Менеджер фигур
    private ShapeManager shapeManager;

    // -----------------------------------------------------------------
    // PUBLIC API (внедрение из MainController)
    // -----------------------------------------------------------------

    /**
     * Устанавливает DAO для работы с устройствами.
     *
     * @param dao экземпляр DeviceDAO
     */
    public void setDeviceDAO(DeviceDAO dao) {
        this.deviceDAO = dao;
    }

    /**
     * Устанавливает DAO для работы со схемами.
     *
     * @param dao экземпляр SchemeDAO
     */
    public void setSchemeDAO(SchemeDAO dao) {
        this.schemeDAO = dao;
    }

    /**
     * Устанавливает DAO для работы с местоположениями устройств.
     *
     * @param dao экземпляр DeviceLocationDAO
     */
    public void setDeviceLocationDAO(DeviceLocationDAO dao) {
        this.deviceLocationDAO = dao;
    }

    // -----------------------------------------------------------------
    // ИНИЦИАЛИЗАЦИЯ
    // -----------------------------------------------------------------

    /**
     * Инициализация FXML-компонентов контроллера.
     * Настраивает ComboBox, обработчики событий и инструменты.
     */
    @FXML
    private void initialize() {
        // Настройка ComboBox
        schemeComboBox.setConverter(new StringConverter<>() {
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
        deviceComboBox.setConverter(new StringConverter<>() {
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
            if (shapeManager != null && shapeManager.getSelectedShape() != null && currentTool == ShapeManager.Tool.SELECT) {
                switch (event.getCode()) {
                    case DELETE, BACK_SPACE -> deleteSelectedShape();
                }
            }
        });
        schemePane.setFocusTraversable(true);
        shapeManager = new ShapeManager(schemePane);
        Consumer<ShapeHandler> onSelectCallback = shapeManager::selectShape;
        shapeManager.setOnSelectCallback(onSelectCallback);
        shapeManager.setStatusSetter(status -> statusLabel.setText(status));
        setupToolButtons();
        saveSchemeBtn.setOnAction(e -> saveCurrentScheme());
        applyButtonStyles();
    }

    /**
     * Основная инициализация контроллера после внедрения зависимостей.
     * Загружает устройства, схемы и настраивает начальную схему, если возможно.
     */
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

    /**
     * Загружает список схем на основе уникальных расположений устройств.
     * Если схема для расположения не найдена, создаёт новую автоматически.
     */
    private void loadSchemes() {
        LOGGER.info("Начата загрузка схем на основе уникальных расположений устройств");
        List<String> locations = deviceDAO.getDistinctLocations();
        List<Scheme> schemesFromLocations = new ArrayList<>();
        for (String location : locations) {
            Scheme scheme = schemeDAO.findSchemeByName(location);
            if (scheme == null) {
                LOGGER.info("Схема для расположения '" + location + "' не найдена — создаём новую автоматически");
                scheme = new Scheme(0, location, "Автоматически созданная схема", "{}");
                boolean created = schemeDAO.addScheme(scheme);
                if (!created) {
                    LOGGER.severe("Не удалось создать схему для расположения '" + location + "'");
                    CustomAlert.showError("Ошибка создания схемы", "Не удалось создать схему для расположения '" + location + "'. Проверьте подключение к базе данных.");
                    continue;
                } else {
                    LOGGER.info("Схема для '" + location + "' успешно создана");
                }
            }
            schemesFromLocations.add(scheme);
        }
        if (schemesFromLocations.isEmpty()) {
            LOGGER.warning("Список схем пуст — ни одна схема не была загружена или создана");
            CustomAlert.showWarning("Загрузка схем", "Не удалось загрузить или создать схемы. Возможно, отсутствуют устройства с расположениями.");
        }
        ObservableList<Scheme> schemeList = FXCollections.observableArrayList(schemesFromLocations);
        schemeComboBox.setItems(schemeList);
        LOGGER.info("Сформовано " + schemeList.size() + " схем");
    }

    /**
     * Загружает все устройства из базы данных и устанавливает их в ComboBox.
     */
    private void loadDevices() {
        if (deviceDAO == null) {
            LOGGER.severe("deviceDAO является null в loadDevices");
            return;
        }
        deviceList = FXCollections.observableArrayList(deviceDAO.getAllDevices());
        deviceComboBox.setItems(deviceList);
    }

    /**
     * Обновляет список доступных устройств для текущей схемы.
     * Фильтрует устройства по расположению, исключая уже используемые на других схемах.
     */
    private void refreshAvailableDevices() {
        ObservableList<Device> availableDevicesList;  // Объявлено в начале метода
        if (deviceList == null || deviceList.isEmpty()) {
            availableDevicesList = FXCollections.observableArrayList();
            deviceComboBox.setItems(availableDevicesList);
            statusLabel.setText("Нет доступных приборов");
            return;
        }
        if (currentScheme == null) {
            availableDevicesList = FXCollections.observableArrayList();
            deviceComboBox.setItems(availableDevicesList);
            statusLabel.setText("Схема не выбрана");
            return;
        }
        String selectedSchemeName = currentScheme.getName();
        List<Integer> currentSchemeDeviceIds = new ArrayList<>();
        List<DeviceLocation> currentLocations = deviceLocationDAO.getLocationsBySchemeId(currentScheme.getId());
        for (DeviceLocation loc : currentLocations) {
            currentSchemeDeviceIds.add(loc.getDeviceId());
        }
        List<Integer> usedDeviceIds = new ArrayList<>();
        List<DeviceLocation> allLocations = deviceLocationDAO.getAllLocations();
        for (DeviceLocation loc : allLocations) {
            if (loc.getSchemeId() != currentScheme.getId()) {
                usedDeviceIds.add(loc.getDeviceId());
            }
        }
        availableDevicesList = FXCollections.observableArrayList();
        for (Device device : deviceList) {
            int devId = device.getId();
            if (selectedSchemeName.equals(device.getLocation())) {
                if (!usedDeviceIds.contains(devId) && !currentSchemeDeviceIds.contains(devId)) {
                    availableDevicesList.add(device);
                }
            }
        }
        deviceComboBox.setItems(availableDevicesList);
        statusLabel.setText("Доступных приборов: " + availableDevicesList.size());
    }

    /**
     * Настраивает действия кнопок инструментов для выбора инструмента редактирования схемы.
     */
    private void setupToolButtons() {
        selectToolBtn.setOnAction(e -> {
            currentTool = ShapeManager.Tool.SELECT;
            statusLabel.setText("Инструмент: Выбор");
        });
        undoBtn.setOnAction(e -> shapeManager.undo());
        redoBtn.setOnAction(e -> shapeManager.redo());
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
        addDeviceToolBtn.setOnAction(e -> {
            currentTool = ShapeManager.Tool.ADD_DEVICE;
            statusLabel.setText("Инструмент: Добавить прибор");
        });
        rhombusToolBtn.setOnAction(e -> {
            currentTool = ShapeManager.Tool.RHOMBUS;
            statusLabel.setText("Инструмент: Ромб");
        });
    }

    /**
     * Применяет стили наведения и анимации к кнопкам инструментов и сохранения.
     */
    private void applyButtonStyles() {
        for (Button btn : List.of(selectToolBtn, undoBtn, redoBtn, lineToolBtn, rectToolBtn, ellipseToolBtn, addDeviceToolBtn, rhombusToolBtn)) {
            StyleUtils.applyHoverAndAnimation(btn, "tool-button", "tool-button-hover");
        }
        StyleUtils.applyHoverAndAnimation(saveSchemeBtn, "save-scheme-button", "save-scheme-button-hover");
    }

    // -----------------------------------------------------------------
    // РАБОТА С СХЕМАМИ
    // -----------------------------------------------------------------

    /**
     * Загружает выбранную схему, восстанавливая объекты и устройства на панели.
     *
     * @param scheme схема для загрузки
     */
    private void loadScheme(Scheme scheme) {
        try {
            currentScheme = scheme;
            schemePane.getChildren().clear();
            shapeManager.deselectShape();
            shapeManager.clearUndoRedo();
            List<SchemeObject> objects = deserializeSchemeData(scheme.getData());
            for (SchemeObject obj : objects) {
                Node node = obj.toNode();
                schemePane.getChildren().add(node);
            }
            List<DeviceLocation> locations = deviceLocationDAO.getLocationsBySchemeId(scheme.getId());
            for (DeviceLocation loc : locations) {
                Device device = deviceDAO.getDeviceById(loc.getDeviceId());
                if (device != null) {
                    Node deviceNode = createDeviceIcon(loc.getX(), loc.getY(), device);
                    schemePane.getChildren().add(deviceNode);
                } else {
                    CustomAlert.showWarning("Загрузка схемы", "Устройство с ID=" + loc.getDeviceId() + " не найдено в базе данных.");
                }
            }
            refreshAvailableDevices();
            statusLabel.setText("Загружена схема: " + scheme.getName());
        } catch (Exception e) {
            LOGGER.severe("Ошибка при загрузке схемы '" + scheme.getName() + "': " + e.getMessage());
            CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить схему: " + e.getMessage());
            statusLabel.setText("Ошибка загрузки схемы");
        }
    }

    /**
     * Сохраняет текущую схему, сериализуя объекты и обновляя местоположения устройств.
     */
    private void saveCurrentScheme() {
        if (currentScheme == null) return;
        shapeManager.deselectShape();
        List<SchemeObject> objects = new ArrayList<>();
        for (Node node : schemePane.getChildren()) {
            if (!(node instanceof Circle c && c.getFill() == Color.BLUE) &&
                    (node instanceof Line || node instanceof Rectangle || node instanceof Ellipse || node instanceof javafx.scene.shape.Path)) {
                SchemeObject obj = SchemeObject.fromNode(node);
                if (obj != null) {
                    objects.add(obj);
                }
            }
        }
        currentScheme.setData(serializeSchemeData(objects));
        schemeDAO.updateScheme(currentScheme);
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

    /**
     * Обновляет списки схем, устройств и доступных устройств.
     */
    public void refreshSchemesAndDevices() {
        loadSchemes();
        loadDevices();
        refreshAvailableDevices();
    }

    // -----------------------------------------------------------------
    // ИНТЕРАКТИВНОСТЬ НА ПАНЕ
    // -----------------------------------------------------------------

    /**
     * Обрабатывает нажатие мыши на панели схемы.
     *
     * @param event событие нажатия мыши
     */
    @FXML
    private void onPaneMousePressed(MouseEvent event) {
        double x = event.getX(), y = event.getY();
        shapeManager.resetWasResized();
        if (currentTool == ShapeManager.Tool.SELECT) {
            shapeManager.onMousePressedForTool(ShapeManager.Tool.SELECT, x, y);
        } else if (currentTool == ShapeManager.Tool.ADD_DEVICE) {
            addDeviceAt(x, y);
        } else {
            shapeManager.onMousePressedForTool(currentTool, x, y);
        }
    }

    /**
     * Обрабатывает перетаскивание мыши на панели схемы.
     *
     * @param event событие перетаскивания мыши
     */
    @FXML
    private void onPaneMouseDragged(MouseEvent event) {
        if (currentTool != ShapeManager.Tool.ADD_DEVICE) {
            shapeManager.onMouseDraggedForTool(currentTool, event.getX(), event.getY());
        }
    }

    /**
     * Обрабатывает отпускание мыши на панели схемы.
     *
     * @param event событие отпускания мыши
     */
    @FXML
    private void onPaneMouseReleased(MouseEvent event) {
        if (currentTool != ShapeManager.Tool.ADD_DEVICE && currentTool != ShapeManager.Tool.SELECT) {
            shapeManager.onMouseReleasedForTool(currentTool, event.getX(), event.getY());
            statusLabel.setText("Фигура добавлена");
        } else if (currentTool == ShapeManager.Tool.SELECT && shapeManager.wasDraggedInSelect())  {
            statusLabel.setText(shapeManager.getSelectedShape() != null ? "Фигура перемещена" : "");
        } else if (currentTool == ShapeManager.Tool.SELECT && shapeManager.wasResized()) {
            statusLabel.setText("Фигура изменена");
        }
    }

    /**
     * Добавляет устройство на заданную позицию схемы.
     *
     * @param x координата X
     * @param y координата Y
     */
    private void addDeviceAt(double x, double y) {
        Device selected = deviceComboBox.getValue();
        if (selected == null) {
            CustomAlert.showWarning("Добавление прибора", "Выберите прибор из списка!");
            return;
        }
        try {
            Node deviceNode = createDeviceIcon(x, y, selected);
            schemePane.getChildren().add(deviceNode);
            if (currentScheme != null) {
                DeviceLocation loc = new DeviceLocation(selected.getId(), currentScheme.getId(), x, y);
                deviceLocationDAO.addDeviceLocation(loc);
            }
            refreshAvailableDevices();
            statusLabel.setText("Прибор добавлен: " + selected.getName());
            CustomAlert.showInfo("Добавление", "Прибор '" + selected.getName() + "' добавлен на схему");
        } catch (Exception e) {
            LOGGER.severe("Error adding device: " + e.getMessage());
            CustomAlert.showError("Ошибка", "Не удалось добавить прибор: " + e.getMessage());
        }
    }

    /**
     * Создаёт иконку устройства на заданной позиции.
     *
     * @param x координата X
     * @param y координата Y
     * @param device устройство для иконки
     * @return созданный узел (ImageView или Circle)
     */
    private Node createDeviceIcon(double x, double y, Device device) {
        try {
            Image iconImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/manometer.png")));
            ImageView deviceIcon = new ImageView(iconImage);
            deviceIcon.setFitWidth(24);
            deviceIcon.setFitHeight(24);
            deviceIcon.setPreserveRatio(true);
            deviceIcon.setSmooth(true);
            deviceIcon.setCursor(Cursor.HAND);
            addContextMenuToNode(deviceIcon, device);
            addMovementHandlersToNode(deviceIcon);
            deviceIcon.setLayoutX(x);
            deviceIcon.setLayoutY(y);
            deviceIcon.setUserData(device);
            return deviceIcon;
        } catch (Exception e) {
            CustomAlert.showWarning("Загрузка иконки", "Не удалось загрузить изображение для '" + device.getName() + "'. Используется резервная круглая иконка.");
            Circle fallbackCircle = new Circle(10, Color.BLUE);
            fallbackCircle.setCursor(Cursor.HAND);
            fallbackCircle.setStroke(Color.GRAY);
            fallbackCircle.setStrokeWidth(1);
            fallbackCircle.setCenterX(x);
            fallbackCircle.setCenterY(y);
            fallbackCircle.setUserData(device);
            addContextMenuToNode(fallbackCircle, device);
            addMovementHandlersToNode(fallbackCircle);
            return fallbackCircle;
        }
    }

    /**
     * Добавляет контекстное меню к узлу устройства.
     *
     * @param node узел устройства
     * @param device устройство для меню
     */
    private void addContextMenuToNode(Node node, Device device) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Удалить прибор");
        MenuItem infoItem = new MenuItem("Показать информацию");
        deleteItem.setOnAction(event -> {
            LOGGER.fine("Удалить прибор: устройство '" + device.getName() + "'");
            boolean confirmed = CustomAlert.showConfirmation("Подтверждение удаления", "Вы уверены, что хотите удалить прибор '" + device.getName() + "'?");
            if (confirmed) {
                try {
                    deviceLocationDAO.deleteDeviceLocation(device.getId(), currentScheme.getId());
                    schemePane.getChildren().remove(node);
                    refreshAvailableDevices();
                    statusLabel.setText("Прибор удалён");
                    CustomAlert.showInfo("Удаление", "Прибор '" + device.getName() + "' удалён со схемы");
                } catch (Exception e) {
                    LOGGER.severe("Ошибка при удалении прибора '" + device.getName() + "': " + e.getMessage());
                    CustomAlert.showError("Ошибка удаления", "Не удалось удалить прибор: " + e.getMessage());
                }
            }
        });
        infoItem.setOnAction(event -> {
            LOGGER.fine("Показать информацию: устройство '" + device.getName() + "'");
            String infoText = "Место: " + device.getLocation() + "\nСтатус: " + device.getStatus() + "\nДополнительно: " + (device.getAdditionalInfo() != null ? device.getAdditionalInfo() : "нет");
            CustomAlert.showInfo("Информация о приборе", "'" + device.getName() + "' (" + device.getInventoryNumber() + ")\n" + infoText);
        });
        contextMenu.getItems().addAll(deleteItem, infoItem);
        LOGGER.fine("Меню добавлено к устройству '" + device.getName() + "'");
        node.setOnContextMenuRequested(event -> {
            LOGGER.fine("Правый клик на устройстве: '" + device.getName() + "'");
            contextMenu.show(node, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    /**
     * Добавляет обработчики движения к узлу устройства.
     *
     * @param node узел устройства
     */
    private void addMovementHandlersToNode(Node node) {
        final boolean[] dragged = {false};
        final double[] offsetX = new double[1];
        final double[] offsetY = new double[1];
        node.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {  // Только левая кнопка
                double centerOffsetX = node instanceof ImageView ? 12 : 10;
                double centerOffsetY = node instanceof ImageView ? 12 : 10;
                offsetX[0] = event.getSceneX() - (node.getLayoutX() + centerOffsetX);
                offsetY[0] = event.getSceneY() - (node.getLayoutY() + centerOffsetY);
                dragged[0] = false;
                node.setCursor(Cursor.MOVE);
            }
        });

        node.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) return;  // Явно игнорировать правую кнопку
            double newLayoutX = event.getSceneX() - offsetX[0];
            double newLayoutY = event.getSceneY() - offsetY[0];
            newLayoutX = Math.max(0, Math.min(newLayoutX, schemePane.getWidth() - 50));
            newLayoutY = Math.max(0, Math.min(newLayoutY, schemePane.getHeight() - 50));
            node.setLayoutX(newLayoutX);
            node.setLayoutY(newLayoutY);
            dragged[0] = true;
        });

        node.setOnMouseReleased(event -> {
            if (!event.isPrimaryButtonDown()) return;  // Проверка для правой кнопки
            node.setCursor(Cursor.HAND);
            if (currentScheme != null && dragged[0]) {
                Device device = (Device) node.getUserData();
                if (device != null) {
                    try {
                        DeviceLocation loc = new DeviceLocation(device.getId(), currentScheme.getId(), node.getLayoutX(), node.getLayoutY());
                        deviceLocationDAO.updateDeviceLocation(loc);
                    } catch (Exception e) {
                        LOGGER.severe("Ошибка при обновлении позиции устройства '" + device.getName() + "': " + e.getMessage());
                        CustomAlert.showError("Ошибка перемещения", "Не удалось сохранить новую позицию: " + e.getMessage());
                    }
                }
            }
            dragged[0] = false;
        });
    }

    /**
     * Удаляет выбранную фигуру из схемы.
     */
    private void deleteSelectedShape() {
        ShapeHandler selected = shapeManager.getSelectedShape();
        if (selected != null) {
            shapeManager.removeShape((Node) selected);
            shapeManager.deselectShape();
            statusLabel.setText("Фигура удалена");
        }
    }

    // -----------------------------------------------------------------
    // СЕРИАЛИЗАЦИЯ И ДЕСЕРИАЛИЗАЦИЯ
    // -----------------------------------------------------------------

    /**
     * Сериализует список объектов схемы в строку для хранения.
     *
     * @param objects список объектов схемы
     * @return сериализованная строка
     */
    private String serializeSchemeData(List<SchemeObject> objects) {
        if (objects.isEmpty()) return "";
        return objects.stream().map(SchemeObject::toStringSegment).collect(Collectors.joining(";"));
    }

    /**
     * Десериализует строку в список объектов схемы.
     *
     * @param data сериализованная строка
     * @return список объектов схемы
     */
    private List<SchemeObject> deserializeSchemeData(String data) {
        List<SchemeObject> objects = new ArrayList<>();
        if (data == null || data.trim().isEmpty()) return objects;
        String[] segments = data.split(";");
        for (String seg : segments) {
            if (!seg.trim().isEmpty()) {
                SchemeObject obj = SchemeObject.fromString(seg.trim());
                if (obj != null) objects.add(obj);
            }
        }
        return objects;
    }
}