package com.kipia.management.kipia_management.controllers.cell.tree_table_cell;

import com.kipia.management.kipia_management.utils.CustomAlert;

/**
 * @author vladimir_shi
 * @since 13.09.2025
 */

public class ValidatingIntegerTreeCell extends AbstractValidatingTreeCell<Integer> {
    @Override
    public void validateAndCommit(String input) {
        try {
            int val = Integer.parseInt(input);
            if (val < 1900 || val > 2100) {
                // Замена: используем CustomAlert вместо родительского showAlert
                CustomAlert.showError("Ошибка валидации", "Год должен быть в диапазоне 1900–2100.");
                cancelEdit();
                return;
            }
            commitEdit(val);
        } catch (NumberFormatException e) {
            // Замена: используем CustomAlert вместо родительского showAlert
            CustomAlert.showError("Ошибка валидации", "Введите целое число.");
            cancelEdit();
        }
    }
}
