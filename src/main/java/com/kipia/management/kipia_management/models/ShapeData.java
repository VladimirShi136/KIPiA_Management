package com.kipia.management.kipia_management.models;

import com.kipia.management.kipia_management.shapes.ShapeType;
import javafx.scene.paint.Color;

/**
 * Модель данных для фигуры на схеме
 *
 * @author vladimir_shi
 * @since 07.03.2026
 */

public class ShapeData {
    private ShapeType type;
    private double x;
    private double y;
    private double width;
    private double height;
    private double rotation;
    private String fillColor;
    private String strokeColor;
    private double strokeWidth;
    private String text; // для текстовых фигур
    private double startX; // для линии
    private double startY; // для линии
    private double endX;   // для линии
    private double endY;   // для линии
    private double fontSize;      // размер шрифта
    private String fontFamily;    // семейство шрифта
    private String fontStyle;     // стиль (Regular, Bold, Italic, Bold Italic)

    // Конструкторы
    public ShapeData() {}

    // Геттеры и сеттеры
    public ShapeType getType() { return type; }
    public void setType(ShapeType type) { this.type = type; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public double getRotation() { return rotation; }
    public void setRotation(double rotation) { this.rotation = rotation; }

    public String getFillColor() { return fillColor; }
    public void setFillColor(String fillColor) { this.fillColor = fillColor; }

    public String getStrokeColor() { return strokeColor; }
    public void setStrokeColor(String strokeColor) { this.strokeColor = strokeColor; }

    public double getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(double strokeWidth) { this.strokeWidth = strokeWidth; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public double getStartX() { return startX; }
    public void setStartX(double startX) { this.startX = startX; }

    public double getStartY() { return startY; }
    public void setStartY(double startY) { this.startY = startY; }

    public double getEndX() { return endX; }
    public void setEndX(double endX) { this.endX = endX; }

    public double getEndY() { return endY; }
    public void setEndY(double endY) { this.endY = endY; }

    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) { this.fontSize = fontSize; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public String getFontStyle() { return fontStyle; }
    public void setFontStyle(String fontStyle) { this.fontStyle = fontStyle; }

    // Хелперы для цветов
    public static String colorToString(Color color) {
        if (color == null) return null;
        return String.format("#%02X%02X%02X",
                (int)(color.getRed() * 255),
                (int)(color.getGreen() * 255),
                (int)(color.getBlue() * 255));
    }

    public static Color stringToColor(String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) return null;
        try {
            return Color.web(colorStr);
        } catch (Exception e) {
            return null;
        }
    }
}