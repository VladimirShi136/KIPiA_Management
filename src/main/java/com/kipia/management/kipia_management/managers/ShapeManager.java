package com.kipia.management.kipia_management.managers;

import com.kipia.management.kipia_management.shapes.*;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.*;
import javafx.scene.paint.Color;

import java.util.Arrays;
import java.util.Stack;
import java.util.function.Consumer;

/**
 * Менеджер для управления фигурами на панели схемы.
 * Отвечает за undo/redo, выделение и координацию работы с фигурами.
 *
 * @author vladimir_shi
 * @since 11.10.2025
 */
public class ShapeManager {

    /** Enum инструментов редактора */
    public enum Tool { SELECT, LINE, RECTANGLE, ELLIPSE, RHOMBUS, TEXT, ADD_DEVICE }

    // Основные зависимости
    private final AnchorPane pane;
    private ShapeService shapeService;

    // Стеки для undo/redo
    private final Stack<Command> undoStack = new Stack<>();
    private final Stack<Command> redoStack = new Stack<>();

    // Состояние выделения и перемещения
    private ShapeHandler selectedShape;
    private boolean wasDraggedInSelect;
    private boolean wasResized;

    // Колбэки для взаимодействия с UI
    private Consumer<ShapeHandler> onSelectCallback;
    private Consumer<String> statusSetter;

    // Поля для временных preview-фигур
    private Shape previewShape;
    private double startX, startY;
    private Circle snapHighlight;

    private double previewEndX, previewEndY;

    private boolean isSelectToolActive = false;

    private Runnable onShapeAdded;  // Callback для auto-save после добавления
    private Runnable onShapeRemoved;  // Callback для auto-save после удаления (опционально)

    /**
     * Интерфейс команды для паттерна Command
     */
    public interface Command {
        void execute();
        void undo();
    }

    // Команда добавления (execute: ничего, уже добавлено; undo: remove из pane + shapes)
    public class AddShapeCommand implements Command {
        private final Node shape;
        private final AnchorPane pane;
        public AddShapeCommand(AnchorPane pane, Node shape) {
            this.pane = pane;
            this.shape = shape;
        }
        @Override
        public void execute() {
            // Уже добавлено в shapeService.addShape, так что ничего (или add в pane если нужно)
            if (!pane.getChildren().contains(shape)) {
                pane.getChildren().add(shape);
            }
            shapeService.addShapeToList((ShapeBase) shape);
            System.out.println("DEBUG: Add execute - shape in pane, shapes count=" + shapeService.getShapeCount());
        }
        @Override
        public void undo() {
            pane.getChildren().remove(shape);
            shapeService.removeShapeFromList((ShapeBase) shape);  // Удаляем из списка
            if (shape instanceof ShapeBase base) {
                base.removeResizeHandles();  // Clear handles
            }
            System.out.println("DEBUG: Undo add - shape removed from pane/shapes, count=" + shapeService.getShapeCount());
        }
    }

    // Команда удаления (execute: remove из pane + shapes; undo: add в pane + shapes)
    public class RemoveShapeCommand implements Command {
        private final Node shape;
        private final AnchorPane pane;
        public RemoveShapeCommand(AnchorPane pane, Node shape) {
            this.pane = pane;
            this.shape = shape;
        }
        @Override
        public void execute() {
            pane.getChildren().remove(shape);
            shapeService.removeShapeFromList((ShapeBase) shape);  // Удаляем из списка
            if (shape instanceof ShapeBase base) {
                base.removeResizeHandles();  // Clear handles
            }
            System.out.println("DEBUG: Remove execute - shape removed from pane/shapes, count=" + shapeService.getShapeCount());
        }
        @Override
        public void undo() {
            if (!pane.getChildren().contains(shape)) {
                pane.getChildren().add(shape);
            }
            shapeService.addShapeToList((ShapeBase) shape);  // Добавляем в список
            // Не select автоматически, только возвращаем (handles создаст select если нужно)
            System.out.println("DEBUG: Undo remove - shape added back to pane/shapes, count=" + shapeService.getShapeCount());
        }
    }

    /**
     * Конструктор менеджера фигур
     *
     * @param pane панель для отображения фигур
     * @param shapeService сервис для создания фигур
     */
    public ShapeManager(AnchorPane pane, ShapeService shapeService) {
        this.pane = pane;
        this.shapeService = shapeService;
    }

    // -----------------------------------------------------------------
    // PUBLIC API - УПРАВЛЕНИЕ ФИГУРАМИ
    // -----------------------------------------------------------------

    /**
     * Обработка нажатия мыши для указанного инструмента
     */
    public void onMousePressedForTool(Tool tool, double x, double y) {
        clearPreview();
        setStartCoordinates(x, y);

        switch (tool) {
            case SELECT -> handleSelectOnPress(x, y);
            case LINE, RECTANGLE, ELLIPSE, RHOMBUS -> createPreviewShape(tool, x, y);
            // ADD_DEVICE и TEXT обрабатываются в контроллере
        }
    }

    /**
     * Обработка перемещения мыши для указанного инструмента
     */
    public void onMouseDraggedForTool(Tool tool, double x, double y) {
        if (previewShape != null) {
            updatePreviewShape(tool, x, y);
        }
    }

    /**
     * Обработка отпускания мыши для указанного инструмента
     */
    public void onMouseReleasedForTool(Tool tool, double x, double y) {
        if (tool == Tool.SELECT) return;
        if (previewShape == null) return;

        createFinalShape(tool, x, y);
        clearPreview();
    }

    /**
     * Добавление фигуры с записью в undo-стек
     */
    public void addShape(Node shape) {
        // shape уже добавлена в shapes/pane через shapeService.addShape (в createFinalShape или контроллере)
        AddShapeCommand cmd = new AddShapeCommand(pane, shape);
        cmd.execute();  // На всякий: убедиться в pane
        undoStack.push(cmd);
        redoStack.clear();  // Стандартно: стираем redo при новом действии
        if (onShapeAdded != null) {
            onShapeAdded.run();
        }
        System.out.println("DEBUG: AddShape pushed to undoStack, count=" + shapeService.getShapeCount());
    }

    /**
     * Удаление фигуры с записью в undo-стек
     */
    public void removeShape(Node shape) {
        System.out.println("DEBUG: removeShape called, before count=" + shapeService.getShapeCount());
        RemoveShapeCommand cmd = new RemoveShapeCommand(pane, shape);
        cmd.execute();  // Удаляем сразу
        undoStack.push(cmd);
        redoStack.clear();
        if (onShapeRemoved != null) {
            onShapeRemoved.run();
        }
        System.out.println("DEBUG: removeShape pushed to undoStack, after count=" + shapeService.getShapeCount());
    }

    // -----------------------------------------------------------------
    // UNDO/REDO MANAGEMENT
    // -----------------------------------------------------------------

    /**
     * Отмена последнего действия
     */
    public void undo() {
        if (!undoStack.isEmpty()) {
            Command cmd = undoStack.pop();
            cmd.undo();  // Отменяем последнее действие

            // ВЫЗОВ КОЛБЭКА ДЛЯ АВТОСОХРАНЕНИЯ
            if (cmd instanceof AddShapeCommand && onShapeRemoved != null) {
                onShapeRemoved.run();
            } else if (cmd instanceof RemoveShapeCommand && onShapeAdded != null) {
                onShapeAdded.run();
            }

            redoStack.push(cmd);  // Сохраняем для redo
            updateSelectionAfterUndoRedo();
            System.out.println("DEBUG: Undo executed, undoStack size=" + undoStack.size() + ", redoStack size=" + redoStack.size() + ", shapes count=" + shapeService.getShapeCount());
        } else {
            System.out.println("DEBUG: Undo - stack empty");
        }
    }

    /**
     * Повтор последнего отмененного действия
     */
    public void redo() {
        if (!redoStack.isEmpty()) {
            Command cmd = redoStack.pop();
            cmd.execute();  // Повторяем отменённое действие

            // ВЫЗОВ КОЛБЭКА ДЛЯ АВТОСОХРАНЕНИЯ
            if (cmd instanceof AddShapeCommand && onShapeAdded != null) {
                onShapeAdded.run();
            } else if (cmd instanceof RemoveShapeCommand && onShapeRemoved != null) {
                onShapeRemoved.run();
            }

            undoStack.push(cmd);  // Сохраняем для повторного undo
            updateSelectionAfterUndoRedo();
            System.out.println("DEBUG: Redo executed, undoStack size=" + undoStack.size() + ", redoStack size=" + redoStack.size() + ", shapes count=" + shapeService.getShapeCount());
        } else {
            System.out.println("DEBUG: Redo - stack empty");
        }
    }

    /**
     * Очистка стеков undo/redo
     */
    public void clearUndoRedo() {
        undoStack.clear();
        redoStack.clear();
    }

    // -----------------------------------------------------------------
    // SELECTION MANAGEMENT
    // -----------------------------------------------------------------

    /**
     * Выделение указанной фигуры
     */
    public void selectShape(ShapeHandler shapeHandler) {
        if (selectedShape == shapeHandler) {
            selectedShape.makeResizeHandlesVisible();
            selectedShape.updateResizeHandles();
            return;
        }

        deselectShape();
        selectedShape = shapeHandler;

        if (selectedShape != null) {
            selectedShape.highlightAsSelected();
            selectedShape.makeResizeHandlesVisible();
            selectedShape.updateResizeHandles();

            if (onSelectCallback != null) {
                onSelectCallback.accept(selectedShape);
            }
        }
    }

    /**
     * Снятие выделения с текущей фигуры
     */
    public void deselectShape() {
        if (selectedShape != null) {
            selectedShape.resetHighlight();  // Уже есть: стиль + hide handles

            // Новое: Полный сброс handles для selectedShape (удаление из pane)
            if (selectedShape instanceof ShapeBase base) {  // Cast к ShapeBase для доступа
                base.removeResizeHandles();  // Вызов public метода — удалит handles полностью
            }
        }
        selectedShape = null;
        wasResized = false;
        hideSnapHighlight();
    }

    // -----------------------------------------------------------------
    // GETTERS & SETTERS
    // -----------------------------------------------------------------

    public ShapeHandler getSelectedShape() {
        return selectedShape;
    }

    public boolean wasResized() {
        return wasResized;
    }

    public void resetWasResized() {
        wasResized = false;
    }

    public boolean wasDraggedInSelect() {
        return wasDraggedInSelect;
    }

    public void setStatusSetter(Consumer<String> statusSetter) {
        this.statusSetter = statusSetter;
    }

    public void setOnSelectCallback(Consumer<ShapeHandler> onSelectCallback) {
        this.onSelectCallback = onSelectCallback;
    }

    public boolean isSelectToolActive() {
        return isSelectToolActive;
    }

    public void setSelectToolActive(boolean active) {
        this.isSelectToolActive = active;
    }

    public void setShapeService(ShapeService shapeService) {
        this.shapeService = shapeService;
    }

    // Setter для добавления фигур
    public void setOnShapeAdded(Runnable callback) {
        this.onShapeAdded = callback;
    }

    // Setter для удаления фигур (если нужно)
    public void setOnShapeRemoved(Runnable callback) {
        this.onShapeRemoved = callback;
    }

    public AnchorPane getPane() {
        return pane;
    }
    // -----------------------------------------------------------------
    // PRIVATE METHODS - PREVIEW SHAPES
    // -----------------------------------------------------------------

    /**
     * Установка начальных координат для создания фигуры
     */
    private void setStartCoordinates(double x, double y) {
        startX = x;
        startY = y;
    }

    /**
     * Создание preview-фигуры для указанного инструмента
     */
    private void createPreviewShape(Tool tool, double x, double y) {
        previewShape = createPreviewShapeByTool(tool, x, y);
        if (previewShape != null) {
            pane.getChildren().add(previewShape);
        }
    }

    /**
     * Создание конкретной preview-фигуры по типу инструмента
     */
    private Shape createPreviewShapeByTool(Tool tool, double x, double y) {
        return switch (tool) {
            case LINE -> createLinePreview(x, y);
            case RECTANGLE -> createRectanglePreview(x, y);
            case ELLIPSE -> createEllipsePreview(x, y);
            case RHOMBUS -> createRhombusPreview(x, y);
            default -> null;
        };
    }

    private Line createLinePreview(double x, double y) {
        Line line = new Line(startX, startY, x, y);
        line.setStroke(Color.GRAY);
        line.setStrokeWidth(1);
        return line;
    }

    private Rectangle createRectanglePreview(double x, double y) {
        Rectangle rect = new Rectangle(
                Math.min(startX, x),
                Math.min(startY, y),
                Math.abs(x - startX),
                Math.abs(y - startY)
        );
        rect.setFill(Color.TRANSPARENT);
        rect.setStroke(Color.GRAY);
        rect.setStrokeWidth(1);
        return rect;
    }

    private Ellipse createEllipsePreview(double x, double y) {
        double centerX = (startX + x) / 2;
        double centerY = (startY + y) / 2;
        double radiusX = Math.abs(x - startX) / 2;
        double radiusY = Math.abs(y - startY) / 2;

        Ellipse ellipse = new Ellipse(centerX, centerY, radiusX, radiusY);
        ellipse.setFill(Color.TRANSPARENT);
        ellipse.setStroke(Color.GRAY);
        ellipse.setStrokeWidth(1);
        return ellipse;
    }

    private Path createRhombusPreview(double x, double y) {
        Path path = new Path();
        path.setFill(Color.TRANSPARENT); // Делаем preview тоже прозрачным
        path.setStroke(Color.GRAY);
        path.setStrokeWidth(1);

        // ВАЖНО: устанавливаем позицию preview относительно начальных координат
        path.setLayoutX(Math.min(startX, x));
        path.setLayoutY(Math.min(startY, y));

        rebuildButterflyPath(path, x, y);
        return path;
    }

    /**
     * Обновление preview-фигуры при перемещении мыши
     */
    private void updatePreviewShape(Tool tool, double x, double y) {
        switch (tool) {
            case LINE -> updateLinePreview((Line) previewShape, x, y);
            case RECTANGLE -> updateRectanglePreview((Rectangle) previewShape, x, y);
            case ELLIPSE -> updateEllipsePreview((Ellipse) previewShape, x, y);
            case RHOMBUS -> updateRhombusPreview((Path) previewShape, x, y);
        }
    }

    private void updateLinePreview(Line line, double x, double y) {
        double startX = this.startX;
        double startY = this.startY;

        // Убираем принудительную фиксацию - оставляем свободное рисование
        double endX = x;
        double endY = y;

        // "Умная" фиксация только при приближении
        double deltaX = Math.abs(endX - startX);
        double deltaY = Math.abs(endY - startY);
        double snapThreshold = 15.0;

        // Проверяем близость к осям
        boolean nearHorizontal = deltaY < snapThreshold && deltaX > snapThreshold;
        boolean nearVertical = deltaX < snapThreshold && deltaY > snapThreshold;

        if (nearHorizontal) {
            endY = startY; // Фиксируем горизонталь
        } else if (nearVertical) {
            endX = startX; // Фиксируем вертикаль
        }

        this.previewEndX = endX;
        this.previewEndY = endY;
        line.setEndX(endX);
        line.setEndY(endY);

        // Показываем индикатор только при фиксации
        if (nearHorizontal || nearVertical) {
            showSnapHighlight(endX, endY);
        } else {
            hideSnapHighlight();
        }
    }


    private void updateRectanglePreview(Rectangle rect, double x, double y) {
        rect.setX(Math.min(startX, x));
        rect.setY(Math.min(startY, y));
        rect.setWidth(Math.abs(x - startX));
        rect.setHeight(Math.abs(y - startY));
    }

    private void updateEllipsePreview(Ellipse ellipse, double x, double y) {
        ellipse.setCenterX((startX + x) / 2);
        ellipse.setCenterY((startY + y) / 2);
        ellipse.setRadiusX(Math.abs(x - startX) / 2);
        ellipse.setRadiusY(Math.abs(y - startY) / 2);
    }

    // Замени updateRhombusPreview на:
    private void updateRhombusPreview(Path path, double x, double y) {
        // Обновляем позицию preview
        path.setLayoutX(Math.min(startX, x));
        path.setLayoutY(Math.min(startY, y));
        rebuildButterflyPath(path, x, y);
    }

    // Добавь общий метод (из RhombusShape)
    private void rebuildButterflyPath(Path rhombusPath, double endX, double endY) {
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);
        rhombusPath.getElements().clear();

        double centerX = width / 2;
        double centerY = height / 2;

        // ТОЧНО ТАКАЯ ЖЕ ЛОГИКА КАК В RHOMBUSSHAPE
        double leftTopY = 0;
        double leftBottomY = height;
        double rightTopY = 0;
        double rightBottomY = height;

        // Левый треугольник
        rhombusPath.getElements().addAll(
                new MoveTo(0, leftTopY),           // Левый верх (0)
                new LineTo(centerX, centerY),      // Центр
                new LineTo(0, leftBottomY),        // Левый низ (height)
                new ClosePath()
        );

        // Правый треугольник
        rhombusPath.getElements().addAll(
                new MoveTo(width, rightTopY),      // Правый верх (0)
                new LineTo(centerX, centerY),      // Центр
                new LineTo(width, rightBottomY),   // Правый низ (height)
                new ClosePath()
        );
    }

    /**
     * Создание финальной фигуры при отпускании мыши
     */
    private void createFinalShape(Tool tool, double x, double y) {
        ShapeType shapeType = convertToolToShapeType(tool);
        if (shapeType == null) return;

        double[] coordinates = calculateFinalCoordinates(shapeType, x, y);
        if (tool == Tool.LINE) {
            coordinates = calculateFinalCoordinates(ShapeType.LINE, previewEndX, previewEndY);
        }

        try {
            System.out.println("DEBUG: Creating shape: " + tool + " at (" + x + ", " + y + "), coords: " + Arrays.toString(coordinates));
            ShapeBase shape = shapeService.addShape(shapeType, coordinates);
            if (shape != null) {
                addShape(shape);

                // НЕ выделяем фигуру автоматически после создания!
                // shape.highlightAsSelected(); // УБРАТЬ эту строку если есть

                setStatus("Фигура добавлена");
            }
        } catch (Exception e) {
            setStatus("Ошибка создания фигуры");
            System.err.println("ERROR creating shape: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // PRIVATE UTILITY METHODS
    // -----------------------------------------------------------------

    /**
     * Обработка выбора фигуры при нажатии (fallback для не-ShapeHandler фигур)
     */
    private void handleSelectOnPress(double x, double y) {
        deselectShape();
    }

    /**
     * Обновление состояния выделения после undo/redo
     */
    private void updateSelectionAfterUndoRedo() {
        deselectShape();  // Сброс выделения (не мешает возврату фигур)
        // Опционально: если нужно восстановить выделение последней фигуры, добавь логику поиска
        System.out.println("DEBUG: Selection updated after undo/redo");
    }

    /**
     * Очистка preview-фигуры
     */
    private void clearPreview() {
        if (previewShape != null) {
            pane.getChildren().remove(previewShape);
            previewShape = null;
        }
        hideSnapHighlight();
    }

    /**
     * Установка статуса через колбэк
     */
    private void setStatus(String message) {
        if (statusSetter != null) {
            statusSetter.accept(message);
        }
    }

    // -----------------------------------------------------------------
    // COORDINATE CALCULATION METHODS
    // -----------------------------------------------------------------

    private double[] calculateFinalCoordinates(ShapeType type, double endX, double endY) {
        return switch (type) {
            case RECTANGLE, ELLIPSE -> {
                double x = Math.min(startX, endX);
                double y = Math.min(startY, endY);
                double width = Math.abs(endX - startX);
                double height = Math.abs(endY - startY);
                yield new double[]{x, y, width, height};
            }
            case RHOMBUS -> new double[]{startX, startY, endX, endY};  // Новое: Точные start/end для бабочки (не min/width)
            case LINE -> new double[]{startX, startY, endX, endY};
            case TEXT -> new double[]{startX, startY, 0};
            default -> new double[]{};
        };
    }

    private ShapeType convertToolToShapeType(Tool tool) {
        return switch (tool) {
            case RECTANGLE -> ShapeType.RECTANGLE;
            case LINE -> ShapeType.LINE;
            case ELLIPSE -> ShapeType.ELLIPSE;
            case RHOMBUS -> ShapeType.RHOMBUS;
            case TEXT -> ShapeType.TEXT;
            default -> null;
        };
    }

    // -----------------------------------------------------------------
    // SNAP HIGHLIGHT METHODS
    // -----------------------------------------------------------------

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