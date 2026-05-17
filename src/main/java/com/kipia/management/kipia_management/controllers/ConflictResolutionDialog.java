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
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.kipia.management.kipia_management.utils.StyleUtils;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
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
        
        // Обработчики кнопок
        chooseAllLocalButton.setOnAction(_ -> chooseAll(ConflictResolution.LOCAL));
        chooseAllRemoteButton.setOnAction(_ -> chooseAll(ConflictResolution.REMOTE));
        applyButton.setOnAction(_ -> applyResolution());
        cancelButton.setOnAction(_ -> cancelResolution());
        
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
            
            // Применяем текущую тему
            String currentTheme = StyleUtils.getCurrentTheme();
            LOGGER.info("Current theme: {}", currentTheme);
            
            // Добавляем стили для конфликтного диалога
            try {
                // Всегда добавляем светлую тему как базовую
                URL lightStylesUrl = ConflictResolutionDialog.class.getResource("/styles/light-theme.css");
                if (lightStylesUrl != null) {
                    scene.getStylesheets().add(lightStylesUrl.toExternalForm());
                    LOGGER.info("Added light-theme.css for conflict dialog");
                } else {
                    LOGGER.warn("light-theme.css not found at /styles/light-theme.css");
                }
                
                // Если темная тема, добавляем её после светлой (для перекрытия)
                if (currentTheme.contains("dark")) {
                    URL darkStylesUrl = ConflictResolutionDialog.class.getResource("/styles/dark-theme.css");
                    if (darkStylesUrl != null) {
                        scene.getStylesheets().add(darkStylesUrl.toExternalForm());
                        LOGGER.info("Added dark-theme.css for conflict dialog");
                    } else {
                        LOGGER.warn("dark-theme.css not found at /styles/dark-theme.css");
                    }
                }
                
            } catch (Exception e) {
                LOGGER.error("Failed to load conflict dialog styles: {}", e.getMessage());
            }
            
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
        root.setStyle("-fx-background-color: #ffffff;");
        
        // Заголовок
        Label title = new Label(String.format("Разрешение конфликтов данных (%d)", conflicts.size()));
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        // Инструкция
        Label instruction = new Label("Для каждого конфликта выберите, какую версию сохранить:");
        instruction.setStyle("-fx-font-size: 14px; -fx-text-fill: #34495e; -fx-wrap-text: true;");
        
        // ListView с конфликтами
        ListView<ConflictItem> listView = new ListView<>();
        listView.setPrefHeight(300);
        listView.setStyle("-fx-border-color: #bdc3c7; -fx-border-radius: 5; -fx-background-color: #f8f9fa;");
        
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
        chooseLocalBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 8 15;");
        
        Button chooseRemoteBtn = new Button("Выбрать все REMOTE");
        chooseRemoteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 8 15;");
        
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
        cancelBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 8 15;");
        
        Button applyBtn = new Button("Применить");
        applyBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 8 15;");
        
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
        
        // Применяем стили если доступны
        try {
            String currentTheme = StyleUtils.getCurrentTheme();
            LOGGER.info("Programmatic dialog - Current theme: {}", currentTheme);
            
            // Всегда добавляем светлую тему как базовую
            URL lightStylesUrl = ConflictResolutionDialog.class.getResource("/styles/light-theme.css");
            if (lightStylesUrl != null) {
                scene.getStylesheets().add(lightStylesUrl.toExternalForm());
                LOGGER.info("Programmatic dialog - Added light-theme.css");
            }
            
            // Если темная тема, добавляем её после светлой
            if (currentTheme.contains("dark")) {
                URL darkStylesUrl = ConflictResolutionDialog.class.getResource("/styles/dark-theme.css");
                if (darkStylesUrl != null) {
                    scene.getStylesheets().add(darkStylesUrl.toExternalForm());
                    LOGGER.info("Programmatic dialog - Added dark-theme.css");
                }
            }
            
            LOGGER.info("Programmatic dialog - Loaded stylesheets: {}", scene.getStylesheets());
        } catch (Exception e) {
            LOGGER.error("Programmatic dialog - Failed to load styles: {}", e.getMessage());
            // Если стили не найдены, используем встроенные
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

        @Override
        protected void updateItem(ConflictItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            ConflictInfo conflict = item.getConflict();
            boolean localNewer = isLocalNewer(conflict);
            boolean remoteNewer = isRemoteNewer(conflict);

            VBox mainBox = new VBox(8);
            mainBox.getStyleClass().add("conflict-cell-box");
            mainBox.setPadding(new Insets(10));

            // === ЗАГОЛОВОК ===
            HBox headerBox = new HBox(10);
            headerBox.setAlignment(Pos.CENTER_LEFT);

            Label badge = new Label(getTypeLabel(conflict.type));
            badge.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 2 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");

            Label keyLabel = new Label(conflict.key);
            keyLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            Label statusLabel = new Label(getChoiceLabel(item.getChoice()));
            statusLabel.setStyle(getChoiceStyle(item.getChoice()));

            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

            headerBox.getChildren().addAll(badge, keyLabel, spacer, statusLabel);

            // === СРАВНЕНИЕ КОЛОНОК ===
            HBox compareBox = new HBox(15);
            compareBox.setAlignment(Pos.TOP_CENTER);

            VBox localColumn = buildColumn("ЛОКАЛЬНО", conflict.local, localNewer, true, conflict);
            VBox remoteColumn = buildColumn("В АРХИВЕ", conflict.remote, remoteNewer, false, conflict);

            String selectedBorder = "-fx-border-color: #3498db; -fx-border-width: 2; -fx-border-radius: 6;";
            String unselectedBorder = "-fx-border-color: #bdc3c7; -fx-border-width: 1; -fx-border-radius: 6;";
            String baseStyle = "-fx-background-color: #f8f9fa; -fx-padding: 10; -fx-background-radius: 6;";

            updateColumnStyles(localColumn, remoteColumn, item.getChoice(), baseStyle, selectedBorder, unselectedBorder);

            VBox.setVgrow(localColumn, javafx.scene.layout.Priority.ALWAYS);
            VBox.setVgrow(remoteColumn, javafx.scene.layout.Priority.ALWAYS);
            HBox.setHgrow(localColumn, javafx.scene.layout.Priority.ALWAYS);
            HBox.setHgrow(remoteColumn, javafx.scene.layout.Priority.ALWAYS);

            compareBox.getChildren().addAll(localColumn, remoteColumn);

            // === КНОПКИ ВЫБОРА ===
            HBox buttonBox = new HBox(12);
            buttonBox.setAlignment(Pos.CENTER_LEFT);

            ToggleGroup toggleGroup = new ToggleGroup();

            RadioButton localRadio = new RadioButton("LOCAL");
            localRadio.setToggleGroup(toggleGroup);
            localRadio.setUserData(ConflictResolution.LOCAL);

            RadioButton remoteRadio = new RadioButton("В АРХИВЕ");
            remoteRadio.setToggleGroup(toggleGroup);
            remoteRadio.setUserData(ConflictResolution.REMOTE);

            RadioButton skipRadio = new RadioButton("Пропустить");
            skipRadio.setToggleGroup(toggleGroup);
            skipRadio.setUserData(ConflictResolution.SKIP);

            switch (item.getChoice()) {
                case LOCAL -> localRadio.setSelected(true);
                case REMOTE -> remoteRadio.setSelected(true);
                case SKIP -> skipRadio.setSelected(true);
                case UNRESOLVED -> toggleGroup.selectToggle(null);
            }

            // Предупреждение при пропуске
            Label skipWarning = new Label("⚠ Объект будет пропущен. Конфликт сохранится при следующем merge.");
            skipWarning.setStyle("-fx-text-fill: #e67e22; -fx-font-size: 11px; -fx-wrap-text: true;");
            skipWarning.setVisible(item.getChoice() == ConflictResolution.SKIP);
            skipWarning.setManaged(item.getChoice() == ConflictResolution.SKIP);

            toggleGroup.selectedToggleProperty().addListener((_, _, newVal) -> {
                if (newVal != null) {
                    item.setChoice((ConflictResolution) newVal.getUserData());
                } else {
                    item.setChoice(ConflictResolution.UNRESOLVED);
                }
                updateColumnStyles(localColumn, remoteColumn, item.getChoice(), baseStyle, selectedBorder, unselectedBorder);
                statusLabel.setText(getChoiceLabel(item.getChoice()));
                statusLabel.setStyle(getChoiceStyle(item.getChoice()));
                boolean isSkip = newVal != null && newVal.getUserData() == ConflictResolution.SKIP;
                skipWarning.setVisible(isSkip);
                skipWarning.setManaged(isSkip);
            });

            buttonBox.getChildren().addAll(localRadio, remoteRadio, skipRadio);

            mainBox.getChildren().addAll(headerBox, compareBox, buttonBox, skipWarning);

            setGraphic(mainBox);
            setText(null);
        }

        private void updateColumnStyles(VBox localColumn, VBox remoteColumn, ConflictResolution choice,
                                        String baseStyle, String selectedBorder, String unselectedBorder) {
            if (choice == ConflictResolution.LOCAL) {
                localColumn.setStyle(baseStyle + selectedBorder);
                remoteColumn.setStyle(baseStyle + unselectedBorder);
            } else if (choice == ConflictResolution.REMOTE) {
                localColumn.setStyle(baseStyle + unselectedBorder);
                remoteColumn.setStyle(baseStyle + selectedBorder);
            } else {
                localColumn.setStyle(baseStyle + unselectedBorder);
                remoteColumn.setStyle(baseStyle + unselectedBorder);
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
                case LOCAL -> "✓ LOCAL";
                case REMOTE -> "✓ В АРХИВЕ";
                case SKIP -> "✗ Пропущен";
                case UNRESOLVED -> "? Не решено";
            };
        }

        private String getChoiceStyle(ConflictResolution choice) {
            return switch (choice) {
                case LOCAL -> "-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 12px;";
                case REMOTE -> "-fx-text-fill: #2980b9; -fx-font-weight: bold; -fx-font-size: 12px;";
                case SKIP -> "-fx-text-fill: #e67e22; -fx-font-weight: bold; -fx-font-size: 12px;";
                case UNRESOLVED -> "-fx-text-fill: #c0392b; -fx-font-weight: bold; -fx-font-size: 12px;";
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

            HBox titleBox = new HBox(6);
            titleBox.setAlignment(Pos.CENTER_LEFT);

            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #2c3e50;");
            titleBox.getChildren().add(titleLabel);

            if (isNewer) {
                Label newerLabel = new Label("НОВЕЕ");
                newerLabel.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 1 6; -fx-background-radius: 4; -fx-font-size: 10px; -fx-font-weight: bold;");
                titleBox.getChildren().add(newerLabel);
            }

            column.getChildren().add(titleBox);

            long updatedAt = getUpdatedAt(obj);
            Label timeLabel = new Label("Изменён: " + formatTimestamp(updatedAt));
            timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");
            column.getChildren().add(timeLabel);

            VBox fieldsBox = new VBox(3);
            fieldsBox.setPadding(new Insets(4, 0, 0, 0));
            addFieldComparisons(fieldsBox, conflict, isLocal);
            column.getChildren().add(fieldsBox);

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

            Label nameLabel = new Label(fieldName + ":");
            nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d; -fx-min-width: 100;");

            String valueText = displayValue != null ? displayValue : "(пусто)";
            Label valueLabel = new Label(valueText);
            if (differs) {
                valueLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #c0392b; -fx-font-weight: bold;");
            } else {
                valueLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2c3e50;");
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
