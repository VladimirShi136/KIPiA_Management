package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import com.kipia.management.kipia_management.utils.CustomAlert;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.Optional;
import java.util.function.Consumer;


/**
 * @author vladimir_shi
 * @since 24.10.2025
 */

public class TextShape extends ShapeBase {
    private final Text text;
    private final Color defaultFill = Color.BLACK;
    private final Color selectedFill = Color.DODGERBLUE;
    private final ShapeManager shapeManager;
    private final Consumer<String> statusSetter;

    public TextShape(double x, double y, String content,
                     AnchorPane pane, Consumer<String> statusSetter,
                     Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        super(pane, statusSetter, onSelectCallback, shapeManager);
        this.shapeManager = shapeManager;
        this.statusSetter = statusSetter;

        String initialContent = (content != null && !content.trim().isEmpty()) ? content : "Текст";

        // СОЗДАЕМ текст
        text = new Text(initialContent);
        text.setFill(defaultFill);
        text.setFont(Font.font("Arial", 16));
        text.setTextOrigin(VPos.TOP); // ВАЖНО: верхняя граница = верх букв

        getChildren().add(text);

        // УСТАНАВЛИВАЕМ позицию
        setPosition(x, y);

        // РАССЧИТЫВАЕМ размеры на основе текста
        calculateTextSize();

        setupTextEditHandler();
        setupContextMenu();
    }

    /**
     * РАССЧИТЫВАЕТ размеры текста автоматически
     */
    public void calculateTextSize() {
        javafx.application.Platform.runLater(() -> {
            text.applyCss();

            Bounds textBounds = text.getBoundsInLocal();

            // УСТАНАВЛИВАЕМ реальные размеры текста + небольшие отступы
            double textWidth = textBounds.getWidth() + 4;  // +2px с каждой стороны
            double textHeight = textBounds.getHeight() + 4; // +2px сверху и снизу

            setCurrentDimensions(textWidth, textHeight);

            System.out.println("Text calculated size: " + textWidth + "x" + textHeight);
        });
    }

    @Override
    protected void resizeShape(double newWidth, double newHeight) {
        // ОТКЛЮЧАЕМ автоматический ресайз - размер определяется текстом
        calculateTextSize();
    }

    /**
     * ОТКЛЮЧАЕМ создание handles для ресайза
     */
    @Override
    public void createResizeHandles() {
        // НЕ создаем handles - ресайз через меню
    }

    @Override
    public void makeResizeHandlesVisible() {
        // НЕ показываем handles
    }

    @Override
    public void removeResizeHandles() {
        // НЕ удаляем handles - их нет
    }

    /**
     * НАСТРОЙКА контекстного меню для текста
     */
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        // Пункт "Изменить текст"
        MenuItem editTextItem = new MenuItem("Изменить текст");
        editTextItem.setOnAction(e -> openTextEditDialog());

        // Пункт "Изменить шрифт"
        MenuItem changeFontItem = new MenuItem("Изменить шрифт");
        changeFontItem.setOnAction(e -> openFontDialog());

        // Пункт "Удалить"
        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.setOnAction(e -> {
            if (shapeManager != null) {
                shapeManager.removeShape(this);
            }
        });

        contextMenu.getItems().addAll(editTextItem, changeFontItem, new SeparatorMenuItem(), deleteItem);

        // ПРИВЯЗЫВАЕМ меню к фигуре
        setOnContextMenuRequested(event -> {
            contextMenu.show(this, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    /**
     * ДИАЛОГ изменения шрифта
     */
    private void openFontDialog() {
        // СОЗДАЕМ диалог для выбора шрифта и размера
        Dialog<Font> dialog = new Dialog<>();
        dialog.setTitle("Изменение шрифта");
        dialog.setHeaderText("Выберите параметры шрифта");

        // КНОПКИ
        ButtonType applyButton = new ButtonType("Применить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButton, ButtonType.CANCEL);

        // СЕТКА с настройками
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // ВЫБОР размера шрифта
        TextField fontSizeField = new TextField();
        fontSizeField.setText(String.valueOf((int)text.getFont().getSize()));
        fontSizeField.setPromptText("Размер шрифта");

        // ВЫБОР жирности
        CheckBox boldCheckBox = new CheckBox("Жирный");

        // ВЫБОР начертания
        CheckBox italicCheckBox = new CheckBox("Курсив");

        grid.add(new Label("Размер:"), 0, 0);
        grid.add(fontSizeField, 1, 0);
        grid.add(boldCheckBox, 0, 1);
        grid.add(italicCheckBox, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // ОБРАБОТКА результата
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == applyButton) {
                try {
                    int fontSize = Integer.parseInt(fontSizeField.getText());
                    fontSize = Math.max(8, Math.min(72, fontSize)); // Ограничение 8-72px

                    // СОЗДАЕМ новый шрифт
                    String fontFamily = "Arial";
                    FontWeight weight = boldCheckBox.isSelected() ? FontWeight.BOLD : FontWeight.NORMAL;
                    FontPosture posture = italicCheckBox.isSelected() ? FontPosture.ITALIC : FontPosture.REGULAR;

                    return Font.font(fontFamily, weight, posture, fontSize);
                } catch (NumberFormatException e) {
                    CustomAlert.showError("Ошибка", "Введите корректный размер шрифта");
                }
            }
            return null;
        });

        Optional<Font> result = dialog.showAndWait();
        result.ifPresent(newFont -> {
            text.setFont(newFont);
            calculateTextSize(); // ПЕРЕСЧИТЫВАЕМ размеры
            statusSetter.accept("Шрифт изменен");
        });
    }

    @Override
    protected void applySelectedStyle() {
        text.setFill(selectedFill);
        // Можете добавить подсветку фона при выделении
        // setStyle("-fx-background-color: rgba(30,144,255,0.1);");
    }

    @Override
    protected void applyDefaultStyle() {
        text.setFill(defaultFill);
        // setStyle("-fx-background-color: transparent;");
    }

    @Override
    protected String getShapeType() {
        return "TEXT";
    }

    @Override
    public String serialize() {
        double[] pos = getPosition();
        double width = getCurrentWidth();
        double height = getCurrentHeight();
        String textContent = getText();
        Font font = text.getFont();

        // Сохраняем размер шрифта и стиль
        double fontSize = font.getSize();
        String fontFamily = font.getFamily();
        String fontStyle = font.getStyle();

        // Экранируем разделители в тексте
        String escapedText = textContent.replace("|", "\\|");

        return String.format(java.util.Locale.US, "TEXT|%.2f|%.2f|%.2f|%.2f|%s|%.1f|%s|%s",
                pos[0], pos[1], width, height,
                escapedText, fontSize, fontFamily, fontStyle);
    }

    /**
     * Публичный метод для установки шрифта (для десериализации)
     */
    public void setFont(Font font) {
        if (text != null && font != null) {
            text.setFont(font);
        }
    }

    public void setText(String content) {
        text.setText(content != null ? content : "Текст");
        calculateTextSize();
    }

    public String getText() {
        return text.getText();
    }

    private void setupTextEditHandler() {
        setOnMouseClicked(event -> {
            if (shapeManager != null && shapeManager.isSelectToolActive() && event.getClickCount() == 2) {
                event.consume();
                openTextEditDialog();
            }
        });
    }

    private void openTextEditDialog() {
        Optional<String> result = CustomAlert.showTextInputDialog("Редактирование текста", "Введите новый текст:", getText());
        if (result.isPresent()) {
            String newText = result.get().trim();
            setText(newText);
            statusSetter.accept("Текст изменен");
        }
    }
}