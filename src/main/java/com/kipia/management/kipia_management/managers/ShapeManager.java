package com.kipia.management.kipia_management.managers;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.scene.paint.Color;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.geometry.Point2D;
import java.util.Stack;

/**
 * Класс-менеджер для управления фигурами на панели схемы
 *
 * @author vladimir_shi
 * @since 11.10.2025
 */
public class ShapeManager {
    // Tool enum: определён здесь для независимости
    public enum Tool {SELECT, LINE, RECTANGLE, ELLIPSE, RHOMBUS, TEXT, ADD_DEVICE}
    // Поля для snap-позиционирования
    private static final double SNAP_THRESHOLD = 10;
    private static final double SNAP_EDGE_THRESHOLD = 20.0;
    private boolean snappedHorizontal = false;
    private boolean snappedVertical = false;
    private Circle snapHighlight;  // Кружок для подсветки snap точки
    // Ссылка на панель схемы
    private final AnchorPane pane;
    // Поля для фигур и состояния
    private static final double THICKNESS_THRESHOLD = 10.0;  // Ширина области клика вокруг линии (в пикселях)
    private final Stack<Command> undoStack = new Stack<>();
    private final Stack<Command> redoStack = new Stack<>();
    private Node selectedNode;
    private boolean isDraggingSelected;
    private Circle[] resizeHandles;
    private boolean isResizing;
    private int resizeCorner;
    private double dragOffsetX, dragOffsetY;
    private double pressX, pressY;  // Для расчёта дельты при resize, чтобы избежать ульёта
    private double initialX, initialY, initialW, initialH;  // Начальные bounds при pressed для delta-resize
    // Preview для рисования
    private Line previewLine;
    private Rectangle previewRect;
    private Ellipse previewEllipse;
    private Path previewRhombus;  // Превью ромба
    private Text previewText;
    private boolean addingText = false;  // Для TEXT инструмента
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
            pane.getChildren().add(shape);
        }

        @Override
        public void undo() {
            pane.getChildren().remove(shape);
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
        if (previewText != null) {
            pane.getChildren().remove(previewText);
            previewText = null;
        }
        if (previewRhombus != null) {
            pane.getChildren().remove(previewRhombus);
            previewRhombus = null;
        }
        hideSnapHighlight();  // НОВОЕ: спрятать подсветку
    }

    // Добавление/удаление фигур с командами (адаптировано из контроллера)
    public void addShape(Node shape) {
        undoStack.push(new AddShapeCommand(pane, shape));
        redoStack.clear();
        pane.getChildren().add(shape);
    }

    public void removeShape(Node shape) {
        Command deleteCmd = new Command() {
            @Override
            public void execute() {
                pane.getChildren().remove(shape);
            }

            @Override
            public void undo() {
                pane.getChildren().add(shape);
            }
        };
        deleteCmd.execute();
        undoStack.push(deleteCmd);
        redoStack.clear();
    }

    // Undo/Redo/Clear
    public void undo() {
        if (!undoStack.isEmpty()) {
            Command cmd = undoStack.pop();
            cmd.undo();
            redoStack.push(cmd);
        }
        deselectShape();  // После undo сбросить выделение
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            Command cmd = redoStack.pop();
            cmd.execute();
            undoStack.push(cmd);
        }
    }

    public void clearUndoRedo() {
        undoStack.clear();
        redoStack.clear();
    }

    // Геттеры для контроллера
    public Node getSelectedNode() {
        return selectedNode;
    }

    // расчет расстояния до линии
    private double lineDistance(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            return Math.hypot(px - x1, py - y1);  // Если линия - точка
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));  // Ограничиваем 0 до 1 для проекции на отрезок
        double closestX = x1 + t * dx;
        double closestY = y1 + t * dy;
        return Math.hypot(px - closestX, py - closestY);  // Расстояние до ближайшей точки на отрезке
    }

    // Выделение/сброс
    public void selectShape(Node shape) {
        deselectShape();
        selectedNode = shape;
        shape.setOnContextMenuRequested(e -> {
            ContextMenu contextMenu = new ContextMenu();
            MenuItem deleteItem = new MenuItem("Удалить");
            deleteItem.setOnAction(ev -> removeShape(shape));  // Удаляем через менеджер
            contextMenu.getItems().add(deleteItem);
            contextMenu.show(shape, e.getScreenX(), e.getScreenY());
        });
        if (shape instanceof Rectangle || shape instanceof Ellipse || shape instanceof Line) {
            addResizeHandles(shape);
        }
        if (shape instanceof javafx.scene.shape.Shape s) {
            s.setStroke(Color.RED);
            s.setStrokeWidth(3);
        }
        if (shape instanceof Path path) {  // Упрощённо для любой Path (включая бабочку)
            addRhombusResizeHandles(path);
        }
    }

    public void deselectShape() {
        if (selectedNode instanceof javafx.scene.shape.Shape s) {
            s.setStroke(Color.BLACK);
            s.setStrokeWidth(1);
        }
        if (resizeHandles != null) {
            for (Circle handle : resizeHandles) {
                pane.getChildren().remove(handle);
            }
            resizeHandles = null;
        }
        selectedNode = null;
        isResizing = false;
        resizeCorner = -1;
        hideSnapHighlight();
    }

    // Resize-методы
    private void addResizeHandles(Node shape) {
        if (resizeHandles != null) return;  // Уже есть
        resizeHandles = new Circle[8];  // Для Rectangle/Ellipse: 8, для Line: 2
        if (shape instanceof Rectangle) {
            addRectangleResizeHandles((Rectangle) shape);
        } else if (shape instanceof Ellipse) {
            addEllipseResizeHandles((Ellipse) shape);
        } else if (shape instanceof Line) {
            addLineResizeHandles((Line) shape);
        } else if (shape instanceof Path path && path.getElements().size() == 5) {  // Проверить на ромб
            addRhombusResizeHandles(path);
        }
    }

    private void addRectangleResizeHandles(Rectangle rect) {
        double x0 = rect.getX(), y0 = rect.getY(), w = rect.getWidth(), h = rect.getHeight();
        double[] x = {x0, x0 + w / 2, x0 + w};
        double[] y = {y0, y0 + h / 2, y0 + h};
        int index = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i == 1 && j == 1) continue; // центр пропускаем
                Circle handle = createResizeHandle(x[i], y[j], index);
                resizeHandles[index++] = handle;
                pane.getChildren().add(handle);
            }
        }
    }

    private void addEllipseResizeHandles(Ellipse ellipse) {
        double cx = ellipse.getCenterX(), cy = ellipse.getCenterY();
        double rx = ellipse.getRadiusX(), ry = ellipse.getRadiusY();
        Circle left = createResizeHandle(cx - rx, cy, 0);
        Circle right = createResizeHandle(cx + rx, cy, 1);
        Circle top = createResizeHandle(cx, cy - ry, 2);
        Circle bottom = createResizeHandle(cx, cy + ry, 3);
        resizeHandles[0] = left;
        resizeHandles[1] = right;
        resizeHandles[2] = top;
        resizeHandles[3] = bottom;
        pane.getChildren().addAll(left, right, top, bottom);
    }

    private void addRhombusResizeHandles(Path rhombus) {
        if (resizeHandles != null) return;
        resizeHandles = new Circle[8];
        // Получить bounding box бабочки для расчёта ручек
        double x0 = rhombus.getBoundsInLocal().getMinX();
        double y0 = rhombus.getBoundsInLocal().getMinY();
        double w = rhombus.getBoundsInLocal().getWidth();
        double h = rhombus.getBoundsInLocal().getHeight();
        double[] xs = {x0, x0 + w / 2, x0 + w};
        double[] ys = {y0, y0 + h / 2, y0 + h};
        int idx = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i == 1 && j == 1) continue;  // Пропустить центр
                Circle handle = createResizeHandle(xs[i], ys[j], idx);  // Теперь createResizeHandle обрабатывает Path
                resizeHandles[idx++] = handle;
                pane.getChildren().add(handle);
            }
        }
    }

    private void addLineResizeHandles(Line line) {
        Circle startHandle = createResizeHandle(line.getStartX(), line.getStartY(), 0);
        Circle endHandle = createResizeHandle(line.getEndX(), line.getEndY(), 1);
        resizeHandles[0] = startHandle;
        resizeHandles[1] = endHandle;
        pane.getChildren().addAll(startHandle, endHandle);
    }

    private Circle createResizeHandle(double x, double y, int handleIndex) {
        Circle handle = new Circle(x, y, 6, Color.CORAL);
        handle.setCursor(Cursor.CROSSHAIR);
        handle.setOnMousePressed(e -> {
            isResizing = true;
            resizeCorner = handleIndex;
            pressX = e.getSceneX();  // Глобальные pressed
            pressY = e.getSceneY();

            // НОВОЕ: Сохраняем initial bounds для delta-resize
            if (selectedNode instanceof Path path) {
                Bounds bounds = path.getBoundsInLocal();
                initialX = bounds.getMinX();
                initialY = bounds.getMinY();
                initialW = bounds.getWidth();
                initialH = bounds.getHeight();
            }
            hideSnapHighlight();
            e.consume();
        });
        handle.setOnMouseDragged(e -> {
            if (!isResizing) return;
            // Конвертируем в pane-координаты
            Point2D currPane = pane.sceneToLocal(e.getSceneX(), e.getSceneY());
            Point2D pressPane = pane.sceneToLocal(pressX, pressY);
            double deltaX = currPane.getX() - pressPane.getX();  // Дельта движения
            double deltaY = currPane.getY() - pressPane.getY();

            if (Math.abs(deltaX) < 1 && Math.abs(deltaY) < 1) return;  // Игнор минимального движения

            if (selectedNode instanceof Rectangle rect) {
                // Для Rectangle оставляем абсолют (как работало), но с currX/currY из pane
                resizeRectangleByHandle(rect, resizeCorner, currPane.getX(), currPane.getY());
            } else if (selectedNode instanceof Ellipse ell) {
                resizeEllipseByHandle(ell, resizeCorner, currPane.getX(), currPane.getY());
            } else if (selectedNode instanceof Line line) {
                resizeLineByHandle(line, resizeCorner, currPane.getX(), currPane.getY());
            } else if (selectedNode instanceof Path path) {
                // НОВОЕ: Delta-resize для Path (бабочка)
                resizePathByDelta(path, resizeCorner, deltaX, deltaY);
            }
            updateResizeHandles();  // Обновляем handles после изменения
            e.consume();
        });
        handle.setOnMouseReleased(e -> {
            isResizing = false;
            resizeCorner = -1;
            hideSnapHighlight();
            updateResizeHandles();
            e.consume();
        });
        return handle;
    }

    private void resizePathByDelta(Path path, int handleIdx, double deltaX, double deltaY) {
        double currentX = initialX;
        double currentY = initialY;
        double currentWidth = initialW;
        double currentHeight = initialH;

        // Стандартная логика scale для углов и сторон (копирует Rectangle)
        switch (handleIdx) {
            case 0: // left-top
                currentX += deltaX;
                currentY += deltaY;
                currentWidth -= deltaX;
                currentHeight -= deltaY;
                break;
            case 1: // left-middle
                currentX += deltaX;
                currentWidth -= deltaX;
                break;
            case 2: // left-bottom
                currentX += deltaX;
                currentHeight += deltaY;
                currentWidth -= deltaX;
                break;
            case 3: // top-middle
                currentY += deltaY;
                currentHeight -= deltaY;
                break;
            case 4: // bottom-middle
                currentHeight += deltaY;
                break;
            case 5: // right-top
                currentWidth += deltaX;
                currentY += deltaY;
                currentHeight -= deltaY;
                break;
            case 6: // right-middle
                currentWidth += deltaX;
                break;
            case 7: // right-bottom
                currentWidth += deltaX;
                currentHeight += deltaY;
                break;
        }

        // Игнорируем tiny движения
        if (Math.abs(deltaX) < 1 && Math.abs(deltaY) < 1) return;

        // Флип для отрицательных размеров
        if (currentWidth < 0) {
            currentWidth = -currentWidth;
            currentX -= currentWidth; // Сдвигаем X для флипа
        }
        if (currentHeight < 0) {
            currentHeight = -currentHeight;
            currentY -= currentHeight; // Сдвигаем Y для флипа
        }

        // Минимальные размеры
        double minSize = 20.0;
        currentWidth = Math.max(minSize, currentWidth);
        currentHeight = Math.max(minSize, currentHeight);

        // Геометрия бабочки: всегда пропорционально текущим размерам
        double side = currentWidth / 2.0;
        double finalTriangleHeight = currentHeight / 2.0;
        double topOffsetY = finalTriangleHeight * (1 - Math.sqrt(3.0) / 3.0);

        // Центр всегда в середине bounding box
        double centerX = currentX + currentWidth / 2.0;
        double centerY = currentY + currentHeight / 2.0;

        // Вершины бабочки
        double leftBaseX = centerX - side;
        double leftTopY = centerY - finalTriangleHeight + topOffsetY;
        double leftBottomY = centerY + finalTriangleHeight - topOffsetY;
        double rightBaseX = centerX + side;

        // Перестройка Path
        path.getElements().clear();
        // Левый треугольник
        path.getElements().addAll(
                new MoveTo(leftBaseX, leftTopY),
                new LineTo(centerX, centerY),
                new LineTo(leftBaseX, leftBottomY),
                new ClosePath()
        );
        // Правый треугольник
        path.getElements().addAll(
                new MoveTo(rightBaseX, leftTopY),  // rightTopX = rightBaseX, rightTopY = leftTopY
                new LineTo(centerX, centerY),
                new LineTo(rightBaseX, leftBottomY),  // rightBottomX = rightBaseX, rightBottomY = leftBottomY
                new ClosePath()
        );
        path.setStrokeWidth(1);
        path.setStroke(Color.BLACK);
    }

    private void resizeRectangleByHandle(Rectangle rect, int handleIdx, double x, double y) {
        double x0 = rect.getX(), y0 = rect.getY(), w0 = rect.getWidth(), h0 = rect.getHeight();
        double newX = x0, newY = y0, newWidth = w0, newHeight = h0;
        switch (handleIdx) {
            case 0:
                newX = x;
                newY = y;
                newWidth = w0 + (x0 - x);
                newHeight = h0 + (y0 - y);
                break;
            case 1:
                newX = x;
                newWidth = w0 + (x0 - x);
                break;
            case 2:
                newX = x;
                newHeight = y - y0;
                newWidth = w0 + (x0 - x);
                break;
            case 3:
                newY = y;
                newHeight = h0 + (y0 - y);
                break;
            case 4:
                newHeight = y - y0;
                break;
            case 5:
                newWidth = x - x0;
                newY = y;
                newHeight = h0 + (y0 - y);
                break;
            case 6:
                newWidth = x - x0;
                break;
            case 7:
                newWidth = x - x0;
                newHeight = y - y0;
                break;
        }
        if (newWidth < 0) {
            newWidth = Math.abs(newWidth);
            newX = newX - newWidth;
            if (handleIdx == 0) resizeCorner = 5;
            else if (handleIdx == 1) resizeCorner = 6;
            else if (handleIdx == 2) resizeCorner = 7;
            else if (handleIdx == 5) resizeCorner = 0;
            else if (handleIdx == 6) resizeCorner = 1;
            else if (handleIdx == 7) resizeCorner = 2;
        }
        if (newHeight < 0) {
            newHeight = Math.abs(newHeight);
            newY = newY - newHeight;
            if (handleIdx == 0) resizeCorner = 2;
            else if (handleIdx == 3) resizeCorner = 4;
            else if (handleIdx == 5) resizeCorner = 7;
            else if (handleIdx == 2) resizeCorner = 0;
            else if (handleIdx == 4) resizeCorner = 3;
            else if (handleIdx == 7) resizeCorner = 5;
        }
        if (newX != x0) rect.setX(newX);
        if (newY != y0) rect.setY(newY);
        if (newWidth != w0) rect.setWidth(newWidth);
        if (newHeight != h0) rect.setHeight(newHeight);
    }

    private void resizeEllipseByHandle(Ellipse ellipse, int handleIdx, double x, double y) {
        switch (handleIdx) {
            case 0:
                ellipse.setRadiusX(Math.abs(ellipse.getCenterX() - x));
                break;
            case 1:
                ellipse.setRadiusX(Math.abs(x - ellipse.getCenterX()));
                break;
            case 2:
                ellipse.setRadiusY(Math.abs(ellipse.getCenterY() - y));
                break;
            case 3:
                ellipse.setRadiusY(Math.abs(y - ellipse.getCenterY()));
                break;
        }
    }

    private void resizeLineByHandle(Line line, int handleIdx, double x, double y) {
        if (handleIdx == 0) {  // Ручка start
            double newStartX = x;
            double newStartY = y;
            // Применяем snap к end точке
            if (Math.abs(newStartX - line.getEndX()) < SNAP_THRESHOLD) {
                newStartX = line.getEndX();  // Вертикальная линия
            }
            if (Math.abs(newStartY - line.getEndY()) < SNAP_THRESHOLD) {
                newStartY = line.getEndY();  // Горизонтальная линия
            }
            double[] edgeSnappedStart = findNearestEdgeSnap(newStartX, newStartY);
            newStartX = edgeSnappedStart[0];
            newStartY = edgeSnappedStart[1];
            line.setStartX(newStartX);
            line.setStartY(newStartY);
            // НОВОЕ: Подсветка, если snap применён
            if (newStartX != x || newStartY != y) {
                showSnapHighlight(newStartX, newStartY);
            } else {
                hideSnapHighlight();
            }
        } else if (handleIdx == 1) {  // Ручка end
            double newEndX = x;
            double newEndY = y;
            // Применяем snap к start точке
            if (Math.abs(newEndX - line.getStartX()) < SNAP_THRESHOLD) {
                newEndX = line.getStartX();  // Вертикальная линия
            }
            if (Math.abs(newEndY - line.getStartY()) < SNAP_THRESHOLD) {
                newEndY = line.getStartY();  // Горизонтальная линия
            }
            // НОВОЕ: Edge snap к границам других линий
            double[] edgeSnappedEnd = findNearestEdgeSnap(newEndX, newEndY);
            newEndX = edgeSnappedEnd[0];
            newEndY = edgeSnappedEnd[1];
            line.setEndX(newEndX);
            line.setEndY(newEndY);
            // НОВОЕ: Подсветка, если snap применён
            if (newEndX != x || newEndY != y) {
                showSnapHighlight(newEndX, newEndY);
            } else {
                hideSnapHighlight();
            }
        }
    }

    private void updateResizeHandles() {
        if (selectedNode instanceof Rectangle rect && resizeHandles != null) {
            double[] x = {rect.getX(), rect.getX() + rect.getWidth() / 2, rect.getX() + rect.getWidth()};
            double[] y = {rect.getY(), rect.getY() + rect.getHeight() / 2, rect.getY() + rect.getHeight()};
            int index = 0;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (i == 1 && j == 1) continue;
                    if (resizeHandles[index] != null) {
                        resizeHandles[index].setCenterX(x[i]);
                        resizeHandles[index].setCenterY(y[j]);
                    }
                    index++;
                }
            }
        } else if (selectedNode instanceof Ellipse ellipse && resizeHandles != null) {
            double cx = ellipse.getCenterX(), cy = ellipse.getCenterY();
            double rx = ellipse.getRadiusX(), ry = ellipse.getRadiusY();
            if (resizeHandles[0] != null) {
                resizeHandles[0].setCenterX(cx - rx);
                resizeHandles[0].setCenterY(cy);
            }
            if (resizeHandles[1] != null) {
                resizeHandles[1].setCenterX(cx + rx);
                resizeHandles[1].setCenterY(cy);
            }
            if (resizeHandles[2] != null) {
                resizeHandles[2].setCenterX(cx);
                resizeHandles[2].setCenterY(cy - ry);
            }
            if (resizeHandles[3] != null) {
                resizeHandles[3].setCenterX(cx);
                resizeHandles[3].setCenterY(cy + ry);
            }
        } else if (selectedNode instanceof Line line && resizeHandles != null) {
            if (resizeHandles[0] != null) {
                resizeHandles[0].setCenterX(line.getStartX());
                resizeHandles[0].setCenterY(line.getStartY());
            }
            if (resizeHandles[1] != null) {
                resizeHandles[1].setCenterX(line.getEndX());
                resizeHandles[1].setCenterY(line.getEndY());
            }
            if (resizeCorner == 0) {
                double[] snappedStart = findNearestEdgeSnap(line.getStartX(), line.getStartY());  // Если нужен edge snap в редактировании
                if (Math.abs(snappedStart[0] - line.getStartX()) < SNAP_THRESHOLD || Math.abs(snappedStart[1] - line.getStartY()) < SNAP_THRESHOLD) {
                    showSnapHighlight(snappedStart[0], snappedStart[1]);
                } else {
                    hideSnapHighlight();
                }
            } else if (resizeCorner == 1) {
                double[] snappedEnd = findNearestEdgeSnap(line.getEndX(), line.getEndY());
                if (Math.abs(snappedEnd[0] - line.getEndX()) < SNAP_THRESHOLD || Math.abs(snappedEnd[1] - line.getEndY()) < SNAP_THRESHOLD) {
                    showSnapHighlight(snappedEnd[0], snappedEnd[1]);
                } else {
                    hideSnapHighlight();
                }
            }
        } else if (selectedNode instanceof Path path && resizeHandles != null) {
            Bounds bounds = path.getBoundsInLocal();  // Актуальные после resize
            double x0 = bounds.getMinX();
            double y0 = bounds.getMinY();
            double w = bounds.getWidth();
            double h = bounds.getHeight();
            double[] xs = {x0, x0 + w / 2, x0 + w};
            double[] ys = {y0, y0 + h / 2, y0 + h};
            int idx = 0;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (i == 1 && j == 1) continue;  // Центр пропускаем
                    if (idx < resizeHandles.length && resizeHandles[idx] != null) {
                        resizeHandles[idx].setCenterX(xs[i]);
                        resizeHandles[idx].setCenterY(ys[j]);
                    }
                    idx++;
                }
            }
            // ДЕБАГ: Опционально, принт bounds после update
            System.out.println("Update Handles: BOUNDS X=" + x0 + ", Y=" + y0 + ", W=" + w + ", H=" + h);
        }
    }

    // Методы для обработки событий мыши (адаптированные из контроллера)
    public void setStartCoordinates(double x, double y) {
        startX = x;
        startY = y;
    }

    // Для TEXT: контроллер вызовет диалог, затем этот метод
    public void setPreviewText(String text) {
        if (addingText) {
            previewText = new Text(startX, startY, text);
            previewText.setFill(Color.BLACK);
            pane.getChildren().add(previewText);
            addingText = false;
        }
    }

    public void onMousePressedForTool(Tool tool, double x, double y) {
        setStartCoordinates(x, y);
        clearPreview();
        hideSnapHighlight();
        if (tool == Tool.SELECT) {
            handleSelectOnPress(x, y);
        } else if (tool == Tool.LINE) {
            snappedHorizontal = false;  // Сброс флагов
            snappedVertical = false;
            previewLine = new Line(x, y, x, y);
            previewLine.setStroke(Color.GRAY);
            pane.getChildren().add(previewLine);
        } else if (tool == Tool.RECTANGLE) {
            previewRect = new Rectangle(x, y, 0, 0);
            previewRect.setFill(Color.TRANSPARENT);
            previewRect.setStroke(Color.GRAY);
            pane.getChildren().add(previewRect);
        } else if (tool == Tool.ELLIPSE) {
            previewEllipse = new Ellipse(x, y, 0, 0);
            previewEllipse.setFill(Color.TRANSPARENT);
            previewEllipse.setStroke(Color.GRAY);
            pane.getChildren().add(previewEllipse);
        } else if (tool == Tool.RHOMBUS) {
            startX = x;
            startY = y;  // Теперь центр фигуры (соединение вершин)
            previewRhombus = new Path();  // Preview для бабочки
            previewRhombus.setFill(Color.TRANSPARENT);
            previewRhombus.setStroke(Color.GRAY);
            pane.getChildren().add(previewRhombus);
        } else if (tool == Tool.TEXT) {
            addingText = true;  // Контроллер покажет диалог и вызовет setPreviewText
        }
    }

    private void handleSelectOnPress(double x, double y) {
        Node hovered = pane.getChildren().stream()
                .filter(n -> {
                    if (n instanceof Line line) {
                        // Проверяем расстояние от точки клика до линии
                        return lineDistance(x, y, line.getStartX(), line.getStartY(), line.getEndX(), line.getEndY()) < THICKNESS_THRESHOLD;
                    } else if (n instanceof javafx.scene.shape.Shape shape && !(n instanceof Circle && ((Circle) n).getFill() == Color.BLUE)) {
                        return shape.contains(x, y);
                    }
                    return false;
                })
                .findFirst().orElse(null);
        if (hovered != null) {
            selectShape(hovered);
            isDraggingSelected = true;
            dragOffsetX = x - getShapeX(hovered);
            dragOffsetY = y - getShapeY(hovered);
            hideSnapHighlight();
        } else {
            deselectShape();
            hideSnapHighlight();
        }
    }

    public void onMouseDraggedForTool(Tool tool, double x, double y) {
        if (isDraggingSelected && selectedNode != null && tool == Tool.SELECT) {
            double newX = x - dragOffsetX, newY = y - dragOffsetY;
            setShapePosition(selectedNode, newX, newY);
            updateResizeHandles();
        } else if (isResizing && selectedNode != null) {
            switch (selectedNode) {
                case Rectangle rect -> resizeRectangleByHandle(rect, resizeCorner, x, y);
                case Ellipse ell -> resizeEllipseByHandle(ell, resizeCorner, x, y);
                case Line line -> resizeLineByHandle(line, resizeCorner, x, y);
                default -> {
                }
            }
            updateResizeHandles();
        } else {
            if (tool == Tool.LINE && previewLine != null) {
                double endX = x;
                double endY = y;
                // Сброс флагов перед проверкой
                snappedHorizontal = false;
                snappedVertical = false;
                // Проверка и snap к вертикали/горизонтали
                if (Math.abs(endX - startX) < SNAP_THRESHOLD) {
                    endX = startX;
                    snappedHorizontal = true;  // Устанавливаем флаг
                }
                if (Math.abs(endY - startY) < SNAP_THRESHOLD) {
                    endY = startY;
                    snappedVertical = true;  // Устанавливаем флаг
                }
                // Потом edge snap к границам
                double[] snappedEnd = findNearestEdgeSnap(endX, endY);
                endX = snappedEnd[0];
                endY = snappedEnd[1];
                previewLine.setEndX(endX);
                previewLine.setEndY(endY);
                // Подсветка, если snap применён
                if (!(endX == x && endY == y)) {  // Если любая snap был применён
                    showSnapHighlight(endX, endY);
                } else {
                    hideSnapHighlight();
                }
            } else if (tool == Tool.RECTANGLE && previewRect != null) {
                double w = Math.abs(x - startX), h = Math.abs(y - startY);
                previewRect.setX(Math.min(startX, x));
                previewRect.setY(Math.min(startY, y));
                previewRect.setWidth(w);
                previewRect.setHeight(h);
            } else if (tool == Tool.ELLIPSE && previewEllipse != null) {
                double rx = Math.abs(x - startX) / 2, ry = Math.abs(y - startY) / 2;
                previewEllipse.setRadiusX(rx);
                previewEllipse.setRadiusY(ry);
                previewEllipse.setCenterX((startX + x) / 2);
                previewEllipse.setCenterY((startY + y) / 2);
            } else if (tool == Tool.RHOMBUS && previewRhombus != null) {
                double totalWidth = Math.abs(x - startX) * 2;  // Общая ширина: по стороне на каждый треугольник
                double mouseHeight = Math.abs(y - startY);
                double side = totalWidth / 2;  // Side для одного треугольника (от основания до центра)
                double triangleHeight = (Math.sqrt(3) / 2) * side;  // Равносторонняя высота
                double finalHeight = Math.min(mouseHeight, triangleHeight * 2);  // Подгоняем под мышь, чтобы не искажать; *2 для двух треугольников сверху/снизу от центра

                // Координаты для левого треугольника (вершина в центре, основание слева)
                double leftBaseX = startX - side;
                double centerTopY = startY - finalHeight / 2;
                double centerBottomY = startY + finalHeight / 2;
                double leftTopX = leftBaseX;
                double leftTopY = centerTopY + (finalHeight / 2) * (1 - Math.sqrt(3) / 3);  // Корректировка для 60° угла (точная геометрия)
                double leftBottomX = leftBaseX;
                double leftBottomY = centerBottomY - (finalHeight / 2) * (1 - Math.sqrt(3) / 3);

                // Координаты для правого треугольника (вершина в центре, основание справа)
                double rightBaseX = startX + side;
                double rightTopX = rightBaseX;
                double rightTopY = centerTopY + (finalHeight / 2) * (1 - Math.sqrt(3) / 3);
                double rightBottomX = rightBaseX;
                double rightBottomY = centerBottomY - (finalHeight / 2) * (1 - Math.sqrt(3) / 3);

                previewRhombus.getElements().clear();

                // Левый треугольник: основание сверху/снизу к центру (вершина вправо)
                previewRhombus.getElements().addAll(
                        new MoveTo(leftTopX, leftTopY),      // Верх левого основания
                        new LineTo(startX, startY),          // К центру (вершина)
                        new LineTo(leftBottomX, leftBottomY), // К низу левого основания
                        new ClosePath()                      // Замыкаем левый треугольник
                );

                // Правый треугольник: основание справа, вершина влево к центру
                previewRhombus.getElements().addAll(
                        new MoveTo(rightTopX, rightTopY),    // Верх правого основания
                        new LineTo(startX, startY),          // К центру (вершина)
                        new LineTo(rightBottomX, rightBottomY), // К низу правого основания
                        new ClosePath()                      // Замыкаем правый треугольник
                );
            }
        }
    }

    private void setShapePosition(Node shape, double x, double y) {
        if (shape instanceof Rectangle r) {
            r.setX(x);
            r.setY(y);
        } else if (shape instanceof Ellipse e) {
            e.setCenterX(x);
            e.setCenterY(y);
        } else if (shape instanceof Text t) {
            t.setX(x);
            t.setY(y);
        } else if (shape instanceof Line l) {
            double dx = x - l.getStartX(), dy = y - l.getStartY();
            l.setStartX(x);
            l.setStartY(y);
            l.setEndX(l.getEndX() + dx);
            l.setEndY(l.getEndY() + dy);
        } else if (shape instanceof Path p) {  // НОВОЕ: Добавлено для ромба
            double dx = x - p.getBoundsInLocal().getMinX();
            double dy = y - p.getBoundsInLocal().getMinY();
            // Сдвигаем каждую вершину ромба
            for (PathElement element : p.getElements()) {
                if (element instanceof MoveTo moveTo) {
                    moveTo.setX(moveTo.getX() + dx);
                    moveTo.setY(moveTo.getY() + dy);
                } else if (element instanceof LineTo lineTo) {
                    lineTo.setX(lineTo.getX() + dx);
                    lineTo.setY(lineTo.getY() + dy);
                }
                // ClosePath не имеет координат, пропускаем
            }
        }
    }

    private double getShapeX(Node shape) {
        return shape instanceof Rectangle r ? r.getX() :
                shape instanceof Ellipse e ? e.getCenterX() :
                        shape instanceof Text t ? t.getX() :
                                shape instanceof Line l ? l.getStartX() :
                                        shape instanceof Path p ? p.getBoundsInLocal().getMinX() : 0;  // НОВОЕ: Добавлено для ромба
    }

    private double getShapeY(Node shape) {
        return shape instanceof Rectangle r ? r.getY() :
                shape instanceof Ellipse e ? e.getCenterY() :
                        shape instanceof Text t ? t.getY() :
                                shape instanceof Line l ? l.getStartY() :
                                        shape instanceof Path p ? p.getBoundsInLocal().getMinY() : 0;  // НОВОЕ: Добавлено для ромба
    }

    public void onMouseReleasedForTool(Tool tool, double x, double y) {
        Node finalShape;
        if (tool == Tool.LINE && previewLine != null) {
            double finalEndX = x;
            double finalEndY = y;
            // Применяем сохранённые snap флаги (если были установлены в последний dragged)
            if (snappedHorizontal) {
                finalEndX = startX;  // Фиксируем по вертикали
            }
            if (snappedVertical) {
                finalEndY = startY;  // Фиксируем по горизонтали
            }
            // Потом применяем edge snap к границам (на основе исправленных координат)
            double[] snappedFinalEnd = findNearestEdgeSnap(finalEndX, finalEndY);
            finalEndX = snappedFinalEnd[0];
            finalEndY = snappedFinalEnd[1];
            finalShape = new Line(startX, startY, finalEndX, finalEndY);
            ((Line) finalShape).setStroke(Color.BLACK);
            addShape(finalShape);  // Добавлено: вызываем addShape здесь
            hideSnapHighlight();
        } else if (tool == Tool.RECTANGLE && previewRect != null) {
            finalShape = new Rectangle(previewRect.getX(), previewRect.getY(), previewRect.getWidth(), previewRect.getHeight());
            ((Rectangle) finalShape).setFill(Color.TRANSPARENT);
            ((Rectangle) finalShape).setStroke(Color.BLACK);
            addShape(finalShape);  // Добавлено: вызываем addShape здесь
        } else if (tool == Tool.ELLIPSE && previewEllipse != null) {
            finalShape = new Ellipse(previewEllipse.getCenterX(), previewEllipse.getCenterY(), previewEllipse.getRadiusX(), previewEllipse.getRadiusY());
            ((Ellipse) finalShape).setFill(Color.TRANSPARENT);
            ((Ellipse) finalShape).setStroke(Color.BLACK);
            addShape(finalShape);  // Добавлено: вызываем addShape здесь
        } else if (tool == Tool.RHOMBUS && previewRhombus != null) {
            double totalWidth = Math.abs(x - startX) * 2;
            double mouseHeight = Math.abs(y - startY);
            double side = totalWidth / 2;
            double triangleHeight = (Math.sqrt(3) / 2) * side;
            double finalHeight = Math.min(mouseHeight, triangleHeight * 2);
            // Те же координаты, что в dragged
            double leftBaseX = startX - side;
            double centerTopY = startY - finalHeight / 2;
            double centerBottomY = startY + finalHeight / 2;
            // Убрали локальные переменные, используем базовые напрямую
            double leftTopY = centerTopY + (finalHeight / 2) * (1 - Math.sqrt(3) / 3);
            double leftBottomY = centerBottomY - (finalHeight / 2) * (1 - Math.sqrt(3) / 3);
            double rightBaseX = startX + side;
            double rightTopY = centerTopY + (finalHeight / 2) * (1 - Math.sqrt(3) / 3);  // rightTopY = leftTopY, но вычисляем явно
            double rightBottomY = centerBottomY - (finalHeight / 2) * (1 - Math.sqrt(3) / 3);
            Path butterfly = new Path();  // Финальная фигура
            butterfly.setFill(Color.TRANSPARENT);
            butterfly.setStroke(Color.BLACK);
            butterfly.setStrokeWidth(1);
            // Левый треугольник (прямое использование leftBaseX)
            butterfly.getElements().addAll(
                    new MoveTo(leftBaseX, leftTopY),  // leftTopX заменён на leftBaseX
                    new LineTo(startX, startY),
                    new LineTo(leftBaseX, leftBottomY),  // leftBottomX заменён на leftBaseX
                    new ClosePath()
            );
            // Правый треугольник (прямое использование rightBaseX)
            butterfly.getElements().addAll(
                    new MoveTo(rightBaseX, rightTopY),  // rightTopX заменён на rightBaseX
                    new LineTo(startX, startY),
                    new LineTo(rightBaseX, rightBottomY),  // rightBottomX заменён на rightBaseX
                    new ClosePath()
            );
            finalShape = butterfly;
            addShape(finalShape);  // Добавляем (как в исправленной версии ранее)
            clearPreview();
            hideSnapHighlight();
        } else if (tool == Tool.TEXT && previewText != null) {
            finalShape = previewText;
            addShape(finalShape);  // Добавлено: вызываем addShape здесь
        }
        if (tool == Tool.TEXT) addingText = false;
        isDraggingSelected = false;
        isResizing = false;
    }

    private double[] findNearestEdgeSnap(double x, double y) {
        double[] closest = {x, y};
        double minDist = Double.MAX_VALUE;
        for (Node node : pane.getChildren()) {
            if (node instanceof Line line && node != previewLine && node != selectedNode) {  // Проверяем только линии, исключаем превью
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
