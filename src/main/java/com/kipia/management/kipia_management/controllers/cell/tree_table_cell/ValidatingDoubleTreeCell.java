package com.kipia.management.kipia_management.controllers.cell.tree_table_cell;

import com.kipia.management.kipia_management.utils.CustomAlert;

/**
 * @author vladimir_shi
 * @since 13.09.2025
 */

public class ValidatingDoubleTreeCell extends AbstractValidatingTreeCell<Double> {
    @Override
    public void validateAndCommit(String input) {
        try {
            double val = Double.parseDouble(input);
            if (val < 0) {
                // Замена: используем CustomAlert вместо родительского showAlert
                CustomAlert.showError("Ошибка валидации", "Значение должно быть неотрицательным.");
                cancelEdit();
                return;
            }
            commitEdit(val);
        } catch (NumberFormatException e) {
            // Замена: используем CustomAlert вместо родительского showAlert
            CustomAlert.showError("Ошибка валидации", "Введите число с точкой.");
            cancelEdit();
        }
    }
}