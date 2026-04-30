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

    private static final double CANVAS_WIDTH = 2000.0;
    private static final double CANVAS_HEIGHT = 1200.0;

    public ShapeBase createShape(ShapeType type, double... coordinates) {
        type.validateCoordinates(coordinates);
        // Корректируем координаты, чтобы фигура не выходила за пределы канваса
        clampCoordinatesToCanvas(coordinates);

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

    /**
     * Корректирует координаты фигуры, чтобы она не выходила за пределы канваса
     */
    private void clampCoordinatesToCanvas(double[] coordinates) {
        if (coordinates.length >= 2) {
            // Ограничиваем x и y (начальная позиция)
            coordinates[0] = Math.max(0, Math.min(coordinates[0], CANVAS_WIDTH - 20));
            coordinates[1] = Math.max(0, Math.min(coordinates[1], CANVAS_HEIGHT - 20));
        }
        if (coordinates.length >= 4) {
            // Для фигур с шириной и высотой (rectangle, ellipse, rhombus)
            double x = coordinates[0];
            double y = coordinates[1];
            double width = coordinates[2];
            double height = coordinates[3];
            
            // Проверяем, выходит ли фигура за границы
            if (x + width > CANVAS_WIDTH) {
                coordinates[2] = CANVAS_WIDTH - x;
            }
            if (y + height > CANVAS_HEIGHT) {
                coordinates[3] = CANVAS_HEIGHT - y;
            }
            // Минимальный размер
            coordinates[2] = Math.max(20, coordinates[2]);
            coordinates[3] = Math.max(20, coordinates[3]);
        }
        if (coordinates.length >= 4 && coordinates.length < 6) {
            // Для линий (startX, startY, endX, endY)
            coordinates[0] = Math.max(0, Math.min(coordinates[0], CANVAS_WIDTH));
            coordinates[1] = Math.max(0, Math.min(coordinates[1], CANVAS_HEIGHT));
            coordinates[2] = Math.max(0, Math.min(coordinates[2], CANVAS_WIDTH));
            coordinates[3] = Math.max(0, Math.min(coordinates[3], CANVAS_HEIGHT));
        }
    }
}
