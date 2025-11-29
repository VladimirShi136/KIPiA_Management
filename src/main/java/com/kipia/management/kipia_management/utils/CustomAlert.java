package com.kipia.management.kipia_management.utils;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.*;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.Optional;

/**
 * Класс CustomAlert предоставляет методы для создания и отображения пользовательских alerts на русском языке.
 *
 * @author vladimir_shi
 * @since 10.10.2025
 */
public class CustomAlert {
    private static final Logger LOGGER = LogManager.getLogger(CustomAlert.class);

    // Константы для русских кнопок
    public static final ButtonType RETRY_BUTTON = new ButtonType("Повторить", ButtonBar.ButtonData.APPLY);
    public static final ButtonType CANCEL_BUTTON = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
    private static final ButtonType OK_BUTTON = new ButtonType("ОК", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType YES_BUTTON = new ButtonType("Да", ButtonBar.ButtonData.YES);
    private static final ButtonType NO_BUTTON = new ButtonType("Нет", ButtonBar.ButtonData.NO);

    // Базовые методы с русифицированными заголовками и текстом

    public static void showInfo(String title, String message) {
        showSimpleAlert(
                Alert.AlertType.INFORMATION,
                title,
                message,
                "info",
                "Информация"  // Русский заголовок по умолчанию
        );
    }

    public static void showWarning(String title, String message) {
        showSimpleAlert(
                Alert.AlertType.WARNING,
                title,
                message,
                "warning",
                "Предупреждение"
        );
    }

    public static void showError(String title, String message) {
        showSimpleAlert(
                Alert.AlertType.ERROR,
                title,
                message,
                "error",
                "Ошибка"
        );
    }

    public static void showSuccess(String title, String message) {
        showSimpleAlert(
                Alert.AlertType.INFORMATION,
                title,
                message,
                "success",
                "Успех"
        );
    }

    // Подтверждение (Да/Нет)
    public static boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText("Подтверждение");  // Русский заголовок вверху окна
        alert.setContentText(message);

        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(YES_BUTTON, NO_BUTTON);

        StyleUtils.setupAlertStyle(alert, title, StyleUtils.getAlertStyleClass("confirm"));
        alert.showAndWait();
        return alert.getResult() == YES_BUTTON;
    }

    // Ввод текста
    public static Optional<String> showTextInputDialog(String title, String message, String defaultValue) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        StyleUtils.setupAlertStyle(alert, title, StyleUtils.getAlertStyleClass("confirm"));
        alert.setTitle(title);
        alert.setHeaderText(message);

        TextField textField = new TextField(defaultValue != null ? defaultValue : "");
        alert.setGraphic(textField);

        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(OK_BUTTON, CANCEL_BUTTON);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == OK_BUTTON) {
            return Optional.of(textField.getText());
        } else {
            return Optional.empty();
        }
    }

    // Расширенный предупреждение об ошибке
    public static ButtonType showAdvancedError(String title, String message, Throwable exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        StyleUtils.setupAlertStyle(alert, title, StyleUtils.getAlertStyleClass("error"));
        alert.setTitle(title);
        alert.setHeaderText("Произошла ошибка!");
        alert.setContentText(message);

        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(OK_BUTTON, RETRY_BUTTON, CANCEL_BUTTON);

        if (exception != null) {
            TextArea textArea = new TextArea(exception.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);

            GridPane gridPane = new GridPane();
            gridPane.setMaxWidth(Double.MAX_VALUE);
            gridPane.add(textArea, 0, 0);
            alert.getDialogPane().setExpandableContent(gridPane);
            alert.setResizable(true);
        }

        if (exception != null) {
            LOGGER.error("Ошибка: {}", message, exception);
        } else {
            LOGGER.error("Ошибка: {}", message);
        }

        alert.showAndWait();
        return alert.getResult();
    }

    // Уведомление об автосохранении
    public static void showAutoSaveNotification(String message, double durationSec) {
        try {
            Stage notificationStage = new Stage();
            notificationStage.initStyle(StageStyle.TRANSPARENT);
            notificationStage.initModality(Modality.NONE);
            notificationStage.setAlwaysOnTop(true);
            notificationStage.setResizable(false);

            Label label = new Label(message);
            label.getStyleClass().add("auto-save-notification");

            StackPane root = new StackPane(label);
            root.getStyleClass().add("auto-save-notification-container");

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);

            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            CustomAlert.class.getResource("/styles/light-theme.css")
                    ).toExternalForm()
            );

            notificationStage.setScene(scene);

            Window mainWindow = Stage.getWindows().stream()
                    .filter(Window::isShowing)
                    .findFirst()
                    .orElse(null);

            if (mainWindow != null) {
                double offsetX = 10;
                double offsetY = 36;
                notificationStage.setX(mainWindow.getX() + mainWindow.getWidth() - 250 - offsetX);
                notificationStage.setY(mainWindow.getY() + offsetY);
            }

            root.setOpacity(0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), root);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), root);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(_ -> notificationStage.close());

            notificationStage.show();
            fadeIn.play();

            PauseTransition pause = new PauseTransition(Duration.seconds(durationSec));
            pause.setOnFinished(_ -> fadeOut.play());
            pause.play();

        } catch (Exception e) {
            System.err.println("Ошибка показа уведомления: " + e.getMessage());
        }
    }

    // Вспомогательный метод для простых алерт-окон
    private static void showSimpleAlert(
            Alert.AlertType type,
            String title,
            String message,
            String styleType,
            String defaultHeader
    ) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(defaultHeader);  // Русский заголовок вверху окна
        alert.setContentText(message);
        alert.getButtonTypes().clear();
        alert.getButtonTypes().add(OK_BUTTON);

        StyleUtils.setupAlertStyle(alert, title, StyleUtils.getAlertStyleClass(styleType));
        alert.showAndWait();
    }
}
