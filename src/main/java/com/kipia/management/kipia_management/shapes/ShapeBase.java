package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import javafx.scene.Group;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.Cursor;
import javafx.geometry.Point2D;
import javafx.geometry.Bounds;

import java.util.Arrays;
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
                     ShapeManager shapeManager) {  // Добавлен параметр shapeManager
        this.pane = pane;
        this.statusSetter = statusSetter;
        this.onSelectCallback = onSelectCallback;
        this.shapeManager = shapeManager;  // Сохраняем ссылку
        initializeState();
        setupEventHandlers();
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
        }
    }

    /**
     * Создание одной ручки изменения размера
     */
    private Circle createResizeHandle() {
        Circle handle = new Circle(0, 0, RESIZE_HANDLE_RADIUS, RESIZE_HANDLE_COLOR);
        handle.setCursor(Cursor.CROSSHAIR);
        handle.setVisible(false);
        return handle;
    }

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
        // Если handles нет — создаём
        if (resizeHandles == null) {
            createResizeHandles();  // Создаёт массив Circle и добавляет в pane
        }

        // Привязываем/перепривязываем события на handles (ключевой фикс!)
        setupResizeHandleHandlers();

        // Делаем видимыми и обновляем позиции
        for (Circle handle : resizeHandles) {
            if (handle != null) {
                handle.setVisible(true);
            }
        }
        updateResizeHandles();  // Обновляет позиции
    }

    // ============================================================
    // EVENT HANDLERS SETUP
    // ============================================================

    /**
     * Настройка обработчиков событий
     */
    private void setupEventHandlers() {
        setupDragHandlers();
        setupContextMenu();
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
            final int handleIndex = i;
            Circle handle = resizeHandles[i];
            if (handle != null) {
                setupResizeHandleHandler(handle, handleIndex);  // Привязка для каждой ручки
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
    private void initializeDrag(Point2D mousePos) {
        dragOffsetX = mousePos.getX() - getLayoutX();
        dragOffsetY = mousePos.getY() - getLayoutY();
    }

    /**
     * Обработка перетаскивания фигуры
     */
    private void handleDragDragged(MouseEvent event) {
        isDragging = true;
        Point2D panePos = pane.sceneToLocal(event.getSceneX(), event.getSceneY());

        double newX = calculateNewPositionX(panePos.getX());
        double newY = calculateNewPositionY(panePos.getY());

        setPosition(newX, newY);
        updateResizeHandles();
        event.consume();
    }

    /**
     * Расчет новой позиции по X с учетом границ
     */
    private double calculateNewPositionX(double mouseX) {
        double newX = mouseX - dragOffsetX;
        double maxX = pane.getWidth() - getBoundsInLocal().getWidth();
        return Math.max(0, Math.min(newX, maxX));
    }

    /**
     * Расчет новой позиции по Y с учетом границ
     */
    private double calculateNewPositionY(double mouseY) {
        double newY = mouseY - dragOffsetY;
        double maxY = pane.getHeight() - getBoundsInLocal().getHeight();
        return Math.max(0, Math.min(newY, maxY));
    }

    /**
     * Обработка окончания перетаскивания
     */
    private void handleDragReleased(MouseEvent event) {
        if (isDragging && statusSetter != null) {
            statusSetter.accept("Позиция фигуры изменена");
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
    private static class ResizeParams {
        final double x, y, width, height;

        ResizeParams(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    // ============================================================
    // CONTEXT MENU
    // ============================================================

    /**
     * Настройка контекстного меню
     */
    private void setupContextMenu() {
        ContextMenu contextMenu = createContextMenu();
        setOnContextMenuRequested(event -> {
            contextMenu.show(this, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    /**
     * Создание контекстного меню
     */
    private ContextMenu createContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.setOnAction(event -> handleDelete());
        menu.getItems().add(deleteItem);
        return menu;
    }

    /**
     * Обработка удаления фигуры
     */
    private void handleDelete() {
        // Реализация будет в конкретном контексте использования
    }

    /**
     * Добавление контекстного меню к фигуре
     *
     * @param deleteAction действие при удалении фигуры
     */
    @Override
    public void addContextMenu(Consumer<ShapeHandler> deleteAction) {
        ContextMenu contextMenu = createContextMenu(deleteAction);
        setOnContextMenuRequested(event -> {
            contextMenu.show(this, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    /**
     * Создание контекстного меню с действием удаления
     */
    private ContextMenu createContextMenu(Consumer<ShapeHandler> deleteAction) {
        ContextMenu menu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.setOnAction(event -> deleteAction.accept(this));
        menu.getItems().add(deleteItem);
        return menu;
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
        makeResizeHandlesVisible();
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
        if (pane != null && pane.getChildren().contains(this)) {
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
        if (parts.length < 5) return null;  // Минимум для всех фигур, для TEXT с текстом может быть >5
        try {
            return createShapeFromParts(parts, pane, statusSetter, onSelectCallback, shapeManager);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Создание фигуры из частей строки (с фиксом локали: "," → ".")
     */
    private static ShapeBase createShapeFromParts(String[] parts, AnchorPane pane,
                                                  Consumer<String> statusSetter,
                                                  Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        if (parts.length < 5) {
            System.out.println("SKIP: Недостаточно частей в данных: " + Arrays.toString(parts));  // Debug, удали после
            return null;
        }
        String type = parts[0];
        try {
            // Фикс: Заменяем запятую на точку во всех частях (для старых данных с ",")
            // Это robust: не сломает "." в новых данных (replace не change)
            String[] fixedParts = new String[parts.length];
            for (int i = 0; i < parts.length; i++) {
                fixedParts[i] = parts[i].replace(",", ".");  // "70,00" → "70.00"
            }
            double x = Double.parseDouble(fixedParts[1]);
            double y = Double.parseDouble(fixedParts[2]);
            double width = Double.parseDouble(fixedParts[3]);
            double height = Double.parseDouble(fixedParts[4]);
            // Проверки на NaN/negative (для стабильности)
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(width) || Double.isNaN(height) ||
                    width <= 0 || height <= 0) {  // Для TEXT width/height ~0 ok, но проверь
                System.out.println("SKIP: Некорректные координаты для " + type + ": x=" + x + ", y=" + y + ", w=" + width + ", h=" + height);
                return null;
            }
            // Безопасный sample в лог (debug)
            System.out.println("Parsing OK: " + type + " at (" + x + ", " + y + ") size (" + width + ", " + height + ") — creating instance");
            return switch (type.toUpperCase()) {  // .toUpperCase() для robustness (если "rectangle")
                case "RECTANGLE" ->
                        new RectangleShape(x, y, width, height, pane, statusSetter, onSelectCallback, shapeManager);
                case "LINE" ->
                        new LineShape(x, y, x + width, y + height, pane, statusSetter, onSelectCallback, shapeManager);
                case "ELLIPSE" -> {
                    double centerX = x + width / 2;
                    double centerY = y + height / 2;
                    double radiusX = Math.max(1.0, width / 2);  // Min 1.0 для видимости
                    double radiusY = Math.max(1.0, height / 2);
                    yield new EllipseShape(centerX, centerY, radiusX, radiusY, pane, statusSetter, onSelectCallback, shapeManager);
                }
                case "RHOMBUS" ->
                        new RhombusShape(x, y, width, height, pane, statusSetter, onSelectCallback, shapeManager);
                case "TEXT" -> {
                    String actualText = (fixedParts.length > 5) ? fixedParts[5] : "Новый текст";
                    // Фикс: заменить escape если был
                    actualText = actualText.replace("\\|", "|");  // Если escapили
                    yield new TextShape(x, y, actualText, pane, statusSetter, onSelectCallback, shapeManager);
                }
                default -> {
                    System.out.println("SKIP: Неизвестный тип фигуры: " + type);
                    yield null;
                }
            };
        } catch (NumberFormatException e) {
            System.out.println("SKIP: Ошибка парсинга чисел в " + type + ": " + e.getMessage() + " (parts: " + Arrays.toString(parts) + ")");
            System.out.println("  Возможная причина: Некорректный формат (e.g., ',', буквы). Raw: '" + Arrays.toString(parts) + "'");
            return null;
        } catch (Exception e) {
            System.out.println("SKIP: Общая ошибка в создании " + type + ": " + e.getMessage());
            e.printStackTrace();  // Для других ошибок (e.g., constructor fail)
            return null;
        }
    }

    /**
     * Получение stored width (exact, для serialize)
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
}