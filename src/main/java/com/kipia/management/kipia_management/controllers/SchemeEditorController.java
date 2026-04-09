package com.kipia.management.kipia_management.controllers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kipia.management.kipia_management.managers.ClipboardManager;
import com.kipia.management.kipia_management.managers.ShapeManager;
import com.kipia.management.kipia_management.models.*;
import com.kipia.management.kipia_management.services.*;
import com.kipia.management.kipia_management.shapes.*;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;
import com.kipia.management.kipia_management.utils.LoadingIndicator;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Контроллер редактора схем с оптимизированной архитектурой фигур.
 * Управляет созданием схем, работой с устройствами и координацией компонентов
 *
 * @author vladimir_shi
 * @since 30.09.2025
 */
@SuppressWarnings("unchecked")
public class SchemeEditorController {

    // ============================================================
    // FXML COMPONENTS
    // ============================================================

    @FXML
    private StackPane rootPane;  // Корневой контейнер для индикатора загрузки
    @FXML
    private BorderPane contentBox;  // Контейнер с контентом
    @FXML
    private ComboBox<Scheme> schemeComboBox;
    @FXML
    private Button saveSchemeBtn, lineToolBtn, rectToolBtn, textToolBtn, rhombusToolBtn;
    @FXML
    private Button undoBtn, redoBtn, ellipseToolBtn, clearSchemeBtn, deleteSchemeBtn, resetViewBtn;
    @FXML
    private AnchorPane schemePane;
    @FXML
    private ScrollPane schemeScrollPane;
    @FXML
    private Label statusLabel;
    @FXML
    private Label zoomLabel;
    @FXML
    private Label schemeUpdatedLabel;

    // ============================================================
    // DEPENDENCIES & SERVICES
    // ============================================================

    private static final Logger LOGGER = LogManager.getLogger(SchemeEditorController.class);
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    private DeviceDAO deviceDAO;
    private SchemeDAO schemeDAO;
    private DeviceLocationDAO deviceLocationDAO;
    private ShapeManager shapeManager;
    private ShapeService shapeService;
    private DeviceIconService deviceIconService;
    private SchemeSaver schemeSaver;
    
    // Индикатор загрузки
    private LoadingIndicator loadingIndicator;

    // ============================================================
    // DATA MODELS
    // ============================================================

    private Scheme currentScheme;
    private ObservableList<Device> deviceList;
    private ShapeManager.Tool currentTool = null;

    // ============================================================
    // CANVAS STATE
    // ============================================================

    private final CanvasState canvasState = new CanvasState();
    private Point2D lastMiddleMousePos;
    private final javafx.scene.transform.Scale canvasScale = new javafx.scene.transform.Scale(1, 1, 0, 0);
    private final javafx.scene.transform.Translate canvasTranslate = new javafx.scene.transform.Translate(0, 0);

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
            setupUIComponents();
            setupPaneEventHandlers();
            setupToolButtons();
            applyButtonStyles();
            setupWindowCloseHandler();
            setupCanvasTransforms();
        } catch (Exception e) {
            LOGGER.error("Ошибка в initialize(): {}", e.getMessage(), e);
        }
    }

    /**
     * Основная инициализация после внедрения зависимостей
     */
    public void init() {
        try {
            validateDAODependencies();
            
            // Инициализация индикатора загрузки
            loadingIndicator = new LoadingIndicator("Загрузка схемы...");
            if (rootPane != null) {
                rootPane.getChildren().add(loadingIndicator.getOverlay());
            }
            
            // Скрываем контент до загрузки
            hideContentBeforeLoad();
            
            // Запускаем асинхронную загрузку
            loadDataAsync();
        } catch (Exception e) {
            LOGGER.error("Ошибка в init(): {}", e.getMessage(), e);
            if (statusLabel != null) statusLabel.setText("Ошибка запуска: " + e.getMessage());
        }
    }
    
    /**
     * Скрывает контент до загрузки данных
     */
    private void hideContentBeforeLoad() {
        if (contentBox != null) {
            contentBox.setOpacity(0);
        }
    }
    
    /**
     * Показывает контент после загрузки данных
     */
    private void showContentAfterLoad() {
        if (contentBox != null) {
            contentBox.setOpacity(1);
        }
    }
    
    /**
     * Асинхронная загрузка данных с индикатором загрузки
     */
    private void loadDataAsync() {
        Platform.runLater(() -> loadingIndicator.show());
        
        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long startTime = System.currentTimeMillis();
                
                // Инициализация сервисов и загрузка данных
                Platform.runLater(() -> {
                    initializeServices();
                    loadInitialData();
                    setupInitialScheme();
                    statusLabel.setText("Готов - выберите инструмент или работайте с фигурами");
                    refreshAvailableDevices();
                });
                
                // Умная задержка
                long elapsedTime = System.currentTimeMillis() - startTime;
                long minDisplayTime = 500;
                
                if (elapsedTime < minDisplayTime) {
                    Thread.sleep(minDisplayTime - elapsedTime);
                }
                
                return null;
            }
        };
        
        loadTask.setOnSucceeded(_ -> {
            showContentAfterLoad();
            loadingIndicator.hide();
        });
        
        loadTask.setOnFailed(_ -> {
            LOGGER.error("Ошибка загрузки схемы: {}", loadTask.getException().getMessage());
            CustomAlertDialog.showError("Ошибка", "Не удалось загрузить схему");
            showContentAfterLoad();
            loadingIndicator.hide();
        });
        
        new Thread(loadTask).start();
    }

    /**
     * Инициализация сервисов и менеджеров
     */
    private void initializeServices() {
        // Создаём менеджер фигур
        this.shapeManager = new ShapeManager(schemePane, null,
                canUndo -> undoBtn.setDisable(!canUndo),
                canRedo -> redoBtn.setDisable(!canRedo)
        );
        shapeManager.setStatusSetter(statusLabel::setText);
        shapeManager.setOnSelectCallback(_ -> {
            // Сбрасываем текущий инструмент при выделении фигуры
            resetCurrentTool();
            // Вызываем обработчик выделения
            handleShapeSelected();
        });
        shapeManager.setCanvasBounds(canvasState.getWidth(), canvasState.getHeight());


        // Создаём фабрику фигур
        ShapeFactory shapeFactory = new ShapeFactory(
                schemePane,
                statusLabel::setText,
                shapeManager::selectShape,
                shapeManager
        );

        // Создаём сервис фигур
        this.shapeService = new ShapeService(shapeFactory);

        // Заменяем shapeService в менеджере
        shapeManager.setShapeService(shapeService);

        // Создаём сервис для иконок устройств
        this.deviceIconService = new DeviceIconService(
                schemePane,
                this::saveDeviceLocation,
                deviceLocationDAO,
                this::refreshAvailableDevices,
                currentScheme,
                deviceDAO
        );

        shapeManager.setOnShapeSelected(this::handleShapeSelection);
        shapeManager.setOnShapeDeselected(this::handleShapeDeselection);

        this.schemeSaver = new SchemeSaver(schemeDAO, deviceLocationDAO, shapeService, schemePane, deviceDAO);
        // Любая мутация фигур → помечаем схему как изменённую
        shapeManager.setOnChangeCallback(schemeSaver::markDirty);

        // Устанавливаем размеры канваса из схемы (если есть)
        if (currentScheme != null) {
            loadCanvasStateFromScheme(currentScheme);
        }
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
    private void setupUIComponents() {
        setupSchemeComboBox();
    }

    /**
     * Настройка ComboBox для схем
     */
    private void setupSchemeComboBox() {
        schemeComboBox.setConverter(createSchemeConverter());
        schemeComboBox.valueProperty().addListener((_, oldV, newV) -> {
            if (newV != null && !newV.equals(oldV)) {
                loadScheme(newV);
            }
        });
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
     * Настройка обработчиков событий панели
     */
    private void setupPaneEventHandlers() {
        if (schemePane != null) {
            schemePane.setOnMouseClicked(_ -> schemePane.requestFocus());
            schemePane.setFocusTraversable(true);
        }
        setupPaneKeyHandlers();
    }

    /**
     * Настройка обработчиков клавиш для панели
     */
    private void setupPaneKeyHandlers() {
        schemePane.setOnKeyPressed(event -> {
            if (isShapeSelected()) {
                if (event.isControlDown()) {
                    switch (event.getCode()) {
                        case C -> copySelectedShape();
                        case V -> pasteShape();
                    }
                } else {
                    switch (event.getCode()) {
                        case DELETE, BACK_SPACE -> deleteSelectedShape();
                    }
                }
            }
        });
    }

    /**
     * Настройка обработчика закрытия окна
     */
    private void setupWindowCloseHandler() {
        schemePane.sceneProperty().addListener((_, _, newScene) -> {
            if (newScene != null) {
                updateButtonIcons();
                newScene.getStylesheets().addListener((javafx.collections.ListChangeListener<String>) _ -> updateButtonIcons());
                newScene.windowProperty().addListener((_, _, newWindow) -> {
                    if (newWindow != null) {
                        newWindow.setOnCloseRequest(_ -> schemeSaver.saveOnExit(currentScheme));
                    }
                });
            }
        });
    }

    /**
     * Копирование выделенной фигуры
     */
    private void copySelectedShape() {
        ShapeHandler selected = shapeManager.getSelectedShape();
        if (selected instanceof ShapeBase shapeBase) {
            shapeBase.copyToClipboard();
            statusLabel.setText("Фигура скопирована");
        }
    }

    /**
     * Вставка фигуры из буфера обмена
     */
    private void pasteShape() {
        if (!ClipboardManager.hasShapeData()) {
            return;
        }
        try {
            String shapeData = ClipboardManager.getCopiedShapeData();
            ShapeBase pastedShape = ShapeBase.deserialize(shapeData, schemePane,
                    statusLabel::setText, shapeManager::selectShape, shapeManager);
            if (pastedShape != null) {
                double[] lastPos = getPastePosition();
                pastedShape.setPosition(lastPos[0] + 20, lastPos[1] + 20);
                pastedShape.addToPane();
                shapeManager.addShape(pastedShape);
                statusLabel.setText("Фигура вставлена");
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка при вставке фигуры: {}", e.getMessage(), e);
            statusLabel.setText("Ошибка вставки фигуры");
        }
    }

    /**
     * Получает позицию для вставки (смещение от последней фигуры или центр)
     */
    private double[] getPastePosition() {
        ShapeHandler selected = shapeManager.getSelectedShape();
        if (selected != null) {
            return selected.getPosition();
        }
        return new double[]{400, 300};
    }

    /**
     * Получить объект сервиса сохранений
     *
     * @return - объект сервис
     */
    public SchemeSaver getSchemeSaver() {
        return schemeSaver;
    }

    /**
     * Получить текущую схему
     *
     * @return - текущая схема
     */
    public Scheme getCurrentScheme() {
        return currentScheme;
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
        lineToolBtn.setOnAction(_ -> toggleTool(ShapeManager.Tool.LINE, "Инструмент: Линия - кликните и перетащите для рисования"));
        rectToolBtn.setOnAction(_ -> toggleTool(ShapeManager.Tool.RECTANGLE, "Инструмент: Прямоугольник - кликните и перетащите для рисования"));
        ellipseToolBtn.setOnAction(_ -> toggleTool(ShapeManager.Tool.ELLIPSE, "Инструмент: Эллипс - кликните и перетащите для рисования"));
        rhombusToolBtn.setOnAction(_ -> toggleTool(ShapeManager.Tool.RHOMBUS, "Инструмент: Ромб - кликните и перетащите для рисования"));
        textToolBtn.setOnAction(_ -> toggleTool(ShapeManager.Tool.TEXT, "Инструмент: Текст - кликните для добавления текста"));
    }

    /**
     * Настройка кнопок действий
     */
    private void setupActionButtons() {
        undoBtn.setOnAction(_ -> shapeManager.undo());
        redoBtn.setOnAction(_ -> shapeManager.redo());
        saveSchemeBtn.setOnAction(_ -> {
            schemeSaver.selectButtonSaveScheme(currentScheme);
            updateSchemeTimestamp(currentScheme);
        });
        clearSchemeBtn.setOnAction(_ -> clearScheme());
        deleteSchemeBtn.setOnAction(_ -> deleteScheme());  // ⭐⭐ НОВОЕ ⭐⭐
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
                lineToolBtn, rectToolBtn, ellipseToolBtn,
                rhombusToolBtn, textToolBtn
        );
        for (Button button : toolButtons) {
            StyleUtils.setupShapeToolButton(button);
        }
    }

    /**
     * Применение стилей к кнопкам действий
     */
    private void applyActionButtonStyles() {
        undoBtn.getStyleClass().add("tool-button");
        redoBtn.getStyleClass().add("tool-button");
        saveSchemeBtn.getStyleClass().add("tool-button");
        clearSchemeBtn.getStyleClass().add("tool-button");
        deleteSchemeBtn.getStyleClass().add("tool-button");
    }

    // ============================================================
    // TOOL MANAGEMENT
    // ============================================================

    /**
     * Переключение инструмента - при повторном клике отключается
     */
    private void toggleTool(ShapeManager.Tool tool, String statusMessage) {
        // Если кликнули на уже активный инструмент - отключаем его
        if (currentTool == tool) {
            currentTool = null;
            // Обновляем статус
            if (statusLabel != null) {
                statusLabel.setText("Инструмент отключен - выберите инструмент или работайте с фигурами");
            }
        } else {
            // Включаем новый инструмент
            currentTool = tool;
            // ОБНОВЛЯЕМ СТАТУС
            if (statusLabel != null) {
                statusLabel.setText(statusMessage);
            }
        }
        // Снимаем выделение при смене инструмента
        if (shapeManager != null) {
            shapeManager.deselectShape();
        }
        // Обновляем стили кнопок
        updateToolButtonStyles();
    }

    /**
     * Обновление стилей кнопок инструментов
     */
    private void updateToolButtonStyles() {
        List<Button> toolButtons = Arrays.asList(
                lineToolBtn, rectToolBtn, ellipseToolBtn,
                rhombusToolBtn, textToolBtn
        );
        // Сбрасываем все активные стили
        toolButtons.forEach((Button btn) -> StyleUtils.setToolButtonActive(btn, false, "tool-button-active"));
        // Применяем активный стиль к текущему инструменту (если он есть)
        if (currentTool != null) {
            Button activeButton = getButtonForTool(currentTool);
            if (activeButton != null) {
                StyleUtils.setToolButtonActive(activeButton, true, "tool-button-active");
            }
        }
    }

    /**
     * Получение кнопки для указанного инструмента
     */
    private Button getButtonForTool(ShapeManager.Tool tool) {
        return switch (tool) {
            case LINE -> lineToolBtn;
            case RECTANGLE -> rectToolBtn;
            case ELLIPSE -> ellipseToolBtn;
            case RHOMBUS -> rhombusToolBtn;
            case TEXT -> textToolBtn;
        };
    }

    /**
     * Обработка двойного клика - временно сбрасываем инструмент
     */
    private void handleShapeSelection() {
        // НЕ сбрасываем инструмент при выделении фигуры, пользователь может продолжать работать с текущим инструментом
        statusLabel.setText("Фигура выделена - используйте ручки для изменения или продолжайте рисование");
    }

    /**
     * Обработка выделения фигуры - без сброса инструмента
     */
    private void handleShapeSelected() {
        // НЕ сбрасываем инструмент при выделении фигуры, пользователь может продолжать работать с текущим инструментом
        if (statusLabel != null) {
            if (currentTool != null) {
                statusLabel.setText("Фигура выделена - используйте ручки для изменения или продолжайте рисование с инструментом: " + getToolName(currentTool));
            } else {
                statusLabel.setText("Фигура выделена - используйте ручки для изменения");
            }
        }
    }

    /**
     * Обработка снятия выделения - восстанавливаем инструмент
     */
    private void handleShapeDeselection() {
        // Обновляем статус в зависимости от состояния инструмента
        if (currentTool != null) {
            statusLabel.setText("Инструмент: " + getToolName(currentTool) + " - кликните на схему для создания");
        } else {
            statusLabel.setText("Готов - выберите инструмент или работайте с фигурами");
        }
        // Обновляем стили кнопок (на всякий случай)
        updateToolButtonStyles();
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
        LOGGER.info("Загружено {} устройств", deviceList.size());
    }

    /**
     * Загрузка списка схем на основе уникальных расположений устройств
     */
    private void loadSchemes() {
        List<String> locations = deviceDAO.getDistinctLocations();
        createOrLoadSchemes(locations);
        
        // ⭐⭐ НОВОЕ: Загружаем ВСЕ схемы из БД (включая без приборов) ⭐⭐
        List<Scheme> allSchemes = schemeDAO.getAllSchemes();
        updateSchemeComboBox(allSchemes);
    }

    /**
     * Создание или загрузка схем для указанных расположений
     */
    private void createOrLoadSchemes(List<String> locations) {
        List<Scheme> schemes = new ArrayList<>();
        for (String location : locations) {
            Scheme scheme = findOrCreateScheme(location);
            if (scheme != null) {
                schemes.add(scheme);
            }
        }
    }

    /**
     * Поиск или создание схемы для указанного расположения
     */
    private Scheme findOrCreateScheme(String location) {
        Scheme scheme = schemeDAO.findSchemeByName(location);
        if (scheme == null) {
            scheme = createAutoScheme(location);
            if (schemeDAO.addScheme(scheme)) {
                LOGGER.info("Создана автоматическая схема для: {}", location);
            } else {
                LOGGER.warn("Не удалось создать схему для: {}", location);
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
        LOGGER.info("Загружено {} схем", schemeList.size());
    }

    /**
     * Обработка случая, когда схемы не найдены
     */
    private void handleNoSchemesFound() {
        LOGGER.warn("Список схем пуст");
        CustomAlertDialog.showWarning("Загрузка схем",
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
    // CANVAS STATE METHODS
    // ============================================================

    private void setupCanvasTransforms() {
        // Привязываем трансформации к schemePane
        schemePane.getTransforms().addAll(canvasTranslate, canvasScale);

        // Слушаем изменения canvasState и применяем к трансформациям
        canvasState.scaleProperty().addListener((_, _, newVal) -> {
            canvasScale.setX(newVal.doubleValue());
            canvasScale.setY(newVal.doubleValue());
            updateZoomLabel();
        });
        canvasState.offsetXProperty().addListener((_, _, newVal) ->
                canvasTranslate.setX(newVal.doubleValue()));
        canvasState.offsetYProperty().addListener((_, _, newVal) ->
                canvasTranslate.setY(newVal.doubleValue()));

        // Зум через ScrollPane — перехватываем на уровне schemeScrollPane
        schemeScrollPane.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                double factor = event.getDeltaY() > 0 ? 1.1 : 0.9;
                double oldScale = canvasState.getScale();
                double newScale = Math.max(0.5, Math.min(3.0, oldScale * factor)); // 50%-300%

                if (newScale == oldScale) {
                    event.consume();
                    return;
                }

                double mouseSceneX = event.getSceneX();
                double mouseSceneY = event.getSceneY();
                double worldX = (mouseSceneX - canvasTranslate.getX()) / oldScale;
                double worldY = (mouseSceneY - canvasTranslate.getY()) / oldScale;

                canvasState.setScale(newScale);
                canvasState.setOffsetX(mouseSceneX - worldX * newScale);
                canvasState.setOffsetY(mouseSceneY - worldY * newScale);
                clampPanOffset();
                event.consume();
            }
            // Прокрутку без Ctrl полностью блокируем (скроллбары убраны)
            event.consume();
        });
        setupMiddleMousePan();
    }

    /**
     * Поиск узла (фигуры или прибора) в указанной мировой позиции
     * Обновленная версия с использованием ShapeUtils
     */
    private Node findNodeAtWorldPosition(double worldX, double worldY) {

        // Ищем в обратном порядке (сверху вниз)
        for (int i = schemePane.getChildren().size() - 1; i >= 0; i--) {
            Node node = schemePane.getChildren().get(i);

            // Пропускаем неинтерактивные элементы
            if (node instanceof javafx.scene.shape.Rectangle && "visibleBorder".equals(node.getId())) {
                continue;
            }

            // Пропускаем resize handles и rotation handles
            if (node instanceof Circle) {
                continue;
            }

            // Для фигур используем containsWorldPoint
            if (node instanceof ShapeBase shape) {
                if (shape.containsWorldPoint(worldX, worldY)) {
                    return node;
                }
            }
            // Для устройств (иконки) используем bounding box в мировых координатах
            else if (isDeviceNode(node)) {
                double deviceWorldX = node.getLayoutX();
                double deviceWorldY = node.getLayoutY();
                double deviceSize = 60; // Размер иконки

                if (worldX >= deviceWorldX && worldX <= deviceWorldX + deviceSize &&
                        worldY >= deviceWorldY && worldY <= deviceWorldY + deviceSize) {
                    return node;
                }
            }
            // Для остальных элементов используем стандартную проверку
            else {
                // Конвертируем координаты мыши в локальные для узла
                Point2D localPoint = node.screenToLocal(
                        schemePane.localToScreen(worldX, worldY)
                );
                if (localPoint != null && node.contains(localPoint)) {
                    return node;
                }
            }
        }
        return null;
    }

    /**
     * Проверка, является ли узел устройством
     */
    private boolean isDeviceNode(Node node) {
        return node.getUserData() != null &&
                (node.getUserData() instanceof Device ||
                        node.getUserData() instanceof DeviceIconService.DeviceWithRotation);
    }

    /**
     * Загрузить состояние канваса из схемы
     */
    private void loadCanvasStateFromScheme(Scheme scheme) {
        try {
            SchemeData schemeData = gson.fromJson(scheme.getData(), SchemeData.class);
            if (schemeData != null) {
                canvasState.setWidth(schemeData.getWidth());
                canvasState.setHeight(schemeData.getHeight());
                canvasState.setShowGrid(schemeData.isGridEnabled());
                canvasState.setGridSize(schemeData.getGridSize());

                LOGGER.info("Загружены настройки канваса: {}x{}, сетка={}px",
                        canvasState.getWidth(), canvasState.getHeight(),
                        canvasState.getGridSize());
            }
            shapeManager.setCanvasBounds(canvasState.getWidth(), canvasState.getHeight());
        } catch (Exception e) {
            LOGGER.warn("Не удалось загрузить настройки канваса из схемы: {}", e.getMessage());
        }
    }

    /**
     * Ограничивает offset так, чтобы канвас всегда был виден в viewport
     */
    private void clampPanOffset() {
        double scale = canvasState.getScale();
        double scaledW = canvasState.getWidth() * scale;
        double scaledH = canvasState.getHeight() * scale;

        // Размер видимой области
        double viewW = schemeScrollPane.getWidth();
        double viewH = schemeScrollPane.getHeight();

        // Минимальный запас видимости канваса — 100px должны быть в пределах экрана
        double margin = 100.0;

        // Максимальный offset: канвас не уходит левее/выше viewport
        double maxX =  viewW - margin;
        double maxY =  viewH - margin;

        // Минимальный offset: правый/нижний край канваса не уходит дальше margin
        double minX = -(scaledW - margin);
        double minY = -(scaledH - margin);

        canvasState.setOffsetX(Math.max(minX, Math.min(maxX, canvasState.getOffsetX())));
        canvasState.setOffsetY(Math.max(minY, Math.min(maxY, canvasState.getOffsetY())));
    }

    /**
     * Обновить отображение канваса (сетка, фон и т.д.)
     */
    private void updateCanvasDisplay() {
        // Очищаем старые визуальные элементы
        schemePane.getChildren().removeIf(node ->
                node instanceof javafx.scene.shape.Rectangle && "canvasBackground".equals(node.getId()) ||
                        node instanceof javafx.scene.shape.Line && "gridLine".equals(node.getId())
        );

        // Рисуем фон канваса
        Rectangle background = new Rectangle(
                0, 0,
                canvasState.getWidth(),
                canvasState.getHeight()
        );
        background.setId("canvasBackground");
        background.setFill(Color.WHITE);
        background.setStroke(Color.LIGHTGRAY);
        background.setStrokeWidth(1);
        background.setMouseTransparent(true);
        schemePane.getChildren().addFirst(background);

        // Рисуем сетку если нужно
        if (canvasState.isShowGrid()) {
            drawGrid();
        }
    }

    /**
     * Нарисовать сетку на канвасе
     */
    private void drawGrid() {
        double width = canvasState.getWidth();
        double height = canvasState.getHeight();
        double gridSize = canvasState.getGridSize();

        // Вертикальные линии
        for (double x = 0; x <= width; x += gridSize) {
            Line gridLine = new Line(x, 0, x, height);
            gridLine.setId("gridLine");
            gridLine.setStroke(Color.LIGHTGRAY);
            gridLine.setStrokeWidth(0.5);
            gridLine.setMouseTransparent(true);
            schemePane.getChildren().add(1, gridLine);
        }

        // Горизонтальные линии
        for (double y = 0; y <= height; y += gridSize) {
            Line gridLine = new Line(0, y, width, y);
            gridLine.setId("gridLine");
            gridLine.setStroke(Color.LIGHTGRAY);
            gridLine.setStrokeWidth(0.5);
            gridLine.setMouseTransparent(true);
            schemePane.getChildren().add(1, gridLine);
        }
    }

    private void setupMiddleMousePan() {
        schemeScrollPane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
            if (event.isMiddleButtonDown()) {
                lastMiddleMousePos = new Point2D(event.getSceneX(), event.getSceneY());
                event.consume();
            }
        });

        schemeScrollPane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, event -> {
            if (event.isMiddleButtonDown() && lastMiddleMousePos != null) {
                double dx = event.getSceneX() - lastMiddleMousePos.getX();
                double dy = event.getSceneY() - lastMiddleMousePos.getY();
                canvasState.setOffsetX(canvasState.getOffsetX() + dx);
                canvasState.setOffsetY(canvasState.getOffsetY() + dy);
                clampPanOffset();
                lastMiddleMousePos = new Point2D(event.getSceneX(), event.getSceneY());
                event.consume();
            }
        });

        schemeScrollPane.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.MIDDLE) {
                lastMiddleMousePos = null;
                event.consume();
            }
        });
    }

    /**
     * Сброс вида (scale=1, offset=0)
     */
    @FXML
    private void resetView() {
        canvasState.setScale(1.0);
        canvasState.setOffset(Point2D.ZERO);
        clampPanOffset();
        updateZoomLabel();
        statusLabel.setText("Вид сброшен");
    }

    private void updateZoomLabel() {
        if (zoomLabel != null) {
            zoomLabel.setText(String.format("Масштаб: %d%%  |  Ctrl+колесо мыши",
                    (int)(canvasState.getScale() * 100)));
        }
    }

    // ============================================================
    // SCHEME MANAGEMENT
    // ============================================================

    /**
     * Загрузка выбранной схемы
     */
    private void loadScheme(Scheme scheme) {
        // СОХРАНЯЕМ текущую схему перед загрузкой новой
        if (currentScheme != null && !currentScheme.equals(scheme)) {
            boolean hadChanges = schemeSaver.isDirty();
            if (!schemeSaver.saveBeforeSchemeChange(currentScheme)) {
                CustomAlertDialog.showError("Ошибка сохранения", "Не удалось сохранить текущую схему. Смена схемы отменена.");
                return;
            }
            if (hadChanges) {
                CustomAlertDialog.showAutoSaveNotification("Автосохранение", 1.3);
            }
        }

        try {
            currentScheme = scheme;
            deviceIconService.setCurrentScheme(scheme);

            // Загружаем настройки канваса из схемы
            loadCanvasStateFromScheme(scheme);

            // Очищаем и обновляем отображение канваса
            clearSchemePane();
            updateCanvasDisplay();

            // Загружаем фигуры и устройства
            loadSchemeContent(scheme);

            // Сбрасываем вид
            resetView();

            refreshAvailableDevices();
            schemeSaver.resetDirty(); // свежезагруженная схема — изменений нет
            statusLabel.setText("Загружена схема: " + scheme.getName() +
                    " (" + (int)canvasState.getWidth() + "x" + (int)canvasState.getHeight() + ")");

            updateSchemeTimestamp(currentScheme);
            updateDeleteButtonState();
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
     * Очистка схемы (полная: pane, undo, статус)
     */
    private void clearScheme() {
        boolean confirm = CustomAlertDialog.showConfirmation("Очистка схемы",
                "Это действие удалит ВСЕ фигуры и приборы с панели. Продолжить?");
        if (!confirm) return;

        try {
            if (currentScheme != null) {
                if (deviceLocationDAO != null) {
                    deviceLocationDAO.deleteAllLocationsForScheme(currentScheme.getId());
                }

                // Сохраняем пустую JSON структуру с настройками канваса
                SchemeData emptyData = new SchemeData();
                emptyData.setWidth((int) canvasState.getWidth());
                emptyData.setHeight((int) canvasState.getHeight());
                emptyData.setGridEnabled(canvasState.isShowGrid());
                emptyData.setGridSize(canvasState.getGridSize());

                currentScheme.setData(gson.toJson(emptyData));
                currentScheme.updateTimestamp();

                if (schemeDAO != null) {
                    schemeDAO.updateScheme(currentScheme);
                    updateSchemeTimestamp(currentScheme);
                }
            }

            clearSchemePane();
            updateCanvasDisplay(); // Восстанавливаем фон и сетку
            refreshAvailableDevices();
            statusLabel.setText("Схема полностью очищена");

        } catch (Exception e) {
            LOGGER.error("Ошибка при очистке схемы: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка", "Не удалось полностью очистить схему: " + e.getMessage());
        }
    }

    /**
     * ⭐⭐ Удаление схемы из БД ⭐⭐
     */
    private void deleteScheme() {
        if (currentScheme == null) {
            CustomAlertDialog.showWarning("Удаление схемы", "Не выбрана схема для удаления");
            return;
        }
        boolean hasDevices = schemeDAO.hasDevicesOnScheme(currentScheme.getId());
        if (hasDevices) {
            CustomAlertDialog.showWarning("Удаление схемы",
                    "Невозможно удалить схему \"" + currentScheme.getName() + "\".\n\nК схеме привязаны приборы (поле 'Местоположение').\nСначала измените местоположение всех приборов или удалите их.");
            return;
        }
        boolean confirm = CustomAlertDialog.showConfirmation("Удаление схемы",
                "Удалить схему \"" + currentScheme.getName() + "\"?\n\nСхема будет полностью удалена из базы данных.\nЭто действие необратимо!");
        if (!confirm) return;
        try {
            int schemeId = currentScheme.getId();
            String schemeName = currentScheme.getName();
            if (deviceLocationDAO != null) {
                deviceLocationDAO.deleteAllLocationsForScheme(schemeId);
            }
            boolean deleted = schemeDAO.deleteScheme(schemeId);
            if (deleted) {
                LOGGER.info("✅ Схема удалена: {} (ID={})", schemeName, schemeId);
                clearSchemePane();
                currentScheme = null;
                loadSchemes();
                if (!schemeComboBox.getItems().isEmpty()) {
                    schemeComboBox.getSelectionModel().selectFirst();
                }
                CustomAlertDialog.showSuccess("Удаление схемы", "Схема \"" + schemeName + "\" успешно удалена");
            } else {
                CustomAlertDialog.showError("Ошибка", "Не удалось удалить схему");
            }
        } catch (Exception e) {
            LOGGER.error("❌ Ошибка при удалении схемы: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка", "Произошла ошибка при удалении схемы");
        }
    }

    private void updateDeleteButtonState() {
        if (deleteSchemeBtn == null || currentScheme == null) {
            if (deleteSchemeBtn != null) deleteSchemeBtn.setDisable(true);
            return;
        }
        boolean hasDevices = schemeDAO.hasDevicesOnScheme(currentScheme.getId());
        deleteSchemeBtn.setDisable(hasDevices);
        if (hasDevices) {
            deleteSchemeBtn.setTooltip(new javafx.scene.control.Tooltip("Удаление заблокировано: к схеме привязаны приборы"));
        } else {
            deleteSchemeBtn.setTooltip(new javafx.scene.control.Tooltip("Удалить схему"));
        }
    }

    /**
     * Загрузка фигур из схемы (JSON формат)
     */
    private void loadShapesFromScheme(Scheme scheme) {
        statusLabel.setText("Очистка старых фигур...");

        // Очищаем существующие фигуры
        if (shapeService != null) {
            try {
                shapeService.clearAllShapes();
            } catch (Exception e) {
                LOGGER.warn("clearAllShapes failed: {} — fallback to manual", e.getMessage(), e);
                if (schemePane != null) schemePane.getChildren().clear();
                try {
                    shapeService.removeAllShapes();
                } catch (Exception ex) {
                    LOGGER.warn("removeAllShapes also failed");
                }
            }
        }

        String schemeData = scheme.getData();
        if (schemeData == null || schemeData.trim().isEmpty() || schemeData.equals("{}")) {
            LOGGER.info("Схема пуста");
            return;
        }

        // Загружаем через новый JSON метод
        shapeService.deserializeAndAddAll(schemeData);
        shapeService.applyCanvasBoundsToAll(canvasState.getWidth(), canvasState.getHeight()); // <- добавить

        LOGGER.info("Загружено фигур: {}", shapeService.getShapeCount());
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
                deviceNode.setRotate(location.getRotation());
                schemePane.getChildren().add(deviceNode);
            }
        }

        LOGGER.info("Загружено {} устройств на схему", locations.size());
    }

    /**
     * Обработка ошибки загрузки схемы
     */
    private void handleSchemeLoadError(Scheme scheme, Exception e) {
        LOGGER.error("Ошибка при загрузке схемы '{}': {}", scheme.getName(), e.getMessage());
        CustomAlertDialog.showError("Ошибка загрузки", "Не удалось загрузить схему: " + e.getMessage());
        statusLabel.setText("Ошибка загрузки схемы");
    }

    private void updateSchemeTimestamp(Scheme scheme) {
        if (schemeUpdatedLabel == null || scheme == null) return;
        if (scheme.getUpdatedAt() == 0) {
            schemeUpdatedLabel.setText("ещё не сохранялась");
            return;
        }
        String formatted = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
                .format(new java.util.Date(scheme.getUpdatedAt()));
        schemeUpdatedLabel.setText("сохранено: " + formatted);
    }

    // ============================================================
    // DEVICE MANAGEMENT
    // ============================================================

    /**
     * Обновление списка доступных устройств для текущей схемы
     */
    private void refreshAvailableDevices() {
        if (deviceList == null || deviceList.isEmpty()) {
            statusLabel.setText("Нет доступных приборов");
            return;
        }

        if (currentScheme == null) {
            statusLabel.setText("Схема не выбрана");
            return;
        }
        // Просто логируем информацию о доступных приборах
        String selectedSchemeName = currentScheme.getName();
        List<Integer> usedDeviceIds = getUsedDeviceIds();
        List<Integer> currentSchemeDeviceIds = getCurrentSchemeDeviceIds();
        deviceList.stream()
                .filter(device -> selectedSchemeName.equals(device.getLocation()))
                .filter(device -> !usedDeviceIds.contains(device.getId()))
                .filter(device -> !currentSchemeDeviceIds.contains(device.getId()))
                .count();
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
     * Получение имени инструмента для статуса
     */
    private String getToolName(ShapeManager.Tool tool) {
        if (tool == null) return "нет инструмента";
        return switch (tool) {
            case LINE -> "Линия";
            case RECTANGLE -> "Прямоугольник";
            case ELLIPSE -> "Эллипс";
            case RHOMBUS -> "Ромб";
            case TEXT -> "Текст";
        };
    }

    // ============================================================
    // MOUSE EVENT HANDLERS
    // ============================================================

    @FXML
    private void onPaneMousePressed(MouseEvent event) {
        if (schemePane == null || shapeManager == null) return;

        if (event.isSecondaryButtonDown()) return;
        if (event.getClickCount() == 2) return;

        // event.getX/Y() уже в мировых координатах благодаря трансформациям
        Point2D worldPoint = new Point2D(event.getX(), event.getY());

        Node clickedNode = findNodeAtWorldPosition(worldPoint.getX(), worldPoint.getY());
        boolean clickedOnShape = clickedNode != null && isShapeOrDevice(clickedNode);

        if (shapeManager.getSelectedShape() != null && !clickedOnShape) {
            shapeManager.deselectShape();
            event.consume();
            return;
        }

        if (clickedOnShape && clickedNode instanceof LineShape lineShape) {
            shapeManager.selectShape(lineShape);
            lineShape.makeResizeHandlesVisible();
            return;
        }

        if (clickedOnShape) return;

        if (currentTool != null) {
            shapeManager.resetWasResized();
            shapeManager.onMousePressedForTool(currentTool, worldPoint.getX(), worldPoint.getY());
        }
    }

    @FXML
    private void onPaneMouseDragged(MouseEvent event) {
        if (event.isSecondaryButtonDown()) return;

        // event.getX/Y() уже в мировых координатах
        shapeManager.onMouseDraggedForTool(currentTool, event.getX(), event.getY());
    }

    @FXML
    private void onPaneMouseReleased(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            handleLeftMouseRelease(event.getX(), event.getY());
        } else if (event.getButton() == MouseButton.SECONDARY) {
            handleRightMouseClick(event.getSceneX(), event.getSceneY());
        }
        updateStatusAfterMouseRelease();
    }

    /**
     * Обработка отпускания ПРАВОЙ кнопки мыши (контекстное меню)
     */
    private void handleRightMouseClick(double sceneX, double sceneY) {
        // Преобразуем координаты сцены в координаты панели
        Point2D paneCoords = schemePane.sceneToLocal(sceneX, sceneY);
        double paneX = paneCoords.getX();
        double paneY = paneCoords.getY();

        // Находим элемент под курсором в координатах панели
        Node clickedNode = findNodeAtPosition(paneX, paneY);

        if (clickedNode != null) {
            // ЕСЛИ КЛИКНУЛИ ПО ФИГУРЕ - НЕ ПОКАЗЫВАЕМ ОБЩЕЕ МЕНЮ
            if (isShapeOrDevice(clickedNode)) {
                return;
            }
        }
        showGeneralContextMenu(paneX, paneY);
    }

    /**
     * Проверка, является ли узел фигурой или прибором
     */
    private boolean isShapeOrDevice(Node node) {
        // Фигуры наследуются от ShapeBase (который extends Group)
        if (node instanceof ShapeBase) {
            return true;
        }

        // Приборы имеют Device или DeviceWithRotation в userData
        if (node.getUserData() != null) {
            Object userData = node.getUserData();
            return (userData instanceof Device) ||
                    (userData instanceof DeviceIconService.DeviceWithRotation);
        }
        return false;
    }

    /**
     * Поиск узла (фигуры или прибора) в указанной позиции
     */
    private Node findNodeAtPosition(double paneX, double paneY) {
        // Ищем в обратном порядке (сверху вниз)
        for (int i = schemePane.getChildren().size() - 1; i >= 0; i--) {
            Node node = schemePane.getChildren().get(i);

            // Пропускаем неинтерактивные элементы
            if (node instanceof javafx.scene.shape.Rectangle && "visibleBorder".equals(node.getId())) {
                continue;
            }

            // Пропускаем resize handles и rotation handles (они в основном pane)
            if (node instanceof Circle) {
                continue;
            }

            if (node instanceof LineShape lineShape) {
                boolean contains = lineShape.containsPoint(paneX, paneY);
                if (contains) return node;
            }
            // Для остальных фигур используем стандартную проверку bounding box
            if (node.getBoundsInParent().contains(paneX, paneY)) {
                return node;
            }
        }
        return null;
    }

    /**
     * Сброс текущего инструмента
     */
    private void resetCurrentTool() {
        currentTool = null;
        // Обновляем статус
        if (statusLabel != null) {
            statusLabel.setText("Фигура выделена - используйте ручки для изменения");
        }
    }

    /**
     * Общее контекстное меню для пустой области
     */
    private void showGeneralContextMenu(double paneX, double paneY) {
        ContextMenu contextMenu = new ContextMenu();

        // Пункт "Вставить"
        MenuItem pasteItem = new MenuItem("Вставить");
        pasteItem.setOnAction(_ -> {
            pasteShapeAtPosition(paneX, paneY);
            contextMenu.hide();
        });

        // Пункт "Добавить прибор"
        MenuItem addDeviceItem = new MenuItem("Добавить прибор");
        addDeviceItem.setOnAction(_ -> {
            addDeviceAtPosition(paneX, paneY);
            contextMenu.hide();
        });

        // Делаем пункты активными/неактивными в зависимости от условий
        pasteItem.setDisable(!ClipboardManager.hasShapeData());

        // Проверяем есть ли доступные приборы для текущей схемы
        List<Device> availableDevices = getAvailableDevicesForCurrentScheme();
        addDeviceItem.setDisable(availableDevices.isEmpty());

        contextMenu.getItems().addAll(pasteItem, addDeviceItem);

        Point2D screenPoint = schemePane.localToScreen(paneX, paneY);
        contextMenu.show(schemePane.getScene().getWindow(), screenPoint.getX(), screenPoint.getY());
    }

    /**
     * Вставка фигуры из буфера обмена в указанную позицию с проверкой границ
     */
    private void pasteShapeAtPosition(double x, double y) {
        if (!ClipboardManager.hasShapeData()) {
            return;
        }
        try {
            // Проверяем, чтобы координаты были в пределах панели
            double safeX = Math.max(0, Math.min(x, schemePane.getWidth() - 50));
            double safeY = Math.max(0, Math.min(y, schemePane.getHeight() - 50));

            String shapeData = ClipboardManager.getCopiedShapeData();
            ShapeBase pastedShape = ShapeBase.deserialize(shapeData, schemePane,
                    statusLabel::setText, shapeManager::selectShape, shapeManager);

            if (pastedShape != null) {
                // Устанавливаем позицию в месте клика курсора
                pastedShape.setPosition(safeX, safeY);
                pastedShape.addToPane();
                shapeManager.addShape(pastedShape);
                statusLabel.setText("Фигура вставлена в позицию курсора");
                // Автоматически выделяем вставленную фигуру
                shapeManager.selectShape(pastedShape);
            } else {
                statusLabel.setText("Ошибка вставки фигуры");
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка при вставке фигуры: {}", e.getMessage(), e);
            statusLabel.setText("Ошибка вставки фигуры");
            System.err.println("ERROR in pasteShapeAtPosition: " + e.getMessage());
        }
    }

    /**
     * Обработка отпускания ЛЕВОЙ кнопки мыши
     */
    private void handleLeftMouseRelease(double x, double y) {
        // ЕСЛИ ИНСТРУМЕНТ НЕ ВЫБРАН - ничего не делаем
        if (currentTool == null) {
            return;
        }
        if (currentTool == ShapeManager.Tool.TEXT) {
            // Текст создается по клику (отдельный диалог)
            Optional<String> result = CustomAlertDialog.showTextInputDialog("Добавление текста", "Введите текст для добавления на схему:", "Новый текст");
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
                        }
                    } catch (Exception e) {
                        LOGGER.error("ERROR adding TEXT: {}", e.getMessage(), e);
                        CustomAlertDialog.showError("Ошибка добавления текста", "Не удалось добавить текст: " + e.getMessage());
                    }
                } else {
                    statusLabel.setText("Текст не добавлен (пустой ввод)");
                }
            }
        } else {
            shapeManager.onMouseReleasedForTool(currentTool, x, y);
        }
    }

    /**
     * Обновление статуса после отпускания мыши
     */
    private void updateStatusAfterMouseRelease() {
        if (shapeManager.wasDraggedInSelect()) {
            statusLabel.setText(isShapeSelected() ? "Фигура перемещена" : "");
        } else if (shapeManager.wasResized()) {
            statusLabel.setText("Фигура изменена");
        }
    }

    // ============================================================
    // DEVICE INTERACTION
    // ============================================================

    /**
     * Добавление устройства на схему в указанную позицию через контекстное меню
     */
    private void addDeviceAtPosition(double x, double y) {
        // Проверяем, есть ли доступные приборы
        if (deviceList == null || deviceList.isEmpty()) {
            CustomAlertDialog.showWarning("Добавление прибора", "Нет доступных приборов для добавления!");
            return;
        }

        // Показываем красивый диалог выбора прибора
        Device selectedDevice = showDeviceSelectionDialog();
        if (selectedDevice == null) {
            return; // Пользователь отменил выбор
        }

        try {
            if (deviceIconService != null) {
                // Проверяем, что точка внутри канваса
                if (x < 0 || x > canvasState.getWidth() ||
                        y < 0 || y > canvasState.getHeight()) {
                    CustomAlertDialog.showWarning("Добавление прибора",
                            "Нельзя добавить прибор за пределами схемы!");
                    return;
                }

                Node deviceNode = deviceIconService.createDeviceIcon(x, y, selectedDevice, currentScheme);
                if (schemePane != null) schemePane.getChildren().add(deviceNode);

                if (currentScheme != null && deviceLocationDAO != null) {
                    DeviceLocation location = new DeviceLocation(
                            selectedDevice.getId(), currentScheme.getId(), x, y
                    );
                    boolean added = deviceLocationDAO.addDeviceLocation(location);
                    if (added) {
                        selectedDevice.updateTimestamp();
                        deviceDAO.updateDevice(selectedDevice);
                    }
                    if (!added) {
                        CustomAlertDialog.showError("Ошибка", "Не удалось сохранить прибор в базу данных");
                    }
                }

                refreshAvailableDevices();
                statusLabel.setText("Прибор добавлен: " + selectedDevice.getName());

                CustomAlertDialog.showSuccess("Добавление прибора",
                        "Прибор '" + selectedDevice.getName() + "' успешно добавлен на схему");
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка добавления устройства: {}", e.getMessage(), e);
            CustomAlertDialog.showError("Ошибка", "Не удалось добавить прибор: " + e.getMessage());
        }
    }

    /**
     * Кастомный диалог выбора прибора с красивым отображением
     */
    private Device showDeviceSelectionDialog() {
        // Получаем доступные приборы для текущей схемы
        List<Device> availableDevices = getAvailableDevicesForCurrentScheme();

        if (availableDevices.isEmpty()) {
            CustomAlertDialog.showWarning("Выбор прибора", "Нет доступных приборов для текущей схемы!");
            return null;
        }

        // Создаем диалог с помощью StyleUtils
        Dialog<Device> dialog = StyleUtils.createDeviceSelectionDialog(
                "Выбор прибора",
                "Выберите прибор для добавления на схему"
        );

        // Создаем TableView для отображения приборов
        TableView<Device> tableView = StyleUtils.createStyledTableView();

        // Создаем колонки
        TableColumn<Device, String> modelColumn = StyleUtils.createStyledColumn("Модель", "name", 250);
        TableColumn<Device, String> inventoryColumn = StyleUtils.createStyledColumn("Инвентарный номер", "inventoryNumber", 200);
        TableColumn<Device, String> valveColumn = StyleUtils.createStyledColumn("Кран", "valveNumber", 132);
        TableColumn<Device, String> locationColumn = StyleUtils.createStyledColumn("Местоположение", "location", 210);

        tableView.getColumns().addAll(modelColumn, inventoryColumn, valveColumn, locationColumn);

        // Загружаем данные
        ObservableList<Device> availableDevicesList = FXCollections.observableArrayList(availableDevices);
        tableView.setItems(availableDevicesList);

        // Настраиваем поведение - ВАЖНО: двойной клик и активация кнопки
        StyleUtils.setupTableViewBehavior(tableView, dialog);

        // Создаем контент
        VBox content = StyleUtils.createDialogContent(
                "Доступные приборы:",
                "Доступно приборов: " + availableDevices.size(),
                tableView
        );

        dialog.getDialogPane().setContent(content);

        // Преобразуем результат - ВАЖНО: проверяем что прибор выбран
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                Device selectedDevice = tableView.getSelectionModel().getSelectedItem();
                if (selectedDevice != null) {
                    return selectedDevice;
                } else {
                    CustomAlertDialog.showWarning("Выбор прибора", "Пожалуйста, выберите прибор из таблицы!");
                }
            }
            return null;
        });

        // Делаем кнопку "Выбрать" активной только при выборе элемента - ВАЖНО
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        if (okButton != null) {
            okButton.setDisable(true);
            tableView.getSelectionModel().selectedItemProperty().addListener((_, _, newSelection) -> okButton.setDisable(newSelection == null));
        }
        // Фокус на первую строку
        if (!availableDevicesList.isEmpty()) {
            tableView.getSelectionModel().selectFirst();
        }
        // Показываем диалог и возвращаем результат
        Optional<Device> result = dialog.showAndWait();
        return result.orElse(null);
    }

    /**
     * Получение доступных приборов для текущей схемы
     */
    private List<Device> getAvailableDevicesForCurrentScheme() {
        if (deviceList == null || deviceList.isEmpty() || currentScheme == null) {
            return new ArrayList<>();
        }

        String selectedSchemeName = currentScheme.getName();
        List<Integer> usedDeviceIds = getUsedDeviceIds();
        List<Integer> currentSchemeDeviceIds = getCurrentSchemeDeviceIds();

        return deviceList.stream()
                .filter(device -> selectedSchemeName.equals(device.getLocation()))
                .filter(device -> !usedDeviceIds.contains(device.getId()))
                .filter(device -> !currentSchemeDeviceIds.contains(device.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Сохранение позиции устройства
     */
    private void saveDeviceLocation(Node node, Device device) {
        schemeSaver.saveDeviceLocation(node, device, currentScheme);
        schemeSaver.markDirty(); // прибор перемещён мышью
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

    // ============================================================
    // THEME SUPPORT
    // ============================================================

    /**
     * Обновление иконок кнопок в зависимости от темы
     */
    private void updateButtonIcons() {
        if (schemePane.getScene() == null) return;

        boolean isDarkTheme = schemePane.getScene().getStylesheets().stream()
                .anyMatch(s -> s.contains("dark-theme.css"));

        String suffix = isDarkTheme ? "-dark.png" : "-white.png";

        setButtonIcon(undoBtn, "/images/scheme-editor/undo" + suffix);
        setButtonIcon(redoBtn, "/images/scheme-editor/redo" + suffix);
        setButtonIcon(resetViewBtn, "/images/scheme-editor/reset" + suffix);
        setButtonIcon(lineToolBtn, "/images/scheme-editor/line" + suffix);
        setButtonIcon(rectToolBtn, "/images/scheme-editor/rectangle" + suffix);
        setButtonIcon(ellipseToolBtn, "/images/scheme-editor/circle" + suffix);
        setButtonIcon(rhombusToolBtn, "/images/scheme-editor/valve" + suffix);
        setButtonIcon(textToolBtn, "/images/scheme-editor/text" + suffix);
        setButtonIcon(saveSchemeBtn, "/images/scheme-editor/save" + suffix);
        setButtonIcon(clearSchemeBtn, "/images/scheme-editor/clear" + suffix);
    }

    /**
     * Установка иконки для кнопки
     */
    private void setButtonIcon(Button button, String iconPath) {
        if (button != null) {
            try {
                javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(
                        new javafx.scene.image.Image(Objects.requireNonNull(getClass().getResourceAsStream(iconPath)))
                );
                imageView.setFitWidth(24);
                imageView.setFitHeight(24);
                imageView.setPreserveRatio(true);
                button.setGraphic(imageView);
            } catch (Exception e) {
                LOGGER.warn("Не удалось загрузить иконку: {}", iconPath);
            }
        }
    }
}