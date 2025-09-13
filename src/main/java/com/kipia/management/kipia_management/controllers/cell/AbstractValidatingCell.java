package com.kipia.management.kipia_management.controllers.cell;

import com.kipia.management.kipia_management.models.Device;
import javafx.scene.control.Alert;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;

/**
 * Абстрактный класс для валидации числовых данных в таблице приборов.
 * @author vladimir_shi
 * @since 13.09.2025
 */

public abstract class AbstractValidatingCell<T> extends TableCell<Device, T> {

    protected TextField textField;  // Поле для ввода текста
    private boolean isShowingAlert = false;  // Показывать ли предупреждение о некорректном вводе

    /**
     * Запускает редактирование ячейки
     */
    @Override
    public void startEdit() {
        super.startEdit();
        if (textField == null) createTextField();
        setText(null);
        setGraphic(textField);
        textField.setText(getItem() == null ? "" : getItem().toString());
        textField.selectAll();
        textField.requestFocus();
    }

    /**
     * Отменяет редактирование ячейки
     */
    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getItem() == null ? "" : getItem().toString());
        setGraphic(null);
    }

    /**
     * Обновляет ячейку
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
            if (textField != null) {
                textField.setText(item == null ? "" : item.toString());
            }
            setText(null);
            setGraphic(textField);
        } else {
            setText(item == null ? "" : item.toString());
            setGraphic(null);
        }
    }

    /**
     * Создает текстовое поле
     */
    private void createTextField() {
        textField = new TextField();
        textField.setOnAction(e -> processEdit());
        textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) processEdit();
        });
    }

    // Метод вызывается при окончании редактирования (нажали Enter или ушёл фокус)
    protected void processEdit() {
        String input = textField.getText().trim();

        if (input.contains(",")) {
            showAlert("Используйте точку вместо запятой.");
            cancelEdit();
            return;
        }

        // Если пустое, разрешаем null и коммитим
        if (input.isEmpty()) {
            commitEdit(null);
            return;
        }

        // Специфичная валидация и коммит — реализуется в наследнике
        validateAndCommit(input);
    }

    // Абстрактный метод, чтобы наследник сам валидировал и вызывал commitEdit/cancelEdit
    protected abstract void validateAndCommit(String input);

    protected void showAlert(String msg) {
        if (isShowingAlert) return;
        isShowingAlert = true;
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Ошибка ввода");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
        isShowingAlert = false;
    }
}
