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
            this.choice = ConflictResolution.UNRESOLVED;
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
        
        titleLabel.setText("Разрешение конфликтов");
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
        dialogStage.setTitle("Разрешение конфликтов синхронизации");
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        CustomAlertDialog.setAppIcon(dialogStage);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #ffffff;");
        
        // Заголовок
        Label title = new Label(String.format("Разрешение конфликтов (%d)", conflicts.size()));
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
     * Ячейка ListView для отображения конфликта с кнопками выбора
     */
    private static class ConflictListCell extends ListCell<ConflictItem> {
        @Override
        protected void updateItem(ConflictItem item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            
            VBox mainBox = new VBox(5);
            mainBox.getStyleClass().add("conflict-cell-box");

            // Заголовок конфликта
            Label titleLabel = new Label(getConflictTitle(item));
            titleLabel.getStyleClass().add("conflict-cell-title");

            // Детали конфликта
            Label detailsLabel = new Label(getConflictDetails(item));
            detailsLabel.getStyleClass().add("conflict-cell-details");

            // Кнопки выбора
            HBox buttonBox = new HBox(10);
            buttonBox.setAlignment(Pos.CENTER_LEFT);
            
            ToggleGroup toggleGroup = new ToggleGroup();
            
            RadioButton localRadio = new RadioButton("LOCAL");
            localRadio.setToggleGroup(toggleGroup);
            localRadio.setUserData(ConflictResolution.LOCAL);
            localRadio.getStyleClass().add("conflict-cell-radio");

            RadioButton remoteRadio = new RadioButton("REMOTE");
            remoteRadio.setToggleGroup(toggleGroup);
            remoteRadio.setUserData(ConflictResolution.REMOTE);
            remoteRadio.getStyleClass().add("conflict-cell-radio");
            
            RadioButton skipRadio = new RadioButton("Пропустить");
            skipRadio.setToggleGroup(toggleGroup);
            skipRadio.setUserData(ConflictResolution.SKIP);
            skipRadio.getStyleClass().add("conflict-cell-radio");
            
            // Устанавливаем выбранное значение
            switch (item.getChoice()) {
                case LOCAL -> localRadio.setSelected(true);
                case REMOTE -> remoteRadio.setSelected(true);
                case SKIP -> skipRadio.setSelected(true);
                case UNRESOLVED -> toggleGroup.selectToggle(null);
            }
            
            // Обработчик изменения выбора
            toggleGroup.selectedToggleProperty().addListener((_, _, newVal) -> {
                if (newVal != null) {
                    item.setChoice((ConflictResolution) newVal.getUserData());
                } else {
                    item.setChoice(ConflictResolution.UNRESOLVED);
                }
            });
            
            buttonBox.getChildren().addAll(localRadio, remoteRadio, skipRadio);
            mainBox.getChildren().addAll(titleLabel, detailsLabel, buttonBox);
            
            setGraphic(mainBox);
            setText(null);
        }
        
        private String getConflictTitle(ConflictItem item) {
            String typeStr = switch (item.getConflict().type) {
                case "device" -> "Устройство";
                case "scheme" -> "Схема";
                case "device_location" -> "Локация";
                default -> item.getConflict().type;
            };
            
            String statusStr = switch (item.getChoice()) {
                case LOCAL -> "✓ LOCAL";
                case REMOTE -> "✓ REMOTE";
                case SKIP -> "✗ Пропущено";
                case UNRESOLVED -> "? Не решено";
            };
            
            return String.format("[%s] %s - %s", typeStr, item.getConflict().key, statusStr);
        }
        
        private String getConflictDetails(ConflictItem item) {
            ConflictInfo conflict = item.getConflict();
            
            if (conflict.type.equals("device")) {
                Device local = (Device) conflict.local;
                Device remote = (Device) conflict.remote;
                return String.format("📍 LOCAL: '%s' (изменен %s) | 📡 REMOTE: '%s' (изменен %s)", 
                        local.getName(), formatTimestamp(local.getUpdatedAt()),
                        remote.getName(), formatTimestamp(remote.getUpdatedAt()));
            } else if (conflict.type.equals("scheme")) {
                Scheme local = (Scheme) conflict.local;
                Scheme remote = (Scheme) conflict.remote;
                return String.format("📍 LOCAL: '%s' (изменен %s) | 📡 REMOTE: '%s' (изменен %s)", 
                        local.getName(), formatTimestamp(local.getUpdatedAt()),
                        remote.getName(), formatTimestamp(remote.getUpdatedAt()));
            } else {
                DeviceLocation local = (DeviceLocation) conflict.local;
                DeviceLocation remote = (DeviceLocation) conflict.remote;
                return String.format("📍 LOCAL: координаты (%.1f,%.1f) | 📡 REMOTE: координаты (%.1f,%.1f)", 
                        local.getX(), local.getY(), remote.getX(), remote.getY());
            }
        }
        
        private String formatTimestamp(Long timestamp) {
            if (timestamp == null) return "неизвестно";
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp), 
                java.time.ZoneId.systemDefault()
            );
            return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"));
        }
    }
}
