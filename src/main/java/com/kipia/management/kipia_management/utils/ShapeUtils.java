package com.kipia.management.kipia_management.utils;

import com.kipia.management.kipia_management.shapes.*;
import javafx.geometry.Rectangle2D;

/**
 * Утилиты для работы с фигурами (копия Android ShapeUtils)
 *
 * @author vladimir_shi
 * @since 08.03.2026
 */
public class ShapeUtils {

    // ============================================================
    // МЕТОДЫ ДЛЯ РАБОТЫ С ГРАНИЦАМИ ФИГУР
    // ============================================================

    /**
     * Получить реальные границы фигуры с учетом поворота
     */
    public static Rectangle2D getShapeBounds(ShapeBase shape) {
        if (shape == null) {
            return new Rectangle2D(0, 0, 0, 0);
        }

        // Если нет поворота, возвращаем обычный bounding box
        if (shape.getRotate() == 0) {
            return new Rectangle2D(
                    shape.getLayoutX(),
                    shape.getLayoutY(),
                    shape.getCurrentWidth(),
                    shape.getCurrentHeight()
            );
        }

        // Для фигур с поворотом используем специализированные методы
        if (shape instanceof RectangleShape || shape instanceof EllipseShape || shape instanceof TextShape) {
            return getRotatedRectBounds(shape);
        } else if (shape instanceof RhombusShape) {
            return getRotatedRhombusBounds((RhombusShape) shape);
        } else if (shape instanceof LineShape) {
            return getLineBounds((LineShape) shape);
        }

        // Fallback - используем метод из ShapeBase
        return shape.getWorldBounds();
    }

    /**
     * Получить границы повернутого прямоугольника/эллипса/текста
     */
    private static Rectangle2D getRotatedRectBounds(ShapeBase shape) {
        double centerX = shape.getLayoutX() + shape.getCurrentWidth() / 2;
        double centerY = shape.getLayoutY() + shape.getCurrentHeight() / 2;
        double radians = Math.toRadians(shape.getRotate());

        double halfW = shape.getCurrentWidth() / 2;
        double halfH = shape.getCurrentHeight() / 2;

        // Четыре угла прямоугольника
        double[][] corners = {
                {-halfW, -halfH},
                {halfW, -halfH},
                {halfW, halfH},
                {-halfW, halfH}
        };

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (double[] corner : corners) {
            double rotatedX = corner[0] * Math.cos(radians) - corner[1] * Math.sin(radians);
            double rotatedY = corner[0] * Math.sin(radians) + corner[1] * Math.cos(radians);

            double worldX = centerX + rotatedX;
            double worldY = centerY + rotatedY;

            minX = Math.min(minX, worldX);
            minY = Math.min(minY, worldY);
            maxX = Math.max(maxX, worldX);
            maxY = Math.max(maxY, worldY);
        }

        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Получить границы повернутого ромба (бабочки)
     */
    private static Rectangle2D getRotatedRhombusBounds(RhombusShape shape) {
        double centerX = shape.getLayoutX() + shape.getCurrentWidth() / 2;
        double centerY = shape.getLayoutY() + shape.getCurrentHeight() / 2;
        double radians = Math.toRadians(shape.getRotate());

        double halfW = shape.getCurrentWidth() / 2;
        double halfH = shape.getCurrentHeight() / 2;

        // Все ключевые точки ромба (для точности берем 5 точек)
        double[][] points = {
                {0, 0},                          // левый верхний
                {shape.getCurrentWidth(), 0},     // правый верхний
                {shape.getCurrentWidth() / 2, shape.getCurrentHeight() / 2}, // центр
                {0, shape.getCurrentHeight()},    // левый нижний
                {shape.getCurrentWidth(), shape.getCurrentHeight()} // правый нижний
        };

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (double[] point : points) {
            double relX = point[0] - halfW;
            double relY = point[1] - halfH;

            double rotatedX = relX * Math.cos(radians) - relY * Math.sin(radians);
            double rotatedY = relX * Math.sin(radians) + relY * Math.cos(radians);

            double worldX = centerX + rotatedX;
            double worldY = centerY + rotatedY;

            minX = Math.min(minX, worldX);
            minY = Math.min(minY, worldY);
            maxX = Math.max(maxX, worldX);
            maxY = Math.max(maxY, worldY);
        }

        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Получить границы линии с учетом поворота и толщины
     */
    private static Rectangle2D getLineBounds(LineShape shape) {
        double centerX = shape.getLayoutX() + shape.getCurrentWidth() / 2;
        double centerY = shape.getLayoutY() + shape.getCurrentHeight() / 2;
        double radians = Math.toRadians(shape.getRotate());

        double halfW = shape.getCurrentWidth() / 2;
        double halfH = shape.getCurrentHeight() / 2;
        double halfStroke = shape.getStrokeWidth() / 2;

        // Точки линии с учетом толщины
        double[][] points = {
                {shape.getStartX() - shape.getLayoutX(), shape.getStartY() - shape.getLayoutY()},
                {shape.getEndX() - shape.getLayoutX(), shape.getEndY() - shape.getLayoutY()},
                {shape.getStartX() - shape.getLayoutX(), shape.getStartY() - shape.getLayoutY() - halfStroke},
                {shape.getStartX() - shape.getLayoutX(), shape.getStartY() - shape.getLayoutY() + halfStroke},
                {shape.getEndX() - shape.getLayoutX(), shape.getEndY() - shape.getLayoutY() - halfStroke},
                {shape.getEndX() - shape.getLayoutX(), shape.getEndY() - shape.getLayoutY() + halfStroke}
        };

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (double[] point : points) {
            double relX = point[0] - halfW;
            double relY = point[1] - halfH;

            double rotatedX = relX * Math.cos(radians) - relY * Math.sin(radians);
            double rotatedY = relX * Math.sin(radians) + relY * Math.cos(radians);

            double worldX = centerX + rotatedX;
            double worldY = centerY + rotatedY;

            minX = Math.min(minX, worldX);
            minY = Math.min(minY, worldY);
            maxX = Math.max(maxX, worldX);
            maxY = Math.max(maxY, worldY);
        }

        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }
}