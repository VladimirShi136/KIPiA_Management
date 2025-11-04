package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import javafx.scene.Cursor;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.geometry.Point2D;
import java.util.function.Consumer;

public class LineShape extends ShapeBase {
    private Line line;
    private Line hitLine;
    private Color defaultStroke = Color.BLACK;
    private double defaultStrokeWidth = 1.0;
    private double hitStrokeWidth = 8.0;
    private Color selectedStroke = Color.DODGERBLUE;
    private Circle startHandle, endHandle;
    private static final double HANDLE_RADIUS = 6.0;

    // Границы красного квадрата
    private static final double MIN_X = 0;
    private static final double MIN_Y = 0;
    private static final double MAX_X = 1600;
    private static final double MAX_Y = 1200;

    public LineShape(double startX, double startY, double endX, double endY,
                     AnchorPane pane, Consumer<String> statusSetter,
                     Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        super(pane, statusSetter, onSelectCallback, shapeManager);

        // Приводим координаты к допустимым границам при создании
        startX = clampX(startX);
        startY = clampY(startY);
        endX = clampX(endX);
        endY = clampY(endY);

        line = new Line(0, 0, endX - startX, endY - startY);
        line.setStroke(defaultStroke);
        line.setStrokeWidth(defaultStrokeWidth);

        // Линия для захвата
        hitLine = new Line(0, 0, endX - startX, endY - startY);
        hitLine.setStroke(Color.TRANSPARENT);
        hitLine.setStrokeWidth(hitStrokeWidth);
        hitLine.setPickOnBounds(true);

        hitLine.startXProperty().bind(line.startXProperty());
        hitLine.startYProperty().bind(line.startYProperty());
        hitLine.endXProperty().bind(line.endXProperty());
        hitLine.endYProperty().bind(line.endYProperty());

        getChildren().addAll(hitLine, line);

        setLayoutX(startX);
        setLayoutY(startY);

        createLineHandles();
        setupHitLineEvents();

        setCurrentDimensions(Math.abs(endX - startX), Math.abs(endY - startY));
    }

    // Методы для ограничения координат
    private double clampX(double x) {
        return Math.max(MIN_X, Math.min(x, MAX_X));
    }

    private double clampY(double y) {
        return Math.max(MIN_Y, Math.min(y, MAX_Y));
    }

    /**
     * Проверяет, находятся ли обе точки линии в пределах границ
     */
    private boolean areBothPointsInBounds(double startXAbs, double startYAbs, double endXAbs, double endYAbs) {
        boolean startInBounds = (startXAbs >= MIN_X && startXAbs <= MAX_X && startYAbs >= MIN_Y && startYAbs <= MAX_Y);
        boolean endInBounds = (endXAbs >= MIN_X && endXAbs <= MAX_X && endYAbs >= MIN_Y && endYAbs <= MAX_Y);
        return startInBounds && endInBounds;
    }

    /**
     * Получает абсолютные координаты обеих точек линии
     */
    private double[] getAbsoluteCoordinates() {
        double startXAbs = getLayoutX() + line.getStartX();
        double startYAbs = getLayoutY() + line.getStartY();
        double endXAbs = getLayoutX() + line.getEndX();
        double endYAbs = getLayoutY() + line.getEndY();
        return new double[]{startXAbs, startYAbs, endXAbs, endYAbs};
    }

    public Line getLine() {
        return line;
    }

    @Override
    protected double getMaxRelativeX() {
        return Math.max(line.getStartX(), line.getEndX());
    }

    @Override
    protected double getMaxRelativeY() {
        return Math.max(line.getStartY(), line.getEndY());
    }

    @Override
    protected void resizeShape(double width, double height) {
        // For line: ignore, resize через handles
    }

    private void createLineHandles() {
        if (startHandle == null) {
            startHandle = new Circle(HANDLE_RADIUS, Color.CORAL);
            startHandle.setCursor(Cursor.CROSSHAIR);
            startHandle.setVisible(false);
            pane.getChildren().add(startHandle);
        }
        if (endHandle == null) {
            endHandle = new Circle(HANDLE_RADIUS, Color.CORAL);
            endHandle.setCursor(Cursor.CROSSHAIR);
            endHandle.setVisible(false);
            pane.getChildren().add(endHandle);
        }
        updateResizeHandles();
        setupLineHandleEvents();
    }

    @Override
    public void createResizeHandles() {
        createLineHandles();
    }

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
    }

    private void setupLineHandleEvents() {
        if (shapeManager != null) {
            for (Circle handle : new Circle[]{startHandle, endHandle}) {
                if (handle != null) {
                    handle.setOnMousePressed(event -> {
                        double handleX = getLayoutX() + (handle == startHandle ? line.getStartX() : line.getEndX());
                        double handleY = getLayoutY() + (handle == startHandle ? line.getStartY() : line.getEndY());
                        initialX = handleX;
                        initialY = handleY;
                        event.consume();
                    });

                    handle.setOnMouseDragged(event -> {
                        double sceneX = event.getSceneX();
                        double sceneY = event.getSceneY();
                        Point2D panePoint = pane.sceneToLocal(sceneX, sceneY);
                        double paneX = clampX(panePoint.getX());
                        double paneY = clampY(panePoint.getY());

                        // Получаем текущие абсолютные координаты
                        double[] currentCoords = getAbsoluteCoordinates();
                        double otherX, otherY;

                        if (handle == startHandle) {
                            otherX = currentCoords[2]; // endX
                            otherY = currentCoords[3]; // endY
                        } else {
                            otherX = currentCoords[0]; // startX
                            otherY = currentCoords[1]; // startY
                        }

                        // Проверяем, будут ли обе точки в границах после перемещения
                        if (handle == startHandle) {
                            if (areBothPointsInBounds(paneX, paneY, otherX, otherY)) {
                                double newRelX = paneX - getLayoutX();
                                double newRelY = paneY - getLayoutY();
                                line.setStartX(newRelX);
                                line.setStartY(newRelY);
                                updateResizeHandles();
                            }
                        } else {
                            if (areBothPointsInBounds(otherX, otherY, paneX, paneY)) {
                                double newRelX = paneX - getLayoutX();
                                double newRelY = paneY - getLayoutY();
                                line.setEndX(newRelX);
                                line.setEndY(newRelY);
                                updateResizeHandles();
                            }
                        }

                        event.consume();
                    });

                    handle.setOnMouseReleased(event -> event.consume());
                }
            }
        }
    }

    private void setupHitLineEvents() {
        hitLine.setOnMousePressed(event -> {
            Point2D mousePos = pane.sceneToLocal(event.getSceneX(), event.getSceneY());
            initializeDrag(mousePos);

            if (shapeManager == null || !shapeManager.isSelectToolActive()) {
                applyDefaultStyle();
                hideResizeHandles();
            } else {
                if (onSelectCallback != null) {
                    onSelectCallback.accept(this);
                }
            }
            isDragging = false;
            event.consume();
        });

        hitLine.setOnMouseDragged(event -> {
            isDragging = true;
            Point2D scenePos = new Point2D(event.getSceneX(), event.getSceneY());
            Point2D panePos = pane.sceneToLocal(scenePos);

            // Используем базовую логику перемещения
            double currentZoom = 1.0;
            double adjustedX = panePos.getX() / currentZoom;
            double adjustedY = panePos.getY() / currentZoom;

            double newX = adjustedX - dragOffsetX;
            double newY = adjustedY - dragOffsetY;

            // Получаем абсолютные координаты после перемещения
            double newStartXAbs = newX + line.getStartX();
            double newStartYAbs = newY + line.getStartY();
            double newEndXAbs = newX + line.getEndX();
            double newEndYAbs = newY + line.getEndY();

            // Применяем перемещение только если обе точки остаются в границах
            if (areBothPointsInBounds(newStartXAbs, newStartYAbs, newEndXAbs, newEndYAbs)) {
                newX = calculateNewPositionX(adjustedX);
                newY = calculateNewPositionY(adjustedY);
                setPosition(newX, newY);
                updateResizeHandles();
            }

            event.consume();
        });

        hitLine.setOnMouseReleased(event -> {
            if (isDragging && statusSetter != null) {
                statusSetter.accept("Позиция линии изменена");
            }
            isDragging = false;
            updateResizeHandles();
            event.consume();
        });
    }

    @Override
    protected void applyCurrentStyle() {
        line.setStroke(strokeColor);
        line.setStrokeWidth(defaultStrokeWidth);
    }

    @Override
    protected void applySelectedStyle() {
        line.setStroke(Color.BLUE);
        line.setStrokeWidth(defaultStrokeWidth);
    }

    @Override
    protected void applyDefaultStyle() {
        applyCurrentStyle();
    }

    @Override
    protected String getShapeType() {
        return "LINE";
    }

    @Override
    public void highlightAsSelected() {
        applySelectedStyle();
        makeResizeHandlesVisible();
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
    }

    @Override
    public void makeResizeHandlesVisible() {
        if (shapeManager != null && !shapeManager.isSelectToolActive()) {
            hideResizeHandles();
            return;
        }

        if (startHandle == null || endHandle == null) {
            createResizeHandles();
        }

        if (startHandle != null) startHandle.setVisible(true);
        if (endHandle != null) endHandle.setVisible(true);
        updateResizeHandles();
    }

    @Override
    public String serialize() {
        double[] absCoords = getAbsoluteCoordinates();
        return String.format(java.util.Locale.US, "%s|%.2f|%.2f|%.2f|%.2f%s",
                getShapeType(), absCoords[0], absCoords[1], absCoords[2], absCoords[3], serializeColors());
    }

    @Override
    protected double getCurrentWidth() {
        return Math.abs(line.getEndX() - line.getStartX());
    }

    @Override
    protected double getCurrentHeight() {
        return Math.abs(line.getEndY() - line.getStartY());
    }

    @Override
    protected void setCurrentDimensions(double width, double height) {
        // Для линии не нужно сохранять dimensions
    }
}