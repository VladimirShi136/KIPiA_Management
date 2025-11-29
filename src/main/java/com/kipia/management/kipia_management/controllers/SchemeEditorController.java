package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.managers.ClipboardManager;
import com.kipia.management.kipia_management.managers.ShapeManager;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.models.DeviceLocation;
import com.kipia.management.kipia_management.services.*;
import com.kipia.management.kipia_management.shapes.*;
import com.kipia.management.kipia_management.utils.CustomAlert;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
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
public class SchemeEditorController {

    // ============================================================
    // FXML COMPONENTS
    // ============================================================

    @FXML
    private ComboBox<Scheme> schemeComboBox;
    @FXML
    private Button saveSchemeBtn, lineToolBtn, rectToolBtn, textToolBtn, rhombusToolBtn;
    @FXML
    private Button undoBtn, redoBtn, ellipseToolBtn, clearSchemeBtn;
    @FXML
    private AnchorPane schemePane;
    @FXML
    private ScrollPane schemeScrollPane;
    @FXML
    private Label statusLabel;

    // ============================================================
    // DEPENDENCIES & SERVICES
    // ============================================================

    private static final Logger LOGGER = LogManager.getLogger(SchemeEditorController.class);
    private DeviceDAO deviceDAO;
    private SchemeDAO schemeDAO;
    private DeviceLocationDAO deviceLocationDAO;
    private ShapeManager shapeManager;
    private ShapeService shapeService;
    private DeviceIconService deviceIconService;
    private SchemeSaver schemeSaver;

    // ============================================================
    // DATA MODELS
    // ============================================================

    private Scheme currentScheme;
    private ObservableList<Device> deviceList;
    private ShapeManager.Tool currentTool = null;

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
        } catch (Exception e) {
            LOGGER.error("Ошибка в initialize(): {}", e.getMessage(), e);
            e.printStackTrace();
        }
    }

    /**
     * Основная инициализация после внедрения зависимостей
     */
    public void init() {
        try {
            validateDAODependencies();
            initializeServices();
            loadInitialData();
            setupInitialScheme();
            // Обновляем статус
            statusLabel.setText("Готов - выберите инструмент или работайте с фигурами");
            // Обновляем информацию о доступных приборах
            refreshAvailableDevices();
        } catch (Exception e) {
            LOGGER.error("Ошибка в init(): {}", e.getMessage(), e);
            e.printStackTrace();
            if (statusLabel != null) statusLabel.setText("Ошибка запуска: " + e.getMessage());
        }
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
        // Создаём фабрику фигур
        ShapeFactory shapeFactory = new ShapeFactory(
                schemePane,
                statusLabel::setText,
                shapeManager::selectShape,
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
                this::refreshAvailableDevices,
                currentScheme  // Передаем текущую схему
        );
        shapeManager.setOnShapeSelected(this::handleShapeSelection);
        shapeManager.setOnShapeDeselected(this::handleShapeDeselection);
        this.schemeSaver = new SchemeSaver(schemeDAO, deviceLocationDAO, shapeService, schemePane);
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
        saveSchemeBtn.setOnAction(_ -> schemeSaver.selectButtonSaveScheme(currentScheme));
        clearSchemeBtn.setOnAction(_ -> clearScheme());
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
        StyleUtils.applyHoverAndAnimation(undoBtn, "tool-button", "tool-button-hover");
        StyleUtils.applyHoverAndAnimation(redoBtn, "tool-button", "tool-button-hover");
        StyleUtils.applyHoverAndAnimation(saveSchemeBtn, "tool-button", "tool-button-hover");
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
        // СОХРАНЯЕМ текущую схему перед загрузкой новой
        if (currentScheme != null && !currentScheme.equals(scheme)) {
            if (!schemeSaver.saveBeforeSchemeChange(currentScheme)) {
                // Можно показать диалог и отменить смену схемы
                CustomAlert.showError("Ошибка сохранения", "Не удалось сохранить текущую схему. Смена схемы отменена.");
                return;
            }
            CustomAlert.showAutoSaveNotification("Автосохранение", 1.3);
        }
        try {
            currentScheme = scheme;
            deviceIconService.setCurrentScheme(scheme);
            clearSchemePane();
            loadSchemeContent(scheme);
            refreshAvailableDevices();
            statusLabel.setText("Загружена схема: " + scheme.getName());
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
        boolean confirm = CustomAlert.showConfirmation("Очистка схемы",
                "Это действие удалит ВСЕ фигуры и приборы с панели. Продолжить?");
        if (!confirm) return;
        try {
            if (currentScheme != null) {
                if (deviceLocationDAO != null) {
                    deviceLocationDAO.deleteAllLocationsForScheme(currentScheme.getId());
                }
                currentScheme.setData("{}");
                if (schemeDAO != null) {
                    schemeDAO.updateScheme(currentScheme);
                }
            }
            clearSchemePane();
            refreshAvailableDevices();
            statusLabel.setText("Схема полностью очищена");
        } catch (Exception e) {
            LOGGER.error("Ошибка при очистке схемы: {}", e.getMessage(), e);
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
        if (schemeData != null && !schemeData.isEmpty()) {
            LOGGER.info("Содержимое scheme.data: {}", safeSubstring(schemeData, 100));
        }
        if (isValidSchemeData(schemeData) && shapeService != null) {
            List<String> shapeData = parseShapeData(schemeData);
            shapeService.getShapeCount();
            shapeService.deserializeAndAddAll(shapeData);
            shapeService.getShapeCount();
        } else {
            safeSubstring(schemeData, 50);
        }
    }

    /**
     * Проверка валидности данных схемы
     */
    private boolean isValidSchemeData(String schemeData) {
        return schemeData != null && !schemeData.trim().isEmpty();
    }

    /**
     * Парсинг данных фигур из строки
     */
    private List<String> parseShapeData(String schemeData) {
        if (schemeData == null || schemeData.trim().isEmpty()) {
            return new ArrayList<>();
        }
        if (schemeData.equals("{}")) {
            return new ArrayList<>();
        }
        return Arrays.stream(schemeData.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> s.contains("|"))
                .filter(s -> s.split("\\|").length >= 5)
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

        // ЕСЛИ ПРАВАЯ КНОПКА - обрабатываем контекстное меню
        if (event.isSecondaryButtonDown()) {
            return; // Позволяем обработать контекстное меню
        }

        // ЕСЛИ ДВОЙНОЙ КЛИК - обрабатывается в onPaneMouseClicked
        if (event.getClickCount() == 2) {
            return;
        }

        Point2D panePoint = getAbsolutePaneCoordinates(event.getSceneX(), event.getSceneY());
        double x = panePoint.getX();
        double y = panePoint.getY();
        // ПРОВЕРЯЕМ, ЕСЛИ КЛИКНУЛИ ПО ФИГУРЕ
        Node clickedNode = findNodeAtPosition(x, y);
        boolean clickedOnShape = clickedNode != null && isShapeOrDevice(clickedNode);
        // ЕСЛИ ЕСТЬ ВЫДЕЛЕННАЯ ФИГУРА И КЛИКНУЛИ НА ПУСТУЮ ОБЛАСТЬ - снимаем выделение
        if (shapeManager.getSelectedShape() != null && !clickedOnShape) {
            shapeManager.deselectShape();
            event.consume();
            return;
        }

        // ЕСЛИ КЛИКНУЛИ ПО ЛИНИИ ОДИНОЧНЫМ КЛИКОМ - начинаем перетаскивание
        if (clickedOnShape && clickedNode instanceof LineShape lineShape) {
            shapeManager.selectShape(lineShape);
            // ДЛЯ ЛИНИИ: handles показываются сразу для перетаскивания
            lineShape.makeResizeHandlesVisible();
            return; // Позволяем LineShape обработать перетаскивание
        }
        if (clickedOnShape) {
            // НЕ выделяем фигуру здесь - пусть она сама обработает клик
            return;
        }

        // ЕСЛИ ИНСТРУМЕНТ ВЫБРАН И КЛИКНУЛИ НА ПУСТУЮ ОБЛАСТЬ - начинаем создание фигуры
        if (currentTool != null) {
            shapeManager.resetWasResized();
            // ВАЖНО: Вызываем метод ShapeManager для создания preview
            shapeManager.onMousePressedForTool(currentTool, x, y);

        }
    }

    @FXML
    private void onPaneMouseDragged(MouseEvent event) {
        // ЕСЛИ ПРАВАЯ КНОПКА - НЕ ОБРАБАТЫВАЕМ перетаскивание
        if (event.isSecondaryButtonDown()) {
            return;
        }

        Point2D panePoint = getAbsolutePaneCoordinates(event.getSceneX(), event.getSceneY());
        double x = panePoint.getX();
        double y = panePoint.getY();

        // ЕСЛИ ИНСТРУМЕНТ ВЫБРАН - обрабатываем перетаскивание для создания фигур
        if (currentTool != null) {
            shapeManager.onMouseDraggedForTool(currentTool, x, y);
        }
    }

    @FXML
    private void onPaneMouseReleased(MouseEvent event) {
        Point2D panePoint = getAbsolutePaneCoordinates(event.getSceneX(), event.getSceneY());
        double x = panePoint.getX();
        double y = panePoint.getY();

        if (event.getButton() == MouseButton.PRIMARY) { // Левая кнопка
            handleLeftMouseRelease(x, y);
        } else if (event.getButton() == MouseButton.SECONDARY) { // Правая кнопка
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
            boolean isDevice = (userData instanceof Device) ||
                    (userData instanceof DeviceIconService.DeviceWithRotation);
            return isDevice;
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
            e.printStackTrace();
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
                        }
                    } catch (Exception e) {
                        LOGGER.error("ERROR adding TEXT: {}", e.getMessage(), e);
                        CustomAlert.showError("Ошибка добавления текста", "Не удалось добавить текст: " + e.getMessage());
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
     * ПРАВИЛЬНОЕ преобразование координат
     */
    private Point2D getAbsolutePaneCoordinates(double sceneX, double sceneY) {
        if (schemeScrollPane == null || schemePane == null) {
            return new Point2D(sceneX, sceneY);
        }
        try {
            // ПРОСТОЕ преобразование сцены в локальные координаты панели
            Point2D local = schemePane.sceneToLocal(sceneX, sceneY);
            return new Point2D(local.getX(), local.getY());
        } catch (Exception e) {
            return new Point2D(sceneX, sceneY);
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
            CustomAlert.showWarning("Добавление прибора", "Нет доступных приборов для добавления!");
            return;
        }

        // Показываем красивый диалог выбора прибора
        Device selectedDevice = showDeviceSelectionDialog();
        if (selectedDevice == null) {
            return; // Пользователь отменил выбор
        }

        try {
            if (deviceIconService != null) {
                Node deviceNode = deviceIconService.createDeviceIcon(x, y, selectedDevice, currentScheme);
                if (schemePane != null) schemePane.getChildren().add(deviceNode);
                if (currentScheme != null && deviceLocationDAO != null) {
                    DeviceLocation location = new DeviceLocation(
                            selectedDevice.getId(), currentScheme.getId(), x, y
                    );
                    boolean added = deviceLocationDAO.addDeviceLocation(location);
                    if (!added) {
                        CustomAlert.showError("Ошибка", "Не удалось сохранить прибор в базу данных");
                    }
                }
                refreshAvailableDevices();
                statusLabel.setText("Прибор добавлен: " + selectedDevice.getName());

                // Показываем уведомление об успешном добавлении
                CustomAlert.showSuccess("Добавление прибора",
                        "Прибор '" + selectedDevice.getName() + "' успешно добавлен на схему");
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка добавления устройства: {}", e.getMessage(), e);
            CustomAlert.showError("Ошибка", "Не удалось добавить прибор: " + e.getMessage());
        }
    }

    /**
     * Кастомный диалог выбора прибора с красивым отображением
     */
    private Device showDeviceSelectionDialog() {
        // Получаем доступные приборы для текущей схемы
        List<Device> availableDevices = getAvailableDevicesForCurrentScheme();

        if (availableDevices.isEmpty()) {
            CustomAlert.showWarning("Выбор прибора", "Нет доступных приборов для текущей схемы!");
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
                    CustomAlert.showWarning("Выбор прибора", "Пожалуйста, выберите прибор из таблицы!");
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