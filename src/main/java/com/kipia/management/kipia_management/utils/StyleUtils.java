package com.kipia.management.kipia_management.utils;

import com.kipia.management.kipia_management.models.Device;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.Objects;

/**
 * Утилиты для работы со стилями CSS в JavaFX.
 * ВАЖНО: Все hover-эффекты теперь работают через CSS-псевдокласс :hover,
 * поэтому ручное переключение классов *-hover больше не требуется.
 *
 * @author vladimir_shi
 * @since 08.09.2025
 */
public class StyleUtils {

    private static String currentThemePath = "/styles/light-theme.css";

    // CSS классы для алертов
    private static final String INFO_ALERT = "info-alert";
    private static final String WARNING_ALERT = "warning-alert";
    private static final String ERROR_ALERT = "error-alert";
    private static final String CONFIRM_ALERT = "confirm-alert";
    private static final String SUCCESS_ALERT = "success-alert";

    private static final String STYLED_TABLE_VIEW = "styled-table-view";
    private static final String STYLED_DIALOG = "styled-dialog";

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
     * Просто добавляет CSS-класс кнопке.
     * Все hover-эффекты теперь в CSS (:hover)
     *
     * @param button кнопка
     * @param cssClass CSS класс
     */
    public static void addStyleClass(Button button, String cssClass) {
        if (!button.getStyleClass().contains(cssClass)) {
            button.getStyleClass().add(cssClass);
        }
    }

    /**
     * Добавляет анимацию масштабирования при наведении (опционально).
     * Это чисто визуальный эффект, не связанный со сменой классов.
     *
     * @param button кнопка
     * @param scaleFactor коэффициент увеличения (1.05 = +5%)
     */
    public static void addScaleHoverAnimation(Button button, double scaleFactor) {
        button.setOnMouseEntered(_ -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), button);
            st.setToX(scaleFactor);
            st.setToY(scaleFactor);
            st.setAutoReverse(true);
            st.play();
        });
        button.setOnMouseExited(_ -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), button);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
    }

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

    // ============================================================
    // ALERT STYLES
    // ============================================================

    public static void setupAlertStyle(Alert alert, String title, String styleClass) {
        alert.initStyle(StageStyle.UTILITY);
        alert.setTitle(title);
        alert.getDialogPane().getStyleClass().add(styleClass);
        alert.getDialogPane().getStyleClass().add("custom-alert");

        try {
            alert.getDialogPane().getStylesheets().add(
                    Objects.requireNonNull(StyleUtils.class.getResource(currentThemePath)).toExternalForm()
            );
        } catch (Exception e) {
            System.err.println("Не удалось загрузить CSS: " + e.getMessage());
        }
    }

    public static String getAlertStyleClass(String alertType) {
        return switch (alertType.toLowerCase()) {
            case "warning" -> WARNING_ALERT;
            case "error" -> ERROR_ALERT;
            case "confirm" -> CONFIRM_ALERT;
            case "success" -> SUCCESS_ALERT;
            default -> INFO_ALERT;
        };
    }

    // ============================================================
    // DIALOG UTILITIES
    // ============================================================

    public static Dialog<Device> createDeviceSelectionDialog(String title, String header) {
        Dialog<Device> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        if (okButton != null) {
            okButton.setText("Выбрать");
        }

        setupDialogStyles(dialog);
        return dialog;
    }

    public static void setupDialogStyles(Dialog<?> dialog) {
        // Применяем единый стиль через DialogStyler (без системного titlebar и иконки)
        com.kipia.management.kipia_management.utils.DialogStyler.applyStyle(dialog);

        dialog.getDialogPane().setMinWidth(400);
        dialog.getDialogPane().setMinHeight(300);
    }

    public static <T> void setupTableViewBehavior(TableView<T> tableView, Dialog<T> dialog) {
        tableView.setRowFactory(_ -> {
            TableRow<T> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    dialog.setResult(row.getItem());
                }
            });
            return row;
        });

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        if (okButton != null) {
            okButton.setDisable(true);
            tableView.getSelectionModel().selectedItemProperty().addListener(
                    (_, _, newSelection) -> okButton.setDisable(newSelection == null));
        }
    }

    public static VBox createDialogContent(String title, String infoText, TableView<?> tableView) {
        VBox content = new VBox(10);
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(20));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-title");

        Label infoLabel = new Label(infoText);
        infoLabel.getStyleClass().add("dialog-info");

        content.getChildren().addAll(titleLabel, infoLabel, tableView);
        return content;
    }

    public static <T> TableView<T> createStyledTableView() {
        TableView<T> tableView = new TableView<>();
        applyTableViewStyles(tableView);
        return tableView;
    }

    public static void applyTableViewStyles(TableView<?> tableView) {
        tableView.getStyleClass().add(STYLED_TABLE_VIEW);
        tableView.setPrefHeight(400);
        tableView.setPrefWidth(800);

        try {
            tableView.getStylesheets().add(
                    Objects.requireNonNull(StyleUtils.class.getResource(currentThemePath)).toExternalForm()
            );
        } catch (Exception e) {
            System.err.println("Не удалось загрузить CSS: " + e.getMessage());
        }
    }

    public static <T, S> TableColumn<T, S> createStyledColumn(String title, String property, double width) {
        TableColumn<T, S> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }
}