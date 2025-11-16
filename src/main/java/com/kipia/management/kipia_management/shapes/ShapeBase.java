package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ClipboardManager;
import com.kipia.management.kipia_management.managers.ShapeManager;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.Cursor;
import javafx.geometry.Point2D;
import javafx.geometry.Bounds;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    // Добавляем флаг для блокировки автосохранения во время undo/redo
    private final boolean isUndoRedoInProgress = false;

    // Добавляем поля для сохранения начальной позиции перед перемещением
    private double dragStartX, dragStartY;

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
    // ROTATION PROPERTIES
    // ============================================================

    protected double rotationAngle = 0.0; // Текущий угол поворота в градусах

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
    public ShapeBase(AnchorPane pane, Consumer<String> statusSetter, Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        this.pane = pane;
        this.statusSetter = statusSetter;
        this.onSelectCallback = onSelectCallback;
        this.shapeManager = shapeManager;
        initializeState();
        setupEventHandlers();
        //setupContextMenu(); // Создаем контекстное меню по умолчанию
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
    public abstract String getShapeType();

    // ============================================================
    // RESIZE HANDLES MANAGEMENT
    // ============================================================

    /**
     * Создание ручек изменения размера
     */
    @Override
    public void createResizeHandles() {
        if (resizeHandles != null) return;

        System.out.println("DEBUG: Creating resize handles for " + getShapeType());
        resizeHandles = new Circle[RESIZE_HANDLE_COUNT];

        for (int i = 0; i < RESIZE_HANDLE_COUNT; i++) {
            resizeHandles[i] = createResizeHandle();
            pane.getChildren().add(resizeHandles[i]);
            resizeHandles[i].setVisible(false);
        }

        // ВАЖНО: Настраиваем обработчики после создания handles
        setupResizeHandleHandlers();
        System.out.println("DEBUG: Resize handles created and handlers setup for " + getShapeType());
    }

    /**
     * Получение точек привязки фигуры (углы, центр, середины сторон)
     */
    public List<Point2D> getSnapPoints() {
        Bounds bounds = getBoundsInParent();
        List<Point2D> points = new ArrayList<>();

        // Углы
        points.add(new Point2D(bounds.getMinX(), bounds.getMinY())); // левый верхний
        points.add(new Point2D(bounds.getMaxX(), bounds.getMinY())); // правый верхний
        points.add(new Point2D(bounds.getMinX(), bounds.getMaxY())); // левый нижний
        points.add(new Point2D(bounds.getMaxX(), bounds.getMaxY())); // правый нижний

        // Центр
        points.add(new Point2D(bounds.getMinX() + bounds.getWidth() / 2,
                bounds.getMinY() + bounds.getHeight() / 2));

        // Середины сторон
        points.add(new Point2D(bounds.getMinX() + bounds.getWidth() / 2, bounds.getMinY())); // верх
        points.add(new Point2D(bounds.getMaxX(), bounds.getMinY() + bounds.getHeight() / 2)); // право
        points.add(new Point2D(bounds.getMinX() + bounds.getWidth() / 2, bounds.getMaxY())); // низ
        points.add(new Point2D(bounds.getMinX(), bounds.getMinY() + bounds.getHeight() / 2)); // лево

        return points;
    }

    /**
     * Публичный метод для изменения размера фигуры (для использования в командах)
     *
     * @param newWidth  новая ширина
     * @param newHeight новая высота
     */
    public void applyResize(double newWidth, double newHeight) {
        resizeShape(newWidth, newHeight);
        setCurrentDimensions(newWidth, newHeight);
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
     * Возвращает сообщение о перемещении для данного типа фигуры
     * Может быть переопределен в подклассах для кастомных сообщений
     */
    protected String getMoveStatusMessage() {
        return "Позиция фигуры изменена";
    }

    /**
     * Возвращает сообщение об изменении размера для данного типа фигуры
     * Может быть переопределен в подклассах для кастомных сообщений
     */
    protected String getResizeStatusMessage() {
        return "Размер фигуры изменен";
    }

    /**
     * Обновление позиций ручек
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
        if (resizeHandles != null) return;

        System.out.println("DEBUG: Creating resize handles for " + getShapeType());
        resizeHandles = new Circle[RESIZE_HANDLE_COUNT];

        for (int i = 0; i < RESIZE_HANDLE_COUNT; i++) {
            resizeHandles[i] = createResizeHandle();
            pane.getChildren().add(resizeHandles[i]);
            resizeHandles[i].setVisible(false);
        }

        // ВАЖНО: Настраиваем обработчики после создания handles
        setupResizeHandleHandlers();
        System.out.println("DEBUG: Resize handles created and handlers setup for " + getShapeType());
    }

    // ============================================================
    // EVENT HANDLERS SETUP
    // ============================================================

    /**
     * Настройка обработчиков событий
     */
    private void setupEventHandlers() {
        setupDragHandlers();
        setupDoubleClickForSelection();
    }

    /**
     * Настройка обработчиков перетаскивания
     */
    private void setupDragHandlers() {
        setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) { // ТОЛЬКО левая кнопка
                handleDragPressed(event);
            }
        });

        // Обработчик перетаскивания - проверяем ЛЕВУЮ кнопку
        setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) { // ТОЛЬКО левая кнопка
                handleDragDragged(event);
            }
        });

        // Обработчик отпускания - проверяем ЛЕВУЮ кнопку
        setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY) { // ТОЛЬКО левая кнопка
                handleDragReleased(event);
            }
        });
    }

    /**
     * Настройка обработки двойного клика для выделения
     */
    private void setupDoubleClickForSelection() {
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                if (event.getClickCount() == 2) {
                    // Двойной клик - выделяем фигуру и показываем handles
                    System.out.println("DEBUG: Double click - selecting shape with handles");
                    handleDoubleClickSelection();
                    event.consume();
                }
                // Одинарный клик НЕ выделяет - позволяет перетаскивать без выделения
            }
        });
    }

    /**
     * Обработка выделения по двойному клику
     */
    private void handleDoubleClickSelection() {
        System.out.println("DEBUG: Double click selection - selecting shape WITH handles");

        // Выделяем эту фигуру через менеджер
        if (onSelectCallback != null) {
            onSelectCallback.accept(this);
        }

        // Показываем handles для ресайза ТОЛЬКО при двойном клике
        makeResizeHandlesVisible();

        if (statusSetter != null) {
            statusSetter.accept("Фигура выделена: " + getShapeType() + " - используйте ручки для изменения");
        }

        System.out.println("DEBUG: Double click - resize handles shown");
    }

    /**
     * Настройка обработчиков для ручек изменения размера
     */
    private void setupResizeHandleHandlers() {
        System.out.println("DEBUG: Setting up resize handle handlers for " + getShapeType());

        if (resizeHandles == null || resizeHandles.length == 0) {
            System.out.println("DEBUG: No resize handles to setup handlers for");
            return;
        }

        int handlersSetup = 0;
        for (int i = 0; i < resizeHandles.length; i++) {
            Circle handle = resizeHandles[i];
            if (handle != null) {
                // ОЧИСТИТЬ старые обработчики
                handle.setOnMousePressed(null);
                handle.setOnMouseDragged(null);
                handle.setOnMouseReleased(null);

                // ДОБАВИТЬ новые обработчики
                final int handleIndex = i;

                handle.setOnMousePressed(event -> {
                    System.out.println("DEBUG: Resize handle " + handleIndex + " PRESSED");
                    if (!event.isPrimaryButtonDown()) {
                        System.out.println("DEBUG: Not primary button - ignoring");
                        event.consume();
                        return;
                    }
                    Point2D pressPoint = pane.sceneToLocal(event.getSceneX(), event.getSceneY());
                    System.out.println("DEBUG: Init resize at (" + pressPoint.getX() + ", " + pressPoint.getY() + ")");
                    initResize(pressPoint.getX(), pressPoint.getY());
                    event.consume();
                });

                handle.setOnMouseDragged(event -> {
                    System.out.println("DEBUG: Resize handle " + handleIndex + " DRAGGED");
                    if (!event.isPrimaryButtonDown()) {
                        System.out.println("DEBUG: Not primary button - ignoring");
                        event.consume();
                        return;
                    }
                    Point2D currentPoint = pane.sceneToLocal(event.getSceneX(), event.getSceneY());
                    System.out.println("DEBUG: Handle drag to (" + currentPoint.getX() + ", " + currentPoint.getY() + ")");
                    handleResizeDrag(handleIndex, currentPoint.getX(), currentPoint.getY());
                    event.consume();
                });

                handle.setOnMouseReleased(event -> {
                    System.out.println("DEBUG: Resize handle " + handleIndex + " RELEASED");
                    if (event.getButton() == MouseButton.PRIMARY) {
                        System.out.println("DEBUG: Handling resize release");
                        handleResizeRelease();
                    }
                    event.consume();
                });

                handlersSetup++;
                System.out.println("DEBUG: Handlers setup for handle " + handleIndex);
            }
        }

        System.out.println("DEBUG: Total " + handlersSetup + " resize handle handlers setup for " + getShapeType());
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

        // ВАЖНО: Для линии выделение происходит при ЛЮБОМ клике
        if (this instanceof LineShape) {
            System.out.println("DEBUG: Line drag started - selecting line");
            if (onSelectCallback != null) {
                onSelectCallback.accept(this);
            }
            makeResizeHandlesVisible();
        }

        isDragging = false; // Сбрасываем флаг в начале
        System.out.println("DEBUG: Drag pressed at (" + mousePos.getX() + "," + mousePos.getY() + ")");
        event.consume();
    }

    /**
     * Инициализация параметров перетаскивания
     */
    protected void initializeDrag(Point2D mousePos) {
        dragOffsetX = mousePos.getX() - getLayoutX();
        dragOffsetY = mousePos.getY() - getLayoutY();

        // СОХРАНЯЕМ НАЧАЛЬНУЮ ПОЗИЦИЮ ДЛЯ UNDO
        dragStartX = getLayoutX();
        dragStartY = getLayoutY();

        System.out.println("DEBUG: initializeDrag - current pos: (" + getLayoutX() + "," + getLayoutY() +
                "), mouse: (" + mousePos.getX() + "," + mousePos.getY() +
                "), offset: (" + dragOffsetX + "," + dragOffsetY + ")");
        System.out.println("DEBUG: start position saved: (" + dragStartX + "," + dragStartY + ")");
    }

    /**
     * Обработка перетаскивания фигуры
     */
    private void handleDragDragged(MouseEvent event) {
        if (!isDragging) {
            isDragging = true;
            System.out.println("DEBUG: Drag started - isDragging set to true");
        }

        Point2D scenePos = new Point2D(event.getSceneX(), event.getSceneY());
        Point2D panePos = pane.sceneToLocal(scenePos);
        double newX = panePos.getX() - dragOffsetX;
        double newY = panePos.getY() - dragOffsetY;

        System.out.println("DEBUG: Drag dragged to (" + newX + "," + newY + ")");

        setPosition(newX, newY);
        updateResizeHandles();
        event.consume();
    }

    /**
     * Обработка окончания перетаскивания
     */
    private void handleDragReleased(MouseEvent event) {
        System.out.println("DEBUG: === handleDragReleased START ===");
        System.out.println("DEBUG: isDragging: " + isDragging);
        System.out.println("DEBUG: event.getButton(): " + event.getButton());
        System.out.println("DEBUG: MouseButton.PRIMARY: " + MouseButton.PRIMARY);

        if (isDragging && statusSetter != null && event.getButton() == MouseButton.PRIMARY) {
            System.out.println("DEBUG: Conditions met for registering move");

            // ИСПОЛЬЗУЕМ КАСТОМНОЕ СООБЩЕНИЕ ДЛЯ ТИПА ФИГУРЫ
            statusSetter.accept(getMoveStatusMessage());

            // РЕГИСТРИРУЕМ ПЕРЕМЕЩЕНИЕ В UNDO/REDO
            double currentX = getLayoutX();
            double currentY = getLayoutY();

            // ИСПОЛЬЗУЕМ СОХРАНЕННЫЕ НАЧАЛЬНЫЕ КООРДИНАТЫ ИЗ handleDragPressed
            double oldX = dragStartX;
            double oldY = dragStartY;

            System.out.println("DEBUG: Drag released - from (" + oldX + "," + oldY + ") to (" + currentX + "," + currentY + ")");

            if (shapeManager != null) {
                shapeManager.registerMove(this, oldX, oldY, currentX, currentY);
                System.out.println("DEBUG: Movement registered for " + getShapeType());
            }
        } else {
            System.out.println("DEBUG: Conditions NOT met for registering move");
        }
        isDragging = false;
        updateResizeHandles();
        event.consume();
        System.out.println("DEBUG: === handleDragReleased END ===");
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
        double deltaX = currentX - pressPaneX;
        double deltaY = currentY - pressPaneY;

        System.out.println("DEBUG: handleResizeDrag - handle: " + handleIndex + ", deltaX: " + deltaX + ", deltaY: " + deltaY);

        if (Math.abs(deltaX) < 1 && Math.abs(deltaY) < 1) return;

        resizeByHandle(handleIndex, deltaX, deltaY);
    }

    /**
     * Обработка окончания изменения размера
     */
    private void handleResizeRelease() {
        if (wasResizedInSession && statusSetter != null) {
            // ИСПОЛЬЗУЕМ КАСТОМНОЕ СООБЩЕНИЕ ДЛЯ ТИПА ФИГУРЫ
            statusSetter.accept(getResizeStatusMessage());

            // РЕГИСТРИРУЕМ РЕСАЙЗ В UNDO/REDO
            double currentX = getLayoutX();
            double currentY = getLayoutY();
            double currentWidth = getCurrentWidth();
            double currentHeight = getCurrentHeight();

            if (shapeManager != null) {
                shapeManager.registerResize(this, initialX, initialY, initialWidth, initialHeight, currentX, currentY, currentWidth, currentHeight);
            }
        }
    }

    /**
     * Изменение размера фигуры с помощью указанной ручки
     */
    @Override
    public void resizeByHandle(int handleIndex, double deltaX, double deltaY) {
        System.out.println("DEBUG: resizeByHandle - handle: " + handleIndex + ", deltaX: " + deltaX + ", deltaY: " + deltaY);

        ResizeParams params = calculateResizeParams(handleIndex, deltaX, deltaY);
        System.out.println("DEBUG: Calculated params - x: " + params.x + ", y: " + params.y + ", width: " + params.width + ", height: " + params.height);

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
        System.out.println("DEBUG: After correction - x: " + corrected.x + ", y: " + corrected.y + ", width: " + corrected.width + ", height: " + corrected.height);

        // Проверяем минимальный размер
        corrected = enforceMinimumSize(corrected);
        System.out.println("DEBUG: After min size - x: " + corrected.x + ", y: " + corrected.y + ", width: " + corrected.width + ", height: " + corrected.height);

        // Проверяем, было ли реальное изменение
        wasResizedInSession = hasRealSizeChange(corrected);
        System.out.println("DEBUG: Was resized: " + wasResizedInSession);

        // Применяем изменения
        System.out.println("DEBUG: Applying resize - layoutX: " + corrected.x + ", layoutY: " + corrected.y);
        setLayoutX(corrected.x);
        setLayoutY(corrected.y);

        System.out.println("DEBUG: Calling resizeShape with: " + corrected.width + "x" + corrected.height);
        resizeShape(corrected.width, corrected.height);

        System.out.println("DEBUG: Resize applied successfully");
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
        return params.width != initialWidth || params.height != initialHeight || params.x != initialX || params.y != initialY;
    }

    /**
     * Вспомогательный класс для параметров изменения размера
     */
    private record ResizeParams(double x, double y, double width, double height) {
    }

    // ============================================================
    // ROTATION METHODS
    // ============================================================

    /**
     * Получение центра фигуры в координатах панели
     */
    protected Point2D getCenterInPane() {
        Bounds bounds = getBoundsInParent();
        return new Point2D(bounds.getMinX() + bounds.getWidth() / 2, bounds.getMinY() + bounds.getHeight() / 2);
    }

    /**
     * Установка угла поворота
     */
    public void setRotation(double angle) {
        this.rotationAngle = normalizeAngle(angle);
        setRotate(this.rotationAngle);
    }

    /**
     * Нормализация угла в диапазон 0-360
     */
    private double normalizeAngle(double angle) {
        angle = angle % 360;
        if (angle < 0) angle += 360;
        return angle;
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
            event.consume(); // ВАЖНО: предотвращаем всплытие события
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

        // Пункт "Повернуть"
        MenuItem rotateItem = new MenuItem("Повернуть");
        rotateItem.setOnAction(event -> handleRotationInMenu());

        // Пункт "Копировать"
        MenuItem copyItem = new MenuItem("Копировать");
        copyItem.setOnAction(event -> copyToClipboard());

        // Пункт "Вставить" (будет активен только если есть что вставлять)
        MenuItem pasteItem = new MenuItem("Вставить");
        pasteItem.setOnAction(event -> pasteFromClipboard());
        pasteItem.disableProperty().bind(ClipboardManager.hasShapeDataProperty().not());

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

        contextMenu.getItems().addAll(copyItem, pasteItem, strokeColorItem, fillColorItem, rotateItem, separator, deleteItem);
        // УБЕДИТЕСЬ, что событие потребляется
        setOnContextMenuRequested(event -> {
            System.out.println("DEBUG: ShapeBase context menu requested");
            contextMenu.show(this, event.getScreenX(), event.getScreenY());
            event.consume(); // ВАЖНО: предотвращаем всплытие
            System.out.println("DEBUG: ShapeBase context menu event consumed");
        });
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
        // УБИРАЕМ вызов makeRotationHandleVisible()
        System.out.println("DEBUG: Shape selected - only resize handles shown");
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
        double oldX = getLayoutX();
        double oldY = getLayoutY();

        setLayoutX(x);
        setLayoutY(y);

        System.out.println("DEBUG: setPosition - from (" + oldX + "," + oldY + ") to (" + x + "," + y + ")");

        // Принудительно обновляем handles если они есть
        if (resizeHandles != null) {
            updateResizeHandles();
        }
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
            System.out.println("DEBUG: Shape added to pane at position (" + getLayoutX() + ", " + getLayoutY() + ")");
            System.out.println("DEBUG: Shape bounds in parent: " + getBoundsInParent());
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

        // Для всех фигур КРОМЕ TEXT используем этот формат
        if (!"TEXT".equals(type)) {
            return String.format(java.util.Locale.US, "%s|%.2f|%.2f|%.2f|%.2f|%.1f%s", type, pos[0], pos[1], w, h, rotationAngle, serializeColors());
        }

        // Для Text будет вызван переопределенный метод
        return "";
    }

    /**
     * Десериализация фигуры из строки
     */
    public static ShapeBase deserialize(String data, AnchorPane pane, Consumer<String> statusSetter, Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        if (data == null || data.trim().isEmpty()) return null;
        String[] parts = data.split("\\|");
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

            // Чтение угла поворота
            double rotation = 0.0;
            if (fixedParts.length > 5) {
                try {
                    rotation = Double.parseDouble(fixedParts[5]);
                } catch (NumberFormatException e) {
                    rotation = 0.0;
                }
            }

            ShapeBase shape = switch (type.toUpperCase()) {
                case "RECTANGLE" ->
                        new RectangleShape(x, y, width, height, pane, statusSetter, onSelectCallback, shapeManager);
                case "LINE" -> {
                    double startX = Double.parseDouble(fixedParts[1]);
                    double startY = Double.parseDouble(fixedParts[2]);
                    double endX = Double.parseDouble(fixedParts[3]);
                    double endY = Double.parseDouble(fixedParts[4]);
                    yield new LineShape(startX, startY, endX, endY, pane, statusSetter, onSelectCallback, shapeManager);
                }
                case "ELLIPSE" ->
                        new EllipseShape(x + width / 2, y + height / 2, width / 2, height / 2, pane, statusSetter, onSelectCallback, shapeManager);
                case "RHOMBUS" ->
                        new RhombusShape(x, y, width, height, pane, statusSetter, onSelectCallback, shapeManager);
                case "TEXT" -> createTextShape(fixedParts, pane, statusSetter, onSelectCallback, shapeManager);
                default -> null;
            };

            // ВАЖНО: Применяем поворот ко всем фигурам
            if (shape != null) {
                shape.setRotation(rotation);

                // Восстанавливаем цвета
                if (!"TEXT".equals(type) && fixedParts.length > 6) {
                    shape.deserializeColors(parts, 6, 7);
                }

                // ОТЛАДКА: проверяем примененные цвета
                shape.addContextMenu(shape::handleDelete);
            }

            return shape;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Creates a TextShape from serialized parts during deserialization
     */
    private static TextShape createTextShape(String[] fixedParts, AnchorPane pane, Consumer<String> statusSetter, Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        try {
            // Основные параметры
            double x = Double.parseDouble(fixedParts[1]);
            double y = Double.parseDouble(fixedParts[2]);

            // Текст (индекс 6) - ВАЖНО: правильный индекс
            String text = (fixedParts.length > 6) ? fixedParts[6] : "Текст";
            text = text.replace("\\|", "|"); // Unescape
            TextShape textShape = new TextShape(x, y, text, pane, statusSetter, onSelectCallback, shapeManager);

            // Шрифт (индексы 7-9)
            if (fixedParts.length > 7) {
                try {
                    double fontSize = Double.parseDouble(fixedParts[7]);
                    fontSize = Math.max(8, Math.min(72, fontSize));

                    String fontFamily = fixedParts.length > 8 ? fixedParts[8] : "Arial";
                    String fontStyle = fixedParts.length > 9 ? fixedParts[9] : "Regular";

                    FontWeight fontWeight = fontStyle.contains("Bold") ? FontWeight.BOLD : FontWeight.NORMAL;
                    FontPosture fontPosture = fontStyle.contains("Italic") ? FontPosture.ITALIC : FontPosture.REGULAR;

                    Font restoredFont = Font.font(fontFamily, fontWeight, fontPosture, fontSize);
                    textShape.setFont(restoredFont);
                } catch (NumberFormatException e) {
                    System.out.println("Ошибка восстановления шрифта: " + e.getMessage());
                }
            }

            // Угол поворота (индекс 5) - ВАЖНО: применяем ДО цветов
            if (fixedParts.length > 5) {
                try {
                    double rotation = Double.parseDouble(fixedParts[5]);
                    textShape.setRotation(rotation);
                } catch (NumberFormatException e) {
                    System.out.println("Ошибка восстановления угла поворота текста: " + e.getMessage());
                }
            }

            // ВАЖНО: ВОССТАНАВЛИВАЕМ ЦВЕТА ДЛЯ ТЕКСТА
            if (fixedParts.length > 11) {
                // Для текста цвета находятся на позициях 10 и 11
                textShape.deserializeColors(fixedParts, 10, 11);
            } else if (fixedParts.length > 9) {
                // Старый формат - цвета на позициях 8 и 9
                textShape.deserializeColors(fixedParts, 8, 9);
            }
            javafx.application.Platform.runLater(textShape::calculateTextSize);
            return textShape;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Сериализует цвета в строку
     */
    protected String serializeColors() {
        String result = String.format(java.util.Locale.US, "|%.3f,%.3f,%.3f,%.3f|%.3f,%.3f,%.3f,%.3f", strokeColor.getRed(), strokeColor.getGreen(), strokeColor.getBlue(), strokeColor.getOpacity(), fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), fillColor.getOpacity());
        return result;
    }

    /**
     * Десериализует цвета из строки
     */
    protected void deserializeColors(String[] originalParts, int strokeIndex, int fillIndex) {
        if (originalParts.length > strokeIndex) {
            String strokeData = originalParts[strokeIndex];
            // ОБРАБАТЫВАЕМ ОБА ВАРИАНТА РАЗДЕЛИТЕЛЕЙ
            double[] strokeValues = parseColorValues(strokeData);
            if (strokeValues.length == 4) {
                strokeColor = Color.color(strokeValues[0], strokeValues[1], strokeValues[2], strokeValues[3]);
            } else {
                strokeColor = Color.BLACK;
            }
        }

        if (originalParts.length > fillIndex) {
            String fillData = originalParts[fillIndex];
            double[] fillValues = parseColorValues(fillData);
            if (fillValues.length == 4) {
                fillColor = Color.color(fillValues[0], fillValues[1], fillValues[2], fillValues[3]);
            } else {
                fillColor = Color.TRANSPARENT;
            }
        }

        applyCurrentStyle();
    }

    /**
     * Парсит значения цвета из строки, обрабатывая оба формата разделителей
     */
    private double[] parseColorValues(String colorData) {
        String[] patterns = {
                "(\\d+\\.\\d+)\\.(\\d+\\.\\d+)\\.(\\d+\\.\\d+)\\.(\\d+\\.\\d+)", // 1.000.1.000.0.000.1.000
                "(\\d+\\.\\d+),(\\d+\\.\\d+),(\\d+\\.\\d+),(\\d+\\.\\d+)"        // 0.200,0.102,0.502,1.000
        };

        for (String patternStr : patterns) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr);
            java.util.regex.Matcher matcher = pattern.matcher(colorData);

            if (matcher.find()) {
                try {
                    return new double[]{
                            Double.parseDouble(matcher.group(1)),
                            Double.parseDouble(matcher.group(2)),
                            Double.parseDouble(matcher.group(3)),
                            Double.parseDouble(matcher.group(4))
                    };
                } catch (NumberFormatException e) {
                    System.out.println("ERROR parsing with pattern: " + patternStr);
                }
            }
        }
        return new double[]{0.0, 0.0, 0.0, 1.0};
    }

    /**
     * Обработчик удаления фигуры для контекстного меню
     */
    protected void handleDelete(ShapeHandler shapeHandler) {
        if (shapeManager != null) {
            shapeManager.removeShape((Node) shapeHandler);
        }
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

    public void setStrokeColor(Color newColor) {
        // Сохраняем старый цвет перед изменением
        Color oldColor = this.strokeColor;

        this.strokeColor = newColor;
        applyCurrentStyle();

        // РЕГИСТРИРУЕМ ИЗМЕНЕНИЕ ЦВЕТА В UNDO/REDO (только если не undo/redo)
        if (shapeManager != null && !newColor.equals(oldColor) && !isUndoRedoInProgress) {
            shapeManager.registerColorChange(this, oldColor, this.fillColor, newColor, this.fillColor);
        }
    }

    public void setFillColor(Color newColor) {
        // Сохраняем старый цвет перед изменением
        Color oldColor = this.fillColor;

        this.fillColor = newColor;
        applyCurrentStyle();

        // РЕГИСТРИРУЕМ ИЗМЕНЕНИЕ ЦВЕТА В UNDO/REDO (только если не undo/redo)
        if (shapeManager != null && !newColor.equals(oldColor) && !isUndoRedoInProgress) {
            shapeManager.registerColorChange(this, this.strokeColor, oldColor, this.strokeColor, newColor);
        }
    }

    /**
     * Метод для установки цвета без регистрации в undo/redo (для использования в командах)
     */
    public void setColorsSilent(Color newStroke, Color newFill) {
        this.strokeColor = newStroke;
        this.fillColor = newFill;
        applyCurrentStyle();
    }

    /**
     * Обработчик поворота в контекстном меню (будет переопределен в LineShape)
     */
    protected void handleRotationInMenu() {
        // Для линии этот метод не должен вызываться
        if (this instanceof LineShape) {
            System.out.println("DEBUG: Rotation disabled for LineShape");
            return;
        }

        double newAngle = (rotationAngle + 45) % 360;
        setRotation(newAngle);

        if (shapeManager != null) {
            shapeManager.registerRotation(this, rotationAngle, newAngle);
        }

        if (statusSetter != null) {
            statusSetter.accept("Фигура повернута на " + newAngle + "°");
        }
    }

    /**
     * Копирует фигуру в буфер обмена
     */
    public void copyToClipboard() {
        ClipboardManager.copyShape(this);
        if (statusSetter != null) {
            statusSetter.accept("Фигура скопирована");
        }
    }

    /**
     * Вставляет фигуру из буфера обмена
     */
    public void pasteFromClipboard() {
        if (!ClipboardManager.hasShapeData()) {
            return;
        }

        try {
            String shapeData = ClipboardManager.getCopiedShapeData();
            ShapeBase copiedShape = deserialize(shapeData, pane, statusSetter, onSelectCallback, shapeManager);

            if (copiedShape != null) {
                // Смещаем копию на 20 пикселей от оригинала
                double[] originalPos = getPosition();
                copiedShape.setPosition(originalPos[0] + 20, originalPos[1] + 20);

                // Добавляем копию на панель
                copiedShape.addToPane();
                if (shapeManager != null) {
                    shapeManager.addShape(copiedShape);
                }

                if (statusSetter != null) {
                    statusSetter.accept("Фигура вставлена");
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при вставке фигуры: " + e.getMessage());
            if (statusSetter != null) {
                statusSetter.accept("Ошибка вставки фигуры");
            }
        }
    }
}