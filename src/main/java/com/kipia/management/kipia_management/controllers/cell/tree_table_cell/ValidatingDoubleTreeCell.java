package com.kipia.management.kipia_management.controllers.cell.tree_table_cell;

/**
 * @author vladimir_shi
 * @since 13.09.2025
 */

public class ValidatingDoubleTreeCell extends AbstractValidatingTreeCell<Double> {
    @Override
    protected void validateAndCommit(String input) {
        try {
            double val = Double.parseDouble(input);
            if (val < 0) {
                showAlert("Значение должно быть неотрицательным.");
                cancelEdit();
                return;
            }
            commitEdit(val);
        } catch (NumberFormatException e) {
            showAlert("Введите число с точкой.");
            cancelEdit();
        }
    }
}
