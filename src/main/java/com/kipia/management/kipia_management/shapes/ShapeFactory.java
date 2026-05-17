package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;

import java.util.function.Consumer;

import static com.kipia.management.kipia_management.shapes.ShapeBase.LOGGER;
import static com.kipia.management.kipia_management.shapes.ShapeBase.MIN_SHAPE_SIZE;

/**
 * Класс - фабрика создания фигур для редактора схем
 *
 * @author vladimir_shi
 * @since 25.10.2025
 */

public record ShapeFactory(AnchorPane pane, Consumer<String> statusSetter, Consumer<ShapeHandler> onSelectCallback,
                           ShapeManager shapeManager) {

    private static final double CANVAS_WIDTH = 2000.0;
    private static final double CANVAS_HEIGHT = 1200.0;

    public ShapeBase createShape(ShapeType type, double... coordinates) {
        type.validateCoordinates(coordinates);
        // ИСПРАВЛЕНО: передаём type, чтобы не путать endX линии с width прямоугольника
        clampCoordinatesToCanvas(type, coordinates);

        ShapeBase shape = switch (type) {
            case RECTANGLE -> createRectangle(coordinates);
            case ELLIPSE -> createEllipse(coordinates);
            case LINE -> createLine(coordinates);
            case RHOMBUS -> createRhombus(coordinates);
            case TEXT -> createText(coordinates);
        };

        shape.addContextMenu(shape::handleDelete);

        return shape;
    }

    private RectangleShape createRectangle(double[] coords) {
        double x = coords[0], y = coords[1], width = coords[2], height = coords[3];
        RectangleShape shape = new RectangleShape(x, y, width, height, pane, statusSetter, onSelectCallback, shapeManager);
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
        double startX = coords[0];
        double startY = coords[1];
        double endX   = coords[2];
        double endY   = coords[3];

        if (Double.isNaN(startX) || Double.isNaN(startY) || Double.isNaN(endX) || Double.isNaN(endY) ||
                Double.isInfinite(startX) || Double.isInfinite(startY) || Double.isInfinite(endX) || Double.isInfinite(endY)) {
            LOGGER.error("Invalid line coordinates, using defaults");
            startX = 0; startY = 0; endX = 100; endY = 100;
        }

        // Clamp уже применён в clampCoordinatesToCanvas, но страхуемся
        startX = Math.max(0, Math.min(startX, CANVAS_WIDTH));
        startY = Math.max(0, Math.min(startY, CANVAS_HEIGHT));
        endX   = Math.max(0, Math.min(endX,   CANVAS_WIDTH));
        endY   = Math.max(0, Math.min(endY,   CANVAS_HEIGHT));

        LOGGER.info("Creating Line: start=({},{}), end=({},{})", startX, startY, endX, endY);

        return new LineShape(startX, startY, endX, endY, pane, statusSetter, onSelectCallback, shapeManager);
    }

    private RhombusShape createRhombus(double[] coords) {
        double x = coords[0], y = coords[1];
        double width = coords[2], height = coords[3];

        LOGGER.info("Creating Rhombus in factory: x={}, y={}, width={}, height={}", x, y, width, height);

        RhombusShape shape = new RhombusShape(x, y, width, height, pane, statusSetter, onSelectCallback, shapeManager);
        shape.setFillColor(Color.TRANSPARENT);
        shape.setStrokeColor(Color.BLACK);
        return shape;
    }

    private TextShape createText(double... coords) {
        double x = coords[0], y = coords[1];
        return new TextShape(x, y, "Текст", pane, statusSetter, onSelectCallback, shapeManager);
    }

    /**
     * ИСПРАВЛЕНО: clamp теперь знает тип фигуры.
     *
     * Линия:              coords = [startX, startY, endX, endY]
     *                     — clamp каждой точки независимо, никакой "ширины".
     *
     * Прямоугольник/etc: coords = [x, y, width, height]
     *                     — clamp позиции, затем обрезаем width/height у границы.
     */
    private void clampCoordinatesToCanvas(ShapeType type, double[] coordinates) {
        if (type == ShapeType.LINE) {
            // [startX, startY, endX, endY] — две точки, не ширина/высота
            if (coordinates.length >= 2) {
                coordinates[0] = Math.max(0, Math.min(coordinates[0], CANVAS_WIDTH));
                coordinates[1] = Math.max(0, Math.min(coordinates[1], CANVAS_HEIGHT));
            }
            if (coordinates.length >= 4) {
                coordinates[2] = Math.max(0, Math.min(coordinates[2], CANVAS_WIDTH));
                coordinates[3] = Math.max(0, Math.min(coordinates[3], CANVAS_HEIGHT));
            }
            return;
        }

        // Все остальные: [x, y, width, height]
        if (coordinates.length >= 2) {
            coordinates[0] = Math.max(0, Math.min(coordinates[0], CANVAS_WIDTH));
            coordinates[1] = Math.max(0, Math.min(coordinates[1], CANVAS_HEIGHT));
        }
        if (coordinates.length >= 4) {
            double x = coordinates[0];
            double y = coordinates[1];
            double width = coordinates[2];
            double height = coordinates[3];

            if (x + width > CANVAS_WIDTH)   coordinates[2] = CANVAS_WIDTH  - x;
            if (y + height > CANVAS_HEIGHT)  coordinates[3] = CANVAS_HEIGHT - y;

            coordinates[2] = Math.max(MIN_SHAPE_SIZE, coordinates[2]);
            coordinates[3] = Math.max(MIN_SHAPE_SIZE, coordinates[3]);
        }
    }
}