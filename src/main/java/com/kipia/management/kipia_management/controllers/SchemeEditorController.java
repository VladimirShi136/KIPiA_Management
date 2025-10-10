package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.models.DeviceLocation;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.SchemeDAO;
import com.kipia.management.kipia_management.services.DeviceLocationDAO;
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
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.scene.Cursor;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

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
    private Button undoBtn, redoBtn, ellipseToolBtn, textToolBtn;  // Из FXML
    @FXML
    private AnchorPane schemePane;
    @FXML
    private Label statusLabel;

    // DAO (внедряются из MainController)
    private DeviceDAO deviceDAO;
    private SchemeDAO schemeDAO;
    private DeviceLocationDAO deviceLocationDAO;

    // Данные
    private Scheme currentScheme;
    private ObservableList<Device> deviceList;
    private ObservableList<Device> availableDevicesList;

    // Режимы работы
    private enum Tool {SELECT, LINE, RECTANGLE, ELLIPSE, TEXT, ADD_DEVICE}

    private Tool currentTool = Tool.SELECT;

    // Для рисования
    private double startX, startY;

    // Стек для Undo/Redo (только для фигур)
    private final Stack<Command> undoStack = new Stack<>();
    private final Stack<Command> redoStack = new Stack<>();

    // Preview для рисования фигур в реальном времени
    private Line previewLine;
    private Rectangle previewRect;
    private Ellipse previewEllipse;
    private Text previewText;

    private boolean addingText = false;

    // Выделение фигур для редактирования
    private Node selectedNode;
    private boolean isDraggingSelected;
    private Circle[] resizeHandles; // Маркеры для изменения размера
    private boolean isResizing;
    private int resizeCorner;
    private double dragOffsetX, dragOffsetY;

    // Интерфейс команд для Undo/Redo
    private interface Command {
        void execute();

        void undo();
    }

    private record AddShapeCommand(AnchorPane pane, Node shape) implements Command {

        @Override
        public void execute() {
            pane.getChildren().add(shape);
        }

        @Override
        public void undo() {
            pane.getChildren().remove(shape);
        }
    }

    private record MoveShapeCommand(Node shape, double oldX, double oldY, double newX, double newY) implements Command {

        @Override
        public void execute() {
            shape.setLayoutX(newX);
            shape.setLayoutY(newY);
        }

        @Override
        public void undo() {
            shape.setLayoutX(oldX);
            shape.setLayoutY(oldY);
        }
    }

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
            if (newV != null && !newV.equals(oldV)) {  // Добавь проверку на изменение, чтобы избежать дубликатов
                System.out.println("DEBUG: Listener detected scheme change from " + (oldV != null ? oldV.getName() : "null") + " to " + newV.getName());
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
            if (selectedNode != null && currentTool == Tool.SELECT) {
                switch (event.getCode()) {
                    case DELETE, BACK_SPACE -> deleteSelectedShape();
                }
            }
        });
        schemePane.setFocusTraversable(true);  // Чтобы schemePane мог получать фокус и клавиши

        setupToolButtons();

        // кнопки не видны и не управляются
        newSchemeBtn.setVisible(false);
        deleteSchemeBtn.setVisible(false);
        newSchemeBtn.setManaged(false);
        deleteSchemeBtn.setManaged(false);

        saveSchemeBtn.setOnAction(e -> saveCurrentScheme());

        applyButtonStyles();
    }

    public void init() {
        System.out.println("*** DEBUG: init() started for SchemeEditorController ***");

        // Проверяем DAO (если null — init() вызван слишком рано, выходим)
        if (deviceDAO == null) {
            System.out.println("ERROR: deviceDAO is null — init() called before setDeviceDAO!");
            return;
        }
        if (schemeDAO == null) {
            System.out.println("ERROR: schemeDAO is null");
            return;
        }
        if (deviceLocationDAO == null) {
            System.out.println("ERROR: deviceLocationDAO is null");
            return;
        }

        System.out.println("DEBUG: DAO are ready");

        // НОВЫЙ ПОРЯДОК: Сначала загружаем устройства, потом схемы (чтобы deviceList был готов)
        loadDevices();  // Загружаем приборы первым делом
        loadSchemes();  // Теперь схемы — будет работать с deviceList

        // Принудительно выбираем первую схему (если контроллер новый)
        if (!schemeComboBox.getItems().isEmpty() && schemeComboBox.getValue() == null) {
            Scheme firstScheme = schemeComboBox.getItems().get(0);
            schemeComboBox.setValue(firstScheme);
            currentScheme = firstScheme;
            loadScheme(firstScheme);  // Загрузит схему, добавит иконки из БД, обновит список
        }

        System.out.println("*** DEBUG: init() finished (one-time only) ***");
    }

    private void loadSchemes() {
        System.out.println("*** DEBUG: loadSchemes called ***");

        List<String> locations = deviceDAO.getDistinctLocations();
        System.out.println("DEBUG: Distinct locations: " + locations);

        List<Scheme> schemesFromLocations = new ArrayList<>();
        for (String location : locations) {
            System.out.println("DEBUG: Processing location: '" + location + "'");
            Scheme scheme = schemeDAO.findSchemeByName(location);
            if (scheme == null) {
                System.out.println("DEBUG: Scheme for '" + location + "' not found, creating new");
                scheme = new Scheme(0, location, "Автоматически созданная схема", "{}");
                boolean created = schemeDAO.addScheme(scheme);
                if (!created) {
                    System.out.println("DEBUG: Failed to create scheme for location: " + location);
                    continue;
                }
            } else {
                System.out.println("DEBUG: Found existing scheme for '" + location + "'");
            }
            schemesFromLocations.add(scheme);
        }

        System.out.println("DEBUG: Total schemes created/found: " + schemesFromLocations.size());

        ObservableList<Scheme> schemeList = FXCollections.observableArrayList(schemesFromLocations);
        schemeComboBox.setItems(schemeList);

        // УБРАН: schemeComboBox.getSelectionModel().select(0); — теперь в init(), чтобы контролировать вызов
        // Только если нет элементов — ошибка
        if (schemeList.isEmpty()) {
            System.out.println("ERROR: No schemes to load");
        }

        System.out.println("*** DEBUG: loadSchemes finished ***");
    }

    private void loadDevices() {
        System.out.println("*** DEBUG: loadDevices called ***");
        if (deviceDAO == null) {
            System.out.println("ERROR: deviceDAO is null in loadDevices");
            return;
        }
        deviceList = FXCollections.observableArrayList(deviceDAO.getAllDevices());
        System.out.println("DEBUG: Loaded " + deviceList.size() + " devices");
        for (Device d : deviceList) {
            System.out.println("DEBUG: Device id=" + d.getId() + ", location='" + d.getLocation() + "'");
        }
        // Устанавливаем временно в deviceComboBox для теста (потом refreshAvailableDevices() перезапишет)
        deviceComboBox.setItems(deviceList);
        System.out.println("*** DEBUG: loadDevices finished ***");
    }


    private void refreshAvailableDevices() {
        System.out.println("*** DEBUG: refreshAvailableDevices called ***");

        if (deviceList == null || deviceList.isEmpty()) {
            System.out.println("ERROR: deviceList is null or empty — cannot filter");
            availableDevicesList = FXCollections.observableArrayList();
            deviceComboBox.setItems(availableDevicesList);
            System.out.println("*** DEBUG: refreshAvailableDevices finished (empty list) ***");
            return;
        }

        if (currentScheme == null) {
            System.out.println("ERROR: currentScheme is null — cannot filter by location");
            availableDevicesList = FXCollections.observableArrayList();
            deviceComboBox.setItems(availableDevicesList);
            System.out.println("*** DEBUG: refreshAvailableDevices finished (no scheme) ***");
            return;
        }

        String selectedSchemeName = currentScheme.getName();
        System.out.println("DEBUG: Filtering for scheme: '" + selectedSchemeName + "'");

        // Получаем ID приборов на ТЕКУЩЕЙ схеме (чтобы исключить дубликаты на ней)
        List<Integer> currentSchemeDeviceIds = new ArrayList<>();
        List<DeviceLocation> currentLocations = deviceLocationDAO.getLocationsBySchemeId(currentScheme.getId());
        for (DeviceLocation loc : currentLocations) {
            currentSchemeDeviceIds.add(loc.getDeviceId());
        }
        System.out.println("DEBUG: Devices on current scheme: " + currentSchemeDeviceIds);

        // Получаем ID приборов из привязок к ДРУГИМ схемам (занятые elsewhere)
        List<DeviceLocation> allLocations = deviceLocationDAO.getAllLocations();
        System.out.println("DEBUG: Total locations in DB: " + allLocations.size());

        List<Integer> usedDeviceIds = new ArrayList<>();  // Только из других схем
        for (DeviceLocation loc : allLocations) {
            if (loc.getSchemeId() != currentScheme.getId()) {
                if (!usedDeviceIds.contains(loc.getDeviceId())) {
                    usedDeviceIds.add(loc.getDeviceId());
                }
            }
        }
        System.out.println("DEBUG: Used devices in other schemes: " + usedDeviceIds + " (current scheme ID: " + currentScheme.getId() + ")");

        // Фильтрация: Только приборы с location = schemeName, НЕ used elsewhere И НЕ на current scheme
        availableDevicesList = FXCollections.observableArrayList();
        int addedCount = 0;
        for (Device device : deviceList) {
            int devId = device.getId();
            if (selectedSchemeName.equals(device.getLocation())) {
                boolean isUsedElsewhere = usedDeviceIds.contains(devId);
                boolean isOnCurrentScheme = currentSchemeDeviceIds.contains(devId);

                System.out.println("DEBUG: Device " + devId + " (" + device.getName() + "): usedElsewhere=" + isUsedElsewhere + ", onCurrentScheme=" + isOnCurrentScheme);

                if (!isUsedElsewhere && !isOnCurrentScheme) {  // Исключаем и с текущей схемы (нет дубликатов!)
                    availableDevicesList.add(device);
                    addedCount++;
                    System.out.println("DEBUG: Added available device: " + device.getName() + " (ID " + devId + ")");
                } else if (isOnCurrentScheme) {
                    System.out.println("DEBUG: Excluded device " + devId + " — already on current scheme (no duplicates allowed)");
                } else {
                    System.out.println("DEBUG: Excluded device " + devId + " — used in other scheme");
                }
            } else {
                System.out.println("DEBUG: Device " + devId + " location '" + device.getLocation() + "' does not match scheme '" + selectedSchemeName + "'");
            }
        }

        deviceComboBox.setItems(availableDevicesList);

        System.out.println("DEBUG: Final available devices count: " + addedCount);

        if (!availableDevicesList.isEmpty()) {
            deviceComboBox.getSelectionModel().selectFirst();
            System.out.println("DEBUG: Selected first available device: " + availableDevicesList.get(0).getName());
        } else {
            deviceComboBox.getSelectionModel().clearSelection();
            System.out.println("DEBUG: No available devices, cleared selection");
        }

        statusLabel.setText("Доступных приборов: " + addedCount);  // Обновляем статус (опционально)
        System.out.println("*** DEBUG: refreshAvailableDevices finished ***");
    }

    private void clearPreview() {
        if (previewLine != null) {
            schemePane.getChildren().remove(previewLine);
            previewLine = null;
        }
        if (previewRect != null) {
            schemePane.getChildren().remove(previewRect);
            previewRect = null;
        }
        if (previewEllipse != null) {
            schemePane.getChildren().remove(previewEllipse);
            previewEllipse = null;
        }
        if (previewText != null) {
            schemePane.getChildren().remove(previewText);
            previewText = null;
        }
    }

    // -----------------------------------------------------------------
    // Undo/Redo методы (переданы сюда, чтобы быть доступными до initialize())
    // -----------------------------------------------------------------
    private void undo() {
        if (!undoStack.isEmpty()) {
            Command cmd = undoStack.pop();
            cmd.undo();
            redoStack.push(cmd);
            statusLabel.setText("Отменено");
        } else {
            statusLabel.setText("Нет действий для отмены");
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            Command cmd = redoStack.pop();
            cmd.execute();
            undoStack.push(cmd);
            statusLabel.setText("Повторено");
        } else {
            statusLabel.setText("Нет действий для повтора");
        }
    }

    private void setupToolButtons() {
        selectToolBtn.setOnAction(e -> {
            currentTool = Tool.SELECT;
            statusLabel.setText("Инструмент: Выбор");
        });
        undoBtn.setOnAction(e -> undo());
        redoBtn.setOnAction(e -> redo());
        lineToolBtn.setOnAction(e -> {
            currentTool = Tool.LINE;
            statusLabel.setText("Инструмент: Линия");
        });
        rectToolBtn.setOnAction(e -> {
            currentTool = Tool.RECTANGLE;
            statusLabel.setText("Инструмент: Прямоугольник");
        });
        ellipseToolBtn.setOnAction(e -> {  // Новый из FXML
            currentTool = Tool.ELLIPSE;
            statusLabel.setText("Инструмент: Эллипс");
        });
        textToolBtn.setOnAction(e -> {  // Новый из FXML
            currentTool = Tool.TEXT;
            statusLabel.setText("Инструмент: Текст");
        });
        addDeviceToolBtn.setOnAction(e -> {
            currentTool = Tool.ADD_DEVICE;
            statusLabel.setText("Инструмент: Добавить прибор");
        });
    }

    private void applyButtonStyles() {
        for (Button btn : List.of(selectToolBtn, undoBtn, redoBtn, lineToolBtn, rectToolBtn, ellipseToolBtn, textToolBtn, addDeviceToolBtn)) {  // Добавил новые
            StyleUtils.applyHoverAndAnimation(btn, "tool-button", "tool-button-hover");
        }
        //newSchemeBtn, saveSchemeBtn, deleteSchemeBtn
        StyleUtils.applyHoverAndAnimation(newSchemeBtn, "new-scheme-button", "new-scheme-button-hover");
        StyleUtils.applyHoverAndAnimation(saveSchemeBtn, "save-scheme-button", "save-scheme-button-hover");
        StyleUtils.applyHoverAndAnimation(deleteSchemeBtn, "delete-scheme-button", "delete-scheme-button-hover");
    }

    // -----------------------------------------------------------------
    // Вспомогательные методы для выделения и редактирования фигур
    // -----------------------------------------------------------------

    // Выделить фигуру
    private void selectShape(Node shape) {
        deselectShape();
        selectedNode = shape;
        selectedNode.setOnContextMenuRequested(e -> {
            ContextMenu contextMenu = new ContextMenu();
            MenuItem deleteItem = new MenuItem("Удалить");
            deleteItem.setOnAction(ev -> deleteSelectedShape());
            contextMenu.getItems().add(deleteItem);
            contextMenu.show(selectedNode, e.getScreenX(), e.getScreenY());
        });
        if (shape instanceof Rectangle || shape instanceof Ellipse || shape instanceof Line) {
            addResizeHandles();
        }
        if (shape instanceof Shape s) {
            s.setStroke(Color.RED);
            s.setStrokeWidth(3);
        }
        statusLabel.setText("Выбрана фигура");
    }

    // Сбросить выделение
    private void deselectShape() {
        if (selectedNode instanceof Shape s) {
            s.setStroke(Color.BLACK);
            s.setStrokeWidth(1);
        }
        if (resizeHandles != null) {
            for (Circle handle : resizeHandles) {
                schemePane.getChildren().remove(handle);
            }
            resizeHandles = null;
        }
        selectedNode = null;
    }

    // Добавить маркеры для изменения размера
    private void addResizeHandles() {
        if (resizeHandles != null) return;
        resizeHandles = new Circle[8];

        if (selectedNode instanceof Rectangle) {
            addRectangleResizeHandles((Rectangle) selectedNode);
        } else if (selectedNode instanceof Ellipse) {
            addEllipseResizeHandles((Ellipse) selectedNode);
        } else if (selectedNode instanceof Line) {
            addLineResizeHandles((Line) selectedNode);
        }
    }

    private void deleteSelectedShape() {
        if (selectedNode == null) return;

        Node shapeToRemove = selectedNode;  // Сохраняем ссылку до очистки
        deselectShape();                   // Очистка выделения и маркеров

        Command deleteCmd = new Command() {
            @Override
            public void execute() {
                schemePane.getChildren().remove(shapeToRemove);
            }

            @Override
            public void undo() {
                schemePane.getChildren().add(shapeToRemove);
            }
        };

        deleteCmd.execute();
        undoStack.push(deleteCmd);
        redoStack.clear();
        statusLabel.setText("Фигура удалена");
    }

    private void addRectangleResizeHandles(Rectangle rect) {
        double[] x = {rect.getX(), rect.getX() + rect.getWidth() / 2, rect.getX() + rect.getWidth()};
        double[] y = {rect.getY(), rect.getY() + rect.getHeight() / 2, rect.getY() + rect.getHeight()};
        int index = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i == 1 && j == 1) continue; // центр пропускаем
                Circle handle = createResizeHandle(x[i], y[j], index);
                resizeHandles[index++] = handle;
                schemePane.getChildren().add(handle);
            }
        }
    }

    private void addEllipseResizeHandles(Ellipse ellipse) {
        // 4 точки: по центру радиусов по X и Y (слева, справа, сверху, снизу)
        double cx = ellipse.getCenterX(), cy = ellipse.getCenterY();
        double rx = ellipse.getRadiusX(), ry = ellipse.getRadiusY();

        Circle left = createResizeHandle(cx - rx, cy, 0);
        Circle right = createResizeHandle(cx + rx, cy, 1);
        Circle top = createResizeHandle(cx, cy - ry, 2);
        Circle bottom = createResizeHandle(cx, cy + ry, 3);

        resizeHandles[0] = left;
        resizeHandles[1] = right;
        resizeHandles[2] = top;
        resizeHandles[3] = bottom;

        schemePane.getChildren().addAll(left, right, top, bottom);
    }

    // Вспомогательный метод создания маркера с обработчиками
    private Circle createResizeHandle(double x, double y, int handleIndex) {
        Circle handle = new Circle(x, y, 6, Color.CORAL);
        handle.setCursor(Cursor.CROSSHAIR);
        handle.setOnMousePressed(e -> {
            isResizing = true;
            resizeCorner = handleIndex;
            e.consume();
        });
        handle.setOnMouseDragged(e -> {
            if (!isResizing) return;
            double currX = e.getX();
            double currY = e.getY();
            if (selectedNode instanceof Rectangle) {
                resizeRectangleByHandle((Rectangle) selectedNode, resizeCorner, currX, currY);
            } else if (selectedNode instanceof Ellipse) {
                resizeEllipseByHandle((Ellipse) selectedNode, resizeCorner, currX, currY);
            } else if (selectedNode instanceof Line) {
                resizeLineByHandle((Line) selectedNode, resizeCorner, currX, currY);
            }
            updateResizeHandles();
            e.consume();
        });
        handle.setOnMouseReleased(e -> {
            isResizing = false;
            resizeCorner = -1;
            e.consume();
        });
        return handle;
    }

    private void resizeRectangleByHandle(Rectangle rect, int handleIdx, double x, double y) {
        double x0 = rect.getX();
        double y0 = rect.getY();
        double w0 = rect.getWidth();
        double h0 = rect.getHeight();

        double newX = x0;
        double newY = y0;
        double newWidth = w0;
        double newHeight = h0;

        switch (handleIdx) {
            case 0:  // top-left
                newX = x;
                newY = y;
                newWidth = w0 + (x0 - x);
                newHeight = h0 + (y0 - y);
                break;
            case 1:  // left-center
                newX = x;
                newWidth = w0 + (x0 - x);
                break;
            case 2:  // bottom-left
                newX = x;
                newHeight = y - y0;
                newWidth = w0 + (x0 - x);
                break;
            case 3:  // top-center
                newY = y;
                newHeight = h0 + (y0 - y);
                break;
            case 4:  // bottom-center
                newHeight = y - y0;
                break;
            case 5:  // top-right
                newWidth = x - x0;
                newY = y;
                newHeight = h0 + (y0 - y);
                break;
            case 6:  // right-center
                newWidth = x - x0;
                break;
            case 7:  // bottom-right
                newWidth = x - x0;
                newHeight = y - y0;
                break;
        }

        // Обработка “переворота” по ширине
        if (newWidth < 0) {
            newWidth = Math.abs(newWidth);
            newX = newX - newWidth;

            // Меняем маркер handleIdx на противоположный по горизонтали
            if (handleIdx == 0) handleIdx = 5;
            else if (handleIdx == 1) handleIdx = 6;
            else if (handleIdx == 2) handleIdx = 7;
            else if (handleIdx == 5) handleIdx = 0;
            else if (handleIdx == 6) handleIdx = 1;
            else if (handleIdx == 7) handleIdx = 2;
        }

        // Обработка “переворота” по высоте
        if (newHeight < 0) {
            newHeight = Math.abs(newHeight);
            newY = newY - newHeight;

            // Меняем маркер handleIdx на противоположный по вертикали
            if (handleIdx == 0) handleIdx = 2;
            else if (handleIdx == 3) handleIdx = 4;
            else if (handleIdx == 5) handleIdx = 7;
            else if (handleIdx == 2) handleIdx = 0;
            else if (handleIdx == 4) handleIdx = 3;
            else if (handleIdx == 7) handleIdx = 5;
        }

        // Обновляем фигуру
        rect.setX(newX);
        rect.setY(newY);
        rect.setWidth(newWidth);
        rect.setHeight(newHeight);

        // Обновляем текущий индекс маркера, чтобы обработать дальнейшее растягивание правильно
        resizeCorner = handleIdx;
    }

    private void resizeEllipseByHandle(Ellipse ellipse, int handleIdx, double x, double y) {
        switch (handleIdx) {
            case 0: // left radiusX
                ellipse.setRadiusX(Math.abs(ellipse.getCenterX() - x));
                break;
            case 1: // right radiusX
                ellipse.setRadiusX(Math.abs(x - ellipse.getCenterX()));
                break;
            case 2: // top radiusY
                ellipse.setRadiusY(Math.abs(ellipse.getCenterY() - y));
                break;
            case 3: // bottom radiusY
                ellipse.setRadiusY(Math.abs(y - ellipse.getCenterY()));
                break;
        }
    }

    private void resizeLineByHandle(Line line, int handleIdx, double x, double y) {
        if (handleIdx == 0) {
            line.setStartX(x);
            line.setStartY(y);
        } else if (handleIdx == 1) {
            line.setEndX(x);
            line.setEndY(y);
        }
    }

    private void addLineResizeHandles(Line line) {
        Circle startHandle = createResizeHandle(line.getStartX(), line.getStartY(), 0);
        Circle endHandle = createResizeHandle(line.getEndX(), line.getEndY(), 1);
        resizeHandles[0] = startHandle;
        resizeHandles[1] = endHandle;
        schemePane.getChildren().addAll(startHandle, endHandle);
    }

    // Обновить положение маркеров
    private void updateResizeHandles() {
        if (selectedNode instanceof Rectangle rect && resizeHandles != null) {
            double[] x = {rect.getX(), rect.getX() + rect.getWidth() / 2, rect.getX() + rect.getWidth()};
            double[] y = {rect.getY(), rect.getY() + rect.getHeight() / 2, rect.getY() + rect.getHeight()};
            int index = 0;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (i == 1 && j == 1) continue;
                    if (resizeHandles[index] != null) {
                        resizeHandles[index].setCenterX(x[i]);
                        resizeHandles[index].setCenterY(y[j]);
                    }
                    index++;
                }
            }
        } else if (selectedNode instanceof Ellipse ellipse && resizeHandles != null) {
            double cx = ellipse.getCenterX(), cy = ellipse.getCenterY();
            double rx = ellipse.getRadiusX(), ry = ellipse.getRadiusY();
            if (resizeHandles[0] != null) resizeHandles[0].setCenterX(cx - rx); // left
            if (resizeHandles[1] != null) resizeHandles[1].setCenterX(cx + rx); // right
            if (resizeHandles[2] != null) resizeHandles[2].setCenterY(cy - ry); // top
            if (resizeHandles[3] != null) resizeHandles[3].setCenterY(cy + ry); // bottom

            if (resizeHandles[0] != null) resizeHandles[0].setCenterY(cy);
            if (resizeHandles[1] != null) resizeHandles[1].setCenterY(cy);
            if (resizeHandles[2] != null) resizeHandles[2].setCenterX(cx);
            if (resizeHandles[3] != null) resizeHandles[3].setCenterX(cx);
        } else if (selectedNode instanceof Line line && resizeHandles != null) {
            if (resizeHandles[0] != null) {
                resizeHandles[0].setCenterX(line.getStartX());
                resizeHandles[0].setCenterY(line.getStartY());
            }
            if (resizeHandles[1] != null) {
                resizeHandles[1].setCenterX(line.getEndX());
                resizeHandles[1].setCenterY(line.getEndY());
            }
        }
    }

    // -----------------------------------------------------------------
    // РАБОТА С СХЕМАМИ
    // -----------------------------------------------------------------
    // Загрузить схему
    private void loadScheme(Scheme scheme) {
        System.out.println("*** DEBUG: loadScheme called for scheme: " + (scheme != null ? scheme.getName() : "null") + " ***");

        if (scheme == null) {
            System.out.println("DEBUG: scheme is null, exiting loadScheme");
            return;
        }
        currentScheme = scheme;
        System.out.println("DEBUG: currentScheme set to: " + currentScheme.getName());

        schemePane.getChildren().clear();
        deselectShape();
        undoStack.clear();
        redoStack.clear();

        List<SchemeObject> objects = deserializeSchemeData(scheme.getData());
        System.out.println("DEBUG: Loaded " + objects.size() + " scheme objects from data");

        for (SchemeObject obj : objects) {
            schemePane.getChildren().add(obj.toNode());
        }

        List<DeviceLocation> locations = deviceLocationDAO.getLocationsBySchemeId(scheme.getId());
        System.out.println("DEBUG: Found " + locations.size() + " device locations for this scheme");

        // ... добавление иконок из БД ...
        for (DeviceLocation loc : locations) {
            Device device = deviceDAO.getDeviceById(loc.getDeviceId());
            if (device != null) {
                Node deviceNode = createDeviceIcon(loc.getX(), loc.getY(), device);
                schemePane.getChildren().add(deviceNode);
                System.out.println("DEBUG: Restored device icon from DB: " + device.getName());
            }
        }

        refreshAvailableDevices();
        statusLabel.setText("Загружена схема: " + scheme.getName());
        System.out.println("*** DEBUG: loadScheme finished for " + scheme.getName() + " ***");
    }

    // Сохранить текущую схему
    private void saveCurrentScheme() {
        if (currentScheme == null) return;
        deselectShape();  // Сбросить выделение перед сохранением
        List<SchemeObject> objects = new ArrayList<>();
        for (Node node : schemePane.getChildren()) {
            if (!(node instanceof Circle && ((Circle) node).getFill() == Color.BLUE) && !(node instanceof Circle && resizeHandles != null && List.of(resizeHandles).contains(node))) {
                SchemeObject obj = SchemeObject.fromNode(node);
                if (obj != null) objects.add(obj);
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

    // Обновить схемы и приборы
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
        startX = event.getX();
        startY = event.getY();
        clearPreview();

        if (currentTool == Tool.SELECT) {
            Node hovered = schemePane.getChildren().stream()
                    .filter(node -> node instanceof Shape && !(node instanceof Circle && ((Circle) node).getFill() == Color.BLUE))
                    .filter(node -> node.contains(event.getX(), event.getY()))
                    .findFirst().orElse(null);
            if (hovered != null) {
                selectShape(hovered);
                isDraggingSelected = true;
                double realX = 0, realY = 0;
                switch (hovered) {
                    case Rectangle rect -> {
                        realX = rect.getX();
                        realY = rect.getY();
                    }
                    case Ellipse ellipse -> {
                        realX = ellipse.getCenterX();
                        realY = ellipse.getCenterY();
                    }
                    case Text text -> {
                        realX = text.getX();
                        realY = text.getY();
                    }
                    case Line line -> {
                        realX = line.getStartX();
                        realY = line.getStartY();
                    }
                    default -> {
                    }
                }
                dragOffsetX = event.getX() - realX;
                dragOffsetY = event.getY() - realY;
            } else {
                deselectShape();
            }
        } else if (currentTool == Tool.ADD_DEVICE) {
            addDeviceAt(event.getX(), event.getY());
        } else if (currentTool == Tool.LINE) {
            previewLine = new Line(startX, startY, startX, startY);
            previewLine.setStroke(Color.GRAY);
            schemePane.getChildren().add(previewLine);
        } else if (currentTool == Tool.RECTANGLE) {
            previewRect = new Rectangle(startX, startY, 0, 0);
            previewRect.setFill(Color.TRANSPARENT);
            previewRect.setStroke(Color.GRAY);
            schemePane.getChildren().add(previewRect);
        } else if (currentTool == Tool.ELLIPSE) {
            previewEllipse = new Ellipse(startX, startY, 0, 0);
            previewEllipse.setFill(Color.TRANSPARENT);
            previewEllipse.setStroke(Color.GRAY);
            schemePane.getChildren().add(previewEllipse);
        } else if (currentTool == Tool.TEXT) {
            TextInputDialog dialog = new TextInputDialog("Текст");
            dialog.setTitle("Введите текст");
            dialog.setHeaderText("Введите текст для добавления:");
            dialog.showAndWait().ifPresent(text -> {
                previewText = new Text(startX, startY, text);
                previewText.setFill(Color.BLACK);
                schemePane.getChildren().add(previewText);
                addingText = true;
            });
        }
    }

    @FXML
    private void onPaneMouseDragged(MouseEvent event) {
        double currentX = event.getX();
        double currentY = event.getY();

        // Приоритет: сначала проверяем, если это preview рисования (standalone инструменты)
        if (currentTool == Tool.LINE && previewLine != null) {
            previewLine.setEndX(currentX);
            previewLine.setEndY(currentY);
        } else if (currentTool == Tool.RECTANGLE && previewRect != null) {
            double width = Math.abs(currentX - startX);
            double height = Math.abs(currentY - startY);
            previewRect.setX(Math.min(startX, currentX));
            previewRect.setY(Math.min(startY, currentY));
            previewRect.setWidth(width);
            previewRect.setHeight(height);
        } else if (currentTool == Tool.ELLIPSE && previewEllipse != null) {
            double radiusX = Math.abs(currentX - startX) / 2;
            double radiusY = Math.abs(currentY - startY) / 2;
            previewEllipse.setRadiusX(radiusX);
            previewEllipse.setRadiusY(radiusY);
            previewEllipse.setCenterX((startX + currentX) / 2);
            previewEllipse.setCenterY((startY + currentY) / 2);
        } else {
            // Теперь инструменты редактирования (SELECT и RESIZING)
            if (currentTool == Tool.SELECT && isDraggingSelected && selectedNode != null) {
                double newX = event.getX() - dragOffsetX;
                double newY = event.getY() - dragOffsetY;
                // Обновляем реальные координаты фигуры
                switch (selectedNode) {
                    case Rectangle rect -> {
                        rect.setX(newX);
                        rect.setY(newY);
                    }
                    case Ellipse ellipse -> {
                        ellipse.setCenterX(newX);
                        ellipse.setCenterY(newY);
                    }
                    case Text text -> {
                        text.setX(newX);
                        text.setY(newY);
                    }
                    case Line line -> {
                        // Сдвигаем линию целиком
                        double dx = newX - line.getStartX();
                        double dy = newY - line.getStartY();
                        line.setStartX(newX);
                        line.setStartY(newY);
                        line.setEndX(line.getEndX() + dx);
                        line.setEndY(line.getEndY() + dy);
                    }
                    default -> {
                    }
                }
                updateResizeHandles();  // Обновляем маркеры
            } else if (isResizing && selectedNode != null) {
                double currX = event.getX(), currY = event.getY();
                if (selectedNode instanceof Rectangle rect) {
                    if (resizeCorner == 0) {  // Top-left
                        rect.setWidth(rect.getWidth() + (rect.getX() - currX));
                        rect.setHeight(rect.getHeight() + (rect.getY() - currY));
                        rect.setX(currX);
                        rect.setY(currY);
                    } else if (resizeCorner == 1) {  // Top-center
                        rect.setHeight(rect.getHeight() + (rect.getY() - currY));
                        rect.setY(currY);
                    } else if (resizeCorner == 2) {  // Top-right
                        rect.setWidth(currX - rect.getX());
                        rect.setHeight(rect.getHeight() + (rect.getY() - currY));
                        rect.setY(currY);
                    } else if (resizeCorner == 3) {  // Left-center
                        rect.setWidth(rect.getWidth() + (rect.getX() - currX));
                        rect.setX(currX);
                    } else if (resizeCorner == 4) {  // Right-center
                        rect.setWidth(currX - rect.getX());
                    } else if (resizeCorner == 5) {  // Bottom-left
                        rect.setWidth(rect.getWidth() + (rect.getX() - currX));
                        rect.setHeight(currY - rect.getY());
                        rect.setX(currX);
                    } else if (resizeCorner == 6) {  // Bottom-center
                        rect.setHeight(currY - rect.getY());
                    } else if (resizeCorner == 7) {  // Bottom-right
                        rect.setWidth(currX - rect.getX());
                        rect.setHeight(currY - rect.getY());
                    }
                }
                updateResizeHandles();
            }
        }
    }

    @FXML
    private void onPaneMouseReleased(MouseEvent event) {
        if (currentTool == Tool.SELECT) {
            if (isDraggingSelected && selectedNode != null) {
                // Запоминаем старое положение перед перемещением (для Undo, если нужно)
                double oldX = 0, oldY = 0, newX, newY;
                if (selectedNode instanceof Rectangle rect) {
                    newX = rect.getX();
                    newY = rect.getY();
                    // oldX/newY нужно отслеживать, но для простоты пропустим
                    undoStack.push(new MoveShapeCommand(selectedNode, oldX, oldY, newX, newY));
                } // Аналогично для других типов
                redoStack.clear();
            }
            isDraggingSelected = false;
            isResizing = false;
            return;
        }

        double endX = event.getX();
        double endY = event.getY();

        Node finalShape = null;
        if (currentTool == Tool.LINE && previewLine != null) {
            Line line = new Line(startX, startY, endX, endY);
            line.setStroke(Color.BLACK);
            finalShape = line;
        } else if (currentTool == Tool.RECTANGLE && previewRect != null) {
            Rectangle rect = new Rectangle(previewRect.getX(), previewRect.getY(), previewRect.getWidth(), previewRect.getHeight());
            rect.setFill(Color.TRANSPARENT);
            rect.setStroke(Color.BLACK);
            finalShape = rect;
        } else if (currentTool == Tool.ELLIPSE && previewEllipse != null) {
            Ellipse ellipse = new Ellipse(previewEllipse.getCenterX(), previewEllipse.getCenterY(), previewEllipse.getRadiusX(), previewEllipse.getRadiusY());
            ellipse.setFill(Color.TRANSPARENT);
            ellipse.setStroke(Color.BLACK);
            finalShape = ellipse;
        } else if (currentTool == Tool.TEXT && previewText != null && addingText) {
            finalShape = previewText;
        }

        clearPreview();

        if (finalShape != null) {
            undoStack.push(new AddShapeCommand(schemePane, finalShape));
            redoStack.clear();
            schemePane.getChildren().add(finalShape);
            statusLabel.setText("Фигура добавлена");
        }

        if (currentTool == Tool.TEXT) addingText = false;
    }

    private void addDeviceAt(double x, double y) {
        Device selected = deviceComboBox.getValue();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Выберите прибор из списка!");
            alert.show();
            return;
        }

        Node deviceNode = createDeviceIcon(x, y, selected);  // Теперь Node (ImageView или Circle)
        schemePane.getChildren().add(deviceNode);

        if (currentScheme != null) {
            DeviceLocation loc = new DeviceLocation(selected.getId(), currentScheme.getId(), x, y);
            deviceLocationDAO.addDeviceLocation(loc);
            System.out.println("DEBUG: Added location to DB for device " + selected.getName() + " at (" + x + ", " + y + ")");
        }

        refreshAvailableDevices();  // Теперь прибор пропадёт из списка (isOnCurrentScheme = true)
        statusLabel.setText("Прибор добавлен: " + selected.getName());
        System.out.println("DEBUG: Device added to scheme: " + selected.getName());
    }

    private void addContextMenuToIcon(ImageView icon, Device device) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Удалить прибор");
        MenuItem infoItem = new MenuItem("Показать информацию");
        deleteItem.setOnAction(event -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Подтверждение удаления");
            confirm.setHeaderText("Вы уверены, что хотите удалить прибор " + device.getName() + "?");
            confirm.setContentText("Это действие нельзя будет отменить.");
            confirm.showAndWait().ifPresent(result -> {
                if (result == ButtonType.OK) {
                    deviceLocationDAO.deleteDeviceLocation(device.getId(), currentScheme.getId());
                    schemePane.getChildren().remove(icon);
                    refreshAvailableDevices();
                    statusLabel.setText("Прибор удалён");
                }
            });
        });
        infoItem.setOnAction(event -> {
            Alert infoAlert = new Alert(Alert.AlertType.INFORMATION);
            infoAlert.setTitle("Информация о приборе");
            infoAlert.setHeaderText(device.getName() + " (" + device.getInventoryNumber() + ")");
            infoAlert.setContentText(
                    "Место: " + device.getLocation() + "\n" +
                            "Статус: " + device.getStatus() + "\n" +
                            "Дополнительно: " + device.getAdditionalInfo()
            );
            infoAlert.show();
        });
        contextMenu.getItems().addAll(deleteItem, infoItem);
        icon.setOnContextMenuRequested(event -> {
            contextMenu.show(icon, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void addMovementHandlersToIcon(ImageView icon) {
        final boolean[] dragged = {false};
        final double[] offsetX = new double[1];
        final double[] offsetY = new double[1];
        icon.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                // Рассчитываем смещение относительно центра иконки (поскольку layout посередине)
                offsetX[0] = event.getX() - icon.getLayoutX() - icon.getFitWidth() / 2;
                offsetY[0] = event.getY() - icon.getLayoutY() - icon.getFitHeight() / 2;
                dragged[0] = false;
                icon.setCursor(Cursor.MOVE);
            }
        });
        icon.setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) {
                double newX = event.getX() - offsetX[0];
                double newY = event.getY() - offsetY[0];
                icon.setLayoutX(newX);
                icon.setLayoutY(newY);
                dragged[0] = true;
            }
        });
        icon.setOnMouseReleased(event -> {
            icon.setCursor(Cursor.HAND);
            if (currentScheme != null && dragged[0]) {
                Device device = (Device) icon.getUserData();
                DeviceLocation loc = new DeviceLocation(device.getId(), currentScheme.getId(), icon.getLayoutX(), icon.getLayoutY());
                deviceLocationDAO.updateDeviceLocation(loc);
            }
            statusLabel.setText("Прибор перемещён");
        });
    }

    private Node createDeviceIcon(double x, double y, Device device) {
        Node iconNode;
        try {
            // Загружаем иконку — путь относительно resources
            Image iconImage = new Image(getClass().getResourceAsStream("/images/manometr.png"));

            // Проверка на ошибку загрузки (файл не найден, повреждён и т.д.)
            if (iconImage.isError()) {
                throw new Exception("Image load error: " + iconImage.getException());
            }

            ImageView deviceIcon = new ImageView(iconImage);
            deviceIcon.setFitWidth(24);  // Ширина иконки
            deviceIcon.setFitHeight(24); // Высота иконки
            deviceIcon.setPreserveRatio(true);  // Сохраняем соотношение сторон
            deviceIcon.setSmooth(true);  // Плавное масштабирование
            deviceIcon.setCursor(Cursor.HAND);  // Курсор по умолчанию

            iconNode = deviceIcon;
            System.out.println("DEBUG: Created ImageView icon for device: " + device.getName());

        } catch (Exception e) {
            System.err.println("Не удалось загрузить иконку прибора '" + device.getName() + "': " + e.getMessage() + " — fallback to Circle");

            // Fallback: Создаём видимый синий круг, как в оригинальном коде
            Circle fallbackCircle = new Circle(10, Color.BLUE);
            fallbackCircle.setCursor(Cursor.HAND);
            fallbackCircle.setStroke(Color.GRAY);  // Обводка для видимости
            fallbackCircle.setStrokeWidth(1);

            iconNode = fallbackCircle;
            System.out.println("DEBUG: Created Circle fallback for device: " + device.getName());
        }

        // Общие настройки для любого Node (ImageView или Circle)
        iconNode.setLayoutX(x);
        iconNode.setLayoutY(y);
        iconNode.setUserData(device);  // Сохраняем ссылку на Device

        // Добавляем контекстное меню (общий метод для Node, но адаптируем)
        addContextMenuToNode(iconNode, device);  // Новый общий метод, см. ниже

        // Добавляем обработчики перемещения (общий метод)
        addMovementHandlersToNode(iconNode);  // Новый общий метод, см. ниже

        return iconNode;
    }

    /**
     * Добавляет контекстное меню к Node (работает для ImageView и Circle).
     * @param node - узел (иконка или круг)
     * @param device - устройство
     */
    private void addContextMenuToNode(Node node, Device device) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Удалить прибор");
        MenuItem infoItem = new MenuItem("Показать информацию");

        deleteItem.setOnAction(event -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Подтверждение удаления");
            confirm.setHeaderText("Вы уверены, что хотите удалить прибор " + device.getName() + "?");
            confirm.setContentText("Это действие нельзя будет отменить.");
            confirm.showAndWait().ifPresent(result -> {
                if (result == ButtonType.OK) {
                    deviceLocationDAO.deleteDeviceLocation(device.getId(), currentScheme.getId());
                    schemePane.getChildren().remove(node);
                    refreshAvailableDevices();  // Обновляем список после удаления
                    statusLabel.setText("Прибор удалён");
                    System.out.println("DEBUG: Device deleted from scheme: " + device.getName());
                }
            });
        });

        infoItem.setOnAction(event -> {
            Alert infoAlert = new Alert(Alert.AlertType.INFORMATION);
            infoAlert.setTitle("Информация о приборе");
            infoAlert.setHeaderText(device.getName() + " (" + device.getInventoryNumber() + ")");
            infoAlert.setContentText(
                    "Место: " + device.getLocation() + "\n" +
                            "Статус: " + device.getStatus() + "\n" +
                            "Дополнительно: " + device.getAdditionalInfo()
            );
            infoAlert.show();
        });

        contextMenu.getItems().addAll(deleteItem, infoItem);

        node.setOnContextMenuRequested(event -> {
            contextMenu.show(node, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        System.out.println("DEBUG: Context menu added to node for device: " + device.getName());
    }

    /**
     * Добавляет обработчики перемещения к Node (работает для ImageView и Circle).
     * @param node - узел (иконка или круг)
     */
    private void addMovementHandlersToNode(Node node) {
        final boolean[] dragged = {false};
        final double[] offsetX = new double[1];
        final double[] offsetY = new double[1];

        node.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                // Рассчитываем смещение от точки клика до центра элемента
                double centerOffsetX = 0, centerOffsetY = 0;
                if (node instanceof ImageView iv) {
                    centerOffsetX = iv.getFitWidth() / 2;
                    centerOffsetY = iv.getFitHeight() / 2;
                } else if (node instanceof Circle c) {
                    centerOffsetX = c.getRadius();
                    centerOffsetY = c.getRadius();
                }
                offsetX[0] = event.getSceneX() - (node.getLayoutX() + centerOffsetX);
                offsetY[0] = event.getSceneY() - (node.getLayoutY() + centerOffsetY);
                dragged[0] = false;
                node.setCursor(Cursor.MOVE);
                System.out.println("DEBUG: Drag started for node at (" + node.getLayoutX() + ", " + node.getLayoutY() + ")");
            }
        });

        node.setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) {
                // Обновляем позицию: новая мышь минус offset (для следования за кликом)
                double newLayoutX = event.getSceneX() - offsetX[0];
                double newLayoutY = event.getSceneY() - offsetY[0];

                // Ограничиваем позицию границами pane (опционально, чтобы не выезжало за край)
                newLayoutX = Math.max(0, Math.min(newLayoutX, schemePane.getWidth() - 50));  // 50 — размер иконки/круга
                newLayoutY = Math.max(0, Math.min(newLayoutY, schemePane.getHeight() - 50));

                node.setLayoutX(newLayoutX);
                node.setLayoutY(newLayoutY);
                dragged[0] = true;
                System.out.println("DEBUG: Node dragged to (" + newLayoutX + ", " + newLayoutY + ")");
            }
        });

        node.setOnMouseReleased(event -> {
            node.setCursor(Cursor.HAND);  // Возвращаем стандартный курсор
            if (currentScheme != null && dragged[0]) {
                // Извлекаем Device из userData
                Device device = (Device) node.getUserData();
                if (device != null) {
                    // Создаём обновлённую DeviceLocation с новой позицией
                    DeviceLocation loc = new DeviceLocation(device.getId(), currentScheme.getId(), node.getLayoutX(), node.getLayoutY());
                    deviceLocationDAO.updateDeviceLocation(loc);  // Сохраняем в БД

                    // Обновляем список доступных (иногда нужно, если позиция влияет на фильтр)
                    refreshAvailableDevices();

                    statusLabel.setText("Прибор перемещён");
                    System.out.println("DEBUG: Device " + device.getName() + " position updated in DB to (" + node.getLayoutX() + ", " + node.getLayoutY() + ")");
                } else {
                    System.out.println("WARN: No Device in userData for node");
                }
            } else if (!dragged[0]) {
                System.out.println("DEBUG: Clicked but no drag on node");
            }
            dragged[0] = false;  // Сброс для следующего раза
        });

        System.out.println("DEBUG: Movement handlers added to node");
    }

    // -----------------------------------------------------------------
    // ВНУТРЕННИЙ КЛАСС ДЛЯ ОБЪЕКТОВ СХЕМЫ
    // -----------------------------------------------------------------
    private static class SchemeObject {
        enum Type {LINE, RECTANGLE, ELLIPSE, TEXT}

        Type type;
        double x1, y1, x2, y2, width, height, radiusX, radiusY;
        String text;

        SchemeObject(Type t, double... coords) {
            this.type = t;
            if (t == Type.LINE) {
                x1 = coords[0];
                y1 = coords[1];
                x2 = coords[2];
                y2 = coords[3];
            } else if (t == Type.RECTANGLE) {
                x1 = coords[0];
                y1 = coords[1];
                width = coords[2];
                height = coords[3];
            } else if (t == Type.ELLIPSE) {
                x1 = coords[0];
                y1 = coords[1];
                radiusX = coords[2];
                radiusY = coords[3];
            } else if (t == Type.TEXT) {
                x1 = coords[0];
                y1 = coords[1];
                text = coords.length > 2 ? String.valueOf(coords[2]) : "Текст";
            }
        }

        Node toNode() {
            if (type == Type.LINE) {
                Line line = new Line(x1, y1, x2, y2);
                line.setStroke(Color.BLACK);
                return line;
            } else if (type == Type.RECTANGLE) {
                Rectangle rect = new Rectangle(x1, y1, width, height);
                rect.setFill(Color.TRANSPARENT);
                rect.setStroke(Color.BLACK);
                return rect;
            } else if (type == Type.ELLIPSE) {
                Ellipse ellipse = new Ellipse(x1, y1, radiusX, radiusY);
                ellipse.setFill(Color.TRANSPARENT);
                ellipse.setStroke(Color.BLACK);
                return ellipse;
            } else if (type == Type.TEXT) {
                return new Text(x1, y1, text);
            }
            return null;
        }

        String toStringSegment() {
            if (type == Type.LINE) {
                return type.name() + "," + x1 + "," + y1 + "," + x2 + "," + y2;
            } else if (type == Type.RECTANGLE) {
                return type.name() + "," + x1 + "," + y1 + "," + width + "," + height;
            } else if (type == Type.ELLIPSE) {
                return type.name() + "," + x1 + "," + y1 + "," + radiusX + "," + radiusY;
            } else if (type == Type.TEXT) {
                return type.name() + "," + x1 + "," + y1 + "," + text;
            }
            return "";
        }

        static SchemeObject fromString(String str) {
            String[] parts = str.split(",");
            if (parts.length < 5) return null;
            Type t = Type.valueOf(parts[0]);
            try {
                double x1 = Double.parseDouble(parts[1]);
                double y1 = Double.parseDouble(parts[2]);
                if (t == Type.LINE) {
                    double x2 = Double.parseDouble(parts[3]);
                    double y2 = Double.parseDouble(parts[4]);
                    return new SchemeObject(t, x1, y1, x2, y2);
                } else if (t == Type.RECTANGLE) {
                    double width = Double.parseDouble(parts[3]);
                    double height = Double.parseDouble(parts[4]);
                    return new SchemeObject(t, x1, y1, width, height);
                } else if (t == Type.ELLIPSE) {
                    double radiusX = Double.parseDouble(parts[3]);
                    double radiusY = Double.parseDouble(parts[4]);
                    return new SchemeObject(t, x1, y1, radiusX, radiusY);
                } else if (t == Type.TEXT) {
                    return new SchemeObject(t, x1, y1, 0, 0);  // текст в конструкторе
                }
            } catch (NumberFormatException e) {
                return null;
            }
            return null;
        }

        static SchemeObject fromNode(Node node) {
            if (node instanceof Line line) {
                return new SchemeObject(Type.LINE, line.getStartX(), line.getStartY(), line.getEndX(), line.getEndY());
            } else if (node instanceof Rectangle rect) {
                return new SchemeObject(Type.RECTANGLE, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
            } else if (node instanceof Ellipse ellipse) {
                return new SchemeObject(Type.ELLIPSE, ellipse.getCenterX(), ellipse.getCenterY(), ellipse.getRadiusX(), ellipse.getRadiusY());
            } else if (node instanceof Text text) {
                return new SchemeObject(Type.TEXT, text.getX(), text.getY(), 0, 0);  // текст в конструкторе
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
        return String.join(";", segs);
    }

    private List<SchemeObject> deserializeSchemeData(String data) {
        List<SchemeObject> objects = new ArrayList<>();
        if (data == null || data.trim().isEmpty()) return objects;
        String[] segments = data.split(";");
        for (String seg : segments) {
            SchemeObject obj = SchemeObject.fromString(seg.trim());
            if (obj != null) {
                objects.add(obj);
            }
        }
        return objects;
    }

    // -----------------------------------------------------------------
    // КОНЕЦ КЛАССА
    // -----------------------------------------------------------------
}