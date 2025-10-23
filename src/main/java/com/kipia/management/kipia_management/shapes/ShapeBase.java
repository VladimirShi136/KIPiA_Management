package com.kipia.management.kipia_management.shapes;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.Cursor;
import javafx.geometry.Point2D;
import java.util.function.Consumer;

/**
 * Абстрактный базовый класс для всех интерактивных фигур схемы.
 * Унифицирует логику drag, resize, handles, undo/redo.
 * Подклассы реализуют drawShape, resizeShape, updateHandles.
 *
 * @author vladimir_shi
 * @since 22.10.2025
 */
public abstract class ShapeBase extends Group implements ShapeHandler {
    protected AnchorPane pane;  // Pane для добавления фигуры и handles
    protected Circle[] resizeHandles;  // Массив handles (8 штук)
    protected Consumer statusSetter;  // Callback для статуса
    protected Consumer onSelectCallback;  // Callback для выбора фигуры
    protected double pressPaneX, pressPaneY;  // Для resize (в координатах pane)
    protected double initialX, initialY, initialW, initialH;  // Initial bounds для resize (layoutX/layoutY, width/height)
    protected boolean wasResizedInSession;  // Флаг resize
    protected double dragOffsetX, dragOffsetY;  // Для smooth drag (смещение мыши относительно левого верхнего угла фигуры)
    protected boolean isDragging;  // Флаг drag сессии


    public ShapeBase(AnchorPane pane, Consumer statusSetter, Consumer onSelectCallback) {
        this.pane = pane;
        this.statusSetter = statusSetter;
        this.onSelectCallback = onSelectCallback;
        this.resizeHandles = null;
        this.wasResizedInSession = false;
        this.isDragging = false;
        createResizeHandles();  // Создаём handles сразу (они невидимы до select)
        addDragListeners();  // Общая логика drag
    }


    // Абстрактные методы для подклассов (реализуют рисование и resize)
    protected abstract void drawShape(double startX, double startY, double endX, double endY);
    protected abstract void resizeShape(double width, double height);
    protected abstract void updateHandles();


    // Общая логика resize handles
    @Override
    public void createResizeHandles() {
        if (resizeHandles != null) return;
        resizeHandles = new Circle[8];
        for (int i = 0; i < 8; i++) {
            Circle handle = new Circle(0, 0, 6, Color.CORAL);
            handle.setCursor(Cursor.CROSSHAIR);
            handle.setVisible(false);  // Невидимы до select
            addHandleListeners(handle, i);
            resizeHandles[i] = handle;
            // handles добавляем в pane (общий родитель), чтобы они отображались поверх
            pane.getChildren().add(handle);
        }
        // Позиции будут установлены updateResizeHandles()
    }


    private void addHandleListeners(Circle handle, int index) {
        handle.setOnMousePressed(e -> {
            Point2D pressPane = pane.sceneToLocal(e.getSceneX(), e.getSceneY());
            initResize(pressPane.getX(), pressPane.getY());
            e.consume();
        });
        handle.setOnMouseDragged(e -> {
            Point2D currPane = pane.sceneToLocal(e.getSceneX(), e.getSceneY());
            double deltaX = currPane.getX() - pressPaneX;
            double deltaY = currPane.getY() - pressPaneY;
            if (Math.abs(deltaX) < 1 && Math.abs(deltaY) < 1) return;
            resizeByHandle(index, deltaX, deltaY);
            updateHandles();
            e.consume();
        });
        handle.setOnMouseReleased(e -> {
            updateHandles();
            if (wasResizedInSession && statusSetter != null) {
                statusSetter.accept("Размер фигуры изменен");
            }
            e.consume();
        });
    }


    // Общая логика drag (используем layoutX/layoutY)
    private void addDragListeners() {
        setOnMousePressed(event -> {
            // Координата мыши в координатах pane (родителя)
            Point2D mousePos = pane.sceneToLocal(event.getSceneX(), event.getSceneY());
            // Смещение между мышью и текущим левым верхним углом Shape (layoutX/layoutY)
            dragOffsetX = mousePos.getX() - getLayoutX();
            dragOffsetY = mousePos.getY() - getLayoutY();


            // Логирование для диагностики
            System.out.println("MousePressed: Mouse at (" + mousePos.getX() + ", " + mousePos.getY() + ")");
            System.out.println("Shape layout before select: layoutX=" + getLayoutX() + ", layoutY=" + getLayoutY());
            System.out.println("Calculated drag offset: (" + dragOffsetX + ", " + dragOffsetY + ")");
// Теперь вызываем selectShape (через callback у менеджера)
            if (onSelectCallback != null) {
                onSelectCallback.accept(this);
            }

            isDragging = false;
            event.consume();
        });

        setOnMouseDragged(event -> {
            isDragging = true;
            Point2D panePos = pane.sceneToLocal(event.getSceneX(), event.getSceneY());
            double newX = panePos.getX() - dragOffsetX;
            double newY = panePos.getY() - dragOffsetY;
// Логирование
            System.out.println("MouseDragged: Pane pos (" + panePos.getX() + ", " + panePos.getY() + "), new pos (" + newX + ", " + newY + ")");

// Limits: не уходим за края pane (используем width/height фигуры)
            double bw = getBoundsInLocal().getWidth();
            double bh = getBoundsInLocal().getHeight();
            newX = Math.max(0, Math.min(newX, pane.getWidth() - bw));
            newY = Math.max(0, Math.min(newY, pane.getHeight() - bh));

            setPosition(newX, newY);
            updateHandles();
            event.consume();
        });
        setOnMouseReleased(event -> {
            if (isDragging) {
                if (statusSetter != null) {
                    statusSetter.accept("Позиция фигуры изменена");
                }
            }
            isDragging = false;
            updateHandles();
            event.consume();
        });

    }


    // Реализация ShapeHandler методов (общие для всех)
    @Override
    public void updateResizeHandles() {
        if (resizeHandles == null) return;
        // Позиция левого верхнего угла Shape в координатах pane:
        double x0 = getLayoutX() + getBoundsInLocal().getMinX();
        double y0 = getLayoutY() + getBoundsInLocal().getMinY();
        double w = getBoundsInLocal().getWidth();
        double h = getBoundsInLocal().getHeight();
        double[] xs = {x0, x0 + w / 2, x0 + w};
        double[] ys = {y0, y0 + h / 2, y0 + h};
        int index = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i == 1 && j == 1) continue;
                if (index < resizeHandles.length && resizeHandles[index] != null) {
                    resizeHandles[index].setCenterX(xs[i]);
                    resizeHandles[index].setCenterY(ys[j]);
                }
                index++;
            }
        }
    }


    @Override
    public void removeResizeHandles() {
        if (resizeHandles != null) {
            for (Circle handle : resizeHandles) {
                if (handle != null) {
                    pane.getChildren().remove(handle);
                }
            }
            resizeHandles = null;
        }
        wasResizedInSession = false;
    }


    @Override
    public void setPosition(double x, double y) {
        setLayoutX(x);
        setLayoutY(y);
    }


    @Override
    public double[] getPosition() {
        return new double[]{getLayoutX(), getLayoutY()};
    }


    @Override
    public Point2D getCenterOffset(double mouseX, double mouseY) {
        double centerX = getLayoutX() + getBoundsInLocal().getMinX() + getBoundsInLocal().getWidth() / 2;
        double centerY = getLayoutY() + getBoundsInLocal().getMinY() + getBoundsInLocal().getHeight() / 2;
        double offsetX = mouseX - centerX;
        double offsetY = mouseY - centerY;


// Логирование
        System.out.println("getCenterOffset: Shape center (" + centerX + ", " + centerY + "), mouse (" + mouseX + ", " + mouseY + "), offset (" + offsetX + ", " + offsetY + ")");

        return new Point2D(offsetX, offsetY);

    }


    @Override
    public void initResize(double pressX, double pressY) {
        this.pressPaneX = pressX;
        this.pressPaneY = pressY;
        this.initialX = getLayoutX();
        this.initialY = getLayoutY();
        this.initialW = getBoundsInLocal().getWidth();
        this.initialH = getBoundsInLocal().getHeight();
        this.wasResizedInSession = false;
    }


    @Override
    public void resizeByHandle(int handleIndex, double deltaX, double deltaY) {
        double newX = initialX, newY = initialY, newWidth = initialW, newHeight = initialH;
        switch (handleIndex) {
            case 0: newX += deltaX; newY += deltaY; newWidth -= deltaX; newHeight -= deltaY; break;
            case 1: newX += deltaX; newWidth -= deltaX; break;
            case 2: newX += deltaX; newWidth -= deltaX; newHeight += deltaY; break;
            case 3: newY += deltaY; newHeight -= deltaY; break;
            case 4: newHeight += deltaY; break;
            case 5: newY += deltaY; newWidth += deltaX; newHeight -= deltaY; break;
            case 6: newWidth += deltaX; break;
            case 7: newWidth += deltaX; newHeight += deltaY; break;
        }
        boolean realChange = (Math.abs(deltaX) > 1 || Math.abs(deltaY) > 1) &&
                (newWidth != initialW || newHeight != initialH || newX != initialX || newY != initialY);
        wasResizedInSession = realChange;


// Обработка отрицательных размеров — приводим к положительным и корректируем позицию
        if (newWidth < 0) { newWidth = Math.abs(newWidth); newX -= newWidth; }
        if (newHeight < 0) { newHeight = Math.abs(newHeight); newY -= newHeight; }

        double minSize = 20.0;
        newWidth = Math.max(minSize, newWidth);
        newHeight = Math.max(minSize, newHeight);

// Перемещаем Shape (layoutX/layoutY) и меняем размеры через абстрактный метод resizeShape
        setLayoutX(newX);
        setLayoutY(newY);
        resizeShape(newWidth, newHeight);

    }


    @Override
    public void addContextMenu(Consumer deleteAction) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.setOnAction(ev -> deleteAction.accept(this));
        contextMenu.getItems().add(deleteItem);
        setOnContextMenuRequested(e -> {
            contextMenu.show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }


    @Override
    public void highlightAsSelected() {
        // Подклассы реализуют (например, setStroke для Rectangle)
    }


    @Override
    public void resetHighlight() {
        // Подклассы реализуют
    }


    @Override
    public boolean wasResizedInSession() {
        return wasResizedInSession;
    }


    // Методы для undo/redo (интеграция с ShapeManager)
    public void addToPane() {
        // Добавляем сам shape (Group) в pane
        if (!pane.getChildren().contains(this)) {
            pane.getChildren().add(this);
        }
        createResizeHandles();
        updateHandles();
    }


    public void removeFromPane() {
        pane.getChildren().remove(this);
        removeResizeHandles();
    }


    @Override
    public void makeResizeHandlesVisible() {
        if (resizeHandles != null) {
            for (Circle handle : resizeHandles) {
                if (handle != null) {
                    handle.setVisible(true);
                }
            }
            updateResizeHandles();
        }
    }
}