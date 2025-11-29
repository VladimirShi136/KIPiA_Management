package com.kipia.management.kipia_management;

import com.kipia.management.kipia_management.controllers.MainController;
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

/**
 * Главный класс приложения "Система учёта приборов КИПиА"
 *
 * @author vladimir_shi
 * @since 23.08.2025
 */
public class Main extends Application {
    // Сервисы базы данных
    private DatabaseService databaseService;
    private DeviceDAO deviceDAO;
    private SchemeDAO schemeDAO;
    private DeviceLocationDAO deviceLocationDAO;
    // Логгер для сообщений
    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private MainController mainController;
    private Stage primaryStage;

    /**
     * Главный метод приложения
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        // Инициализация логгера ПЕРВОЙ - ИСПРАВЛЕНО
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
        // Инициализация ДО создания UI - ИСПРАВЛЕНО
        LOGGER.info("Инициализация приложения...");
    }

    /**
     * @param primaryStage the primary stage for this application, onto which
     *                     the application scene can be set.
     *                     Applications may create other stages, if needed, but they will not be
     *                     primary stages.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            this.primaryStage = primaryStage;

            LOGGER.info("Запуск приложения...");

            // Инициализация сервисов базы данных ДО загрузки UI
            initializeServices();

            // Проверяем что критические сервисы инициализированы
            if (databaseService == null) {
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
                mainController.setDeviceDAO(deviceDAO);
                mainController.setSchemeDAO(schemeDAO);
                mainController.setDeviceLocationDAO(deviceLocationDAO);
                LOGGER.info("Сервисы переданы в MainController");
            } else {
                LOGGER.warn("MainController не найден");
            }

            // Настраиваем сцену
            Scene scene = new Scene(root, 1000, 700);
            // Применяем стиль светлой темы
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/light-theme.css")).toExternalForm());

            if (mainController != null) {
                mainController.setScene(scene);  // Передаём Scene для темы
            }

            // Настраиваем главное окно
            primaryStage.setTitle("Система учёта приборов КИПиА");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);

            // Добавляем иконку
            try {
                Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/iconApp.png")));
                primaryStage.getIcons().add(icon);
                LOGGER.debug("Иконка приложения загружена");
            } catch (Exception e) {
                LOGGER.warn("Иконка не найдена, используется стандартная: {}", e.getMessage());
            }

            // Обработка закрытия окна - закрываем соединение с БД и сохраняем схему
            primaryStage.setOnCloseRequest(_ -> {
                LOGGER.info("Закрытие приложения - сохранение схемы");
                // Сохраняем схему через MainController
                if (mainController != null) {
                    mainController.saveSchemeBeforeNavigation();
                }
                // Закрываем соединение с БД
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
            showErrorAndRetry("Ошибка запуска приложения",
                    "Не удалось запустить приложение: " + e.getMessage());
        }
    }

    /**
     * Инициализация сервисов базы данных
     */
    private void initializeServices() {
        try {
            LOGGER.info("Инициализация сервисов...");

            // Создаем сервис для работы с базой данных
            databaseService = new DatabaseService();
            LOGGER.info("DatabaseService инициализирован");

            // Создаем DAO для работы с приборами
            deviceDAO = new DeviceDAO(databaseService);
            LOGGER.info("DeviceDAO инициализирован");

            schemeDAO = new SchemeDAO(databaseService);
            LOGGER.info("SchemeDAO инициализирован");

            deviceLocationDAO = new DeviceLocationDAO(databaseService);
            LOGGER.info("DeviceLocationDAO инициализирован");

            LOGGER.info("Все сервисы успешно инициализированы");

        } catch (Exception e) {
            LOGGER.error("Ошибка инициализации сервисов: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось инициализировать сервисы приложения", e);
        }
    }

    /**
     * Показать ошибку и предложить повтор
     */
    private void showErrorAndRetry(String title, String message) {
        Platform.runLater(() -> {
            ButtonType result = CustomAlert.showAdvancedError(title, message, new Exception(message));
            if (result == CustomAlert.RETRY_BUTTON) {
                LOGGER.info("Повторная попытка запуска...");
                handleRetry();
            } else if (result == CustomAlert.CANCEL_BUTTON) {
                LOGGER.info("Отмена запуска приложения");
                handleCancel();
            }
        });
    }

    /**
     * Показать ошибку и выйти
     */
    private void showErrorAndExit(String title, String message) {
        Platform.runLater(() -> {
            CustomAlert.showError(title, message);
            Platform.exit();
            System.exit(1);
        });
    }

    /**
     * Обработка повторного запуска приложения
     */
    private void handleRetry() {
        Platform.runLater(() -> {
            try {
                LOGGER.info("Перезапуск приложения...");
                // Создаем новый экземпляр приложения
                Main newApp = new Main();
                newApp.start(new Stage());

                // Закрываем текущее окно если оно есть
                if (primaryStage != null) {
                    primaryStage.close();
                }
            } catch (Exception e) {
                LOGGER.error("Ошибка перезапуска приложения: {}", e.getMessage(), e);
                showErrorAndExit("Ошибка перезапуска", "Не удалось перезапустить приложение: " + e.getMessage());
            }
        });
    }

    /**
     * Обработка отмены действия
     */
    private void handleCancel() {
        LOGGER.info("Завершение работы приложения...");
        Platform.exit();
        System.exit(0);
    }

    @Override
    public void stop() {
        LOGGER.info("Приложение завершает работу");
        // Дополнительная очистка ресурсов
        if (databaseService != null) {
            databaseService.closeConnection();
        }
    }
}