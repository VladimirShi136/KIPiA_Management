package com.kipia.management.kipia_management.models;

import javafx.beans.property.*;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

/**
 * Состояние канваса (мир -> экран трансформация)
 * Полная копия Android версии
 *
 * @author vladimir_shi
 * @since 08.03.2026
 */
public class CanvasState {

    // Используем JavaFX Properties для автоматического обновления UI
    private final DoubleProperty scale = new SimpleDoubleProperty(1.0);
    private final DoubleProperty offsetX = new SimpleDoubleProperty(0.0);
    private final DoubleProperty offsetY = new SimpleDoubleProperty(0.0);

    // Размеры канваса в мировых координатах
    private final DoubleProperty width = new SimpleDoubleProperty(2000.0);
    private final DoubleProperty height = new SimpleDoubleProperty(1200.0);

    // Настройки отображения
    private final BooleanProperty showGrid = new SimpleBooleanProperty(true);
    private final IntegerProperty gridSize = new SimpleIntegerProperty(50);
    private final BooleanProperty dimOutsideBounds = new SimpleBooleanProperty(true);

    // ============================================================
    // Геттеры и сеттеры для свойств
    // ============================================================

    public double getScale() { return scale.get(); }
    public void setScale(double scale) { this.scale.set(scale); }
    public DoubleProperty scaleProperty() { return scale; }

    public double getOffsetX() { return offsetX.get(); }
    public void setOffsetX(double offsetX) { this.offsetX.set(offsetX); }
    public DoubleProperty offsetXProperty() { return offsetX; }

    public double getOffsetY() { return offsetY.get(); }
    public void setOffsetY(double offsetY) { this.offsetY.set(offsetY); }
    public DoubleProperty offsetYProperty() { return offsetY; }

    public Point2D getOffset() { return new Point2D(offsetX.get(), offsetY.get()); }
    public void setOffset(Point2D offset) {
        this.offsetX.set(offset.getX());
        this.offsetY.set(offset.getY());
    }

    public double getWidth() { return width.get(); }
    public void setWidth(double width) { this.width.set(width); }
    public DoubleProperty widthProperty() { return width; }

    public double getHeight() { return height.get(); }
    public void setHeight(double height) { this.height.set(height); }
    public DoubleProperty heightProperty() { return height; }

    public boolean isShowGrid() { return showGrid.get(); }
    public void setShowGrid(boolean showGrid) { this.showGrid.set(showGrid); }
    public BooleanProperty showGridProperty() { return showGrid; }

    public int getGridSize() { return gridSize.get(); }
    public void setGridSize(int gridSize) { this.gridSize.set(gridSize); }
    public IntegerProperty gridSizeProperty() { return gridSize; }

    public boolean isDimOutsideBounds() { return dimOutsideBounds.get(); }
    public void setDimOutsideBounds(boolean dimOutsideBounds) { this.dimOutsideBounds.set(dimOutsideBounds); }
    public BooleanProperty dimOutsideBoundsProperty() { return dimOutsideBounds; }

    // ============================================================
    // Методы трансформации координат
    // ============================================================

    /**
     * Конвертирует мировые координаты в экранные
     */
    public Point2D worldToScreen(double worldX, double worldY) {
        return new Point2D(
                worldX * scale.get() + offsetX.get(),
                worldY * scale.get() + offsetY.get()
        );
    }

    /**
     * Конвертирует экранные координаты в мировые
     */
    public Point2D screenToWorld(double screenX, double screenY) {
        return new Point2D(
                (screenX - offsetX.get()) / scale.get(),
                (screenY - offsetY.get()) / scale.get()
        );
    }

    /**
     * Проверяет, находится ли мировая точка в пределах канваса
     */
    public boolean isPointInCanvas(double worldX, double worldY) {
        return worldX >= 0 && worldX <= width.get() &&
                worldY >= 0 && worldY <= height.get();
    }

    /**
     * Получить границы канваса в экранных координатах
     */
    public Rectangle2D getCanvasScreenBounds() {
        Point2D topLeft = worldToScreen(0, 0);
        Point2D bottomRight = worldToScreen(width.get(), height.get());

        return new Rectangle2D(
                topLeft.getX(), topLeft.getY(),
                bottomRight.getX() - topLeft.getX(),
                bottomRight.getY() - topLeft.getY()
        );
    }

    @Override
    public String toString() {
        return String.format("CanvasState{scale=%.2f, offset=(%.1f,%.1f), size=%.0fx%.0f}",
                scale.get(), offsetX.get(), offsetY.get(), width.get(), height.get());
    }
}