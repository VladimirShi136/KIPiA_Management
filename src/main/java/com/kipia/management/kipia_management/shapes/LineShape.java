package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ClipboardManager;
import com.kipia.management.kipia_management.managers.ShapeManager;
import com.kipia.management.kipia_management.services.ShapeService;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
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
    private static final double HIT_THRESHOLD = 20.0; // Порог захвата в пикселях

    // Границы красного квадрата

    public LineShape(double startX, double startY, double endX, double endY,
                     AnchorPane pane, Consumer<String> statusSetter,
                     Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        super(pane, statusSetter, onSelectCallback, shapeManager);

        // УБИРАЕМ ВСЕ ПРОВЕРКИ И ОГРАНИЧЕНИЯ
        line = new Line(0, 0, endX - startX, endY - startY);
        Color defaultStroke = Color.BLACK;
        line.setStroke(defaultStroke);
        line.setStrokeWidth(defaultStrokeWidth);

        getChildren().clear();
        getChildren().add(line);

        // Устанавливаем позицию без ограничений
        setLayoutX(startX);
        setLayoutY(startY);

        createLineHandles();
        setCurrentDimensions(Math.abs(endX - startX), Math.abs(endY - startY));
        setupLineEventHandlers();
    }

    /**
     * Переопределяем contains для точного определения попадания на линию
     * Это ЕДИНСТВЕННЫЙ метод, который нужно переопределить для hit detection
     */
    @Override
    public boolean contains(double localX, double localY) {
        return containsPoint(localX, localY);
    }

    @Override
    public boolean contains(Point2D localPoint) {
        return containsPoint(localPoint.getX(), localPoint.getY());
    }

    /**
     * Получает абсолютные координаты обеих точек линии
     */
    protected double[] getAbsoluteCoordinates() {
        double startXAbs = getLayoutX() + line.getStartX();
        double startYAbs = getLayoutY() + line.getStartY();
        double endXAbs = getLayoutX() + line.getEndX();
        double endYAbs = getLayoutY() + line.getEndY();  // ← ИСПРАВИТЬ НА getEndY()
        return new double[]{startXAbs, startYAbs, endXAbs, endYAbs};
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
    protected String getMoveStatusMessage() {
        return "Позиция линии изменена";
    }

    @Override
    protected String getResizeStatusMessage() {
        return "Размер линии изменен";
    }

    @Override
    protected void resizeShape(double width, double height) {
        // For line: ignore, resize через handles
    }

    private void createLineHandles() {
        // УДАЛЯЕМ СТАРЫЕ HANDLES ЕСЛИ ЕСТЬ
        if (startHandle != null) {
            pane.getChildren().remove(startHandle);
            startHandle = null;
        }
        if (endHandle != null) {
            pane.getChildren().remove(endHandle);
            endHandle = null;
        }

        // СОЗДАЕМ НОВЫЕ HANDLES
        startHandle = new Circle(HANDLE_RADIUS, Color.CORAL);
        startHandle.setCursor(Cursor.CROSSHAIR);
        startHandle.setVisible(false);
        pane.getChildren().add(startHandle);

        endHandle = new Circle(HANDLE_RADIUS, Color.CORAL);
        endHandle.setCursor(Cursor.CROSSHAIR);
        endHandle.setVisible(false);
        pane.getChildren().add(endHandle);

        // ВАЖНО: НАСТРАИВАЕМ ОБРАБОТЧИКИ СРАЗУ
        setupLineHandleEvents();
        updateResizeHandles();
    }

    @Override
    public void createResizeHandles() {
        createLineHandles();
    }

    @Override
    public void updateResizeHandles() {
        if (startHandle != null) {
            double startXAbs = getLayoutX() + line.getStartX();
            double startYAbs = getLayoutY() + line.getStartY();
            double endXAbs = getLayoutX() + line.getEndX();
            double endYAbs = getLayoutY() + line.getEndY();

            startHandle.setCenterX(startXAbs);
            startHandle.setCenterY(startYAbs);
            endHandle.setCenterX(endXAbs);
            endHandle.setCenterY(endYAbs);
        }
    }

    /**
     * Настройка обработчиков событий для основной линии
     */
    private void setupLineEventHandlers() {
        line.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                // Используем родительскую логику инициализации перетаскивания
                Point2D mousePos = pane.sceneToLocal(event.getSceneX(), event.getSceneY());
                initializeDrag(mousePos);

                // Выделяем линию при клике
                if (onSelectCallback != null) {
                    onSelectCallback.accept(this);
                }
                // Показываем handles при одинарном клике
                makeResizeHandlesVisible();

                event.consume();
            }
        });

        // Двойной клик на линии
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
     * Проверяет, находится ли точка рядом с линией
     * Теперь координаты точки уже преобразованы в рабочую область
     */
    public boolean containsPoint(double workspaceX, double workspaceY) {
        try {
            // ПРОСТАЯ проверка без коррекции зума
            double[] absCoords = getAbsoluteCoordinates();
            double x1 = absCoords[0], y1 = absCoords[1];
            double x2 = absCoords[2], y2 = absCoords[3];

            double distance = distanceToLine(workspaceX, workspaceY, x1, y1, x2, y2);
            double effectiveThreshold = HIT_THRESHOLD + (line.getStrokeWidth() / 2);

            return distance <= effectiveThreshold;
        } catch (Exception e) {
            return false;
        }
    }

    // УБИРАЕМ все проверки границ из методов drag

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

        // Обрабатываем случай нулевой длины линии
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
        // Удаляем индикатор фиксации
        removeSnapHighlight();
        removeSnapIndicator();
    }

    private void setupLineHandleEvents() {
        if (shapeManager != null) {
            for (Circle handle : new Circle[]{startHandle, endHandle}) {
                if (handle != null) {
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
                        double paneX = panePoint.getX();
                        double paneY = panePoint.getY();

                        double[] currentCoords = getAbsoluteCoordinates();
                        double otherX, otherY;

                        if (handle == startHandle) {
                            otherX = currentCoords[2];
                            otherY = currentCoords[3];
                        } else {
                            otherX = currentCoords[0];
                            otherY = currentCoords[1];
                        }

                        // ИСПОЛЬЗУЕМ НОВЫЙ МЕТОД С ПРИВЯЗКОЙ К ФИГУРАМ
                        double[] snappedCoords = applyLineSnapWithShapes(paneX, paneY, otherX, otherY);
                        double snappedX = snappedCoords[0];
                        double snappedY = snappedCoords[1];

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
                                // РЕГИСТРИРУЕМ ИЗМЕНЕНИЕ В UNDO/REDO
                                shapeManager.registerLinePointsChange(
                                        this,
                                        initialCoordsHolder[0][0], initialCoordsHolder[0][1],
                                        initialCoordsHolder[0][2], initialCoordsHolder[0][3],
                                        finalCoords[0], finalCoords[1],
                                        finalCoords[2], finalCoords[3]
                                );

                                // ДОБАВЬТЕ СТАТУС ДЛЯ ЛИНИИ
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
     */
    private double[] applyLineSnap(double movingX, double movingY, double fixedX, double fixedY) {
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

        // Старая логика фиксации к осям
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
        // Скрываем индикатор фиксации
        hideSnapHighlight();
    }

    // Привязка к фигурам

    /**
     * Поиск ближайшей точки привязки среди всех фигур
     */
    private Point2D findNearestSnapPoint(double x, double y) {
        if (shapeManager == null) return null;

        Point2D bestSnap = null;
        double minDistance = Double.MAX_VALUE;

        // Получаем все фигуры из ShapeService
        List<ShapeBase> allShapes = getAllOtherShapes();

        for (ShapeBase shape : allShapes) {
            if (shape == this) continue; // Пропускаем саму себя

            List<Point2D> snapPoints = shape.getSnapPoints();
            for (Point2D point : snapPoints) {
                double distance = point.distance(x, y);
                if (distance < SNAP_THRESHOLD && distance < minDistance) {
                    minDistance = distance;
                    bestSnap = point;
                }
            }
        }

        // Также проверяем привязку к другим линиям
        Point2D lineSnap = findNearestLineSnapPoint(x, y);
        if (lineSnap != null && lineSnap.distance(x, y) < minDistance) {
            bestSnap = lineSnap;
        }

        return bestSnap;
    }

    /**
     * Поиск точек привязки на других линиях
     */
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

    /**
     * Получение всех других фигур на панели через ShapeService
     */
    private List<ShapeBase> getAllOtherShapes() {
        List<ShapeBase> shapes = new ArrayList<>();

        // Получаем доступ к ShapeService через ShapeManager
        if (shapeManager != null) {
            // Используем рефлексию для доступа к ShapeService
            try {
                java.lang.reflect.Field shapeServiceField = shapeManager.getClass().getDeclaredField("shapeService");
                shapeServiceField.setAccessible(true);
                ShapeService shapeService = (ShapeService) shapeServiceField.get(shapeManager);

                if (shapeService != null) {
                    // Получаем список фигур из ShapeService
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
                // Fallback: собираем фигуры из панели
                return getShapesFromPane();
            }
        }

        return shapes;
    }

    /**
     * Fallback метод: сбор фигур из панели
     */
    private List<ShapeBase> getShapesFromPane() {
        List<ShapeBase> shapes = new ArrayList<>();
        for (Node node : pane.getChildren()) {
            if (node instanceof ShapeBase shape && shape != this) {
                shapes.add(shape);
            }
        }
        return shapes;
    }

    /**
     * Показывает индикатор привязки
     */
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

    /**
     * Возвращает цвет индикатора в зависимости от типа привязки
     */
    private Color getSnapColor(int snapType) {
        return switch (snapType) {
            case 1 -> Color.RED;    // Горизонталь
            case 2 -> Color.BLUE;   // Вертикаль
            case 3 -> Color.GREEN;  // Диагональ
            case 4 -> Color.PURPLE; // Привязка к фигуре
            default -> Color.RED;
        };
    }

    /**
     * Скрывает индикатор привязки
     */
    private void hideSnapIndicator() {
        if (snapIndicator != null) {
            snapIndicator.setVisible(false);
        }
    }

    /**
     * Улучшенный метод для применения привязки при перемещении ручек
     */
    private double[] applyLineSnapWithShapes(double movingX, double movingY, double fixedX, double fixedY) {
        // Сначала проверяем привязку к другим фигурам
        Point2D snapPoint = findNearestSnapPoint(movingX, movingY);

        if (snapPoint != null) {
            showSnapIndicator(snapPoint.getX(), snapPoint.getY(), 4);
            return new double[]{snapPoint.getX(), snapPoint.getY(), 4}; // тип 4 = привязка к фигуре
        } else {
            hideSnapIndicator();
        }

        // Если привязки к фигурам нет, применяем старую логику привязки к осям
        return applyLineSnap(movingX, movingY, fixedX, fixedY);
    }

    /**
     * Удаляет индикатор привязки
     */
    private void removeSnapIndicator() {
        if (snapIndicator != null) {
            pane.getChildren().remove(snapIndicator);
            snapIndicator = null;
        }
    }

    @Override
    public void addContextMenu(Consumer<ShapeHandler> deleteAction) {
        ContextMenu lineContextMenu = new ContextMenu();

        // Пункт "Копировать"
        MenuItem copyItem = new MenuItem("Копировать");
        copyItem.setOnAction(_ -> {
            copyToClipboard();
            lineContextMenu.hide();
        });

        // Пункт "Вставить"
        MenuItem pasteItem = new MenuItem("Вставить");
        pasteItem.setOnAction(_ -> {
            pasteFromClipboard();
            lineContextMenu.hide();
        });
        pasteItem.disableProperty().bind(ClipboardManager.hasShapeDataProperty().not());

        // Пункт "Изменить цвет линии"
        MenuItem strokeColorItem = new MenuItem("Изменить цвет линии");
        strokeColorItem.setOnAction(_ -> {
            changeLineColor();
            lineContextMenu.hide();
        });

        SeparatorMenuItem separator = new SeparatorMenuItem();

        // Пункт "Удалить"
        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.setOnAction(_ -> {
            if (deleteAction != null) {
                deleteAction.accept(this);
            }
            lineContextMenu.hide();
        });

        lineContextMenu.getItems().addAll(copyItem, pasteItem, strokeColorItem, separator, deleteItem);

        // Настраиваем обработчик контекстного меню на линию
        line.setOnContextMenuRequested(event -> {
            lineContextMenu.show(line, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        // Также настраиваем на группу
        setOnContextMenuRequested(event -> {
            lineContextMenu.show(this, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    /**
     * Собственный метод для изменения цвета линии
     */
    private void changeLineColor() {
        javafx.scene.control.ColorPicker colorPicker = new javafx.scene.control.ColorPicker(strokeColor);

        Dialog<Color> dialog = new Dialog<>();
        dialog.setTitle("Изменение цвета линии");
        dialog.setHeaderText("Выберите цвет линии");

        ButtonType applyButton = new ButtonType("Применить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Цвет линии:"), 0, 0);
        grid.add(colorPicker, 1, 0);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == applyButton) {
                return colorPicker.getValue();
            }
            return null;
        });

        Optional<Color> result = dialog.showAndWait();
        result.ifPresent(color -> {
            setStrokeColor(color);
            if (statusSetter != null) {
                statusSetter.accept("Цвет линии изменен");
            }
        });
    }

    @Override
    public void makeResizeHandlesVisible() {
        // ВАЖНО: Всегда пересоздаем handles если их нет
        if (startHandle == null || endHandle == null) {
            removeResizeHandles(); // Очищаем старые если есть
            createLineHandles();
        }
        // ВАЖНО: Всегда перенастраиваем обработчики
        setupLineHandleEvents();
        if (startHandle != null) {
            startHandle.setVisible(true);
        }
        if (endHandle != null) {
            endHandle.setVisible(true);
        }
        updateResizeHandles();
    }

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
        // ВАЖНО: Правильно вычисляем относительные координаты
        double relStartX = 0;
        double relStartY = 0;
        double relEndX = endX - startX;
        double relEndY = endY - startY;

        line.setStartX(relStartX);
        line.setStartY(relStartY);
        line.setEndX(relEndX);
        line.setEndY(relEndY);

        // Устанавливаем позицию группы
        setLayoutX(startX);
        setLayoutY(startY);

        // Обновляем handles
        updateResizeHandles();
    }

    @Override
    public void setPosition(double x, double y) {
        setLayoutX(x);
        setLayoutY(y);
        // Принудительно обновляем handles если они есть
        if (startHandle != null && endHandle != null) {
            updateResizeHandles();
        }
    }

    /**
     * Переопределяем метод поворота - отключаем для линии
     */
    @Override
    public void setRotation(double angle) {
        // Линия не поддерживает поворот
    }

    /**
     * Переопределяем handleRotationInMenu для линии - отключаем поворот
     */
    @Override
    protected void handleRotationInMenu() {
        // Линия не поддерживает поворот через контекстное меню
        if (statusSetter != null) {
            statusSetter.accept("Поворот линии не поддерживается");
        }
    }

    /**
     * Переопределяем сериализацию для линии
     */
    @Override
    public String serialize() {
        double[] absCoords = getAbsoluteCoordinates();
        return String.format("LINE|%.2f|%.2f|%.2f|%.2f|%.1f%s",
                absCoords[0], absCoords[1], absCoords[2], absCoords[3], 0.0, serializeColors());
    }
}