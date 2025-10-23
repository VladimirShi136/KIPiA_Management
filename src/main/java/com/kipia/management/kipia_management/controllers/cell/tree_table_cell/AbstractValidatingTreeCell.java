package com.kipia.management.kipia_management.controllers.cell.tree_table_cell;

import com.kipia.management.kipia_management.controllers.DevicesGroupedController;
import com.kipia.management.kipia_management.controllers.cell.ValidatingCellEditor;
import com.kipia.management.kipia_management.controllers.cell.ValidationCallback;
import javafx.scene.control.TreeTableCell;

/**
 * Абстрактный класс для валидации числовых данных в дереве-таблице приборов.
 * Теперь использует ValidatingCellEditor для делегирования общей логики.
 * @author vladimir_shi
 * @since 13.09.2025
 */
public abstract class AbstractValidatingTreeCell<T> extends TreeTableCell<DevicesGroupedController.TreeRowItem, T> implements ValidationCallback {
    private final ValidatingCellEditor editor = new ValidatingCellEditor(this);  // Композиция для общей логики

    /**
     * Запускает редактирование ячейки.
     */
    @Override
    public void startEdit() {
        super.startEdit();
        setText(null);
        setGraphic(editor.getTextField());
        editor.getTextField().setText(getItemAsString());
        editor.getTextField().selectAll();
        editor.getTextField().requestFocus();
    }

    /**
     * Отменяет редактирование ячейки.
     */
    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getItemAsString());
        setGraphic(null);
    }

    /**
     * Обновляет ячейку.
     * @param item - новое значение
     * @param empty - является ли ячейка пустой
     */
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

    /**
     * Вспомогательный метод для безопасного преобразования элемента в строку.
     * @return строку элемента или пустую строку, если элемент null
     */
    protected String getItemAsString() {
        return getItem() == null ? "" : getItem().toString();
    }

    /**
     * Реализация callback: специфичная валидация и коммит.
     * Делегируется ValidatingCellEditor в processEdit().
     * @param input строка ввода для валидации
     */
    @Override
    public abstract void validateAndCommit(String input);
}