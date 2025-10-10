package com.kipia.management.kipia_management;

import com.kipia.management.kipia_management.controllers.MainController;
import com.kipia.management.kipia_management.services.DatabaseService;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.DeviceLocationDAO;
import com.kipia.management.kipia_management.services.SchemeDAO;
import com.kipia.management.kipia_management.utils.CustomAlert;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import java.util.Objects;
import java.util.logging.Logger;

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
    // Поле для логирования
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    /**
     * Главный метод приложения
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        launch(args);
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
            // Инициализация сервисов базы данных
            initializeServices();
            // Загружаем FXML файл
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/main.fxml"));
            Parent root = loader.load();
            // Настраиваем сцену
            Scene scene = new Scene(root, 1000, 700);
            // Применяем стиль светлой темы
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/light-theme.css")).toExternalForm());
            // Получаем контроллер и передаем ему сервисы
            MainController controller = loader.getController();
            if (controller != null) {
                controller.setDeviceDAO(deviceDAO);
                controller.setSchemeDAO(schemeDAO);
                controller.setDeviceLocationDAO(deviceLocationDAO);
                controller.setScene(scene);  // Передаём Scene для темы
            }
            // Настраиваем главное окно
            primaryStage.setTitle("Система учёта приборов КИПиА");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            // Добавляем иконку
            try {
                Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/icon.png")));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                LOGGER.warning("Иконка не найдена, используется стандартная");  // Замена println на logger для consistent
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
            ButtonType result = CustomAlert.showAdvancedError("Ошибка запуска приложения", e.getMessage(), e);
            if (result == CustomAlert.RETRY_BUTTON) {
                handleRetry("Ошибка запуска приложения");
            } else if (result == CustomAlert.CANCEL_BUTTON) {
                handleCancel("Ошибка запуска приложения");
            }
            // OK — ничего
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
            schemeDAO = new SchemeDAO(databaseService);
            deviceLocationDAO = new DeviceLocationDAO(databaseService);
            // Добавляем тестовые данные если таблицы пустые
            databaseService.addTestData();
            LOGGER.info("Сервисы базы данных успешно инициализированы");  // Замена println на logger для consistent
        } catch (Exception e) {
            ButtonType result = CustomAlert.showAdvancedError("Ошибка базы данных", "Не удалось подключиться к базе данных", e);
            if (result == CustomAlert.RETRY_BUTTON) {
                handleRetry("Ошибка базы данных");
            } else if (result == CustomAlert.CANCEL_BUTTON) {
                handleCancel("Ошибка базы данных");
            }
            // OK — ничего
        }
    }

    /**
     * Обработка повторного запуска приложения
     *
     * @param context контекст ошибки
     */
    private static void handleRetry(String context) {
        if ("Ошибка базы данных".equals(context)) {
            // Повтор инициализации сервисов (с защитой от бесконечного цикла)
            Platform.runLater(() -> {
                Main app = new Main();
                try {
                    app.initializeServices();
                } catch (Exception e) {
                    // Замена: используем CustomAlert.showAdvancedError вместо showErrorDialog
                    ButtonType retryResult = CustomAlert.showAdvancedError("Ошибка базы данных", "Повторная инициализация не удалась", e);
                    if (retryResult == CustomAlert.RETRY_BUTTON) {
                        handleRetry("Ошибка базы данных");
                    }
                    // CANCEL/OK — не обрабатываем для упрощения
                }
            });
        } else if ("Ошибка запуска приложения".equals(context)) {
            // Перезапуск приложения
            Platform.runLater(() -> {
                try {
                    new Main().start(new Stage());
                } catch (Exception e) {
                    // Замена: используем CustomAlert.showAdvancedError вместо showErrorDialog
                    ButtonType retryResult = CustomAlert.showAdvancedError("Ошибка перезапуска", "Не удалось перезапустить приложение", e);
                    if (retryResult == CustomAlert.RETRY_BUTTON) {
                        handleRetry("Ошибка запуска приложения");
                    }
                    // CANCEL/OK — не обрабатываем
                }
            });
        } else {
            LOGGER.info("Повторение действия...");  // Замена println на logger
        }
    }

    /**
     * Обработка отмены действия
     *
     * @param context контекст ошибки
     */
    private static void handleCancel(String context) {
        if ("Ошибка запуска приложения".equals(context)) {
            Platform.exit();
            System.exit(0);
        } else {
            LOGGER.info("Отмена действия...");  // Замена println на logger
        }
    }
}