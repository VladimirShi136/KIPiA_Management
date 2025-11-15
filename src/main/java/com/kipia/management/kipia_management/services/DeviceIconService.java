package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.models.DeviceLocation;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.utils.CustomAlert;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Сервис для создания и управления иконками устройств на схеме
 * Инкапсулирует логику создания, перемещения и контекстного меню устройств
 *
 * @author vladimir_shi
 * @since 25.10.2025
 */
public class DeviceIconService {

    private static final Logger LOGGER = Logger.getLogger(DeviceIconService.class.getName());

    private final AnchorPane schemePane;
    private final BiConsumer<Node, Device> onDeviceMovedCallback;
    private final DeviceLocationDAO deviceLocationDAO;
    private Scheme currentScheme; // Добавьте поле для текущей схемы

    // Константы для стилей
    private static final double DEFAULT_ICON_SIZE = 45.0;
    private static final double FALLBACK_CIRCLE_RADIUS = 10.0;
    private static final String DEFAULT_ICON_PATH = "/images/manometer.png";

    private final Runnable onDeviceDeletedCallback;

    // Обновите конструктор
    public DeviceIconService(AnchorPane schemePane,
                             BiConsumer<Node, Device> onDeviceMovedCallback,
                             DeviceLocationDAO deviceLocationDAO,
                             Runnable onDeviceDeletedCallback,
                             Scheme currentScheme) {  // Добавьте параметр
        this.schemePane = schemePane;
        this.onDeviceMovedCallback = onDeviceMovedCallback;
        this.deviceLocationDAO = deviceLocationDAO;
        this.onDeviceDeletedCallback = onDeviceDeletedCallback;
        this.currentScheme = currentScheme; // Сохраняем текущую схему
    }

    /**
     * Создание иконки устройства на указанных координатах
     *
     * @param x             координата X
     * @param y             координата Y
     * @param device        устройство для отображения
     * @param currentScheme текущая схема (может быть null)
     * @return созданный узел иконки устройства
     */
    public Node createDeviceIcon(double x, double y, Device device, Scheme currentScheme) {
        try {
            return createIconWithImage(x, y, device, currentScheme);
        } catch (Exception e) {
            LOGGER.warning("Не удалось создать иконку с изображением для '" +
                    device.getName() + "': " + e.getMessage());
            return createFallbackIcon(x, y, device, currentScheme);
        }
    }

    /**
     * Создание иконки с изображением
     */
    private Node createIconWithImage(double x, double y, Device device, Scheme currentScheme) {
        Image iconImage = loadDeviceImage();
        ImageView deviceIcon = createImageView(iconImage);
        configureIcon(deviceIcon, x, y, device, currentScheme);
        return deviceIcon;
    }

    /**
     * Создание резервной иконки (круг)
     */
    private Node createFallbackIcon(double x, double y, Device device, Scheme currentScheme) {
        CustomAlert.showWarning("Загрузка иконки",
                "Не удалось загрузить изображение для '" + device.getName() +
                        "'. Используется резервная круглая иконка.");

        Circle fallbackCircle = createFallbackCircle();
        configureIcon(fallbackCircle, x, y, device, currentScheme);
        return fallbackCircle;
    }

    /**
     * Загрузка изображения устройства
     */
    private Image loadDeviceImage() {
        return new Image(Objects.requireNonNull(
                getClass().getResourceAsStream(DEFAULT_ICON_PATH)
        ));
    }

    /**
     * Создание ImageView для иконки
     */
    private ImageView createImageView(Image image) {
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(DEFAULT_ICON_SIZE);
        imageView.setFitHeight(DEFAULT_ICON_SIZE);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        return imageView;
    }

    /**
     * Создание резервного круга
     */
    private Circle createFallbackCircle() {
        Circle circle = new Circle(FALLBACK_CIRCLE_RADIUS, Color.BLUE);
        circle.setStroke(Color.GRAY);
        circle.setStrokeWidth(1);
        return circle;
    }

    /**
     * Конфигурация иконки устройства
     */
    private void configureIcon(Node icon, double x, double y, Device device, Scheme currentScheme) {
        System.out.println("DEBUG: Configuring device icon - " + device.getName() +
                " at (" + x + ", " + y + ")");

        icon.setLayoutX(x);
        icon.setLayoutY(y);
        icon.setCursor(Cursor.HAND);

        // Сохраняем устройство с начальным углом поворота 0
        icon.setUserData(new DeviceWithRotation(device, 0.0));

        addMovementHandlers(icon);
        addContextMenu(icon, device, currentScheme);

        System.out.println("DEBUG: Device icon configured successfully");
    }

    /**
     * Сохранение позиции и угла поворота устройства
     */
    private void saveDevicePositionAndRotation(Node node, Device device) {
        System.out.println("DEBUG: Saving device position and rotation after rotation");

        if (currentScheme != null && deviceLocationDAO != null) {
            double x = node.getLayoutX();
            double y = node.getLayoutY();
            double rotation = node.getRotate();

            // Корректировка для Circle (центр вместо левого верхнего угла)
            if (node instanceof Circle) {
                x -= 10;
                y -= 10;
            }

            System.out.println("DEBUG: Saving device position after rotation - X: " + x + ", Y: " + y +
                    ", Rotation: " + rotation + "°, Scheme ID: " + currentScheme.getId() +
                    ", Device ID: " + device.getId());

            DeviceLocation location = new DeviceLocation(device.getId(), currentScheme.getId(), x, y, rotation);

            boolean saved = deviceLocationDAO.addDeviceLocation(location);

            if (saved) {
                System.out.println("DEBUG: Device position and rotation successfully saved to database after rotation");
            } else {
                System.out.println("DEBUG: FAILED to save device position and rotation after rotation!");
            }
        } else {
            System.out.println("DEBUG: Cannot save device position and rotation - " +
                    "scheme: " + (currentScheme != null) + ", DAO: " + (deviceLocationDAO != null));
        }
    }

    /**
     * Добавление обработчиков перемещения для иконки
     */
    private void addMovementHandlers(Node node) {
        // Создаем callbacks для отслеживания состояния
        Runnable onDragStart = () -> {
            System.out.println("DEBUG: Device drag started tracking");
            // Здесь можно добавить дополнительную логику при начале перемещения
        };

        Runnable onDragEnd = () -> {
            System.out.println("DEBUG: Device drag ended tracking");
            // Здесь можно добавить дополнительную логику при завершении перемещения
        };

        DragHandler dragHandler = new DragHandler(node, schemePane,
                onDeviceMovedCallback, onDragStart, onDragEnd);
        dragHandler.attach();
    }

    /**
     * Добавление контекстного меню для иконки
     */
    private void addContextMenu(Node node, Device device, Scheme currentScheme) {
        ContextMenu contextMenu = createContextMenu(node, device, currentScheme);
        node.setOnContextMenuRequested(event -> {
            contextMenu.show(node, event.getScreenX(), event.getScreenY());
            event.consume(); // ВАЖНО: предотвращаем всплытие события
        });
    }

    /**
     * Создание контекстного меню для устройства
     */
    private ContextMenu createContextMenu(Node node, Device device, Scheme currentScheme) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem deleteItem = createDeleteMenuItem(node, currentScheme);
        MenuItem infoItem = createInfoMenuItem(node);  // Передайте node

        // Добавляем пункты поворота
        Menu rotateMenu = createRotateMenu(node, device);

        contextMenu.getItems().addAll(deleteItem, infoItem, rotateMenu);
        return contextMenu;
    }

    /**
     * Создание подменю для поворота
     */
    private Menu createRotateMenu(Node node, Device device) {
        Menu rotateMenu = new Menu("Повернуть");

        MenuItem rotate0 = new MenuItem("0° (стандартно)");
        MenuItem rotate90 = new MenuItem("90° (вправо)");
        MenuItem rotate180 = new MenuItem("180° (перевернуть)");
        MenuItem rotate270 = new MenuItem("270° (влево)");

        rotate0.setOnAction(event -> rotateDeviceIcon(node, 0, device));
        rotate90.setOnAction(event -> rotateDeviceIcon(node, 90, device));
        rotate180.setOnAction(event -> rotateDeviceIcon(node, 180, device));
        rotate270.setOnAction(event -> rotateDeviceIcon(node, 270, device));

        rotateMenu.getItems().addAll(rotate0, rotate90, rotate180, rotate270);
        return rotateMenu;
    }

    /**
     * Поворот иконки устройства
     */
    private void rotateDeviceIcon(Node node, double angle, Device device) {
        System.out.println("DEBUG: Rotating device '" + device.getName() + "' to " + angle + " degrees");

        // Применяем поворот
        node.setRotate(angle);

        // Сохраняем угол поворота в UserData для последующего сохранения
        node.setUserData(new DeviceWithRotation(device, angle));

        // ВАЖНО: Сохраняем позицию и угол поворота в БД
        saveDevicePositionAndRotation(node, device);

        System.out.println("DEBUG: Device rotated to " + angle + " degrees, position and rotation saved");
    }

    /**
     * Создание пункта меню "Удалить"
     */
    private MenuItem createDeleteMenuItem(Node node, Scheme currentScheme) {
        MenuItem deleteItem = new MenuItem("Удалить прибор");
        deleteItem.setOnAction(event -> handleDeviceDeletion(node, currentScheme));
        return deleteItem;
    }

    /**
     * Обработка удаления устройства
     */
    private void handleDeviceDeletion(Node node, Scheme currentScheme) {
        // Получаем устройство из UserData (может быть Device или DeviceWithRotation)
        Device deviceToDelete = extractDeviceFromUserData(node);
        assert deviceToDelete != null;
        boolean confirmed = CustomAlert.showConfirmation(
                "Подтверждение удаления",
                "Вы уверены, что хотите удалить прибор '" + deviceToDelete.getName() + "'?"
        );

        if (confirmed) {
            deleteDeviceFromScheme(node, deviceToDelete, currentScheme);
        }

        if (onDeviceDeletedCallback != null) {
            onDeviceDeletedCallback.run();
        }
    }

    /**
     * Извлечение устройства из UserData (поддержка старого и нового формата)
     */
    private Device extractDeviceFromUserData(Node node) {
        Object userData = node.getUserData();
        if (userData instanceof DeviceWithRotation) {
            return ((DeviceWithRotation) userData).device();
        } else if (userData instanceof Device) {
            return (Device) userData;
        }
        return null;
    }

    /**
     * Удаление устройства со схемы
     */
    private void deleteDeviceFromScheme(Node node, Device device, Scheme currentScheme) {
        try {
            if (currentScheme != null && deviceLocationDAO != null) {
                deviceLocationDAO.deleteDeviceLocation(device.getId(), currentScheme.getId());
            }

            schemePane.getChildren().remove(node);
            LOGGER.info("Прибор '" + device.getName() + "' удален со схемы");
            CustomAlert.showInfo("Удаление", "Прибор '" + device.getName() + "' удалён со схемы");

        } catch (Exception e) {
            LOGGER.severe("Ошибка при удалении прибора '" + device.getName() + "': " + e.getMessage());
            CustomAlert.showError("Ошибка удаления", "Не удалось удалить прибор: " + e.getMessage());
        }
    }

    /**
     * Создание пункта меню "Информация"
     */
    private MenuItem createInfoMenuItem(Node node) {  // Добавьте параметр Node
        MenuItem infoItem = new MenuItem("Показать информацию");
        infoItem.setOnAction(event -> showDeviceInfo(node));  // Передайте node
        return infoItem;
    }

    /**
     * Показ информации об устройстве
     */
    private void showDeviceInfo(Node node) {  // Добавьте параметр Node
        Device deviceToShow = extractDeviceFromUserData(node);
        assert deviceToShow != null;
        String infoText = buildDeviceInfoText(deviceToShow);
        CustomAlert.showInfo(
                "Информация о приборе",
                "'" + deviceToShow.getName() + "' (" + deviceToShow.getInventoryNumber() + ")\n" + infoText
        );
    }

    /**
     * Формирование текста информации об устройстве
     */
    private String buildDeviceInfoText(Device device) {
        StringBuilder info = new StringBuilder();
        info.append("Место: ").append(device.getLocation()).append("\n");
        info.append("Кран: ").append(device.getValveNumber()).append("\n");
        info.append("Статус: ").append(device.getStatus()).append("\n");
        info.append("Дополнительно: ");

        if (device.getAdditionalInfo() != null && !device.getAdditionalInfo().isEmpty()) {
            info.append(device.getAdditionalInfo());
        } else {
            info.append("нет");
        }

        return info.toString();
    }

    // Метод для обновления текущей схемы
    public void setCurrentScheme(Scheme scheme) {
        this.currentScheme = scheme;
    }

    // ============================================================
    // ВНУТРЕННИЙ КЛАСС ДЛЯ ХРАНЕНИЯ УСТРОЙСТВА И ЕГО УГЛА ПОВОРОТА
    // ============================================================

    /**
         * Вспомогательный класс для хранения устройства и его угла поворота
         */
        public record DeviceWithRotation(Device device, double rotation) {
    }

    // ============================================================
    // INNER CLASS FOR DRAG HANDLING
    // ============================================================

    /**
     * Внутренний класс для обработки перемещения устройств
     */
    private static class DragHandler {
        private final Node node;
        private final AnchorPane pane;
        private final BiConsumer<Node, Device> onMoveCallback;
        private boolean isDragging = false;
        private double dragOffsetX, dragOffsetY;
        private double initialMouseX, initialMouseY;
        private double initialLayoutX, initialLayoutY;
        private final Runnable onDragStartCallback; // Новый callback
        private final Runnable onDragEndCallback;   // Новый callback

        public DragHandler(Node node, AnchorPane pane,
                           BiConsumer<Node, Device> onMoveCallback,
                           Runnable onDragStartCallback,  // Новый параметр
                           Runnable onDragEndCallback) {  // Новый параметр
            this.node = node;
            this.pane = pane;
            this.onMoveCallback = onMoveCallback;
            this.onDragStartCallback = onDragStartCallback;
            this.onDragEndCallback = onDragEndCallback;
        }

        /**
         * Прикрепление обработчиков событий мыши
         */
        public void attach() {
            System.out.println("DEBUG: Attaching drag handlers to device node - " +
                    node.getClass().getSimpleName());

            // Удаляем старые обработчики на всякий случай
            node.setOnMousePressed(null);
            node.setOnMouseDragged(null);
            node.setOnMouseReleased(null);

            // Прикрепляем новые обработчики
            node.setOnMousePressed(this::handleMousePressed);
            node.setOnMouseDragged(this::handleMouseDragged);
            node.setOnMouseReleased(this::handleMouseReleased);

            // Проверяем, что обработчики установлены
            System.out.println("DEBUG: Handlers attached - " +
                    "onMousePressed: " + (node.getOnMousePressed() != null) +
                    ", onMouseDragged: " + (node.getOnMouseDragged() != null) +
                    ", onMouseReleased: " + (node.getOnMouseReleased() != null));

            // Важно для улучшения отслеживания
            node.setOnDragDetected(event -> {
                System.out.println("DEBUG: Drag detected");
                node.startFullDrag();
                event.consume();
            });

            System.out.println("DEBUG: Drag handlers attached successfully");
        }

        private void handleMousePressed(javafx.scene.input.MouseEvent event) {
            if (event.isPrimaryButtonDown()) {
                // Получаем устройство из UserData (может быть Device или DeviceWithRotation)
                Object userData = node.getUserData();
                String deviceName;
                if (userData instanceof DeviceWithRotation) {
                    deviceName = ((DeviceWithRotation) userData).device().getName();
                } else if (userData instanceof Device) {
                    deviceName = ((Device) userData).getName();
                } else {
                    deviceName = "Unknown";
                }

                System.out.println("DEBUG: Device drag START - Device: " + deviceName);

                // Уведомляем о начале перемещения
                if (onDragStartCallback != null) {
                    onDragStartCallback.run();
                }

                initialMouseX = event.getSceneX();
                initialMouseY = event.getSceneY();
                initialLayoutX = node.getLayoutX();
                initialLayoutY = node.getLayoutY();

                double centerOffsetX = calculateCenterOffsetX();
                double centerOffsetY = calculateCenterOffsetY();

                dragOffsetX = event.getSceneX() - (node.getLayoutX() + centerOffsetX);
                dragOffsetY = event.getSceneY() - (node.getLayoutY() + centerOffsetY);

                isDragging = false;
                node.setCursor(Cursor.MOVE);
                event.consume();
            }
        }

        private void handleMouseDragged(javafx.scene.input.MouseEvent event) {
            if (!event.isPrimaryButtonDown()) return;

            // Используем ОТНОСИТЕЛЬНОЕ перемещение - это ключевое исправление!
            double deltaX = event.getSceneX() - initialMouseX;
            double deltaY = event.getSceneY() - initialMouseY;

            double newX = initialLayoutX + deltaX;
            double newY = initialLayoutY + deltaY;

            // Применяем ограничения
            newX = applyBoundsX(newX);
            newY = applyBoundsY(newY);

            // Немедленно обновляем позицию
            node.setLayoutX(newX);
            node.setLayoutY(newY);

            isDragging = true;

            // Лог при перемещении
            System.out.println("DEBUG: Device dragging - X: " + newX + ", Y: " + newY);

            event.consume(); // Критически важно!
        }


        private void handleMouseReleased(javafx.scene.input.MouseEvent event) {
            System.out.println("DEBUG: Device drag END - isDragging: " + isDragging);

            node.setCursor(Cursor.HAND);

            if (isDragging && onMoveCallback != null) {
                // Получаем устройство из UserData (может быть Device или DeviceWithRotation)
                Object userData = node.getUserData();
                Device device;
                if (userData instanceof DeviceWithRotation) {
                    device = ((DeviceWithRotation) userData).device();
                } else {
                    device = (Device) userData;
                }

                System.out.println("DEBUG: Calling onMoveCallback for device: " + device.getName());

                onMoveCallback.accept(node, device);
            }

            isDragging = false;
            event.consume();
        }


        /**
         * Применение границ по X с учетом реального размера pane
         */
        private double applyBoundsX(double x) {
            double nodeWidth = (node instanceof ImageView) ? DEFAULT_ICON_SIZE : FALLBACK_CIRCLE_RADIUS * 2;
            double maxX = pane.getWidth() - nodeWidth;
            return Math.max(0, Math.min(x, maxX));
        }

        /**
         * Применение границ по Y с учетом реального размера pane
         */
        private double applyBoundsY(double y) {
            double nodeHeight = (node instanceof ImageView) ? DEFAULT_ICON_SIZE : FALLBACK_CIRCLE_RADIUS * 2;
            double maxY = pane.getHeight() - nodeHeight;
            return Math.max(0, Math.min(y, maxY));
        }

        private double calculateCenterOffsetX() {
            return (node instanceof ImageView) ? DEFAULT_ICON_SIZE / 2 : FALLBACK_CIRCLE_RADIUS;
        }

        private double calculateCenterOffsetY() {
            return (node instanceof ImageView) ? DEFAULT_ICON_SIZE / 2 : FALLBACK_CIRCLE_RADIUS;
        }
    }
}