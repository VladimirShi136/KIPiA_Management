package com.kipia.management.kipia_management.utils;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Утилита для отображения индикатора загрузки поверх контента.
 * 
 * @author vladimir_shi
 * @since 01.04.2026
 */
public class LoadingIndicator {
    
    private final StackPane overlay;
    private final Label messageLabel;
    
    /**
     * Создает индикатор загрузки с сообщением по умолчанию.
     */
    public LoadingIndicator() {
        this("Загрузка...");
    }
    
    /**
     * Создает индикатор загрузки с заданным сообщением.
     * 
     * @param message текст сообщения
     */
    public LoadingIndicator(String message) {
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(60, 60);
        
        messageLabel = new Label(message);
        messageLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        
        VBox content = new VBox(15, progressIndicator, messageLabel);
        content.setAlignment(Pos.CENTER);
        content.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); " +
                        "-fx-background-radius: 10; " +
                        "-fx-padding: 30;");
        
        overlay = new StackPane(content);
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.3); -fx-background-radius: 10;");
        overlay.setVisible(false);
        overlay.setManaged(false);
    }
    
    /**
     * Возвращает overlay для добавления в контейнер.
     */
    public StackPane getOverlay() {
        return overlay;
    }
    
    /**
     * Показывает индикатор загрузки.
     */
    public void show() {
        Platform.runLater(() -> {
            overlay.setVisible(true);
            overlay.setManaged(true);
            overlay.toFront();
        });
    }
    
    /**
     * Скрывает индикатор загрузки.
     */
    public void hide() {
        Platform.runLater(() -> {
            overlay.setVisible(false);
            overlay.setManaged(false);
        });
    }
    
    /**
     * Обновляет текст сообщения.
     */
    public void setMessage(String message) {
        Platform.runLater(() -> messageLabel.setText(message));
    }
}
