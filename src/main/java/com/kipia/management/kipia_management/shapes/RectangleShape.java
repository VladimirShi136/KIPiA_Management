package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;

import java.util.function.Consumer;

/**
 * Класс прямоугольной фигуры с поддержкой выделения, изменения размера и перемещения
 *
 * @author vladimir_shi
 * @since 22.10.2025
 */
public class RectangleShape extends ShapeBase {

    // ============================================================
    // STYLE CONSTANTS
    // ============================================================

    private static final Color DEFAULT_FILL = Color.TRANSPARENT;
    private static final Color DEFAULT_STROKE = Color.BLACK;
    private static final double DEFAULT_STROKE_WIDTH = 2.0;

    private static final Color SELECTED_FILL = Color.TRANSPARENT;
    private static final Color SELECTED_STROKE = Color.BLUE;
    private static final double SELECTED_STROKE_WIDTH = 3.0;

    // ============================================================
    // COMPONENTS
    // ============================================================

    private final Rectangle rectangle;

    // ============================================================
    // CONSTRUCTORS
    // ============================================================

    /**
     * Конструктор прямоугольника по координатам и размерам
     *
     * @param x координата X левого верхнего угла
     * @param y координата Y левого верхнего угла
     * @param width ширина прямоугольника
     * @param height высота прямоугольника
     * @param pane панель для отображения
     * @param statusSetter колбэк для статуса
     * @param onSelectCallback колбэк при выборе
     */
    public RectangleShape(double x, double y, double width, double height,
                          AnchorPane pane, Consumer<String> statusSetter,
                          Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        super(pane, statusSetter, onSelectCallback, shapeManager);

        this.rectangle = createRectangle();
        setCurrentDimensions(width, height);
        initializeShape(x, y, width, height);
    }

    // ============================================================
    // INITIALIZATION
    // ============================================================

    /**
     * Создание базового прямоугольника
     */
    private Rectangle createRectangle() {
        Rectangle rect = new Rectangle();
        applyDefaultStyle(rect);
        return rect;
    }

    /**
     * Инициализация фигуры
     */
    private void initializeShape(double x, double y, double width, double height) {
        setPosition(x, y);
        resizeShape(width, height);
        getChildren().add(rectangle);
    }

    // ============================================================
    // SHAPEBase IMPLEMENTATION
    // ============================================================

    /**
     * Изменение размера прямоугольника
     */
    @Override
    protected void resizeShape(double width, double height) {
        if (rectangle != null) {
            rectangle.setWidth(width);
            rectangle.setHeight(height);
            // Твоя логика, если есть (e.g., arc для rounded rect)
        }
        // Новое: Set stored exact
        setCurrentDimensions(width, height);
    }

    /**
     * Применение стиля выделения
     */
    @Override
    protected void applySelectedStyle() {
        applyStyle(rectangle, SELECTED_FILL, SELECTED_STROKE, SELECTED_STROKE_WIDTH);
    }

    /**
     * Применение стандартного стиля
     */
    @Override
    protected void applyDefaultStyle() {
        applyStyle(rectangle, DEFAULT_FILL, DEFAULT_STROKE, DEFAULT_STROKE_WIDTH);
    }

    /**
     * Получение типа фигуры
     */
    @Override
    protected String getShapeType() {
        return "RECTANGLE";
    }

    // ============================================================
    // STYLE MANAGEMENT
    // ============================================================

    /**
     * Применение стиля к прямоугольнику
     */
    private void applyStyle(Rectangle rect, Color fill, Color stroke, double strokeWidth) {
        rect.setFill(fill);
        rect.setStroke(stroke);
        rect.setStrokeWidth(strokeWidth);
        rect.setStrokeType(StrokeType.INSIDE);
    }

    /**
     * Применение стандартного стиля (для инициализации)
     */
    private void applyDefaultStyle(Rectangle rect) {
        applyStyle(rect, DEFAULT_FILL, DEFAULT_STROKE, DEFAULT_STROKE_WIDTH);
    }
}