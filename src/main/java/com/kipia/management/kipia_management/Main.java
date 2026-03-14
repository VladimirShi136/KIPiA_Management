package com.kipia.management.kipia_management;

import com.kipia.management.kipia_management.controllers.MainController;
import com.kipia.management.kipia_management.managers.PhotoManager;
import com.kipia.management.kipia_management.managers.SyncManager;
import com.kipia.management.kipia_management.services.*;
import com.kipia.management.kipia_management.utils.CustomAlert;
import com.kipia.management.kipia_management.utils.LoggingConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

public class Main extends Application {
    private DatabaseService databaseService;
    private DeviceDAO deviceDAO;
    private SchemeDAO schemeDAO;
    private DeviceLocationDAO deviceLocationDAO;
    private SyncManager syncManager;
    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private MainController mainController;
    private Stage primaryStage;

    public static void main(String[] args) {
        LoggingConfig.initialize();
        LOGGER.info("Запуск главного метода приложения...");
        try {
            launch(args);
        } catch (Exception e) {
            LOGGER.fatal("Критическая ошибка при запуске приложения: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    @Override
    public void init() {
        LOGGER.info("Инициализация приложения...");
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            this.primaryStage = primaryStage;
            LOGGER.info("Запуск приложения...");

            // Инициализация сервисов базы данных
            initializeServices();

            // Проверяем что критические сервисы инициализированы
            if (databaseService == null || deviceDAO == null) {
                LOGGER.error("Критические сервисы не инициализированы");
                showErrorAndExit("Критическая ошибка", "Не удалось инициализировать необходимые сервисы");
                return;
            }

            // Загружаем FXML файл
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/main.fxml"));
            Parent root = loader.load();

            // Получаем контроллер и передаем ему сервисы
            mainController = loader.getController();
            if (mainController != null) {
                // ⭐⭐ ИЗМЕНЕНИЕ: Теперь передаем DatabaseService вместо отдельных DAO ⭐⭐
                mainController.setDatabaseService(databaseService);

                // Но также передаем и отдельные DAO для совместимости
                mainController.setDeviceDAO(deviceDAO);
                mainController.setSchemeDAO(schemeDAO);
                mainController.setDeviceLocationDAO(deviceLocationDAO);
                mainController.setSyncManager(syncManager);

                LOGGER.info("Все сервисы переданы в MainController");
            } else {
                LOGGER.warn("MainController не найден");
            }

            // Настраиваем сцену
            Scene scene = new Scene(root, 1000, 700);
            scene.getStylesheets().add(Objects.requireNonNull(
                    getClass().getResource("/styles/light-theme.css")).toExternalForm());

            if (mainController != null) {
                mainController.setScene(scene);
            }

            // Настраиваем главное окно
            primaryStage.setTitle("Система учёта приборов КИПиА");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);

            // Добавляем иконку
            try {
                Image icon = new Image(Objects.requireNonNull(
                        getClass().getResourceAsStream("/images/iconApp.png")));
                primaryStage.getIcons().add(icon);
                LOGGER.info("Иконка приложения загружена");
            } catch (Exception e) {
                LOGGER.warn("Иконка не найдена, используется стандартная: {}", e.getMessage());
            }

            // Обработка закрытия окна
            primaryStage.setOnCloseRequest(_ -> {
                LOGGER.info("Закрытие приложения - сохранение схемы");
                if (mainController != null) {
                    mainController.saveSchemeBeforeNavigation();
                }
                if (databaseService != null) {
                    databaseService.closeConnection();
                    LOGGER.info("Соединение с БД закрыто");
                }
            });

            // Показываем окно
            primaryStage.show();
            LOGGER.info("Приложение успешно запущено");

        } catch (Exception e) {
            LOGGER.error("Ошибка запуска приложения: {}", e.getMessage(), e);
            showErrorAndRetry("Не удалось запустить приложение: " + e.getMessage());
        }
    }

    /**
     * Инициализация сервисов базы данных - ОБНОВЛЕННЫЙ
     */
    private void initializeServices() {
        try {
            LOGGER.info("🔄 Инициализация сервисов...");

            databaseService = new DatabaseService();
            LOGGER.info("✅ DatabaseService инициализирован");

            deviceDAO = new DeviceDAO(databaseService);
            LOGGER.info("✅ DeviceDAO инициализирован");

            schemeDAO = new SchemeDAO(databaseService);
            LOGGER.info("✅ SchemeDAO инициализирован");

            deviceLocationDAO = new DeviceLocationDAO(databaseService);
            LOGGER.info("✅ DeviceLocationDAO инициализирован");

            // ⭐⭐ ИНИЦИАЛИЗИРУЕМ PhotoManager как синглтон ⭐⭐
            PhotoManager photoManager = PhotoManager.getInstance();
            photoManager.setDeviceDAO(deviceDAO); // Для автосохранения в БД
            LOGGER.info("✅ PhotoManager инициализирован");

            this.syncManager = new SyncManager(
                    databaseService, deviceDAO, schemeDAO,
                    deviceLocationDAO,
                    photoManager.getBasePhotosPath()
            );
            LOGGER.info("✅ SyncManager инициализирован");

            LOGGER.info("🎉 Все сервисы успешно инициализированы");

        } catch (Exception e) {
            LOGGER.error("❌ Ошибка инициализации сервисов: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось инициализировать сервисы приложения", e);
        }
    }

    private void showErrorAndRetry(String message) {
        Platform.runLater(() -> {
            ButtonType result = CustomAlert.showAdvancedError("Ошибка запуска приложения", message, new Exception(message));
            if (result == CustomAlert.RETRY_BUTTON) {
                LOGGER.info("Повторная попытка запуска...");
                handleRetry();
            } else if (result == CustomAlert.CANCEL_BUTTON) {
                LOGGER.info("Отмена запуска приложения");
                handleCancel();
            }
        });
    }

    private void showErrorAndExit(String title, String message) {
        Platform.runLater(() -> {
            CustomAlert.showError(title, message);
            Platform.exit();
            System.exit(1);
        });
    }

    private void handleRetry() {
        Platform.runLater(() -> {
            try {
                LOGGER.info("Перезапуск приложения...");
                Main newApp = new Main();
                newApp.start(new Stage());
                if (primaryStage != null) {
                    primaryStage.close();
                }
            } catch (Exception e) {
                LOGGER.error("Ошибка перезапуска приложения: {}", e.getMessage(), e);
                showErrorAndExit("Ошибка перезапуска", "Не удалось перезапустить приложение: " + e.getMessage());
            }
        });
    }

    private void handleCancel() {
        LOGGER.info("Завершение работы приложения...");
        Platform.exit();
        System.exit(0);
    }

    @Override
    public void stop() {
        LOGGER.info("Приложение завершает работу");
        if (databaseService != null) {
            databaseService.closeConnection();
        }
    }
}