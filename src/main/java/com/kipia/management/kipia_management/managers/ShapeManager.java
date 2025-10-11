package com.kipia.management.kipia_management.managers;

import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.scene.paint.Color;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import java.util.Stack;


/**
 * Класс-менеджер для управления фигурами на панели схемы
 *
 * @author vladimir_shi
 * @since 11.10.2025
 */
public class ShapeManager {
    // Tool enum: определён здесь для независимости (можно вынести в общий файл, если нужно)
    public enum Tool { SELECT, LINE, RECTANGLE, ELLIPSE, TEXT, ADD_DEVICE }

    private final AnchorPane pane;  // Ссылка на панель схемы

    // Переменные для фигур и состояния (перенесены из контроллера)
    private final Stack<Command> undoStack = new Stack<>();
    private final Stack<Command> redoStack = new Stack<>();
    private Node selectedNode;
    private boolean isDraggingSelected;
    private Circle[] resizeHandles;
    private boolean isResizing;
    private int resizeCorner;
    private double dragOffsetX, dragOffsetY;

    // Preview для рисования (перенесены из контроллера)
    private Line previewLine;
    private Rectangle previewRect;
    private Ellipse previewEllipse;
    private Text previewText;
    private boolean addingText = false;  // Для TEXT инструмента
    private double startX, startY;  // Для временного хранения координат

    // Интерфейс команд (перенесён из контроллера)
    public interface Command {
        void execute();
        void undo();
    }

    public record AddShapeCommand(AnchorPane pane, Node shape) implements Command {
        @Override
        public void execute() { pane.getChildren().add(shape); }
        @Override
        public void undo() { pane.getChildren().remove(shape); }
    }

    // Конструктор: принимает pane
    public ShapeManager(AnchorPane pane) {
        this.pane = pane;
        clearPreview();
    }

    // Очистка preview (перенесена из контроллера)
    public void clearPreview() {
        if (previewLine != null) { pane.getChildren().remove(previewLine); previewLine = null; }
        if (previewRect != null) { pane.getChildren().remove(previewRect); previewRect = null; }
        if (previewEllipse != null) { pane.getChildren().remove(previewEllipse); previewEllipse = null; }
        if (previewText != null) { pane.getChildren().remove(previewText); previewText = null; }
    }

    // Добавление/удаление фигур с командами (адаптировано из контроллера)
    public void addShape(Node shape) {
        undoStack.push(new AddShapeCommand(pane, shape));
        redoStack.clear();
        pane.getChildren().add(shape);
    }

    public void removeShape(Node shape) {
        Command deleteCmd = new Command() {
            @Override public void execute() { pane.getChildren().remove(shape); }
            @Override public void undo() { pane.getChildren().add(shape); }
        };
        deleteCmd.execute();
        undoStack.push(deleteCmd);
        redoStack.clear();
    }

    // Undo/Redo (перенесены из контроллера)
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

    // Геттеры для контроллера (чтобы знать состояние)
    public Node getSelectedNode() { return selectedNode; }

    // Выделение/сброс (перенесены и адаптированы из контроллера)
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
        resizeHandles[0] = left; resizeHandles[1] = right;
        resizeHandles[2] = top; resizeHandles[3] = bottom;
        pane.getChildren().addAll(left, right, top, bottom);
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
            e.consume();
        });
        handle.setOnMouseDragged(e -> {
            if (!isResizing) return;
            double currX = e.getX(), currY = e.getY();
            if (selectedNode instanceof Rectangle rect) {
                resizeRectangleByHandle(rect, resizeCorner, currX, currY);
            } else if (selectedNode instanceof Ellipse ell) {
                resizeEllipseByHandle(ell, resizeCorner, currX, currY);
            } else if (selectedNode instanceof Line line) {
                resizeLineByHandle(line, resizeCorner, currX, currY);
            }
            updateResizeHandles();
            e.consume();
        });
        handle.setOnMouseReleased(e -> {
            isResizing = false;
            resizeCorner = -1;
            e.consume();
        });
        return handle;
    }

    private void resizeRectangleByHandle(Rectangle rect, int handleIdx, double x, double y) {
        double x0 = rect.getX(), y0 = rect.getY(), w0 = rect.getWidth(), h0 = rect.getHeight();
        double newX = x0, newY = y0, newWidth = w0, newHeight = h0;
        switch (handleIdx) {
            case 0: newX = x; newY = y; newWidth = w0 + (x0 - x); newHeight = h0 + (y0 - y); break;
            case 1: newX = x; newWidth = w0 + (x0 - x); break;
            case 2: newX = x; newHeight = y - y0; newWidth = w0 + (x0 - x); break;
            case 3: newY = y; newHeight = h0 + (y0 - y); break;
            case 4: newHeight = y - y0; break;
            case 5: newWidth = x - x0; newY = y; newHeight = h0 + (y0 - y); break;
            case 6: newWidth = x - x0; break;
            case 7: newWidth = x - x0; newHeight = y - y0; break;
        }
        if (newWidth < 0) { newWidth = Math.abs(newWidth); newX = newX - newWidth;
            if (handleIdx == 0) resizeCorner = 5; else if (handleIdx == 1) resizeCorner = 6; else if (handleIdx == 2) resizeCorner = 7;
            else if (handleIdx == 5) resizeCorner = 0; else if (handleIdx == 6) resizeCorner = 1; else if (handleIdx == 7) resizeCorner = 2; }
        if (newHeight < 0) { newHeight = Math.abs(newHeight); newY = newY - newHeight;
            if (handleIdx == 0) resizeCorner = 2; else if (handleIdx == 3) resizeCorner = 4; else if (handleIdx == 5) resizeCorner = 7;
            else if (handleIdx == 2) resizeCorner = 0; else if (handleIdx == 4) resizeCorner = 3; else if (handleIdx == 7) resizeCorner = 5; }
        if (newX != x0) rect.setX(newX);
        if (newY != y0) rect.setY(newY);
        if (newWidth != w0) rect.setWidth(newWidth);
        if (newHeight != h0) rect.setHeight(newHeight);
    }

    private void resizeEllipseByHandle(Ellipse ellipse, int handleIdx, double x, double y) {
        switch (handleIdx) {
            case 0: ellipse.setRadiusX(Math.abs(ellipse.getCenterX() - x)); break;
            case 1: ellipse.setRadiusX(Math.abs(x - ellipse.getCenterX())); break;
            case 2: ellipse.setRadiusY(Math.abs(ellipse.getCenterY() - y)); break;
            case 3: ellipse.setRadiusY(Math.abs(y - ellipse.getCenterY())); break;
        }
    }

    private void resizeLineByHandle(Line line, int handleIdx, double x, double y) {
        if (handleIdx == 0) { line.setStartX(x); line.setStartY(y); }
        else if (handleIdx == 1) { line.setEndX(x); line.setEndY(y); }
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
            if (resizeHandles[0] != null) { resizeHandles[0].setCenterX(cx - rx); resizeHandles[0].setCenterY(cy); }
            if (resizeHandles[1] != null) { resizeHandles[1].setCenterX(cx + rx); resizeHandles[1].setCenterY(cy); }
            if (resizeHandles[2] != null) { resizeHandles[2].setCenterX(cx); resizeHandles[2].setCenterY(cy - ry); }
            if (resizeHandles[3] != null) { resizeHandles[3].setCenterX(cx); resizeHandles[3].setCenterY(cy + ry); }
        } else if (selectedNode instanceof Line line && resizeHandles != null) {
            if (resizeHandles[0] != null) { resizeHandles[0].setCenterX(line.getStartX()); resizeHandles[0].setCenterY(line.getStartY()); }
            if (resizeHandles[1] != null) { resizeHandles[1].setCenterX(line.getEndX()); resizeHandles[1].setCenterY(line.getEndY()); }
        }
    }

    // Методы для обработки событий мыши (адаптированные из контроллера)
    public void setStartCoordinates(double x, double y) { startX = x; startY = y; }

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
        if (tool == Tool.SELECT) {
            handleSelectOnPress(x, y);
        } else if (tool == Tool.LINE) {
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
        } else if (tool == Tool.TEXT) {
            addingText = true;  // Контроллер покажет диалог и вызовет setPreviewText
        }
        // ADD_DEVICE обрабатывается контроллером вообще
    }

    private void handleSelectOnPress(double x, double y) {
        Node hovered = pane.getChildren().stream()
                .filter(n -> n instanceof javafx.scene.shape.Shape && !(n instanceof Circle && ((Circle) n).getFill() == Color.BLUE))
                .filter(n -> n.contains(x, y)).findFirst().orElse(null);
        if (hovered != null) {
            selectShape(hovered);
            isDraggingSelected = true;
            dragOffsetX = x - getShapeX(hovered);
            dragOffsetY = y - getShapeY(hovered);
        } else {
            deselectShape();
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
            if (tool == Tool.LINE && previewLine != null) { previewLine.setEndX(x); previewLine.setEndY(y); }
            else if (tool == Tool.RECTANGLE && previewRect != null) {
                double w = Math.abs(x - startX), h = Math.abs(y - startY);
                previewRect.setX(Math.min(startX, x)); previewRect.setY(Math.min(startY, y));
                previewRect.setWidth(w); previewRect.setHeight(h);
            } else if (tool == Tool.ELLIPSE && previewEllipse != null) {
                double rx = Math.abs(x - startX) / 2, ry = Math.abs(y - startY) / 2;
                previewEllipse.setRadiusX(rx); previewEllipse.setRadiusY(ry);
                previewEllipse.setCenterX((startX + x) / 2); previewEllipse.setCenterY((startY + y) / 2);
            }
        }
    }

    private void setShapePosition(Node shape, double x, double y) {
        if (shape instanceof Rectangle r) { r.setX(x); r.setY(y); }
        else if (shape instanceof Ellipse e) { e.setCenterX(x); e.setCenterY(y); }
        else if (shape instanceof Text t) { t.setX(x); t.setY(y); }
        else if (shape instanceof Line l) {
            double dx = x - l.getStartX(), dy = y - l.getStartY();
            l.setStartX(x); l.setStartY(y);
            l.setEndX(l.getEndX() + dx); l.setEndY(l.getEndY() + dy);
        }
    }

    private double getShapeX(Node shape) {
        return shape instanceof Rectangle r ? r.getX() :
                shape instanceof Ellipse e ? e.getCenterX() :
                        shape instanceof Text t ? t.getX() :
                                shape instanceof Line l ? l.getStartX() : 0;
    }

    private double getShapeY(Node shape) {
        return shape instanceof Rectangle r ? r.getY() :
                shape instanceof Ellipse e ? e.getCenterY() :
                        shape instanceof Text t ? t.getY() :
                                shape instanceof Line l ? l.getStartY() : 0;
    }

    public void onMouseReleasedForTool(Tool tool, double x, double y) {
        Node finalShape = null;
        if (tool == Tool.LINE && previewLine != null) {
            finalShape = new Line(startX, startY, x, y);
            ((Line) finalShape).setStroke(Color.BLACK);
        } else if (tool == Tool.RECTANGLE && previewRect != null) {
            finalShape = new Rectangle(previewRect.getX(), previewRect.getY(), previewRect.getWidth(), previewRect.getHeight());
            ((Rectangle) finalShape).setFill(Color.TRANSPARENT);
            ((Rectangle) finalShape).setStroke(Color.BLACK);
        } else if (tool == Tool.ELLIPSE && previewEllipse != null) {
            finalShape = new Ellipse(previewEllipse.getCenterX(), previewEllipse.getCenterY(), previewEllipse.getRadiusX(), previewEllipse.getRadiusY());
            ((Ellipse) finalShape).setFill(Color.TRANSPARENT);
            ((Ellipse) finalShape).setStroke(Color.BLACK);
        } else if (tool == Tool.TEXT && previewText != null) {
            finalShape = previewText;
        }
        clearPreview();
        if (finalShape != null) addShape(finalShape);
        if (tool == Tool.TEXT) addingText = false;
        isDraggingSelected = false;
        isResizing = false;
    }
    public void clearUndoRedo() {
        undoStack.clear();
        redoStack.clear();
    }
}
