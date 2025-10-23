package com.kipia.management.kipia_management.shapes;

import javafx.scene.Node;
import javafx.geometry.Point2D;
import java.util.function.Consumer;

/**
 * Интерфейс для управления фигурами схемы.
 * Определяет общие методы для resize, highlight, handles и т.д.
 * Подклассы имплементируют этот интерфейс движется конкретными фигурами (Rectangle, Ellipse и т.д.);
 *
 * @author vladimir_shi
 * @since 21.10.2025
 */
public interface ShapeHandler {
    /**
     * Создаёт и добавляет resize-handles для фигуры.
     */
    void createResizeHandles();
    /**
     * Обновляет позиции resize-handles после перемещения или resize фигуры.
     */
    void updateResizeHandles();
    /**
     * Удаляет resize-handles из pane.
     */
    void removeResizeHandles();
    /**
     * Устанавливает позицию фигуры (перемещает без scale).
     */
    void setPosition(double x, double y);
    /**
     * Возвращает текущую позицию фигуры.
     */
    double[] getPosition();
    /**
     * Инициализирует состояние resize (сохраняет initial bounds и press-координаты).
     * Вызывается в onMousePressed для handle.
     * @param pressX local X при pressed.
     * @param pressY local Y при pressed.
     */
    void initResize(double pressX, double pressY);
    /**
     * Изменяет размер фигуры по индексу handle и delta (от pressed).
     * @param handleIndex Индекс ручки.
     * @param deltaX Дельта X.
     * @param deltaY Дельта Y.
     */
    void resizeByHandle(int handleIndex, double deltaX, double deltaY);
    /**
     * Возвращает offset от центра фигуры до мыши для корректного drag.
     * @param mouseX X мыши.
     * @param mouseY Y мыши.
     * @return Point2D с offset (dx, dy).
     */
    Point2D getCenterOffset(double mouseX, double mouseY);
    /**
     * Добавляет контекстное меню к фигуре (например, "Удалить").
     */
    void addContextMenu(Consumer<Node> deleteAction);
    /**
     * Подсвечивает фигуру как выделенную (красный stroke).
     */
    void highlightAsSelected();
    /**
     * Сбрасывает подсветку (чёрный stroke).
     */
    void resetHighlight();
    /**
     * Возвращает флаг: был ли реальный resize в сессии (delta >1px).
     * @return true, если изменён.
     */
    boolean wasResizedInSession();

    /**
     * Показывает resize-handles.
     */
    void makeResizeHandlesVisible();
}