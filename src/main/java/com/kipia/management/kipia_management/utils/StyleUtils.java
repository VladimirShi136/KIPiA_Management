package com.kipia.management.kipia_management.utils;

import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Transition;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Window;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Утилиты для работы со стилями CSS в JavaFX.
 *
 * @author vladimir_shi
 * @since 08.09.2025
 */
public class StyleUtils {

    private static boolean isDarkTheme = false;

    /**
     * Устанавливает текущую тему (light или dark)
     */
    public static void setDarkTheme(boolean dark) {
        isDarkTheme = dark;
    }

    /**
     * Проверяет, включена ли темная тема
     */
    public static boolean isDarkTheme() {
        return isDarkTheme;
    }

    /**
     * Получает путь к файлу переменных текущей темы
     */
    public static String getThemeVariablesPath() {
        return isDarkTheme ? "/styles/dark-theme-vars.css" : "/styles/light-theme-vars.css";
    }

    /**
     * Получает список всех базовых стилей для загрузки
     */
    public static List<String> getBaseStylesheets() {
        List<String> stylesheets = new ArrayList<>();
        stylesheets.add(getThemeVariablesPath());
        stylesheets.add("/styles/common.css");
        stylesheets.add("/styles/main.css");
        return stylesheets;
    }

    /**
     * Получает стили для конкретного экрана
     */
    public static String getScreenStylesheet(String screenName) {
        return switch (screenName) {
            case "devices" -> "/styles/devices.css";
            case "settings" -> "/styles/settings.css";
            case "add-device" -> "/styles/add-device.css";
            case "conflict-dialog" -> "/styles/conflict-dialog.css";
            case "help-dialog" -> "/styles/help-dialog.css";
            case "schemes" -> "/styles/schemes.css";
            case "reports" -> "/styles/reports.css";
            case "photo-gallery" -> "/styles/photo-gallery.css";
            default -> null;
        };
    }

    /**
     * Устанавливает текущую тему для использования в алертах и диалогах
     * @deprecated Используйте setDarkTheme(boolean) вместо этого
     */
    @Deprecated
    public static void setCurrentTheme(String themePath) {
        isDarkTheme = themePath.contains("dark");
    }

//    /**
//     * Получает путь к текущей теме
//     * @deprecated Используйте getThemeVariablesPath() вместо этого
//     */
//    @Deprecated
//    public static String getCurrentTheme() {
//        return isDarkTheme ? "/styles/dark-theme-old.css" : "/styles/light-theme-old.css";
//    }

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

    /**
     * Настройка анимации стрелки комбобокса при открытии/закрытии
     * @param combo комбобокс
     * @param animate включить ли плавную анимацию (по умолчанию true)
     */
    public static void setupComboBoxArrowAnimation(ComboBox<?> combo, boolean animate) {
        if (combo == null) return;

        combo.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (!animate) {
                // Без анимации - просто меняем угол
                Node arrow = combo.lookup(".arrow");
                if (arrow != null) {
                    arrow.setRotate(isShowing ? 180 : 0);
                }
            } else {
                // С плавной анимацией
                Node arrow = combo.lookup(".arrow");
                if (arrow != null) {
                    RotateTransition rotate = new RotateTransition(Duration.millis(220), arrow);
                    rotate.setToAngle(isShowing ? 180 : 0);
                    rotate.setInterpolator(javafx.animation.Interpolator.EASE_BOTH);
                    rotate.play();
                }
            }
        });
    }

    /**
     * Настройка анимации стрелки комбобокса при открытии/закрытии (с анимацией по умолчанию)
     */
    public static void setupComboBoxArrowAnimation(ComboBox<?> combo) {
        setupComboBoxArrowAnimation(combo, true);
    }

    /**
     * Настройка плавной анимации открытия popup комбобокса
     */
    public static void setupComboBoxPopupAnimation(ComboBox<?> combo) {
        if (combo == null) return;

        combo.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                // Даём JavaFX кадр на то, чтобы попап реально появился в Window.getWindows()
                Platform.runLater(() -> {
                    Window popupWindow = findOpenPopupWindow();
                    if (popupWindow == null) return;

                    double startY = popupWindow.getY() - 14;
                    double targetY = popupWindow.getY();
                    popupWindow.setOpacity(0);
                    popupWindow.setY(startY);

                    // Window.yProperty() доступен только на чтение (есть только setY()),
                    // поэтому анимируем вручную через interpolate(), а не через KeyValue/Timeline.
                    Transition reveal = new Transition() {
                        {
                            setCycleDuration(Duration.millis(240));
                            setInterpolator(Interpolator.EASE_OUT);
                        }

                        @Override
                        protected void interpolate(double frac) {
                            popupWindow.setOpacity(frac);
                            popupWindow.setY(startY + (targetY - startY) * frac);
                        }
                    };
                    reveal.play();
                });
            }
        });
    }

    /**
     * Поиск открытого popup окна комбобокса
     */
    private static Window findOpenPopupWindow() {
        for (Window window : Window.getWindows()) {
            if (window instanceof javafx.stage.PopupWindow) {
                return window;
            }
        }
        return null;
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