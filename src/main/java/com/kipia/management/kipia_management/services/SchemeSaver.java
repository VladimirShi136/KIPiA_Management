package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.models.DeviceLocation;
import com.kipia.management.kipia_management.models.Scheme;
import com.kipia.management.kipia_management.utils.CustomAlert;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Сервис для сохранения схемы, включая фигуры и позиции устройств.
 * Вынесен из SchemeEditorController.
 */
public class SchemeSaver {

    private static final Logger LOGGER = LogManager.getLogger(SchemeSaver.class);

    private final SchemeDAO schemeDAO;
    private final DeviceLocationDAO deviceLocationDAO;
    private final ShapeService shapeService;
    private final AnchorPane schemePane;

    public SchemeSaver(
            SchemeDAO schemeDAO,
            DeviceLocationDAO deviceLocationDAO,
            ShapeService shapeService,
            AnchorPane schemePane
    ) {
        this.schemeDAO = schemeDAO;
        this.deviceLocationDAO = deviceLocationDAO;
        this.shapeService = shapeService;
        this.schemePane = schemePane;
    }

    /**
     * Сохраняет текущую схему: данные фигур и позиции устройств.
     */
    public boolean saveScheme(Scheme scheme) {
        if (scheme == null) {
            LOGGER.warn("Попытка сохранить null-схему");
            return false;
        }
        try {
            saveSchemeData(scheme);
            saveDeviceLocations(scheme);
            LOGGER.info("Схема сохранена: {}, ID={}", scheme.getName(), scheme.getId());
            return true;
        } catch (Exception e) {
            LOGGER.error("Ошибка при сохранении схемы '{}': {}", scheme.getName(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Автосохранение перед сменой схемы.
     * Возвращает false, если сохранение не удалось (можно отменить смену схемы).
     */
    public boolean saveBeforeSchemeChange(Scheme currentScheme) {
        if (currentScheme == null) return true; // Нет текущей схемы — можно менять
        boolean saved = saveScheme(currentScheme);
        if (!saved) {
            LOGGER.warn("Не удалось сохранить схему перед сменой: {}", currentScheme.getName());
        }
        return saved;
    }

    /**
     * Сохранение при закрытии приложения.
     */
    public void saveOnExit(Scheme currentScheme) {
        if (currentScheme != null) {
            boolean saved = saveScheme(currentScheme);
            if (!saved) {
                LOGGER.error("Не удалось сохранить схему при выходе: {}", currentScheme.getName());
            }
        }
    }

    /**
     * Сохранение перед переходом в другой контроллер.
     * Возвращает true, если можно продолжать навигацию.
     */
    public void saveBeforeNavigation(Scheme currentScheme) {
        if (currentScheme == null) return;

        boolean saved = saveScheme(currentScheme);
        if (!saved) {
            LOGGER.warn("Сохранение перед навигацией не удалось: {}", currentScheme.getName());
        }
    }

    /**
     * Сохранение схемы через кнопку сохранить.
     * @param scheme - сохраняемая схема
     */
    public void selectButtonSaveScheme(Scheme scheme) {
        saveScheme(scheme);
        CustomAlert.showAutoSaveNotification("Сохранение", 1.5);
    }

    // --- Вспомогательные методы (прежние) ---

    private void saveSchemeData(Scheme scheme) {
        List<String> shapeData = shapeService.serializeAll();
        String schemeData = shapeData.isEmpty() ? "{}" : String.join(";", shapeData);

        scheme.setData(schemeData);
        boolean updated = schemeDAO.updateScheme(scheme);


        if (!updated) {
            throw new RuntimeException("Не удалось обновить схему в БД (ID=" + scheme.getId() + ")");
        }
    }

    private void saveDeviceLocations(Scheme scheme) {
        for (Node node : schemePane.getChildren()) {
            if (isDeviceNode(node)) {
                Device device = extractDeviceFromUserData(node.getUserData());
                if (device != null) {
                    saveDeviceLocation(node, device, scheme); // Теперь использует исправленную версию
                }
            }
        }
    }


    private boolean isDeviceNode(Node node) {
        return node.getUserData() != null &&
                (node.getUserData() instanceof Device ||
                        node.getUserData() instanceof DeviceIconService.DeviceWithRotation);
    }

    private Device extractDeviceFromUserData(Object userData) {
        if (userData instanceof Device) {
            return (Device) userData;
        } else if (userData instanceof DeviceIconService.DeviceWithRotation) {
            return ((DeviceIconService.DeviceWithRotation) userData).device();
        }
        return null;
    }

    public void saveDeviceLocation(Node node, Device device, Scheme scheme) {
        double x = node.getLayoutX();
        double y = node.getLayoutY();

        double rotation = node.getRotate();

        DeviceLocation location = new DeviceLocation(
                device.getId(),
                scheme.getId(),
                x,
                y,
                rotation
        );

        boolean saved = deviceLocationDAO.addDeviceLocation(location);
        if (!saved) {
            LOGGER.warn("Не удалось сохранить позицию устройства (ID={}) для схемы ID={}", device.getId(), scheme.getId());
        }
    }
}
