package com.kipia.management.kipia_management.controllers.cell.tree_table_cell;

import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeTableCell;

/**
 * @author vladimir_shi
 * @since 13.09.2025
 */

public abstract class AbstractValidatingTreeCell<T> extends TreeTableCell<Object, T> {
    protected TextField textField;
    private boolean isShowingAlert = false;

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

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getItem() == null ? "" : getItem().toString());
        setGraphic(null);
    }

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

    private void createTextField() {
        textField = new TextField();
        textField.setOnAction(e -> processEdit());
        textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) processEdit();
        });
    }

    protected void processEdit() {
        String input = textField.getText().trim();
        if (input.contains(",")) {
            showAlert("Используйте точку вместо запятой.");
            cancelEdit();
            return;
        }
        if (input.isEmpty()) {
            commitEdit(null);
            return;
        }
        validateAndCommit(input);
    }

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