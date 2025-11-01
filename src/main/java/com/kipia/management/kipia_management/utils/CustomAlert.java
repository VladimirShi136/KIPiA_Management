package com.kipia.management.kipia_management.utils;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

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

    private static void showSimpleAlert(Alert.AlertType type, String title, String message, String styleType) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        StyleUtils.setupAlertStyle(alert, title, StyleUtils.getAlertStyleClass(styleType));
        alert.showAndWait();
    }
}