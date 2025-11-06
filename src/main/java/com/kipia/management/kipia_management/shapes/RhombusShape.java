package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import javafx.geometry.Point2D;
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

    public RhombusShape(double x, double y, double width, double height, AnchorPane pane, Consumer<String> statusSetter,
                        Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        super(pane, statusSetter, onSelectCallback, shapeManager);

        setLayoutX(x);
        setLayoutY(y);

        rhombusPath = new Path();
        getChildren().add(rhombusPath);

        // ИСПОЛЬЗУЕМ ТОЧНО ТУ ЖЕ ЛОГИКУ ЧТО И В PREVIEW
        rebuildButterflyPath(width, height);
        applyDefaultStyle();
        setCurrentDimensions(width, height);
    }

    /**
     * СОЗДАЕМ ТОЛЬКО 4 HANDLES вместо 8
     */
    @Override
    public void createResizeHandles() {
        if (resizeHandles != null) return;

        resizeHandles = new Circle[4]; // Только 4 handles
        for (int i = 0; i < 4; i++) {
            resizeHandles[i] = createResizeHandle();
            pane.getChildren().add(resizeHandles[i]);
        }
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

    /**
     * ПЕРЕОПРЕДЕЛЯЕМ метод для точного расчета смещения при drag
     */
    @Override
    public Point2D getCenterOffset(double mouseX, double mouseY) {
        // Используем реальные размеры для точного позиционирования
        double centerX = getLayoutX() + getCurrentWidth() / 2;
        double centerY = getLayoutY() + getCurrentHeight() / 2;

        return new Point2D(mouseX - centerX, mouseY - centerY);
    }

    /**
     * ОБНОВЛЯЕМ только 4 handles на углах треугольников
     */
    @Override
    public void updateResizeHandles() {
        if (resizeHandles == null) return;

        double width = getCurrentWidth();
        double height = getCurrentHeight();

        // ВЫЧИСЛЯЕМ реальные координаты углов бабочки
        double centerX = width / 2;
        double centerY = height / 2;

        // Углы бабочки - точно по границам
        double leftTopY = 0;
        double leftBottomY = height;
        double rightTopY = 0;
        double rightBottomY = height;

        // HANDLES на видимых углах бабочки
        resizeHandles[0].setCenterX(getLayoutX() + 0);          // Левый верхний
        resizeHandles[0].setCenterY(getLayoutY() + leftTopY);

        resizeHandles[1].setCenterX(getLayoutX() + 0);          // Левый нижний
        resizeHandles[1].setCenterY(getLayoutY() + leftBottomY);

        resizeHandles[2].setCenterX(getLayoutX() + width);      // Правый верхний
        resizeHandles[2].setCenterY(getLayoutY() + rightTopY);

        resizeHandles[3].setCenterX(getLayoutX() + width);      // Правый нижний
        resizeHandles[3].setCenterY(getLayoutY() + rightBottomY);

        for (Circle handle : resizeHandles) {
            if (handle != null) {
                handle.setVisible(true);
            }
        }
    }


    /**
     * ЛОГИКА РЕСАЙЗА для 4 handles
     */
    @Override
    public void resizeByHandle(int handleIndex, double deltaX, double deltaY) {
        double newX = initialX;
        double newY = initialY;
        double newWidth = initialWidth;
        double newHeight = initialHeight;

        switch (handleIndex) {
            case 0: // Левый верхний
                newX += deltaX;
                newY += deltaY;
                newWidth -= deltaX;
                newHeight -= deltaY;
                break;
            case 1: // Левый нижний
                newX += deltaX;
                newWidth -= deltaX;
                newHeight += deltaY;
                break;
            case 2: // Правый верхний
                newY += deltaY;
                newWidth += deltaX;
                newHeight -= deltaY;
                break;
            case 3: // Правый нижний
                newWidth += deltaX;
                newHeight += deltaY;
                break;
        }

        // КОРРЕКЦИЯ отрицательных размеров
        if (newWidth < 0) {
            newWidth = Math.abs(newWidth);
            newX -= newWidth;
        }
        if (newHeight < 0) {
            newHeight = Math.abs(newHeight);
            newY -= newHeight;
        }

        // ПРИМЕНЕНИЕ с ограничениями для КРАСНОГО КВАДРАТА
        newWidth = Math.max(40, newWidth);
        newHeight = Math.max(40, newHeight);

        // ВАЖНО: ограничиваем чтобы фигура точно доходила до границ
        double maxX = 1600 - newWidth;
        double maxY = 1200 - newHeight;
        newX = Math.max(0, Math.min(newX, maxX));
        newY = Math.max(0, Math.min(newY, maxY));

        // ПРИМЕНЯЕМ изменения
        setLayoutX(newX);
        setLayoutY(newY);
        resizeShape(newWidth, newHeight);

        // ВАЖНО: обновляем handles СРАЗУ после ресайза
        updateResizeHandles();

        wasResizedInSession = true;
    }

    @Override
    protected void resizeShape(double width, double height) {
        double actualWidth = Math.max(40, width);
        double actualHeight = Math.max(40, height);

        rebuildButterflyPath(actualWidth, actualHeight);
        setCurrentDimensions(actualWidth, actualHeight);
    }

    /**
     * БАБОЧКА занимает ВСЮ высоту без отступов
     */
    private void rebuildButterflyPath(double width, double height) {
        rhombusPath.getElements().clear();

        double centerX = width / 2;
        double centerY = height / 2;

        // ТОЧНО ТАК ЖЕ КАК В SHAPEMANAGER
        double leftTopY = 0;
        double leftBottomY = height;
        double rightTopY = 0;
        double rightBottomY = height;

        // Левый треугольник - от верха до низа
        rhombusPath.getElements().addAll(
                new MoveTo(0, leftTopY),           // Левый верх (0)
                new LineTo(centerX, centerY),      // Центр
                new LineTo(0, leftBottomY),        // Левый низ (height)
                new ClosePath()
        );

        // Правый треугольник - от верха до низа
        rhombusPath.getElements().addAll(
                new MoveTo(width, rightTopY),      // Правый верх (0)
                new LineTo(centerX, centerY),      // Центр
                new LineTo(width, rightBottomY),   // Правый низ (height)
                new ClosePath()
        );
    }


    @Override
    protected void applyCurrentStyle() {
        applyStyle(fillColor, strokeColor, DEFAULT_STROKE_WIDTH);
    }

    @Override
    protected void applySelectedStyle() {
        applyStyle(fillColor, Color.BLUE, SELECTED_STROKE_WIDTH);
    }

    @Override
    protected void applyDefaultStyle() {
        applyCurrentStyle();
    }

    @Override
    protected String getShapeType() {
        return "RHOMBUS";
    }

    private void applyStyle(Color fill, Color stroke, double strokeWidth) {
        rhombusPath.setFill(fill);
        rhombusPath.setStroke(stroke);
        rhombusPath.setStrokeWidth(strokeWidth);
        rhombusPath.setStrokeType(StrokeType.INSIDE);
    }

    /**ц
     * УДАЛЯЕМ ТОЛЬКО 4 HANDLES
     */
    @Override
    public void removeResizeHandles() {
        if (resizeHandles == null) return;
        for (Circle handle : resizeHandles) {
            if (handle != null) {
                handle.setOnMousePressed(null);
                handle.setOnMouseDragged(null);
                handle.setOnMouseReleased(null);
                pane.getChildren().remove(handle);
            }
        }
        resizeHandles = null;
        wasResizedInSession = false;
    }

    /**
     * ДЕБАГ метод для проверки границ
     */
    public void debugBounds() {
        System.out.println("Rhombus debug - " +
                "Layout: (" + getLayoutX() + "," + getLayoutY() + ") " +
                "Size: " + getCurrentWidth() + "x" + getCurrentHeight() + " " +
                "LocalBounds: " + getBoundsInLocal() + " " +
                "ParentBounds: " + getBoundsInParent());
    }

//    @Override
//    public String serialize() {
//        double[] pos = getPosition();
//        double w = getCurrentWidth();
//        double h = getCurrentHeight();
//        return String.format(java.util.Locale.US, "%s|%.2f|%.2f|%.2f|%.2f%s",
//                getShapeType(), pos[0], pos[1], w, h, serializeColors());
//    }
}