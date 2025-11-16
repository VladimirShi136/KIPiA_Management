package com.kipia.management.kipia_management.managers;

import com.kipia.management.kipia_management.services.ShapeService;
import com.kipia.management.kipia_management.shapes.*;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import java.util.List;
import java.util.function.Consumer;

/**
 * Менеджер для управления фигурами на панели схемы.
 * Отвечает за undo/redo, выделение и координацию работы с фигурами.
 *
 * @author vladimir_shi
 * @since 11.10.2025
 */
public class ShapeManager {

    /**
     * Enum инструментов редактора
     */
    public enum Tool {LINE, RECTANGLE, ELLIPSE, RHOMBUS, TEXT}

    // Основные зависимости
    private final AnchorPane pane;
    private ShapeService shapeService;
    private final CommandManager commandManager;
    // Состояние выделения и перемещения
    private ShapeHandler selectedShape;
    private boolean wasDraggedInSelect;
    private boolean wasResized;
    private Circle snapHighlight;

    // Callback для взаимодействия с UI
    private Consumer<ShapeHandler> onSelectCallback;
    private Consumer<String> statusSetter;

    // Поля для временных preview-фигур
    private Shape previewShape;
    private double startX, startY;

    private double previewEndX, previewEndY;

    private Runnable onShapeSelected;    // Callback при выделении фигуры
    private Runnable onShapeDeselected;  // Callback при снятии выделения

    // Команда добавления
    public class AddShapeCommand implements CommandManager.Command {
        private final Node shape;
        private final AnchorPane pane;

        public AddShapeCommand(AnchorPane pane, Node shape) {
            this.pane = pane;
            this.shape = shape;
        }

        @Override
        public void execute() {
            if (!pane.getChildren().contains(shape)) {
                pane.getChildren().add(shape);
            }
            shapeService.addShapeToList((ShapeBase) shape);
        }

        @Override
        public void undo() {
            pane.getChildren().remove(shape);
            shapeService.removeShapeFromList((ShapeBase) shape);
            if (shape instanceof ShapeBase base) {
                base.removeResizeHandles();
            }
        }
    }

    // Команда удаления
    public class RemoveShapeCommand implements CommandManager.Command {
        private final Node shape;
        private final AnchorPane pane;

        public RemoveShapeCommand(AnchorPane pane, Node shape) {
            this.pane = pane;
            this.shape = shape;
        }

        @Override
        public void execute() {
            pane.getChildren().remove(shape);
            shapeService.removeShapeFromList((ShapeBase) shape);
            if (shape instanceof ShapeBase base) {
                base.removeResizeHandles();
            }
        }

        @Override
        public void undo() {
            if (!pane.getChildren().contains(shape)) {
                pane.getChildren().add(shape);
            }
            shapeService.addShapeToList((ShapeBase) shape);
        }
    }

    // Команда для перемещения фигур
    public static class MoveShapeCommand implements CommandManager.Command {
        private final ShapeBase shape;
        private final double oldX, oldY, newX, newY;

        public MoveShapeCommand(ShapeBase shape, double oldX, double oldY, double newX, double newY) {
            this.shape = shape;
            this.oldX = oldX;
            this.oldY = oldY;
            this.newX = newX;
            this.newY = newY;
        }

        @Override
        public void execute() {
            shape.setPosition(newX, newY);
        }

        @Override
        public void undo() {
            shape.setPosition(oldX, oldY);
        }
    }

    // Команда для изменения цвета
    public static class ChangeColorCommand implements CommandManager.Command {
        private final ShapeBase shape;
        private final Color oldStroke, oldFill, newStroke, newFill;

        public ChangeColorCommand(ShapeBase shape, Color oldStroke, Color oldFill, Color newStroke, Color newFill) {
            this.shape = shape;
            this.oldStroke = oldStroke; this.oldFill = oldFill;
            this.newStroke = newStroke; this.newFill = newFill;
        }

        @Override
        public void execute() {
            shape.setColorsSilent(newStroke, newFill);
        }

        @Override
        public void undo() {
            shape.setColorsSilent(oldStroke, oldFill);
        }
    }

    // Команда для изменения размера
    public static class ResizeShapeCommand implements CommandManager.Command {
        private final ShapeBase shape;
        private final double oldX, oldY, oldWidth, oldHeight, newX, newY, newWidth, newHeight;

        public ResizeShapeCommand(ShapeBase shape, double oldX, double oldY, double oldWidth, double oldHeight,
                                  double newX, double newY, double newWidth, double newHeight) {
            this.shape = shape;
            this.oldX = oldX;
            this.oldY = oldY;
            this.oldWidth = oldWidth;
            this.oldHeight = oldHeight;
            this.newX = newX;
            this.newY = newY;
            this.newWidth = newWidth;
            this.newHeight = newHeight;
        }

        @Override
        public void execute() {
            shape.setPosition(newX, newY);
            shape.applyResize(newWidth, newHeight);
        }

        @Override
        public void undo() {
            shape.setPosition(oldX, oldY);
            shape.applyResize(oldWidth, oldHeight);
        }
    }

    // Команда для изменения шрифта текста
    public static class ChangeFontCommand implements CommandManager.Command {
        private final TextShape textShape;
        private final Font oldFont, newFont;

        public ChangeFontCommand(TextShape textShape, Font oldFont, Font newFont) {
            this.textShape = textShape;
            this.oldFont = oldFont;
            this.newFont = newFont;
        }

        @Override
        public void execute() {
            textShape.setFont(newFont);
            textShape.calculateTextSize();
        }

        @Override
        public void undo() {
            textShape.setFont(oldFont);
            textShape.calculateTextSize();
        }
    }

    // Команда для изменения конечных точек линии
    public static class ChangeLinePointsCommand implements CommandManager.Command {
        private final LineShape lineShape;
        private final double oldStartX, oldStartY, oldEndX, oldEndY;
        private final double newStartX, newStartY, newEndX, newEndY;

        public ChangeLinePointsCommand(LineShape lineShape,
                                       double oldStartX, double oldStartY, double oldEndX, double oldEndY,
                                       double newStartX, double newStartY, double newEndX, double newEndY) {
            this.lineShape = lineShape;
            this.oldStartX = oldStartX;
            this.oldStartY = oldStartY;
            this.oldEndX = oldEndX;
            this.oldEndY = oldEndY;
            this.newStartX = newStartX;
            this.newStartY = newStartY;
            this.newEndX = newEndX;
            this.newEndY = newEndY;
        }

        @Override
        public void execute() {
            lineShape.setLinePoints(newStartX, newStartY, newEndX, newEndY);
        }

        @Override
        public void undo() {
            lineShape.setLinePoints(oldStartX, oldStartY, oldEndX, oldEndY);
        }
    }

    // Команда для поворота фигур
    public static class RotateShapeCommand implements CommandManager.Command {
        private final ShapeBase shape;
        private final double oldAngle, newAngle;

        public RotateShapeCommand(ShapeBase shape, double oldAngle, double newAngle) {
            this.shape = shape;
            this.oldAngle = oldAngle;
            this.newAngle = newAngle;
        }

        @Override
        public void execute() {
            shape.setRotation(newAngle);
        }

        @Override
        public void undo() {
            shape.setRotation(oldAngle);
        }
    }

    /**
     * Конструктор менеджера фигур
     *
     * @param pane         панель для отображения фигур
     * @param shapeService сервис для создания фигур
     */
    public ShapeManager(AnchorPane pane, ShapeService shapeService,
                        Consumer<Boolean> onUndoStateChange, Consumer<Boolean> onRedoStateChange) {
        this.pane = pane;
        this.shapeService = shapeService;
        this.commandManager = new CommandManager(onUndoStateChange, onRedoStateChange);
    }

    // -----------------------------------------------------------------
    // PUBLIC API - УПРАВЛЕНИЕ ФИГУРАМИ
    // -----------------------------------------------------------------

    /**
     * Обработка нажатия мыши для указанного инструмента
     */
    public void onMousePressedForTool(Tool tool, double x, double y) {
        // Если инструмент null - этот метод не должен вызываться
        if (tool == null) {
            return;
        }

        clearPreview();
        setStartCoordinates(x, y);

        switch (tool) {
            case LINE, RECTANGLE, ELLIPSE, RHOMBUS -> createPreviewShape(tool, x, y);
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
        if (previewShape == null) return;

        // СОХРАНЯЕМ ПРОВЕРКИ МИНИМАЛЬНОГО РАЗМЕРА
        double minSize = 10.0;

        if (tool == Tool.LINE) {
            // Для линии проверяем общую длину
            double dx = Math.abs(x - startX);
            double dy = Math.abs(y - startY);
            double length = Math.sqrt(dx * dx + dy * dy);

            if (length >= minSize) {
                createFinalShape(tool, x, y);
                if (statusSetter != null) {
                    statusSetter.accept("Линия добавлена");
                }
            } else {
                if (statusSetter != null) {
                    statusSetter.accept("Линия слишком короткая");
                }
            }
        } else {
            // Для остальных фигур - стандартная проверка
            double width = Math.abs(x - startX);
            double height = Math.abs(y - startY);

            if (width >= minSize && height >= minSize) {
                createFinalShape(tool, x, y);
                if (statusSetter != null) {
                    statusSetter.accept("Фигура добавлена");
                }
            } else {
                if (statusSetter != null) {
                    statusSetter.accept("Фигура слишком маленькая");
                }
            }
        }

        clearPreview();
    }

    /**
     * Добавление фигуры с записью в undo-стек
     */
    public void addShape(Node shape) {
        AddShapeCommand cmd = new AddShapeCommand(pane, shape);
        commandManager.execute(cmd);  // Используем CommandManager вместо прямого управления
    }

    /**
     * Удаление фигуры с записью в undo-стек
     */
    public void removeShape(Node shape) {
        RemoveShapeCommand cmd = new RemoveShapeCommand(pane, shape);
        commandManager.execute(cmd);  // Используем CommandManager вместо прямого управления
    }

    /**
     * Регистрация поворота фигуры в undo-стек
     */
    public void registerRotation(ShapeBase shape, double oldAngle, double newAngle) {
        RotateShapeCommand cmd = new RotateShapeCommand(shape, oldAngle, newAngle);
        commandManager.execute(cmd);
    }

    // -----------------------------------------------------------------
    // UNDO/REDO MANAGEMENT
    // -----------------------------------------------------------------

    /**
     * Отмена последнего действия
     */
    public void undo() {
        commandManager.undo();
        updateSelectionAfterUndoRedo();
        // ДОБАВЬТЕ ЭТО:
        if (statusSetter != null) {
            statusSetter.accept("Отмена изменений");
        }
    }

    /**
     * Повтор последнего отмененного действия
     */
    public void redo() {
        commandManager.redo();
        updateSelectionAfterUndoRedo();
        // ДОБАВЬТЕ ЭТО:
        if (statusSetter != null) {
            statusSetter.accept("Повтор изменений");
        }
    }

    /**
     * Регистрация перемещения фигуры в undo-стек
     */
    public void registerMove(ShapeBase shape, double oldX, double oldY, double newX, double newY) {
        MoveShapeCommand cmd = new MoveShapeCommand(shape, oldX, oldY, newX, newY);
        commandManager.execute(cmd);
    }

    /**
     * Регистрация изменения цвета в undo-стек
     */
    public void registerColorChange(ShapeBase shape, Color oldStroke, Color oldFill, Color newStroke, Color newFill) {
        ChangeColorCommand cmd = new ChangeColorCommand(shape, oldStroke, oldFill, newStroke, newFill);
        commandManager.execute(cmd);
    }

    /**
     * Регистрация изменения размеров фигуры в undo-стек
     */
    public void registerResize(ShapeBase shape, double oldX, double oldY, double oldWidth, double oldHeight,
                               double newX, double newY, double newWidth, double newHeight) {
        ResizeShapeCommand cmd = new ResizeShapeCommand(shape, oldX, oldY, oldWidth, oldHeight, newX, newY, newWidth, newHeight);
        commandManager.execute(cmd);
    }

    /**
     * Регистрация изменения шрифта в undo-стек
     */
    public void registerFontChange(TextShape textShape, Font oldFont, Font newFont) {
        ChangeFontCommand cmd = new ChangeFontCommand(textShape, oldFont, newFont);
        commandManager.execute(cmd);
    }

    /**
     * Регистрация изменения конечных точек линии в undo-стек
     */
    public void registerLinePointsChange(LineShape lineShape,
                                         double oldStartX, double oldStartY, double oldEndX, double oldEndY,
                                         double newStartX, double newStartY, double newEndX, double newEndY) {
        ChangeLinePointsCommand cmd = new ChangeLinePointsCommand(lineShape,
                oldStartX, oldStartY, oldEndX, oldEndY,
                newStartX, newStartY, newEndX, newEndY);
        commandManager.execute(cmd);
    }

    /**
     * Очистка стеков undo/redo
     */
    public void clearUndoRedo() {
        commandManager.clear();
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

            if (onShapeSelected != null) {
                onShapeSelected.run();
            }
        }
    }

    /**
     * Снятие выделения с текущей фигуры
     */
    public void deselectShape() {
        if (selectedShape != null) {
            selectedShape.resetHighlight();

            if (selectedShape instanceof ShapeBase base) {
                base.removeResizeHandles();
            }

            if (onShapeDeselected != null) {
                onShapeDeselected.run();
            }
        }
        selectedShape = null;
        wasResized = false;
        hideSnapHighlight();
    }

    // -----------------------------------------------------------------
    // GETTERS & SETTERS
    // -----------------------------------------------------------------

    public void setOnShapeSelected(Runnable onShapeSelected) {
        this.onShapeSelected = onShapeSelected;
    }

    public void setOnShapeDeselected(Runnable onShapeDeselected) {
        this.onShapeDeselected = onShapeDeselected;
    }

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

    public void setShapeService(ShapeService shapeService) {
        this.shapeService = shapeService;
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

        // Проверяем привязку при создании
        Point2D snapPoint = findNearestSnapPointForPreview(x, y);
        if (snapPoint != null) {
            line.setEndX(snapPoint.getX());
            line.setEndY(snapPoint.getY());
            showSnapHighlight(snapPoint.getX(), snapPoint.getY());
        }

        return line;
    }

    private Point2D findNearestSnapPointForPreview(double x, double y) {
        if (shapeService == null) return null;

        Point2D bestSnap = null;
        double minDistance = 10.0; // SNAP_THRESHOLD

        try {
            // Получаем список фигур из ShapeService
            java.lang.reflect.Field shapesField = shapeService.getClass().getDeclaredField("shapes");
            shapesField.setAccessible(true);
            List<ShapeBase> allShapes = (List<ShapeBase>) shapesField.get(shapeService);

            for (ShapeBase shape : allShapes) {
                List<Point2D> snapPoints = shape.getSnapPoints();
                for (Point2D point : snapPoints) {
                    double distance = point.distance(x, y);
                    if (distance < minDistance) {
                        minDistance = distance;
                        bestSnap = point;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка поиска точек привязки: " + e.getMessage());
        }

        return bestSnap;
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
        path.setFill(Color.TRANSPARENT);
        path.setStroke(Color.GRAY);
        path.setStrokeWidth(1);

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

    private void updateRhombusPreview(Path path, double x, double y) {
        path.setLayoutX(Math.min(startX, x));
        path.setLayoutY(Math.min(startY, y));
        rebuildButterflyPath(path, x, y);
    }

    private void rebuildButterflyPath(Path rhombusPath, double endX, double endY) {
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);
        rhombusPath.getElements().clear();

        double centerX = width / 2;
        double centerY = height / 2;

        double leftTopY = 0;
        double rightTopY = 0;

        // Левый треугольник
        rhombusPath.getElements().addAll(
                new MoveTo(0, leftTopY),           // Левый верх (0)
                new LineTo(centerX, centerY),      // Центр
                new LineTo(0, height),        // Левый низ (height)
                new ClosePath()
        );

        // Правый треугольник
        rhombusPath.getElements().addAll(
                new MoveTo(width, rightTopY),      // Правый верх (0)
                new LineTo(centerX, centerY),      // Центр
                new LineTo(width, height),   // Правый низ (height)
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
            ShapeBase shape = shapeService.addShape(shapeType, coordinates);
            if (shape != null) {
                shape.addContextMenu(shapeHandler -> removeShape((Node) shapeHandler));
                addShape(shape);
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
     * Обновление состояния выделения после undo/redo
     */
    private void updateSelectionAfterUndoRedo() {
        deselectShape();  // Сброс выделения
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
     * Установка статуса через callback
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
            case RHOMBUS ->
                    new double[]{startX, startY, endX, endY};
            case LINE -> new double[]{startX, startY, endX, endY}; // Для линии - start и end точки
            case TEXT -> new double[]{startX, startY, 0};
        };
    }

    private ShapeType convertToolToShapeType(Tool tool) {
        return switch (tool) {
            case RECTANGLE -> ShapeType.RECTANGLE;
            case LINE -> ShapeType.LINE;
            case ELLIPSE -> ShapeType.ELLIPSE;
            case RHOMBUS -> ShapeType.RHOMBUS;
            case TEXT -> ShapeType.TEXT;
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