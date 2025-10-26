package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import java.util.function.Consumer;

/**
 * Класс ромбовидной фигуры (бабочка) с поддержкой выделения, изменения размера и перемещения
 */
public class RhombusShape extends ShapeBase {
    private static final Color DEFAULT_FILL = Color.TRANSPARENT;
    private static final Color DEFAULT_STROKE = Color.BLACK;
    private static final double DEFAULT_STROKE_WIDTH = 2.0;
    private static final Color SELECTED_FILL = Color.TRANSPARENT;
    private static final Color SELECTED_STROKE = Color.BLUE;
    private static final double SELECTED_STROKE_WIDTH = 3.0;

    private final Path rhombusPath;

    // В конструкторе RhombusShape(double x, double y, double width, double height, ...)
    public RhombusShape(double x, double y, double width, double height, AnchorPane pane, Consumer<String> statusSetter,
                        Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        super(pane, statusSetter, onSelectCallback, shapeManager);
        setLayoutX(x);  // Позиция группы = left-top rect
        setLayoutY(y);

        // Создаём Path
        rhombusPath = new Path();
        rhombusPath.setFill(Color.LIGHTBLUE);  // Или твой цвет
        rhombusPath.setStroke(Color.BLACK);
        rhombusPath.setStrokeWidth(2);
        getChildren().add(rhombusPath);

        // Новое: Rebuild сразу с width/height (относительно (0,0) группы)
        rebuildButterflyPath(width, height);  // Передаём width/height напрямую (см. ниже)

        applyDefaultStyle();
        setCurrentDimensions(width, height);
    }

    @Override
    protected void resizeShape(double width, double height) {
        if (rhombusPath != null) {
            rebuildButterflyPath(width, height);  // Твоя логика (с finalHeight = min)
        }
        // Новое: Set stored exact (используем newWidth, newHeight — не finalHeight, чтобы match preview/load)
        // Если хочешь сохранить "обрезку" — setCurrentDimensions(newWidth, finalHeight); Но для consistency — full newHeight
        setCurrentDimensions(width, height);  // Full, как input
    }

    @Override
    protected void applySelectedStyle() {
        applyStyle(rhombusPath, SELECTED_FILL, SELECTED_STROKE, SELECTED_STROKE_WIDTH);
    }

    @Override
    protected void applyDefaultStyle() {
        applyStyle(rhombusPath, DEFAULT_FILL, DEFAULT_STROKE, DEFAULT_STROKE_WIDTH);
    }

    @Override
    protected String getShapeType() {
        return "RHOMBUS";
    }

    /**
     * Перестроение пути бабочки по новым размерам
     */
    private void rebuildButterflyPath(double width, double height) {
        rhombusPath.getElements().clear();

        // Логика как в preview/ShapeManager (унифицирована)
        double side = width / 2;
        double triangleHeight = (Math.sqrt(3) / 2) * side;
        double finalHeight = Math.min(height, triangleHeight * 2);  // Обрезка как в preview
        double centerX = width / 2;  // Относительно группы (0,0) — centerX = width/2
        double centerY = finalHeight / 2;  // finalHeight, не height (фикс размера!)
        double leftBaseX = 0;  // Левый край = 0 в группе
        double centerTopY = centerY - finalHeight / 2;
        double centerBottomY = centerY + finalHeight / 2;
        double calculate = (finalHeight / 2) * (1 - Math.sqrt(3) / 3);
        double leftTopY = centerTopY + calculate;
        double leftBottomY = centerBottomY - calculate;
        double rightBaseX = width;  // Правый край = width

        // Левый треугольник (points от 0,0)
        rhombusPath.getElements().addAll(
                new MoveTo(leftBaseX, leftTopY),
                new LineTo(centerX, centerY),
                new LineTo(leftBaseX, leftBottomY),
                new ClosePath()
        );

        // Правый треугольник
        rhombusPath.getElements().addAll(
                new MoveTo(rightBaseX, leftTopY),
                new LineTo(centerX, centerY),
                new LineTo(rightBaseX, leftBottomY),
                new ClosePath()
        );

        // Обновляем bounds после rebuild (для serialize)
        requestLayout();  // JavaFX обновит bounds
    }

    private void applyStyle(Path path, Color fill, Color stroke, double strokeWidth) {
        path.setFill(fill);
        path.setStroke(stroke);
        path.setStrokeWidth(strokeWidth);
        path.setStrokeType(StrokeType.INSIDE);
    }
}