package com.kipia.management.kipia_management.utils;

import com.kipia.management.kipia_management.controllers.HelpController;
import com.kipia.management.kipia_management.models.Device;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.shape.Rectangle;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Инструменты для разработки - тестовые функции и отладка.
 * Класс содержит методы для тестирования компонентов приложения.
 * Функции доступны только в режиме разработки.
 *
 * @author vladimir_shi
 * @since 24.04.2026
 */
public class DevTools {

    /**
     * Проверяет, запущено ли приложение в режиме разработки
     * @return true если режим разработки, false если продакшен
     */
    public static boolean isDevelopmentMode() {
        return LoggingConfig.isDevelopmentMode();
    }

    /**
     * Создаёт панель с кнопками для тестирования диалогов
     *
     * @param onStatusUpdate callback для обновления статуса
     * @return VBox с кнопками тестирования
     */
    public static VBox createDialogTestPanel(java.util.function.Consumer<String> onStatusUpdate) {
        VBox testPanel = new VBox(10);
        testPanel.setStyle("-fx-padding: 20px; -fx-background-color: #f0f0f0;");

        // Info Dialog - используется в AddDeviceController, PhotoGalleryController, DeviceIconService, PhotoManager для информационных сообщений
        Button btnInfo = new Button("Show Info Dialog");
        btnInfo.setMaxWidth(Double.MAX_VALUE);
        btnInfo.setOnAction(_ -> CustomAlertDialog.showInfo("Информация", "Это информационное сообщение"));

        // Success Dialog - используется в SettingsController, SchemeEditorController, ReportsController, PhotoGalleryController, DevicesTableController для сообщений об успехе
        Button btnSuccess = new Button("Show Success Dialog");
        btnSuccess.setMaxWidth(Double.MAX_VALUE);
        btnSuccess.setOnAction(_ -> CustomAlertDialog.showSuccess("Успех", "Операция выполнена успешно"));

        // Warning Dialog - используется в SchemeEditorController, MainController, AddDeviceController, PhotoViewer, DeviceIconService для предупреждений
        Button btnWarning = new Button("Show Warning Dialog");
        btnWarning.setMaxWidth(Double.MAX_VALUE);
        btnWarning.setOnAction(_ -> CustomAlertDialog.showWarning("Предупреждение", "Внимание! Проверьте данные"));

        // Error Dialog - используется в SettingsController, SchemeEditorController, ReportsController, DeviceIconService, Main.java для сообщений об ошибках
        Button btnError = new Button("Show Error Dialog");
        btnError.setMaxWidth(Double.MAX_VALUE);
        btnError.setOnAction(_ -> CustomAlertDialog.showError("Ошибка", "Произошла ошибка"));

        // Confirm Dialog - используется в SettingsController, SchemeEditorController для подтверждения действий
        Button btnConfirm = new Button("Show Confirm Dialog");
        btnConfirm.setMaxWidth(Double.MAX_VALUE);
        btnConfirm.setOnAction(_ -> {
            boolean result = CustomAlertDialog.showConfirmation("Подтверждение", "Вы уверены?");
            if (onStatusUpdate != null) onStatusUpdate.accept("Confirm result: " + result);
        });

        // Choice Dialog - используется в PhotoGalleryController для выбора фото для удаления
        Button btnChoice = new Button("Show Choice Dialog");
        btnChoice.setMaxWidth(Double.MAX_VALUE);
        btnChoice.setOnAction(_ -> {
            List<String> choices = Arrays.asList("Вариант 1", "Вариант 2", "Вариант 3");
            CustomAlertDialog.showChoiceDialog("Выбор", "Выберите вариант", choices, "Вариант 1")
                    .ifPresent(r -> {
                        if (onStatusUpdate != null) onStatusUpdate.accept("Выбрано: " + r);
                    });
        });

        // Text Input Dialog - используется в SchemeEditorController для добавления текста, в TextShape для редактирования текста
        Button btnTextInput = new Button("Show Text Input Dialog");
        btnTextInput.setMaxWidth(Double.MAX_VALUE);
        btnTextInput.setOnAction(_ -> CustomAlertDialog.showTextInputDialog("Ввод текста", "Введите значение:", "Текст по умолчанию")
                .ifPresent(r -> {
                    if (onStatusUpdate != null) onStatusUpdate.accept("Введено: " + r);
                }));

        // Color Picker Dialog - используется в LineShape, TextShape, ShapeBase для изменения цвета линий, текста, контура, заливки
        Button btnColorPicker = new Button("Show Color Picker Dialog");
        btnColorPicker.setMaxWidth(Double.MAX_VALUE);
        btnColorPicker.setOnAction(_ -> CustomAlertDialog.showColorPickerDialog("Выбор цвета", Color.BLUE)
                .ifPresent(r -> {
                    if (onStatusUpdate != null) onStatusUpdate.accept("Выбран цвет: " + r);
                }));

        // Font Dialog - используется в TextShape для изменения шрифта текста
        Button btnFontDialog = new Button("Show Font Dialog");
        btnFontDialog.setMaxWidth(Double.MAX_VALUE);
        btnFontDialog.setOnAction(_ -> CustomAlertDialog.showFontDialog(Font.font("Arial", 14))
                .ifPresent(r -> {
                    if (onStatusUpdate != null) onStatusUpdate.accept("Выбран шрифт: " + r);
                }));

        // Loading Notification - используется в MainController, SchemeEditorController для уведомлений о сохранении
        Button btnLoading = new Button("Show Loading Notification");
        btnLoading.setMaxWidth(Double.MAX_VALUE);
        btnLoading.setOnAction(_ -> CustomAlertDialog.showSaveNotification("Загрузка...", 2.0));

        // Help Dialog - используется в MainController для отображения справки
        Button btnHelpDialog = new Button("Show Help Dialog");
        btnHelpDialog.setMaxWidth(Double.MAX_VALUE);
        btnHelpDialog.setOnAction(_ -> showHelpDialog());

        // Device Selection Dialog - используется в SchemeEditorController для выбора прибора для добавления на схему
        Button btnDeviceSelection = getBtnDeviceSelection(onStatusUpdate);

        testPanel.getChildren().addAll(
                btnInfo, btnSuccess, btnWarning, btnError,
                btnConfirm, btnChoice, btnTextInput,
                btnColorPicker, btnFontDialog, btnLoading,
                btnHelpDialog, btnDeviceSelection
        );

        return testPanel;
    }

    /**
     * Создаёт кнопку для тестирования диалога выбора прибора
     * @param onStatusUpdate callback для обновления статуса при выборе прибора
     * @return кнопка для показа диалога выбора прибора
     */
    private static Button getBtnDeviceSelection(Consumer<String> onStatusUpdate) {
        Button btnDeviceSelection = new Button("Show Device Selection Dialog");
        btnDeviceSelection.setMaxWidth(Double.MAX_VALUE);
        btnDeviceSelection.setOnAction(_ -> {
            // Тестовый список приборов для демонстрации
            Device device1 = new Device();
            device1.setType("Манометр");
            device1.setName("МП4-УУ2");
            device1.setInventoryNumber("ТМ-001");
            device1.setLocation("88 км Губкин");

            List<Device> testDevices = getTestDevices(device1);
            Device selectedDevice = CustomAlertDialog.showDeviceSelection(testDevices);
            if (selectedDevice != null && onStatusUpdate != null) {
                onStatusUpdate.accept("Выбран прибор: " + selectedDevice.getName() + " (" + selectedDevice.getInventoryNumber() + ")");
            }
        });
        return btnDeviceSelection;
    }

    /**
     * Создаёт тестовый список приборов для демонстрации диалога выбора
     * @param device1 первый прибор для добавления в список
     * @return список из трёх тестовых приборов
     */
    private static List<Device> getTestDevices(Device device1) {
        Device device2 = new Device();
        device2.setType("Термометр");
        device2.setName("ТС-100");
        device2.setInventoryNumber("ТМ-002");
        device2.setLocation("88 км Губкин");

        Device device3 = new Device();
        device3.setType("Датчик давления");
        device3.setName("ДД-50");
        device3.setInventoryNumber("ТМ-003");
        device3.setLocation("88 км Губкин");

        return Arrays.asList(device1, device2, device3);
    }

    /**
     * Показывает диалог справки для тестирования
     * Загружает FXML файл, создаёт окно справки с текущей темой и отображает его
     */
    private static void showHelpDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(DevTools.class.getResource("/views/help-dialog.fxml"));
            VBox root = loader.load();
            HelpController controller = loader.getController();

            Stage helpStage = new Stage();
            helpStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            helpStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            helpStage.setTitle("Справка");

            // Прозрачный фон сцены
            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);

            // Применяем текущую тему
            String currentTheme = StyleUtils.getCurrentTheme();
            scene.getStylesheets().add(
                    Objects.requireNonNull(DevTools.class.getResource(currentTheme)).toExternalForm()
            );

            helpStage.setScene(scene);
            helpStage.setResizable(true);
            helpStage.setMinWidth(550);
            helpStage.setMinHeight(300);

            // Скругление углов через clip
            Rectangle clip = new Rectangle();
            clip.setArcWidth(24);
            clip.setArcHeight(24);
            clip.widthProperty().bind(root.widthProperty());
            clip.heightProperty().bind(root.heightProperty());
            root.setClip(clip);

            controller.setStage(helpStage);
            controller.loadHelpContent("main");

            helpStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            CustomAlertDialog.showError("Ошибка", "Не удалось открыть окно справки: " + e.getMessage());
        }
    }
}
