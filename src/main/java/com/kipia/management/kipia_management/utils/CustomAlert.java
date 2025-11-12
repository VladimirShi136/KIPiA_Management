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

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Класс CustomAlert предоставляет методы для создания и отображения пользовательских alerts.
 *
 * @author vladimir_shi
 * @since 10.10.2025
 */
public class CustomAlert {
    private static final Logger LOGGER = Logger.getLogger(CustomAlert.class.getName());
    // Статические константы для кнопок (для consistency и сравнения)
    public static final ButtonType RETRY_BUTTON = new ButtonType("Повторить");
    public static final ButtonType CANCEL_BUTTON = new ButtonType("Отмена");
    // Простые статические методы для базовых алертов (замена стандартных Alert.alert)
    public static void showInfo(String title, String message) {
        showSimpleAlert(Alert.AlertType.INFORMATION, title, message, "info");
    }
    public static void showWarning(String title, String message) {
        showSimpleAlert(Alert.AlertType.WARNING, title, message, "warning");
    }
    public static void showError(String title, String message) {
        showSimpleAlert(Alert.AlertType.ERROR, title, message, "error");
    }
    public static void showSuccess(String title, String message) {
        showSimpleAlert(Alert.AlertType.INFORMATION, title, message, "success");
    }
    public static boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        StyleUtils.setupAlertStyle(alert, title, StyleUtils.getAlertStyleClass("confirm"));
        alert.showAndWait();
        return alert.getResult() == ButtonType.YES;
    }
    // Новый метод для ввода текста (для редактирования TEXT фигур)
    public static Optional<String> showTextInputDialog(String title, String message, String defaultValue) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        StyleUtils.setupAlertStyle(alert, title, StyleUtils.getAlertStyleClass("confirm"));
        alert.setHeaderText(message);

        TextField textField = new TextField(defaultValue != null ? defaultValue : "");
        alert.setGraphic(textField);
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            return Optional.of(textField.getText());
        } else {
            return Optional.empty();
        }
    }
    // Расширенный метод для ошибок с кнопками, expandable content и логгингом (заменяет showErrorDialog)
    public static ButtonType showAdvancedError(String title, String message, Throwable exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        StyleUtils.setupAlertStyle(alert, title, StyleUtils.getAlertStyleClass("error"));
        alert.setHeaderText("Произошла ошибка!");
        alert.setContentText(message);

        alert.getButtonTypes().setAll(ButtonType.OK, RETRY_BUTTON, CANCEL_BUTTON);

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
            LOGGER.log(Level.SEVERE, "Ошибка: " + message, exception);
        } else {
            LOGGER.log(Level.SEVERE, "Ошибка: " + message);
        }

        alert.showAndWait();
        return alert.getResult();
    }

    /**
     * Показывает окно автосохранения с предустановленными стилями.
     * @param message текст сообщения (например, "Автосохранение...")
     * @param durationSec время отображения в секундах
     */
    public static void showAutoSaveNotification(String message, double durationSec) {
        try {
            Stage notificationStage = new Stage();
            notificationStage.initStyle(StageStyle.TRANSPARENT);
            notificationStage.initModality(Modality.NONE);
            notificationStage.setAlwaysOnTop(true);
            notificationStage.setResizable(false);

            // Используем CSS класс вместо inline стилей
            Label label = new Label(message);
            label.getStyleClass().add("auto-save-notification");

            StackPane root = new StackPane(label);
            root.getStyleClass().add("auto-save-notification-container");

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);

            // Подключаем CSS файл
            scene.getStylesheets().add(Objects.requireNonNull(CustomAlert.class.getResource("/styles/light-theme.css")).toExternalForm());

            notificationStage.setScene(scene);

            // Позиционируем в правом верхнем углу
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

            // Анимация появления/исчезновения
            root.setOpacity(0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), root);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), root);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> notificationStage.close());

            notificationStage.show();
            fadeIn.play();

            PauseTransition pause = new PauseTransition(Duration.seconds(durationSec));
            pause.setOnFinished(e -> fadeOut.play());
            pause.play();

        } catch (Exception e) {
            System.err.println("Ошибка показа уведомления: " + e.getMessage());
        }
    }


    private static void showSimpleAlert(Alert.AlertType type, String title, String message, String styleType) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        StyleUtils.setupAlertStyle(alert, title, StyleUtils.getAlertStyleClass(styleType));
        alert.showAndWait();
    }
}