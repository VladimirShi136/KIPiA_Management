package com.kipia.management.kipia_management.managers;

import com.kipia.management.kipia_management.shapes.ShapeBase;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Менеджер буфера обмена для фигур
 *
 * @author vladimir_shi
 * @since 05.11.2025
 */
public class ClipboardManager {
    private static String copiedShapeData;
    private static final BooleanProperty hasShapeData = new SimpleBooleanProperty(false);

    public static void copyShape(ShapeBase shape) {
        if (shape != null) {
            copiedShapeData = shape.serialize();
            hasShapeData.set(true);
            System.out.println("DEBUG: Фигура скопирована в буфер");
        }
    }

    public static String getCopiedShapeData() {
        return copiedShapeData;
    }

    public static boolean hasShapeData() {
        return copiedShapeData != null && !copiedShapeData.isEmpty();
    }

    public static BooleanProperty hasShapeDataProperty() {
        return hasShapeData;
    }

    public static void clear() {
        copiedShapeData = null;
        hasShapeData.set(false);
    }
}
