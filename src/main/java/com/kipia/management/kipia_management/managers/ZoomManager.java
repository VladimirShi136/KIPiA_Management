package com.kipia.management.kipia_management.managers;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.geometry.Bounds;
import java.util.function.Consumer;

/**
 * Менеджер зума для управления масштабом схемы.
 * @author vladimir_shi
 * @since 29.10.2025
 */
public class ZoomManager {
    // ============================================================
    // DEPENDENCIES & STATE
    // ============================================================
    private final AnchorPane pane;
    private final ScrollPane scrollPane;
    private final Consumer<String> statusUpdater;  // Callback для обновления label (опционально)
    private final Runnable onZoomChanged;  // E.g., обновить selected shape handles

    private double currentZoom = 1.0;
    private static final double MIN_ZOOM = 0.3;
    private static final double MAX_ZOOM = 1.0;
    private static final double ZOOM_FACTOR = 1.2;

    /**
     * Конструктор
     * @param pane панель для зума (e.g., schemePane)
     * @param statusUpdater callback для статуса (can be null)
     * @param onZoomChanged callback после зума (e.g., update handles, can be null)
     */
    public ZoomManager(AnchorPane pane, ScrollPane scrollPane, Consumer<String> statusUpdater, Runnable onZoomChanged) {
        this.pane = pane;
        this.scrollPane = scrollPane;
        this.statusUpdater = statusUpdater;
        this.onZoomChanged = onZoomChanged;
        centerOnBorder();
    }

    // ============================================================
    // PUBLIC API
    // ============================================================
    /**
     * Zoom in
     */
    public void zoomIn() {
        zoom(ZOOM_FACTOR);
    }

    /**
     * Zoom out
     */
    public void zoomOut() {
        zoom(1.0 / ZOOM_FACTOR);
    }

    /**
     * Zoom to factor
     */
    public void zoom(double factor, double mouseSceneX, double mouseSceneY) {
        double newZoom = currentZoom * factor;
        newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));

        if (newZoom != currentZoom) {
            currentZoom = newZoom;

            // Простой зум без сложных трансформаций
            pane.setScaleX(currentZoom);
            pane.setScaleY(currentZoom);
            pane.setTranslateX(0);
            pane.setTranslateY(0);

            if (statusUpdater != null) {
                statusUpdater.accept(String.format("Зум: %.0f%%", currentZoom * 100));
            }
            if (onZoomChanged != null) {
                onZoomChanged.run();
            }

            // После зума автоматически центрируем на квадрате
            centerOnBorder();
        }
    }

    public void zoom(double factor) {
        // Зум относительно центра viewport
        if (scrollPane != null) {
            Bounds viewport = scrollPane.getViewportBounds();
            if (viewport != null) {
                double centerX = viewport.getWidth() / 2;
                double centerY = viewport.getHeight() / 2;
                zoom(factor, centerX, centerY);
            }
        }
    }

    /**
     * Подгоняет масштаб чтобы вся схема была видна в viewport
     */
    public void zoomToFit() {
        if (pane == null || scrollPane == null) return;

        try {
            // Получаем размеры панели схемы и viewport
            double paneWidth = pane.getWidth();
            double paneHeight = pane.getHeight();
            double viewportWidth = scrollPane.getViewportBounds().getWidth();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();

            // Вычисляем коэффициенты масштабирования для ширины и высоты
            double scaleX = viewportWidth / paneWidth;
            double scaleY = viewportHeight / paneHeight;

            // Выбираем минимальный коэффициент чтобы вся схема поместилась
            double newScale = Math.min(scaleX, scaleY) * 0.9; // 90% чтобы были небольшие отступы

            // Ограничиваем масштаб разумными пределами
            newScale = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newScale));

            // Применяем масштаб
            setZoom(newScale);

            // Центрируем схему
            centerOnBorder();

            if (statusUpdater != null) {
                statusUpdater.accept("Масштаб подогнан: " + String.format("%.0f%%", newScale * 100));
            }

            System.out.println("DEBUG: Zoom to fit - scale: " + newScale +
                    ", pane: " + paneWidth + "x" + paneHeight +
                    ", viewport: " + viewportWidth + "x" + viewportHeight);

        } catch (Exception e) {
            System.err.println("ERROR in zoomToFit: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Центрирует viewport на красной рамке схемы
     */
    public void centerOnBorder() {
        if (scrollPane == null || pane == null) return;

        try {
            // Даем JavaFX время на обновление layout
            javafx.application.Platform.runLater(() -> {
                try {
                    // Получаем реальные размеры
                    double paneWidth = pane.getWidth();
                    double paneHeight = pane.getHeight();
                    double viewportWidth = scrollPane.getViewportBounds().getWidth();
                    double viewportHeight = scrollPane.getViewportBounds().getHeight();

                    System.out.println("DEBUG centerOnBorder:");
                    System.out.println("  - Pane size: " + paneWidth + "x" + paneHeight);
                    System.out.println("  - Viewport: " + viewportWidth + "x" + viewportHeight);
                    System.out.println("  - Pane pref size: " + pane.getPrefWidth() + "x" + pane.getPrefHeight());
                    System.out.println("  - ScrollPane size: " + scrollPane.getWidth() + "x" + scrollPane.getHeight());

                    // Если размеры невалидные, используем предустановленные
                    if (paneWidth <= 0 || paneHeight <= 0) {
                        paneWidth = 1600; // Ширина красного квадрата
                        paneHeight = 1200; // Высота красного квадрата
                        System.out.println("DEBUG: Using fixed sizes: " + paneWidth + "x" + paneHeight);
                    }

                    // Красный квадрат находится в (0,0) с размерами 1600x1200
                    // Мы хотим центрировать viewport на центре квадрата
                    double centerX = 1600.0 / 2;
                    double centerY = 1200.0 / 2;

                    // Вычисляем значения прокрутки для центрирования
                    // hvalue = (targetX - viewportWidth/2) / (paneWidth - viewportWidth)
                    double hvalue = (centerX - viewportWidth / 2) / Math.max(1, paneWidth - viewportWidth);
                    double vvalue = (centerY - viewportHeight / 2) / Math.max(1, paneHeight - viewportHeight);

                    // Ограничиваем значения между 0 и 1
                    hvalue = Math.max(0, Math.min(1, hvalue));
                    vvalue = Math.max(0, Math.min(1, vvalue));

                    scrollPane.setHvalue(hvalue);
                    scrollPane.setVvalue(vvalue);

                    System.out.println("DEBUG: Scroll values - hvalue: " + hvalue + ", vvalue: " + vvalue);
                    System.out.println("DEBUG: Target center - X: " + centerX + ", Y: " + centerY);

                } catch (Exception e) {
                    System.err.println("ERROR in centerOnBorder: " + e.getMessage());
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            System.err.println("ERROR in centerOnBorder outer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Установка конкретного значения зума
     */
    public void setZoom(double zoomLevel) {
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomLevel));

        if (newZoom != currentZoom) {
            currentZoom = newZoom;

            // Простой зум без сложных трансформаций
            pane.setScaleX(currentZoom);
            pane.setScaleY(currentZoom);
            pane.setTranslateX(0);
            pane.setTranslateY(0);

            if (statusUpdater != null) {
                statusUpdater.accept(String.format("Зум: %.0f%%", currentZoom * 100));
            }
            if (onZoomChanged != null) {
                onZoomChanged.run();
            }

            // После зума автоматически центрируем на квадрате
            centerOnBorder();
        }
    }
}

