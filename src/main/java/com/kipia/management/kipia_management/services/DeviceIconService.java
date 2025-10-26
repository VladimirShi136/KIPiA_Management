package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.utils.CustomAlert;
import javafx.scene.Node;
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

    // Константы для стилей
    private static final double DEFAULT_ICON_SIZE = 24.0;
    private static final double FALLBACK_CIRCLE_RADIUS = 10.0;
    private static final String DEFAULT_ICON_PATH = "/images/manometer.png";

    private Runnable onDeviceDeletedCallback;

    // Обнови конструкторы (добавить парметр)
    public DeviceIconService(AnchorPane schemePane, BiConsumer<Node, Device> onDeviceMovedCallback, DeviceLocationDAO deviceLocationDAO, Runnable onDeviceDeletedCallback) {
        this.schemePane = schemePane;
        this.onDeviceMovedCallback = onDeviceMovedCallback;
        this.deviceLocationDAO = deviceLocationDAO;
        this.onDeviceDeletedCallback = onDeviceDeletedCallback;
    }

    // Для обратной совместимости (добавь или оставь)
    public DeviceIconService(AnchorPane schemePane, BiConsumer<Node, Device> onDeviceMovedCallback) {
        this(schemePane, onDeviceMovedCallback, null, null);
    }

    /**
     * Создание иконки устройства на указанных координатах
     *
     * @param x координата X
     * @param y координата Y
     * @param device устройство для отображения
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
        icon.setLayoutX(x);
        icon.setLayoutY(y);
        icon.setCursor(Cursor.HAND);
        icon.setUserData(device);

        addMovementHandlers(icon);
        addContextMenu(icon, device, currentScheme);
    }

    /**
     * Добавление обработчиков перемещения для иконки
     */
    private void addMovementHandlers(Node node) {
        DragHandler dragHandler = new DragHandler(node, schemePane, onDeviceMovedCallback);
        dragHandler.attach();
    }

    /**
     * Добавление контекстного меню для иконки
     */
    private void addContextMenu(Node node, Device device, Scheme currentScheme) {
        ContextMenu contextMenu = createContextMenu(node, device, currentScheme);
        node.setOnContextMenuRequested(event -> {
            contextMenu.show(node, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    /**
     * Создание контекстного меню для устройства
     */
    private ContextMenu createContextMenu(Node node, Device device, Scheme currentScheme) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem deleteItem = createDeleteMenuItem(node, device, currentScheme);
        MenuItem infoItem = createInfoMenuItem(device);

        contextMenu.getItems().addAll(deleteItem, infoItem);
        return contextMenu;
    }

    /**
     * Создание пункта меню "Удалить"
     */
    private MenuItem createDeleteMenuItem(Node node, Device device, Scheme currentScheme) {
        MenuItem deleteItem = new MenuItem("Удалить прибор");
        deleteItem.setOnAction(event -> handleDeviceDeletion(node, device, currentScheme));
        return deleteItem;
    }

    /**
     * Обработка удаления устройства
     */
    private void handleDeviceDeletion(Node node, Device device, Scheme currentScheme) {
        LOGGER.fine("Удалить прибор: устройство '" + device.getName() + "'");

        boolean confirmed = CustomAlert.showConfirmation(
                "Подтверждение удаления",
                "Вы уверены, что хотите удалить прибор '" + device.getName() + "'?"
        );

        if (confirmed) {
            deleteDeviceFromScheme(node, device, currentScheme);
        }

        if (onDeviceDeletedCallback != null) {
            onDeviceDeletedCallback.run();  // Добавлено: мгновенно обновляет список
        }
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
    private MenuItem createInfoMenuItem(Device device) {
        MenuItem infoItem = new MenuItem("Показать информацию");
        infoItem.setOnAction(event -> showDeviceInfo(device));
        return infoItem;
    }

    /**
     * Показ информации об устройстве
     */
    private void showDeviceInfo(Device device) {
        LOGGER.fine("Показать информацию: устройство '" + device.getName() + "'");

        String infoText = buildDeviceInfoText(device);
        CustomAlert.showInfo(
                "Информация о приборе",
                "'" + device.getName() + "' (" + device.getInventoryNumber() + ")\n" + infoText
        );
    }

    /**
     * Формирование текста информации об устройстве
     */
    private String buildDeviceInfoText(Device device) {
        StringBuilder info = new StringBuilder();
        info.append("Место: ").append(device.getLocation()).append("\n");
        info.append("Статус: ").append(device.getStatus()).append("\n");
        info.append("Дополнительно: ");

        if (device.getAdditionalInfo() != null && !device.getAdditionalInfo().isEmpty()) {
            info.append(device.getAdditionalInfo());
        } else {
            info.append("нет");
        }

        return info.toString();
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

        public DragHandler(Node node, AnchorPane pane, BiConsumer<Node, Device> onMoveCallback) {
            this.node = node;
            this.pane = pane;
            this.onMoveCallback = onMoveCallback;
        }

        /**
         * Прикрепление обработчиков событий мыши
         */
        public void attach() {
            node.setOnMousePressed(this::handleMousePressed);
            node.setOnMouseDragged(this::handleMouseDragged);
            node.setOnMouseReleased(this::handleMouseReleased);
        }

        private void handleMousePressed(javafx.scene.input.MouseEvent event) {
            if (event.isPrimaryButtonDown()) {
                double centerOffsetX = calculateCenterOffsetX();
                double centerOffsetY = calculateCenterOffsetY();

                dragOffsetX = event.getSceneX() - (node.getLayoutX() + centerOffsetX);
                dragOffsetY = event.getSceneY() - (node.getLayoutY() + centerOffsetY);
                isDragging = false;
                node.setCursor(Cursor.MOVE);
            }
        }

        private void handleMouseDragged(javafx.scene.input.MouseEvent event) {
            if (!event.isPrimaryButtonDown()) return;

            double newX = calculateNewPositionX(event.getSceneX());
            double newY = calculateNewPositionY(event.getSceneY());

            node.setLayoutX(newX);
            node.setLayoutY(newY);
            isDragging = true;
        }

        private void handleMouseReleased(javafx.scene.input.MouseEvent event) {
            if (!event.isPrimaryButtonDown()) return;

            node.setCursor(Cursor.HAND);

            if (isDragging && onMoveCallback != null) {
                Device device = (Device) node.getUserData();
                onMoveCallback.accept(node, device);
            }

            isDragging = false;
        }

        private double calculateCenterOffsetX() {
            return (node instanceof ImageView) ? DEFAULT_ICON_SIZE / 2 : FALLBACK_CIRCLE_RADIUS;
        }

        private double calculateCenterOffsetY() {
            return (node instanceof ImageView) ? DEFAULT_ICON_SIZE / 2 : FALLBACK_CIRCLE_RADIUS;
        }

        private double calculateNewPositionX(double sceneX) {
            double newX = sceneX - dragOffsetX;
            double maxX = pane.getWidth() -
                    ((node instanceof ImageView) ? DEFAULT_ICON_SIZE : FALLBACK_CIRCLE_RADIUS * 2);
            return Math.max(0, Math.min(newX, maxX));
        }

        private double calculateNewPositionY(double sceneY) {
            double newY = sceneY - dragOffsetY;
            double maxY = pane.getHeight() -
                    ((node instanceof ImageView) ? DEFAULT_ICON_SIZE : FALLBACK_CIRCLE_RADIUS * 2);
            return Math.max(0, Math.min(newY, maxY));
        }
    }
}