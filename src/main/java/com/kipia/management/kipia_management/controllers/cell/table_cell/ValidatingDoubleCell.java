package com.kipia.management.kipia_management.controllers.cell.table_cell;

import com.kipia.management.kipia_management.utils.CustomAlertDialog;

/**
 * Класс для валидации чисел с плавающей точкой.
 * Теперь использует композицию с ValidatingCellEditor для делегирования сообщений об ошибках.
 *
 * @author vladimir_shi
 * @since 13.09.2025
 */
public class ValidatingDoubleCell extends AbstractValidatingCell<Double> {

    /**
     * Метод валидации и сохранения значения.
     * @param input - введённое значение
     */
    @Override
    public void validateAndCommit(String input) {
        try {
            double val = Double.parseDouble(input);
            if (val < 0) {
                // Замена: используем CustomAlert вместо старого showAlert
                CustomAlertDialog.showError("Ошибка валидации", "Значение должно быть неотрицательным.");
                cancelEdit();
                return;
            }
            commitEdit(val);
        } catch (NumberFormatException e) {
            // Замена: используем CustomAlert вместо старого showAlert
            CustomAlertDialog.showError("Ошибка валидации", "Введите число с точкой.");
            cancelEdit();
        }
    }
}