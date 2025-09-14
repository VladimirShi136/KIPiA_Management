package com.kipia.management.kipia_management.controllers.cell.tree_table_cell;

/**
 * @author vladimir_shi
 * @since 13.09.2025
 */

public class ValidatingIntegerTreeCell extends AbstractValidatingTreeCell<Integer> {
    @Override
    protected void validateAndCommit(String input) {
        try {
            int val = Integer.parseInt(input);
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
