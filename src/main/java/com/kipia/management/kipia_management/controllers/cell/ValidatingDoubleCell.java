package com.kipia.management.kipia_management.controllers.cell;

/**
 * Класс для валидации чисел с плавающей точкой
 * @author vladimir_shi
 * @since 13.09.2025
 */

public class ValidatingDoubleCell extends AbstractValidatingCell<Double> {

    /**
     * Метод валидации и сохранения значения
     * @param input - введённое значение
     */
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