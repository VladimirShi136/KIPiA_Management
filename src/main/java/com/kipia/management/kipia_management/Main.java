package com.kipia.management.kipia_management;

import com.kipia.management.kipia_management.controllers.MainController;
import com.kipia.management.kipia_management.services.DatabaseService;
import com.kipia.management.kipia_management.services.DeviceDAO;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Главный класс приложения "Система учёта приборов КИПиА"
 *
 * @author vladimir_shi
 * @since 23.08.2025
 */
public class Main extends Application {

    // Сервисы приложения (могут быть использованы в контроллерах)
    private DatabaseService databaseService;
    private DeviceDAO deviceDAO;

    @Override
    public void start(Stage primaryStage) {
        try {
            // Инициализация сервисов базы данных
            initializeServices();

            // Загружаем FXML файл
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/main.fxml"));
            Parent root = loader.load();

            // Получаем контроллер и передаем ему сервисы
            MainController controller = loader.getController();
            if (controller != null) {
                controller.setDeviceDAO(deviceDAO);
            }

            // Настраиваем сцену
            Scene scene = new Scene(root, 1000, 700);

            // Настраиваем главное окно
            primaryStage.setTitle("Система учёта приборов КИПиА");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);

            // Добавляем иконку
            try {
                Image icon = new Image(getClass().getResourceAsStream("/images/icon.png"));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("Иконка не найдена, используется стандартная");
            }

            // Обработка закрытия окна - закрываем соединение с БД
            primaryStage.setOnCloseRequest(event -> {
                if (databaseService != null) {
                    databaseService.closeConnection();
                }
            });

            // Показываем окно
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Ошибка запуска приложения", e.getMessage());
        }
    }

    /**
     * Инициализация сервисов базы данных
     */
    private void initializeServices() {
        try {
            // Создаем сервис для работы с базой данных
            databaseService = new DatabaseService();

            // Инициализируем базу данных (создаем таблицы если их нет)
            databaseService.createTables();

            // Проверяем создались ли таблицы
            if (!databaseService.tablesExist()) {
                throw new RuntimeException("Таблицы не были созданы!");
            }

            // Создаем DAO для работы с приборами
            deviceDAO = new DeviceDAO(databaseService);

            // Добавляем тестовые данные если таблицы пустые
            databaseService.addTestData();

            System.out.println("Сервисы базы данных успешно инициализированы");

        } catch (Exception e) {
            System.err.println("Ошибка инициализации сервисов базы данных: " + e.getMessage());
            e.printStackTrace();
            showErrorDialog("Ошибка базы данных", "Не удалось подключиться к базе данных");
        }
    }

    /**
     * Отображение диалога ошибки (временная реализация)
     * @param title заголовок ошибки
     * @param message сообщение об ошибке
     */
    private void showErrorDialog(String title, String message) {
        System.err.println(title + ": " + message);
        // TODO: заменить на JavaFX Alert dialog
    }

    public static void main(String[] args) {
        // Запускаем JavaFX приложение
        launch(args);
    }
}