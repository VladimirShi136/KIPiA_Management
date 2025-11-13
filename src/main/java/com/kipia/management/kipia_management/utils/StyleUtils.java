package com.kipia.management.kipia_management.utils;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
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
    // Путь к файлу со стилями css
    private static final String CSS_PATH = "/styles/light-theme.css";

    // CSS классы для разных типов алертов
    private static final String INFO_ALERT = "info-alert";
    private static final String WARNING_ALERT = "warning-alert";
    private static final String ERROR_ALERT = "error-alert";
    private static final String CONFIRM_ALERT = "confirm-alert";
    private static final String SUCCESS_ALERT = "success-alert";

    // CSS классы для компонентов
    private static final String STYLED_TABLE_VIEW = "styled-table-view";
    private static final String STYLED_DIALOG = "styled-dialog";
    private static final String DIALOG_CONTENT = "dialog-content";
    private static final String DIALOG_TITLE = "dialog-title";
    private static final String DIALOG_INFO = "dialog-info";

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
                    Objects.requireNonNull(StyleUtils.class.getResource(CSS_PATH)).toExternalForm()
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

    /**
     * Специальный метод для кнопок инструментов фигур с активным состоянием
     *
     * @param button кнопка инструмента
     * @param defaultCssClass класс по умолчанию
     * @param hoverCssClass класс при наведении
     * @param activeCssClass класс активного состояния
     */
    public static void applyToolButtonStyles(Button button, String defaultCssClass, String hoverCssClass, String activeCssClass) {
        // Инициализация - устанавливаем обычный стиль
        button.getStyleClass().removeIf(c -> c.equals(defaultCssClass) || c.equals(hoverCssClass) || c.equals(activeCssClass));
        button.getStyleClass().add(defaultCssClass);

        // Обработчик наведения мыши
        button.setOnMouseEntered(e -> {
            // Не применяем hover, если кнопка уже активна
            if (!button.getStyleClass().contains(activeCssClass)) {
                button.getStyleClass().remove(defaultCssClass);
                if (!button.getStyleClass().contains(hoverCssClass)) {
                    button.getStyleClass().add(hoverCssClass);
                }
            }
        });

        // Обработчик ухода мыши
        button.setOnMouseExited(e -> {
            // Не меняем обратно, если кнопка активна
            if (!button.getStyleClass().contains(activeCssClass)) {
                button.getStyleClass().remove(hoverCssClass);
                if (!button.getStyleClass().contains(defaultCssClass)) {
                    button.getStyleClass().add(defaultCssClass);
                }
            }
        });
    }

    /**
     * Устанавливает активное состояние для кнопки инструмента
     *
     * @param button кнопка инструмента
     * @param isActive флаг активности
     * @param activeCssClass CSS класс активного состояния
     */
    public static void setToolButtonActive(Button button, boolean isActive, String activeCssClass) {
        if (isActive) {
            // Активируем кнопку
            button.getStyleClass().removeIf(cls -> cls.equals("tool-button") || cls.equals("tool-button-hover"));
            if (!button.getStyleClass().contains(activeCssClass)) {
                button.getStyleClass().add(activeCssClass);
            }

            // Легкая анимация активации
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(150), button);
            scaleIn.setFromX(1.0);
            scaleIn.setFromY(1.0);
            scaleIn.setToX(1.03);
            scaleIn.setToY(1.03);
            scaleIn.setInterpolator(Interpolator.EASE_OUT);
            scaleIn.play();

        } else {
            // Деактивируем кнопку
            button.getStyleClass().remove(activeCssClass);
            if (!button.getStyleClass().contains("tool-button")) {
                button.getStyleClass().add("tool-button");
            }

            // Анимация деактивации
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(150), button);
            scaleOut.setFromX(1.03);
            scaleOut.setFromY(1.03);
            scaleOut.setToX(1.0);
            scaleOut.setToY(1.0);
            scaleOut.setInterpolator(Interpolator.EASE_OUT);
            scaleOut.play();
        }
    }

    /**
     * Упрощенный метод для кнопок инструментов (использует стандартные классы)
     */
    public static void setupShapeToolButton(Button button) {
        applyToolButtonStyles(button, "tool-button", "tool-button-hover", "tool-button-active");
    }

    // ============================================================
    // НОВЫЕ МЕТОДЫ ДЛЯ ТАБЛИЦ И ДИАЛОГОВ
    // ============================================================

    /**
     * Создание стилизованного TableView
     */
    public static <T> TableView<T> createStyledTableView() {
        TableView<T> tableView = new TableView<>();
        applyTableViewStyles(tableView);
        return tableView;
    }

    /**
     * Применение стилей к TableView
     */
    public static void applyTableViewStyles(TableView<?> tableView) {
        tableView.getStyleClass().add(STYLED_TABLE_VIEW);
        tableView.setPrefHeight(400);
        tableView.setPrefWidth(800);

        // Подключаем CSS
        try {
            tableView.getStylesheets().add(
                    Objects.requireNonNull(StyleUtils.class.getResource(CSS_PATH)).toExternalForm()
            );
        } catch (Exception e) {
            System.err.println("Не удалось загрузить CSS для TableView: " + e.getMessage());
        }
    }

    /**
     * Создание стилизованной колонки для TableView
     */
    public static <T, S> TableColumn<T, S> createStyledColumn(String title, String property, double width) {
        TableColumn<T, S> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }

    /**
     * Настройка стилей диалога
     */
    public static void setupDialogStyles(Dialog<?> dialog) {
        // Устанавливаем иконку окна
        Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
        try {
            Image appIcon = new Image(Objects.requireNonNull(StyleUtils.class.getResourceAsStream("/images/app-icon.png")));
            dialogStage.getIcons().add(appIcon);
        } catch (Exception e) {
            System.err.println("Не удалось загрузить иконку для диалога: " + e.getMessage());
        }

        // Применяем CSS стили
        dialog.getDialogPane().getStyleClass().add(STYLED_DIALOG);
        try {
            dialog.getDialogPane().getStylesheets().addAll(
                    Objects.requireNonNull(StyleUtils.class.getResource(CSS_PATH)).toExternalForm(),
                    Objects.requireNonNull(StyleUtils.class.getResource(CSS_PATH)).toExternalForm()
            );
        } catch (Exception e) {
            System.err.println("Не удалось загрузить CSS для диалога: " + e.getMessage());
        }
    }

    /**
     * Создание контента для диалога
     */
    public static VBox createDialogContent(String title, String infoText, TableView<?> tableView) {
        VBox content = new VBox(10);
        content.getStyleClass().add(DIALOG_CONTENT);
        content.setPadding(new Insets(20));

        // Заголовок
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add(DIALOG_TITLE);

        // Информационная строка
        Label infoLabel = new Label(infoText);
        infoLabel.getStyleClass().add(DIALOG_INFO);

        content.getChildren().addAll(titleLabel, infoLabel, tableView);
        return content;
    }

    /**
     * Настройка поведения TableView (двойной клик и hover)
     */
    public static <T> void setupTableViewBehavior(TableView<T> tableView, Dialog<T> dialog) {
        tableView.setRowFactory(tv -> {
            TableRow<T> row = new TableRow<>();

            // Двойной клик для выбора
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    dialog.setResult(row.getItem());
                    dialog.close();
                }
            });

            return row;
        });
    }

    /**
     * Создание готового диалога выбора прибора
     */
    public static <T> Dialog<T> createDeviceSelectionDialog(String title, String header, ObservableList<T> items) {
        Dialog<T> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);

        // Кнопки
        ButtonType selectButton = new ButtonType("Выбрать", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectButton, ButtonType.CANCEL);

        // Настройка стилей
        setupDialogStyles(dialog);

        return dialog;
    }
}


