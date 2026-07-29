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

    // -----------------------------------------------
    //  Конвертеры цветов для совместимости с Android
    // -----------------------------------------------

    /**
     * Преобразует объект {@link javafx.scene.paint.Color} в строку формата
     * <b>0xRRGGBBAA</b>, где последние два символа – альфа‑канал.
     * Соответствует формату Android (Kotlin) реализации.
     *
     * @param color объект JavaFX‑цвета (может быть null)
     * @return строковое представление в формате 0xRRGGBBAA или null,
     *         если color == null
     */
    public static String colorToString(Color color) {
        if (color == null) return null;
        return String.format("0x%02X%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255),
                (int) Math.round(color.getOpacity() * 255));
    }

    /**
     * Преобразует строковое представление цвета в объект {@link Color}.
     * Поддерживает формат Android (Kotlin): 0xRRGGBBAA.
     *
     * @param colorStr строка с цветом
     * @return объект {@link Color} либо null, если строка пустая/null
     */
    public static Color stringToColor(String colorStr) {
        if (colorStr == null || colorStr.trim().isEmpty()) return null;

        String trimmed = colorStr.trim();

        try {
            // Если формат 0xRRGGBBAA (как в Kotlin: "0x" + 8 символов)
            if (trimmed.toLowerCase().startsWith("0x") && trimmed.length() == 10) {
                String hex = trimmed.substring(2); // Берем 8 символов после 0x
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                int a = Integer.parseInt(hex.substring(6, 8), 16);
                return Color.rgb(r, g, b, a / 255.0);
            }

            // Если старый формат #AARRGGBB
            if (trimmed.startsWith("#") && trimmed.length() == 9) {
                int a = Integer.parseInt(trimmed.substring(1, 3), 16);
                int r = Integer.parseInt(trimmed.substring(3, 5), 16);
                int g = Integer.parseInt(trimmed.substring(5, 7), 16);
                int b = Integer.parseInt(trimmed.substring(7, 9), 16);
                return Color.rgb(r, g, b, a / 255.0);
            }

            // Если просто 6-значный HEX (RGB)
            if (trimmed.length() == 7 && trimmed.startsWith("#")) {
                return Color.web(trimmed);
            }

            // Fallback для стандартных названий цветов JavaFX
            return Color.web(trimmed);
        } catch (Exception e) {
            return Color.BLACK; // Безопасное значение по умолчанию
        }
    }
}