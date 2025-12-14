package com.kipia.management.kipia_management.controllers.cell;

import javafx.scene.control.Alert;
import javafx.scene.control.TextField;

/**
 * Вспомогательный класс для инкапсуляции общей логики редактирования валидируемых ячеек таблиц.
 * Обеспечивает создание TextField, обработку ввода, показ alert и вспомогательные методы.
 * Использует ValidationCallback для делегирования специфичной валидации подклассам.
 * @author vladimir_shi
 * @since 10.10.2025
 */
public class ValidatingCellEditor {
    protected TextField textField;  // Поле для ввода текста
    private boolean isShowingAlert = false;  // Флаг для предотвращения множественных предупреждений
    private final ValidationCallback callback;  // Callback для валидации и commiting

    /**
     * Конструктор. Принимает callback для обработки валидации.
     */
    public ValidatingCellEditor(ValidationCallback callback) {
        this.callback = callback;
    }

    /**
     * Получает TextField, создавая его при необходимости.
     * @return текстовое поле для редактирования
     */
    public TextField getTextField() {
        if (textField == null) {
            createTextField();
        }
        return textField;
    }

    /**
     * Обрабатывает окончание редактирования (например, при потере фокуса или Enter).
     * Проверяет ввод на запятую; если пусто, разрешает null; иначе делегирует валидацию через callback.
     */
    public void processEdit() {
        String input = textField.getText().trim();
        if (input.contains(",")) {
            showAlert("Используйте точку вместо запятой.");
            return;  // No commit, подкласс должен вызвать cancelEdit
        }
        if (input.isEmpty()) {
            callback.validateAndCommit(null);  // Пустое значение — подкласс решит, как делать commit
            return;
        }
        callback.validateAndCommit(input);  // Делегируем валидацию специфичной логике
    }

    /**
     * Показывает предупреждение, предотвращая множественные alerts.
     * @param msg текст сообщения
     */
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

    /**
     * Создает текстовое поле с обработчиками событий.
     */
    private void createTextField() {
        textField = new TextField();
        textField.setOnAction(e -> processEdit());
        textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) processEdit();  // При потере фокуса
        });
    }
}
