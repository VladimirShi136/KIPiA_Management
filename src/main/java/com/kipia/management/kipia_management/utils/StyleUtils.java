package com.kipia.management.kipia_management.utils;

import javafx.animation.ScaleTransition;
import javafx.scene.control.*;
import javafx.util.Duration;

/**
 * Утилиты для работы со стилями CSS в JavaFX.
 *
 * @author vladimir_shi
 * @since 08.09.2025
 */
public class StyleUtils {

    private static String currentThemePath = "/styles/light-theme.css";

    /**
     * Устанавливает текущую тему для использования в алертах и диалогах
     */
    public static void setCurrentTheme(String themePath) {
        currentThemePath = themePath;
    }

    /**
     * Получает путь к текущей теме
     */
    public static String getCurrentTheme() {
        return currentThemePath;
    }

    // ============================================================
    // УПРОЩЁННЫЕ МЕТОДЫ (без ручного переключения hover-классов)
    // ============================================================

    /**
     * Установка активного состояния для кнопки навигации.
     * Работает через добавление/удаление *-active класса.
     */
    public static void setNavigationButtonActive(Button button, boolean isActive,
                                                 String defaultCssClass,
                                                 String hoverCssClass,
                                                 String activeCssClass) {
        if (isActive) {
            button.getStyleClass().remove(defaultCssClass);
            button.getStyleClass().remove(hoverCssClass);
            if (!button.getStyleClass().contains(activeCssClass)) {
                button.getStyleClass().add(activeCssClass);
            }
        } else {
            button.getStyleClass().remove(activeCssClass);
            if (!button.getStyleClass().contains(defaultCssClass)) {
                button.getStyleClass().add(defaultCssClass);
            }
        }
    }

    /**
     * Установка активного состояния для кнопки инструмента.
     */
    public static void setToolButtonActive(Button button, boolean isActive, String activeCssClass) {
        if (isActive) {
            // НЕ удаляем базовый класс tool-button!
            button.getStyleClass().remove("tool-button-hover");
            if (!button.getStyleClass().contains(activeCssClass)) {
                button.getStyleClass().add(activeCssClass);
            }
        } else {
            button.getStyleClass().remove(activeCssClass);
            if (!button.getStyleClass().contains("tool-button")) {
                button.getStyleClass().add("tool-button");
            }
        }
    }

    /**
     * Упрощённая инициализация кнопки инструмента.
     */
    public static void setupShapeToolButton(Button button) {
        if (!button.getStyleClass().contains("tool-button")) {
            button.getStyleClass().add("tool-button");
        }
    }

    // ============================================================
    // СТИЛИ ДЛЯ RADIOBUTTON (оставлено как есть)
    // ============================================================

    public static void applyStyleToRadioButton(RadioButton button) {
        button.setStyle(
                "-fx-background-color: linear-gradient(to right, #6b5ce7, #a29bfe); " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-radius: 10; " +
                        "-fx-border-radius: 10; " +
                        "-fx-padding: 5 10 5 10; " +
                        "-fx-cursor: hand;"
        );

        // Анимация через ScaleTransition (не через смену классов)
        button.setOnMouseEntered(_ -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), button);
            st.setToX(1.1);
            st.setToY(1.1);
            st.play();
        });
        button.setOnMouseExited(_ -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), button);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
    }
}