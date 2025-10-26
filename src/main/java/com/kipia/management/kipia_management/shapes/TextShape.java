package com.kipia.management.kipia_management.shapes;

import com.kipia.management.kipia_management.managers.ShapeManager;
import com.kipia.management.kipia_management.utils.CustomAlert;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import java.util.Optional;
import java.util.function.Consumer;


/**
 * @author vladimir_shi
 * @since 24.10.2025
 */

public class TextShape extends ShapeBase {
    private Text text;
    private Color defaultFill = Color.BLACK;
    private Color selectedFill = Color.DODGERBLUE;
    private ShapeManager shapeManager;
    private final Consumer<String> statusSetter;

    public TextShape(double x, double y, String content,
                     AnchorPane pane, Consumer<String> statusSetter,
                     Consumer<ShapeHandler> onSelectCallback, ShapeManager shapeManager) {
        super(pane, statusSetter, onSelectCallback, shapeManager);
        this.shapeManager = shapeManager;
        this.statusSetter = statusSetter;
        String initialContent = (content != null && !content.trim().isEmpty()) ? content : "Новый текст";
        // Создаём text относительно group (layout=0,0), group позиционируется в setPosition
        text = new Text(0, 0, initialContent);  // Фикс: позиция текста внутри group = 0,0
        text.setFill(defaultFill);
        text.setFont(Font.font("Arial", 18));
        getChildren().add(text);
        setPosition(x, y);  // Теперь group будет на (x,y), текст на (0,0) внутри
        setupTextEditHandler();
        System.out.println("Created TextShape: '" + initialContent + "' at (" + x + ", " + y + ")");
    }

    @Override
    protected void resizeShape(double newWidth, double newHeight) {
        // Меняем font size по новой height (опционально: можно ignor newWidth)
        double fontSize = Math.max(10, Math.min(50, newHeight / 2));  // Шкала по height, min 10, max 50
        text.setFont(Font.font("Arial", fontSize));
        setCurrentDimensions(newWidth, newHeight);  // Синхронизируй stored dimensions
    }

    @Override
    protected void applySelectedStyle() {
        text.setFill(selectedFill);  // Изменяем цвет текста для выделения
    }

    @Override
    protected void applyDefaultStyle() {
        text.setFill(defaultFill);  // Возвращаем чёрный
    }

    @Override
    protected String getShapeType() {
        return "TEXT";
    }

    public void setText(String content) {
        text.setText(content);
        if (content == null || content.trim().isEmpty()) {
            text.setText("Новый текст");  // Fallback если пустой
        }
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