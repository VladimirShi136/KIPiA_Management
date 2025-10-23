package com.kipia.management.kipia_management.controllers.cell.table_cell;

import com.kipia.management.kipia_management.controllers.cell.ValidatingCellEditor;
import com.kipia.management.kipia_management.controllers.cell.ValidationCallback;
import com.kipia.management.kipia_management.models.Device;
import javafx.scene.control.TableCell;

/**
 * Абстрактный класс для валидации числовых данных в таблице приборов.
 * Теперь использует ValidatingCellEditor для делегирования общей логики.
 *
 * @author vladimir_shi
 * @since 13.09.2025
 */
public abstract class AbstractValidatingCell<T> extends TableCell<Device, T> implements ValidationCallback {
    private final ValidatingCellEditor editor = new ValidatingCellEditor(this);  // Композиция

    @Override
    public void startEdit() {
        super.startEdit();
        setText(null);
        setGraphic(editor.getTextField());
        editor.getTextField().setText(getItemAsString());
        editor.getTextField().selectAll();
        editor.getTextField().requestFocus();
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getItemAsString());
        setGraphic(null);
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
        } else if (isEditing()) {
            editor.getTextField().setText(item == null ? "" : item.toString());
            setText(null);
            setGraphic(editor.getTextField());
        } else {
            setText(item == null ? "" : item.toString());
            setGraphic(null);
        }
    }

    // Вспомогательный метод
    protected String getItemAsString() {
        return getItem() == null ? "" : getItem().toString();
    }

    // Реализация callback: специфичная валидация и коммит
    @Override
    public abstract void validateAndCommit(String input);
}