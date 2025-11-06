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
    private Circle snapHighlight;
    private static final double SNAP_HIGHLIGHT_RADIUS = 5.0;

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
        // Удаляем индикатор фиксации
        removeSnapHighlight();
    }

    private void setupLineHandleEvents() {
        if (shapeManager != null) {
            for (Circle handle : new Circle[]{startHandle, endHandle}) {
                if (handle != null) {
                    // Используем массив для обхода ограничения final
                    final double[][] initialCoordsHolder = new double[1][];

                    handle.setOnMousePressed(event -> {
                        double handleX = getLayoutX() + (handle == startHandle ? line.getStartX() : line.getEndX());
                        double handleY = getLayoutY() + (handle == startHandle ? line.getStartY() : line.getEndY());
                        initialX = handleX;
                        initialY = handleY;

                        // Сохраняем начальные координаты линии для undo
                        initialCoordsHolder[0] = getAbsoluteCoordinates();
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

                        // ПРИМЕНЯЕМ ФИКСАЦИЮ К НОВЫМ КООРДИНАТАМ
                        double[] snappedCoords = applyLineSnap(paneX, paneY, otherX, otherY, handle == startHandle);
                        double snappedX = snappedCoords[0];
                        double snappedY = snappedCoords[1];

                        // Проверяем, будут ли обе точки в границах после перемещения
                        if (handle == startHandle) {
                            if (areBothPointsInBounds(snappedX, snappedY, otherX, otherY)) {
                                double newRelX = snappedX - getLayoutX();
                                double newRelY = snappedY - getLayoutY();
                                line.setStartX(newRelX);
                                line.setStartY(newRelY);
                                updateResizeHandles();

                                // Показываем индикатор фиксации если применили
                                if (snappedCoords[2] == 1) { // horizontal snap
                                    showSnapHighlight(snappedX, snappedY);
                                } else if (snappedCoords[2] == 2) { // vertical snap
                                    showSnapHighlight(snappedX, snappedY);
                                } else {
                                    hideSnapHighlight();
                                }
                            }
                        } else {
                            if (areBothPointsInBounds(otherX, otherY, snappedX, snappedY)) {
                                double newRelX = snappedX - getLayoutX();
                                double newRelY = snappedY - getLayoutY();
                                line.setEndX(newRelX);
                                line.setEndY(newRelY);
                                updateResizeHandles();

                                // Показываем индикатор фиксации если применили
                                if (snappedCoords[2] == 1) { // horizontal snap
                                    showSnapHighlight(snappedX, snappedY);
                                } else if (snappedCoords[2] == 2) { // vertical snap
                                    showSnapHighlight(snappedX, snappedY);
                                } else {
                                    hideSnapHighlight();
                                }
                            }
                        }

                        event.consume();
                    });

                    handle.setOnMouseReleased(event -> {
                        // Скрываем индикатор фиксации при отпускании
                        hideSnapHighlight();

                        // РЕГИСТРИРУЕМ ИЗМЕНЕНИЕ КОНЕЧНЫХ ТОЧЕК ЛИНИИ В UNDO/REDO
                        if (initialCoordsHolder[0] != null) {
                            double[] finalCoords = getAbsoluteCoordinates();

                            // Проверяем, было ли реальное изменение
                            if (hasLineChanged(initialCoordsHolder[0], finalCoords)) {
                                shapeManager.registerLinePointsChange(
                                        this,
                                        initialCoordsHolder[0][0], initialCoordsHolder[0][1], // old start X, Y
                                        initialCoordsHolder[0][2], initialCoordsHolder[0][3], // old end X, Y
                                        finalCoords[0], finalCoords[1],     // new start X, Y
                                        finalCoords[2], finalCoords[3]      // new end X, Y
                                );

                                // АВТОСОХРАНЕНИЕ
                                if (shapeManager.getOnShapeAdded() != null) {
                                    shapeManager.getOnShapeAdded().run();
                                }
                            }
                        }
                        event.consume();
                    });
                }
            }
        }
    }

    /**
     * Показывает индикатор фиксации
     */
    private void showSnapHighlight(double x, double y) {
        if (snapHighlight == null) {
            snapHighlight = new Circle(SNAP_HIGHLIGHT_RADIUS, Color.RED);
            pane.getChildren().add(snapHighlight);
        }
        snapHighlight.setCenterX(x);
        snapHighlight.setCenterY(y);
        snapHighlight.setVisible(true);
    }

    /**
     * Скрывает индикатор фиксации
     */
    private void hideSnapHighlight() {
        if (snapHighlight != null) {
            snapHighlight.setVisible(false);
        }
    }

    /**
     * Удаляет индикатор фиксации
     */
    private void removeSnapHighlight() {
        if (snapHighlight != null) {
            pane.getChildren().remove(snapHighlight);
            snapHighlight = null;
        }
    }

    /**
     * Применяет фиксацию линии к осям при приближении
     * Возвращает [x, y, snapType] где snapType: 0=нет, 1=горизонталь, 2=вертикаль
     */
    private double[] applyLineSnap(double movingX, double movingY, double fixedX, double fixedY, boolean isStartHandle) {
        double snapThreshold = 15.0;
        double angleSnapThreshold = 10.0; // градусы

        // Вычисляем текущий угол линии
        double currentAngle = Math.toDegrees(Math.atan2(movingY - fixedY, movingX - fixedX));

        // Фиксация к основным углам (0°, 45°, 90°, 135°, 180°, 225°, 270°, 315°)
        double[] snapAngles = {0, 45, 90, 135, 180, 225, 270, 315};
        double snappedAngle = currentAngle;

        for (double snapAngle : snapAngles) {
            double angleDiff = Math.abs(((currentAngle - snapAngle) + 180) % 360 - 180);
            if (angleDiff < angleSnapThreshold) {
                snappedAngle = snapAngle;
                break;
            }
        }

        // Если нашли подходящий угол, применяем фиксацию
        if (snappedAngle != currentAngle) {
            double distance = Math.sqrt(Math.pow(movingX - fixedX, 2) + Math.pow(movingY - fixedY, 2));
            double angleRad = Math.toRadians(snappedAngle);

            double resultX = fixedX + distance * Math.cos(angleRad);
            double resultY = fixedY + distance * Math.sin(angleRad);

            // Определяем тип фиксации
            int snapType = (snappedAngle % 90 == 0) ?
                    (snappedAngle % 180 == 0 ? 1 : 2) : 3; // 1=horizontal, 2=vertical, 3=diagonal

            return new double[]{resultX, resultY, snapType};
        }

        // Старая логика фиксации к осям (оставляем для обратной совместимости)
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

    /**
     * Проверяет, изменились ли координаты линии
     */
    private boolean hasLineChanged(double[] initial, double[] current) {
        return Math.abs(initial[0] - current[0]) > 0.1 ||
                Math.abs(initial[1] - current[1]) > 0.1 ||
                Math.abs(initial[2] - current[2]) > 0.1 ||
                Math.abs(initial[3] - current[3]) > 0.1;
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

                // РЕГИСТРИРУЕМ ПЕРЕМЕЩЕНИЕ ЛИНИИ В UNDO/REDO
                double[] currentCoords = getAbsoluteCoordinates();
                double currentX = getLayoutX();
                double currentY = getLayoutY();

                // Используем сохраненные начальные координаты из initializeDrag
                double oldX = getDragStartX();
                double oldY = getDragStartY();

                if (shapeManager != null) {
                    // Для линии перемещение - это изменение всех координат
                    double[] oldCoords = new double[]{
                            oldX + line.getStartX(), oldY + line.getStartY(),
                            oldX + line.getEndX(), oldY + line.getEndY()
                    };

                    shapeManager.registerLinePointsChange(
                            this,
                            oldCoords[0], oldCoords[1], oldCoords[2], oldCoords[3],
                            currentCoords[0], currentCoords[1], currentCoords[2], currentCoords[3]
                    );
                    // ДОБАВИТЬ АВТОСОХРАНЕНИЕ ПРИ ПЕРЕМЕЩЕНИИ ЛИНИИ
                    if (shapeManager.getOnShapeAdded() != null) {
                        shapeManager.getOnShapeAdded().run();
                    }
                }
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
        // Скрываем индикатор фиксации
        hideSnapHighlight();
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

//    @Override
//    public String serialize() {
//        double[] absCoords = getAbsoluteCoordinates();
//        return String.format(java.util.Locale.US, "%s|%.2f|%.2f|%.2f|%.2f|%.1f%s", // ДОБАВЛЕН |%.1f для угла
//                getShapeType(), absCoords[0], absCoords[1], absCoords[2], absCoords[3],
//                rotationAngle, serializeColors());
//    }

    @Override
    protected Point2D getCenterInPane() {
        double startXAbs = getLayoutX() + line.getStartX();
        double startYAbs = getLayoutY() + line.getStartY();
        double endXAbs = getLayoutX() + line.getEndX();
        double endYAbs = getLayoutY() + line.getEndY();

        return new Point2D(
                (startXAbs + endXAbs) / 2,
                (startYAbs + endYAbs) / 2
        );
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

    /**
     * Устанавливает абсолютные координаты линии (для использования в командах undo/redo)
     */
    public void setLinePoints(double startX, double startY, double endX, double endY) {
        // Приводим координаты к допустимым границам
        startX = clampX(startX);
        startY = clampY(startY);
        endX = clampX(endX);
        endY = clampY(endY);

        // Устанавливаем относительные координаты
        line.setStartX(0);
        line.setStartY(0);
        line.setEndX(endX - startX);
        line.setEndY(endY - startY);

        // Устанавливаем позицию группы
        setLayoutX(startX);
        setLayoutY(startY);

        // Обновляем handles
        updateResizeHandles();
    }
}