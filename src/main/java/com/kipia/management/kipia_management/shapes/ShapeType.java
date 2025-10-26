package com.kipia.management.kipia_management.shapes;

/**
 * @author vladimir_shi
 * @since 25.10.2025
 */

public enum ShapeType {
    RECTANGLE(4),  // x, y, width, height
    ELLIPSE(4),    // x, y, width, height (bounding box)
    LINE(4),       // startX, startY, endX, endY
    RHOMBUS(4),    // x, y, width, height (bounding box)
    TEXT(3);       // x, y, text

    private final int requiredCoordinates;

    ShapeType(int requiredCoordinates) {
        this.requiredCoordinates = requiredCoordinates;
    }

    public void validateCoordinates(double[] coords) {
        if (coords.length != requiredCoordinates) {
            throw new IllegalArgumentException(
                    "Shape " + this + " requires " + requiredCoordinates + " coordinates"
            );
        }
    }
}
