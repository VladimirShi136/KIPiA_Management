package com.kipia.management.kipia_management.controllers.cell.table_cell;

/**
 * Класс для валидации целых чисел
 * @author vladimir_shi
 * @since 13.09.2025
 */

public class ValidatingIntegerCell extends AbstractValidatingCell<Integer> {

    /**
     * Метод для валидации и сохранения значения
     * @param input - введённое значение
     */
    @Override
    protected void validateAndCommit(String input) {
        try {
            int val = Integer.parseInt(input);
            // Пример проверки диапазона (по желанию)
            if (val < 1900 || val > 2100) {
                showAlert("Год должен быть в диапазоне 1900–2100.");
                cancelEdit();
                return;
            }
            commitEdit(val);
        } catch (NumberFormatException e) {
            showAlert("Введите целое число.");
            cancelEdit();
        }
    }
}
