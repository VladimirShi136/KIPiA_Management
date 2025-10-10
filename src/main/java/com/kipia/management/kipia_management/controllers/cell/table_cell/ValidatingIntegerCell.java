package com.kipia.management.kipia_management.controllers.cell.table_cell;

import com.kipia.management.kipia_management.utils.CustomAlert;

/**
 * Класс для валидации целых чисел.
 * Теперь использует композицию с ValidatingCellEditor для делегирования сообщений об ошибках.
 * @author vladimir_shi
 * @since 13.09.2025
 */
public class ValidatingIntegerCell extends AbstractValidatingCell<Integer> {

    /**
     * Метод валидации и сохранения значения.
     * @param input - введённое значение
     */
    @Override
    public void validateAndCommit(String input) {
        try {
            int val = Integer.parseInt(input);
            // Проверка диапазона (по желанию)
            if (val < 1900 || val > 2100) {
                // Замена: используем CustomAlert вместо старого showAlert
                CustomAlert.showError("Ошибка валидации", "Год должен быть в диапазоне 1900–2100.");
                cancelEdit();
                return;
            }
            commitEdit(val);
        } catch (NumberFormatException e) {
            // Замена: используем CustomAlert вместо старого showAlert
            CustomAlert.showError("Ошибка валидации", "Введите целое число.");
            cancelEdit();
        }
    }
}