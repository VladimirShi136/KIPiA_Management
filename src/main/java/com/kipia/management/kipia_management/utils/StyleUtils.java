package com.kipia.management.kipia_management.utils;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.effect.Glow;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.Objects;

/**
 * Класс с утилитами для работы со стилями CSS
 *
 * @author vladimir_shi
 * @since 08.09.2025
 */
public class StyleUtils {
    // CSS классы для разных типов алертов
    private static final String INFO_ALERT = "info-alert";
    private static final String WARNING_ALERT = "warning-alert";
    private static final String ERROR_ALERT = "error-alert";
    private static final String CONFIRM_ALERT = "confirm-alert";
    private static final String SUCCESS_ALERT = "success-alert";

    /**
     * Метод для применения CSS классов с плавной сменой классов при наведении мыши.
     *
     * @param button          кнопка, к которой применяются стили
     * @param defaultCssClass класс по умолчанию
     * @param hoverCssClass   класс для состояния hover
     */
    public static void applyHoverAndAnimation(Button button, String defaultCssClass, String hoverCssClass) {
        button.getStyleClass().removeIf(c -> c.equals(defaultCssClass) || c.equals(hoverCssClass));
        button.getStyleClass().add(defaultCssClass);
        button.setOnMouseEntered(e -> {
            button.getStyleClass().remove(defaultCssClass);
            if (!button.getStyleClass().contains(hoverCssClass)) {
                button.getStyleClass().add(hoverCssClass);
            }
        });
        button.setOnMouseExited(e -> {
            button.getStyleClass().remove(hoverCssClass);
            if (!button.getStyleClass().contains(defaultCssClass)) {
                button.getStyleClass().add(defaultCssClass);
            }
        });
    }

    /**
     * Применение стиля и анимации для RadioButton
     *
     * @param button кнопка, к которой применяются стили
     */
    public static void applyStyleToRadioButton(RadioButton button) {
        // Начальный стиль
        button.setStyle(
                "-fx-background-color: linear-gradient(to right, #6b5ce7, #a29bfe); " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-radius: 10; " +
                        "-fx-border-radius: 10; " +
                        "-fx-padding: 5 10 5 10; " +
                        "-fx-effect: null; " +
                        "-fx-cursor: hand;"
        );

        Glow glow = new Glow(0.0);
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(300), button);
        scaleIn.setFromX(1.0);
        scaleIn.setFromY(1.0);
        scaleIn.setToX(1.2);
        scaleIn.setToY(1.2);
        scaleIn.setInterpolator(Interpolator.EASE_OUT);

        Timeline hoverIn = new Timeline();
        hoverIn.getKeyFrames().add(new KeyFrame(Duration.millis(300),
                        new KeyValue(glow.levelProperty(), 0.8, Interpolator.EASE_IN),
                        new KeyValue(button.styleProperty(),
                                "-fx-background-color: linear-gradient(to right, #ff6b6b, #4ecdc4); " +
                                        "-fx-text-fill: white; " +
                                        "-fx-font-size: 14px; " +
                                        "-fx-font-weight: bold; " +
                                        "-fx-background-radius: 10; " +
                                        "-fx-border-radius: 10; " +
                                        "-fx-padding: 5 10 5 10; " +
                                        "-fx-effect: dropshadow(gaussian, rgba(255,107,107,0.5), 10, 0, 0, 0); " +
                                        "-fx-cursor: hand;", Interpolator.EASE_IN)
                )
        );

        button.setOnMouseEntered(e -> {
            button.setEffect(glow);
            hoverIn.playFromStart();
            scaleIn.playFromStart();
        });

        button.setOnMouseExited(e -> {
            button.setEffect(null);
            hoverIn.stop();
            button.setStyle(
                    "-fx-background-color: linear-gradient(to right, #6b5ce7, #a29bfe); " +
                            "-fx-text-fill: white; " +
                            "-fx-font-size: 14px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-background-radius: 10; " +
                            "-fx-border-radius: 10; " +
                            "-fx-padding: 5 10 5 10; " +
                            "-fx-effect: null; " +
                            "-fx-cursor: hand;"
            );
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(300), button);
            scaleOut.setFromX(1.2);
            scaleOut.setFromY(1.2);
            scaleOut.setToX(1.0);
            scaleOut.setToY(1.0);
            scaleOut.setInterpolator(Interpolator.EASE_OUT);
            scaleOut.play();
        });
    }

    /**
     * Настройка стилей для Alert
     *
     * @param alert Alert для настройки
     * @param title заголовок
     * @param styleClass CSS класс для стилизации
     */
    public static void setupAlertStyle(Alert alert, String title, String styleClass) {
        alert.initStyle(StageStyle.UTILITY);
        alert.setTitle(title);

        // Добавляем CSS классы к DialogPane
        alert.getDialogPane().getStyleClass().add(styleClass);
        alert.getDialogPane().getStyleClass().add("custom-alert");

        // Подключаем CSS файл
        try {
            alert.getDialogPane().getStylesheets().add(
                    Objects.requireNonNull(StyleUtils.class.getResource("/styles/light-theme.css")).toExternalForm()
            );
        } catch (Exception e) {
            System.err.println("Не удалось загрузить CSS файл для алертов: " + e.getMessage());
        }
    }

    /**
     * Получить CSS класс для типа алерта
     *
     * @param alertType тип алерта
     * @return CSS класс
     */
    public static String getAlertStyleClass(String alertType) {
        return switch (alertType.toLowerCase()) {
            case "info" -> INFO_ALERT;
            case "warning" -> WARNING_ALERT;
            case "error" -> ERROR_ALERT;
            case "confirm" -> CONFIRM_ALERT;
            case "success" -> SUCCESS_ALERT;
            default -> INFO_ALERT;
        };
    }
}

