package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import javafx.scene.layout.AnchorPane;

import java.util.function.Consumer;

/**
 * @author vladimir_shi
 * @since 25.10.2025
 */

public record ShapeFactory(AnchorPane pane, Consumer<String> statusSetter, Consumer<ShapeHandler> onSelectCallback,
                           ShapeManager shapeManager) {

    public ShapeBase createShape(ShapeType type, double... coordinates) {
        type.validateCoordinates(coordinates);

        return switch (type) {
            case RECTANGLE -> createRectangle(coordinates);
            case ELLIPSE -> createEllipse(coordinates);
            case LINE -> createLine(coordinates);
            case RHOMBUS -> createRhombus(coordinates);
            case TEXT -> createText(coordinates);
        };
    }

    private RectangleShape createRectangle(double[] coords) {
        double x = coords[0], y = coords[1], width = coords[2], height = coords[3];
        return new RectangleShape(x, y, width, height, pane, statusSetter, onSelectCallback, shapeManager);
    }

    private EllipseShape createEllipse(double[] coords) {
        // Унифицируем подход: левый верхний угол + размер
        double x = coords[0], y = coords[1], width = coords[2], height = coords[3];
        double centerX = x + width / 2;
        double centerY = y + height / 2;
        double radiusX = width / 2;
        double radiusY = height / 2;
        return new EllipseShape(centerX, centerY, radiusX, radiusY, pane, statusSetter, onSelectCallback, shapeManager);
    }

    private LineShape createLine(double[] coords) {
        double startX = coords[0], startY = coords[1], endX = coords[2], endY = coords[3];
        return new LineShape(startX, startY, endX, endY, pane, statusSetter, onSelectCallback, shapeManager);
    }

    private RhombusShape createRhombus(double[] coords) {
        double startX = coords[0], startY = coords[1], endX = coords[2], endY = coords[3];
        // Вычисляем left-top rect (как в preview)
        double x = Math.min(startX, endX);
        double y = Math.min(startY, endY);
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);
        // Передаём x,y,width,height — RhombusShape нарисует бабочку внутри
        return new RhombusShape(x, y, width, height, pane, statusSetter, onSelectCallback, shapeManager);
    }

    private TextShape createText(double... coords) {
        double x = coords[0], y = coords[1];
        String text = "Текст";
        return new TextShape(x, y, text, pane, statusSetter, onSelectCallback, shapeManager);
    }
}
