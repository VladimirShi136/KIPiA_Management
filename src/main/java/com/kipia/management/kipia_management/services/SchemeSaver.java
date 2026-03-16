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
    private final DeviceDAO deviceDAO;
    private final DeviceLocationDAO deviceLocationDAO;
    private final ShapeService shapeService;
    private final AnchorPane schemePane;

    // Флаг несохранённых изменений
    private boolean isDirty = false;

    public SchemeSaver(
            SchemeDAO schemeDAO,
            DeviceLocationDAO deviceLocationDAO,
            ShapeService shapeService,
            AnchorPane schemePane,
            DeviceDAO deviceDAO) {
        this.schemeDAO = schemeDAO;
        this.deviceLocationDAO = deviceLocationDAO;
        this.shapeService = shapeService;
        this.schemePane = schemePane;
        this.deviceDAO = deviceDAO;
    }

    // ─────────────────────────────────────────────
    // УПРАВЛЕНИЕ ФЛАГОМ ИЗМЕНЕНИЙ
    // ─────────────────────────────────────────────

    /** Помечает схему как изменённую — вызывается при любой мутации */
    public void markDirty() {
        isDirty = true;
    }

    /** Сбрасывает флаг после успешного сохранения или загрузки новой схемы */
    public void resetDirty() {
        isDirty = false;
    }

    public boolean isDirty() {
        return isDirty;
    }

    /**
     * Сохраняет текущую схему: данные фигур и позиции устройств.
     */
    public boolean saveScheme(Scheme scheme) {
        if (scheme == null) {
            LOGGER.warn("Попытка сохранить null-схему");
            return false;
        }
        if (!isDirty) {
            LOGGER.debug("Схема '{}' не изменена, сохранение пропущено", scheme.getName());
            return true;
        }
        try {
            scheme.updateTimestamp();
            saveSchemeData(scheme);
            saveDeviceLocations(scheme);
            resetDirty();
            LOGGER.info("Схема сохранена: {}, ID={}", scheme.getName(), scheme.getId());
            return true;
        } catch (Exception e) {
            LOGGER.error("Ошибка при сохранении схемы '{}': {}", scheme.getName(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Автосохранение перед сменой схемы.
     */
    public boolean saveBeforeSchemeChange(Scheme currentScheme) {
        if (currentScheme == null || !isDirty) {
            LOGGER.debug("saveBeforeSchemeChange: нечего сохранять");
            return true;
        }
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
        if (currentScheme == null || !isDirty) {
            LOGGER.debug("saveOnExit: нечего сохранять");
            return;
        }
        boolean saved = saveScheme(currentScheme);
        if (!saved) {
            LOGGER.error("Не удалось сохранить схему при выходе: {}", currentScheme.getName());
        }
    }

    /**
     * Сохранение перед переходом в другой контроллер.
     */
    public void saveBeforeNavigation(Scheme currentScheme) {
        if (currentScheme == null || !isDirty) {
            LOGGER.debug("saveBeforeNavigation: нечего сохранять");
            return;
        }
        boolean saved = saveScheme(currentScheme);
        if (!saved) {
            LOGGER.warn("Сохранение перед навигацией не удалось: {}", currentScheme.getName());
        }
    }

    /**
     * Сохранение схемы через кнопку сохранить.
     */
    public void selectButtonSaveScheme(Scheme scheme) {
        // Кнопка "Сохранить" работает всегда — принудительно помечаем dirty
        markDirty();
        saveScheme(scheme);
        CustomAlert.showAutoSaveNotification("Сохранение", 1.5);
    }

    // --- Вспомогательные методы ---

    private void saveSchemeData(Scheme scheme) {
        // Получаем JSON из ShapeService
        String schemeData = shapeService.serializeAllToJson();
        scheme.setData(schemeData);

        boolean updated = schemeDAO.updateScheme(scheme);
        if (!updated) {
            throw new RuntimeException("Не удалось обновить схему в БД (ID=" + scheme.getId() + ")");
        }

        LOGGER.info("Сохранено {} фигур в JSON", shapeService.getShapeCount());
    }

    private void saveDeviceLocations(Scheme scheme) {
        for (Node node : schemePane.getChildren()) {
            if (isDeviceNode(node)) {
                Device device = extractDeviceFromUserData(node.getUserData());
                if (device != null) {
                    saveDeviceLocation(node, device, scheme);
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

        if (saved) {
            device.updateTimestamp();
            if (deviceDAO != null) {
                deviceDAO.updateDevice(device);
            }
        } else {
            LOGGER.warn("Не удалось сохранить позицию устройства (ID={}) для схемы ID={}", device.getId(), scheme.getId());
        }
    }
}