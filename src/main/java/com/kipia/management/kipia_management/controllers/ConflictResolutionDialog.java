package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.managers.SyncManager.ConflictInfo;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.models.DeviceLocation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.kipia.management.kipia_management.utils.StyleUtils;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

import static com.kipia.management.kipia_management.shapes.ShapeBase.LOGGER;

/**
 * Контроллер диалога разрешения конфликтов синхронизации.
 * Позволяет пользователю выбирать между LOCAL и REMOTE версиями для каждого конфликта.
 *
 * @author vladimir_shi
 * @since 28.04.2026
 */
public class ConflictResolutionDialog implements Initializable {

    @FXML
    private ListView<ConflictItem> conflictListView;
    
    @FXML
    private Button chooseAllLocalButton;

    @FXML
    private Button chooseAllRemoteButton;

    @FXML
    private StackPane helpIcon;
    
    @FXML
    private Button applyButton;
    
    @FXML
    private Button cancelButton;

    @FXML
    private Label titleLabel;
    
    @FXML
    private Label countLabel;

    private ObservableList<ConflictItem> conflictItems;
    private boolean applied = false;

    /**
     * Внутренний класс для отображения конфликта в ListView
     */
    public static class ConflictItem {
        private final ConflictInfo conflict;
        private ConflictResolution choice;

        public ConflictItem(ConflictInfo conflict) {
            this.conflict = conflict;
            // По умолчанию выбираем REMOTE ("В архиве"), так как импорт обычно делается для получения свежих данных
            this.choice = ConflictResolution.REMOTE;
        }

        public ConflictInfo getConflict() {
            return conflict;
        }

        public ConflictResolution getChoice() {
            return choice;
        }

        public void setChoice(ConflictResolution choice) {
            this.choice = choice;
        }

        @Override
        public String toString() {
            String typeStr = switch (conflict.type) {
                case "device" -> "Устройство";
                case "scheme" -> "Схема";
                case "device_location" -> "Локация";
                default -> conflict.type;
            };
            
            String choiceStr = switch (choice) {
                case LOCAL -> "✓ LOCAL";
                case REMOTE -> "✓ REMOTE";
                case SKIP -> "✗ Пропустить";
                case UNRESOLVED -> "? Не решено";
            };
            
            return String.format("[%s] %s - %s: %s", 
                    typeStr, conflict.key, choiceStr, getConflictDescription());
        }
        
        private String getConflictDescription() {
            if (conflict.type.equals("device")) {
                Device local = (Device) conflict.local;
                Device remote = (Device) conflict.remote;
                return String.format("LOCAL: '%s' | REMOTE: '%s'", 
                        local.getName(), remote.getName());
            } else if (conflict.type.equals("scheme")) {
                Scheme local = (Scheme) conflict.local;
                Scheme remote = (Scheme) conflict.remote;
                return String.format("LOCAL: '%s' | REMOTE: '%s'", 
                        local.getName(), remote.getName());
            } else {
                DeviceLocation local = (DeviceLocation) conflict.local;
                DeviceLocation remote = (DeviceLocation) conflict.remote;
                return String.format("LOCAL: (%.1f,%.1f) | REMOTE: (%.1f,%.1f)", 
                        local.getX(), local.getY(), remote.getX(), remote.getY());
            }
        }
    }

    /**
     * Перечисление вариантов разрешения конфликта
     */
    public enum ConflictResolution {
        LOCAL, REMOTE, SKIP, UNRESOLVED
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        conflictItems = FXCollections.observableArrayList();
        conflictListView.setItems(conflictItems);

        // Настраиваем отображение элементов
        conflictListView.setCellFactory(_ -> new ConflictListCell());

        // Полностью отключаем выделение - используем null модель для предотвращения IndexOutOfBoundsException
        conflictListView.setSelectionModel(null);
        conflictListView.setFocusTraversable(false);

        // Предотвращаем клики на пустой список (защита от IndexOutOfBoundsException)
        conflictListView.setOnMousePressed(mouseEvent -> {
            if (conflictItems.isEmpty()) {
                mouseEvent.consume();
            }
        });
        conflictListView.setOnMouseClicked(mouseEvent -> {
            if (conflictItems.isEmpty()) {
                mouseEvent.consume();
            }
        });

        // Обработчики кнопок
        chooseAllLocalButton.setOnAction(_ -> chooseAll(ConflictResolution.LOCAL));
        chooseAllRemoteButton.setOnAction(_ -> chooseAll(ConflictResolution.REMOTE));
        helpIcon.setOnMouseClicked(_ -> showHelpDialog());
        applyButton.setOnAction(_ -> applyResolution());
        cancelButton.setOnAction(_ -> cancelResolution());

        // Настраиваем стили кнопок как в CustomAlertDialog
        CustomAlertDialog.setupButtonStyles(applyButton, true);
        CustomAlertDialog.setupButtonStyles(cancelButton, false);

        // Добавляем перетаскивание за шапку
        setupDragHandling();
    }
    
    /**
     * Настройка перетаскивания диалога за шапку
     */
    private void setupDragHandling() {
        // Ждем пока сцена будет установлена для titleLabel
        titleLabel.sceneProperty().addListener((_, _, newScene) -> {
            if (newScene != null) {
                VBox root = (VBox) newScene.getRoot();
                LOGGER.info("setupDragHandling: scene set, root={}", root);
                
                // Ждем пока окно будет привязано к сцене
                newScene.windowProperty().addListener((_, _, newWindow) -> {
                    if (newWindow instanceof Stage stage) {
                        LOGGER.info("setupDragHandling: window set, stage={}", stage);
                        if (root != null) {
                            // Находим шапку (первый HBox в VBox)
                            if (!root.getChildren().isEmpty() && root.getChildren().getFirst() instanceof HBox header) {
                                LOGGER.info("setupDragHandling: found header={}, children count={}", header, header.getChildren().size());
                                makeDraggable(stage, header);
                            } else {
                                LOGGER.warn("setupDragHandling: first child is not HBox or no children");
                            }
                        }
                    }
                });
            }
        });
    }
    
    /**
     * Делаем окно перетаскиваемым за указанный узел
     */
    private void makeDraggable(Stage stage, HBox header) {
        final Delta dragDelta = new Delta();
        LOGGER.info("makeDraggable: setting up drag for header");
        
        // Добавляем обработчики на саму шапку и все её дочерние элементы
        addDragHandlers(stage, header, dragDelta);
        
        // Рекурсивно добавляем обработчики на все дочерние элементы
        int childCount = header.getChildren().size();
        LOGGER.info("makeDraggable: processing {} children", childCount);
        for (int i = 0; i < childCount; i++) {
            Node child = header.getChildren().get(i);
            LOGGER.info("makeDraggable: processing child {}: {}", i, child.getClass().getSimpleName());
            addDragHandlersRecursively(stage, child, dragDelta);
        }
    }
    
    private void addDragHandlersRecursively(Stage stage, Node node, Delta dragDelta) {
        addDragHandlers(stage, node, dragDelta);
        
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                addDragHandlersRecursively(stage, child, dragDelta);
            }
        }
    }
    
    private void addDragHandlers(Stage stage, Node node, Delta dragDelta) {
        node.setOnMousePressed(event -> {
            dragDelta.x = stage.getX() - event.getScreenX();
            dragDelta.y = stage.getY() - event.getScreenY();
            LOGGER.info("MousePressed on {}: stageX={}, stageY={}, screenX={}, screenY={}", 
                node.getClass().getSimpleName(), stage.getX(), stage.getY(), event.getScreenX(), event.getScreenY());
            event.consume();
        });
        
        node.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() + dragDelta.x);
            stage.setY(event.getScreenY() + dragDelta.y);
            LOGGER.info("MouseDragged on {}: newX={}, newY={}", 
                node.getClass().getSimpleName(), stage.getX(), stage.getY());
            event.consume();
        });
    }
    
    /**
     * Показать диалог справки о вариантах выбора
     */
    private void showHelpDialog() {
        // Получаем текущий stage диалога конфликтов
        Stage conflictStage = (Stage) conflictListView.getScene().getWindow();
        
        // Создаем диалог справки с модальностью WINDOW_MODAL
        Stage helpStage = new Stage();
        helpStage.initOwner(conflictStage);
        helpStage.initModality(Modality.WINDOW_MODAL);
        helpStage.initStyle(StageStyle.TRANSPARENT);
        helpStage.setResizable(false);
        CustomAlertDialog.setAppIcon(helpStage);
        
        // Создаем иконку информации
        SVGPath infoIcon = new SVGPath();
        infoIcon.setContent("M12,2 C6.48,2 2,6.48 2,12 s4.48,10 10,10 s10,-4.48 10,-10 S17.52,2 12,2 z M13,17 h-2 v-6 h2 v6 z M13,9 h-2 V7 h2 v2 z");
        infoIcon.setFill(StyleUtils.isDarkTheme() ? Color.web("#7090b0") : Color.WHITE);
        
        // Создаем шапку по паттерну CustomAlertDialog
        HBox topBox = CustomAlertDialog.createTopBox(infoIcon, "СПРАВКА", "Варианты разрешения конфликтов", null, helpStage);
        
        // Создаем контент с описанием вариантов
        VBox body = new VBox(12);
        body.setPadding(new Insets(10, 20, 8, 20));
        body.setStyle("-fx-background-color: transparent;");
        
        // LOCAL
        Label localLabel = new Label("LOCAL (БД ПК)");
        localLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #277AAF; -fx-font-size: 14px;");
        Label localDesc = new Label("Сохранить изменения с этого устройства (ваша текущая версия данных)");
        localDesc.setStyle("-fx-text-fill: -fx-text-secondary; -fx-font-size: 13px; -fx-wrap-text: true;");
        VBox localBox = new VBox(4, localLabel, localDesc);
        
        // REMOTE
        Label remoteLabel = new Label("REMOTE (БД АРХИВ)");
        remoteLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #249C57; -fx-font-size: 14px;");
        Label remoteDesc = new Label("Взять данные с другого устройства (версия с удаленного устройства)");
        remoteDesc.setStyle("-fx-text-fill: -fx-text-secondary; -fx-font-size: 13px; -fx-wrap-text: true;");
        VBox remoteBox = new VBox(4, remoteLabel, remoteDesc);
        
        // SKIP
        Label skipLabel = new Label("Пропустить");
        skipLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -fx-accent-warning; -fx-font-size: 14px;");
        Label skipDesc = new Label("Не синхронизировать этот прибор (оставить как есть без изменений)");
        skipDesc.setStyle("-fx-text-fill: -fx-text-secondary; -fx-font-size: 13px; -fx-wrap-text: true;");
        VBox skipBox = new VBox(4, skipLabel, skipDesc);
        
        body.getChildren().addAll(localBox, remoteBox, skipBox);
        
        // Кнопка закрытия
        Button closeButton = new Button("Понятно");
        closeButton.setStyle("-fx-background-color: #465261; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 5px; -fx-border-radius: 5px; -fx-cursor: hand; -fx-min-width: 80px; -fx-border-width: 0;");
        closeButton.setOnMouseEntered(_ -> closeButton.setStyle("-fx-background-color: #465261; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 5px; -fx-border-radius: 5px; -fx-cursor: hand; -fx-min-width: 80px; -fx-border-width: 0; -fx-opacity: 0.9;"));
        closeButton.setOnMouseExited(_ -> closeButton.setStyle("-fx-background-color: #465261; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 5px; -fx-border-radius: 5px; -fx-cursor: hand; -fx-min-width: 80px; -fx-border-width: 0;"));
        closeButton.setOnAction(_ -> helpStage.close());
        
        HBox btnBar = CustomAlertDialog.createButtonBar(closeButton);
        
        // Разделитель
        Region divider = CustomAlertDialog.createDivider();
        VBox.setMargin(divider, new Insets(12, 0, 0, 0));
        
        // Создаем корень по паттерну CustomAlertDialog
        VBox root = CustomAlertDialog.createRoot(topBox, body, divider, btnBar);
        root.setMinWidth(380);
        root.setMaxWidth(420);
        
        // Настраиваем перемещение
        CustomAlertDialog.setupDragToMove(root, helpStage);
        
        // Создаем сцену
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        
        // Применяем стили темы
        for (String stylesheet : StyleUtils.getBaseStylesheets()) {
            URL url = ConflictResolutionDialog.class.getResource(stylesheet);
            if (url != null) {
                scene.getStylesheets().add(url.toExternalForm());
            }
        }
        
        // Загружаем стили для alert-dialog
        scene.getStylesheets().add(
                Objects.requireNonNull(ConflictResolutionDialog.class.getResource("/styles/alert-dialog.css")).toExternalForm()
        );
        
        helpStage.setScene(scene);
        helpStage.showAndWait();
    }

    /**
     * Вспомогательный класс для хранения дельты перетаскивания
     */
    private static class Delta {
        double x, y;
    }

    /**
     * Установка списка конфликтов для разрешения
     */
    public void setConflicts(List<ConflictInfo> conflicts) {
        conflictItems.clear();

        for (ConflictInfo conflict : conflicts) {
            conflictItems.add(new ConflictItem(conflict));
        }

        titleLabel.setText("Разрешение конфликтов данных");
        countLabel.setText(String.format("Обнаружено конфликтов: %d", conflicts.size()));

        // Отключаем выделение если список пуст, чтобы избежать IndexOutOfBoundsException
        if (conflictItems.isEmpty()) {
            conflictListView.setMouseTransparent(true);
        } else {
            conflictListView.setMouseTransparent(false);
        }
    }

    /**
     * Выбрать вариант для всех конфликтов
     */
    private void chooseAll(ConflictResolution resolution) {
        for (ConflictItem item : conflictItems) {
            item.setChoice(resolution);
        }
        conflictListView.refresh();
    }

    /**
     * Применить выбранные решения
     */
    @FXML
    public void applyResolution() {
        // Проверяем, что все конфликты разрешены
        boolean allResolved = conflictItems.stream()
                .allMatch(item -> item.getChoice() != ConflictResolution.UNRESOLVED);
        
        if (!allResolved) {
            CustomAlertDialog.showWarning("Не все конфликты разрешены", "Пожалуйста, выберите вариант для всех конфликтов");
            return;
        }
        
        applied = true;
        closeDialog();
    }

    /**
     * Отменить разрешение конфликтов
     */
    public void cancelResolution() {
        applied = false;
        closeDialog();
    }

    /**
     * Закрыть диалог
     */
    private void closeDialog() {
        Stage stage = (Stage) conflictListView.getScene().getWindow();
        stage.close();
    }

    /**
     * Получить результаты разрешения конфликтов
     */
    public List<ConflictResolution> getResolutions() {
        List<ConflictResolution> resolutions = new ArrayList<>();
        for (ConflictItem item : conflictItems) {
            resolutions.add(item.getChoice());
        }
        return resolutions;
    }

    /**
     * Проверить, были ли применены изменения
     */
    public boolean isApplied() {
        return applied;
    }

    /**
     * Показать диалог разрешения конфликтов
     */
    public static boolean showConflictResolutionDialog(List<ConflictInfo> conflicts, 
                                                      List<ConflictResolution> resolutions) {
        try {
            // Загружаем FXML так же, как в других контроллерах проекта
            URL fxmlUrl = ConflictResolutionDialog.class.getResource("/views/conflict_resolution_dialog.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("FXML resource not found: /views/conflict_resolution_dialog.fxml");
            }
            
            LOGGER.info("Loading conflict dialog FXML from: {}", fxmlUrl);
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(fxmlUrl);
            VBox root = loader.load();
            LOGGER.info("FXML loaded successfully");
            
            // Создаем диалог с кастомным стилем
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initStyle(StageStyle.TRANSPARENT);
            CustomAlertDialog.setAppIcon(dialogStage);
            
            // Устанавливаем размер диалога
            root.setPrefWidth(900); // Увеличиваем ширину
            root.setMinWidth(850);
            root.setMaxWidth(1000);
            
            // Применяем скругление углов через clip
            Rectangle clip = new Rectangle();
            clip.setArcWidth(24);
            clip.setArcHeight(24);
            clip.widthProperty().bind(root.widthProperty());
            clip.heightProperty().bind(root.heightProperty());
            root.setClip(clip);
            
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            
            // Применяем текущую тему через новую систему стилей
            LOGGER.info("Current theme is dark: {}", StyleUtils.isDarkTheme());
            
            // Добавляем базовые стили с переменными темы
            for (String stylesheet : StyleUtils.getBaseStylesheets()) {
                URL url = ConflictResolutionDialog.class.getResource(stylesheet);
                if (url != null) {
                    scene.getStylesheets().add(url.toExternalForm());
                    LOGGER.info("Added stylesheet: {}", stylesheet);
                } else {
                    LOGGER.warn("Stylesheet not found: {}", stylesheet);
                }
            }
            
            // Добавляем специфичные стили для диалога конфликтов
            scene.getStylesheets().add(
                    Objects.requireNonNull(ConflictResolutionDialog.class.getResource("/styles/conflict-dialog.css")).toExternalForm()
            );
            
            dialogStage.setScene(scene);
            
            // Отладочная информация - выводим все загруженные стили
            LOGGER.info("Loaded stylesheets: {}", scene.getStylesheets());
            
            // Получаем контроллер и устанавливаем конфликты
            ConflictResolutionDialog controller = loader.getController();
            controller.setConflicts(conflicts);
            
            // Показываем диалог и ждем закрытия
            dialogStage.showAndWait();
            
            // Получаем результаты
            if (controller.isApplied()) {
                resolutions.addAll(controller.getResolutions());
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            // Если FXML не найден, создаем диалог программно
            LOGGER.error("FXML loading failed, using programmatic dialog");
            LOGGER.error("Exception type: {}", e.getClass().getName());
            LOGGER.error("Exception message: {}", e.getMessage(), e);
            return showProgrammaticDialog(conflicts, resolutions);
        }
    }

    /**
     * Программное создание диалога (если FXML не доступен)
     */
    private static boolean showProgrammaticDialog(List<ConflictInfo> conflicts,
                                                List<ConflictResolution> resolutions) {
        Stage dialogStage = new Stage();
        dialogStage.setTitle("Разрешение конфликтов данных");
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        CustomAlertDialog.setAppIcon(dialogStage);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("conflict-dialog");

        // Заголовок
        Label title = new Label(String.format("Разрешение конфликтов данных (%d)", conflicts.size()));
        title.getStyleClass().add("title-label");

        // Инструкция
        Label instruction = new Label("Для каждого конфликта выберите, какую версию сохранить:");
        instruction.getStyleClass().add("instruction-label");

        // ListView с конфликтами
        ListView<ConflictItem> listView = new ListView<>();
        listView.setPrefHeight(300);
        listView.getStyleClass().add("conflict-list");

        // Полностью отключаем выделение для предотвращения IndexOutOfBoundsException
        listView.setSelectionModel(null);
        listView.setFocusTraversable(false);

        ObservableList<ConflictItem> items = FXCollections.observableArrayList();

        for (ConflictInfo conflict : conflicts) {
            items.add(new ConflictItem(conflict));
        }
        listView.setItems(items);
        listView.setCellFactory(_ -> new ConflictListCell());

        // Кнопки массового выбора
        HBox massActionBox = new HBox(10);
        massActionBox.setAlignment(Pos.CENTER);

        Button chooseLocalBtn = new Button("Выбрать все LOCAL");
        chooseLocalBtn.getStyleClass().add("button-local");

        Button chooseRemoteBtn = new Button("Выбрать все REMOTE");
        chooseRemoteBtn.getStyleClass().add("button-remote");

        chooseLocalBtn.setOnAction(_ -> {
            for (ConflictItem item : items) {
                item.setChoice(ConflictResolution.LOCAL);
            }
            listView.refresh();
        });

        chooseRemoteBtn.setOnAction(_ -> {
            for (ConflictItem item : items) {
                item.setChoice(ConflictResolution.REMOTE);
            }
            listView.refresh();
        });

        massActionBox.getChildren().addAll(chooseLocalBtn, chooseRemoteBtn);

        // Кнопки управления
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Отмена");
        cancelBtn.getStyleClass().add("button-secondary");

        Button applyBtn = new Button("Применить");
        applyBtn.getStyleClass().add("button-primary");

        final boolean[] applied = {false};

        applyBtn.setOnAction(_ -> {
            boolean allResolved = items.stream()
                    .allMatch(item -> item.getChoice() != ConflictResolution.UNRESOLVED);

            if (allResolved) {
                for (ConflictItem item : items) {
                    resolutions.add(item.getChoice());
                }
                applied[0] = true;
                dialogStage.close();
            } else {
                CustomAlertDialog.showWarning("Не все конфликты разрешены", "Пожалуйста, выберите вариант для всех конфликтов");
            }
        });

        cancelBtn.setOnAction(_ -> {
            applied[0] = false;
            dialogStage.close();
        });

        buttonBox.getChildren().addAll(cancelBtn, applyBtn);

        root.getChildren().addAll(title, instruction, listView, massActionBox, buttonBox);
        
        Scene scene = new Scene(root, 700, 500);
        
        // Применяем стили через новую систему
        try {
            LOGGER.info("Programmatic dialog - Current theme is dark: {}", StyleUtils.isDarkTheme());
            
            // Добавляем базовые стили с переменными темы
            for (String stylesheet : StyleUtils.getBaseStylesheets()) {
                URL url = ConflictResolutionDialog.class.getResource(stylesheet);
                if (url != null) {
                    scene.getStylesheets().add(url.toExternalForm());
                    LOGGER.info("Programmatic dialog - Added stylesheet: {}", stylesheet);
                } else {
                    LOGGER.warn("Programmatic dialog - Stylesheet not found: {}", stylesheet);
                }
            }
            
            // Добавляем специфичные стили для диалога конфликтов
            scene.getStylesheets().add(
                    Objects.requireNonNull(ConflictResolutionDialog.class.getResource("/styles/conflict-dialog.css")).toExternalForm()
            );
            
            LOGGER.info("Programmatic dialog - Loaded stylesheets: {}", scene.getStylesheets());
        } catch (Exception e) {
            LOGGER.error("Programmatic dialog - Failed to load styles: {}", e.getMessage());
        }
        
        dialogStage.setScene(scene);
        dialogStage.setResizable(false);
        dialogStage.showAndWait();
        
        return applied[0];
    }

    /**
     * Ячейка ListView для отображения конфликта с двумя колонками сравнения
     */
    private static class ConflictListCell extends ListCell<ConflictItem> {
        private static final java.time.format.DateTimeFormatter DATE_FORMATTER =
                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        // Кэшированные UI элементы
        private VBox mainBox;
        private Label badge;
        private Label keyLabel;
        private VBox localColumn;
        private VBox skipColumn;
        private VBox remoteColumn;
        private HBox compareBox;

        // Элементы для LOCAL колонки
        private Label localTitleLabel;
        private Label localNewerLabel;
        private Label localTimeLabel;
        private VBox localFieldsBox;

        // Элементы для REMOTE колонки
        private Label remoteTitleLabel;
        private Label remoteNewerLabel;
        private Label remoteTimeLabel;
        private VBox remoteFieldsBox;

        private ConflictItem currentItem;

        public ConflictListCell() {
            // Создаем UI элементы один раз
            mainBox = new VBox(8);
            mainBox.getStyleClass().add("conflict-cell-box");
            mainBox.setPadding(new Insets(10));

            // === ЗАГОЛОВОК ===
            HBox headerBox = new HBox(10);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            headerBox.getStyleClass().add("conflict-header-box");

            badge = new Label();
            badge.getStyleClass().add("conflict-badge");

            keyLabel = new Label();
            keyLabel.getStyleClass().add("conflict-key-label");

            headerBox.getChildren().addAll(badge, keyLabel);

            // === СРАВНЕНИЕ КОЛОНОК ===
            compareBox = new HBox(10);
            compareBox.setAlignment(Pos.TOP_CENTER);

            // LOCAL колонка
            localColumn = new VBox(6);
            localColumn.setAlignment(Pos.TOP_LEFT);
            localColumn.getStyleClass().add("conflict-column");

            HBox localTitleBox = new HBox(6);
            localTitleBox.setAlignment(Pos.CENTER_LEFT);
            localTitleLabel = new Label("БД ПК");
            localTitleLabel.getStyleClass().add("column-title-label");
            localNewerLabel = new Label("НОВЕЕ");
            localNewerLabel.getStyleClass().add("newer-badge");
            localNewerLabel.setVisible(false);
            localNewerLabel.setManaged(false);
            localTitleBox.getChildren().addAll(localTitleLabel, localNewerLabel);
            localColumn.getChildren().add(localTitleBox);

            localTimeLabel = new Label();
            localTimeLabel.getStyleClass().add("time-label");
            localColumn.getChildren().add(localTimeLabel);

            localFieldsBox = new VBox(3);
            localFieldsBox.setPadding(new Insets(4, 0, 0, 0));
            localColumn.getChildren().add(localFieldsBox);

            // SKIP колонка
            skipColumn = buildSkipColumn();

            // REMOTE колонка
            remoteColumn = new VBox(6);
            remoteColumn.setAlignment(Pos.TOP_LEFT);
            remoteColumn.getStyleClass().add("conflict-column");

            HBox remoteTitleBox = new HBox(6);
            remoteTitleBox.setAlignment(Pos.CENTER_LEFT);
            remoteTitleLabel = new Label("БД АРХИВ");
            remoteTitleLabel.getStyleClass().add("column-title-label");
            remoteNewerLabel = new Label("НОВЕЕ");
            remoteNewerLabel.getStyleClass().add("newer-badge");
            remoteNewerLabel.setVisible(false);
            remoteNewerLabel.setManaged(false);
            remoteTitleBox.getChildren().addAll(remoteTitleLabel, remoteNewerLabel);
            remoteColumn.getChildren().add(remoteTitleBox);

            remoteTimeLabel = new Label();
            remoteTimeLabel.getStyleClass().add("time-label");
            remoteColumn.getChildren().add(remoteTimeLabel);

            remoteFieldsBox = new VBox(3);
            remoteFieldsBox.setPadding(new Insets(4, 0, 0, 0));
            remoteColumn.getChildren().add(remoteFieldsBox);

            VBox.setVgrow(localColumn, javafx.scene.layout.Priority.ALWAYS);
            VBox.setVgrow(remoteColumn, javafx.scene.layout.Priority.ALWAYS);
            HBox.setHgrow(localColumn, javafx.scene.layout.Priority.ALWAYS);
            HBox.setHgrow(remoteColumn, javafx.scene.layout.Priority.ALWAYS);

            compareBox.getChildren().addAll(localColumn, skipColumn, remoteColumn);

            mainBox.getChildren().addAll(headerBox, compareBox);
        }

        @Override
        protected void updateItem(ConflictItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                currentItem = null;
                return;
            }

            // Если элемент тот же, только обновляем выбор и стили
            if (item == currentItem) {
                updateColumnStyles(localColumn, skipColumn, remoteColumn, currentItem.getChoice());
                return;
            }

            currentItem = item;
            ConflictInfo conflict = item.getConflict();
            boolean localNewer = isLocalNewer(conflict);
            boolean remoteNewer = isRemoteNewer(conflict);

            // Обновляем данные без пересоздания UI
            badge.setText(getTypeLabel(conflict.type));
            keyLabel.setText(conflict.key);

            // Обновляем LOCAL колонку
            localNewerLabel.setVisible(localNewer);
            localNewerLabel.setManaged(localNewer);
            localTimeLabel.setText("Изменён: " + formatTimestamp(getUpdatedAt(conflict.local)));
            localFieldsBox.getChildren().clear();
            addFieldComparisons(localFieldsBox, conflict, true);

            // Обновляем REMOTE колонку
            remoteNewerLabel.setVisible(remoteNewer);
            remoteNewerLabel.setManaged(remoteNewer);
            remoteTimeLabel.setText("Изменён: " + formatTimestamp(getUpdatedAt(conflict.remote)));
            remoteFieldsBox.getChildren().clear();
            addFieldComparisons(remoteFieldsBox, conflict, false);

            // Обновляем стили
            updateColumnStyles(localColumn, skipColumn, remoteColumn, item.getChoice());

            // Устанавливаем graphic только если его нет
            if (getGraphic() == null) {
                // Предотвращаем выделение ячейки при клике - полностью блокируем mousePressed
                setOnMousePressed(event -> {
                    event.consume();
                });

                // Настраиваем клики только один раз
                localColumn.setOnMouseClicked(event -> {
                    if (currentItem != null) {
                        currentItem.setChoice(ConflictResolution.LOCAL);
                        updateColumnStyles(localColumn, skipColumn, remoteColumn, currentItem.getChoice());
                        event.consume();
                    }
                });

                skipColumn.setOnMouseClicked(event -> {
                    if (currentItem != null) {
                        currentItem.setChoice(ConflictResolution.SKIP);
                        updateColumnStyles(localColumn, skipColumn, remoteColumn, currentItem.getChoice());
                        event.consume();
                    }
                });

                remoteColumn.setOnMouseClicked(event -> {
                    if (currentItem != null) {
                        currentItem.setChoice(ConflictResolution.REMOTE);
                        updateColumnStyles(localColumn, skipColumn, remoteColumn, currentItem.getChoice());
                        event.consume();
                    }
                });

                setGraphic(mainBox);
            }
            setText(null);

            // Предотвращаем выделение ячейки при клике
            setMouseTransparent(false);
        }

        private void updateColumnStyles(VBox localColumn, VBox skipColumn, VBox remoteColumn, ConflictResolution choice) {
            // Remove all selection classes first
            localColumn.getStyleClass().removeAll("conflict-column-selected", "conflict-column-skip-selected");
            skipColumn.getStyleClass().removeAll("conflict-column-selected", "conflict-column-skip-selected");
            remoteColumn.getStyleClass().removeAll("conflict-column-selected", "conflict-column-skip-selected");
            
            if (choice == ConflictResolution.LOCAL) {
                localColumn.getStyleClass().add("conflict-column-selected");
            } else if (choice == ConflictResolution.REMOTE) {
                remoteColumn.getStyleClass().add("conflict-column-selected");
            } else if (choice == ConflictResolution.SKIP) {
                skipColumn.getStyleClass().add("conflict-column-skip-selected");
            }
        }

        private String getTypeLabel(String type) {
            return switch (type) {
                case "device" -> "УСТРОЙСТВО";
                case "scheme" -> "СХЕМА";
                case "device_location" -> "РАЗМЕЩЕНИЕ";
                default -> type.toUpperCase();
            };
        }

        private String getChoiceLabel(ConflictResolution choice) {
            return switch (choice) {
                case LOCAL -> "✓ БД ПК";
                case REMOTE -> "✓ БД АРХИВ";
                case SKIP -> "✗ Пропущен";
                case UNRESOLVED -> "? Не решено";
            };
        }

        private String getChoiceStyleClass(ConflictResolution choice) {
            return switch (choice) {
                case LOCAL -> "status-label-local";
                case REMOTE -> "status-label-remote";
                case SKIP -> "status-label-skip";
                case UNRESOLVED -> "status-label-unresolved";
            };
        }

        private boolean isLocalNewer(ConflictInfo conflict) {
            long localTime = getUpdatedAt(conflict.local);
            long remoteTime = getUpdatedAt(conflict.remote);
            return localTime > remoteTime;
        }

        private boolean isRemoteNewer(ConflictInfo conflict) {
            long localTime = getUpdatedAt(conflict.local);
            long remoteTime = getUpdatedAt(conflict.remote);
            return remoteTime > localTime;
        }

        private long getUpdatedAt(Object obj) {
            if (obj instanceof Device d) return d.getUpdatedAt();
            if (obj instanceof Scheme s) return s.getUpdatedAt();
            if (obj instanceof DeviceLocation loc) return loc.getUpdatedAt();
            return 0;
        }

        private VBox buildColumn(String title, Object obj, boolean isNewer, boolean isLocal, ConflictInfo conflict) {
            VBox column = new VBox(6);
            column.setAlignment(Pos.TOP_LEFT);
            column.getStyleClass().add("conflict-column");

            HBox titleBox = new HBox(6);
            titleBox.setAlignment(Pos.CENTER_LEFT);

            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("column-title-label");
            titleBox.getChildren().add(titleLabel);

            if (isNewer) {
                Label newerLabel = new Label("НОВЕЕ");
                newerLabel.getStyleClass().add("newer-badge");
                titleBox.getChildren().add(newerLabel);
            }

            column.getChildren().add(titleBox);

            long updatedAt = getUpdatedAt(obj);
            Label timeLabel = new Label("Изменён: " + formatTimestamp(updatedAt));
            timeLabel.getStyleClass().add("time-label");
            column.getChildren().add(timeLabel);

            VBox fieldsBox = new VBox(3);
            fieldsBox.setPadding(new Insets(4, 0, 0, 0));
            addFieldComparisons(fieldsBox, conflict, isLocal);
            column.getChildren().add(fieldsBox);

            return column;
        }

        private VBox buildSkipColumn() {
            VBox column = new VBox(6);
            column.setAlignment(Pos.CENTER);
            column.getStyleClass().addAll("conflict-column", "skip-column");

            Label titleLabel = new Label("⏭️");
            titleLabel.getStyleClass().add("skip-icon");
            column.getChildren().add(titleLabel);

            Label textLabel = new Label("Пропустить");
            textLabel.getStyleClass().add("skip-text");
            column.getChildren().add(textLabel);

            return column;
        }

        private void addFieldComparisons(VBox container, ConflictInfo conflict, boolean isLocal) {
            Object localObj = conflict.local;
            Object remoteObj = conflict.remote;

            if (conflict.type.equals("device")) {
                Device local = (Device) localObj;
                Device remote = (Device) remoteObj;
                addFieldRow(container, "Статус", local.getStatus(), remote.getStatus(), isLocal);
                addFieldRow(container, "Тип", local.getType(), remote.getType(), isLocal);
                addFieldRow(container, "Имя", local.getName(), remote.getName(), isLocal);
                addFieldRow(container, "Местоположение", local.getLocation(), remote.getLocation(), isLocal);
                addFieldRow(container, "Завод-изготовитель", local.getManufacturer(), remote.getManufacturer(), isLocal);
                addFieldRow(container, "Год", String.valueOf(local.getYear()), String.valueOf(remote.getYear()), isLocal);
                addFieldRow(container, "Предел измерения", local.getMeasurementLimit(), remote.getMeasurementLimit(), isLocal);
                addFieldRow(container, "Класс точности",
                        local.getAccuracyClass() != null ? local.getAccuracyClass().toString() : null,
                        remote.getAccuracyClass() != null ? remote.getAccuracyClass().toString() : null, isLocal);
                addFieldRow(container, "Номер крана", local.getValveNumber(), remote.getValveNumber(), isLocal);
                int localPhotos = local.getPhotos() != null ? local.getPhotos().size() : 0;
                int remotePhotos = remote.getPhotos() != null ? remote.getPhotos().size() : 0;
                addFieldRow(container, "Фото (шт)", String.valueOf(localPhotos), String.valueOf(remotePhotos), isLocal);
            } else if (conflict.type.equals("scheme")) {
                Scheme local = (Scheme) localObj;
                Scheme remote = (Scheme) remoteObj;
                addFieldRow(container, "Описание", local.getDescription(), remote.getDescription(), isLocal);
                int localDataLen = local.getData() != null ? local.getData().length() : 0;
                int remoteDataLen = remote.getData() != null ? remote.getData().length() : 0;
                addFieldRow(container, "Объём данных", localDataLen + " симв.", remoteDataLen + " симв.", isLocal);
            } else if (conflict.type.equals("device_location")) {
                DeviceLocation local = (DeviceLocation) localObj;
                DeviceLocation remote = (DeviceLocation) remoteObj;
                addFieldRow(container, "Координата X",
                        String.valueOf(local.getX()), String.valueOf(remote.getX()), isLocal);
                addFieldRow(container, "Координата Y",
                        String.valueOf(local.getY()), String.valueOf(remote.getY()), isLocal);
                addFieldRow(container, "Угол поворота",
                        String.valueOf(local.getRotation()), String.valueOf(remote.getRotation()), isLocal);
            }
        }

        private void addFieldRow(VBox container, String fieldName, String localValue, String remoteValue, boolean showLocal) {
            String displayValue = showLocal ? localValue : remoteValue;
            boolean differs = !java.util.Objects.equals(localValue, remoteValue);

            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("field-row");

            Label nameLabel = new Label(fieldName + ":");
            nameLabel.getStyleClass().add("field-name-label");

            String valueText = displayValue != null ? displayValue : "(пусто)";
            Label valueLabel = new Label(valueText);
            valueLabel.getStyleClass().add("field-value-label");
            if (differs) {
                valueLabel.getStyleClass().add("field-value-label-differs");
            }

            row.getChildren().addAll(nameLabel, valueLabel);
            container.getChildren().add(row);
        }

        private String formatTimestamp(long timestamp) {
            if (timestamp <= 0) return "неизвестно";
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(timestamp),
                    java.time.ZoneId.systemDefault()
            );
            return dateTime.format(DATE_FORMATTER);
        }
    }
}
