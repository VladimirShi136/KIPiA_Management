package com.kipia.management.kipia_management.managers;

import com.kipia.management.kipia_management.controllers.SearchableController;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.List;

/**
 * Менеджер поисковой панели в верхней части приложения.
 * Управляет отображением, связыванием элементов и логикой поиска.
 *
 * @author vladimir_shi
 * @since 09.08.2026
 */
public class SearchPanelManager {

    // UI элементы поисковой панели
    private HBox topSearchPanel;
    private Button topSearchToggleButton;
    private HBox topSearchFieldContainer;
    private TextField topSearchField;
    private ComboBox<String> topLocationFilter;
    private ComboBox<Scheme> topSchemeFilter;
    private CheckBox topPhotosOnlyCheck;
    private ComboBox<String> topStatusFilter;
    private ComboBox<String> topTypeFilter;
    private ComboBox<String> topManufacturerFilter;
    private ComboBox<String> topYearFilter;
    private HBox topActiveFiltersBox;
    private ComboBox<String> topAddFilterCombo;
    private Button topLocationRemove;
    private Button topPhotosOnlyRemove;
    private Button topStatusRemove;
    private Button topTypeRemove;
    private Button topManufacturerRemove;
    private Button topYearRemove;
    private ComboBox<String> topReportFilterTypeCombo;
    private ComboBox<String> topReportFilterValueCombo;

    // Состояние
    private SearchableController currentSearchableController;
    private boolean isTopSearchExpanded = false;
    private boolean isReportsMode = false;
    private boolean isSchemeEditorMode = false;

    /**
     * Инициализирует менеджер с UI элементами
     */
    public void initialize(HBox topSearchPanel, Button topSearchToggleButton, HBox topSearchFieldContainer,
                           TextField topSearchField, ComboBox<String> topLocationFilter,
                           ComboBox<Scheme> topSchemeFilter, CheckBox topPhotosOnlyCheck,
                           ComboBox<String> topStatusFilter, ComboBox<String> topTypeFilter,
                           ComboBox<String> topManufacturerFilter, ComboBox<String> topYearFilter,
                           HBox topActiveFiltersBox, ComboBox<String> topAddFilterCombo,
                           Button topLocationRemove, Button topPhotosOnlyRemove,
                           Button topStatusRemove, Button topTypeRemove,
                           Button topManufacturerRemove, Button topYearRemove,
                           ComboBox<String> topReportFilterTypeCombo, ComboBox<String> topReportFilterValueCombo) {
        this.topSearchPanel = topSearchPanel;
        this.topSearchToggleButton = topSearchToggleButton;
        this.topSearchFieldContainer = topSearchFieldContainer;
        this.topSearchField = topSearchField;
        this.topLocationFilter = topLocationFilter;
        this.topSchemeFilter = topSchemeFilter;
        this.topPhotosOnlyCheck = topPhotosOnlyCheck;
        this.topStatusFilter = topStatusFilter;
        this.topTypeFilter = topTypeFilter;
        this.topManufacturerFilter = topManufacturerFilter;
        this.topYearFilter = topYearFilter;
        this.topActiveFiltersBox = topActiveFiltersBox;
        this.topAddFilterCombo = topAddFilterCombo;
        this.topLocationRemove = topLocationRemove;
        this.topPhotosOnlyRemove = topPhotosOnlyRemove;
        this.topStatusRemove = topStatusRemove;
        this.topTypeRemove = topTypeRemove;
        this.topManufacturerRemove = topManufacturerRemove;
        this.topYearRemove = topYearRemove;
        this.topReportFilterTypeCombo = topReportFilterTypeCombo;
        this.topReportFilterValueCombo = topReportFilterValueCombo;

        setupEventHandlers();
        hideSearchFieldContainer();

        // Плавный выезд списка (fade + slide) и разворот стрелки для комбобоксов
        StyleUtils.setupComboBoxArrowAnimation(topLocationFilter);
        StyleUtils.setupComboBoxPopupAnimation(topLocationFilter);
        StyleUtils.setupComboBoxArrowAnimation(topSchemeFilter);
        StyleUtils.setupComboBoxPopupAnimation(topSchemeFilter);

        // Лёгкий "pop"-эффект на галочке чекбокса при выборе
        setupCheckboxPop(topPhotosOnlyCheck);
    }

    /**
     * Находит текущий открытый попап ComboBox среди всех окон приложения.
     * Берём последнее показанное {@link PopupWindow} — это и есть только что
     * открывшийся выпадающий список.
     */
    private Window findOpenPopupWindow() {
        List<Window> windows = Window.getWindows();
        for (int i = windows.size() - 1; i >= 0; i--) {
            Window w = windows.get(i);
            if (w instanceof PopupWindow && w.isShowing()) {
                return w;
            }
        }
        return null;
    }

    /**
     * Добавляет чекбоксу небольшую анимацию "выскакивания" галочки при выборе.
     */
    private void setupCheckboxPop(CheckBox checkBox) {
        if (checkBox == null) return;

        checkBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (!isSelected) return;
            Node mark = checkBox.lookup(".mark");
            if (mark == null) return;

            mark.setScaleX(0.3);
            mark.setScaleY(0.3);

            ScaleTransition pop = new ScaleTransition(Duration.millis(200), mark);
            pop.setToX(1);
            pop.setToY(1);
            pop.setInterpolator(Interpolator.EASE_OUT);
            pop.play();
        });
    }

    /**
     * Настраивает обработчики событий
     */
    private void setupEventHandlers() {
        if (topSearchToggleButton != null) {
            topSearchToggleButton.setOnAction(_ -> toggleSearch());
        }
        if (topSearchField != null) {
            topSearchField.textProperty().addListener((_, _, _) -> {});
        }
        if (topLocationFilter != null) {
            topLocationFilter.valueProperty().addListener((_, _, _) -> {});
        }
        if (topPhotosOnlyCheck != null) {
            topPhotosOnlyCheck.selectedProperty().addListener((_, _, _) -> {});
        }
        if (topSchemeFilter != null) {
            topSchemeFilter.valueProperty().addListener((_, _, _) -> {});
        }
        if (topStatusFilter != null) {
            topStatusFilter.valueProperty().addListener((_, _, _) -> {});
        }
        if (topTypeFilter != null) {
            topTypeFilter.valueProperty().addListener((_, _, _) -> {});
        }
        if (topManufacturerFilter != null) {
            topManufacturerFilter.valueProperty().addListener((_, _, _) -> {});
        }
        if (topYearFilter != null) {
            topYearFilter.valueProperty().addListener((_, _, _) -> {});
        }
    }

    /**
     * Переключает отображение поля поиска
     */
    private void toggleSearch() {
        if (topSearchFieldContainer == null) return;
        isTopSearchExpanded = !isTopSearchExpanded;

        if (isTopSearchExpanded) {
            topSearchFieldContainer.setVisible(true);
            topSearchFieldContainer.setManaged(true);
            topSearchFieldContainer.setOpacity(0.0);

            // Показываем комбобоксы отчетов только в режиме отчетов
            if (isReportsMode) {
                if (topReportFilterTypeCombo != null) {
                    topReportFilterTypeCombo.setVisible(true);
                    topReportFilterTypeCombo.setManaged(true);
                }
                if (topReportFilterValueCombo != null) {
                    topReportFilterValueCombo.setVisible(true);
                    topReportFilterValueCombo.setManaged(true);
                }
                // Обновляем комбобоксы отчетов для восстановления promptText
                if (currentSearchableController != null) {
                    currentSearchableController.refreshReportFilterCombos();
                }
            }

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(350), topSearchFieldContainer);
            slideIn.setFromX(60);
            slideIn.setToX(0);
            slideIn.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(350), topSearchFieldContainer);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition parallelIn = new ParallelTransition(slideIn, fadeIn);
            parallelIn.play();
        } else {
            if (topSearchField != null) topSearchField.clear();
            if (topLocationFilter != null) topLocationFilter.setValue("Все места");
            if (topPhotosOnlyCheck != null) topPhotosOnlyCheck.setSelected(false);
            // Не сбрасываем фильтр схем в режиме редактора схем
            if (!isSchemeEditorMode && topSchemeFilter != null) {
                topSchemeFilter.setValue(null);
            }

            // Сбрасываем фильтры отчетов при сворачивании
            if (isReportsMode && currentSearchableController != null) {
                currentSearchableController.clearReportFilter();
            }

            // Сбрасываем и скрываем комбобоксы отчетов при сворачивании
            if (topReportFilterTypeCombo != null) {
                topReportFilterTypeCombo.setValue(null);
                topReportFilterTypeCombo.setVisible(false);
                topReportFilterTypeCombo.setManaged(false);
            }
            if (topReportFilterValueCombo != null) {
                topReportFilterValueCombo.setValue(null);
                topReportFilterValueCombo.setVisible(false);
                topReportFilterValueCombo.setManaged(false);
            }

            TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), topSearchFieldContainer);
            slideOut.setFromX(0);
            slideOut.setToX(60);
            slideOut.setInterpolator(Interpolator.EASE_IN);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), topSearchFieldContainer);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setInterpolator(Interpolator.EASE_IN);

            ParallelTransition parallelOut = new ParallelTransition(slideOut, fadeOut);
            parallelOut.setOnFinished(_ -> hideSearchFieldContainer());
            parallelOut.play();
        }
    }

    /**
     * Скрывает контейнер поля поиска
     */
    private void hideSearchFieldContainer() {
        if (topSearchFieldContainer != null) {
            topSearchFieldContainer.setVisible(false);
            topSearchFieldContainer.setManaged(false);
        }
    }

    /**
     * Сбрасывает состояние поиска при навигации
     */
    public void resetOnNavigation() {
        if (topSearchField != null) topSearchField.clear();
        // Принудительно скрываем контейнер при навигации, независимо от флага
        isTopSearchExpanded = false;
        hideSearchFieldContainer();
        if (topSchemeFilter != null) topSchemeFilter.setValue(null);
        if (topLocationFilter != null) topLocationFilter.setValue("Все места");
        if (topPhotosOnlyCheck != null) topPhotosOnlyCheck.setSelected(false);
        if (topStatusFilter != null) topStatusFilter.setValue(null);
        if (topTypeFilter != null) topTypeFilter.setValue(null);
        if (topManufacturerFilter != null) topManufacturerFilter.setValue(null);
        if (topYearFilter != null) topYearFilter.setValue(null);
        // Скрываем комбобоксы отчетов
        if (topReportFilterTypeCombo != null) {
            topReportFilterTypeCombo.setVisible(false);
            topReportFilterTypeCombo.setManaged(false);
        }
        if (topReportFilterValueCombo != null) {
            topReportFilterValueCombo.setVisible(false);
            topReportFilterValueCombo.setManaged(false);
        }
        currentSearchableController = null;
    }

    /**
     * Показывает/скрывает поисковую панель с нужными фильтрами
     */
    public void showPanel(boolean show, boolean hasExtendedFilters, boolean hasSchemeFilter, boolean hasReportFilters) {
        if (topSearchPanel == null) return;
        topSearchPanel.setVisible(show);
        topSearchPanel.setManaged(show);

        // Устанавливаем режим отчетов и редактора схем
        isReportsMode = hasReportFilters;
        isSchemeEditorMode = hasSchemeFilter;

        // Устанавливаем promptText для комбобоксов отчетов
        if (hasReportFilters) {
            if (topReportFilterTypeCombo != null) {
                topReportFilterTypeCombo.setPromptText("Фильтр...");
                // Значение по умолчанию будет установлено через refreshReportFilterCombos
            }
            if (topReportFilterValueCombo != null) {
                topReportFilterValueCombo.setPromptText("Значение...");
                topReportFilterValueCombo.setValue(null);
            }
        }

        if (topLocationFilter != null) {
            topLocationFilter.setVisible(hasExtendedFilters);
            topLocationFilter.setManaged(hasExtendedFilters);
        }
        if (topPhotosOnlyCheck != null) {
            topPhotosOnlyCheck.setVisible(hasExtendedFilters);
            topPhotosOnlyCheck.setManaged(hasExtendedFilters);
        }
        if (topSchemeFilter != null) {
            topSchemeFilter.setVisible(hasSchemeFilter);
            topSchemeFilter.setManaged(hasSchemeFilter);
        }

        // Для редактора схем скрываем только TextField, но оставляем ComboBox
        if (hasSchemeFilter && topSearchField != null) {
            topSearchField.setVisible(false);
            topSearchField.setManaged(false);
            isTopSearchExpanded = false;
        }
        // Для таблицы приборов и галереи показываем TextField
        else if (!hasSchemeFilter && !hasReportFilters && topSearchField != null) {
            topSearchField.setVisible(true);
            topSearchField.setManaged(true);
        }
        // Для отчетов скрываем TextField, но показываем комбобоксы отчетов при раскрытии
        else if (hasReportFilters && topSearchField != null) {
            topSearchField.setVisible(false);
            topSearchField.setManaged(false);
            // Комбобоксы отчетов видны только при раскрытом контейнере
            if (topReportFilterTypeCombo != null) {
                topReportFilterTypeCombo.setVisible(isTopSearchExpanded);
                topReportFilterTypeCombo.setManaged(isTopSearchExpanded);
            }
            if (topReportFilterValueCombo != null) {
                topReportFilterValueCombo.setVisible(isTopSearchExpanded);
                topReportFilterValueCombo.setManaged(isTopSearchExpanded);
            }
        }
        // В других режимах скрываем комбобоксы отчетов
        else {
            if (topReportFilterTypeCombo != null) {
                topReportFilterTypeCombo.setVisible(false);
                topReportFilterTypeCombo.setManaged(false);
            }
            if (topReportFilterValueCombo != null) {
                topReportFilterValueCombo.setVisible(false);
                topReportFilterValueCombo.setManaged(false);
            }
        }

        if (!show) {
            isTopSearchExpanded = false;
            hideSearchFieldContainer();
        }
    }

    /**
     * Связывает текущий контроллер с элементами поиска
     */
    public void bindController(SearchableController controller) {
        // Очищаем listener'ы от предыдущего контроллера
        if (currentSearchableController != null) {
            currentSearchableController.clearFilters();
        }

        this.currentSearchableController = controller;
        if (controller == null) return;

        // Связываем все элементы поиска без проверки видимости
        if (topSearchField != null) controller.bindSearchField(topSearchField);
        if (topLocationFilter != null) controller.bindLocationFilter(topLocationFilter);
        if (topPhotosOnlyCheck != null) controller.bindPhotosOnlyCheck(topPhotosOnlyCheck);
        if (topSchemeFilter != null) controller.bindSchemeFilter(topSchemeFilter);
        if (topStatusFilter != null) controller.bindStatusFilter(topStatusFilter);
        if (topTypeFilter != null) controller.bindTypeFilter(topTypeFilter);
        if (topManufacturerFilter != null) controller.bindManufacturerFilter(topManufacturerFilter);
        if (topYearFilter != null) controller.bindYearFilter(topYearFilter);

        // Связываем комбобоксы для паттерна отчетов
        controller.bindReportFilterCombos(topReportFilterTypeCombo, topReportFilterValueCombo);
    }

    /**
     * Устанавливает конвертер для фильтра схем
     */
    public void setSchemeConverter(javafx.util.StringConverter<Scheme> converter) {
        if (topSchemeFilter != null) {
            topSchemeFilter.setConverter(converter);
        }
    }

    /**
     * Возвращает текущий поисковый контроллер
     */
    public SearchableController getCurrentController() {
        return currentSearchableController;
    }
}