package com.kipia.management.kipia_management;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * @author vladimir_shi
 * @since 23.08.2025
 */

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Загружаем FXML файл
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/main.fxml"));
            Parent root = loader.load();

            // Настраиваем сцену
            Scene scene = new Scene(root, 1000, 700);

            // Настраиваем главное окно
            primaryStage.setTitle("Система учёта приборов КИПиА");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);

            // Добавляем иконку (создайте папку resources/images и положите туда icon.png)
            try {
                Image icon = new Image(getClass().getResourceAsStream("/images/icon.png"));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("Иконка не найдена, используется стандартная");
            }

            // Показываем окно
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Ошибка запуска приложения", e.getMessage());
        }
    }

    private void showErrorDialog(String title, String message) {
        // Простой вывод ошибки в консоль (позже заменим на диалоговое окно)
        System.err.println(title + ": " + message);
    }

    public static void main(String[] args) {
        // Запускаем JavaFX приложение
        launch(args);
    }
}