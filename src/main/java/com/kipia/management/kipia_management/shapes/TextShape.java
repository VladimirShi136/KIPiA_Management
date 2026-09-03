package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ClipboardManager;
import com.kipia.management.kipia_management.managers.ShapeManager;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;
import javafx.geometry.Bounds;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Реализация фигуры "Текст"
 *
 * @author vladimir_shi
 * @since 24.10.2025
 */
public class TextShape extends ShapeBase {
    private final Text text;
    private final ShapeManager shapeManager;
    private final Consumer<String> statusSetter;
    private final javafx.scene.shape.Rectangle selectionBorder;

    public TextShape(double x, double y, String content,
                     AnchorPane pane, Consumer<String> statusSetter,
                     Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        super(pane, statusSetter, onSelectCallback, shapeManager);
        this.shapeManager = shapeManager;
        this.statusSetter = statusSetter;

        String initialContent = (content != null && !content.trim().isEmpty()) ? content : "Текст";

        // СОЗДАЕМ текст
        text = new Text(initialContent);
        Color defaultFill = Color.BLACK;
        text.setFill(defaultFill);
        text.setFont(Font.font("Arial", 16));
        text.setTextOrigin(VPos.TOP); // ВАЖНО: верхняя граница = верх букв

        // ВАЖНО: разрешаем события мыши для текста
        text.setMouseTransparent(false);
        text.setPickOnBounds(true);

        // Создаем рамку выделения
        selectionBorder = new javafx.scene.shape.Rectangle();
        selectionBorder.setStroke(Color.BLUE);
        selectionBorder.setStrokeWidth(2);
        selectionBorder.setFill(Color.TRANSPARENT);
        selectionBorder.setMouseTransparent(true);
        selectionBorder.setVisible(false);

        getChildren().add(selectionBorder);
        getChildren().add(text);

        // УСТАНАВЛИВАЕМ позицию
        setPosition(x, y);

        // РАССЧИТЫВАЕМ размеры на основе текста
        calculateTextSize();

        setupTextEditHandler();
       // setupContextMenu();

        // ВАЖНО: убедимся, что базовые обработчики drag работают
        setupTextDragHandlers();
    }

    /**
     * НАСТРОЙКА обработчиков перетаскивания для текста
     */
    private void setupTextDragHandlers() {
        // Убедимся, что текстовый элемент не блокирует события
        text.setMouseTransparent(false);
        text.setPickOnBounds(true);

        // Дублируем обработчики на текстовый элемент (на всякий случай)
        text.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                // Передаем событие родительской группе
                getOnMousePressed().handle(event);
            }
        });

        text.setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) {
                getOnMouseDragged().handle(event);
            }
        });

        text.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                getOnMouseReleased().handle(event);
            }
        });
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

            // Обновляем рамку выделения
            selectionBorder.setWidth(textWidth);
            selectionBorder.setHeight(textHeight);
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

    /**
     * Получение максимального X относительно позиции фигуры
     */
    @Override
    protected double getMaxRelativeX() {
        return getCurrentWidth();
    }

    /**
     * Получение максимального Y относительно позиции фигуры
     */
    @Override
    protected double getMaxRelativeY() {
        return getCurrentHeight();
    }

    @Override
    protected String getMoveStatusMessage() {
        return "Позиция текста изменена";
    }

    @Override
    public void makeResizeHandlesVisible() {
        // НЕ показываем handles - ресайз через меню
    }

    @Override
    public void removeResizeHandles() {
        // НЕ удаляем handles - их нет
    }

    /**
     * НАСТРОЙКА контекстного меню для текста
     */
    @Override
    public void addContextMenu(Consumer<ShapeHandler> deleteAction) {
        ContextMenu contextMenu = new ContextMenu();

        // ДОБАВИТЬ пункт поворота
        MenuItem rotateItem = new MenuItem("Повернуть");
        rotateItem.setOnAction(_ -> {
            double oldAngle = rotationAngle;
            double newAngle = (oldAngle + 45) % 360;
            setRotation(newAngle);

            // Возвращаем в границы канваса после поворота
            clampToCanvasBounds(canvasBoundsWidth, canvasBoundsHeight);

            if (shapeManager != null) {
                shapeManager.registerRotation(this, oldAngle, newAngle);
                statusSetter.accept("Текст повернут на " + newAngle + " градусов");
            }
        });

        // Пункт "Копировать"
        MenuItem copyItem = new MenuItem("Копировать");
        copyItem.setOnAction(_ -> copyToClipboard());

        // Пункт "Вставить"
        MenuItem pasteItem = new MenuItem("Вставить");
        pasteItem.setOnAction(_ -> pasteFromClipboard());
        pasteItem.disableProperty().bind(ClipboardManager.hasShapeDataProperty().not());

        // Специфичные для текста пункты
        MenuItem textColorItem = new MenuItem("Изменить цвет текста");
        textColorItem.setOnAction(_ -> changeTextColor());

        MenuItem editTextItem = new MenuItem("Изменить текст");
        editTextItem.setOnAction(_ -> openTextEditDialog());

        MenuItem changeFontItem = new MenuItem("Изменить шрифт");
        changeFontItem.setOnAction(_ -> openFontDialog());

        SeparatorMenuItem separator = new SeparatorMenuItem();

        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.setOnAction(_ -> {
            if (deleteAction != null) {
                deleteAction.accept(this);
            }
        });

        contextMenu.getItems().addAll(copyItem, pasteItem, textColorItem, editTextItem, changeFontItem, rotateItem, separator, deleteItem);

        setOnContextMenuRequested(event -> {
            contextMenu.show(this, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    /**
     * Диалог изменения цвета текста
     */
    private void changeTextColor() {
        Optional<Color> result = CustomAlertDialog.showColorPickerDialog("Изменение цвета текста", strokeColor);
        result.ifPresent(color -> {
            setStrokeColor(color);
            if (statusSetter != null) {
                statusSetter.accept("Цвет текста изменен");
            }
        });
    }

    /**
     * ДИАЛОГ изменения шрифта
     */
    private void openFontDialog() {
        // СОХРАНЯЕМ СТАРЫЙ ШРИФТ ДЛЯ UNDO
        Font oldFont = text.getFont();

        Optional<Font> result = CustomAlertDialog.showFontDialog(oldFont);
        result.ifPresent(newFont -> {
            text.setFont(newFont);
            calculateTextSize();

            // РЕГИСТРИРУЕМ ИЗМЕНЕНИЕ ШРИФТА В UNDO/REDO
            if (shapeManager != null && !newFont.equals(oldFont)) {
                shapeManager.registerFontChange(this, oldFont, newFont);
            }

            statusSetter.accept("Шрифт изменен: " + newFont.getStyle() + " " + (int)newFont.getSize() + "px");
        });
    }

    @Override
    public void applyCurrentStyle() {
        text.setFill(strokeColor); // Для текста используем strokeColor как цвет текста
        // Текст обычно не имеет отдельной заливки фона

        // Добавляем белую обводку для черного текста в темной теме
        applyDarkThemeOutline(text, strokeColor);
    }

    @Override
    protected void applySelectedStyle() {
        text.setFill(Color.BLUE); // Выделение синим
        // Для выделенного текста не применяем белую обводку
        text.setEffect(null);
    }

    @Override
    protected void applyDefaultStyle() {
        applyCurrentStyle();
    }

    @Override
    public String getShapeType() {
        return "TEXT";
    }

    /**
     * Переопределяем метод для текста - поворачиваем весь Group
     */
    @Override
    public void setRotation(double angle) {
        // Используем публичный метод setRotation из ShapeBase
        super.setRotation(angle);
    }

    @Override
    public String serialize() {
        double[] pos = getPosition();
        double width = getCurrentWidth();
        double height = getCurrentHeight();
        String textContent = getText();
        Font font = text.getFont();

        // Экранируем разделители в тексте
        String escapedText = textContent.replace("|", "\\|");

        // Сохраняем шрифт
        double fontSize = font.getSize();
        String fontFamily = font.getFamily();
        String fontStyle = font.getStyle();

        // Для отладки
        LOGGER.info("Serializing Text: text='{}', fontSize={}, fontStyle='{}'",
                textContent, fontSize, fontStyle);

        // Формат: TEXT|X|Y|W|H|ROTATION|TEXT|FONT_SIZE|FONT_FAMILY|FONT_STYLE|STROKE_COLOR|FILL_COLOR
        return String.format(java.util.Locale.US, "TEXT|%.2f|%.2f|%.2f|%.2f|%.1f|%s|%.1f|%s|%s%s",
                pos[0], pos[1], width, height,
                rotationAngle,
                escapedText, fontSize, fontFamily, fontStyle,
                serializeColors());
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

    /**
     * Получить текущий шрифт
     */
    public Font getFont() {
        return text.getFont();
    }

    private void setupTextEditHandler() {
        // Двойной клик отключен - теперь текст выделяется как обычная фигура
        // Редактирование текста доступно через контекстное меню
    }

    private void openTextEditDialog() {
        Optional<String> result = CustomAlertDialog.showTextInputDialog(
                "Редактирование текста", "Введите новый текст:", getText());
        if (result.isPresent()) {
            String newText = result.get().trim();
            if (!newText.isEmpty() && !newText.equals(getText())) {
                String oldText = getText(); // сохраняем ДО изменения
                setTextSilent(newText);     // меняем без авторегистрации
                if (shapeManager != null) {
                    shapeManager.registerTextChange(this, oldText, newText);
                }
                statusSetter.accept("Текст изменен");
            }
        }
    }

    // Устанавливает текст без регистрации в undo/redo (для использования в командах)
    public void setTextSilent(String content) {
        this.text.setText(content != null ? content : "Текст");
        calculateTextSize();
    }

    /**
     * ПЕРЕОПРЕДЕЛЯЕМ метод выделения - показываем рамку
     */
    @Override
    public void highlightAsSelected() {
        selectionBorder.setVisible(true);
    }

    /**
     * ПЕРЕОПРЕДЕЛЯЕМ метод сброса выделения - скрываем рамку
     */
    @Override
    public void resetHighlight() {
        selectionBorder.setVisible(false);
    }

    /**
     * Проверка попадания точки на текст (весь ограничивающий прямоугольник)
     */
    @Override
    protected boolean containsLocalPoint(double localX, double localY) {
        // Для текста проверяем весь ограничивающий прямоугольник
        return localX >= 0 && localX <= getCurrentWidth() &&
               localY >= 0 && localY <= getCurrentHeight();
    }
}