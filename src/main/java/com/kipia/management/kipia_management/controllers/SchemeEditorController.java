package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.managers.ShapeManager;
import com.kipia.management.kipia_management.managers.ZoomManager;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.models.DeviceLocation;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.DeviceIconService;
import com.kipia.management.kipia_management.services.SchemeDAO;
import com.kipia.management.kipia_management.services.DeviceLocationDAO;
import com.kipia.management.kipia_management.shapes.*;
import com.kipia.management.kipia_management.utils.CustomAlert;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Контроллер редактора схем с оптимизированной архитектурой фигур.
 * Управляет созданием схем, работой с устройствами и координацией компонентов
 *
 * @author vladimir_shi
 * @since 30.09.2025
 */
public class SchemeEditorController {

    private Rectangle borderRect;

    // ============================================================
    // FXML COMPONENTS
    // ============================================================

    @FXML
    private ComboBox<Scheme> schemeComboBox;
    @FXML
    private ComboBox<Device> deviceComboBox;
    @FXML
    private Button saveSchemeBtn, selectToolBtn, lineToolBtn, rectToolBtn, textToolBtn, addDeviceToolBtn, rhombusToolBtn;
    @FXML
    private Button undoBtn, redoBtn, ellipseToolBtn, zoomInBtn, zoomOutBtn, clearSchemeBtn;
    @FXML
    private AnchorPane schemePane;
    @FXML
    private ScrollPane schemeScrollPane;
    @FXML
    private Label statusLabel;

    // ============================================================
    // DEPENDENCIES & SERVICES
    // ============================================================

    private static final Logger LOGGER = Logger.getLogger(SchemeEditorController.class.getName());
    private DeviceDAO deviceDAO;
    private SchemeDAO schemeDAO;
    private DeviceLocationDAO deviceLocationDAO;
    private ShapeManager shapeManager;
    private ShapeService shapeService;
    private DeviceIconService deviceIconService;
    private ZoomManager zoomManager;

    // ============================================================
    // DATA MODELS
    // ============================================================

    private Scheme currentScheme;
    private ObservableList<Device> deviceList;
    private ShapeManager.Tool currentTool = ShapeManager.Tool.SELECT;

    // ============================================================
    // DEPENDENCY INJECTION API
    // ============================================================

    /**
     * Устанавливает DAO для работы с устройствами
     *
     * @param dao DAO для устройств
     */
    public void setDeviceDAO(DeviceDAO dao) {
        this.deviceDAO = dao;
    }

    /**
     * Устанавливает DAO для работы со схемами
     *
     * @param dao DAO для схем
     */
    public void setSchemeDAO(SchemeDAO dao) {
        this.schemeDAO = dao;
    }

    /**
     * Устанавливает DAO для работы с расположениями устройств
     *
     * @param dao DAO для расположений устройств
     */
    public void setDeviceLocationDAO(DeviceLocationDAO dao) {
        this.deviceLocationDAO = dao;
    }

    // ============================================================
    // INITIALIZATION
    // ============================================================

    /**
     * Инициализация FXML компонентов
     */
    @FXML
    private void initialize() {
        try {
            setupComboBoxes();  // Non-DAO
            setupPaneEventHandlers();  // Non-DAO
            setupShapeSystem();  // Placeholder
            setupToolButtons();  // Non-DAO
            applyButtonStyles();  // Non-DAO
            addVisibleBorder();  // Fix below
            if (schemePane != null) {
                schemePane.setPrefWidth(1000);  // Match рамка
                schemePane.setPrefHeight(1000);
                schemePane.setMinWidth(0.0);  // Allow shrink, but events cover full
                schemePane.setMinHeight(0.0);
                System.out.println("DEBUG: Pane grown to 1000x1000 for full events coverage");
            }
            LOGGER.info("Контроллер редактора схем инициализирован");
        } catch (Exception e) {
            LOGGER.severe("Ошибка в initialize(): " + e.getMessage());
            e.printStackTrace();
            if (statusLabel != null) statusLabel.setText("Ошибка инициализации: " + e.getMessage());
        }
    }

    /**
     * Основная инициализация после внедрения зависимостей
     */
    public void init() {
        try {
            validateDAODependencies();
            initializeServices();
            loadInitialData();  // DAO-dependent
            setupInitialScheme();  // DAO
        } catch (Exception e) {
            LOGGER.severe("Ошибка в init(): " + e.getMessage());
            e.printStackTrace();
            if (statusLabel != null) statusLabel.setText("Ошибка запуска: " + e.getMessage());
        }
    }

    /**
     * Инициализация сервисов и менеджеров
     */
    private void initializeServices() {
        // Создаём менеджер фигур СНАЧАЛА без сервиса (циклическая зависимость)
        this.shapeManager = new ShapeManager(schemePane, null);  // shapeService = null
        this.shapeManager.setStatusSetter(statusLabel::setText);
        this.shapeManager.setOnSelectCallback(shapeManager::selectShape);

        // Новое: Регистрируем callbacks для auto-save (this::autoSaveScheme — лямбда на метод контроллера)
        shapeManager.setOnShapeAdded(this::autoSaveScheme);  // После добавления фигуры
        // Новое: Регистрация для удаления (с отладкой)
        try {
            shapeManager.setOnShapeRemoved(this::autoSaveScheme);
            System.out.println("DEBUG: onShapeRemoved callback registered successfully");
        } catch (Exception e) {
            System.err.println("ERROR: Failed to register onShapeRemoved: " + e.getMessage());
        }

        shapeManager.setOnShapeRemoved(this::autoSaveScheme);  // После удаления (опционально)

        // Создаём фабрику фигур (теперь shapeManager не null)
        ShapeFactory shapeFactory = new ShapeFactory(
                schemePane,
                statusLabel::setText,
                shapeManager::selectShape,  // onSelectCallback НЕ null
                shapeManager  // Теперь передаётся корректный shapeManager
        );

        // Создаём сервис фигур
        this.shapeService = new ShapeService(shapeFactory);

        // Заменяем shapeService в менеджере (разрываем цикл)
        shapeManager.setShapeService(shapeService);

        // Создаём сервис для иконок устройств
        this.deviceIconService = new DeviceIconService(
                schemePane,
                this::saveDeviceLocation,
                deviceLocationDAO,
                this::refreshAvailableDevices  // Новое: колбэк для обновления списка приборов
        );

        // Инициализация ZoomManager с pane, статусом и callback для обновления handles
        this.zoomManager = new ZoomManager(
                schemePane,
                schemeScrollPane,
                statusLabel::setText,
                () -> {
                    // Только обновление handles
                    ShapeHandler selected = shapeManager.getSelectedShape();
                    if (selected != null) {
                        selected.updateResizeHandles();
                    }
                }
        );

        zoomManager.setZoom(0.33);
    }

    /**
     * Загрузка начальных данных
     */
    private void loadInitialData() {
        loadDevices();
        loadSchemes();
    }

    // ============================================================
    // SETUP METHODS
    // ============================================================

    /**
     * Настройка ComboBox элементов
     */
    private void setupComboBoxes() {
        setupSchemeComboBox();
        setupDeviceComboBox();
    }

    /**
     * Настройка ComboBox для схем
     */
    private void setupSchemeComboBox() {
        schemeComboBox.setConverter(createSchemeConverter());
        schemeComboBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.equals(oldV)) {
                loadScheme(newV);
            }
        });
    }

    /**
     * Настройка ComboBox для устройств
     */
    private void setupDeviceComboBox() {
        deviceComboBox.setConverter(createDeviceConverter());
    }

    /**
     * Создание конвертера для отображения схем
     */
    private StringConverter<Scheme> createSchemeConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Scheme scheme) {
                return scheme != null ? scheme.getName() : "";
            }

            @Override
            public Scheme fromString(String string) {
                return null;
            }
        };
    }

    /**
     * Создание конвертера для отображения устройств
     */
    private StringConverter<Device> createDeviceConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Device device) {
                if (device == null) return "";
                return String.format("%s - %s", device.getInventoryNumber(), device.getName());
            }

            @Override
            public Device fromString(String string) {
                return null;
            }
        };
    }

    /**
     * Настройка обработчиков событий панели
     */
    private void setupPaneEventHandlers() {
        if (schemePane != null) {
            schemePane.setOnMouseClicked(e -> schemePane.requestFocus());
            schemePane.setFocusTraversable(true);
        }
        // Wheel зум на ScrollPane (работает по всему viewport)
        if (schemeScrollPane != null) {
            schemeScrollPane.setOnScroll(event -> {
                if (event.isControlDown() && zoomManager != null) {
                    double factor = (event.getDeltaY() > 0) ? 1.2 : 1.0 / 1.2;
                    // Convert scene mouse pos to pane local (since pivot на mouse)
                    Point2D localPos = schemeScrollPane.sceneToLocal(event.getSceneX(), event.getSceneY());
                    zoomManager.zoom(factor, localPos.getX(), localPos.getY());  // Pass local x/y
                    event.consume();
                }
            });
        }
        setupPaneKeyHandlers();
    }

    /**
     * Настройка обработчиков клавиш для панели
     */
    private void setupPaneKeyHandlers() {
        schemePane.setOnKeyPressed(event -> {
            if (isShapeSelected() && currentTool == ShapeManager.Tool.SELECT) {
                handleKeyPress(event.getCode());
            }
        });
    }

    /**
     * Обработка нажатия клавиш
     */
    private void handleKeyPress(javafx.scene.input.KeyCode code) {
        switch (code) {
            case DELETE, BACK_SPACE -> deleteSelectedShape();
        }
    }

    /**
     * Настройка системы фигур
     */
    private void setupShapeSystem() {
        // Инициализация будет выполнена в initializeServices()
        // после загрузки зависимостей
    }

    /**
     * Настройка кнопок инструментов
     */
    private void setupToolButtons() {
        setupShapeToolButtons();
        setupActionButtons();
    }

    /**
     * Настройка кнопок для инструментов рисования
     */
    private void setupShapeToolButtons() {
        selectToolBtn.setOnAction(e -> setCurrentTool(ShapeManager.Tool.SELECT, "Инструмент: Выбор"));
        lineToolBtn.setOnAction(e -> setCurrentTool(ShapeManager.Tool.LINE, "Инструмент: Линия"));
        rectToolBtn.setOnAction(e -> setCurrentTool(ShapeManager.Tool.RECTANGLE, "Инструмент: Прямоугольник"));
        ellipseToolBtn.setOnAction(e -> setCurrentTool(ShapeManager.Tool.ELLIPSE, "Инструмент: Эллипс"));
        rhombusToolBtn.setOnAction(e -> setCurrentTool(ShapeManager.Tool.RHOMBUS, "Инструмент: Ромб"));
        textToolBtn.setOnAction(e -> {
            setCurrentTool(ShapeManager.Tool.TEXT, "Инструмент: Текст");
            System.out.println("DEBUG: TEXT tool activated");  // Опционально для проверки
        });
        addDeviceToolBtn.setOnAction(e -> setCurrentTool(ShapeManager.Tool.ADD_DEVICE, "Инструмент: Добавить прибор"));
    }

    /**
     * Настройка кнопок действий
     */
    private void setupActionButtons() {
        undoBtn.setOnAction(e -> shapeManager.undo());
        redoBtn.setOnAction(e -> shapeManager.redo());
        saveSchemeBtn.setOnAction(e -> saveCurrentScheme());
        zoomInBtn.setOnAction(e -> zoomManager.zoomIn());
        zoomOutBtn.setOnAction(e -> zoomManager.zoomOut());
        clearSchemeBtn.setOnAction(e -> clearScheme());
    }

    /**
     * Применение стилей к кнопкам
     */
    private void applyButtonStyles() {
        applyToolButtonStyles();
        applyActionButtonStyles();
    }

    /**
     * Применение стилей к кнопкам инструментов
     */
    private void applyToolButtonStyles() {
        List<Button> toolButtons = Arrays.asList(
                selectToolBtn, lineToolBtn, rectToolBtn, ellipseToolBtn,
                addDeviceToolBtn, rhombusToolBtn, textToolBtn
        );

        for (Button button : toolButtons) {
            StyleUtils.applyHoverAndAnimation(button, "tool-button", "tool-button-hover");
        }
    }

    /**
     * Применение стилей к кнопкам действий
     */
    private void applyActionButtonStyles() {
        StyleUtils.applyHoverAndAnimation(undoBtn, "tool-button", "tool-button-hover");
        StyleUtils.applyHoverAndAnimation(redoBtn, "tool-button", "tool-button-hover");
        StyleUtils.applyHoverAndAnimation(saveSchemeBtn, "tool-button", "tool-button-hover");
        StyleUtils.applyHoverAndAnimation(zoomInBtn, "tool-button", "tool-button-hover");
        StyleUtils.applyHoverAndAnimation(zoomOutBtn, "tool-button", "tool-button-hover");
    }

    // ============================================================
    // TOOL MANAGEMENT
    // ============================================================

    /**
     * Установка текущего инструмента
     *
     * @param tool          инструмент для установки
     * @param statusMessage сообщение для статусной строки
     */
    private void setCurrentTool(ShapeManager.Tool tool, String statusMessage) {
        currentTool = tool;
        shapeManager.setSelectToolActive(tool == ShapeManager.Tool.SELECT);
        statusLabel.setText(statusMessage);
        shapeManager.deselectShape();
    }

    // ============================================================
    // DATA LOADING METHODS
    // ============================================================

    /**
     * Проверка наличия всех необходимых зависимостей
     */
    private void validateDAODependencies() {
        if (deviceDAO == null) {
            throw new IllegalStateException("deviceDAO is null - init() called before setDeviceDAO!");
        }

        if (schemeDAO == null || deviceLocationDAO == null) {
            throw new IllegalStateException("Missing DAO dependencies");
        }
    }

    /**
     * Загрузка списка устройств
     */
    private void loadDevices() {
        deviceList = FXCollections.observableArrayList(deviceDAO.getAllDevices());
        deviceComboBox.setItems(deviceList);
        LOGGER.info("Загружено " + deviceList.size() + " устройств");
    }

    /**
     * Загрузка списка схем на основе уникальных расположений устройств
     */
    private void loadSchemes() {
        List<String> locations = deviceDAO.getDistinctLocations();
        List<Scheme> schemes = createOrLoadSchemes(locations);
        updateSchemeComboBox(schemes);
    }

    /**
     * Создание или загрузка схем для указанных расположений
     */
    private List<Scheme> createOrLoadSchemes(List<String> locations) {
        List<Scheme> schemes = new ArrayList<>();

        for (String location : locations) {
            Scheme scheme = findOrCreateScheme(location);
            if (scheme != null) {
                schemes.add(scheme);
            }
        }

        return schemes;
    }

    /**
     * Поиск или создание схемы для указанного расположения
     */
    private Scheme findOrCreateScheme(String location) {
        Scheme scheme = schemeDAO.findSchemeByName(location);

        if (scheme == null) {
            scheme = createAutoScheme(location);
            if (schemeDAO.addScheme(scheme)) {
                LOGGER.info("Создана автоматическая схема для: " + location);
            } else {
                LOGGER.warning("Не удалось создать схему для: " + location);
                return null;
            }
        }

        return scheme;
    }

    /**
     * Создание автоматической схемы
     */
    private Scheme createAutoScheme(String location) {
        return new Scheme(0, location, "Автоматически созданная схема", "{}");
    }

    /**
     * Обновление ComboBox со схемами
     */
    private void updateSchemeComboBox(List<Scheme> schemes) {
        if (schemes.isEmpty()) {
            handleNoSchemesFound();
            return;
        }

        ObservableList<Scheme> schemeList = FXCollections.observableArrayList(schemes);
        schemeComboBox.setItems(schemeList);
        LOGGER.info("Загружено " + schemeList.size() + " схем");
    }

    /**
     * Обработка случая, когда схемы не найдены
     */
    private void handleNoSchemesFound() {
        LOGGER.warning("Список схем пуст");
        CustomAlert.showWarning("Загрузка схем",
                "Не удалось загрузить или создать схемы. Возможно, отсутствуют устройства с расположениями.");
    }

    /**
     * Настройка начальной схемы при запуске
     */
    private void setupInitialScheme() {
        if (!schemeComboBox.getItems().isEmpty() && schemeComboBox.getValue() == null) {
            Scheme firstScheme = schemeComboBox.getItems().getFirst();
            schemeComboBox.setValue(firstScheme);
        }
    }

    // ============================================================
    // SCHEME MANAGEMENT
    // ============================================================

    /**
     * Загрузка выбранной схемы
     */
    private void loadScheme(Scheme scheme) {
        try {
            currentScheme = scheme;
            clearSchemePane();
            loadSchemeContent(scheme);
            addVisibleBorder();  // Пересоздаем квадрат

            // Автоматически центрируем на квадрате
            if (zoomManager != null) {
                zoomManager.setZoom(0.33);
                zoomManager.centerOnBorder();
            }

            refreshAvailableDevices();
            statusLabel.setText("Загружена схема: " + scheme.getName() + " (зум 33%)");
        } catch (Exception e) {
            handleSchemeLoadError(scheme, e);
        }
    }

    /**
     * Загрузка содержимого схемы
     */
    private void loadSchemeContent(Scheme scheme) {
        loadShapesFromScheme(scheme);
        loadDevicesFromScheme(scheme);
    }

    /**
     * Очистка панели схемы
     */
    private void clearSchemePane() {
        schemePane.getChildren().clear();
        shapeManager.deselectShape();
        shapeManager.clearUndoRedo();
        shapeService.removeAllShapes();
    }

    /**
     * Очистка схемы (полная: pane, селекты, undo, статус)
     */
    private void clearScheme() {
        boolean confirm = CustomAlert.showConfirmation("Очистка схемы",
                "Это действие удалит ВСЕ фигуры и приборы с панели. Продолжить?");
        if (!confirm) return;

        try {
            // ВАЖНО: Сначала работа с БД, потом с UI
            if (currentScheme != null) {
                // 1. Удаляем приборы из БД
                if (deviceLocationDAO != null) {
                    int deletedCount = deviceLocationDAO.deleteAllLocationsForScheme(currentScheme.getId());
                    System.out.println("Удалено приборов из БД: " + deletedCount);
                }

                // 2. Сохраняем пустую схему (без фигур) в БД
                currentScheme.setData("{}");
                if (schemeDAO != null) {
                    boolean saved = schemeDAO.updateScheme(currentScheme);
                    System.out.println("Схема сохранена в БД: " + saved);
                }
            }

            // 3. Очищаем UI (панель, фигуры, etc.)
            clearSchemePane();

            // 4. ВОССТАНАВЛИВАЕМ КВАДРАТ - это ключевое исправление!
            addVisibleBorder();

            // 5. Обновляем доступные приборы
            refreshAvailableDevices();

            // 6. Центрируем на квадрате
            if (zoomManager != null) {
                zoomManager.setZoom(0.33);
                zoomManager.centerOnBorder();
            }

            statusLabel.setText("Схема полностью очищена (зум 33%)");

        } catch (Exception e) {
            LOGGER.severe("Ошибка при очистке схемы: " + e.getMessage());
            CustomAlert.showError("Ошибка", "Не удалось полностью очистить схему: " + e.getMessage());
        }
    }

    /**
     * Загрузка фигур из схемы
     */
    private void loadShapesFromScheme(Scheme scheme) {
        statusLabel.setText("Очистка старых фигур...");
        if (shapeService != null) {
            try {
                shapeService.clearAllShapes();  // Если метод есть
            } catch (Exception e) {  // Fallback если no clearAllShapes
                LOGGER.warning("clearAllShapes failed: " + e.getMessage() + " — fallback to manual");
                if (schemePane != null) schemePane.getChildren().clear();
                // Manual clear list in service (add method to ShapeService if needed)
                try {
                    shapeService.removeAllShapes();  // Existing method
                } catch (Exception ex) {
                    LOGGER.warning("removeAllShapes also failed");
                }
            }
        }
        String schemeData = scheme.getData();
        // Лог: полная инфа о данных (безопасно)
        System.out.println("=== Загрузка фигур для схемы: " + scheme.getName() + " ===");
        // ... (остальной код без изменений: System.out, if (isValidSchemeData), parse, deserialize, etc.)
        if (schemeData != null && !schemeData.isEmpty()) {
            System.out.println("Содержимое scheme.data: " + safeSubstring(schemeData, 100));
        }
        if (isValidSchemeData(schemeData) && shapeService != null) {
            List<String> shapeData = parseShapeData(schemeData);
            // ... (log, sample)
            int beforeCount = shapeService.getShapeCount();
            shapeService.deserializeAndAddAll(shapeData);
            int afterCount = shapeService.getShapeCount();
            System.out.println("Загружено фигур: " + (afterCount - beforeCount) + " (всего: " + afterCount + ")");
        } else {
            String shortData = safeSubstring(schemeData, 50);
            System.out.println("WARNING: Invalid scheme data для '" + scheme.getName() + "': '" + shortData + "' — пропускаем загрузку фигур");
        }
        System.out.println("=== Конец загрузки фигур ===");
    }

    /**
     * Добавление видимого красного прямоугольника
     */
    private void addVisibleBorder() {
        if (borderRect != null && schemePane != null) {
            schemePane.getChildren().remove(borderRect);
        }

        // ПРЯМОУГОЛЬНЫЕ размеры (шире чем выше)
        double borderWidth = 1600;  // Ширина
        double borderHeight = 1200; // Высота
        borderRect = new Rectangle(borderWidth, borderHeight);
        borderRect.setId("visibleBorder");
        borderRect.setStroke(Color.DARKRED); // Темно-красный вместо марун
        borderRect.setStrokeWidth(10.0);     // Еще жирнее
        borderRect.setStrokeType(StrokeType.OUTSIDE); // Обводка снаружи
        borderRect.setFill(Color.TRANSPARENT);
        borderRect.setMouseTransparent(true);

        // Добавляем эффект тени для лучшей видимости
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.color(0.7, 0.7, 0.7, 0.7));
        shadow.setRadius(5);
        shadow.setOffsetX(2);
        shadow.setOffsetY(2);
        borderRect.setEffect(shadow);

        borderRect.setLayoutX(0);
        borderRect.setLayoutY(0);

        schemePane.getChildren().addFirst(borderRect);
        schemePane.setPrefWidth(borderWidth);
        schemePane.setPrefHeight(borderHeight);

        System.out.println("DEBUG: Rectangular border created at (0,0) with size " +
                borderWidth + "x" + borderHeight);
    }

    /**
     * Проверка валидности данных схемы
     */
    private boolean isValidSchemeData(String schemeData) {
        return schemeData != null && !schemeData.trim().isEmpty();
    }

    /**
     * Проверяет, выходят ли figure'ы за видимую область ScrollPane (viewport).
     * Возвращает true, если хотя бы одна figure не видна полностью без scroll'a — тогда нужен fitZoom.
     */
    private boolean hasInvisibleFigures() {
        if (schemeScrollPane == null || schemePane == null) return false;
        Bounds viewportBounds = schemeScrollPane.getViewportBounds();
        if (viewportBounds == null || viewportBounds.getWidth() == 0 || viewportBounds.getHeight() == 0) {
            return false;
        }
        for (Node node : schemePane.getChildren()) {
            if (node instanceof ShapeBase shape) {
                Bounds shapeBounds = shape.getBoundsInParent();
                if (shapeBounds != null &&
                        (!viewportBounds.contains(shapeBounds.getMinX(), shapeBounds.getMinY()) ||
                                !viewportBounds.contains(shapeBounds.getMaxX(), shapeBounds.getMaxY()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Парсинг данных фигур из строки
     */
    private List<String> parseShapeData(String schemeData) {
        if (schemeData == null || schemeData.trim().isEmpty()) {
            return new ArrayList<>();
        }
        if (schemeData.equals("{}")) {  // Специальный случай пустой схемы
            return new ArrayList<>();
        }

        // Split по ";" и фильтр
        return Arrays.stream(schemeData.split(";"))
                .map(String::trim)  // Убираем пробелы
                .filter(s -> !s.isEmpty())  // Не пустые
                .filter(s -> s.contains("|"))  // Только валидные shapes (тип| x |y|w|h)
                .filter(s -> s.split("\\|").length >= 5)  // Минимум 5 частей (тип + 4 coords)
                .collect(Collectors.toList());
    }

    /**
     * Загрузка устройств из схемы
     */
    private void loadDevicesFromScheme(Scheme scheme) {
        List<DeviceLocation> locations = deviceLocationDAO.getLocationsBySchemeId(scheme.getId());

        for (DeviceLocation location : locations) {
            Device device = deviceDAO.getDeviceById(location.getDeviceId());
            if (device != null) {
                Node deviceNode = deviceIconService.createDeviceIcon(
                        location.getX(), location.getY(), device, currentScheme
                );
                schemePane.getChildren().add(deviceNode);
            }
        }

        LOGGER.info("Загружено " + locations.size() + " устройств на схему");
    }

    /**
     * Обработка ошибки загрузки схемы
     */
    private void handleSchemeLoadError(Scheme scheme, Exception e) {
        LOGGER.severe("Ошибка при загрузке схемы '" + scheme.getName() + "': " + e.getMessage());
        CustomAlert.showError("Ошибка загрузки", "Не удалось загрузить схему: " + e.getMessage());
        statusLabel.setText("Ошибка загрузки схемы");
    }

    // ============================================================
    // DEVICE MANAGEMENT
    // ============================================================

    /**
     * Обновление списка доступных устройств для текущей схемы
     */
    private void refreshAvailableDevices() {
        if (!validateRefreshConditions()) return;

        ObservableList<Device> availableDevices = calculateAvailableDevices();
        deviceComboBox.setItems(availableDevices);
        updateDeviceStatus(availableDevices.size());
    }

    /**
     * Проверка условий для обновления списка устройств
     */
    private boolean validateRefreshConditions() {
        if (deviceList == null || deviceList.isEmpty()) {
            deviceComboBox.setItems(FXCollections.observableArrayList());
            statusLabel.setText("Нет доступных приборов");
            return false;
        }

        if (currentScheme == null) {
            deviceComboBox.setItems(FXCollections.observableArrayList());
            statusLabel.setText("Схема не выбрана");
            return false;
        }

        return true;
    }

    /**
     * Расчет доступных устройств для текущей схемы
     */
    private ObservableList<Device> calculateAvailableDevices() {
        String selectedSchemeName = currentScheme.getName();
        List<Integer> usedDeviceIds = getUsedDeviceIds();
        List<Integer> currentSchemeDeviceIds = getCurrentSchemeDeviceIds();

        return deviceList.stream()
                .filter(device -> selectedSchemeName.equals(device.getLocation()))
                .filter(device -> !usedDeviceIds.contains(device.getId()))
                .filter(device -> !currentSchemeDeviceIds.contains(device.getId()))
                .collect(Collectors.toCollection(FXCollections::observableArrayList));
    }

    /**
     * Получение ID устройств, используемых на других схемах
     */
    private List<Integer> getUsedDeviceIds() {
        return deviceLocationDAO.getAllLocations().stream()
                .filter(location -> location.getSchemeId() != currentScheme.getId())
                .map(DeviceLocation::getDeviceId)
                .collect(Collectors.toList());
    }

    /**
     * Получение ID устройств на текущей схеме
     */
    private List<Integer> getCurrentSchemeDeviceIds() {
        return deviceLocationDAO.getLocationsBySchemeId(currentScheme.getId()).stream()
                .map(DeviceLocation::getDeviceId)
                .collect(Collectors.toList());
    }

    /**
     * Обновление статуса количества устройств
     */
    private void updateDeviceStatus(int availableCount) {
        statusLabel.setText("Доступных приборов: " + availableCount);
    }

    // ============================================================
    // MOUSE EVENT HANDLERS
    // ============================================================

    @FXML
    private void onPaneMousePressed(MouseEvent event) {
        if (schemePane == null || shapeManager == null) return;

        // ПРАВИЛЬНОЕ преобразование координат с учетом скролла
        Point2D panePoint = getAbsolutePaneCoordinates(event.getSceneX(), event.getSceneY());
        double x = panePoint.getX();
        double y = panePoint.getY();

        System.out.println("DEBUG: Mouse pressed at scene(" + event.getSceneX() + "," + event.getSceneY() +
                ") -> pane(" + x + "," + y + ")");

        shapeManager.resetWasResized();
        if (currentTool == ShapeManager.Tool.ADD_DEVICE) {
            addDeviceAt(x, y);
        } else {
            shapeManager.onMousePressedForTool(currentTool, x, y);
        }
    }

    @FXML
    private void onPaneMouseDragged(MouseEvent event) {
        if (currentTool != ShapeManager.Tool.ADD_DEVICE) {
            Point2D panePoint = getAbsolutePaneCoordinates(event.getSceneX(), event.getSceneY());
            shapeManager.onMouseDraggedForTool(currentTool, panePoint.getX(), panePoint.getY());
        }
    }

    @FXML
    private void onPaneMouseReleased(MouseEvent event) {
        if (currentTool == ShapeManager.Tool.TEXT) {
            Point2D panePoint = getAbsolutePaneCoordinates(event.getSceneX(), event.getSceneY());
            double x = panePoint.getX();
            double y = panePoint.getY();

            Optional<String> result = CustomAlert.showTextInputDialog("Добавление текста", "Введите текст для добавления на схему:", "Новый текст");
            if (result.isPresent()) {
                String newText = result.get().trim();
                if (!newText.isEmpty()) {
                    double[] coords = {x, y, 0};
                    try {
                        ShapeBase shape = shapeService.addShape(ShapeType.TEXT, coords);
                        if (shape instanceof TextShape textShape) {
                            textShape.setText(newText);
                            shapeManager.addShape(shape);
                            statusLabel.setText("Текст добавлен: '" + newText + "'");
                            autoSaveScheme();
                        }
                    } catch (Exception e) {
                        LOGGER.severe("ERROR adding TEXT: " + e.getMessage());
                        CustomAlert.showError("Ошибка добавления текста", "Не удалось добавить текст: " + e.getMessage());
                    }
                } else {
                    statusLabel.setText("Текст не добавлен (пустой ввод)");
                }
            }
        } else if (currentTool != ShapeManager.Tool.SELECT) {
            Point2D panePoint = getAbsolutePaneCoordinates(event.getSceneX(), event.getSceneY());
            shapeManager.onMouseReleasedForTool(currentTool, panePoint.getX(), panePoint.getY());
            autoSaveScheme();
        }
        updateStatusAfterMouseRelease();
    }

    /**
     * ПРАВИЛЬНОЕ преобразование координат с учетом скролла и зума
     */
    private Point2D getAbsolutePaneCoordinates(double sceneX, double sceneY) {
        if (schemeScrollPane == null || schemePane == null) {
            return new Point2D(sceneX, sceneY);
        }

        try {
            // 1. Преобразуем scene coordinates в scrollPane local coordinates
            Point2D scrollLocal = schemeScrollPane.sceneToLocal(sceneX, sceneY);

            // 2. Получаем смещение скролла
            double scrollX = schemeScrollPane.getHvalue() * (schemePane.getWidth() - schemeScrollPane.getViewportBounds().getWidth());
            double scrollY = schemeScrollPane.getVvalue() * (schemePane.getHeight() - schemeScrollPane.getViewportBounds().getHeight());

            // 3. Учитываем скролл и возвращаем абсолютные координаты pane
            double absoluteX = scrollLocal.getX() + scrollX;
            double absoluteY = scrollLocal.getY() + scrollY;

            // 4. Ограничиваем координаты рамкой красного прямоугольника
            absoluteX = Math.max(0, Math.min(absoluteX, 1600)); // Ширина прямоугольника
            absoluteY = Math.max(0, Math.min(absoluteY, 1200)); // Высота прямоугольника

            // ОТЛАДОЧНЫЙ ВЫВОД - можно убрать после проверки
            if (absoluteY > 1000) { // Логируем только для нижней части
                System.out.println("DEBUG COORDS: scene(" + sceneX + "," + sceneY +
                        ") -> scrollLocal(" + scrollLocal.getX() + "," + scrollLocal.getY() +
                        ") + scrollOffset(" + scrollX + "," + scrollY +
                        ") -> absolute(" + absoluteX + "," + absoluteY + ")");
            }

            return new Point2D(absoluteX, absoluteY);

        } catch (Exception e) {
            System.err.println("ERROR in coordinate conversion: " + e.getMessage());
            return new Point2D(sceneX, sceneY);
        }
    }

    /**
     * Обновление статуса после отпускания мыши
     */
    private void updateStatusAfterMouseRelease() {
        if (currentTool == ShapeManager.Tool.SELECT) {
            if (shapeManager.wasDraggedInSelect()) {
                statusLabel.setText(isShapeSelected() ? "Фигура перемещена" : "");
            } else if (shapeManager.wasResized()) {
                statusLabel.setText("Фигура изменена");
            }
        }
    }

    // ============================================================
    // DEVICE INTERACTION
    // ============================================================

    /**
     * Добавление устройства на схему в заданную позицию
     */
    private void addDeviceAt(double x, double y) {
        // Уже используем правильные координаты из getAbsolutePaneCoordinates
        Device selectedDevice = deviceComboBox.getValue();
        if (selectedDevice == null) {
            CustomAlert.showWarning("Добавление прибора", "Выберите прибор из списка!");
            return;
        }

        try {
            if (deviceIconService != null) {
                Node deviceNode = deviceIconService.createDeviceIcon(x, y, selectedDevice, currentScheme);
                if (schemePane != null) schemePane.getChildren().add(deviceNode);
                if (currentScheme != null && deviceLocationDAO != null) {
                    DeviceLocation location = new DeviceLocation(
                            selectedDevice.getId(), currentScheme.getId(), x, y
                    );
                    deviceLocationDAO.addDeviceLocation(location);
                    autoSaveScheme();
                }
                refreshAvailableDevices();
                if (statusLabel != null) statusLabel.setText("Прибор добавлен: " + selectedDevice.getName());
                CustomAlert.showInfo("Добавление", "Прибор '" + selectedDevice.getName() + "' добавлен на схему");
            }
        } catch (Exception e) {
            LOGGER.severe("Ошибка добавления устройства: " + e.getMessage());
            CustomAlert.showError("Ошибка", "Не удалось добавить прибор: " + e.getMessage());
        }
    }

    /**
     * Сохранение позиции устройства
     */
    private void saveDeviceLocation(Node node, Device device) {
        if (device != null && currentScheme != null) {
            double x = node.getLayoutX();
            double y = node.getLayoutY();

            // Корректировка для Circle (центр вместо левого верхнего угла)
            if (node instanceof Circle) {
                x -= 10;
                y -= 10;
            }

            DeviceLocation location = new DeviceLocation(device.getId(), currentScheme.getId(), x, y);
            deviceLocationDAO.updateDeviceLocation(location);
        }

    }
    // ============================================================
    // SCHEME SAVING
    // ============================================================

    /**
     * Сохранение текущей схемы
     */
    private void saveCurrentScheme() {
        if (currentScheme == null) {
            statusLabel.setText("Схема не выбрана");
            return;
        }

        shapeManager.deselectShape();  // Сброс выделения
        saveSchemeData();  // Сохраняем фигуры
        saveDeviceLocations();  // Сохраняем приборы (уже работает)

        statusLabel.setText("Схема сохранена: " + currentScheme.getName());
        LOGGER.info("Схема сохранена: " + currentScheme.getName() + ", фигур: " + shapeService.getShapeCount());
    }

    /**
     * Сохранение данных схемы (фигуры)
     */
    private void saveSchemeData() {
        // Новое: Лог перед сериализацией (чтобы увидел count после delete)
        int shapeCount = shapeService.getShapeCount();
        System.out.println("Before saveSchemeData: shapes count = " + shapeCount + ", scheme: " + (currentScheme != null ? currentScheme.getName() : "null"));

        List<String> shapeData = shapeService.serializeAll();
        String schemeData = shapeData.isEmpty() ? "{}" : String.join(";", shapeData);

        if (shapeCount == 0) {
            System.out.println("Empty shapes: saving '{}' to scheme.data");
        } else {
            System.out.println("Saving " + shapeData.size() + " shapes: sample=" + safeSubstring(schemeData, 50));
        }

        if (currentScheme == null) {
            System.out.println("WARNING: No current scheme — skip save");
            return;
        }

        currentScheme.setData(schemeData);

        // Новое: Проверь update в DAO (лог if fail)
        boolean updated = schemeDAO.updateScheme(currentScheme);
        if (updated) {
            System.out.println("SUCCESS: Scheme data updated in DB (ID=" + currentScheme.getId() + ", data len=" + schemeData.length() + ", shapes=" + shapeCount + ")");
        } else {
            System.out.println("ERROR: Failed to update scheme in DB! Check DAO/SQL (ID=" + currentScheme.getId() + ")");
        }
    }

    // Новое: Метод auto-save (вызывается после addShape/remove/resize)
    private void autoSaveScheme() {
        if (currentScheme != null) {
            saveSchemeData();  // Сохраняет shapeService.serializeAll() в scheme.data
            LOGGER.info("Auto-save: Схема '" + currentScheme.getName() + "', фигур: " + shapeService.getShapeCount());
            statusLabel.setText("Автосохранение: " + currentScheme.getName());  // Опционально: миг на статус
        }
    }

    /**
     * Сохранение позиций устройств
     */
    private void saveDeviceLocations() {
        int savedCount = 0;
        for (Node node : schemePane.getChildren()) {
            if (isDeviceNode(node)) {
                Device device = (Device) node.getUserData();
                saveDeviceLocation(node, device);
                savedCount++;
            }
        }
        LOGGER.info("Сохранено " + savedCount + " позиций устройств");
    }

    /**
     * Проверка, является ли узел устройством
     */
    private boolean isDeviceNode(Node node) {
        return (node instanceof Circle circle && circle.getFill() == Color.BLUE) ||
                (node instanceof ImageView);
    }

    /**
     * Безопасный substring: берёт min(длина строки, maxLen), не крашит на коротких строках
     *
     * @param str    исходная строка (может быть null)
     * @param maxLen максимальная длина
     * @return усечённая строка или полная
     */
    private String safeSubstring(String str, int maxLen) {
        if (str == null || str.isEmpty()) {
            return "[пустая строка]";
        }
        if (str.length() <= maxLen) {
            return str;
        }
        return str.substring(0, maxLen) + "...";
    }

    // ============================================================
    // SHAPE MANAGEMENT
    // ============================================================

    /**
     * Проверка, есть ли выделенная фигура
     */
    private boolean isShapeSelected() {
        return shapeManager.getSelectedShape() != null;
    }

    /**
     * Удаление выделенной фигуры
     */
    private void deleteSelectedShape() {
        ShapeHandler selected = shapeManager.getSelectedShape();
        if (selected != null) {
            shapeManager.removeShape((Node) selected);
            autoSaveScheme();
            shapeManager.deselectShape();
            statusLabel.setText("Фигура удалена");
        }
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Обновление списков схем и устройств
     */
    public void refreshSchemesAndDevices() {
        loadSchemes();
        loadDevices();
        refreshAvailableDevices();
        LOGGER.info("Списки схем и устройств обновлены");
    }
}