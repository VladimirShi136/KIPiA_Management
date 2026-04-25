package com.kipia.management.kipia_management.utils;

import com.kipia.management.kipia_management.Main;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.*;
import javafx.stage.Window;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javafx.scene.shape.Rectangle;
import com.kipia.management.kipia_management.models.Device;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Кастомные алерты и диалоги без системной шапки.
 *
 * @author vladimir_shi (redesign)
 * @since 28.03.2026
 */
public class CustomAlertDialog {

    private static final Logger LOGGER = LogManager.getLogger(CustomAlertDialog.class);

    public enum AlertType {INFO, SUCCESS, WARNING, ERROR, CONFIRM}

    // Кнопки — совместимость со старым CustomAlert
    public static final ButtonType RETRY_BUTTON = new ButtonType("Повтор", ButtonBar.ButtonData.APPLY);
    public static final ButtonType CANCEL_BUTTON = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
    public static final ButtonType OK_BUTTON = new ButtonType("ОК", ButtonBar.ButtonData.OK_DONE);
    public static final ButtonType YES_BUTTON = new ButtonType("Да", ButtonBar.ButtonData.YES);
    public static final ButtonType NO_BUTTON = new ButtonType("Нет", ButtonBar.ButtonData.NO);

    // ════════════════════════════════════════════════════════════════════════
    //  ПУБЛИЧНЫЙ API
    // ════════════════════════════════════════════════════════════════════════

    public static void showInfo(String title, String message) {
        dialog(AlertType.INFO, title, message).addBtn(OK_BUTTON, true).showAndWait();
    }

    public static void showSuccess(String title, String message) {
        dialog(AlertType.SUCCESS, title, message).addBtn(OK_BUTTON, true).showAndWait();
    }

    public static void showWarning(String title, String message) {
        dialog(AlertType.WARNING, title, message).addBtn(OK_BUTTON, true).showAndWait();
    }

    public static void showError(String title, String message) {
        dialog(AlertType.ERROR, title, message).addBtn(OK_BUTTON, true).showAndWait();
    }

    public static boolean showConfirmation(String title, String message) {
        Dlg d = dialog(AlertType.CONFIRM, title, message)
                .addBtn(YES_BUTTON, true)
                .addBtn(NO_BUTTON, false);
        d.showAndWait();
        return d.result == YES_BUTTON;
    }

    public static Optional<ButtonType> showConfirmationWithOptions(
            String title, String message, ButtonType... buttons) {
        Dlg d = dialog(AlertType.CONFIRM, title, message);
        boolean first = true;
        for (ButtonType bt : buttons) {
            d.addBtn(bt, first);
            first = false;
        }
        d.showAndWait();
        return Optional.ofNullable(d.result);
    }

    /**
     * Диалог выбора из списка (альтернатива ChoiceDialog с единым стилем)
     */
    public static Optional<String> showChoiceDialog(String title, String message,
                                                    List<String> choices, String defaultValue) {
        boolean dark = isDark();
        Stage stage = createStage();

        // Заголовок
        Label titleLabel = new Label(title);
        titleLabel.setStyle(
                "-fx-font-size:13px;-fx-font-weight:bold;" +
                        "-fx-text-fill:" + (dark ? "#aec6de" : "#2c3a47") + ";" +
                        "-fx-font-family:'Segoe UI',Arial,sans-serif;"
        );

        // Сообщение
        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(350);
        msgLabel.setStyle(
                "-fx-font-size:13px;-fx-line-spacing:2;" +
                        "-fx-text-fill:" + (dark ? "#95a5a6" : "#555") + ";" +
                        "-fx-font-family:'Segoe UI',Arial,sans-serif;"
        );

        // ComboBox для выбора
        ComboBox<String> comboBox = new ComboBox<>(FXCollections.observableArrayList(choices));
        comboBox.setValue(defaultValue);
        comboBox.setPrefWidth(300);
        comboBox.setStyle(comboBoxStyle());

        // Кнопки
        Button cancelBtn = new Button("Отмена");
        Button okBtn = new Button("ОК");
        okBtn.setDefaultButton(true);

        setupButtonStyles(cancelBtn, false);
        setupButtonStyles(okBtn, true);

        HBox btnBar = createButtonBar(cancelBtn, okBtn);

        // Сборка
        VBox content = new VBox(12);
        content.setPadding(new Insets(18, 16, 0, 16));
        content.setStyle("-fx-background-color: transparent;");
        content.getChildren().addAll(titleLabel, msgLabel, comboBox);

        Region divider = createDivider();

        VBox root = createRoot(content, divider, btnBar);
        root.setMinWidth(380);
        root.setMaxWidth(420);

        setupDragToMove(root, stage);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);

        final String[] result = {null};
        okBtn.setOnAction(_ -> {
            result[0] = comboBox.getValue();
            stage.close();
        });
        cancelBtn.setOnAction(_ -> stage.close());

        stage.showAndWait();
        return result[0] != null ? Optional.of(result[0]) : Optional.empty();
    }

    public static ButtonType showAdvancedError(String title, String message, Throwable ex) {
        Dlg d = dialog(AlertType.ERROR, title, message)
                .addBtn(CANCEL_BUTTON, false)
                .addBtn(RETRY_BUTTON, false)
                .addBtn(OK_BUTTON, true);

        if (ex != null) {
            TextArea ta = new TextArea(stackTrace(ex));
            ta.setEditable(false);
            ta.setWrapText(true);
            ta.setPrefHeight(110);
            ta.setMaxWidth(Double.MAX_VALUE);
            ta.setStyle("-fx-font-family:'Consolas',monospace;-fx-font-size:11px;-fx-background-color:transparent;");
            TitledPane tp = new TitledPane("Подробности", ta);
            tp.setExpanded(false);
            tp.setStyle("-fx-font-size:11px;");
            d.body.getChildren().add(tp);
            LOGGER.error("Ошибка: {}", message, ex);
        } else {
            LOGGER.error("Ошибка: {}", message);
        }

        d.showAndWait();
        return d.result != null ? d.result : CANCEL_BUTTON;
    }

    public static Optional<String> showTextInputDialog(
            String title, String prompt, String defaultValue) {
        Dlg d = dialog(AlertType.CONFIRM, title, prompt);

        TextField tf = new TextField(defaultValue != null ? defaultValue : "");
        tf.setStyle(inputFieldStyle());
        d.body.getChildren().add(tf);
        d.addBtn(CANCEL_BUTTON, false).addBtn(OK_BUTTON, true);
        d.showAndWait();
        return (d.result == OK_BUTTON) ? Optional.of(tf.getText()) : Optional.empty();
    }

    /**
     * Диалог выбора цвета с ColorPicker
     */
    public static Optional<Color> showColorPickerDialog(String title, Color initialColor) {
        boolean dark = isDark();
        Stage stage = createStage();

        // ColorPicker
        ColorPicker colorPicker = new ColorPicker(initialColor);
        colorPicker.setStyle(inputFieldStyle());

        // Предпросмотр цвета
        Rectangle colorPreview = new Rectangle(50, 30);
        colorPreview.setArcWidth(5);
        colorPreview.setArcHeight(5);
        colorPreview.setFill(initialColor);
        colorPreview.setStroke(dark ? Color.web("#4A5568") : Color.web("#bdc3c7"));
        colorPreview.setStrokeWidth(1);

        colorPicker.valueProperty().addListener((_, _, newColor) -> colorPreview.setFill(newColor));

        // Контейнер для предпросмотра и пикера
        HBox colorBox = new HBox(10);
        colorBox.setAlignment(Pos.CENTER_LEFT);
        colorBox.getChildren().addAll(colorPicker, colorPreview);

        // Кнопки
        Button okBtn = new Button("Применить");
        Button cancelBtn = new Button("Отмена");
        okBtn.setDefaultButton(true);

        setupButtonStyles(okBtn, true);
        setupButtonStyles(cancelBtn, false);

        HBox btnBar = createButtonBar(cancelBtn, okBtn);

        // Иконка (палитра)
        SVGPath colorIcon = new SVGPath();
        colorIcon.setContent(
                "M8,0C3.6,0,0,3.6,0,8s3.6,8,8,8s8-3.6,8-8S12.4,0,8,0z" +
                        "M8,14c-3.3,0-6-2.7-6-6s2.7-6,6-6s6,2.7,6,6S11.3,14,8,14z" +
                        "M5,7.5A1.5,1.5,0,1,0,5,10.5A1.5,1.5,0,1,0,5,7.5z" +
                        "M8,4.5A1.5,1.5,0,1,0,8,7.5A1.5,1.5,0,1,0,8,4.5z" +
                        "M11,7.5A1.5,1.5,0,1,0,11,10.5A1.5,1.5,0,1,0,11,7.5z"
        );
        colorIcon.setFill(Color.web(dark ? "#7090b0" : "#465261"));

        HBox topBox = createTopBox(colorIcon, "Настройка цвета", title);

        // Сборка контента
        VBox content = new VBox(10);
        content.setPadding(new Insets(0, 20, 4, 20));
        content.setStyle("-fx-background-color: transparent;");
        content.getChildren().add(colorBox);

        Region divider = createDivider();
        VBox.setMargin(divider, new Insets(12, 0, 12, 0));

        VBox root = createRoot(topBox, content, divider, btnBar);
        root.setMinWidth(320);
        root.setMaxWidth(380);

        setupDragToMove(root, stage);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);

        final Color[] result = {null};
        okBtn.setOnAction(_ -> {
            result[0] = colorPicker.getValue();
            stage.close();
        });
        cancelBtn.setOnAction(_ -> stage.close());

        stage.showAndWait();
        return result[0] != null ? Optional.of(result[0]) : Optional.empty();
    }

    /**
     * Диалог изменения шрифта
     */
    public static Optional<javafx.scene.text.Font> showFontDialog(javafx.scene.text.Font initialFont) {
        boolean dark = isDark();
        Stage stage = createStage();

        // Поля для ввода
        TextField sizeField = new TextField(String.valueOf((int)initialFont.getSize()));
        sizeField.setPromptText("Размер");
        sizeField.setPrefWidth(80);
        sizeField.setStyle(inputFieldStyle());

        CheckBox boldCheck = new CheckBox("Жирный");
        boldCheck.setSelected(initialFont.getStyle().contains("Bold"));

        CheckBox italicCheck = new CheckBox("Курсив");
        italicCheck.setSelected(initialFont.getStyle().contains("Italic"));

        String checkStyle =
                "-fx-text-fill:" + (dark ? "#ecf0f1" : "#333") + ";" +
                        "-fx-font-size:13px;" +
                        (dark ? "-fx-mark-color:#ecf0f1;-fx-mark-highlight-color:#ecf0f1;" : "") +
                        (dark ? "-fx-background-color:transparent;" : "");
        boldCheck.setStyle(checkStyle);
        italicCheck.setStyle(checkStyle);

        String labelStyle =
                "-fx-text-fill:" + (dark ? "#95a5a6" : "#555") + ";" +
                        "-fx-font-size:13px;";

        Label sizeLabel = new Label("Размер:");
        sizeLabel.setStyle(labelStyle);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(10, 0, 0, 0));
        grid.add(sizeLabel, 0, 0);
        grid.add(sizeField, 1, 0);
        grid.add(boldCheck, 0, 1);
        grid.add(italicCheck, 1, 1);

        // Кнопки
        Button okBtn = new Button("Применить");
        Button cancelBtn = new Button("Отмена");
        okBtn.setDefaultButton(true);

        setupButtonStyles(okBtn, true);
        setupButtonStyles(cancelBtn, false);

        HBox btnBar = createButtonBar(cancelBtn, okBtn);

        // Иконка (буква А / шрифт)
        SVGPath fontIcon = new SVGPath();
        fontIcon.setContent(
                "M6.6,2L1,14h2.2l1.1-2.8h5.4L10.8,14H13L7.4,2H6.6z" +
                        "M5.1,9.2L7,4.6l1.9,4.6H5.1z"
        );
        fontIcon.setFill(Color.web(dark ? "#7090b0" : "#465261"));

        HBox topBox = createTopBox(fontIcon, "Настройка шрифта", "Настройки шрифта");

        // Сборка
        VBox content = new VBox(10);
        content.setPadding(new Insets(0, 20, 4, 20));
        content.setStyle("-fx-background-color: transparent;");
        content.getChildren().add(grid);

        Region divider = createDivider();
        VBox.setMargin(divider, new Insets(12, 0, 12, 0));

        VBox root = createRoot(topBox, content, divider, btnBar);
        root.setMinWidth(320);
        root.setMaxWidth(380);

        setupDragToMove(root, stage);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);

        final javafx.scene.text.Font[] result = {null};
        okBtn.setOnAction(_ -> {
            try {
                int fontSize = Integer.parseInt(sizeField.getText());
                fontSize = Math.max(8, Math.min(72, fontSize));

                FontWeight weight = boldCheck.isSelected() ? FontWeight.BOLD : FontWeight.NORMAL;
                FontPosture posture = italicCheck.isSelected() ? FontPosture.ITALIC : FontPosture.REGULAR;

                result[0] = javafx.scene.text.Font.font("Arial", weight, posture, fontSize);
                stage.close();
            } catch (NumberFormatException ex) {
                showError("Ошибка", "Введите корректный размер шрифта (8-72)");
            }
        });
        cancelBtn.setOnAction(_ -> stage.close());

        // Перекрашиваем квадратик чекбокса в тёмной теме через lookup после рендера
        if (dark) {
            stage.setOnShown(_ -> {
                String boxStyle =
                        "-fx-background-color:#3a4a5a;" +
                                "-fx-border-color:#6A7D90;-fx-border-width:2px;" +
                                "-fx-border-radius:3px;-fx-background-radius:3px;";
                for (CheckBox cb : new CheckBox[]{boldCheck, italicCheck}) {
                    javafx.scene.Node box = cb.lookup(".box");
                    if (box != null) box.setStyle(boxStyle);
                    javafx.scene.Node mark = cb.lookup(".mark");
                    if (mark != null) {
                        mark.setStyle("-fx-background-color:#ecf0f1;-fx-padding:3px;");
                        mark.setOpacity(cb.isSelected() ? 1.0 : 0.0);
                    }
                    cb.selectedProperty().addListener((_, _, selected) -> {
                        javafx.scene.Node m = cb.lookup(".mark");
                        if (m != null) m.setOpacity(selected ? 1.0 : 0.0);
                    });
                }
            });
        }

        stage.showAndWait();
        return result[0] != null ? Optional.of(result[0]) : Optional.empty();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  УВЕДОМЛЕНИЕ О СОХРАНЕНИИ (В СТИЛЕ LOADING INDICATOR)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Показывает уведомление о сохранении в стиле LoadingIndicator.
     * Крутилка по центру экрана с текстом, автоматически исчезает через 1.5-2 секунды.
     *
     * @param message текст сообщения (например, "Сохранение" или "Автосохранение")
     * @param durationSec длительность отображения в секундах (рекомендуется 1.5-2.0)
     */
    public static void showSaveNotification(String message, double durationSec) {
        showSaveNotificationInternal(message, durationSec, null);
    }

    /**
     * Показывает уведомление о сохранении и выполняет callback после завершения анимации.
     * Используется когда нужно задержать переход до завершения анимации.
     *
     * @param message текст сообщения (например, "Сохранение" или "Автосохранение")
     * @param durationSec длительность отображения в секундах (рекомендуется 0.8-1.2 для навигации)
     * @param onFinished callback который выполнится после завершения анимации
     */
    public static void showSaveNotificationAndWait(String message, double durationSec, Runnable onFinished) {
        showSaveNotificationInternal(message, durationSec, onFinished);
    }

    private static void showSaveNotificationInternal(String message, double durationSec, Runnable onFinished) {
        try {
            boolean dark = isDark();

            Stage stage = new Stage();
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.initModality(Modality.NONE);
            stage.setAlwaysOnTop(true);
            stage.setResizable(false);

            // ProgressIndicator (крутилка)
            VBox content = getBox(message, dark);
            content.setAlignment(Pos.CENTER);
            content.setStyle(
                    "-fx-background-color: transparent;" +
                            "-fx-padding:30;"
            );

            // Scene
            Scene scene = new Scene(content);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);

            // Позиционирование по центру главного окна
            Window main = Stage.getWindows().stream()
                    .filter(Window::isShowing).filter(w -> w != stage)
                    .findFirst().orElse(null);

            stage.show();
            if (main != null) {
                stage.setX(main.getX() + (main.getWidth() - content.getPrefWidth()) / 2);
                stage.setY(main.getY() + (main.getHeight() - content.getPrefHeight()) / 2);
            }

            // Анимация появления/исчезновения
            content.setOpacity(0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), content);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), content);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(_ -> {
                stage.close();
                if (onFinished != null) {
                    onFinished.run();
                }
            });

            fadeIn.play();

            PauseTransition pause = new PauseTransition(Duration.seconds(durationSec));
            pause.setOnFinished(_ -> fadeOut.play());
            pause.play();

        } catch (Exception e) {
            LOGGER.warn("Не удалось показать уведомление о сохранении: {}", e.getMessage());
        }
    }

    private static VBox getBox(String message, boolean dark) {
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(60, 60);
        progressIndicator.setStyle(
                "-fx-progress-color: " + (dark ? "#4a9c2f" : "#4a7c2f") + ";"
        );

        // Текст сообщения
        Label messageLabel = new Label(message);
        messageLabel.setStyle(
                "-fx-font-family:'Segoe UI',Arial,sans-serif;" +
                        "-fx-font-size:14px;" +
                        "-fx-font-weight:500;" +
                        "-fx-text-fill:" + (dark ? "#ecf0f1" : "#333333") + ";"
        );

        // Контейнер с крутилкой и текстом (прозрачный фон без рамки)
        return new VBox(15, progressIndicator, messageLabel);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ДИАЛОГ ВЫБОРА ПРИБОРА
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Кастомный диалог выбора прибора с таблицей и иконкой.
     * Заменяет showDeviceSelectionDialog + DialogStyler + StyleUtils.createDeviceSelectionDialog.
     *
     * @param devices список доступных приборов
     * @return выбранный прибор или null если отменено
     */
    public static Device showDeviceSelection(List<Device> devices) {
        Stage stage = createStage();

        // ===== ИКОНКА (прибор) =====
        SVGPath deviceIcon = getSvgPath();
        deviceIcon.getStyleClass().add("device-icon");

        StackPane iconWrap = new StackPane(deviceIcon);
        iconWrap.setPrefSize(40, 40);
        iconWrap.setMinSize(40, 40);

        // ===== ШАПКА ДИАЛОГА =====
        Label typeLabel = new Label("Выбор прибора");
        typeLabel.getStyleClass().add("type-label");

        Label titleLabel = new Label("Выберите прибор для схемы");
        titleLabel.setWrapText(true);
        titleLabel.getStyleClass().add("title-label");

        Label countLabel = new Label("Доступно приборов: " + devices.size());
        countLabel.getStyleClass().add("count-label");

        VBox rightContent = new VBox(3, typeLabel, titleLabel, countLabel);
        rightContent.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(rightContent, Priority.ALWAYS);

        HBox topBox = new HBox(16, iconWrap, rightContent);
        topBox.setAlignment(Pos.TOP_LEFT);
        topBox.setPadding(new Insets(20, 20, 12, 20));
        topBox.getStyleClass().add("top-box");

        // ===== ТАБЛИЦА =====
        TableView<Device> tableView = new TableView<>();
        tableView.getStyleClass().add("device-selection-table");
        tableView.setPrefHeight(320);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Device, String> modelCol = new TableColumn<>("Модель");
        modelCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        modelCol.setMinWidth(200);

        TableColumn<Device, String> inventoryCol = new TableColumn<>("Инв. номер");
        inventoryCol.setCellValueFactory(new PropertyValueFactory<>("inventoryNumber"));
        inventoryCol.setMinWidth(140);

        TableColumn<Device, String> valveCol = new TableColumn<>("Кран");
        valveCol.setCellValueFactory(new PropertyValueFactory<>("valveNumber"));
        valveCol.setMinWidth(80);

        TableColumn<Device, String> locationCol = new TableColumn<>("Местоположение");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        locationCol.setMinWidth(160);

        tableView.getColumns().addAll(modelCol, inventoryCol, valveCol, locationCol);

        ObservableList<Device> items = FXCollections.observableArrayList(devices);
        tableView.setItems(items);
        if (!items.isEmpty()) tableView.getSelectionModel().selectFirst();

        // ===== КНОПКИ =====
        Button cancelBtn = new Button("Отмена");
        Button okBtn = new Button("Выбрать");
        okBtn.setDefaultButton(true);
        okBtn.setDisable(tableView.getSelectionModel().getSelectedItem() == null);
        tableView.getSelectionModel().selectedItemProperty().addListener(
                (_, _, sel) -> okBtn.setDisable(sel == null)
        );

        cancelBtn.getStyleClass().add("device-selection-btn-ghost");
        okBtn.getStyleClass().add("device-selection-btn-primary");

        // ===== КНОПОЧНАЯ ПАНЕЛЬ =====
        HBox btnBar = new HBox(10, cancelBtn, okBtn);
        btnBar.setAlignment(Pos.CENTER_RIGHT);
        btnBar.setPadding(new Insets(12, 20, 20, 20));
        btnBar.getStyleClass().add("device-selection-btn-bar");

        // ===== ТЕЛО =====
        VBox body = new VBox();
        body.setPadding(new Insets(10, 20, 8, 20));
        body.getChildren().add(tableView);

        // ===== РАЗДЕЛИТЕЛЬ =====
        Region divider = new Region();
        divider.getStyleClass().add("device-selection-divider");

        // ===== ROOT =====
        VBox root = new VBox(topBox, body, divider, btnBar);
        root.getStyleClass().add("device-selection-dialog");
        root.setMinWidth(620);
        root.setMaxWidth(760);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);

        setupDragToMove(root, stage);

        // ===== СЦЕНА =====
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        // Применяем текущую тему через StyleUtils
        String currentTheme = StyleUtils.getCurrentTheme();
        scene.getStylesheets().add(
                Objects.requireNonNull(Main.class.getResource(currentTheme)).toExternalForm()
        );

        stage.setScene(scene);

        // ===== ДЕЙСТВИЯ КНОПОК =====
        final Device[] result = {null};
        okBtn.setOnAction(_ -> {
            Device selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                result[0] = selected;
                stage.close();
            } else {
                showWarning("Выбор прибора", "Пожалуйста, выберите прибор из таблицы!");
            }
        });
        cancelBtn.setOnAction(_ -> stage.close());

        stage.showAndWait();
        return result[0];
    }

    private static SVGPath getSvgPath() {
        SVGPath deviceIcon = new SVGPath();
        deviceIcon.setContent(
                // Внешний корпус (основной круг)
                "M12,2 C6.48,2 2,6.48 2,12 s4.48,10 10,10 s10,-4.48 10,-10 S17.52,2 12,2 z " +

                        // Внутренний круг (циферблат)
                        "M12,3.5 C7.31,3.5 3.5,7.31 3.5,12 s3.81,8.5 8.5,8.5 s8.5,-3.81 8.5,-8.5 S16.69,3.5 12,3.5 z " +

                        // Деления: длинные (0, 90, 180, 270 градусов)
                        "M12,4.2 L12,5 M12,19 L12,19.8 M4.2,12 L5,12 M19,12 L19.8,12 " +

                        // Деления: короткие (45, 135, 225, 315 градусов)
                        "M17.66,6.34 L17.17,6.83 M6.83,17.17 L6.34,17.66 " +
                        "M6.34,6.34 L6.83,6.83 M17.17,17.17 L17.66,17.66 " +

                        // Центральная точка оси
                        "M12,12 m-0.4,0 a0.4,0.4 0 1,0 0.8,0 a0.4,0.4 0 1,0 -0.8,0 " +

                        // Стрелка (от центра вверх-влево, как в 10 часов)
                        "M12,12 L9.2,8.8 " +

                        // Треугольное острие стрелки
                        "M9.2,8.8 L8.8,9.5 L9.6,9.2 Z"
        );
        return deviceIcon;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ВНУТРЕННИЙ КЛАСС Dlg
    // ════════════════════════════════════════════════════════════════════════

    private static Dlg dialog(AlertType type, String title, String message) {
        return new Dlg(type, title, message);
    }

    private static class Dlg {

        final Stage stage;
        final VBox body;
        final HBox btnBar;
        ButtonType result;

        Dlg(AlertType type, String title, String message) {
            boolean dark = isDark();
            IconColors iconColors = IconColors.of(type, dark);

            stage = createStage();

            // ===== ИКОНКА =====
            StackPane iconWrap = buildLargeIcon(type, iconColors);

            // ===== ТИП =====
            Label typeLabel = new Label(getTypeLabel(type));
            typeLabel.setStyle(
                    "-fx-font-size:11px;-fx-font-weight:bold;-fx-letter-spacing:0.06em;" +
                            "-fx-text-fill:" + (dark ? "#5a6a7a" : "#888780") + ";" +
                            "-fx-padding:0 0 5 0;"
            );

            // ===== ЗАГОЛОВОК =====
            Label titleLabel = new Label(title);
            titleLabel.setWrapText(true);
            titleLabel.setStyle(
                    "-fx-font-size:14px;-fx-font-weight:bold;" +
                            "-fx-text-fill:" + (dark ? "#aec6de" : "#2c3a47") + ";" +
                            "-fx-padding:0 0 8 0;"
            );

            // ===== СООБЩЕНИЕ =====
            Label msgLabel = new Label(message);
            msgLabel.setWrapText(true);
            msgLabel.setMaxWidth(350);
            msgLabel.setStyle(
                    "-fx-font-size:13px;-fx-line-spacing:3;" +
                            "-fx-text-fill:" + (dark ? "#95a5a6" : "#555") + ";" +
                            "-fx-padding:0 0 5 0;"
            );

            VBox rightContent = new VBox(8, typeLabel, titleLabel, msgLabel);
            rightContent.setAlignment(Pos.TOP_LEFT);
            VBox.setVgrow(rightContent, Priority.ALWAYS);

            // ===== ВЕРХНЯЯ ЧАСТЬ =====
            HBox topBox = new HBox(14, iconWrap, rightContent);
            topBox.setAlignment(Pos.TOP_LEFT);
            topBox.setPadding(new Insets(16, 18, 12, 18));
            topBox.setStyle("-fx-background-color: transparent;");

            // ===== ТЕЛО =====
            body = new VBox();
            body.setPadding(new Insets(0, 18, 8, 18));
            body.setStyle("-fx-background-color: transparent;");

            // ===== КНОПКИ =====
            btnBar = new HBox(12);
            btnBar.setAlignment(Pos.CENTER_RIGHT);
            btnBar.setPadding(new Insets(10, 18, 16, 18));
            btnBar.setStyle("-fx-background-color: transparent;");

            Region divider = createDivider();
            VBox.setMargin(divider, new Insets(12, 0, 12, 0));

            VBox root = createRoot(topBox, divider, body, btnBar);
            root.setMaxWidth(400);
            root.setMinWidth(340);

            setupDragToMove(root, stage);

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);
        }

        Dlg addBtn(ButtonType bt, boolean primary) {
            isDark();
            Button btn = getButton(bt, primary);
            btnBar.getChildren().add(btn);
            return this;
        }

        private Button getButton(ButtonType bt, boolean primary) {
            Button btn = new Button(bt.getText());
            btn.setMinWidth(72);
            btn.setOnAction(_ -> {
                result = bt;
                stage.close();
            });

            setupButtonStyles(btn, primary);
            return btn;
        }

        void showAndWait() {
            stage.showAndWait();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ЦВЕТА ТОЛЬКО ДЛЯ ИКОНОК
    // ════════════════════════════════════════════════════════════════════════

    private record IconColors(String svgColor) {
        static IconColors of(AlertType type, boolean dark) {
            return switch (type) {
                case INFO    -> new IconColors(dark ? "#7090b0" : "#465261");   // синий
                case SUCCESS -> new IconColors(dark ? "#4a9c2f" : "#4a7c2f");   // зелёный
                case WARNING -> new IconColors(dark ? "#b88a30" : "#9a6200");   // жёлтый
                case ERROR   -> new IconColors(dark ? "#c04040" : "#b03030");   // красный
                case CONFIRM -> new IconColors(dark ? "#a07cc5" : "#7b52a8");   // фиолетовый
            };
        }
    }

    private static String getTypeLabel(AlertType type) {
        return switch (type) {
            case INFO -> "Информация";
            case SUCCESS -> "Успех";
            case WARNING -> "Предупреждение";
            case ERROR -> "Ошибка";
            case CONFIRM -> "Подтверждение";
        };
    }

    // ════════════════════════════════════════════════════════════════════════
    //  КРУПНАЯ ИКОНКА 40x40
    // ════════════════════════════════════════════════════════════════════════

    private static StackPane buildLargeIcon(AlertType type, IconColors colors) {
        SVGPath path = new SVGPath();
        path.setFill(Color.web(colors.svgColor()));
        path.setContent(switch (type) {
            case INFO -> "M8,0C3.6,0,0,3.6,0,8s3.6,8,8,8s8-3.6,8-8S12.4,0,8,0z" +
                    "M9,12H7V7h2V12zM9,5.5H7v-2h2V5.5z";
            case SUCCESS -> "M8,0C3.6,0,0,3.6,0,8s3.6,8,8,8s8-3.6,8-8S12.4,0,8,0z" +
                    "M6.5,11.5L3,8l1.4-1.4l2.1,2.1l4.1-4.1L12,6L6.5,11.5z";
            case WARNING -> "M8.9,1.4l6.6,11.8c0.4,0.7-0.1,1.6-0.9,1.6H1.4C0.6,14.8,0.1,13.9,0.5,13.2L7.1,1.4" +
                    "C7.5,0.7,8.5,0.7,8.9,1.4zM7,6v4h2V6H7zM7,11v2h2v-2H7z";
            case ERROR -> "M8,0C3.6,0,0,3.6,0,8s3.6,8,8,8s8-3.6,8-8S12.4,0,8,0z" +
                    "M11.5,11.5l-1.4,1.4L8,10.8l-2.1,2.1l-1.4-1.4L6.6,9.9L4.5,7.8l1.4-1.4L8,8.5l2.1-2.1l1.4,1.4L9.4,9.9z";
            case CONFIRM -> "M8,0C3.6,0,0,3.6,0,8s3.6,8,8,8s8-3.6,8-8S12.4,0,8,0z" +
                    "M8.5,12.5h-1V11h1V12.5z" +
                    "M9.8,7.8C9.3,8.3,9,8.7,9,9.8H7C7,8,7.7,7.2,8.3,6.6C8.7,6.2,9,5.9,9,5.2C9,4.5,8.6,4,8,4" +
                    "S7,4.5,7,5H5C5,3.3,6.3,2,8,2s3,1.3,3,3C11,6.2,10.4,7,9.8,7.8z";
        });
        // Масштабируем SVG (нарисован в 16px viewport) до ~22px визуально
        path.setScaleX(1.35);
        path.setScaleY(1.35);

        // Без овального фона — просто иконка в прозрачном контейнере 48px
        StackPane wrap = new StackPane(path);
        wrap.setPrefSize(48, 48);
        wrap.setMinSize(48, 48);
        wrap.setStyle("-fx-background-color: transparent;");
        return wrap;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  СТИЛИ КНОПОК (нейтральные)
    // ════════════════════════════════════════════════════════════════════════

    private static String btnPrimary(boolean dark) {
        String bg = dark ? "#4A5568" : "#465261";
        String fg = dark ? "#ecf0f1" : "white";
        return "-fx-background-color:" + bg + ";" +
                "-fx-text-fill:" + fg + ";" +
                "-fx-border-color:transparent;-fx-background-radius:6px;-fx-border-radius:6px;" +
                "-fx-padding:7px 20px;-fx-font-size:13px;" +
                "-fx-font-family:'Segoe UI',Arial,sans-serif;-fx-cursor:hand;";
    }

    private static String btnGhost(boolean dark) {
        return "-fx-background-color:transparent;" +
                "-fx-text-fill:" + (dark ? "#95a5a6" : "#5F5E5A") + ";" +
                "-fx-border-color:" + (dark ? "#4A5568" : "#B4B2A9") + ";" +
                "-fx-border-width:1px;-fx-background-radius:6px;-fx-border-radius:6px;" +
                "-fx-padding:7px 20px;-fx-font-size:13px;" +
                "-fx-font-family:'Segoe UI',Arial,sans-serif;-fx-cursor:hand;";
    }

    private static String btnGhostHover(boolean dark) {
        return "-fx-background-color:" + (dark ? "#2d3e50" : "#E8ECF0") + ";" +
                "-fx-text-fill:" + (dark ? "#ecf0f1" : "#333") + ";" +
                "-fx-border-color:" + (dark ? "#6A7D90" : "#888780") + ";" +
                "-fx-border-width:1px;-fx-background-radius:6px;-fx-border-radius:6px;" +
                "-fx-padding:7px 20px;-fx-font-size:13px;" +
                "-fx-font-family:'Segoe UI',Arial,sans-serif;-fx-cursor:hand;";
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPER МЕТОДЫ ДЛЯ СОЗДАНИЯ КОМПОНЕНТОВ
    // ════════════════════════════════════════════════════════════════════════

    private static Stage createStage() {
        Stage stage = new Stage();
        Stage.getWindows().stream()
                .filter(Window::isShowing).findFirst().ifPresent(stage::initOwner);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        return stage;
    }

    private static HBox createButtonBar(Button... buttons) {
        HBox btnBar = new HBox(8);
        btnBar.setAlignment(Pos.CENTER_RIGHT);
        btnBar.setPadding(new Insets(12, 16, 16, 16));
        btnBar.setStyle("-fx-background-color: transparent;");
        btnBar.getChildren().addAll(buttons);
        return btnBar;
    }

    private static HBox createTopBox(SVGPath icon, String typeLabel, String titleLabel) {
        boolean dark = isDark();
        StackPane iconWrap = new StackPane(icon);
        iconWrap.setPrefSize(40, 40);
        iconWrap.setMinSize(40, 40);
        iconWrap.setStyle("-fx-background-color: transparent;");

        Label typeLbl = new Label(typeLabel);
        typeLbl.setStyle(
                "-fx-font-size:11px;-fx-font-weight:bold;" +
                        "-fx-text-fill:" + (dark ? "#5a6a7a" : "#888780") + ";" +
                        "-fx-padding:0 0 4 0;"
        );

        Label titleLbl = new Label(titleLabel);
        titleLbl.setWrapText(true);
        titleLbl.setStyle(
                "-fx-font-size:14px;-fx-font-weight:bold;" +
                        "-fx-text-fill:" + (dark ? "#aec6de" : "#2c3a47") + ";" +
                        "-fx-padding:0 0 4 0;"
        );

        VBox rightContent = new VBox(4, typeLbl, titleLbl);
        rightContent.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(rightContent, Priority.ALWAYS);

        HBox topBox = new HBox(16, iconWrap, rightContent);
        topBox.setAlignment(Pos.TOP_LEFT);
        topBox.setPadding(new Insets(20, 20, 12, 20));
        topBox.setStyle("-fx-background-color: transparent;");
        return topBox;
    }

    private static Region createDivider() {
        boolean dark = isDark();
        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color:" + (dark ? "#2d3e50" : "#e8e8e8") + ";");
        return divider;
    }

    private static VBox createRoot(Node... children) {
        boolean dark = isDark();
        VBox root = new VBox(children);
        root.setStyle(
                "-fx-background-color:" + (dark ? "#252d38" : "#ffffff") + ";" +
                        "-fx-background-radius:12px;" +
                        "-fx-border-color:" + (dark ? "#2d3e50" : "#d0d4d8") + ";" +
                        "-fx-border-width:1px;-fx-border-radius:12px;"
        );
        root.setEffect(new javafx.scene.effect.DropShadow(
                20, 0, 5, dark ? Color.rgb(0, 0, 0, 0.6) : Color.rgb(0, 0, 0, 0.22)
        ));

        Rectangle clipRect = new Rectangle();
        clipRect.setArcWidth(24);
        clipRect.setArcHeight(24);
        clipRect.widthProperty().bind(root.widthProperty());
        clipRect.heightProperty().bind(root.heightProperty());
        root.setClip(clipRect);
        return root;
    }

    private static void setupDragToMove(VBox root, Stage stage) {
        final double[] dragDelta = new double[2];
        root.setOnMousePressed(e -> {
            dragDelta[0] = stage.getX() - e.getScreenX();
            dragDelta[1] = stage.getY() - e.getScreenY();
        });
        root.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() + dragDelta[0]);
            stage.setY(e.getScreenY() + dragDelta[1]);
        });
    }

    private static String inputFieldStyle() {
        boolean dark = isDark();
        return "-fx-background-color:" + (dark ? "#2d2d2d" : "white") + ";" +
                "-fx-text-fill:" + (dark ? "#ecf0f1" : "#333") + ";" +
                "-fx-border-color:" + (dark ? "#4A5568" : "#bdc3c7") + ";" +
                "-fx-border-width:1px;-fx-border-radius:5px;-fx-background-radius:5px;" +
                "-fx-padding:6px 10px;-fx-font-size:13px;";
    }

    private static String comboBoxStyle() {
        boolean dark = isDark();
        return "-fx-background-color:" + (dark ? "#2d2d2d" : "white") + ";" +
                "-fx-text-fill:" + (dark ? "#ecf0f1" : "#333") + ";" +
                "-fx-border-color:" + (dark ? "#4A5568" : "#bdc3c7") + ";" +
                "-fx-border-width:1px;-fx-border-radius:5px;-fx-background-radius:5px;" +
                "-fx-padding:6px 10px;-fx-font-size:13px;";
    }

    private static void setupButtonStyles(Button btn, boolean primary) {
        boolean dark = isDark();
        if (primary) {
            btn.setStyle(btnPrimary(dark));
            btn.setOnMouseEntered(_ -> btn.setStyle(btnPrimary(dark) + "-fx-opacity:0.85;"));
            btn.setOnMouseExited(_ -> btn.setStyle(btnPrimary(dark)));
        } else {
            btn.setStyle(btnGhost(dark));
            btn.setOnMouseEntered(_ -> btn.setStyle(btnGhostHover(dark)));
            btn.setOnMouseExited(_ -> btn.setStyle(btnGhost(dark)));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  УТИЛИТЫ
    // ════════════════════════════════════════════════════════════════════════

    private static boolean isDark() {
        return StyleUtils.getCurrentTheme().contains("dark");
    }

    private static String stackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString()).append("\n");
        for (StackTraceElement el : t.getStackTrace()) sb.append("  at ").append(el).append("\n");
        if (t.getCause() != null) sb.append("Caused by: ").append(t.getCause());
        return sb.toString();
    }
}