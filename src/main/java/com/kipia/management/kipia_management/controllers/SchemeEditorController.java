package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.models.Scheme;  // Предполагаем, что ты создал эту модель
import com.kipia.management.kipia_management.models.DeviceLocation;  // Предполагаем, что ты создал эту модель
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.SchemeDAO;  // Предполагаем, что ты реализовал этот DAO
import com.kipia.management.kipia_management.services.DeviceLocationDAO;  // Предполагаем, что ты реализовал этот DAO
import com.kipia.management.kipia_management.utils.StyleUtils;  // Для hover-эффектов
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.Cursor;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vladimir_shi
 * @since 30.09.2025
 */

public class SchemeEditorController {
    // FXML элементы
    @FXML private ComboBox<Scheme> schemeComboBox;
    @FXML private ComboBox<Device> deviceComboBox;
    @FXML private Button newSchemeBtn, saveSchemeBtn, deleteSchemeBtn, selectToolBtn, lineToolBtn, rectToolBtn, addDeviceToolBtn;
    @FXML private AnchorPane schemePane;
    @FXML private Label statusLabel;

    // DAO (внедряются из MainController)
    private DeviceDAO deviceDAO;
    private SchemeDAO schemeDAO;
    private DeviceLocationDAO deviceLocationDAO;

    // Данные
    private Scheme currentScheme;
    private ObservableList<Scheme> schemeList;
    private ObservableList<Device> deviceList;

    // Режимы работы
    private enum Tool { SELECT, LINE, RECTANGLE, ADD_DEVICE }
    private Tool currentTool = Tool.SELECT;

    // Для рисования
    private double startX, startY;

    // -----------------------------------------------------------------
    //  PUBLIC API (внедрение из MainController)
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
    //  ИНИЦИАЛИЗАЦИЯ
    // -----------------------------------------------------------------
    @FXML
    private void initialize() {
        // Настройка ComboBox
        schemeComboBox.setItems(schemeList);
        schemeComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Scheme s) { return s != null ? s.getName() : ""; }
            @Override public Scheme fromString(String s) { return null; }
        });
        schemeComboBox.valueProperty().addListener((obs, oldV, newV) -> loadScheme(newV));

        deviceComboBox.setItems(deviceList);
        deviceComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Device d) { return d != null ? d.getInventoryNumber() + " - " + d.getName() : ""; }
            @Override public Device fromString(String s) { return null; }
        });

        // Обработчики кнопок инструментов
        setupToolButtons();

        // Обработчики для схем
        newSchemeBtn.setOnAction(e -> createNewScheme());
        saveSchemeBtn.setOnAction(e -> saveCurrentScheme());
        deleteSchemeBtn.setOnAction(e -> deleteCurrentScheme());

        // Стилизация
        applyButtonStyles();
    }

    public void init() {
        loadSchemes();   // Теперь DAO переданы
        loadDevices();   // Сначала устройства, потом схемы
    }

    private void loadSchemes() {
        schemeList = FXCollections.observableArrayList(schemeDAO.getAllSchemes());
    }

    private void loadDevices() {
        deviceList = FXCollections.observableArrayList(deviceDAO.getAllDevices());
    }

    private void setupToolButtons() {
        selectToolBtn.setOnAction(e -> currentTool = Tool.SELECT);
        lineToolBtn.setOnAction(e -> currentTool = Tool.LINE);
        rectToolBtn.setOnAction(e -> currentTool = Tool.RECTANGLE);
        addDeviceToolBtn.setOnAction(e -> currentTool = Tool.ADD_DEVICE);
    }

    private void applyButtonStyles() {
        for (Button btn : List.of(newSchemeBtn, saveSchemeBtn, deleteSchemeBtn, selectToolBtn, lineToolBtn, rectToolBtn, addDeviceToolBtn)) {
            StyleUtils.applyHoverAndAnimation(btn, "tool-button", "tool-button-hover");
        }
    }

    // -----------------------------------------------------------------
    //  РАБОТА С СХЕМАМИ
    // -----------------------------------------------------------------
    private void loadScheme(Scheme scheme) {
        if (scheme == null) return;
        currentScheme = scheme;
        schemePane.getChildren().clear();

        // Десериализуем объекты схемы
        List<SchemeObject> objects = deserializeSchemeData(scheme.getData());
        for (SchemeObject obj : objects) {
            schemePane.getChildren().add(obj.toNode());
        }

        // Загружаем приборы
        List<DeviceLocation> locations = deviceLocationDAO.getLocationsBySchemeId(scheme.getId());
        for (DeviceLocation loc : locations) {
            Device device = deviceDAO.getDeviceById(loc.getDeviceId());  // Предполагаем метод в DeviceDAO
            if (device != null) {
                Circle circle = new Circle(loc.getX(), loc.getY(), 10, Color.BLUE);
                circle.setCursor(Cursor.HAND);
                circle.setUserData(device);
                circle.setOnMouseClicked(this::onDeviceClick);
                schemePane.getChildren().add(circle);
            }
        }

        statusLabel.setText("Загружена схема: " + scheme.getName());
    }

    private void createNewScheme() {
        TextInputDialog dialog = new TextInputDialog("Новая схема");
        dialog.setTitle("Создать новую схему");
        dialog.setHeaderText("Введите имя схемы:");
        dialog.showAndWait().ifPresent(name -> {
            Scheme newScheme = new Scheme(0, name, "Описание", "{}");
            if (schemeDAO.addScheme(newScheme)) {
                schemeList.add(newScheme);
                schemeComboBox.setValue(newScheme);
                statusLabel.setText("Создана новая схема: " + name);
            }
        });
    }

    private void saveCurrentScheme() {
        if (currentScheme == null) return;

        // Сериализуем объекты
        List<SchemeObject> objects = new ArrayList<>();
        for (Node node : schemePane.getChildren()) {
            if (!(node instanceof Circle && ((Circle) node).getFill() == Color.BLUE)) {  // Исключаем приборы, они в device_locations
                SchemeObject obj = SchemeObject.fromNode(node);
                if (obj != null) objects.add(obj);
            }
        }
        currentScheme.setData(serializeSchemeData(objects));
        schemeDAO.updateScheme(currentScheme);

        // Обновляем координаты приборов
        for (Node node : schemePane.getChildren()) {
            if (node instanceof Circle && ((Circle) node).getFill() == Color.BLUE) {
                Circle circle = (Circle) node;
                Device device = (Device) node.getUserData();
                if (device != null) {
                    DeviceLocation loc = new DeviceLocation(device.getId(), currentScheme.getId(), circle.getCenterX(), circle.getCenterY());
                    deviceLocationDAO.updateDeviceLocation(loc);
                }
            }
        }

        statusLabel.setText("Схема сохранена: " + currentScheme.getName());
    }

    private void deleteCurrentScheme() {
        if (currentScheme == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Удалить схему " + currentScheme.getName() + "?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.YES) {
                schemeDAO.deleteScheme(currentScheme.getId());
                schemeList.remove(currentScheme);
                schemeComboBox.setValue(null);
                statusLabel.setText("Схема удалена");
            }
        });
    }

    // -----------------------------------------------------------------
    //  ИНТЕРАКТИВНОСТЬ НА ПАНЕ
    // -----------------------------------------------------------------
    @FXML
    private void onPaneMousePressed(MouseEvent event) {
        startX = event.getX();
        startY = event.getY();

        if (currentTool == Tool.ADD_DEVICE) {
            addDeviceAt(event.getX(), event.getY());
        }
    }

    @FXML
    private void onPaneMouseDragged(MouseEvent event) {
        // Для прямоугольника можно добавить preview, но пока пусто
    }

    @FXML
    private void onPaneMouseReleased(MouseEvent event) {
        double endX = event.getX();
        double endY = event.getY();

        if (currentTool == Tool.LINE) {
            Line line = new Line(startX, startY, endX, endY);
            line.setStroke(Color.BLACK);
            schemePane.getChildren().add(line);
        } else if (currentTool == Tool.RECTANGLE) {
            double width = Math.abs(endX - startX);
            double height = Math.abs(endY - startY);
            Rectangle rect = new Rectangle(Math.min(startX, endX), Math.min(startY, endY), width, height);
            rect.setFill(Color.TRANSPARENT);
            rect.setStroke(Color.BLACK);
            schemePane.getChildren().add(rect);
        }
    }

    private void addDeviceAt(double x, double y) {
        Device selected = deviceComboBox.getValue();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Выберите прибор из списка!");
            alert.show();
            return;
        }

        Circle circle = new Circle(x, y, 10, Color.BLUE);
        circle.setCursor(Cursor.HAND);
        circle.setUserData(selected);
        circle.setOnMouseClicked(this::onDeviceClick);
        schemePane.getChildren().add(circle);

        if (currentScheme != null) {
            DeviceLocation loc = new DeviceLocation(selected.getId(), currentScheme.getId(), x, y);
            deviceLocationDAO.addDeviceLocation(loc);
        }
    }

    private void onDeviceClick(MouseEvent event) {
        Circle circle = (Circle) event.getSource();
        Device device = (Device) circle.getUserData();
        if (device != null) {
            Alert details = new Alert(Alert.AlertType.INFORMATION);
            details.setTitle("Данные прибора");
            details.setHeaderText(device.getName() + " (" + device.getInventoryNumber() + ")");
            details.setContentText("Место: " + device.getLocation() + "\nСтатус: " + device.getStatus());
            details.show();
        }
    }

    // -----------------------------------------------------------------
    //  ВНУТРЕННИЙ КЛАСС ДЛЯ ОБЪЕКТОВ СХЕМЫ
    // -----------------------------------------------------------------
    private static class SchemeObject {
        enum Type { LINE, RECTANGLE }
        Type type;
        double x1, y1, x2, y2, width, height;

        SchemeObject(Type t, double... coords) {
            this.type = t;
            if (t == Type.LINE) {
                x1 = coords[0]; y1 = coords[1]; x2 = coords[2]; y2 = coords[3];
            } else {
                x1 = coords[0]; y1 = coords[1]; width = coords[2]; height = coords[3];
            }
        }

        Node toNode() {
            if (type == Type.LINE) {
                Line line = new Line(x1, y1, x2, y2);
                line.setStroke(Color.BLACK);
                return line;
            } else {
                Rectangle rect = new Rectangle(x1, y1, width, height);
                rect.setFill(Color.TRANSPARENT);
                rect.setStroke(Color.BLACK);
                return rect;
            }
        }

        // ЗАМЕНА: Не toJson, а toString для сериализации
        String toStringSegment() {
            if (type == Type.LINE) {
                return type.name() + "," + x1 + "," + y1 + "," + x2 + "," + y2;
            } else {
                return type.name() + "," + x1 + "," + y1 + "," + width + "," + height;
            }
        }

        // ЗАМЕНА: Не fromJson, а fromString
        static SchemeObject fromString(String str) {
            String[] parts = str.split(",");
            if (parts.length < 5) return null;  // Неверный формат
            Type t = Type.valueOf(parts[0]);
            try {
                double x1 = Double.parseDouble(parts[1]);
                double y1 = Double.parseDouble(parts[2]);
                if (t == Type.LINE) {
                    double x2 = Double.parseDouble(parts[3]);
                    double y2 = Double.parseDouble(parts[4]);
                    return new SchemeObject(t, x1, y1, x2, y2);
                } else {
                    double width = Double.parseDouble(parts[3]);
                    double height = Double.parseDouble(parts[4]);
                    return new SchemeObject(t, x1, y1, width, height);
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        static SchemeObject fromNode(Node node) {
            if (node instanceof Line) {
                Line line = (Line) node;
                return new SchemeObject(Type.LINE, line.getStartX(), line.getStartY(), line.getEndX(), line.getEndY());
            } else if (node instanceof Rectangle) {
                Rectangle rect = (Rectangle) node;
                return new SchemeObject(Type.RECTANGLE, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
            }
            return null;
        }
    }

    // ЗАМЕНИ: serializeSchemeData на:
    private String serializeSchemeData(List<SchemeObject> objects) {
        if (objects.isEmpty()) return "";
        List<String> segs = new ArrayList<>();
        for (SchemeObject obj : objects) {
            segs.add(obj.toStringSegment());
        }
        return String.join(";", segs);  // Разделить ";"
    }

    // ЗАМЕНИ: deserializeSchemeData на:
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
    //  КОНЕЦ КЛАССА
    // -----------------------------------------------------------------
}