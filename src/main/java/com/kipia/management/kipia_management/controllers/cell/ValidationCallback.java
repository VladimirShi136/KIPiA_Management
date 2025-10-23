package com.kipia.management.kipia_management.controllers.cell;

/**
 * Интерфейс для обратного вызова валидации и commit ввода.
 * Реализуется в подклассах AbstractValidatingCell и AbstractValidatingTreeCell,
 * чтобы делегировать специфичную логику валидации (например, проверка типа данных и вызов commitEdit/cancelEdit).
 *
 * @author vladimir_shi
 * @since 10.10.2025
 */
public interface ValidationCallback {

    /**
     * Выполняет валидацию ввода и, если валидно, commit изменения в ячейке.
     * Если невалидно, отменяет редактирование.
     *
     * @param input строка ввода пользователя
     */
    void validateAndCommit(String input);
}

