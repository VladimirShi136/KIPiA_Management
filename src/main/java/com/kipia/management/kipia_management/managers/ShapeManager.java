package com.kipia.management.kipia_management.managers;

import com.kipia.management.kipia_management.shapes.ShapeBase;
import com.kipia.management.kipia_management.shapes.ShapeHandler;
import com.kipia.management.kipia_management.shapes.RectangleShape;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.*;
import javafx.scene.paint.Color;
import javafx.geometry.Point2D;

import java.util.Arrays;
import java.util.Stack;
import java.util.function.Consumer;

/**
 * Класс-менеджер для управления фигурами на панели схемы
 *
 * @author vladimir_shi
 * @since 11.10.2025
 */
public class ShapeManager {
    // Tool enum: определён здесь для независимости
    public enum Tool {SELECT, LINE, RECTANGLE, ELLIPSE, RHOMBUS, ADD_DEVICE}

    // Поля для snap-позиционирования
    private static final double SNAP_THRESHOLD = 10;
    private static final double SNAP_EDGE_THRESHOLD = 20.0;
    private boolean snappedHorizontal = false;
    private boolean snappedVertical = false;
    private Circle snapHighlight;  // Кружок для подсветки snap точки
    // Ссылка на панель схемы
    private final AnchorPane pane;
    // Поля для фигур и состояния
    private static final double THICKNESS_THRESHOLD = 5.0;  // Ширина области клика вокруг линии (в пикселях)
    private final Stack<Command> undoStack = new Stack<>();
    private final Stack<Command> redoStack = new Stack<>();
    private ShapeHandler selectedShape;
    private boolean isDraggingSelected;
    private boolean wasDraggedInSelect;
    private Consumer<ShapeHandler> onSelectCallback;
    private Consumer<String> statusSetter;  // Callback для установки статуса без ссылок на контроллер
    private boolean wasResized;  // Флаг: было ли изменение размера в сессии (для контроллера)
    public boolean isResizing;
    // Preview для рисования
    private Line previewLine;
    private Rectangle previewRect;
    private Ellipse previewEllipse;
    private Path previewRhombus;  // Превью ромба
    private double startX, startY;  // Для временного хранения координат


    // Интерфейс команд
    public interface Command {
        void execute();

        void undo();
    }

    // Команда добавления фигуры
    public record AddShapeCommand(AnchorPane pane, Node shape) implements Command {
        @Override
        public void execute() {
            if (!pane.getChildren().contains(shape)) {
                pane.getChildren().add(shape);
            }
        }

        @Override
        public void undo() {
            pane.getChildren().remove(shape);
        }
    }

    // Команда удаления (обновлена для Node)
    public record RemoveShapeCommand(AnchorPane pane, Node shape) implements Command {
        @Override
        public void execute() {
            pane.getChildren().remove(shape);
        }

        @Override
        public void undo() {
            pane.getChildren().add(shape);
        }
    }

    // Конструктор: принимает pane
    public ShapeManager(AnchorPane pane) {
        this.pane = pane;
        clearPreview();
    }

    // Очистка preview
    public void clearPreview() {
        if (previewLine != null) {
            pane.getChildren().remove(previewLine);
            previewLine = null;
        }
        if (previewRect != null) {
            pane.getChildren().remove(previewRect);
            previewRect = null;
        }
        if (previewEllipse != null) {
            pane.getChildren().remove(previewEllipse);
            previewEllipse = null;
        }
        if (previewRhombus != null) {
            pane.getChildren().remove(previewRhombus);
            previewRhombus = null;
        }
        hideSnapHighlight();
    }

    // Добавление/удаление фигур с командами
    public void addShape(Node shape) {
        AddShapeCommand cmd = new AddShapeCommand(pane, shape);
        cmd.execute();
        undoStack.push(cmd);
        redoStack.clear();
    }

    public void removeShape(Node shape) {
        RemoveShapeCommand cmd = new RemoveShapeCommand(pane, shape);
        cmd.execute();
        undoStack.push(cmd);
        redoStack.clear();
    }

    // Undo/Redo/Clear
    public void undo() {
        if (!undoStack.isEmpty()) {
            Command cmd = undoStack.pop();
            cmd.undo();
            redoStack.push(cmd);
        }
        updateResizeHandles();  // Обновляем handles после undo
        deselectShape();  // Для простоты — всегда deselect, чтобы избежать несоответствий
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            Command cmd = redoStack.pop();
            cmd.execute();
            undoStack.push(cmd);
        }
        updateResizeHandles();  // Аналогично для redo
        deselectShape();  // Для простоты
    }

    public void clearUndoRedo() {
        undoStack.clear();
        redoStack.clear();
    }

    // Геттеры для контроллера
    public ShapeHandler getSelectedShape() {
        return selectedShape;
    }

    // Геттер для поля wasResized (для статуса в контроллере)
    public boolean wasResized() {
        return wasResized;
    }

    // Сброс флага wasResized
    public void resetWasResized() {
        wasResized = false;
    }

    // Геттер для wasDraggedInSelect
    public boolean wasDraggedInSelect() {
        return wasDraggedInSelect;
    }

    public void setStatusSetter(Consumer<String> statusSetter) {
        this.statusSetter = statusSetter;
    }

    // Setter for onSelectCallback
    public void setOnSelectCallback(Consumer<ShapeHandler> onSelectCallback) {
        this.onSelectCallback = onSelectCallback;
    }

    // Добавь публичный updateResizeHandles (если не было)
    public void updateResizeHandles() {
        if (selectedShape != null) {
            selectedShape.updateResizeHandles();
        }
    }

    // Расчет расстояния до линии
    private double lineDistance(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            return Math.hypot(px - x1, py - y1);
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        double closestX = x1 + t * dx;
        double closestY = y1 + t * dy;
        return Math.hypot(px - closestX, py - closestY);
    }

    // Выделение/сброс
    public void selectShape(ShapeHandler shapeHandler) {
        if (selectedShape == shapeHandler) {
            // Уже выбрана, просто обновляем handles
            shapeHandler.updateResizeHandles();
            return;
        }

        // Логирование позиции перед изменениями
        System.out.println("selectShape: Before deselect, selectedShape position: " + (selectedShape != null ? Arrays.toString(selectedShape.getPosition()) : "null"));

        // Сбрасываем предыдущую
        if (selectedShape != null) {
            selectedShape.resetHighlight();
            selectedShape.removeResizeHandles();
        }

        // Устанавливаем новую
        selectedShape = shapeHandler;

        // Логирование после установки
        System.out.println("selectShape: After set selectedShape, position: " + Arrays.toString(selectedShape.getPosition()));

        // Подсвечиваем и показываем handles
        selectedShape.highlightAsSelected();
        selectedShape.makeResizeHandlesVisible();
        selectedShape.updateResizeHandles();

        // Логирование после highlight и handles
        System.out.println("selectShape: After highlight and handles, position: " + Arrays.toString(selectedShape.getPosition()));
    }


    public void deselectShape() {
        if (selectedShape != null) {
            selectedShape.resetHighlight();
            selectedShape.removeResizeHandles();
        }
        wasResized = false;  // Сброс флага
        selectedShape = null;
        isDraggingSelected = false;
        isResizing = false;
        hideSnapHighlight();
        System.out.println("Deselected: full cleanup");
    }

    /**
     * Вспомогательный метод: перестраивает Path для ромба/бабочки из двух треугольников на основе заданных координат.
     */
    private static void rebuildButterflyPath(Path path, double leftBaseX, double leftTopY, double centerX, double centerY, double leftBottomY, double rightBaseX) {
        path.getElements().clear();
        // Левый треугольник
        path.getElements().addAll(
                new MoveTo(leftBaseX, leftTopY),
                new LineTo(centerX, centerY),
                new LineTo(leftBaseX, leftBottomY),
                new ClosePath()
        );
        // Правый треугольник (rightTopY == leftTopY, rightBottomY == leftBottomY)
        path.getElements().addAll(
                new MoveTo(rightBaseX, leftTopY),
                new LineTo(centerX, centerY),
                new LineTo(rightBaseX, leftBottomY),
                new ClosePath()
        );
    }

    /**
     * Вспомогательный метод: рассчитывает координаты для ромба/бабочки.
     * Возвращает массив: {leftBaseX, leftTopY, centerX, centerY, leftBottomY, rightBaseX}.
     */
    private static double[] calculateButterflyCoordinates(double x, double y, double startX, double startY) {
        double totalWidth = Math.abs(x - startX) * 2;
        double mouseHeight = Math.abs(y - startY);
        double side = totalWidth / 2;
        double triangleHeight = (Math.sqrt(3) / 2) * side;
        double finalHeight = Math.min(mouseHeight, triangleHeight * 2);
        double leftBaseX = startX - side;
        double centerTopY = startY - finalHeight / 2;
        double centerBottomY = startY + finalHeight / 2;
        double calculate = (finalHeight / 2) * (1 - Math.sqrt(3) / 3);
        double leftTopY = centerTopY + calculate;
        double leftBottomY = centerBottomY - calculate;
        double rightBaseX = startX + side;
        // Возвращаем массив в порядке: leftBaseX, leftTopY, centerX, centerY, leftBottomY, rightBaseX
        return new double[]{leftBaseX, leftTopY, startX, startY, leftBottomY, rightBaseX};
    }

    // Методы для обработки событий мыши (адаптированные из контроллера)
    public void setStartCoordinates(double x, double y) {
        startX = x;
        startY = y;
    }

    /**
     * Метод для обработки начала инструмента (pressed).
     * Завершает preview для других инструментов, для SELECT — fallback выбор.
     */
    public void onMousePressedForTool(Tool tool, double x, double y) {
        setStartCoordinates(x, y);
        System.out.println("Press for tool=" + tool + ": startX=" + startX + " startY=" + startY);
        clearPreview();  // Убирает старые preview
        hideSnapHighlight();
        if (tool == Tool.SELECT) {
            handleSelectOnPress(x, y);
        } else if (tool == Tool.LINE) {
            // Создание preview для линии
            previewLine = new Line(startX, startY, startX, startY);
            previewLine.setStroke(Color.GRAY);
            previewLine.setStrokeWidth(1);
            pane.getChildren().add(previewLine);
        } else if (tool == Tool.RECTANGLE) {
            // Создание preview для прямоугольника
            previewRect = new Rectangle(startX, startY, 0, 0);
            previewRect.setFill(Color.TRANSPARENT);
            previewRect.setStroke(Color.GRAY);
            previewRect.setStrokeWidth(1);
            pane.getChildren().add(previewRect);
        } else if (tool == Tool.ELLIPSE) {
            // Создание preview для эллипса
            previewEllipse = new Ellipse(startX, startY, 0, 0);
            previewEllipse.setFill(Color.TRANSPARENT);
            previewEllipse.setStroke(Color.GRAY);
            previewEllipse.setStrokeWidth(1);
            pane.getChildren().add(previewEllipse);
        } else if (tool == Tool.RHOMBUS) {
            // Создание preview для ромба (как Path)
            previewRhombus = new Path();
            previewRhombus.setFill(Color.TRANSPARENT);
            previewRhombus.setStroke(Color.GRAY);
            previewRhombus.setStrokeWidth(1);
            pane.getChildren().add(previewRhombus);
        }
    }

    /**
     * Вспомогательный метод: обработка выбора при pressed (упрощён — теперь фигура сама уведомляет через callback).
     * Используется fallback для случаев, когда фигура не является ShapeHandler.
     */
    private void handleSelectOnPress(double x, double y) {
        System.out.println("handleSelectOnPress: coords(" + x + "," + y + ") — fallback для non-ShapeHandler");
        deselectShape();
    }

    /**
     * Метод для обработки перетаскивания (dragged).
     * Preview обновляется для создаваемых фигур, drag фигуры теперь в самой фигуре (unified).
     */
    public void onMouseDraggedForTool(Tool tool, double x, double y) {
        if (tool == Tool.LINE && previewLine != null) {
            double endX = x, endY = y;
            snappedHorizontal = false;
            snappedVertical = false;
            if (Math.abs(endX - startX) < SNAP_THRESHOLD) {
                endX = startX;
                snappedHorizontal = true;
            }
            if (Math.abs(endY - startY) < SNAP_THRESHOLD) {
                endY = startY;
                snappedVertical = true;
            }
            double[] snappedEnd = findNearestEdgeSnap(endX, endY);
            endX = snappedEnd[0];
            endY = snappedEnd[1];
            previewLine.setEndX(endX);
            previewLine.setEndY(endY);
            if (!(endX == x && endY == y)) {
                showSnapHighlight(endX, endY);
            } else {
                hideSnapHighlight();
            }
        } else if (tool == Tool.RECTANGLE && previewRect != null) {
            double w = Math.abs(x - startX), h = Math.abs(y - startY);
            System.out.println("Drag for RECTANGLE: w=" + w + " h=" + h);  // Лог для проверки размеров во время drag
            previewRect.setX(Math.min(startX, x));
            previewRect.setY(Math.min(startY, y));
            previewRect.setWidth(w);
            previewRect.setHeight(h);
        } else if (tool == Tool.ELLIPSE && previewEllipse != null) {
            double rx = Math.abs(x - startX) / 2;
            double ry = Math.abs(y - startY) / 2;
            previewEllipse.setRadiusX(rx);
            previewEllipse.setRadiusY(ry);
            previewEllipse.setCenterX((startX + x) / 2);
            previewEllipse.setCenterY((startY + y) / 2);
        } else if (tool == Tool.RHOMBUS && previewRhombus != null) {
            double[] coords = calculateButterflyCoordinates(x, y, startX, startY);
            rebuildButterflyPath(previewRhombus, coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
        }
    }

    public void onMouseReleasedForTool(Tool tool, double x, double y) {
        System.out.println("onMouseReleasedForTool: tool=" + tool + " coords(" + x + "," + y + ")");

        // Убрана логика для SELECT — drag и resize unified в фигуре (release там обрабатывается)
        // Оставлена только установка статуса для SELECT (если нужно, но флаг wasResized не используется здесь)
        if (tool == Tool.SELECT) {
            // Minimal: установ_status, но без drag-check (фигура сама sends status)
            if (statusSetter != null) {
                // Например, стаб устанавливается в фигуре on release
            }
            return;  // Ничего больше для SELECT
        }

        // Для создания фигур из preview
        Node finalShape;
        if (tool == Tool.RECTANGLE && previewRect != null) {
            double rx = previewRect.getX(), ry = previewRect.getY(), rw = previewRect.getWidth(), rh = previewRect.getHeight();
            System.out.println("Release for RECTANGLE: startX=" + startX + " startY=" + startY + " releaseX=" + x + " releaseY=" + y + " rw=" + rw + " rh=" + rh);

            clearPreview();  // <-- Удаляем preview ДО создания финальной фигуры

            if (rw > 0 && rh > 0) {
                System.out.println("Adding RectangleShape: rx=" + rx + " ry=" + ry + " rw=" + rw + " rh=" + rh);
                RectangleShape rectShape = new RectangleShape(rx, ry, rw, rh, pane, statusSetter, onSelectCallback);  // Все 7 аргументов!
                addShape(rectShape);
                statusSetter.accept("Прямоугольник добавлен");
            } else {
                System.out.println("Skipping RectangleShape: rw=" + rw + " rh=" + rh + " (must be >0 for both)");
                statusSetter.accept("drag слишком короткий — попробуй потянуть дальше");
            }
        } else if (tool == Tool.LINE && previewLine != null) {
            double finalEndX = x, finalEndY = y;
            if (snappedHorizontal) finalEndX = startX;
            if (snappedVertical) finalEndY = startY;
            double[] snappedFinalEnd = findNearestEdgeSnap(finalEndX, finalEndY);
            finalEndX = snappedFinalEnd[0];
            finalEndY = snappedFinalEnd[1];
            finalShape = new Line(startX, startY, finalEndX, finalEndY);
            ((Line) finalShape).setStroke(Color.BLACK);
            addShape(finalShape);
            hideSnapHighlight();
        } else if (tool == Tool.ELLIPSE && previewEllipse != null) {
            double minR = 10.0;
            double rx = Math.max(minR, previewEllipse.getRadiusX());
            double ry = Math.max(minR, previewEllipse.getRadiusY());
            finalShape = new Ellipse(previewEllipse.getCenterX(), previewEllipse.getCenterY(), rx, ry);
            ((Ellipse) finalShape).setFill(Color.TRANSPARENT);
            ((Ellipse) finalShape).setStroke(Color.BLACK);
            addShape(finalShape);
        } else if (tool == Tool.RHOMBUS && previewRhombus != null) {
            double[] coords = calculateButterflyCoordinates(x, y, startX, startY);
            Path butterfly = new Path();
            butterfly.setFill(Color.TRANSPARENT);
            butterfly.setStroke(Color.BLACK);
            butterfly.setStrokeWidth(1);
            rebuildButterflyPath(butterfly, coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
            finalShape = butterfly;
            addShape(finalShape);
            hideSnapHighlight();
        }

        clearPreview();
        isResizing = false;
        if (statusSetter != null) {
            statusSetter.accept("Фигура добавлена");
        }
    }

    private double[] findNearestEdgeSnap(double x, double y) {
        double[] closest = {x, y};
        double minDist = Double.MAX_VALUE;
        for (Node node : pane.getChildren()) {
            if (node instanceof Line line && node != previewLine && node != selectedShape) {  // Проверяем только линии, исключаем превью
                double distToStart = Math.hypot(x - line.getStartX(), y - line.getStartY());  // Расстояние до start
                double distToEnd = Math.hypot(x - line.getEndX(), y - line.getEndY());       // Расстояние до enda
                double minDistForLine = Math.min(distToStart, distToEnd);  // Ближайшая граница этой линии
                if (minDistForLine < minDist) {
                    minDist = minDistForLine;
                    if (distToStart <= distToEnd) {
                        closest[0] = line.getStartX();
                        closest[1] = line.getStartY();
                    } else {
                        closest[0] = line.getEndX();
                        closest[1] = line.getEndY();
                    }
                }
            }
        }
        if (minDist < SNAP_EDGE_THRESHOLD) {
            return closest;  // Прилипаем
        } else {
            return new double[]{x, y};  // Не прилипаем
        }
    }

    private void showSnapHighlight(double x, double y) {
        if (snapHighlight == null) {
            snapHighlight = new Circle(x, y, 5, Color.RED);
            pane.getChildren().add(snapHighlight);
        } else {
            snapHighlight.setCenterX(x);
            snapHighlight.setCenterY(y);
        }
    }

    private void hideSnapHighlight() {
        if (snapHighlight != null) {
            pane.getChildren().remove(snapHighlight);
            snapHighlight = null;
        }
    }
}

