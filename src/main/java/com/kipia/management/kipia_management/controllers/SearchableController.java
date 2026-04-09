package com.kipia.management.kipia_management.controllers;

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
}
