package com.kipia.management.kipia_management.managers;

import com.kipia.management.kipia_management.controllers.SearchableController;
import com.kipia.management.kipia_management.models.Scheme;
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
    private Button topClearSearchButton;

    // Состояние
    private SearchableController currentSearchableController;
    private boolean isTopSearchExpanded = false;

    /**
     * Инициализирует менеджер с UI элементами
     */
    public void initialize(HBox topSearchPanel, Button topSearchToggleButton, HBox topSearchFieldContainer,
                           TextField topSearchField, ComboBox<String> topLocationFilter,
                           ComboBox<Scheme> topSchemeFilter, CheckBox topPhotosOnlyCheck,
                           Button topClearSearchButton) {
        this.topSearchPanel = topSearchPanel;
        this.topSearchToggleButton = topSearchToggleButton;
        this.topSearchFieldContainer = topSearchFieldContainer;
        this.topSearchField = topSearchField;
        this.topLocationFilter = topLocationFilter;
        this.topSchemeFilter = topSchemeFilter;
        this.topPhotosOnlyCheck = topPhotosOnlyCheck;
        this.topClearSearchButton = topClearSearchButton;

        setupEventHandlers();
        hideSearchFieldContainer();

        // Плавный выезд списка (fade + slide) и разворот стрелки для комбобоксов
        setupComboPopupAnimation(topLocationFilter);
        setupComboPopupAnimation(topSchemeFilter);

        // Лёгкий "pop"-эффект на галочке чекбокса при выборе
        setupCheckboxPop(topPhotosOnlyCheck);
    }

    /**
     * Добавляет комбобоксу плавное появление выпадающего списка (fade + slide вниз)
     * и плавный разворот стрелки при открытии/закрытии.
     * <p>
     * ВАЖНО: попап ComboBox — это отдельный {@link Window} (PopupWindow), а не часть
     * графа сцены самого ComboBox. Поэтому {@code combo.lookup(".list-view")} его
     * никогда не найдёт — нужно искать среди всех открытых окон через
     * {@link Window#getWindows()}. Анимируем сам Window (opacity + y), а не узел
     * внутри него — это исключает визуальную обрезку контента по границам попапа,
     * которая возникает, если анимировать translateY внутреннего узла.
     */
    private void setupComboPopupAnimation(ComboBox<?> combo) {
        if (combo == null) return;

        combo.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            // Разворот стрелки — она часть основной сцены, lookup работает как обычно
            Node arrow = combo.lookup(".arrow");
            if (arrow != null) {
                RotateTransition rotate = new RotateTransition(Duration.millis(220), arrow);
                rotate.setToAngle(isShowing ? 180 : 0);
                rotate.setInterpolator(Interpolator.EASE_BOTH);
                rotate.play();
            }

            if (isShowing) {
                // Даём JavaFX кадр на то, чтобы попап реально появился в Window.getWindows()
                Platform.runLater(() -> {
                    Window popupWindow = findOpenPopupWindow();
                    if (popupWindow == null) return;

                    double startY = popupWindow.getY() - 14;
                    double targetY = popupWindow.getY();
                    popupWindow.setOpacity(0);
                    popupWindow.setY(startY);

                    // Window.yProperty() доступен только на чтение (есть только setY()),
                    // поэтому анимируем вручную через interpolate(), а не через KeyValue/Timeline.
                    Transition reveal = new Transition() {
                        {
                            setCycleDuration(Duration.millis(240));
                            setInterpolator(Interpolator.EASE_OUT);
                        }

                        @Override
                        protected void interpolate(double frac) {
                            popupWindow.setOpacity(frac);
                            popupWindow.setY(startY + (targetY - startY) * frac);
                        }
                    };
                    reveal.play();
                });
            }
        });
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
        if (topClearSearchButton != null) {
            topClearSearchButton.setOnAction(_ -> clearSearch());
        }
        if (topSearchField != null) {
            topSearchField.textProperty().addListener((_, _, _) -> updateClearButtonVisibility());
        }
        if (topLocationFilter != null) {
            topLocationFilter.valueProperty().addListener((_, _, _) -> updateClearButtonVisibility());
        }
        if (topPhotosOnlyCheck != null) {
            topPhotosOnlyCheck.selectedProperty().addListener((_, _, _) -> updateClearButtonVisibility());
        }
        if (topSchemeFilter != null) {
            topSchemeFilter.valueProperty().addListener((_, _, _) -> updateClearButtonVisibility());
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
            if (topSchemeFilter != null) topSchemeFilter.setValue(null);

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
     * Очищает поиск
     */
    private void clearSearch() {
        if (topSearchField != null) topSearchField.clear();
        if (currentSearchableController != null) currentSearchableController.clearFilters();
        if (isTopSearchExpanded) toggleSearch();
    }

    /**
     * Обновляет видимость кнопки очистки
     */
    private void updateClearButtonVisibility() {
        if (topClearSearchButton == null) return;
        boolean hasText = topSearchField != null && topSearchField.getText() != null && !topSearchField.getText().isEmpty();
        boolean hasFilter = topLocationFilter != null && topLocationFilter.isVisible()
                && topLocationFilter.getValue() != null && !"Все места".equals(topLocationFilter.getValue());
        boolean hasCheck = topPhotosOnlyCheck != null && topPhotosOnlyCheck.isVisible() && topPhotosOnlyCheck.isSelected();
        boolean hasScheme = topSchemeFilter != null && topSchemeFilter.isVisible() && topSchemeFilter.getValue() != null;
        topClearSearchButton.setVisible(hasText || hasFilter || hasCheck || hasScheme);
    }

    /**
     * Сбрасывает состояние поиска при навигации
     */
    public void resetOnNavigation() {
        if (topSearchField != null) topSearchField.clear();
        if (isTopSearchExpanded) {
            isTopSearchExpanded = false;
            hideSearchFieldContainer();
        }
        if (topSchemeFilter != null) topSchemeFilter.setValue(null);
        currentSearchableController = null;
    }

    /**
     * Показывает/скрывает поисковую панель с нужными фильтрами
     */
    public void showPanel(boolean show, boolean hasExtendedFilters, boolean hasSchemeFilter) {
        if (topSearchPanel == null) return;
        topSearchPanel.setVisible(show);
        topSearchPanel.setManaged(show);

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
        else if (!hasSchemeFilter && topSearchField != null) {
            topSearchField.setVisible(true);
            topSearchField.setManaged(true);
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
        this.currentSearchableController = controller;
        if (controller == null) return;

        // Связываем TextField только если он видим (не для редактора схем)
        if (topSearchField != null && topSearchField.isVisible()) {
            controller.bindSearchField(topSearchField);
        }
        if (topLocationFilter != null) controller.bindLocationFilter(topLocationFilter);
        if (topPhotosOnlyCheck != null) controller.bindPhotosOnlyCheck(topPhotosOnlyCheck);
        if (topSchemeFilter != null) controller.bindSchemeFilter(topSchemeFilter);
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