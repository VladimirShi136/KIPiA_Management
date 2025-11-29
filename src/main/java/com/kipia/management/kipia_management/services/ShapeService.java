package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.shapes.ShapeBase;
import com.kipia.management.kipia_management.shapes.ShapeFactory;
import com.kipia.management.kipia_management.shapes.ShapeHandler;
import com.kipia.management.kipia_management.shapes.ShapeType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author vladimir_shi
 * @since 25.10.2025
 */

public class ShapeService {
    private static final Logger LOGGER = LogManager.getLogger(ShapeService.class);

    private final Consumer<ShapeHandler> deleteAction;
    private final ShapeFactory factory;
    private final List<ShapeBase> shapes = new ArrayList<>();

    public ShapeService(ShapeFactory factory) {
        this.deleteAction = this::removeShape;
        this.factory = factory;
    }

    public ShapeBase addShape(ShapeType type, double... coordinates) {
        try {
            ShapeBase shape = factory.createShape(type, coordinates);
            if (shape != null) {
                shape.addToPane();
                shapes.add(shape);
                LOGGER.info("Фигура добавлена в сервис, количество: {}", shapes.size());
            }
            return shape;
        } catch (Exception e) {
            LOGGER.error("ОШИБКА в ShapeService.addShape: {}", e.getMessage(), e);
            e.printStackTrace();
            return null;
        }
    }

    public void removeShape(ShapeBase shape) {
        shapes.remove(shape);
        shape.removeFromPane();
    }

    public void removeShape(ShapeHandler shapeHandler) {
        if (shapeHandler instanceof ShapeBase) {
            removeShape((ShapeBase) shapeHandler);
        }
    }

    public void removeAllShapes() {
        new ArrayList<>(shapes).forEach(this::removeShape);
    }

    // Сериализация всех фигур
    public List<String> serializeAll() {
        return shapes.stream()
                .map(ShapeBase::serialize)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Десериализация и добавление всех фигур (с безопасными логами)
     */
    public void deserializeAndAddAll(List<String> shapeData) {
        if (shapeData == null || shapeData.isEmpty()) {
            LOGGER.info("Нет данных фигур для десериализации shapeData пустой");
            return;
        }
        int loaded = 0;
        int failed = 0;
        for (int i = 0; i < shapeData.size(); i++) {
            String data = shapeData.get(i);
            if (data != null && !data.trim().isEmpty()) {
                try {
                    ShapeBase shape = ShapeBase.deserialize(data.trim(),
                            factory.pane(), factory.statusSetter(), factory.onSelectCallback(), factory.shapeManager());
                    if (shape != null) {
                        shapes.add(shape);
                        shape.addToPane();
                        loaded++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                    String shortData = safeSubstring(data, 50);  // Безопасный!
                    LOGGER.error("ERROR: Ошибка десериализации фигуры #{}: '{}'", i + 1, shortData, e);
                    e.printStackTrace();  // Полный стек для анализа (в консоли)
                }
            } else {
                LOGGER.info("SKIP: Пустая data для фигуры #{}", i + 1);
            }
        }
        if (loaded > 0) {
            LOGGER.info("Успешно добавлены фигур: {}", loaded);
        } else if (failed == shapeData.size()) {
            LOGGER.error("Все фигур failed — проверь формат данных в БД");
        }
        LOGGER.info("Общее количество фигур в сервисе: {}", shapes.size());
    }

    /**
     * Safe для типа фигуры (добавь в ShapeService, если нужно)
     */
    private String safeSubstring(String str, int maxLen) {
        if (str == null || str.isEmpty()) {
            return "[пустая]";
        }
        if (str.length() <= maxLen) {
            return str;
        }
        return str.substring(0, maxLen) + "...";
    }

    /**
     * Очистка всех фигур (из list + pane) — вызывается при смене схемы
     */
    public void clearAllShapes() {
        if (shapes.isEmpty()) {
            LOGGER.info("No shapes to clear");
            return;
        }

        for (ShapeBase shape : shapes) {
            if (shape != null) {
                shape.removeFromPane();  // Удаляет из pane (pane.getChildren().remove(shape))
                shape.removeResizeHandles();  // Скрыть handles
            }
        }
        shapes.clear();  // Clear list
    }

    /**
     * Добавление фигуры в список (без добавления в pane — для undo/redo синхронности).
     * Используется в ShapeManager для команд undo.
     */
    public void addShapeToList(ShapeBase shape) {
        if (shape != null && !shapes.contains(shape)) {
            shapes.add(shape);
        }
    }

    /**
     * Удаление фигуры из списка (без удаления из pane — для undo/redo синхронности).
     * Используется в ShapeManager для команд execute/remove.
     */
    public void removeShapeFromList(ShapeBase shape) {
        boolean removed = shapes.remove(shape);
        if (removed) {
            LOGGER.info("Удалена фигура из списка, количество сейчас: {}", shapes.size());
        }
    }

    public int getShapeCount() {
        return shapes.size();
    }
}
