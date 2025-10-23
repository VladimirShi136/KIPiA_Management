package com.kipia.management.kipia_management.shapes;


import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

import java.util.function.Consumer;

/**
 * Конкретная реализация ShapeHandler для прямоугольника (Rectangle).
 * Теперь наследуется от ShapeBase для унификации drag/resize.
 * @author vladimir_shi
 * @since 21.10.2025
 */
public class RectangleShape extends ShapeBase {
    private Rectangle rectangle;
    private Color defaultFill = Color.LIGHTGRAY;
    private Color defaultStroke = Color.BLACK;
    private double defaultStrokeWidth = 1.0;
    private Color selectedStroke = Color.DODGERBLUE;
    private double selectedStrokeWidth = 2.5;


    public RectangleShape(double x, double y, double width, double height, AnchorPane pane, Consumer statusSetter, Consumer onSelectCallback) {
        super(pane, statusSetter, onSelectCallback);
        rectangle = new Rectangle(0, 0, Math.max(1, width), Math.max(1, height)); // локальные coords: x=0,y=0
        rectangle.setFill(defaultFill);
        rectangle.setStroke(defaultStroke);
        rectangle.setStrokeWidth(defaultStrokeWidth);
        rectangle.setPickOnBounds(true);
        getChildren().add(rectangle);


// Позиция самого Shape — через layoutX/layoutY
        setPosition(x, y);

// Обновляем handles позиции (если уже созданы)
        updateHandles();

    }


    @Override
    protected void drawShape(double startX, double startY, double endX, double endY) {
        double x = Math.min(startX, endX);
        double y = Math.min(startY, endY);
        double w = Math.abs(endX - startX);
        double h = Math.abs(endY - startY);
        // Позиция в координатах pane родителя — используем setPosition
        setPosition(x, y);
        resizeShape(w, h);
    }


    @Override
    protected void resizeShape(double width, double height) {
        rectangle.setWidth(width);
        rectangle.setHeight(height);
        updateHandles();
    }


    @Override
    protected void updateHandles() {
        // Базовая логика расположения handles уже реализована в ShapeBase.updateResizeHandles()
        updateResizeHandles();
    }


    @Override
    public void highlightAsSelected() {
        rectangle.setStroke(selectedStroke);
        rectangle.setStrokeWidth(selectedStrokeWidth);
        // После смены stroke размеры bounds могли измениться — обновим handles
        updateHandles();
        makeResizeHandlesVisible();
    }


    @Override
    public void resetHighlight() {
        rectangle.setStroke(defaultStroke);
        rectangle.setStrokeWidth(defaultStrokeWidth);
        // Скрываем handles
        if (resizeHandles != null) {
            for (int i = 0; i < resizeHandles.length; i++) {
                if (resizeHandles[i] != null) resizeHandles[i].setVisible(false);
            }
        }
        updateHandles();
    }
}