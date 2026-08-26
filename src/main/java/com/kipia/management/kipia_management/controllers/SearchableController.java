package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Scheme;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

/**
 * Интерфейс для контроллеров, поддерживающих поиск из верхней панели
 * 
 * @author vladimir_shi
 * @since 15.01.2025
 */
public interface SearchableController {
    
    /**
     * Связывает элементы поиска из верхней панели с контроллером
     * 
     * @param searchField поле поиска
     */
    void bindSearchField(TextField searchField);
    
    /**
     * Связывает фильтр по местоположению (для галереи)
     * 
     * @param locationFilter комбобокс с местами установки
     */
    default void bindLocationFilter(ComboBox<String> locationFilter) {
        // По умолчанию не используется
    }
    
    /**
     * Связывает чекбокс "Только с фото" (для галереи)
     * 
     * @param photosOnlyCheck чекбокс
     */
    default void bindPhotosOnlyCheck(CheckBox photosOnlyCheck) {
        // По умолчанию не используется
    }

    default void bindSchemeFilter(ComboBox<Scheme> schemeFilter) {
        // По умолчанию не используется
    }

    default void bindStatusFilter(ComboBox<String> statusFilter) {
        // По умолчанию не используется
    }

    default void bindTypeFilter(ComboBox<String> typeFilter) {
        // По умолчанию не используется
    }

    default void bindManufacturerFilter(ComboBox<String> manufacturerFilter) {
        // По умолчанию не используется
    }

    default void bindYearFilter(ComboBox<String> yearFilter) {
        // По умолчанию не используется
    }

    /**
     * Очищает все фильтры
     */
    void clearFilters();
    
    /**
     * Возвращает true, если контроллер использует расширенные фильтры (галерея)
     */
    default boolean hasExtendedFilters() {
        return false;
    }

    /**
     * Возвращает список доступных типов фильтров для отчетов.
     * Используется в двухкомбобоксовом паттерне "тип → значение".
     */
    default java.util.List<String> getReportFilterTypes() {
        return java.util.Collections.emptyList();
    }

    /**
     * Возвращает список значений для заданного типа фильтра в отчетах.
     */
    default java.util.List<String> getReportFilterValues(String filterType) {
        return java.util.Collections.emptyList();
    }

    /**
     * Применяет фильтр отчета: тип + значение.
     */
    default void applyReportFilter(String filterType, String filterValue) {
        // по умолчанию ничего не делает
    }

    /**
     * Сбрасывает фильтр отчета.
     */
    default void clearReportFilter() {
        // по умолчанию ничего не делает
    }

    /**
     * Связывает комбобоксы для паттерна отчетов (тип фильтра → значение)
     */
    default void bindReportFilterCombos(ComboBox<String> filterTypeCombo, ComboBox<String> filterValueCombo) {
        // по умолчанию ничего не делает
    }

    /**
     * Обновляет комбобоксы отчетов при раскрытии поисковой панели
     */
    default void refreshReportFilterCombos() {
        // по умолчанию ничего не делает
    }
}
