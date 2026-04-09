package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.StrokeType;

import java.util.function.Consumer;

public class EllipseShape extends ShapeBase {

    private static final double DEFAULT_STROKE_WIDTH = 2.0;
    private static final double SELECTED_STROKE_WIDTH = 3.0;

    private final Ellipse ellipse;

    /**
     * EllipseShape: Конструктор с радиусами (вычисляем full width/height для stored)
     */
    public EllipseShape(double centerX, double centerY, double radiusX, double radiusY,
                        AnchorPane pane, Consumer<String> statusSetter,
                        Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        super(pane, statusSetter, onSelectCallback, shapeManager);

        // Новое: Вычисляем full dimensions для stored (diameter = 2 * radius)
        double width = 2 * radiusX;
        double height = 2 * radiusY;

        this.ellipse = createEllipse();  // Твой метод: создаёт Ellipse с 0/0/radiusX/radiusY
        setCurrentDimensions(width, height);  // Теперь OK: stored = full width/height
        initializeShape(centerX, centerY, radiusX, radiusY);  // Твой метод: setCenterX/Y, radius, layoutX/Y = center - radius (left-top), add to group
    }

    private Ellipse createEllipse() {
        Ellipse el = new Ellipse(0, 0, 0, 0);  // Initial radii=0
        el.setFill(Color.LIGHTBLUE);
        el.setStroke(Color.BLACK);
        el.setStrokeWidth(2.0);
        return el;
    }

    private void initializeShape(double centerX, double centerY, double radiusX, double radiusY) {
        // Позиция группы = left-top bounding rect
        setLayoutX(centerX - radiusX);
        setLayoutY(centerY - radiusY);

        // Set ellipse
        ellipse.setCenterX(radiusX);  // Относительно группы (center = width/2, height/2)
        ellipse.setCenterY(radiusY);
        ellipse.setRadiusX(radiusX);
        ellipse.setRadiusY(radiusY);

        getChildren().add(ellipse);

        applyDefaultStyle();
    }

    @Override
    protected void resizeShape(double newWidth, double newHeight) {
        if (ellipse != null) {
            double newRadiusX = newWidth / 2;
            double newRadiusY = newHeight / 2;
            ellipse.setCenterX(newWidth / 2);  // Relative в группе (fixed offset)
            ellipse.setCenterY(newHeight / 2);
            ellipse.setRadiusX(newRadiusX);
            ellipse.setRadiusY(newRadiusY);
        }
        setCurrentDimensions(newWidth, newHeight);
    }

    @Override
    protected void applyCurrentStyle() {
        applyStyle(ellipse, fillColor, strokeColor, DEFAULT_STROKE_WIDTH);
    }

    @Override
    protected void applySelectedStyle() {
        applyStyle(ellipse, fillColor, Color.BLUE, SELECTED_STROKE_WIDTH);
    }

    @Override
    protected void applyDefaultStyle() {
        applyCurrentStyle();
    }

    @Override
    public String getShapeType() {
        return "ELLIPSE";
    }

    /**
     * Получение максимального X относительно позиции фигуры
     */
    @Override
    protected double getMaxRelativeX() {
        return getCurrentWidth();
    }

    /**
     * Получение максимального Y относительно позиции фигуры
     */
    @Override
    protected double getMaxRelativeY() {
        return getCurrentHeight();
    }

    public Ellipse getEllipse() {
        return ellipse;
    }

    /**
     * Проверка попадания точки на границу эллипса (только контур, не внутренность)
     */
    @Override
    protected boolean containsLocalPoint(double localX, double localY) {
        double radiusX = getCurrentWidth() / 2;
        double radiusY = getCurrentHeight() / 2;
        double centerX = radiusX;
        double centerY = radiusY;
        double tolerance = DEFAULT_STROKE_WIDTH + 2.0;

        // Расстояние от центра до точки в нормализованных координатах
        double dx = (localX - centerX) / radiusX;
        double dy = (localY - centerY) / radiusY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        // Проверяем, находится ли точка на контуре эллипса (с допуском)
        double toleranceNormalized = tolerance / Math.min(radiusX, radiusY);
        return Math.abs(distance - 1.0) <= toleranceNormalized;
    }

    private void applyStyle(Ellipse ellipse, Color fill, Color stroke, double strokeWidth) {
        ellipse.setFill(fill);
        ellipse.setStroke(stroke);
        ellipse.setStrokeWidth(strokeWidth);
        ellipse.setStrokeType(StrokeType.INSIDE);
    }
}