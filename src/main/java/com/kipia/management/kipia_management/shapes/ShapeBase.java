package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.Cursor;
import javafx.geometry.Point2D;
import javafx.geometry.Bounds;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Абстрактный базовый класс для всех интерактивных фигур схемы.
 * Унифицирует логику drag, resize, handles, контекстного меню.
 * Подклассы реализуют drawShape, resizeShape, updateHandles.
 *
 * @author vladimir_shi
 * @since 22.10.2025
 */
public abstract class ShapeBase extends Group implements ShapeHandler {

    // ============================================================
    // CONSTANTS
    // ============================================================

    protected static final double MIN_SHAPE_SIZE = 20.0;
    protected static final double RESIZE_HANDLE_RADIUS = 6.0;
    protected static final int RESIZE_HANDLE_COUNT = 8;
    protected static final Color RESIZE_HANDLE_COLOR = Color.CORAL;
    // Добавляем поля для цветов
    protected Color strokeColor = Color.BLACK;
    protected Color fillColor = Color.TRANSPARENT;

    // ============================================================
    // DEPENDENCIES
    // ============================================================

    protected final AnchorPane pane;
    protected final Consumer<String> statusSetter;
    protected final Consumer<ShapeHandler> onSelectCallback;
    protected final ShapeManager shapeManager;  // Ссылка на менеджер для проверки инструмента


    // ============================================================
    // STATE MANAGEMENT
    // ============================================================

    protected Circle[] resizeHandles;
    protected double pressPaneX, pressPaneY;
    protected double initialX, initialY, initialWidth, initialHeight;
    protected boolean wasResizedInSession;
    protected double dragOffsetX, dragOffsetY;
    protected boolean isDragging;

    // ============================================================
    // STORED DIMENSIONS (для exact сериализации, без bounds rounding)
    // ============================================================

    private double currentWidth = -1.0;  // -1 = not set (initial)
    private double currentHeight = -1.0;
    private ContextMenu contextMenu; // Сохраняем ссылку на меню

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    /**
     * Конструктор базового класса фигуры
     *
     * @param pane             панель для отображения фигуры
     * @param statusSetter     колбэк для установки статуса
     * @param onSelectCallback колбэк при выборе фигуры
     */
    public ShapeBase(AnchorPane pane,
                     Consumer<String> statusSetter,
                     Consumer<ShapeHandler> onSelectCallback,
                     ShapeManager shapeManager) {
        this.pane = pane;
        this.statusSetter = statusSetter;
        this.onSelectCallback = onSelectCallback;
        this.shapeManager = shapeManager;
        initializeState();
        setupEventHandlers();
        setupContextMenu(); // Создаем контекстное меню по умолчанию
    }

    /**
     * Инициализация состояния фигуры
     */
    private void initializeState() {
        this.resizeHandles = null;
        this.wasResizedInSession = false;
        this.isDragging = false;
    }

    // ============================================================
    // ABSTRACT METHODS (для реализации в подклассах)
    // ============================================================

    /**
     * Изменение размера фигуры
     *
     * @param newWidth  новая ширина
     * @param newHeight новая высота
     */
    protected abstract void resizeShape(double newWidth, double newHeight);

    /**
     * Применение стиля выделенной фигуры
     */
    protected abstract void applySelectedStyle();

    /**
     * Применение стандартного стиля фигуры
     */
    protected abstract void applyDefaultStyle();

    /**
     * Получение типа фигуры для сериализации
     *
     * @return строковый идентификатор типа фигуры
     */
    protected abstract String getShapeType();

    // ============================================================
    // RESIZE HANDLES MANAGEMENT
    // ============================================================

    /**
     * Создание ручек изменения размера
     */
    @Override
    public void createResizeHandles() {
        if (resizeHandles != null) return;
        resizeHandles = new Circle[RESIZE_HANDLE_COUNT];
        for (int i = 0; i < RESIZE_HANDLE_COUNT; i++) {
            resizeHandles[i] = createResizeHandle();
            pane.getChildren().add(resizeHandles[i]);
            resizeHandles[i].setVisible(false); // НЕ показываем сразу!
        }
    }

    /**
     * Создание одной ручки изменения размера
     */
    protected Circle createResizeHandle() {
        Circle handle = new Circle(0, 0, RESIZE_HANDLE_RADIUS, RESIZE_HANDLE_COLOR);
        handle.setCursor(Cursor.CROSSHAIR);
        handle.setVisible(false);
        return handle;
    }

    /**
     * Получение максимального X относительно позиции фигуры
     */
    protected abstract double getMaxRelativeX();

    /**
     * Получение максимального Y относительно позиции фигуры
     */
    protected abstract double getMaxRelativeY();

    /**
     * Обновление позиций ручек изменения размера
     */
    @Override
    public void updateResizeHandles() {
        if (resizeHandles == null) return;

        Bounds bounds = getBoundsInParent();
        HandlePositions positions = calculateHandlePositions(bounds);
        applyHandlePositions(positions);
    }

    /**
     * Расчет позиций для ручек изменения размера
     */
    private HandlePositions calculateHandlePositions(Bounds bounds) {
        double x0 = bounds.getMinX();
        double y0 = bounds.getMinY();
        double width = bounds.getWidth();
        double height = bounds.getHeight();

        return new HandlePositions(x0, y0, width, height);
    }

    /**
     * Применение рассчитанных позиций к ручкам
     */
    private void applyHandlePositions(HandlePositions positions) {
        int index = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i == 1 && j == 1) continue; // Пропускаем центральную точку
                if (index < resizeHandles.length) {
                    resizeHandles[index].setCenterX(positions.xs[i]);
                    resizeHandles[index].setCenterY(positions.ys[j]);
                    resizeHandles[index].setVisible(true);
                }
                index++;
            }
        }
    }

    /**
     * Вспомогательный класс для хранения позиций ручек
     */
    private static class HandlePositions {
        final double[] xs;
        final double[] ys;

        HandlePositions(double x0, double y0, double width, double height) {
            this.xs = new double[]{x0, x0 + width / 2, x0 + width};
            this.ys = new double[]{y0, y0 + height / 2, y0 + height};
        }
    }

    /**
     * Удаление ручек изменения размера
     */
    @Override
    public void removeResizeHandles() {
        if (resizeHandles == null) return;
        for (Circle handle : resizeHandles) {
            if (handle != null) {
                // Очищаем события перед удалением (чтобы не висели в памяти/JavaFX)
                handle.setOnMousePressed(null);
                handle.setOnMouseDragged(null);
                handle.setOnMouseReleased(null);
                pane.getChildren().remove(handle);
            }
        }
        resizeHandles = null;
        wasResizedInSession = false;
    }

    /**
     * Показ ручек изменения размера
     */
    @Override
    public void makeResizeHandlesVisible() {
        // Проверяем, что инструмент SELECT активен И фигура выделена
        if (shapeManager != null && !shapeManager.isSelectToolActive()) {
            hideResizeHandles();
            return;
        }

        if (resizeHandles == null) {
            createResizeHandles();
        }

        setupResizeHandleHandlers();

        if (resizeHandles != null) {
            for (Circle handle : resizeHandles) {
                if (handle != null) {
                    handle.setVisible(true);
                }
            }
        }
        updateResizeHandles();
    }

    // ============================================================
    // EVENT HANDLERS SETUP
    // ============================================================

    /**
     * Настройка обработчиков событий
     */
    private void setupEventHandlers() {
        setupDragHandlers();
    }

    /**
     * Настройка обработчиков перетаскивания
     */
    private void setupDragHandlers() {
        setOnMousePressed(this::handleDragPressed);
        setOnMouseDragged(this::handleDragDragged);
        setOnMouseReleased(this::handleDragReleased);
    }

    /**
     * Настройка обработчиков для ручек изменения размера
     */
    private void setupResizeHandleHandlers() {
        if (resizeHandles == null || resizeHandles.length == 0) {
            // Защита: если handles не созданы, ничего не делаем
            return;
        }

        for (int i = 0; i < resizeHandles.length; i++) {
            Circle handle = resizeHandles[i];
            if (handle != null) {
                setupResizeHandleHandler(handle, i);  // Привязка для каждой ручки
            } else {
                System.err.println("Warning: Handle at index " + i + " is null!");  // Debug, удали позже
            }
        }
    }

    /**
     * Настройка обработчика для конкретной ручки
     */
    private void setupResizeHandleHandler(Circle handle, int handleIndex) {
        // Очищаем возможные старые события (на всякий случай)
        handle.setOnMousePressed(null);
        handle.setOnMouseDragged(null);
        handle.setOnMouseReleased(null);

        // Новые события с проверкой SELECT внутри (дополнительная защита)
        handle.setOnMousePressed(event -> {
            // Если не SELECT — игнорируем (handles должны быть скрыты, но на всякий)
            if (shapeManager != null && !shapeManager.isSelectToolActive()) {
                event.consume();  // Consume, чтобы не протекало
                return;
            }
            Point2D pressPoint = pane.sceneToLocal(event.getSceneX(), event.getSceneY());
            initResize(pressPoint.getX(), pressPoint.getY());
            event.consume();
        });

        handle.setOnMouseDragged(event -> {
            // Проверка SELECT при драге
            if (shapeManager != null && !shapeManager.isSelectToolActive()) {
                event.consume();  // Consume для безопасности
                return;
            }
            Point2D currentPoint = pane.sceneToLocal(event.getSceneX(), event.getSceneY());
            handleResizeDrag(handleIndex, currentPoint.getX(), currentPoint.getY());
            event.consume();
        });

        handle.setOnMouseReleased(event -> {
            handleResizeRelease();
            event.consume();
        });
    }

    // ============================================================
    // DRAG HANDLING
    // ============================================================

    /**
     * Обработка начала перетаскивания
     */
    private void handleDragPressed(MouseEvent event) {
        Point2D mousePos = pane.sceneToLocal(event.getSceneX(), event.getSceneY());
        initializeDrag(mousePos);

        // Если инструмент НЕ SELECT: сбрасываем выделение и handles для этой фигуры
        if (shapeManager == null || !shapeManager.isSelectToolActive()) {
            // Явно сбрасываем стиль и handles (чтобы не "застревали")
            applyDefaultStyle();  // Снимаем синий (если был)
            hideResizeHandles();  // Скрываем handles
            // Опционально: удаляем handles из pane, если они созданы (полный сброс)
            if (resizeHandles != null) {
                for (Circle handle : resizeHandles) {
                    if (handle != null) {
                        pane.getChildren().remove(handle);  // Удаляем из pane (не только hide)
                    }
                }
                resizeHandles = null;  // Очищаем массив
            }
            // НЕ вызываем onSelectCallback — фигура не выделяется
        } else {
            // Только при SELECT: выделяем фигуру (показываем handles)
            if (onSelectCallback != null) {
                onSelectCallback.accept(this);
            }
        }

        isDragging = false;
        event.consume();
    }

    /**
     * Инициализация параметров перетаскивания
     */
    protected void initializeDrag(Point2D mousePos) {
        dragOffsetX = mousePos.getX() - getLayoutX();
        dragOffsetY = mousePos.getY() - getLayoutY();
    }

    /**
     * Обработка перетаскивания фигуры
     */
    private void handleDragDragged(MouseEvent event) {
        isDragging = true;
        Point2D scenePos = new Point2D(event.getSceneX(), event.getSceneY());
        Point2D panePos = pane.sceneToLocal(scenePos);

        double currentZoom = 1.0;
        double adjustedX = panePos.getX() / currentZoom;
        double adjustedY = panePos.getY() / currentZoom;

        // УНИВЕРСАЛЬНАЯ ЛОГИКА ДЛЯ ВСЕХ ФИГУР
        double newX = adjustedX - dragOffsetX;
        double newY = adjustedY - dragOffsetY;

        // Для линии проверяем обе точки
        if (this instanceof LineShape lineShape) {
            Line line = lineShape.getLine();

            // Вычисляем абсолютные координаты обеих точек после перемещения
            double newStartX = newX + line.getStartX();
            double newStartY = newY + line.getStartY();
            double newEndX = newX + line.getEndX();
            double newEndY = newY + line.getEndY();

            // Проверяем границы для обеих точек
            boolean startInBounds = newStartX >= 0 && newStartX <= 1600 && newStartY >= 0 && newStartY <= 1200;
            boolean endInBounds = newEndX >= 0 && newEndX <= 1600 && newEndY >= 0 && newEndY <= 1200;

            // Если обе точки остаются в пределах - перемещаем
            if (startInBounds && endInBounds) {
                setPosition(newX, newY);
            }
        } else {
            // Для остальных фигур - обычная логика
            newX = calculateNewPositionX(adjustedX);
            newY = calculateNewPositionY(adjustedY);
            setPosition(newX, newY);
        }

        updateResizeHandles();
        event.consume();
    }

    /**
     * Расчет новой позиции по X с учетом границ
     */
    protected double calculateNewPositionX(double mouseX) {
        double newX = mouseX - dragOffsetX;
        double paneW = 1600;
        double maxRelativeX = getMaxRelativeX();
        return Math.max(0, Math.min(newX, paneW - maxRelativeX));
    }

    /**
     * Расчет новой позиции по Y с учетом границ
     */
    protected double calculateNewPositionY(double mouseY) {
        double newY = mouseY - dragOffsetY;
        double paneH = 1200;
        double maxRelativeY = getMaxRelativeY();
        return Math.max(0, Math.min(newY, paneH - maxRelativeY));
    }

    /**
     * Обработка окончания перетаскивания
     */
    private void handleDragReleased(MouseEvent event) {
        if (isDragging && statusSetter != null) {
            statusSetter.accept("Позиция фигуры изменена");

            // ДЕБАГ: проверяем границы после перемещения
            if (this instanceof RhombusShape) {
                ((RhombusShape) this).debugBounds();
            }
        }
        isDragging = false;
        updateResizeHandles();
        event.consume();
    }

    // ============================================================
    // RESIZE HANDLING
    // ============================================================

    /**
     * Инициализация изменения размера
     */
    @Override
    public void initResize(double pressX, double pressY) {
        this.pressPaneX = pressX;
        this.pressPaneY = pressY;
        this.initialX = getLayoutX();
        this.initialY = getLayoutY();
        currentWidth = getCurrentWidth();
        currentHeight = getCurrentHeight();
        initialWidth = currentWidth;
        initialHeight = currentHeight;
        this.initialWidth = getBoundsInLocal().getWidth();
        this.initialHeight = getBoundsInLocal().getHeight();
        this.wasResizedInSession = false;
    }

    /**
     * Обработка перетаскивания при изменении размера
     */
    private void handleResizeDrag(int handleIndex, double currentX, double currentY) {
        if (shapeManager == null || !shapeManager.isSelectToolActive()) {
            return;  // Аналогично
        }

        double deltaX = currentX - pressPaneX;
        double deltaY = currentY - pressPaneY;

        if (Math.abs(deltaX) < 1 && Math.abs(deltaY) < 1) return;

        resizeByHandle(handleIndex, deltaX, deltaY);
    }

    /**
     * Обработка окончания изменения размера
     */
    private void handleResizeRelease() {
        if (wasResizedInSession && statusSetter != null) {
            statusSetter.accept("Размер фигуры изменен");
        }
    }

    /**
     * Изменение размера фигуры с помощью указанной ручки
     */
    @Override
    public void resizeByHandle(int handleIndex, double deltaX, double deltaY) {
        ResizeParams params = calculateResizeParams(handleIndex, deltaX, deltaY);
        applyResize(params);
        updateResizeHandles();
    }

    /**
     * Расчет параметров изменения размера
     */
    private ResizeParams calculateResizeParams(int handleIndex, double deltaX, double deltaY) {
        double newX = initialX;
        double newY = initialY;
        double newWidth = initialWidth;
        double newHeight = initialHeight;

        // Применяем изменения в зависимости от ручки
        switch (handleIndex) {
            case 0: // Верхний левый
                newX += deltaX;
                newY += deltaY;
                newWidth -= deltaX;
                newHeight -= deltaY;
                break;
            case 1: // Верхний средний
                newX += deltaX;
                newWidth -= deltaX;
                break;
            case 2: // Верхний правый
                newX += deltaX;
                newWidth -= deltaX;
                newHeight += deltaY;
                break;
            case 3: // Средний левый
                newY += deltaY;
                newHeight -= deltaY;
                break;
            case 4: // Средний правый
                newHeight += deltaY;
                break;
            case 5: // Нижний левый
                newY += deltaY;
                newWidth += deltaX;
                newHeight -= deltaY;
                break;
            case 6: // Нижний средний
                newWidth += deltaX;
                break;
            case 7: // Нижний правый
                newWidth += deltaX;
                newHeight += deltaY;
                break;
        }

        return new ResizeParams(newX, newY, newWidth, newHeight);
    }

    /**
     * Применение изменений размера
     */
    private void applyResize(ResizeParams params) {
        // Корректируем отрицательные размеры
        ResizeParams corrected = correctNegativeDimensions(params);

        // Проверяем минимальный размер
        corrected = enforceMinimumSize(corrected);

        // Проверяем, было ли реальное изменение
        wasResizedInSession = hasRealSizeChange(corrected);

        // Применяем изменения
        setLayoutX(corrected.x);
        setLayoutY(corrected.y);
        resizeShape(corrected.width, corrected.height);
    }

    /**
     * Коррекция отрицательных размеров
     */
    private ResizeParams correctNegativeDimensions(ResizeParams params) {
        double newX = params.x;
        double newY = params.y;
        double newWidth = params.width;
        double newHeight = params.height;

        if (newWidth < 0) {
            newWidth = Math.abs(newWidth);
            newX -= newWidth;
        }
        if (newHeight < 0) {
            newHeight = Math.abs(newHeight);
            newY -= newHeight;
        }

        return new ResizeParams(newX, newY, newWidth, newHeight);
    }

    /**
     * Проверка минимального размера
     */
    private ResizeParams enforceMinimumSize(ResizeParams params) {
        double newWidth = Math.max(MIN_SHAPE_SIZE, params.width);
        double newHeight = Math.max(MIN_SHAPE_SIZE, params.height);
        return new ResizeParams(params.x, params.y, newWidth, newHeight);
    }

    /**
     * Проверка реального изменения размера
     */
    private boolean hasRealSizeChange(ResizeParams params) {
        return params.width != initialWidth || params.height != initialHeight ||
                params.x != initialX || params.y != initialY;
    }

    /**
     * Вспомогательный класс для параметров изменения размера
     */
    private record ResizeParams(double x, double y, double width, double height) {
    }

    // ============================================================
    // CONTEXT MENU
    // ============================================================

    /**
     * Применяет текущие цвета к фигуре
     */
    protected abstract void applyCurrentStyle();

    /**
     * Настройка контекстного меню по умолчанию (без действия удаления)
     */
    private void setupContextMenu() {
        contextMenu = new ContextMenu();
        setOnContextMenuRequested(event -> {
            contextMenu.show(this, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    /**
     * Добавление контекстного меню к фигуре
     *
     * @param deleteAction действие при удалении фигуры
     */
    // Обновляем метод добавления контекстного меню
    @Override
    public void addContextMenu(Consumer<ShapeHandler> deleteAction) {
        if (contextMenu == null) {
            setupContextMenu();
        }

        contextMenu.getItems().clear();

        // Пункт "Изменить цвет контура"
        MenuItem strokeColorItem = new MenuItem("Изменить цвет контура");
        strokeColorItem.setOnAction(event -> changeStrokeColor());

        // Пункт "Изменить цвет заливки" (только для фигур с заливкой)
        MenuItem fillColorItem = new MenuItem("Изменить цвет заливки");
        fillColorItem.setOnAction(event -> changeFillColor());

        // Разделитель
        SeparatorMenuItem separator = new SeparatorMenuItem();

        // Пункт "Удалить"
        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.setOnAction(event -> {
            if (deleteAction != null) {
                deleteAction.accept(this);
            }
        });

        contextMenu.getItems().addAll(strokeColorItem, fillColorItem, separator, deleteItem);
    }

    // ============================================================
    // SELECTION HIGHLIGHTING
    // ============================================================

    /**
     * Подсветка фигуры как выделенной
     */
    @Override
    public void highlightAsSelected() {
        applySelectedStyle();
        makeResizeHandlesVisible(); // Только здесь показываем handles!
    }

    /**
     * Сброс подсветки выделения
     */
    @Override
    public void resetHighlight() {
        applyDefaultStyle();
        hideResizeHandles();
    }

    /**
     * Скрытие ручек изменения размера
     */
    private void hideResizeHandles() {
        if (resizeHandles != null) {
            for (Circle handle : resizeHandles) {
                if (handle != null) {
                    handle.setVisible(false);
                }
            }
        }
    }

    // ============================================================
    // POSITION MANAGEMENT
    // ============================================================

    /**
     * Установка позиции фигуры
     */
    @Override
    public void setPosition(double x, double y) {
        setLayoutX(x);
        setLayoutY(y);
    }

    /**
     * Получение позиции фигуры
     */
    @Override
    public double[] getPosition() {
        return new double[]{getLayoutX(), getLayoutY()};
    }

    /**
     * Расчет смещения от центра фигуры
     */
    @Override
    public Point2D getCenterOffset(double mouseX, double mouseY) {
        Bounds bounds = getBoundsInLocal();
        double centerX = getLayoutX() + bounds.getMinX() + bounds.getWidth() / 2;
        double centerY = getLayoutY() + bounds.getMinY() + bounds.getHeight() / 2;

        return new Point2D(mouseX - centerX, mouseY - centerY);
    }

    // ============================================================
    // STATE MANAGEMENT
    // ============================================================

    /**
     * Проверка, был ли изменен размер в текущей сессии
     */
    @Override
    public boolean wasResizedInSession() {
        return wasResizedInSession;
    }

    // ============================================================
    // PANE INTEGRATION
    // ============================================================

    /**
     * Добавление фигуры на панель
     */
    public void addToPane() {
        if (!pane.getChildren().contains(this)) {
            pane.getChildren().add(this);
        }
    }

    /**
     * Удаление фигуры с панели
     */
    public void removeFromPane() {
        if (pane != null) {
            pane.getChildren().remove(this);  // Remove группу
        }
    }

    // ============================================================
    // SERIALIZATION
    // ============================================================

    /**
     * Сериализация фигуры в строку
     */
    public String serialize() {
        double[] pos = getPosition();
        double w = getCurrentWidth();
        double h = getCurrentHeight();
        String type = getShapeType();
        if ("TEXT".equals(type)) {
            // Для TEXT: x|y|w|h|text (text вместо 5-го поля)
            String textContent = ((TextShape) this).getText();
            return String.format(java.util.Locale.US, "%s|%.2f|%.2f|%.2f|%.2f|%s", type, pos[0], pos[1], w, h, textContent.replace("|", ""));  // Escape | if needed
        }
        return String.format(java.util.Locale.US, "%s|%.2f|%.2f|%.2f|%.2f", type, pos[0], pos[1], w, h);
    }

    /**
     * Десериализация фигуры из строки
     */
    public static ShapeBase deserialize(String data, AnchorPane pane,
                                        Consumer<String> statusSetter,
                                        Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        if (data == null || data.trim().isEmpty()) return null;
        String[] parts = data.split("\\|");
        if (parts.length < 5) return null;

        try {
            String[] fixedParts = new String[parts.length];
            for (int i = 0; i < parts.length; i++) {
                fixedParts[i] = parts[i].replace(",", ".");
            }

            String type = fixedParts[0];
            double x = Double.parseDouble(fixedParts[1]);
            double y = Double.parseDouble(fixedParts[2]);
            double width = Double.parseDouble(fixedParts[3]);
            double height = Double.parseDouble(fixedParts[4]);

            ShapeBase shape = switch (type.toUpperCase()) {
                case "RECTANGLE" -> new RectangleShape(x, y, width, height, pane, statusSetter, onSelectCallback, shapeManager);
                case "LINE" -> {
                    double startX = Double.parseDouble(fixedParts[1]);
                    double startY = Double.parseDouble(fixedParts[2]);
                    double endX = Double.parseDouble(fixedParts[3]);
                    double endY = Double.parseDouble(fixedParts[4]);
                    LineShape lineShape = new LineShape(startX, startY, endX, endY, pane, statusSetter, onSelectCallback, shapeManager);
                    // Для линии цвета в индексах 5 и 6
                    if (fixedParts.length > 5) lineShape.deserializeColors(fixedParts, 5, 6);
                    yield lineShape;
                }
                case "ELLIPSE" -> {
                    EllipseShape ellipseShape = new EllipseShape(x + width/2, y + height/2, width/2, height/2,
                            pane, statusSetter, onSelectCallback, shapeManager);
                    // Для эллипса цвета в индексах 5 и 6
                    if (fixedParts.length > 5) ellipseShape.deserializeColors(fixedParts, 5, 6);
                    yield ellipseShape;
                }
                case "RHOMBUS" -> {
                    RhombusShape rhombusShape = new RhombusShape(x, y, width, height, pane, statusSetter, onSelectCallback, shapeManager);
                    // Для ромба цвета в индексах 5 и 6
                    if (fixedParts.length > 5) rhombusShape.deserializeColors(fixedParts, 5, 6);
                    yield rhombusShape;
                }
                case "TEXT" -> {
                    TextShape textShape = createTextShape(fixedParts, pane, statusSetter, onSelectCallback, shapeManager);
                    // Для текста цвета в последних двух индексах (после шрифта)
                    if (textShape != null && fixedParts.length > 9) {
                        textShape.deserializeColors(fixedParts, fixedParts.length - 2, fixedParts.length - 1);
                    }
                    yield textShape;
                }
                default -> null;
            };

            return shape;

        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Creates a TextShape from serialized parts during deserialization
     */
    private static TextShape createTextShape(String[] fixedParts, AnchorPane pane,
                                             Consumer<String> statusSetter,
                                             Consumer<ShapeHandler> onSelectCallback,
                                             ShapeManager shapeManager) {
        try {
            // Parse basic position and size (indices 1-4)
            double x = Double.parseDouble(fixedParts[1]);
            double y = Double.parseDouble(fixedParts[2]);
            double width = Double.parseDouble(fixedParts[3]);
            double height = Double.parseDouble(fixedParts[4]);

            // Get text content (index 5)
            String text = (fixedParts.length > 5) ? fixedParts[5] : "Новый текст";
            text = text.replace("\\|", "|"); // Unescape pipe characters

            // Create the TextShape
            TextShape textShape = new TextShape(x, y, text, pane, statusSetter, onSelectCallback, shapeManager);

            // Restore font properties if they exist (indices 6-8)
            if (fixedParts.length > 6) {
                try {
                    double fontSize = Double.parseDouble(fixedParts[6]);
                    fontSize = Math.max(8, Math.min(72, fontSize)); // Clamp font size

                    String fontFamily = fixedParts.length > 7 ? fixedParts[7] : "Arial";
                    String fontStyle = fixedParts.length > 8 ? fixedParts[8] : "Regular";

                    // Reconstruct font
                    FontWeight fontWeight = fontStyle.contains("Bold") ? FontWeight.BOLD : FontWeight.NORMAL;
                    FontPosture fontPosture = fontStyle.contains("Italic") ? FontPosture.ITALIC : FontPosture.REGULAR;

                    Font restoredFont = Font.font(fontFamily, fontWeight, fontPosture, fontSize);
                    textShape.setFont(restoredFont);

                } catch (NumberFormatException e) {
                    System.out.println("Ошибка восстановления шрифта, используется значение по умолчанию");
                }
            }

            // Recalculate text size with restored font
            javafx.application.Platform.runLater(textShape::calculateTextSize);

            return textShape;

        } catch (Exception e) {
            System.out.println("Ошибка создания TextShape: " + e.getMessage());
            return null;
        }
    }

    /**
     * Сериализует цвета в строку
     */
    protected String serializeColors() {
        return String.format(java.util.Locale.US, "|%.3f,%.3f,%.3f,%.3f|%.3f,%.3f,%.3f,%.3f",
                strokeColor.getRed(), strokeColor.getGreen(), strokeColor.getBlue(), strokeColor.getOpacity(),
                fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), fillColor.getOpacity());
    }

    /**
     * Десериализует цвета из строки
     */
    protected void deserializeColors(String[] parts, int strokeIndex, int fillIndex) {
        if (parts.length > strokeIndex) {
            String[] strokeParts = parts[strokeIndex].split(",");
            if (strokeParts.length == 4) {
                strokeColor = Color.color(
                        Double.parseDouble(strokeParts[0]),
                        Double.parseDouble(strokeParts[1]),
                        Double.parseDouble(strokeParts[2]),
                        Double.parseDouble(strokeParts[3])
                );
            }
        }
        if (parts.length > fillIndex) {
            String[] fillParts = parts[fillIndex].split(",");
            if (fillParts.length == 4) {
                fillColor = Color.color(
                        Double.parseDouble(fillParts[0]),
                        Double.parseDouble(fillParts[1]),
                        Double.parseDouble(fillParts[2]),
                        Double.parseDouble(fillParts[3])
                );
            }
        }
        applyCurrentStyle();
    }

    /**
     * Диалог изменения цвета контура
     */
    private void changeStrokeColor() {
        javafx.scene.control.ColorPicker colorPicker = new javafx.scene.control.ColorPicker(strokeColor);

        Dialog<Color> dialog = new Dialog<>();
        dialog.setTitle("Изменение цвета контура");
        dialog.setHeaderText("Выберите цвет контура");

        ButtonType applyButton = new ButtonType("Применить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Цвет контура:"), 0, 0);
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
                statusSetter.accept("Цвет контура изменен");
            }
        });
    }

    /**
     * Диалог изменения цвета заливки
     */
    private void changeFillColor() {
        javafx.scene.control.ColorPicker colorPicker = new javafx.scene.control.ColorPicker(fillColor);

        Dialog<Color> dialog = new Dialog<>();
        dialog.setTitle("Изменение цвета заливки");
        dialog.setHeaderText("Выберите цвет заливки");

        ButtonType applyButton = new ButtonType("Применить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Цвет заливки:"), 0, 0);
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
            setFillColor(color);
            if (statusSetter != null) {
                statusSetter.accept("Цвет заливки изменен");
            }
        });
    }

    /**
     * Получение stored width (exact, для serialize)
     *
     * @return currentWidth, или bounds если not set
     */
    protected double getCurrentWidth() {
        if (currentWidth < 0) {
            return Math.max(1.0, getBoundsInLocal().getWidth());  // Fallback на initial
        }
        return currentWidth;
    }

    /**
     * Получение stored height (exact)
     */
    protected double getCurrentHeight() {
        if (currentHeight < 0) {
            return Math.max(1.0, getBoundsInLocal().getHeight());
        }
        return currentHeight;
    }

    /**
     * Установка stored dimensions (вызывать в resizeShape, после update shape)
     */
    protected void setCurrentDimensions(double width, double height) {
        this.currentWidth = Math.max(1.0, width);  // Min 1px
        this.currentHeight = Math.max(1.0, height);

        // Опционально: Принудительно обновить JavaFX bounds (для consistency)
        requestLayout();
        // Если need sync: Platform.runLater(() -> System.out.println("Bounds after set: " + getBoundsInLocal()));  // Debug
    }

    // Геттеры и сеттеры
    public Color getStrokeColor() {
        return strokeColor;
    }

    public void setStrokeColor(Color color) {
        this.strokeColor = color;
        applyCurrentStyle();
    }

    public Color getFillColor() {
        return fillColor;
    }

    public void setFillColor(Color color) {
        this.fillColor = color;
        applyCurrentStyle();
    }
}