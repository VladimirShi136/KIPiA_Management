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
     * Центрирует вид на красный квадрат
     */
    public void centerOnBorder() {
        if (scrollPane == null) return;

        // Центрируем на прямоугольнике 1600x1200
        double centerH = 0.5;
        double centerV = 0.5;

        javafx.application.Platform.runLater(() -> {
            scrollPane.setHvalue(centerH);
            scrollPane.setVvalue(centerV);
        });
    }

    public void fitZoom() {
        // Fit на прямоугольник 1600x1200
        if (scrollPane == null) return;

        Bounds viewport = scrollPane.getViewportBounds();
        if (viewport == null) return;

        double zoomX = viewport.getWidth() / 1600;
        double zoomY = viewport.getHeight() / 1200;
        double fitZoom = Math.min(zoomX, zoomY) * 0.85; // 15% отступ

        fitZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, fitZoom));

        currentZoom = fitZoom;
        pane.setScaleX(currentZoom);
        pane.setScaleY(currentZoom);
        pane.setTranslateX(0);
        pane.setTranslateY(0);

        centerOnBorder();

        if (statusUpdater != null) {
            statusUpdater.accept(String.format("Автоподгонка: %.0f%%", currentZoom * 100));
        }
        if (onZoomChanged != null) {
            onZoomChanged.run();
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

    /**
     * Получение текущего значения зума
     */
    public double getCurrentZoom() {
        return currentZoom;
    }
}

