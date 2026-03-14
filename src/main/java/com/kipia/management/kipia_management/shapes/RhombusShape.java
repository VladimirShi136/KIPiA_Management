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

    private static final double DEFAULT_STROKE_WIDTH = 2.0;
    private static final double SELECTED_STROKE_WIDTH = 3.0;

    private final Path rhombusPath;

    public RhombusShape(double x, double y, double width, double height, AnchorPane pane, Consumer<String> statusSetter,
                        Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        super(pane, statusSetter, onSelectCallback, shapeManager);

        setLayoutX(x);
        setLayoutY(y);

        rhombusPath = new Path();
        getChildren().add(rhombusPath);

        // ВАЖНО: Сохраняем размеры ДО создания фигуры
        rebuildButterflyPath(width, height);
        applyDefaultStyle();

        // Сохраняем размеры
        setCurrentDimensions(width, height);

        // Для отладки
        LOGGER.info("Rhombus created: x={}, y={}, width={}, height={}", x, y, width, height);
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

        setupResizeHandleHandlers();
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

        // Углы бабочки - точно по границам
        double leftTopY = 0;
        double rightTopY = 0;

        // HANDLES на видимых углах бабочки
        resizeHandles[0].setCenterX(getLayoutX() + 0);          // Левый верхний
        resizeHandles[0].setCenterY(getLayoutY() + leftTopY);

        resizeHandles[1].setCenterX(getLayoutX() + 0);          // Левый нижний
        resizeHandles[1].setCenterY(getLayoutY() + height);

        resizeHandles[2].setCenterX(getLayoutX() + width);      // Правый верхний
        resizeHandles[2].setCenterY(getLayoutY() + rightTopY);

        resizeHandles[3].setCenterX(getLayoutX() + width);      // Правый нижний
        resizeHandles[3].setCenterY(getLayoutY() + height);

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

        // Корректируем отрицательные размеры
        if (newWidth < 0) {
            newWidth = Math.abs(newWidth);
            newX -= newWidth;
        }
        if (newHeight < 0) {
            newHeight = Math.abs(newHeight);
            newY -= newHeight;
        }

        // СОХРАНЯЕМ минимальный размер
        newWidth = Math.max(40, newWidth);
        newHeight = Math.max(40, newHeight);

        // Применяем изменения
        setLayoutX(newX);
        setLayoutY(newY);
        resizeShape(newWidth, newHeight);

        updateResizeHandles();
        wasResizedInSession = true;
    }

    @Override
    protected void resizeShape(double width, double height) {
        // СОХРАНЯЕМ минимальный размер
        double actualWidth = Math.max(40, width);
        double actualHeight = Math.max(40, height);

        rebuildButterflyPath(actualWidth, actualHeight);
        setCurrentDimensions(actualWidth, actualHeight);

        // Для отладки
        LOGGER.debug("Resize Rhombus to: width={}, height={}", actualWidth, actualHeight);
    }

    /**
     * БАБОЧКА занимает ВСЮ высоту без отступов
     */
    private void rebuildButterflyPath(double width, double height) {
        rhombusPath.getElements().clear();

        double centerX = width / 2;
        double centerY = height / 2;

        // ВАЖНО: Убедимся, что path занимает ВЕСЬ bounding box
        // Левый треугольник - от левого верхнего угла до центра и левого нижнего
        rhombusPath.getElements().addAll(
                new MoveTo(0, 0),                    // Левый верхний угол (0,0)
                new LineTo(centerX, centerY),         // Центр
                new LineTo(0, height),                 // Левый нижний угол (0, height)
                new ClosePath()
        );

        // Правый треугольник - от правого верхнего угла до центра и правого нижнего
        rhombusPath.getElements().addAll(
                new MoveTo(width, 0),                  // Правый верхний угол (width, 0)
                new LineTo(centerX, centerY),           // Центр
                new LineTo(width, height),               // Правый нижний угол (width, height)
                new ClosePath()
        );

        // Для отладки
        LOGGER.debug("Rebuild butterfly: width={}, height={}, center=({},{})",
                width, height, centerX, centerY);
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
    public String getShapeType() {
        return "RHOMBUS";
    }

    private void applyStyle(Color fill, Color stroke, double strokeWidth) {
        rhombusPath.setFill(fill);
        rhombusPath.setStroke(stroke);
        rhombusPath.setStrokeWidth(strokeWidth);
        rhombusPath.setStrokeType(StrokeType.INSIDE);
    }

    @Override
    public void makeResizeHandlesVisible() {
        // НЕ вызываем super.makeResizeHandlesVisible()
        // Вместо этого создаем свои 4 handles если их нет
        if (resizeHandles == null) {
            createResizeHandles();
        } else {
            // Даже если handles уже есть, убедимся что обработчики настроены
            setupResizeHandleHandlers();
        }

        // Делаем handles видимыми
        for (Circle handle : resizeHandles) {
            if (handle != null) {
                handle.setVisible(true);
            }
        }
        updateResizeHandles();
    }

    /**
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

    public Path getPath() {
        return rhombusPath;
    }

    // ============================================================
    // OVERRIDE METHODS FOR CORRECT DIMENSIONS
    // ============================================================

    @Override
    public double getCurrentWidth() {
        return super.getCurrentWidth();
    }

    @Override
    public double getCurrentHeight() {
        return super.getCurrentHeight();
    }

    /**
     * Переопределяем serialize для гарантии правильных размеров
     */
    @Override
    public String serialize() {
        double[] pos = getPosition();
        // Используем сохраненные размеры
        double w = getCurrentWidth();
        double h = getCurrentHeight();

        return String.format(java.util.Locale.US, "%s|%.2f|%.2f|%.2f|%.2f|%.1f%s",
                getShapeType(), pos[0], pos[1], w, h, rotationAngle, serializeColors());
    }
}