package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import javafx.scene.Cursor;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import java.util.function.Consumer;

/**
 * @author vladimir_shi
 * @since 24.10.2025
 */

public class LineShape extends ShapeBase {
    private Line line;
    private Color defaultStroke = Color.BLACK;
    private double defaultStrokeWidth = 1.0;
    private Color selectedStroke = Color.DODGERBLUE;
    private double selectedStrokeWidth = 2.5;
    // Новое: для handle поднятия
    private Circle startHandle, endHandle;
    private static final double HANDLE_RADIUS = 6.0;

    public LineShape(double startX, double startY, double endX, double endY,
                     AnchorPane pane, Consumer<String> statusSetter,
                     Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        super(pane, statusSetter, onSelectCallback, shapeManager);
        line = new Line(startX, startY, endX, endY);
        line.setStroke(defaultStroke);
        line.setStrokeWidth(defaultStrokeWidth);
        getChildren().add(line);
        createLineHandles();  // Новое: создаём custom handles для линии
    }

    @Override
    protected void resizeShape(double width, double height) {
        // For line: ignore, resize через handles
    }

    /**
     * Создаёт handles для линии (имплементирует логику из базового createResizeHandles)
     */
    private void createLineHandles() {
        if (startHandle == null) {
            startHandle = new Circle(HANDLE_RADIUS, Color.CORAL);
            startHandle.setCursor(javafx.scene.Cursor.CROSSHAIR);
            pane.getChildren().add(startHandle);
        }
        if (endHandle == null) {
            endHandle = new Circle(HANDLE_RADIUS, Color.CORAL);
            endHandle.setCursor(javafx.scene.Cursor.CROSSHAIR);
            pane.getChildren().add(endHandle);
        }
        updateResizeHandles();
        setupLineHandleEvents();  // Привязываем события drag
    }

    // Новое: override createResizeHandles для только 2 handles
    @Override
    public void createResizeHandles() {
        if (startHandle == null) {
            startHandle = new Circle(HANDLE_RADIUS, Color.CORAL);
            startHandle.setCursor(Cursor.CROSSHAIR);
            pane.getChildren().add(startHandle);
        }
        if (endHandle == null) {
            endHandle = new Circle(HANDLE_RADIUS, Color.CORAL);
            endHandle.setCursor(Cursor.CROSSHAIR);
            pane.getChildren().add(endHandle);
        }
        updateResizeHandles();
        setupLineHandleEvents();  // Привязываем drag события
    }

    @Override
    public void updateResizeHandles() {
        if (startHandle != null) {
            startHandle.setCenterX(getLayoutX() + line.getStartX());
            startHandle.setCenterY(getLayoutY() + line.getStartY());
        }
        if (endHandle != null) {
            endHandle.setCenterX(getLayoutX() + line.getEndX());
            endHandle.setCenterY(getLayoutY() + line.getEndY());
        }
    }

    // Новое: remove custom handles
    @Override
    public void removeResizeHandles() {
        if (startHandle != null) {
            pane.getChildren().remove(startHandle);
            startHandle.setOnMousePressed(null);
            startHandle.setOnMouseDragged(null);
            startHandle.setOnMouseReleased(null);
            startHandle = null;
        }
        if (endHandle != null) {
            pane.getChildren().remove(endHandle);
            endHandle.setOnMousePressed(null);
            endHandle.setOnMouseDragged(null);
            endHandle.setOnMouseReleased(null);
            endHandle = null;
        }
    }

    // Новое: setup events для drag handles (из ShapeBase setupResizeHandleHandler)
    private void setupLineHandleEvents() {
        if (shapeManager != null) {
            for (Circle handle : new Circle[]{startHandle, endHandle}) {
                if (handle != null) {
                    handle.setOnMousePressed(event -> {
                        // Инициализация drag
                        initialX = getLayoutX() + (handle == startHandle ? line.getStartX() : line.getEndX());
                        initialY = getLayoutY() + (handle == startHandle ? line.getStartY() : line.getEndY());
                        event.consume();
                    });
                    handle.setOnMouseDragged(event -> {
                        // Положение на pane
                        double sceneX = event.getSceneX();
                        double sceneY = event.getSceneY();
                        javafx.geometry.Point2D panePoint = pane.sceneToLocal(sceneX, sceneY);
                        double paneX = panePoint.getX();
                        double paneY = panePoint.getY();
                        double deltaX = paneX - initialX;
                        double deltaY = paneY - initialY;
                        if (handle == startHandle) {
                            line.setStartX(line.getStartX() + deltaX);
                            line.setStartY(line.getStartY() + deltaY);
                        } else {
                            line.setEndX(line.getEndX() + deltaX);
                            line.setEndY(line.getEndY() + deltaY);
                        }
                        initialX = paneX;
                        initialY = paneY;
                        updateResizeHandles();
                        event.consume();
                    });
                    handle.setOnMouseReleased(event -> event.consume());
                }
            }
        }
    }


    @Override
    protected void applySelectedStyle() {
        line.setStroke(selectedStroke);
        line.setStrokeWidth(selectedStrokeWidth);
    }

    @Override
    protected void applyDefaultStyle() {
        line.setStroke(defaultStroke);
        line.setStrokeWidth(defaultStrokeWidth);
    }

    @Override
    protected String getShapeType() {
        return "LINE";
    }
}