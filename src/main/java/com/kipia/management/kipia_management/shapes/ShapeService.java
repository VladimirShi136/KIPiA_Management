package com.kipia.management.kipia_management.shapes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * @author vladimir_shi
 * @since 25.10.2025
 */

public class ShapeService {
    private static final Logger LOGGER = Logger.getLogger(ShapeService.class.getName());

    private final ShapeFactory factory;
    private final List<ShapeBase> shapes = new ArrayList<>();

    public ShapeService(ShapeFactory factory) {
        this.factory = factory;
    }

    public ShapeBase addShape(ShapeType type, double... coordinates) {
        ShapeBase shape = factory.createShape(type, coordinates);
        shapes.add(shape);
        shape.addToPane();
        System.out.println("DEBUG: addShape type=" + type + ", coords=" + Arrays.toString(coordinates));
        return shape;
    }

    public void removeShape(ShapeBase shape) {
        shapes.remove(shape);
        shape.removeFromPane();
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
            System.out.println("Нет данных фигур для десериализации (shapeData пустой)");
            return;
        }

        int loaded = 0;
        int failed = 0;
        System.out.println("Начинаем десериализацию " + shapeData.size() + " фигур...");

        for (int i = 0; i < shapeData.size(); i++) {
            String data = shapeData.get(i);
            if (data != null && !data.trim().isEmpty()) {
                try {
                    ShapeBase shape = ShapeBase.deserialize(data.trim(),
                            factory.getPane(), factory.getStatusSetter(), factory.getOnSelectCallback(), factory.getShapeManager());

                    if (shape != null) {
                        shapes.add(shape);
                        shape.addToPane();
                        loaded++;
                        System.out.println("OK: Десериализована фигура #" + (i+1) + " (" + safeSubstring(shape.getShapeType(), 20) + ") на позицию (" +
                                String.format("%.1f, %.1f", shape.getLayoutX(), shape.getLayoutY()) + ")");
                    } else {
                        failed++;
                        String shortData = safeSubstring(data, 50);  // Безопасный!
                        System.out.println("FAIL: Не удалось десериализовать фигуру #" + (i+1) + ": '" + shortData + "' (null shape)");
                    }
                } catch (Exception e) {
                    failed++;
                    String shortData = safeSubstring(data, 50);  // Безопасный!
                    System.out.println("ERROR: Ошибка десериализации фигуры #" + (i+1) + ": '" + shortData + "'");
                    System.out.println("  Детали: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    e.printStackTrace();  // Полный стек для анализа (в консоли)
                }
            } else {
                System.out.println("SKIP: Пустая data для фигуры #" + (i+1));
            }
        }

        System.out.println("Итог десериализации: OK=" + loaded + ", FAIL=" + failed + ", всего=" + shapeData.size());
        if (loaded > 0) {
            System.out.println("Успешно добавлены фигур: " + loaded);
        } else if (failed == shapeData.size()) {
            System.out.println("Все фигур failed — проверь формат данных в БД (возможно, ',' вместо '.'). Очисти scheme.data='{}' и пересохрани.");
        }
        System.out.println("Общее количество фигур в сервисе: " + shapes.size());
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
            System.out.println("No shapes to clear");  // Или LOGGER
            return;
        }

        for (ShapeBase shape : shapes) {
            if (shape != null) {
                shape.removeFromPane();  // Удаляет из pane (pane.getChildren().remove(shape))
                shape.removeResizeHandles();  // Скрыть handles
            }
        }
        shapes.clear();  // Clear list

        System.out.println("Cleared " + shapes.size() + " old shapes from pane/service");  // Лог (удали после)
    }

    /**
     * Добавление фигуры в список (без добавления в pane — для undo/redo синхронности).
     * Используется в ShapeManager для команд undo.
     */
    public void addShapeToList(ShapeBase shape) {
        if (shape != null && !shapes.contains(shape)) {
            shapes.add(shape);
            System.out.println("DEBUG: Added shape to list, count now: " + shapes.size());
        }
    }

    /**
     * Удаление фигуры из списка (без удаления из pane — для undo/redo синхронности).
     * Используется в ShapeManager для команд execute/remove.
     */
    public boolean removeShapeFromList(ShapeBase shape) {
        boolean removed = shapes.remove(shape);
        if (removed) {
            System.out.println("DEBUG: Removed shape from list, count now: " + shapes.size());
        }
        return removed;
    }

    public int getShapeCount() {
        return shapes.size();
    }
}
