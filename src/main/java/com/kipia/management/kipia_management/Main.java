package com.kipia.management.kipia_management;

import com.kipia.management.kipia_management.controllers.MainController;
import com.kipia.management.kipia_management.services.DatabaseService;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.DeviceLocationDAO;
import com.kipia.management.kipia_management.services.SchemeDAO;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.logging.Level;
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
    private static final Logger logger = Logger.getLogger(Main.class.getName());

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
            scene.getStylesheets().add(getClass().getResource("/styles/light-theme.css").toExternalForm());

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
            showErrorDialog("Ошибка запуска приложения", e.getMessage(), e);
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

            System.out.println("Сервисы базы данных успешно инициализированы");

        } catch (Exception e) {
            showErrorDialog("Ошибка базы данных", "Не удалось подключиться к базе данных", e);
        }
    }

    /**
     * Отображение диалога ошибки
     *
     * @param title     заголовок ошибки
     * @param message   сообщение об ошибке
     * @param exception исключение (для stack trace и логирования)
     */
    private static void showErrorDialog(String title, String message, Throwable exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("Произошла ошибка!");
        alert.setContentText(message);

        // Кнопки: OK, Retry, Cancel
        ButtonType okButton = new ButtonType("OK");
        ButtonType retryButton = new ButtonType("Повторить");
        ButtonType cancelButton = new ButtonType("Отмена");
        alert.getButtonTypes().setAll(okButton, retryButton, cancelButton);

        // Expandable контент для подробностей (stack trace)
        if (exception != null) {
            TextArea textArea = new TextArea(exception.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);

            GridPane gridPane = new GridPane();
            gridPane.setMaxWidth(Double.MAX_VALUE);
            gridPane.add(textArea, 0, 0);

            alert.getDialogPane().setExpandableContent(gridPane);
            alert.setResizable(true);
        }

        // Логирование ошибки
        if (exception != null) {
            logger.log(Level.SEVERE, "Ошибка: " + message, exception);
        } else {
            logger.log(Level.SEVERE, "Ошибка: " + message);
        }

        // Показ диалога и обработка кнопок
        alert.showAndWait().ifPresent(buttonType -> {
            if (buttonType == retryButton) {
                handleRetry(title); // Передаем контекст для разных действий
            } else if (buttonType == cancelButton) {
                handleCancel(title);
            }
            // OK просто закрывает диалог
        });
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
                Main app = new Main(); // Создаем новый экземпляр для повторной инициализации
                try {
                    app.initializeServices(); // Повторяем только инициализацию
                } catch (Exception e) {
                    showErrorDialog("Ошибка базы данных", "Повторная инициализация не удалась", e);
                }
            });
        } else if ("Ошибка запуска приложения".equals(context)) {
            // Попытка перезапуска приложения
            Platform.runLater(() -> {
                try {
                    new Main().start(new Stage()); // Перезапускаем приложение
                } catch (Exception e) {
                    showErrorDialog("Ошибка перезапуска", "Не удалось перезапустить приложение", e);
                }
            });
        } else {
            System.out.println("Повторение действия...");
        }
    }

    /**
     * Обработка отмены действия
     *
     * @param context контекст ошибки
     */
    private static void handleCancel(String context) {
        if ("Ошибка запуска приложения".equals(context)) {
            // Закрываем приложение
            Platform.exit();
            System.exit(0);
        } else {
            System.out.println("Отмена действия...");
        }
    }
}