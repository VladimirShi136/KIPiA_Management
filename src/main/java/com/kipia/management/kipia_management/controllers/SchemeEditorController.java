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

    // ============================================================
    // FXML COMPONENTS
    // ============================================================

    @FXML
    private ComboBox<Scheme> schemeComboBox;
    @FXML
    private ComboBox<Device> deviceComboBox;
    @FXML
    private Button saveSchemeBtn, lineToolBtn, rectToolBtn, textToolBtn, addDeviceToolBtn, rhombusToolBtn;
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

    private static final Logger LOGGER = Logger.getLogger(SchemeEditorController.class.getName());
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
    private ShapeManager.Tool previousTool = null;

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
            setupComboBoxes();
            setupPaneEventHandlers();
            setupShapeSystem();
            setupToolButtons();
            applyButtonStyles();
            setupWindowCloseHandler();
        } catch (Exception e) {
            LOGGER.severe("Ошибка в initialize(): " + e.getMessage());
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
            loadInitialData();  // DAO-dependent
            setupInitialScheme();  // DAO

            // Устанавливаем статус "без инструмента"
            statusLabel.setText("Готов - выберите инструмент или работайте с фигурами");
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
        System.out.println("DEBUG: Initializing services...");

        // Создаём менеджер фигур
        this.shapeManager = new ShapeManager(schemePane, null,
                canUndo -> undoBtn.setDisable(!canUndo),
                canRedo -> redoBtn.setDisable(!canRedo)
        );

        shapeManager.setStatusSetter(statusLabel::setText);
        System.out.println("DEBUG: Status setter configured");
        shapeManager.setOnSelectCallback(shapeHandler -> {
            System.out.println("DEBUG: Shape selected via callback - resetting tools");

            // Сбрасываем текущий инструмент при выделении фигуры
            resetCurrentTool();

            // Вызываем обработчик выделения
            handleShapeSelected();
        });

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
                this::refreshAvailableDevices,
                currentScheme  // Передаем текущую схему
        );
        System.out.println("DEBUG: DeviceIconService initialized with autoSave callback: " + true);

        // Настраиваем колбэки выделения/снятия выделения
        shapeManager.setOnShapeSelected(this::handleShapeSelection);
        shapeManager.setOnShapeDeselected(this::handleShapeDeselection);

        this.schemeSaver = new SchemeSaver(schemeDAO, deviceLocationDAO, shapeService, schemePane);

        System.out.println("DEBUG: Services initialization completed");
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
                return String.format("%s - %s - %s", device.getInventoryNumber(), device.getName(), device.getValveNumber());
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

        setupPaneKeyHandlers();
    }

    /**
     * Настройка обработчиков клавиш для панели
     */
    private void setupPaneKeyHandlers() {
        schemePane.setOnKeyPressed(event -> {
            // УБРАТЬ проверку на SELECT - горячие клавиши работают всегда при выделенной фигуре
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
        // Получаем сцену и добавляем обработчик закрытия
        schemePane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((obs, oldWindow, newWindow) -> {
                    if (newWindow != null) {
                        // Обработчик запроса на закрытие окна
                        newWindow.setOnCloseRequest(event -> {
                            System.out.println("DEBUG: Window close request detected");
                            schemeSaver.saveOnExit(currentScheme);
                        });
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
            System.out.println("DEBUG: No shape data in clipboard for paste");
            return;
        }

        try {
            String shapeData = ClipboardManager.getCopiedShapeData();
            ShapeBase pastedShape = ShapeBase.deserialize(shapeData, schemePane,
                    statusLabel::setText, shapeManager::selectShape, shapeManager);

            if (pastedShape != null) {
                // Для горячих клавиш используем старую логику смещения
                double[] lastPos = getPastePosition();
                pastedShape.setPosition(lastPos[0] + 20, lastPos[1] + 20);

                pastedShape.addToPane();
                shapeManager.addShape(pastedShape);

                statusLabel.setText("Фигура вставлена");
                System.out.println("DEBUG: Shape pasted via hotkey at (" + (lastPos[0] + 20) + ", " + (lastPos[1] + 20) + ")");
            }
        } catch (Exception e) {
            LOGGER.severe("Ошибка при вставке фигуры: " + e.getMessage());
            statusLabel.setText("Ошибка вставки фигуры");
        }
    }

    /**
     * Получает позицию для вставки (смещение от последней фигуры или центр)
     */
    private double[] getPastePosition() {
        // Если есть выделенная фигура - используем ее позицию
        ShapeHandler selected = shapeManager.getSelectedShape();
        if (selected != null) {
            double[] pos = selected.getPosition();
            System.out.println("DEBUG: Paste position from selected shape: (" + pos[0] + ", " + pos[1] + ")");
            return pos;
        }

        // Иначе возвращаем центр видимой области
        System.out.println("DEBUG: Paste position from center: (400, 300)");
        return new double[]{400, 300};
    }

    public SchemeSaver getSchemeSaver() {
        return schemeSaver;
    }

    public Scheme getCurrentScheme() {
        return currentScheme;
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
        // ПРОСТЫЕ обработчики без лишней логики
        lineToolBtn.setOnAction(e -> setCurrentTool(ShapeManager.Tool.LINE, "Инструмент: Линия - кликните и перетащите для рисования"));
        rectToolBtn.setOnAction(e -> setCurrentTool(ShapeManager.Tool.RECTANGLE, "Инструмент: Прямоугольник - кликните и перетащите для рисования"));
        ellipseToolBtn.setOnAction(e -> setCurrentTool(ShapeManager.Tool.ELLIPSE, "Инструмент: Эллипс - кликните и перетащите для рисования"));
        rhombusToolBtn.setOnAction(e -> setCurrentTool(ShapeManager.Tool.RHOMBUS, "Инструмент: Ромб - кликните и перетащите для рисования"));
        textToolBtn.setOnAction(e -> setCurrentTool(ShapeManager.Tool.TEXT, "Инструмент: Текст - кликните для добавления текста"));
        addDeviceToolBtn.setOnAction(e -> setCurrentTool(ShapeManager.Tool.ADD_DEVICE, "Инструмент: Добавить прибор - выберите прибор и кликните на схему"));
    }

    /**
     * Настройка кнопок действий
     */
    private void setupActionButtons() {
        undoBtn.setOnAction(e -> shapeManager.undo());
        redoBtn.setOnAction(e -> shapeManager.redo());
        saveSchemeBtn.setOnAction(e -> schemeSaver.selectButtonSaveScheme(currentScheme));
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
                lineToolBtn, rectToolBtn, ellipseToolBtn,
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
        System.out.println("DEBUG: Setting tool to: " + tool);
        currentTool = tool;

        // ОБНОВЛЯЕМ СТАТУС
        if (statusLabel != null) {
            statusLabel.setText(statusMessage);
        }

        // Снимаем выделение при смене инструмента
        if (shapeManager != null) {
            shapeManager.deselectShape();
        }

        // Обновляем стили кнопок
        updateToolButtonStyles();

        System.out.println("DEBUG: Tool successfully set to: " + tool);
    }

    /**
     * Обновление стилей кнопок инструментов
     */
    private void updateToolButtonStyles() {
        List<Button> toolButtons = Arrays.asList(
                lineToolBtn, rectToolBtn, ellipseToolBtn,
                addDeviceToolBtn, rhombusToolBtn, textToolBtn
        );

        // Сбрасываем все активные стили
        toolButtons.forEach(btn -> {
            btn.getStyleClass().remove("tool-button-active");
            // Убедимся, что есть базовый стиль
            if (!btn.getStyleClass().contains("tool-button")) {
                btn.getStyleClass().add("tool-button");
            }
        });

        // Применяем активный стиль к текущему инструменту
        if (currentTool != null) {
            Button activeButton = getButtonForTool(currentTool);
            if (activeButton != null) {
                activeButton.getStyleClass().remove("tool-button");
                activeButton.getStyleClass().add("tool-button-active");
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
            case ADD_DEVICE -> addDeviceToolBtn;
        };
    }

    /**
     * Обработка двойного клика - временно сбрасываем инструмент
     */
    private void handleShapeSelection() {
        System.out.println("DEBUG: Shape selected - no tool reset");

        // НЕ сбрасываем инструмент при выделении фигуры
        // Пользователь может продолжать работать с текущим инструментом
        statusLabel.setText("Фигура выделена - используйте ручки для изменения или продолжайте рисование");
    }

    /**
     * Обработка выделения фигуры - без параметров
     */
    private void handleShapeSelected() {
        System.out.println("DEBUG: Shape selected - resetting tool");

        // Уже сбросили инструмент в onSelectCallback, просто обновляем статус
        if (statusLabel != null) {
            statusLabel.setText("Фигура выделена - используйте ручки для изменения");
        }
    }

    /**
     * Обработка снятия выделения - восстанавливаем инструмент
     */
    private void handleShapeDeselection() {
        System.out.println("DEBUG: Shape deselected - tool remains: " + currentTool);

        // НЕ сбрасываем инструмент, только обновляем статус
        if (currentTool != null) {
            statusLabel.setText("Инструмент: " + getToolName(currentTool));
        } else {
            statusLabel.setText("Готов - выберите инструмент");
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
        // СОХРАНЯЕМ текущую схему перед загрузкой новой
        if (currentScheme != null && !currentScheme.equals(scheme)) {
            if (!schemeSaver.saveBeforeSchemeChange(currentScheme)) {
                // Можно показать диалог и отменить смену схемы
                CustomAlert.showError("Ошибка сохранения", "Не удалось сохранить текущую схему. Смена схемы отменена.");
                return;
            }
            // Показываем уведомление на n секунд
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
     * Очистка схемы (полная: pane, селекты, undo, статус)
     */
    private void clearScheme() {
        boolean confirm = CustomAlert.showConfirmation("Очистка схемы",
                "Это действие удалит ВСЕ фигуры и приборы с панели. Продолжить?");
        if (!confirm) return;

        try {
            if (currentScheme != null) {
                if (deviceLocationDAO != null) {
                    int deletedCount = deviceLocationDAO.deleteAllLocationsForScheme(currentScheme.getId());
                    System.out.println("Удалено приборов из БД: " + deletedCount);
                }

                currentScheme.setData("{}");
                if (schemeDAO != null) {
                    boolean saved = schemeDAO.updateScheme(currentScheme);
                    System.out.println("Схема сохранена в БД: " + saved);
                }
            }

            clearSchemePane();
            refreshAvailableDevices();

            statusLabel.setText("Схема полностью очищена");

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

        System.out.println("=== DEBUG: LOADING DEVICES FROM SCHEME ===");
        System.out.println("DEBUG: Scheme ID: " + scheme.getId() + ", Name: " + scheme.getName());
        System.out.println("DEBUG: Found " + locations.size() + " device locations in database");

        for (int i = 0; i < locations.size(); i++) {
            DeviceLocation location = locations.get(i);
            System.out.println("DEBUG: Location " + i + " - device_id: " + location.getDeviceId() +
                    ", x: " + location.getX() + ", y: " + location.getY() +
                    ", rotation: " + location.getRotation());

            Device device = deviceDAO.getDeviceById(location.getDeviceId());
            if (device != null) {
                System.out.println("DEBUG: Creating device - " + device.getName() +
                        " at (" + location.getX() + ", " + location.getY() +
                        ") with rotation " + location.getRotation() + "°");

                Node deviceNode = deviceIconService.createDeviceIcon(
                        location.getX(), location.getY(), device, currentScheme
                );

                // Применяем сохраненный угол поворота
                deviceNode.setRotate(location.getRotation());

                schemePane.getChildren().add(deviceNode);
            } else {
                System.out.println("DEBUG: ERROR - Device not found for ID: " + location.getDeviceId());
            }
        }
        System.out.println("=== END DEBUG: LOADING DEVICES ===");

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
            case ADD_DEVICE -> "Добавить прибор";
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
            System.out.println("DEBUG: Right button pressed - handling context menu");
            return; // Позволяем обработать контекстное меню
        }

        // ЕСЛИ ДВОЙНОЙ КЛИК - обрабатывается в onPaneMouseClicked
        if (event.getClickCount() == 2) {
            return;
        }

        Point2D panePoint = getAbsolutePaneCoordinates(event.getSceneX(), event.getSceneY());
        double x = panePoint.getX();
        double y = panePoint.getY();

        System.out.println("DEBUG: LEFT Mouse pressed at scene(" + event.getSceneX() + "," + event.getSceneY() +
                ") -> pane(" + x + "," + y + ")");
        System.out.println("DEBUG: Current tool: " + currentTool);

        // ПРОВЕРЯЕМ, ЕСЛИ КЛИКНУЛИ ПО ФИГУРЕ
        Node clickedNode = findNodeAtPosition(x, y);
        boolean clickedOnShape = clickedNode != null && isShapeOrDevice(clickedNode);

        // ЕСЛИ ЕСТЬ ВЫДЕЛЕННАЯ ФИГУРА И КЛИКНУЛИ НА ПУСТУЮ ОБЛАСТЬ - снимаем выделение
        if (shapeManager.getSelectedShape() != null && !clickedOnShape) {
            System.out.println("DEBUG: Click on empty area - deselecting shape");
            shapeManager.deselectShape();
            event.consume();
            return;
        }

        // ЕСЛИ КЛИКНУЛИ ПО ЛИНИИ ОДИНОЧНЫМ КЛИКОМ - начинаем перетаскивание
        if (clickedOnShape && clickedNode instanceof LineShape lineShape) {
            System.out.println("DEBUG: Single click on LineShape - preparing for drag");
            // Выделяем линию и показываем handles
            shapeManager.selectShape(lineShape);
            // ДЛЯ ЛИНИИ: handles показываются сразу для перетаскивания
            lineShape.makeResizeHandlesVisible();
            return; // Позволяем LineShape обработать перетаскивание
        }

        if (clickedOnShape) {
            System.out.println("DEBUG: Clicked on existing shape - allowing shape to handle events");
            // НЕ выделяем фигуру здесь - пусть она сама обработает клик
            return;
        }

        // ЕСЛИ ИНСТРУМЕНТ ВЫБРАН И КЛИКНУЛИ НА ПУСТУЮ ОБЛАСТЬ - начинаем создание фигуры
        if (currentTool != null) {
            System.out.println("DEBUG: Starting shape creation with tool: " + currentTool);
            shapeManager.resetWasResized();
            if (currentTool == ShapeManager.Tool.ADD_DEVICE) {
                addDeviceAt(x, y);
            } else {
                shapeManager.onMousePressedForTool(currentTool, x, y);
            }
        } else {
            System.out.println("DEBUG: No tool selected - allowing shape interaction");
        }
    }

    @FXML
    private void onPaneMouseDragged(MouseEvent event) {
        // ЕСЛИ ПРАВАЯ КНОПКА - НЕ ОБРАБАТЫВАЕМ перетаскивание
        if (event.isSecondaryButtonDown()) {
            return;
        }

        // ЕСЛИ ИНСТРУМЕНТ ВЫБРАН - обрабатываем перетаскивание для создания фигур
        if (currentTool != null && currentTool != ShapeManager.Tool.ADD_DEVICE) {
            Point2D panePoint = getAbsolutePaneCoordinates(event.getSceneX(), event.getSceneY());
            shapeManager.onMouseDraggedForTool(currentTool, panePoint.getX(), panePoint.getY());
        }
    }

    @FXML
    private void onPaneMouseReleased(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) { // Левая кнопка
            Point2D panePoint = getAbsolutePaneCoordinates(event.getSceneX(), event.getSceneY());
            double x = panePoint.getX();
            double y = panePoint.getY();

            System.out.println("DEBUG: Mouse released at (" + x + ", " + y + ") with tool: " + currentTool);

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
        System.out.println("DEBUG: Right click at scene coordinates: (" + sceneX + ", " + sceneY + ")");

        // Преобразуем координаты сцены в координаты панели
        Point2D paneCoords = schemePane.sceneToLocal(sceneX, sceneY);
        double paneX = paneCoords.getX();
        double paneY = paneCoords.getY();

        System.out.println("DEBUG: Converted to pane coordinates: (" + paneX + ", " + paneY + ")");

        // Находим элемент под курсором в координатах панели
        Node clickedNode = findNodeAtPosition(paneX, paneY);

        if (clickedNode != null) {
            System.out.println("DEBUG: Right click on node: " + clickedNode.getClass().getSimpleName());

            // ЕСЛИ КЛИКНУЛИ ПО ФИГУРЕ - НЕ ПОКАЗЫВАЕМ ОБЩЕЕ МЕНЮ
            if (isShapeOrDevice(clickedNode)) {
                System.out.println("DEBUG: Shape/device clicked - skipping general context menu");
                return;
            }
        }

        // СЮДА ДОЙДЕМ ТОЛЬКО ЕСЛИ кликнули по пустой области ИЛИ по не-фигуре
        System.out.println("DEBUG: Showing general context menu at pane coordinates (" + paneX + ", " + paneY + ")");
        System.out.println("DEBUG: Clipboard has data: " + ClipboardManager.hasShapeData());
        showGeneralContextMenu(paneX, paneY);
    }

    /**
     * Проверка, является ли узел фигурой или прибором
     */
    private boolean isShapeOrDevice(Node node) {
        System.out.println("DEBUG: Checking if node is shape/device: " + node.getClass().getSimpleName());

        // Фигуры наследуются от ShapeBase (который extends Group)
        if (node instanceof ShapeBase) {
            System.out.println("DEBUG: Node is ShapeBase - it's a shape");
            return true;
        }

        // Приборы имеют Device или DeviceWithRotation в userData
        if (node.getUserData() != null) {
            Object userData = node.getUserData();
            boolean isDevice = (userData instanceof Device) ||
                    (userData instanceof DeviceIconService.DeviceWithRotation);
            System.out.println("DEBUG: Node has userData - is device: " + isDevice);
            return isDevice;
        }

        System.out.println("DEBUG: Node is NOT shape or device");
        return false;
    }

    /**
     * Поиск узла (фигуры или прибора) в указанной позиции
     */
    private Node findNodeAtPosition(double paneX, double paneY) {
        System.out.println("DEBUG: Searching for node at PANE coordinates (" + paneX + ", " + paneY + ")");

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
                System.out.println("DEBUG: LineShape contains check: " + contains +
                        " at coords (" + paneX + ", " + paneY + ")");
                if (contains) return node;
            }
            // Для остальных фигур используем стандартную проверку bounding box
            if (node.getBoundsInParent().contains(paneX, paneY)) {
                System.out.println("DEBUG: Found node: " + node.getClass().getSimpleName());
                return node;
            }
        }

        System.out.println("DEBUG: No node found at pane position");
        return null;
    }

    /**
     * Сброс текущего инструмента
     */
    private void resetCurrentTool() {
        System.out.println("DEBUG: Resetting current tool");

        // Сохраняем предыдущий инструмент (если нужно)
        if (currentTool != null) {
            previousTool = currentTool;
        }

        currentTool = null;

        // Обновляем статус
        if (statusLabel != null) {
            statusLabel.setText("Фигура выделена - используйте ручки для изменения");
        }

        System.out.println("DEBUG: Tool reset - currentTool: null, previousTool: " +
                (previousTool != null ? previousTool.toString() : "null"));
    }

    /**
     * Общее контекстное меню для пустой области
     */
    private void showGeneralContextMenu(double paneX, double paneY) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem pasteItem = new MenuItem("Вставить");
        pasteItem.setOnAction(event -> {
            System.out.println("DEBUG: General context menu - Paste clicked at (" + paneX + ", " + paneY + ")");
            pasteShapeAtPosition(paneX, paneY); // Передаем координаты курсора
            contextMenu.hide();
        });

        // Делаем пункт активным только если есть данные в буфере
        pasteItem.setDisable(!ClipboardManager.hasShapeData());

        contextMenu.getItems().addAll(pasteItem);

        Point2D screenPoint = schemePane.localToScreen(paneX, paneY);
        contextMenu.show(schemePane.getScene().getWindow(), screenPoint.getX(), screenPoint.getY());
    }

    /**
     * Вставка фигуры из буфера обмена в указанную позицию с проверкой границ
     */
    private void pasteShapeAtPosition(double x, double y) {
        if (!ClipboardManager.hasShapeData()) {
            System.out.println("DEBUG: No shape data in clipboard for paste");
            return;
        }

        try {
            // Проверяем, чтобы координаты были в пределах панели
            double safeX = Math.max(0, Math.min(x, schemePane.getWidth() - 50));
            double safeY = Math.max(0, Math.min(y, schemePane.getHeight() - 50));

            System.out.println("DEBUG: pasteShapeAtPosition - at (" + safeX + ", " + safeY + ")");

            String shapeData = ClipboardManager.getCopiedShapeData();
            ShapeBase pastedShape = ShapeBase.deserialize(shapeData, schemePane,
                    statusLabel::setText, shapeManager::selectShape, shapeManager);

            if (pastedShape != null) {
                // Устанавливаем позицию в месте клика курсора
                pastedShape.setPosition(safeX, safeY);
                pastedShape.addToPane();
                shapeManager.addShape(pastedShape);

                statusLabel.setText("Фигура вставлена в позицию курсора");
                System.out.println("DEBUG: Shape pasted at cursor position (" + safeX + ", " + safeY + ")");

                // Автоматически выделяем вставленную фигуру
                shapeManager.selectShape(pastedShape);
            } else {
                System.out.println("DEBUG: Failed to deserialize shape in pasteShapeAtPosition");
                statusLabel.setText("Ошибка вставки фигуры");
            }
        } catch (Exception e) {
            LOGGER.severe("Ошибка при вставке фигуры: " + e.getMessage());
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
            System.out.println("DEBUG: No tool selected - ignoring mouse release");
            return;
        }

        if (currentTool == ShapeManager.Tool.TEXT) {
            System.out.println("DEBUG: Handling TEXT tool release");
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
                        LOGGER.severe("ERROR adding TEXT: " + e.getMessage());
                        CustomAlert.showError("Ошибка добавления текста", "Не удалось добавить текст: " + e.getMessage());
                    }
                } else {
                    statusLabel.setText("Текст не добавлен (пустой ввод)");
                }
            }
        } else {
            // Для остальных фигур - создаем только если был drag (перетаскивание)
            System.out.println("DEBUG: Handling shape tool release for: " + currentTool);
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
     * Добавление устройства на схему в заданную позицию
     */
    private void addDeviceAt(double x, double y) {
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
                    boolean added = deviceLocationDAO.addDeviceLocation(location);
                    System.out.println("DEBUG: Initial device location added to DB: " + added);

                    if (!added) {
                        System.out.println("ERROR: Failed to add device location to database!");
                        CustomAlert.showError("Ошибка", "Не удалось сохранить прибор в базу данных");
                    }
                }
                refreshAvailableDevices();
                statusLabel.setText("Прибор добавлен: " + selectedDevice.getName());
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