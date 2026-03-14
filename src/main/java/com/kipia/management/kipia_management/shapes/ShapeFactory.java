package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;

import java.util.function.Consumer;

import static com.kipia.management.kipia_management.shapes.ShapeBase.LOGGER;

/**
 * Класс - фабрика создания фигур для редактора схем
 *
 * @author vladimir_shi
 * @since 25.10.2025
 */

public record ShapeFactory(AnchorPane pane, Consumer<String> statusSetter, Consumer<ShapeHandler> onSelectCallback,
                           ShapeManager shapeManager) {

    public ShapeBase createShape(ShapeType type, double... coordinates) {
        type.validateCoordinates(coordinates);

        ShapeBase shape = switch (type) {
            case RECTANGLE -> createRectangle(coordinates);
            case ELLIPSE -> createEllipse(coordinates);
            case LINE -> createLine(coordinates);
            case RHOMBUS -> createRhombus(coordinates);
            case TEXT -> createText(coordinates);
        };

        // ВАЖНО: Добавляем контекстное меню для новой фигуры
        shape.addContextMenu(shape::handleDelete);

        return shape;
    }

    private RectangleShape createRectangle(double[] coords) {
        double x = coords[0], y = coords[1], width = coords[2], height = coords[3];
        RectangleShape shape = new RectangleShape(x, y, width, height, pane, statusSetter, onSelectCallback, shapeManager);
        // Явно устанавливаем прозрачную заливку по умолчанию
        shape.setFillColor(Color.TRANSPARENT);
        shape.setStrokeColor(Color.BLACK);
        return shape;
    }

    private EllipseShape createEllipse(double[] coords) {
        double x = coords[0], y = coords[1], width = coords[2], height = coords[3];
        double centerX = x + width / 2;
        double centerY = y + height / 2;
        double radiusX = width / 2;
        double radiusY = height / 2;
        EllipseShape shape = new EllipseShape(centerX, centerY, radiusX, radiusY, pane, statusSetter, onSelectCallback, shapeManager);
        shape.setFillColor(Color.TRANSPARENT);
        shape.setStrokeColor(Color.BLACK);
        return shape;
    }

    private LineShape createLine(double[] coords) {
        double startX = coords[0], startY = coords[1], endX = coords[2], endY = coords[3];
        return new LineShape(startX, startY, endX, endY, pane, statusSetter, onSelectCallback, shapeManager);
    }

    private RhombusShape createRhombus(double[] coords) {
        double x = coords[0], y = coords[1];
        double width = coords[2], height = coords[3];

        LOGGER.info("Creating Rhombus in factory: x={}, y={}, width={}, height={}",
                x, y, width, height);

        RhombusShape shape = new RhombusShape(x, y, width, height, pane,
                statusSetter, onSelectCallback, shapeManager);
        shape.setFillColor(Color.TRANSPARENT);
        shape.setStrokeColor(Color.BLACK);
        return shape;
    }

    private TextShape createText(double... coords) {
        double x = coords[0], y = coords[1];
        String text = "Текст";
        return new TextShape(x, y, text, pane, statusSetter, onSelectCallback, shapeManager);
    }
}
