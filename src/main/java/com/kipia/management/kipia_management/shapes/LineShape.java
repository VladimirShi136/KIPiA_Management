package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ClipboardManager;
import com.kipia.management.kipia_management.managers.ShapeManager;
import com.kipia.management.kipia_management.services.ShapeService;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class LineShape extends ShapeBase {
    private final Line line;
    private final double defaultStrokeWidth = 2.0;
    private Circle startHandle, endHandle;
    private static final double HANDLE_RADIUS = 6.0;
    private Circle snapHighlight;

    private Circle snapIndicator;
    private static final double SNAP_THRESHOLD = 10.0;

    // Константы для точного определения попадания
    private static final double HIT_THRESHOLD = 5.0;

    public LineShape(double startX, double startY, double endX, double endY,
                     AnchorPane pane, Consumer<String> statusSetter,
                     Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        super(pane, statusSetter, onSelectCallback, shapeManager);

        LOGGER.info("LineShape constructor: original start=({},{}), end=({},{})",
                startX, startY, endX, endY);

        // ИСПРАВЛЕНО: нормализуем layoutX/Y = minX/minY,
        // а координаты линии хранятся в локальной системе группы
        double minX = Math.min(startX, endX);
        double minY = Math.min(startY, endY);

        line = new Line(startX - minX, startY - minY, endX - minX, endY - minY);
        line.setStroke(Color.BLACK);
        line.setStrokeWidth(defaultStrokeWidth);

        getChildren().clear();
        getChildren().add(line);

        // Группа позиционируется в мировых координатах
        setLayoutX(minX);
        setLayoutY(minY);

        createLineHandles();
        setCurrentDimensions(Math.abs(endX - startX), Math.abs(endY - startY));
        setupLineEventHandlers();

        LOGGER.info("LineShape created: layoutX={}, layoutY={}, line.start=({}, {}), line.end=({}, {})",
                getLayoutX(), getLayoutY(), line.getStartX(), line.getStartY(), line.getEndX(), line.getEndY());
    }

    /**
     * Переопределяем contains для точного определения попадания на линию
     */
    @Override
    public boolean contains(double localX, double localY) {
        return containsPoint(localX, localY);
    }

    /**
     * ИСПРАВЛЕНО: больше не переопределяем handleDragDragged и handleDragReleased —
     * базовая реализация ShapeBase корректно работает через layoutX/Y.
     * Оба метода были удалены из LineShape.
     */

    @Override
    public boolean contains(Point2D localPoint) {
        return containsPoint(localPoint.getX(), localPoint.getY());
    }

    /**
     * Проверяет попадание точки на линию в локальных координатах группы.
     * localX/localY уже в системе группы (т.е. смещены на layoutX/Y).
     */
    @Override
    protected boolean containsLocalPoint(double localX, double localY) {
        double x1 = line.getStartX();
        double y1 = line.getStartY();
        double x2 = line.getEndX();
        double y2 = line.getEndY();
        double distance = distanceToLine(localX, localY, x1, y1, x2, y2);
        double effectiveThreshold = HIT_THRESHOLD + (line.getStrokeWidth() / 2);
        return distance <= effectiveThreshold;
    }

    /**
     * ИСПРАВЛЕНО: возвращает мировые координаты обеих точек,
     * добавляя layoutX/Y к локальным координатам линии.
     */
    protected double[] getAbsoluteCoordinates() {
        return new double[]{
                getLayoutX() + line.getStartX(),
                getLayoutY() + line.getStartY(),
                getLayoutX() + line.getEndX(),
                getLayoutY() + line.getEndY()
        };
    }

    @Override
    protected double getMaxRelativeX() {
        // Локальные координаты линии уже относительны группе
        return Math.max(line.getStartX(), line.getEndX());
    }

    @Override
    protected double getMaxRelativeY() {
        return Math.max(line.getStartY(), line.getEndY());
    }

    @Override
    protected String getMoveStatusMessage() {
        return "Позиция линии изменена";
    }

    @Override
    protected String getResizeStatusMessage() {
        return "Размер линии изменен";
    }

    @Override
    protected void resizeShape(double width, double height) {
        // Для линии resize происходит через handles
    }

    private void createLineHandles() {
        if (startHandle != null) {
            pane.getChildren().remove(startHandle);
            startHandle = null;
        }
        if (endHandle != null) {
            pane.getChildren().remove(endHandle);
            endHandle = null;
        }

        startHandle = new Circle(HANDLE_RADIUS, Color.CORAL);
        startHandle.setCursor(Cursor.CROSSHAIR);
        startHandle.setVisible(false);
        pane.getChildren().add(startHandle);

        endHandle = new Circle(HANDLE_RADIUS, Color.CORAL);
        endHandle.setCursor(Cursor.CROSSHAIR);
        endHandle.setVisible(false);
        pane.getChildren().add(endHandle);

        setupLineHandleEvents();
        updateResizeHandles();
    }

    @Override
    public void createResizeHandles() {
        createLineHandles();
    }

    /**
     * ИСПРАВЛЕНО: позиции handles = layoutX/Y + локальные координаты линии
     */
    @Override
    public void updateResizeHandles() {
        if (startHandle != null) {
            startHandle.setCenterX(getLayoutX() + line.getStartX());
            startHandle.setCenterY(getLayoutY() + line.getStartY());
        }
        if (endHandle != null) {
            endHandle.setCenterX(getLayoutX() + line.getEndX());
            endHandle.setCenterY(getLayoutY() + line.getEndY());
        }
    }

    /**
     * Настройка обработчиков событий для основной линии
     */
    private void setupLineEventHandlers() {
        line.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                // Переводим координаты события линии в координаты панели
                Point2D panePoint = line.localToParent(event.getX(), event.getY());
                // Инициализируем drag: dragOffset = позиция мыши в системе группы
                // event.getX()/getY() уже в локальной системе группы (Group)
                initializeDrag(new Point2D(event.getX(), event.getY()));

                if (onSelectCallback != null) {
                    onSelectCallback.accept(this);
                }
                makeResizeHandlesVisible();
                event.consume();
            }
        });

        line.setOnMouseClicked(clickEvent -> {
            if (clickEvent.getButton() == MouseButton.PRIMARY && clickEvent.getClickCount() == 2) {
                if (onSelectCallback != null) {
                    onSelectCallback.accept(this);
                }
                makeResizeHandlesVisible();
                clickEvent.consume();
            }
        });
    }

    /**
     * Проверяет, находится ли точка рядом с линией (в мировых координатах).
     * containsPoint использует мировые координаты, вычисленные через getAbsoluteCoordinates().
     */
    public boolean containsPoint(double worldX, double worldY) {
        try {
            // Переводим мировые координаты в локальные для distanceToLine
            double localX = worldX;
            double localY = worldY;
            double x1 = line.getStartX();
            double y1 = line.getStartY();
            double x2 = line.getEndX();
            double y2 = line.getEndY();

            // Смещаем точку в локальную систему группы
            double localPx = localX - getLayoutX();
            double localPy = localY - getLayoutY();

            double distance = distanceToLine(localPx, localPy, x1, y1, x2, y2);
            double effectiveThreshold = HIT_THRESHOLD + (line.getStrokeWidth() / 2);

            return distance <= effectiveThreshold;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Вычисляет расстояние от точки до отрезка
     */
    private double distanceToLine(double px, double py, double x1, double y1, double x2, double y2) {
        double A = px - x1;
        double B = py - y1;
        double C = x2 - x1;
        double D = y2 - y1;

        double dot = A * C + B * D;
        double lenSq = C * C + D * D;

        if (lenSq == 0) {
            return Math.sqrt(A * A + B * B);
        }

        double param = dot / lenSq;

        double xx, yy;

        if (param < 0) {
            xx = x1;
            yy = y1;
        } else if (param > 1) {
            xx = x2;
            yy = y2;
        } else {
            xx = x1 + param * C;
            yy = y1 + param * D;
        }

        double dx = px - xx;
        double dy = py - yy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public void removeResizeHandles() {
        if (startHandle != null) {
            pane.getChildren().remove(startHandle);
            startHandle.setOnMousePressed(null);
            startHandle.setOnMouseDragged(null);
            startHandle.setOnMouseReleased(null);
            startHandle = null;
        }
        if (endHandle != null) {
            pane.getChildren().remove(endHandle);
            endHandle.setOnMousePressed(null);
            endHandle.setOnMouseDragged(null);
            endHandle.setOnMouseReleased(null);
            endHandle = null;
        }
        removeSnapHighlight();
        removeSnapIndicator();
    }

    private void setupLineHandleEvents() {
        if (shapeManager != null) {
            for (Circle handle : new Circle[]{startHandle, endHandle}) {
                if (handle != null) {
                    final double[][] initialCoordsHolder = new double[1][];

                    handle.setOnMousePressed(event -> {
                        // Сохраняем начальные мировые координаты линии для undo
                        initialCoordsHolder[0] = getAbsoluteCoordinates();
                        event.consume();
                    });

                    handle.setOnMouseDragged(event -> {
                        // Переводим в мировые координаты панели
                        Point2D worldPoint = ((Node) event.getSource()).localToParent(event.getX(), event.getY());
                        double paneX = worldPoint.getX();
                        double paneY = worldPoint.getY();

                        paneX = Math.max(0, Math.min(paneX, canvasBoundsWidth));
                        paneY = Math.max(0, Math.min(paneY, canvasBoundsHeight));

                        double[] currentCoords = getAbsoluteCoordinates();
                        double otherX, otherY;

                        if (handle == startHandle) {
                            otherX = currentCoords[2];
                            otherY = currentCoords[3];
                        } else {
                            otherX = currentCoords[0];
                            otherY = currentCoords[1];
                        }

                        double[] snappedCoords = applyLineSnapWithShapes(paneX, paneY, otherX, otherY);
                        double snappedX = Math.max(0, Math.min(snappedCoords[0], canvasBoundsWidth));
                        double snappedY = Math.max(0, Math.min(snappedCoords[1], canvasBoundsHeight));

                        if (handle == startHandle) {
                            setLinePoints(snappedX, snappedY, otherX, otherY);
                        } else {
                            setLinePoints(otherX, otherY, snappedX, snappedY);
                        }

                        event.consume();
                    });

                    handle.setOnMouseReleased(event -> {
                        hideSnapIndicator();

                        if (initialCoordsHolder[0] != null) {
                            double[] finalCoords = getAbsoluteCoordinates();

                            if (hasLineChanged(initialCoordsHolder[0], finalCoords)) {
                                shapeManager.registerLinePointsChange(
                                        this,
                                        initialCoordsHolder[0][0], initialCoordsHolder[0][1],
                                        initialCoordsHolder[0][2], initialCoordsHolder[0][3],
                                        finalCoords[0], finalCoords[1],
                                        finalCoords[2], finalCoords[3]
                                );

                                if (statusSetter != null) {
                                    statusSetter.accept("Размер линии изменен");
                                }
                            }
                        }
                        event.consume();
                    });
                }
            }
        }
    }

    private void hideSnapHighlight() {
        if (snapHighlight != null) {
            snapHighlight.setVisible(false);
        }
    }

    private void removeSnapHighlight() {
        if (snapHighlight != null) {
            pane.getChildren().remove(snapHighlight);
            snapHighlight = null;
        }
    }

    /**
     * Применяет фиксацию линии к осям при приближении
     */
    private double[] applyLineSnap(double movingX, double movingY, double fixedX, double fixedY) {
        double snapThreshold = 15.0;
        double angleSnapThreshold = 10.0;

        double currentAngle = Math.toDegrees(Math.atan2(movingY - fixedY, movingX - fixedX));

        double[] snapAngles = {0, 45, 90, 135, 180, 225, 270, 315};
        double snappedAngle = currentAngle;

        for (double snapAngle : snapAngles) {
            double angleDiff = Math.abs(((currentAngle - snapAngle) + 180) % 360 - 180);
            if (angleDiff < angleSnapThreshold) {
                snappedAngle = snapAngle;
                break;
            }
        }

        if (snappedAngle != currentAngle) {
            double distance = Math.sqrt(Math.pow(movingX - fixedX, 2) + Math.pow(movingY - fixedY, 2));
            double angleRad = Math.toRadians(snappedAngle);

            double resultX = fixedX + distance * Math.cos(angleRad);
            double resultY = fixedY + distance * Math.sin(angleRad);

            int snapType = (snappedAngle % 90 == 0) ?
                    (snappedAngle % 180 == 0 ? 1 : 2) : 3;

            return new double[]{resultX, resultY, snapType};
        }

        double deltaX = Math.abs(movingX - fixedX);
        double deltaY = Math.abs(movingY - fixedY);

        boolean nearHorizontal = deltaY < snapThreshold && deltaX > snapThreshold;
        boolean nearVertical = deltaX < snapThreshold && deltaY > snapThreshold;

        double resultX = movingX;
        double resultY = movingY;
        int snapType = 0;

        if (nearHorizontal) {
            resultY = fixedY;
            snapType = 1;
        } else if (nearVertical) {
            resultX = fixedX;
            snapType = 2;
        }

        return new double[]{resultX, resultY, snapType};
    }

    private boolean hasLineChanged(double[] initial, double[] current) {
        return Math.abs(initial[0] - current[0]) > 0.1 ||
                Math.abs(initial[1] - current[1]) > 0.1 ||
                Math.abs(initial[2] - current[2]) > 0.1 ||
                Math.abs(initial[3] - current[3]) > 0.1;
    }

    @Override
    protected void applyCurrentStyle() {
        line.setStroke(strokeColor);
        line.setStrokeWidth(defaultStrokeWidth);
    }

    @Override
    protected void applySelectedStyle() {
        line.setStroke(Color.BLUE);
        line.setStrokeWidth(defaultStrokeWidth + 1);
    }

    @Override
    protected void applyDefaultStyle() {
        applyCurrentStyle();
    }

    @Override
    public String getShapeType() {
        return "LINE";
    }

    @Override
    public void highlightAsSelected() {
        applySelectedStyle();
    }

    @Override
    public void resetHighlight() {
        applyDefaultStyle();
        hideResizeHandles();
    }

    private void hideResizeHandles() {
        if (startHandle != null) {
            startHandle.setVisible(false);
        }
        if (endHandle != null) {
            endHandle.setVisible(false);
        }
        hideSnapHighlight();
    }

    private Point2D findNearestSnapPoint(double x, double y) {
        if (shapeManager == null) return null;

        Point2D bestSnap = null;
        double minDistance = Double.MAX_VALUE;

        List<ShapeBase> allShapes = getAllOtherShapes();

        for (ShapeBase shape : allShapes) {
            if (shape == this) continue;

            List<Point2D> snapPoints = shape.getSnapPoints();
            for (Point2D point : snapPoints) {
                double distance = point.distance(x, y);
                if (distance < SNAP_THRESHOLD && distance < minDistance) {
                    minDistance = distance;
                    bestSnap = point;
                }
            }
        }

        Point2D lineSnap = findNearestLineSnapPoint(x, y);
        if (lineSnap != null && lineSnap.distance(x, y) < minDistance) {
            bestSnap = lineSnap;
        }

        return bestSnap;
    }

    private Point2D findNearestLineSnapPoint(double x, double y) {
        List<ShapeBase> allShapes = getAllOtherShapes();
        Point2D bestSnap = null;
        double minDistance = Double.MAX_VALUE;

        for (ShapeBase shape : allShapes) {
            if (shape instanceof LineShape otherLine && otherLine != this) {
                List<Point2D> linePoints = otherLine.getSnapPoints();
                for (Point2D point : linePoints) {
                    double distance = point.distance(x, y);
                    if (distance < SNAP_THRESHOLD && distance < minDistance) {
                        minDistance = distance;
                        bestSnap = point;
                    }
                }
            }
        }

        return bestSnap;
    }

    private List<ShapeBase> getAllOtherShapes() {
        List<ShapeBase> shapes = new ArrayList<>();

        if (shapeManager != null) {
            try {
                java.lang.reflect.Field shapeServiceField = shapeManager.getClass().getDeclaredField("shapeService");
                shapeServiceField.setAccessible(true);
                ShapeService shapeService = (ShapeService) shapeServiceField.get(shapeManager);

                if (shapeService != null) {
                    java.lang.reflect.Field shapesField = shapeService.getClass().getDeclaredField("shapes");
                    shapesField.setAccessible(true);
                    List<ShapeBase> allShapes = (List<ShapeBase>) shapesField.get(shapeService);

                    for (ShapeBase shape : allShapes) {
                        if (shape != this) {
                            shapes.add(shape);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Ошибка доступа к ShapeService: " + e.getMessage());
                return getShapesFromPane();
            }
        }

        return shapes;
    }

    private List<ShapeBase> getShapesFromPane() {
        List<ShapeBase> shapes = new ArrayList<>();
        for (Node node : pane.getChildren()) {
            if (node instanceof ShapeBase shape && shape != this) {
                shapes.add(shape);
            }
        }
        return shapes;
    }

    private void showSnapIndicator(double x, double y, int snapType) {
        if (snapIndicator == null) {
            snapIndicator = new Circle(4, getSnapColor(snapType));
            snapIndicator.setStroke(Color.WHITE);
            snapIndicator.setStrokeWidth(1);
            pane.getChildren().add(snapIndicator);
        } else {
            snapIndicator.setFill(getSnapColor(snapType));
        }
        snapIndicator.setCenterX(x);
        snapIndicator.setCenterY(y);
        snapIndicator.setVisible(true);
    }

    private Color getSnapColor(int snapType) {
        return switch (snapType) {
            case 1 -> Color.RED;
            case 2 -> Color.BLUE;
            case 3 -> Color.GREEN;
            case 4 -> Color.PURPLE;
            default -> Color.RED;
        };
    }

    private void hideSnapIndicator() {
        if (snapIndicator != null) {
            snapIndicator.setVisible(false);
        }
    }

    private double[] applyLineSnapWithShapes(double movingX, double movingY, double fixedX, double fixedY) {
        Point2D snapPoint = findNearestSnapPoint(movingX, movingY);

        if (snapPoint != null) {
            showSnapIndicator(snapPoint.getX(), snapPoint.getY(), 4);
            return new double[]{snapPoint.getX(), snapPoint.getY(), 4};
        } else {
            hideSnapIndicator();
        }

        return applyLineSnap(movingX, movingY, fixedX, fixedY);
    }

    private void removeSnapIndicator() {
        if (snapIndicator != null) {
            pane.getChildren().remove(snapIndicator);
            snapIndicator = null;
        }
    }

    @Override
    public void addContextMenu(Consumer<ShapeHandler> deleteAction) {
        ContextMenu lineContextMenu = new ContextMenu();

        MenuItem copyItem = new MenuItem("Копировать");
        copyItem.setOnAction(_ -> {
            copyToClipboard();
            lineContextMenu.hide();
        });

        MenuItem pasteItem = new MenuItem("Вставить");
        pasteItem.setOnAction(_ -> {
            pasteFromClipboard();
            lineContextMenu.hide();
        });
        pasteItem.disableProperty().bind(ClipboardManager.hasShapeDataProperty().not());

        MenuItem strokeColorItem = new MenuItem("Изменить цвет линии");
        strokeColorItem.setOnAction(_ -> {
            changeLineColor();
            lineContextMenu.hide();
        });

        SeparatorMenuItem separator = new SeparatorMenuItem();

        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.setOnAction(_ -> {
            if (deleteAction != null) {
                deleteAction.accept(this);
            }
            lineContextMenu.hide();
        });

        lineContextMenu.getItems().addAll(copyItem, pasteItem, strokeColorItem, separator, deleteItem);

        line.setOnContextMenuRequested(event -> {
            lineContextMenu.show(line, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        setOnContextMenuRequested(event -> {
            lineContextMenu.show(this, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void changeLineColor() {
        Optional<Color> result = CustomAlertDialog.showColorPickerDialog("Изменение цвета линии", strokeColor);
        result.ifPresent(color -> {
            setStrokeColor(color);
            if (statusSetter != null) {
                statusSetter.accept("Цвет линии изменен");
            }
        });
    }

    @Override
    public void makeResizeHandlesVisible() {
        if (startHandle == null || endHandle == null) {
            removeResizeHandles();
            createLineHandles();
        }
        setupLineHandleEvents();
        if (startHandle != null) {
            startHandle.setVisible(true);
        }
        if (endHandle != null) {
            endHandle.setVisible(true);
        }
        updateResizeHandles();
    }

    /**
     * ИСПРАВЛЕНО: центр вычисляется в мировых координатах
     */
    @Override
    protected Point2D getCenterInPane() {
        return new Point2D(
                getLayoutX() + (line.getStartX() + line.getEndX()) / 2,
                getLayoutY() + (line.getStartY() + line.getEndY()) / 2
        );
    }

    @Override
    public double getCurrentWidth() {
        return Math.abs(line.getEndX() - line.getStartX());
    }

    @Override
    public double getCurrentHeight() {
        return Math.abs(line.getEndY() - line.getStartY());
    }

    @Override
    protected void setCurrentDimensions(double width, double height) {
        // Для линии dimensions не кешируются — всегда вычисляются из координат
    }

    /**
     * ИСПРАВЛЕНО: нормализует layoutX/Y = minX/minY, линия хранится в локальных координатах.
     * Это обеспечивает корректную работу drag и clampToCanvasBounds из ShapeBase.
     */
    public void setLinePoints(double startX, double startY, double endX, double endY) {
        double minX = Math.min(startX, endX);
        double minY = Math.min(startY, endY);

        line.setStartX(startX - minX);
        line.setStartY(startY - minY);
        line.setEndX(endX - minX);
        line.setEndY(endY - minY);

        setLayoutX(minX);
        setLayoutY(minY);

        updateResizeHandles();
    }

    @Override
    public void setPosition(double x, double y) {
        // Вычисляем смещение и двигаем обе точки, сохраняя направление
        double[] abs = getAbsoluteCoordinates();
        double dx = x - getLayoutX();
        double dy = y - getLayoutY();
        setLinePoints(abs[0] + dx, abs[1] + dy, abs[2] + dx, abs[3] + dy);
    }

    @Override
    public void setRotation(double angle) {
        // Линия не поддерживает поворот
    }

    @Override
    protected void handleRotationInMenu() {
        if (statusSetter != null) {
            statusSetter.accept("Поворот линии не поддерживается");
        }
    }

    @Override
    public String serialize() {
        double[] absCoords = getAbsoluteCoordinates();
        return String.format("LINE|%.2f|%.2f|%.2f|%.2f|%.1f%s",
                absCoords[0], absCoords[1], absCoords[2], absCoords[3], rotationAngle, serializeColors());
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public double getStartX() {
        return getLayoutX() + line.getStartX();
    }

    public double getStartY() {
        return getLayoutY() + line.getStartY();
    }

    public double getEndX() {
        return getLayoutX() + line.getEndX();
    }

    public double getEndY() {
        return getLayoutY() + line.getEndY();
    }

    public Line getLine() {
        return line;
    }

    @Override
    public javafx.geometry.Rectangle2D getWorldBounds() {
        double[] coords = getAbsoluteCoordinates();
        double minX = Math.min(coords[0], coords[2]);
        double minY = Math.min(coords[1], coords[3]);
        double maxX = Math.max(coords[0], coords[2]);
        double maxY = Math.max(coords[1], coords[3]);
        return new javafx.geometry.Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }
}