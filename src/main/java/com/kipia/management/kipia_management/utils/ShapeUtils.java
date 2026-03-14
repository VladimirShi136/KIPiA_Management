package com.kipia.management.kipia_management.utils;

import com.kipia.management.kipia_management.shapes.*;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Утилиты для работы с фигурами (копия Android ShapeUtils)
 *
 * @author vladimir_shi
 * @since 08.03.2026
 */
public class ShapeUtils {

    private static final Logger LOGGER = LogManager.getLogger(ShapeUtils.class);

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

    // ============================================================
    // МЕТОДЫ ДЛЯ ОГРАНИЧЕНИЯ ПОЗИЦИИ
    // ============================================================

    /**
     * Ограничить позицию фигуры границами канваса
     * @param shape фигура
     * @param targetX целевая X координата (левый верхний угол)
     * @param targetY целевая Y координата (левый верхний угол)
     * @param canvasWidth ширина канваса
     * @param canvasHeight высота канваса
     * @return скорректированная позиция
     */
    public static Point2D clampShapePosition(ShapeBase shape, double targetX, double targetY,
                                             double canvasWidth, double canvasHeight) {
        // Сохраняем старую позицию
        double oldX = shape.getLayoutX();
        double oldY = shape.getLayoutY();

        // Временно перемещаем для расчета границ
        shape.setLayoutX(targetX);
        shape.setLayoutY(targetY);

        Rectangle2D bounds = getShapeBounds(shape);

        double clampedX = targetX;
        double clampedY = targetY;

        // Корректируем по X
        if (bounds.getMinX() < 0) {
            clampedX -= bounds.getMinX();
        } else if (bounds.getMaxX() > canvasWidth) {
            clampedX -= (bounds.getMaxX() - canvasWidth);
        }

        // Корректируем по Y
        if (bounds.getMinY() < 0) {
            clampedY -= bounds.getMinY();
        } else if (bounds.getMaxY() > canvasHeight) {
            clampedY -= (bounds.getMaxY() - canvasHeight);
        }

        // Возвращаем старую позицию
        shape.setLayoutX(oldX);
        shape.setLayoutY(oldY);

        // Логируем если позиция изменилась
        if (Math.abs(clampedX - targetX) > 0.01 || Math.abs(clampedY - targetY) > 0.01) {
            LOGGER.debug("Clamped shape position: ({:.1f},{:.1f}) -> ({:.1f},{:.1f})",
                    targetX, targetY, clampedX, clampedY);
        }

        return new Point2D(clampedX, clampedY);
    }

    /**
     * Проверить, находится ли фигура полностью внутри канваса
     */
    public static boolean isShapeWithinBounds(ShapeBase shape, double canvasWidth, double canvasHeight) {
        Rectangle2D bounds = getShapeBounds(shape);
        return bounds.getMinX() >= 0 && bounds.getMinY() >= 0 &&
                bounds.getMaxX() <= canvasWidth && bounds.getMaxY() <= canvasHeight;
    }

    // ============================================================
    // МЕТОДЫ ДЛЯ ТРАНСФОРМАЦИИ КООРДИНАТ
    // ============================================================

    /**
     * Трансформировать точку из мировых координат в локальные координаты фигуры
     * @param point точка в мировых координатах
     * @param shape фигура
     * @return точка в локальных координатах фигуры
     */
    public static Point2D worldToLocal(Point2D point, ShapeBase shape) {
        double centerX = shape.getLayoutX() + shape.getCurrentWidth() / 2;
        double centerY = shape.getLayoutY() + shape.getCurrentHeight() / 2;

        double localX = point.getX() - centerX;
        double localY = point.getY() - centerY;

        double radians = Math.toRadians(-shape.getRotate());
        double rotatedX = localX * Math.cos(radians) - localY * Math.sin(radians);
        double rotatedY = localX * Math.sin(radians) + localY * Math.cos(radians);

        return new Point2D(
                rotatedX + shape.getCurrentWidth() / 2,
                rotatedY + shape.getCurrentHeight() / 2
        );
    }

    /**
     * Трансформировать точку из локальных координат фигуры в мировые
     */
    public static Point2D localToWorld(Point2D point, ShapeBase shape) {
        double centerX = shape.getLayoutX() + shape.getCurrentWidth() / 2;
        double centerY = shape.getLayoutY() + shape.getCurrentHeight() / 2;

        double localX = point.getX() - shape.getCurrentWidth() / 2;
        double localY = point.getY() - shape.getCurrentHeight() / 2;

        double radians = Math.toRadians(shape.getRotate());
        double rotatedX = localX * Math.cos(radians) - localY * Math.sin(radians);
        double rotatedY = localX * Math.sin(radians) + localY * Math.cos(radians);

        return new Point2D(centerX + rotatedX, centerY + rotatedY);
    }

    // ============================================================
    // МЕТОДЫ ДЛЯ ПРОВЕРКИ ПЕРЕСЕЧЕНИЙ
    // ============================================================

    /**
     * Проверить, пересекаются ли две фигуры
     */
    public static boolean shapesIntersect(ShapeBase shape1, ShapeBase shape2) {
        Rectangle2D bounds1 = getShapeBounds(shape1);
        Rectangle2D bounds2 = getShapeBounds(shape2);

        return bounds1.intersects(bounds2);
    }

    /**
     * Найти фигуру под указанной мировой точкой
     * @param shapes список фигур
     * @param worldPoint точка в мировых координатах
     * @return фигура, или null если не найдена
     */
    public static ShapeBase findShapeAtPoint(List<ShapeBase> shapes, Point2D worldPoint) {
        // Ищем в обратном порядке (сверху)
        for (int i = shapes.size() - 1; i >= 0; i--) {
            ShapeBase shape = shapes.get(i);
            if (shape.containsWorldPoint(worldPoint.getX(), worldPoint.getY())) {
                return shape;
            }
        }
        return null;
    }

    // ============================================================
    // МЕТОДЫ ДЛЯ РАБОТЫ С РАЗМЕРАМИ
    // ============================================================

    /**
     * Масштабировать фигуру относительно центра
     */
    public static void scaleShape(ShapeBase shape, double factor, boolean keepProportions) {
        double newWidth = shape.getCurrentWidth() * factor;
        double newHeight = keepProportions ? shape.getCurrentHeight() * factor : newWidth;

        // Ограничиваем минимальный размер
        newWidth = Math.max(20, newWidth);
        newHeight = Math.max(20, newHeight);

        // Сохраняем центр
        double centerX = shape.getLayoutX() + shape.getCurrentWidth() / 2;
        double centerY = shape.getLayoutY() + shape.getCurrentHeight() / 2;

        shape.applyResize(newWidth, newHeight);

        // Возвращаем центр на место
        shape.setLayoutX(centerX - newWidth / 2);
        shape.setLayoutY(centerY - newHeight / 2);
    }

    /**
     * Получить расстояние между центрами двух фигур
     */
    public static double distanceBetweenCenters(ShapeBase shape1, ShapeBase shape2) {
        double x1 = shape1.getLayoutX() + shape1.getCurrentWidth() / 2;
        double y1 = shape1.getLayoutY() + shape1.getCurrentHeight() / 2;
        double x2 = shape2.getLayoutX() + shape2.getCurrentWidth() / 2;
        double y2 = shape2.getLayoutY() + shape2.getCurrentHeight() / 2;

        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    // ============================================================
    // МЕТОДЫ ДЛЯ ОТЛАДКИ
    // ============================================================

    /**
     * Вывести отладочную информацию о фигуре
     */
    public static void debugShapeInfo(ShapeBase shape, String prefix) {
        LOGGER.debug("{} Shape: type={}, pos=({:.1f},{:.1f}), size={:.1f}x{:.1f}, rotation={:.1f}°",
                prefix, shape.getShapeType(),
                shape.getLayoutX(), shape.getLayoutY(),
                shape.getCurrentWidth(), shape.getCurrentHeight(),
                shape.getRotate());

        Rectangle2D bounds = getShapeBounds(shape);
        LOGGER.debug("{}   bounds: ({:.1f},{:.1f}) - ({:.1f},{:.1f})",
                prefix,
                bounds.getMinX(), bounds.getMinY(),
                bounds.getMaxX(), bounds.getMaxY());
    }
}