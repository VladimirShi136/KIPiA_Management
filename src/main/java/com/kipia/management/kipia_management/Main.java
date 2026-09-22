package com.kipia.management.kipia_management;

import com.kipia.management.kipia_management.controllers.MainController;
import com.kipia.management.kipia_management.managers.PhotoManager;
import com.kipia.management.kipia_management.managers.SyncManager;
import com.kipia.management.kipia_management.services.*;
import com.kipia.management.kipia_management.utils.CustomAlertDialog;
import com.kipia.management.kipia_management.utils.LoggingConfig;
import com.kipia.management.kipia_management.utils.TimeValidator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Главный класс приложения
 *
 * @author vladimir_shi
 * @since 28.08.2025
 */
public class Main extends Application {
    private DatabaseService databaseService;
    private DeviceDAO deviceDAO;
    private SchemeDAO schemeDAO;
    private DeviceLocationDAO deviceLocationDAO;
    private SyncManager syncManager;
    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private MainController mainController;
    private Stage primaryStage;
    private static FileLock lock;
    private static FileChannel channel;
    private static final String LOCK_FILE_NAME = "kipia_management.lock";
    private static final int APP_PORT = 54321;
    private static java.net.ServerSocket serverSocket;

    public static void main(String[] args) {
        LoggingConfig.initialize();
        LOGGER.info("Запуск главного метода приложения...");
        
        // Проверка на уже запущенный экземпляр до запуска JavaFX
        if (!acquireLock()) {
            LOGGER.warn("Приложение уже запущено. Завершение работы.");
            // Пытаемся отправить сигнал первому экземпляру для поднятия окна
            try {
                java.net.Socket socket = new java.net.Socket("localhost", APP_PORT);
                socket.close();
            } catch (Exception e) {
                LOGGER.debug("Не удалось отправить сигнал первому экземпляру: {}", e.getMessage());
            }
            System.exit(0);
        }
        
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

            // Запускаем сервер для приема сигналов от вторичных экземпляров
            startSignalServer();

            initializeServices();

            if (databaseService == null || deviceDAO == null) {
                LOGGER.error("Критические сервисы не инициализированы");
                showErrorAndExit("Критическая ошибка", "Не удалось инициализировать необходимые сервисы");
                return;
            }

            // Проверка системного времени — вызываем checkOnStartup(),
            // чтобы обнаружить аномалию сразу, а не при первой записи
            checkSystemTime();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/main.fxml"));
            Parent root = loader.load();

            mainController = loader.getController();
            if (mainController != null) {
                mainController.setDatabaseService();
                mainController.setDeviceDAO(deviceDAO);
                mainController.setSchemeDAO(schemeDAO);
                mainController.setDeviceLocationDAO(deviceLocationDAO);
                mainController.setSyncManager(syncManager);
                LOGGER.info("Все сервисы переданы в MainController");
            } else {
                LOGGER.warn("MainController не найден");
            }

            Scene scene = new Scene(root, 1000, 700);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            
            // Загружаем базовые стили с переменными темы
            for (String stylesheet : com.kipia.management.kipia_management.utils.StyleUtils.getBaseStylesheets()) {
                scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(stylesheet)).toExternalForm());
            }
            
            // Загружаем стили для всех экранов
            String[] screenStylesheets = {
                "/styles/devices.css",
                    "/styles/settings.css",
                "/styles/add-device.css",
                "/styles/schemes.css",
                "/styles/reports.css",
                "/styles/photo-gallery.css",
                "/styles/conflict-dialog.css",
                "/styles/help-dialog.css"
            };
            
            for (String stylesheet : screenStylesheets) {
                try {
                    scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(stylesheet)).toExternalForm());
                } catch (Exception e) {
                    LOGGER.warn("Не удалось загрузить stylesheet: {}", stylesheet);
                }
            }

            if (mainController != null) {
                mainController.setScene(scene);
            }

            primaryStage.initStyle(StageStyle.TRANSPARENT);
            primaryStage.setTitle("Система учёта приборов КИПиА");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);

            try {
                Image icon = new Image(Objects.requireNonNull(
                        getClass().getResourceAsStream("/images/iconApp.png")));
                primaryStage.getIcons().add(icon);
                LOGGER.info("Иконка приложения загружена");
            } catch (Exception e) {
                LOGGER.warn("Иконка не найдена: {}", e.getMessage());
            }

            primaryStage.setOnCloseRequest(_ -> {
                LOGGER.info("Закрытие приложения");
                if (mainController != null) {
                    mainController.saveSchemeBeforeNavigation();
                }
                if (databaseService != null) {
                    databaseService.closeConnection();
                    LOGGER.info("Соединение с БД закрыто");
                }
            });

            primaryStage.show();

            // Анимация появления окна при запуске
            if (mainController != null) {
                mainController.playLaunchAnimation(primaryStage);
            }

            LOGGER.info("Приложение успешно запущено");

        } catch (Exception e) {
            LOGGER.error("Ошибка запуска приложения: {}", e.getMessage(), e);
            showErrorAndRetry("Не удалось запустить приложение: " + e.getMessage());
        }
    }

    private void initializeServices() {
        try {
            LOGGER.info("🔄 Инициализация сервисов...");
            databaseService = new DatabaseService();
            deviceDAO = new DeviceDAO(databaseService);
            schemeDAO = new SchemeDAO(databaseService);
            deviceLocationDAO = new DeviceLocationDAO(databaseService);
            PhotoManager photoManager = PhotoManager.getInstance();
            photoManager.setDeviceDAO(deviceDAO);
            this.syncManager = new SyncManager(
                    databaseService, deviceDAO, schemeDAO,
                    deviceLocationDAO,
                    photoManager.getBasePhotosPath()
            );
            LOGGER.info("🎉 Все сервисы успешно инициализированы");
        } catch (Exception e) {
            LOGGER.error("❌ Ошибка инициализации сервисов: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось инициализировать сервисы приложения", e);
        }
    }

    /**
     * Проверяет системное время при старте.
     * Использует checkOnStartup() — он обнаруживает аномалию сразу,
     * а не только при первой попытке записи в БД.
     */
    private void checkSystemTime() {
        TimeValidator timeValidator = TimeValidator.getInstance();
        boolean timeOk = timeValidator.checkOnStartup();

        if (!timeOk) {
            String issueDescription = timeValidator.getTimeIssueDescription();
            LOGGER.warn("Обнаружена проблема с системным временем: {}", issueDescription);

            Platform.runLater(() ->
                    CustomAlertDialog.showWarning(
                            "Проблема с системным временем",
                            issueDescription + "\n\n" +
                                    "Операции записи данных будут заблокированы до устранения проблемы.\n" +
                                    "После коррекции времени перезапустите приложение."
                    )
            );
        } else {
            LOGGER.info("Системное время в порядке");
        }
    }

    private void showErrorAndRetry(String message) {
        Platform.runLater(() -> {
            ButtonType result = CustomAlertDialog.showAdvancedError("Ошибка запуска приложения", message, new Exception(message));
            if (result == CustomAlertDialog.RETRY_BUTTON) {
                LOGGER.info("Повторная попытка запуска...");
                handleRetry();
            } else if (result == CustomAlertDialog.CANCEL_BUTTON) {
                LOGGER.info("Отмена запуска приложения");
                handleCancel();
            }
        });
    }

    private void showErrorAndExit(String title, String message) {
        Platform.runLater(() -> {
            CustomAlertDialog.showError(title, message);
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
                if (primaryStage != null) primaryStage.close();
            } catch (Exception e) {
                LOGGER.error("Ошибка перезапуска: {}", e.getMessage(), e);
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
        if (databaseService != null) databaseService.closeConnection();
        releaseLock();
    }

    private static boolean acquireLock() {
        try {
            Path lockFilePath = Paths.get(System.getProperty("java.io.tmpdir"), LOCK_FILE_NAME);
            File lockFile = lockFilePath.toFile();
            
            if (!lockFile.exists()) {
                lockFile.createNewFile();
            }
            
            channel = new RandomAccessFile(lockFile, "rw").getChannel();
            lock = channel.tryLock();
            
            if (lock == null) {
                LOGGER.warn("Не удалось получить блокировку. Приложение уже запущено.");
                return false;
            }
            
            LOGGER.info("Блокировка получена успешно. Файл блокировки: {}", lockFilePath);
            return true;
        } catch (IOException e) {
            LOGGER.error("Ошибка при получении блокировки: {}", e.getMessage(), e);
            return false;
        }
    }

    private static void releaseLock() {
        try {
            if (lock != null) {
                lock.release();
                LOGGER.info("Блокировка освобождена");
            }
            if (channel != null) {
                channel.close();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                LOGGER.info("Серверный сокет закрыт");
            }
        } catch (IOException e) {
            LOGGER.error("Ошибка при освобождении блокировки: {}", e.getMessage(), e);
        }
    }

    /**
     * Запускает сервер для приема сигналов от вторичных экземпляров приложения.
     * При получении сигнала поднимает окно первого экземпляра на передний план.
     */
    private void startSignalServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(APP_PORT);
                LOGGER.info("Сервер сигналов запущен на порту {}", APP_PORT);
                
                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    LOGGER.info("Получен сигнал от вторичного экземпляра");
                    clientSocket.close();
                    
                    // Поднимаем окно на передний план
                    Platform.runLater(() -> {
                        if (primaryStage != null) {
                            primaryStage.setIconified(false);
                            primaryStage.show();
                            primaryStage.toFront();
                            primaryStage.requestFocus();
                            
                            // Показываем кастомное уведомление поверх окна
                            CustomAlertDialog.showWarning(
                                "Попытка повторного запуска",
                                "Обнаружена попытка запуска второго экземпляра приложения.\n\n" +
                                "Запуск нескольких экземпляров не поддерживается."
                            );
                        }
                    });
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    LOGGER.error("Ошибка сервера сигналов: {}", e.getMessage(), e);
                }
            }
        }).start();
    }
}