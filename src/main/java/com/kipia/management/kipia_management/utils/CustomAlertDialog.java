package com.kipia.management.kipia_management.utils;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.*;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Кастомные алерты без системной шапки.
 *
 * @author vladimir_shi (redesign)
 */
public class CustomAlertDialog {

    private static final Logger LOGGER = LogManager.getLogger(CustomAlertDialog.class);

    public enum AlertType { INFO, SUCCESS, WARNING, ERROR, CONFIRM }

    // Кнопки — совместимость со старым CustomAlert
    public static final ButtonType RETRY_BUTTON  = new ButtonType("Повтор",  ButtonBar.ButtonData.APPLY);
    public static final ButtonType CANCEL_BUTTON = new ButtonType("Отмена",  ButtonBar.ButtonData.CANCEL_CLOSE);
    public static final ButtonType OK_BUTTON     = new ButtonType("ОК",      ButtonBar.ButtonData.OK_DONE);
    public static final ButtonType YES_BUTTON    = new ButtonType("Да",      ButtonBar.ButtonData.YES);
    public static final ButtonType NO_BUTTON     = new ButtonType("Нет",     ButtonBar.ButtonData.NO);

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
                .addBtn(NO_BUTTON, false)
                .addBtn(YES_BUTTON, true);
        d.showAndWait();
        return d.result == YES_BUTTON;
    }

    public static Optional<ButtonType> showConfirmationWithOptions(
            String title, String message, ButtonType... buttons) {
        Dlg d = dialog(AlertType.CONFIRM, title, message);
        boolean first = true;
        for (ButtonType bt : buttons) { d.addBtn(bt, first); first = false; }
        d.showAndWait();
        return Optional.ofNullable(d.result);
    }

    /**
     * Диалог выбора из списка (альтернатива ChoiceDialog с единым стилем)
     */
    public static Optional<String> showChoiceDialog(String title, String message,
                                                    List<String> choices, String defaultValue) {
        boolean dark = isDark();

        Stage stage = new Stage();
        Stage.getWindows().stream()
                .filter(Window::isShowing).findFirst().ifPresent(stage::initOwner);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);

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
                "-fx-font-size:12.5px;-fx-line-spacing:2;" +
                        "-fx-text-fill:" + (dark ? "#95a5a6" : "#555") + ";" +
                        "-fx-font-family:'Segoe UI',Arial,sans-serif;"
        );

        // ComboBox для выбора
        ComboBox<String> comboBox = new ComboBox<>(FXCollections.observableArrayList(choices));
        comboBox.setValue(defaultValue);
        comboBox.setPrefWidth(300);
        comboBox.setStyle(
                "-fx-background-color:" + (dark ? "#2d2d2d" : "white") + ";" +
                        "-fx-text-fill:" + (dark ? "#ecf0f1" : "#333") + ";" +
                        "-fx-border-color:" + (dark ? "#4A5568" : "#bdc3c7") + ";" +
                        "-fx-border-width:1px;-fx-border-radius:5px;-fx-background-radius:5px;" +
                        "-fx-padding:6px 10px;-fx-font-size:13px;"
        );

        // Кнопки
        Button cancelBtn = new Button("Отмена");
        Button okBtn = new Button("Удалить");
        okBtn.setDefaultButton(true);

        HBox btnBar = new HBox(8);
        btnBar.setAlignment(Pos.CENTER_RIGHT);
        btnBar.setPadding(new Insets(12, 16, 16, 16));
        btnBar.setStyle("-fx-background-color:" + (dark ? "#252d38" : "#ffffff") + ";");
        btnBar.getChildren().addAll(cancelBtn, okBtn);

        // Стили кнопок
        cancelBtn.setStyle(btnGhost(dark));
        cancelBtn.setOnMouseEntered(_ -> cancelBtn.setStyle(btnGhostHover(dark)));
        cancelBtn.setOnMouseExited(_ -> cancelBtn.setStyle(btnGhost(dark)));

        okBtn.setStyle(btnPrimary(dark));
        okBtn.setOnMouseEntered(_ -> okBtn.setStyle(btnPrimary(dark) + "-fx-opacity:0.85;"));
        okBtn.setOnMouseExited(_ -> okBtn.setStyle(btnPrimary(dark)));

        // Сборка
        VBox content = new VBox(12);
        content.setPadding(new Insets(18, 16, 0, 16));
        content.setStyle("-fx-background-color:" + (dark ? "#252d38" : "#ffffff") + ";");
        content.getChildren().addAll(titleLabel, msgLabel, comboBox);

        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color:" + (dark ? "#2d3e50" : "#e8e8e8") + ";");

        VBox root = new VBox(content, divider, btnBar);
        root.setStyle(
                "-fx-background-color:" + (dark ? "#252d38" : "#ffffff") + ";" +
                        "-fx-background-radius:10px;" +
                        "-fx-border-color:" + (dark ? "#2d3e50" : "#d0d4d8") + ";" +
                        "-fx-border-width:1px;-fx-border-radius:10px;" +
                        "-fx-effect:dropshadow(gaussian," + (dark ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.22)") + ",20,0,0,5);"
        );
        root.setMinWidth(380);
        root.setMaxWidth(420);

        // Drag to move
        final double[] dragDelta = new double[2];
        root.setOnMousePressed(e -> {
            dragDelta[0] = stage.getX() - e.getScreenX();
            dragDelta[1] = stage.getY() - e.getScreenY();
        });
        root.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() + dragDelta[0]);
            stage.setY(e.getScreenY() + dragDelta[1]);
        });

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        try {
            scene.getStylesheets().add(Objects.requireNonNull(
                    CustomAlertDialog.class.getResource(StyleUtils.getCurrentTheme())
            ).toExternalForm());
        } catch (Exception ignored) {}

        stage.setScene(scene);

        final String[] result = {null};
        okBtn.setOnAction(e -> {
            result[0] = comboBox.getValue();
            stage.close();
        });
        cancelBtn.setOnAction(e -> stage.close());

        stage.showAndWait();
        return result[0] != null ? Optional.of(result[0]) : Optional.empty();
    }

    public static ButtonType showAdvancedError(String title, String message, Throwable ex) {
        Dlg d = dialog(AlertType.ERROR, title, message)
                .addBtn(CANCEL_BUTTON, false)
                .addBtn(RETRY_BUTTON,  false)
                .addBtn(OK_BUTTON,     true);

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
        boolean dark = isDark();

        TextField tf = new TextField(defaultValue != null ? defaultValue : "");
        tf.setStyle(
                "-fx-background-color:" + (dark ? "#2d2d2d" : "white") + ";" +
                        "-fx-text-fill:"        + (dark ? "#ecf0f1" : "#333") + ";" +
                        "-fx-border-color:"     + (dark ? "#4A5568" : "#bdc3c7") + ";" +
                        "-fx-border-width:1px;-fx-border-radius:5px;-fx-background-radius:5px;" +
                        "-fx-padding:6px 10px;-fx-font-size:13px;"
        );
        d.body.getChildren().add(tf);
        d.addBtn(CANCEL_BUTTON, false).addBtn(OK_BUTTON, true);
        d.showAndWait();
        return (d.result == OK_BUTTON) ? Optional.of(tf.getText()) : Optional.empty();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  УВЕДОМЛЕНИЕ ОБ АВТОСОХРАНЕНИИ
    // ════════════════════════════════════════════════════════════════════════

    public static void showAutoSaveNotification(String message, double durationSec) {
        try {
            boolean dark = isDark();

            Stage stage = new Stage();
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.initModality(Modality.NONE);
            stage.setAlwaysOnTop(true);
            stage.setResizable(false);

            // Иконка дискеты (как в оригинале, но стилизованная под вариант C)
            SVGPath icon = new SVGPath();
            icon.setContent(
                    "M13,0L2,0C0.9,0,0,0.9,0,2L0,14C0,15.1,0.9,16,2,16L14,16C15.1,16,16,15.1,16,14L16,3Z" +
                            "M8,14C6.3,14,5,12.7,5,11C5,9.3,6.3,8,8,8C9.7,8,11,9.3,11,11C11,12.7,9.7,14,8,14Z" +
                            "M11,5L2,5L2,2L11,2Z"
            );
            icon.setFill(Color.web(dark ? "#4a9c2f" : "#4a7c2f"));
            icon.setScaleX(1.2);
            icon.setScaleY(1.2);

            // Круглый фон для иконки (как у алертов)
            StackPane iconWrap = new StackPane(icon);
            iconWrap.setPrefSize(32, 32);
            iconWrap.setMinSize(32, 32);
            iconWrap.setStyle(
                    "-fx-background-color:" + (dark ? "#1e3a1a" : "#E8F3E0") + ";" +
                            "-fx-background-radius:50%;"
            );

            // Текст уведомления
            Label messageLabel = new Label(message);
            messageLabel.setStyle(
                    "-fx-font-family:'Segoe UI',Arial,sans-serif;" +
                            "-fx-font-size:12px;" +
                            "-fx-font-weight:500;" +
                            "-fx-text-fill:" + (dark ? "#bdc3c7" : "#444441") + ";"
            );

            // Контейнер с иконкой и текстом
            HBox content = new HBox(12, iconWrap, messageLabel);
            content.setAlignment(Pos.CENTER_LEFT);
            content.setPadding(new Insets(10, 18, 10, 14));
            content.setStyle(
                    "-fx-background-color:" + (dark ? "#2c3e50" : "#f5f5f2") + ";" +
                            "-fx-background-radius:8px;" +
                            "-fx-border-color:" + (dark ? "#4A5568" : "#D3D1C7") + ";" +
                            "-fx-border-width:1px;-fx-border-radius:8px;" +
                            "-fx-effect:dropshadow(gaussian," + (dark ? "rgba(0,0,0,0.5)" : "rgba(0,0,0,0.15)") + ",12,0,0,3);"
            );

            Scene scene = new Scene(content);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);

            // Позиционирование
            Window main = Stage.getWindows().stream()
                    .filter(Window::isShowing).filter(w -> w != stage)
                    .findFirst().orElse(null);

            stage.show();
            if (main != null) {
                stage.setX(main.getX() + main.getWidth() - stage.getWidth() - 14);
                stage.setY(main.getY() + 44);
            }

            // Анимация появления/исчезновения
            content.setOpacity(0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(220), content);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(280), content);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(_ -> stage.close());

            fadeIn.play();

            PauseTransition pause = new PauseTransition(Duration.seconds(durationSec));
            pause.setOnFinished(_ -> fadeOut.play());
            pause.play();

        } catch (Exception e) {
            LOGGER.warn("Не удалось показать уведомление: {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ВНУТРЕННИЙ КЛАСС Dlg
    // ════════════════════════════════════════════════════════════════════════

    private static Dlg dialog(AlertType type, String title, String message) {
        return new Dlg(type, title, message);
    }

    private static class Dlg {

        final Stage stage;
        final VBox  body;
        ButtonType  result;
        private double dragX, dragY;

        Dlg(AlertType type, String title, String message) {
            boolean dark = isDark();
            IconColors iconColors = IconColors.of(type, dark);

            stage = new Stage();
            Stage.getWindows().stream()
                    .filter(Window::isShowing).findFirst().ifPresent(stage::initOwner);
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);

            // ===== ИКОНКА (увеличенная, 40x40) =====
            StackPane iconWrap = buildLargeIcon(type, iconColors);

            // ===== ТИП (например "Информация", "Ошибка") =====
            Label typeLabel = new Label(getTypeLabel(type, dark));
            typeLabel.setStyle(
                    "-fx-font-size:10px;-fx-font-weight:bold;-fx-letter-spacing:0.06em;" +
                            "-fx-text-fill:" + (dark ? "#5a6a7a" : "#888780") + ";" +
                            "-fx-font-family:'Segoe UI',Arial,sans-serif;"
            );

            // ===== ЗАГОЛОВОК =====
            Label titleLabel = new Label(title);
            titleLabel.setWrapText(true);
            titleLabel.setStyle(
                    "-fx-font-size:13px;-fx-font-weight:bold;" +
                            "-fx-text-fill:" + (dark ? "#aec6de" : "#2c3a47") + ";" +
                            "-fx-font-family:'Segoe UI',Arial,sans-serif;"
            );

            // ===== СООБЩЕНИЕ =====
            Label msgLabel = new Label(message);
            msgLabel.setWrapText(true);
            msgLabel.setMaxWidth(320);
            msgLabel.setStyle(
                    "-fx-font-size:12.5px;-fx-line-spacing:2;" +
                            "-fx-text-fill:" + (dark ? "#95a5a6" : "#555") + ";" +
                            "-fx-font-family:'Segoe UI',Arial,sans-serif;"
            );

            VBox rightContent = new VBox(4, typeLabel, titleLabel, msgLabel);
            rightContent.setAlignment(Pos.TOP_LEFT);
            VBox.setVgrow(rightContent, Priority.ALWAYS);

            // ===== ВЕРХНЯЯ ЧАСТЬ: иконка слева + контент =====
            HBox topBox = new HBox(14, iconWrap, rightContent);
            topBox.setAlignment(Pos.TOP_LEFT);
            topBox.setPadding(new Insets(18, 16, 14, 16));
            topBox.setStyle("-fx-background-color:" + (dark ? "#252d38" : "#ffffff") + ";");

            // ===== ТЕЛО (для дополнительных элементов, например TextInput) =====
            body = new VBox();
            body.setPadding(new Insets(0, 16, 8, 16));
            body.setStyle("-fx-background-color:" + (dark ? "#252d38" : "#ffffff") + ";");

            // ===== КНОПКИ =====
            HBox btnBar = new HBox(8);
            btnBar.setAlignment(Pos.CENTER_RIGHT);
            btnBar.setPadding(new Insets(8, 16, 16, 16));
            btnBar.setStyle("-fx-background-color:" + (dark ? "#252d38" : "#ffffff") + ";");

            // ===== РАЗДЕЛИТЕЛЬНАЯ ЛИНИЯ =====
            Region divider = new Region();
            divider.setPrefHeight(1);
            divider.setStyle("-fx-background-color:" + (dark ? "#2d3e50" : "#e8e8e8") + ";");

            // ===== КОРЕНЬ =====
            VBox root = new VBox(topBox, divider, body, btnBar);
            root.setStyle(
                    "-fx-background-color:" + (dark ? "#252d38" : "#ffffff") + ";" +
                            "-fx-background-radius:10px;" +
                            "-fx-border-color:" + (dark ? "#2d3e50" : "#d0d4d8") + ";" +
                            "-fx-border-width:1px;-fx-border-radius:10px;" +
                            "-fx-effect:dropshadow(gaussian," +
                            (dark ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.22)") +
                            ",20,0,0,5);"
            );
            root.setMaxWidth(400);
            root.setMinWidth(340);
            root.setUserData(btnBar);

            // Drag to move
            root.setOnMousePressed(e -> { dragX = e.getScreenX() - stage.getX(); dragY = e.getScreenY() - stage.getY(); });
            root.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - dragX); stage.setY(e.getScreenY() - dragY); });

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            try {
                scene.getStylesheets().add(Objects.requireNonNull(
                        CustomAlertDialog.class.getResource(StyleUtils.getCurrentTheme())
                ).toExternalForm());
            } catch (Exception ignored) {}

            stage.setScene(scene);
        }

        Dlg addBtn(ButtonType bt, boolean primary) {
            HBox btnBar = (HBox) stage.getScene().getRoot().getUserData();
            boolean dark = isDark();
            Button btn = new Button(bt.getText());
            btn.setMinWidth(72);
            btn.setOnAction(_ -> { result = bt; stage.close(); });

            if (primary) {
                // Основная кнопка — нейтральный цвет
                btn.setStyle(btnPrimary(dark));
                btn.setOnMouseEntered(_ -> btn.setStyle(btnPrimary(dark) + "-fx-opacity:0.85;"));
                btn.setOnMouseExited(_  -> btn.setStyle(btnPrimary(dark)));
            } else {
                btn.setStyle(btnGhost(dark));
                btn.setOnMouseEntered(_ -> btn.setStyle(btnGhostHover(dark)));
                btn.setOnMouseExited(_  -> btn.setStyle(btnGhost(dark)));
            }
            btnBar.getChildren().add(btn);
            return this;
        }

        void showAndWait() { stage.showAndWait(); }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ЦВЕТА ТОЛЬКО ДЛЯ ИКОНОК
    // ════════════════════════════════════════════════════════════════════════

    private record IconColors(String bgColor, String svgColor) {
        static IconColors of(AlertType type, boolean dark) {
            return switch (type) {
                case INFO    -> new IconColors(dark ? "#2a3a4a" : "#E8ECF0", dark ? "#7090b0" : "#465261");
                case SUCCESS -> new IconColors(dark ? "#1e3a1a" : "#E8F3E0", dark ? "#4a9c2f" : "#4a7c2f");
                case WARNING -> new IconColors(dark ? "#3a3018" : "#FDF0D8", dark ? "#b88a30" : "#9a6200");
                case ERROR   -> new IconColors(dark ? "#3a1a1a" : "#FAEAEA", dark ? "#c04040" : "#b03030");
                case CONFIRM -> new IconColors(dark ? "#2a3a4a" : "#E8ECF0", dark ? "#7090b0" : "#465261");
            };
        }
    }

    private static String getTypeLabel(AlertType type, boolean dark) {
        return switch (type) {
            case INFO    -> "Информация";
            case SUCCESS -> "Успех";
            case WARNING -> "Предупреждение";
            case ERROR   -> "Ошибка";
            case CONFIRM -> "Подтверждение";
        };
    }

    // ════════════════════════════════════════════════════════════════════════
    //  КРУПНАЯ ИКОНКА 40x40 (ВАРИАНТ C)
    // ════════════════════════════════════════════════════════════════════════

    private static StackPane buildLargeIcon(AlertType type, IconColors colors) {
        SVGPath path = new SVGPath();
        path.setFill(Color.web(colors.svgColor));
        path.setContent(switch (type) {
            case INFO ->
                    "M8,0C3.6,0,0,3.6,0,8s3.6,8,8,8s8-3.6,8-8S12.4,0,8,0z" +
                            "M9,12H7V7h2V12zM9,5.5H7v-2h2V5.5z";
            case SUCCESS ->
                    "M8,0C3.6,0,0,3.6,0,8s3.6,8,8,8s8-3.6,8-8S12.4,0,8,0z" +
                            "M6.5,11.5L3,8l1.4-1.4l2.1,2.1l4.1-4.1L12,6L6.5,11.5z";
            case WARNING ->
                    "M8.9,1.4l6.6,11.8c0.4,0.7-0.1,1.6-0.9,1.6H1.4C0.6,14.8,0.1,13.9,0.5,13.2L7.1,1.4" +
                            "C7.5,0.7,8.5,0.7,8.9,1.4zM7,6v4h2V6H7zM7,11v2h2v-2H7z";
            case ERROR ->
                    "M8,0C3.6,0,0,3.6,0,8s3.6,8,8,8s8-3.6,8-8S12.4,0,8,0z" +
                            "M11.5,11.5l-1.4,1.4L8,10.8l-2.1,2.1l-1.4-1.4L6.6,9.9L4.5,7.8l1.4-1.4L8,8.5l2.1-2.1l1.4,1.4L9.4,9.9z";
            case CONFIRM ->
                    "M8,0C3.6,0,0,3.6,0,8s3.6,8,8,8s8-3.6,8-8S12.4,0,8,0z" +
                            "M8.5,12.5h-1V11h1V12.5z" +
                            "M9.8,7.8C9.3,8.3,9,8.7,9,9.8H7C7,8,7.7,7.2,8.3,6.6C8.7,6.2,9,5.9,9,5.2C9,4.5,8.6,4,8,4" +
                            "S7,4.5,7,5H5C5,3.3,6.3,2,8,2s3,1.3,3,3C11,6.2,10.4,7,9.8,7.8z";
        });

        StackPane wrap = new StackPane(path);
        wrap.setPrefSize(40, 40);
        wrap.setMinSize(40, 40);
        wrap.setStyle(
                "-fx-background-color:" + colors.bgColor + ";" +
                        "-fx-background-radius:50%;"
        );
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