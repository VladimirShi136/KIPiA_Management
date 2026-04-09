package com.kipia.management.kipia_management.utils;

import javafx.scene.control.Dialog;
import javafx.stage.StageStyle;

/**
 * Утилита для применения единого стиля ко всем диалогам приложения
 * в соответствии со стилем CustomAlertDialog (без системного titlebar)
 * 
 * @author vladimir_shi
 * @since 05.04.2026
 */
public class DialogStyler {
    
    /**
     * Применяет единый стиль к диалогу БЕЗ системного titlebar
     * ВАЖНО: Вызывать ДО showAndWait()!
     */
    public static void applyStyle(Dialog<?> dialog) {
        // КРИТИЧЕСКИ ВАЖНО: initStyle должен быть вызван ПЕРВЫМ, до любых других операций
        try {
            dialog.initStyle(StageStyle.UTILITY);
        } catch (IllegalStateException e) {
            // Если Stage уже создан, игнорируем - ничего не можем сделать
            // В этом случае будет стандартный titlebar
        }
        
        boolean dark = StyleUtils.getCurrentTheme().contains("dark");
        
        // Применяем стили к DialogPane с !important для переопределения CSS
        String dialogPaneStyle = 
            "-fx-background-color:" + (dark ? "#252d38" : "#ffffff") + " !important;" +
            "-fx-background-radius:10px !important;" +
            "-fx-border-color:" + (dark ? "#2d3e50" : "#d0d4d8") + " !important;" +
            "-fx-border-width:1px !important;" +
            "-fx-border-radius:10px !important;" +
            "-fx-effect:dropshadow(gaussian," + 
                (dark ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.22)") + ",20,0,0,5) !important;" +
            "-fx-padding:0 !important;";
        
        dialog.getDialogPane().setStyle(dialogPaneStyle);
        
        // Применяем CSS тему
        try {
            dialog.getDialogPane().getStylesheets().clear();
            dialog.getDialogPane().getStylesheets().add(
                DialogStyler.class.getResource(StyleUtils.getCurrentTheme()).toExternalForm()
            );
        } catch (Exception ignored) {}
    }
}
