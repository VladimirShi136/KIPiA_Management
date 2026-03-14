package com.kipia.management.kipia_management.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kipia.management.kipia_management.models.ShapeData;
import com.kipia.management.kipia_management.models.SchemeData;
import com.kipia.management.kipia_management.shapes.*;
import javafx.scene.paint.Color;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.scene.text.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Сервис для работы с фигурами
 * (Serialize, Deserialize, remove, add, clean)
 *
 * @author vladimir_shi
 * @since 25.10.2025
 */
public class ShapeService {
    private static final Logger LOGGER = LogManager.getLogger(ShapeService.class);
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

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

    /**
     * Сериализация всех фигур в JSON
     */
    public String serializeAllToJson() {
        SchemeData schemeData = new SchemeData();

        for (ShapeBase shape : shapes) {
            ShapeData shapeData = convertShapeToData(shape);
            if (shapeData != null) {
                schemeData.getShapes().add(shapeData);
            }
        }

        return gson.toJson(schemeData);
    }

    /**
     * Конвертация ShapeBase в ShapeData
     */
    private ShapeData convertShapeToData(ShapeBase shape) {
        ShapeData data = new ShapeData();

        // Определяем тип
        switch (shape) {
            case RectangleShape _ -> data.setType(ShapeType.RECTANGLE);
            case EllipseShape _ -> data.setType(ShapeType.ELLIPSE);
            case LineShape _ -> data.setType(ShapeType.LINE);
            case RhombusShape _ -> {
                data.setType(ShapeType.RHOMBUS);
                LOGGER.info("Saving Rhombus: pos=({},{}), size={}x{}",
                        shape.getLayoutX(), shape.getLayoutY(),
                        shape.getCurrentWidth(), shape.getCurrentHeight());
            }
            case TextShape textShape -> {
                data.setType(ShapeType.TEXT);
                Font font = textShape.getFont();

                // Сохраняем параметры шрифта
                data.setFontSize(font.getSize());
                data.setFontFamily(font.getFamily());
                data.setFontStyle(font.getStyle());

                LOGGER.info("Saving Text: pos=({},{}), text='{}', font={} {}",
                        shape.getLayoutX(), shape.getLayoutY(),
                        textShape.getText(),
                        font.getSize(),
                        font.getStyle());
            }
            case null, default -> {
                return null;
            }
        }


        // Общие свойства
        data.setX(shape.getLayoutX());
        data.setY(shape.getLayoutY());
        data.setRotation(shape.getRotate());

        // Цвета - используем новые геттеры
        Color stroke = shape.getStrokeColor();
        Color fill = shape.getFillColor();

        data.setStrokeColor(stroke != null && !stroke.equals(Color.TRANSPARENT) ?
                ShapeData.colorToString(stroke) : null);
        data.setFillColor(fill != null && !fill.equals(Color.TRANSPARENT) ?
                ShapeData.colorToString(fill) : null);
        data.setStrokeWidth(shape.getStrokeWidth());
        // Специфичные для типа свойства
        if (shape instanceof LineShape line) {
            data.setStartX(line.getStartX());
            data.setStartY(line.getStartY());
            data.setEndX(line.getEndX());
            data.setEndY(line.getEndY());
            // Вычисляем bounding box для совместимости с Android
            data.setWidth(Math.abs(line.getEndX() - line.getStartX()));
            data.setHeight(Math.abs(line.getEndY() - line.getStartY()));
        } else if (shape instanceof TextShape text) {
            data.setText(text.getText());
        } else {
            // Для остальных фигур - ширина и высота из bounds
            data.setWidth(shape.getBoundsInLocal().getWidth());
            data.setHeight(shape.getBoundsInLocal().getHeight());
        }

        return data;
    }

    /**
     * Десериализация и добавление всех фигур из JSON
     */
    public void deserializeAndAddAll(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty() || jsonData.equals("{}")) {
            LOGGER.info("Нет данных фигур для десериализации");
            return;
        }

        try {
            SchemeData schemeData = gson.fromJson(jsonData, SchemeData.class);
            if (schemeData == null || schemeData.getShapes() == null) {
                return;
            }

            int loaded = 0;
            int failed = 0;

            for (ShapeData shapeData : schemeData.getShapes()) {
                try {
                    ShapeBase shape = convertDataToShape(shapeData);
                    if (shape != null) {
                        shapes.add(shape);
                        shape.addToPane();
                        shape.addContextMenu(shape::handleDelete);
                        loaded++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                    LOGGER.error("Ошибка создания фигуры типа {}: {}", shapeData.getType(), e.getMessage());
                }
            }

            LOGGER.info("Загружено фигур: {}, ошибок: {}", loaded, failed);

        } catch (Exception e) {
            LOGGER.error("Ошибка парсинга JSON: {}", e.getMessage(), e);
            // Пробуем загрузить в старом формате как fallback
            tryLoadLegacyFormat(jsonData);
        }
    }

    /**
     * Конвертация ShapeData в ShapeBase
     */
    private ShapeBase convertDataToShape(ShapeData data) {
        double[] coords;

        switch (data.getType()) {
            case LINE:
                coords = new double[]{
                        data.getStartX(), data.getStartY(),
                        data.getEndX(), data.getEndY()
                };
                break;
            case TEXT:
                coords = new double[]{data.getX(), data.getY(), 0};
                LOGGER.info("Loading Text: x={}, y={}, text='{}', fontSize={}, fontStyle={}",
                        data.getX(), data.getY(), data.getText(),
                        data.getFontSize(), data.getFontStyle());
                break;
            case RHOMBUS:
                // Для отладки
                LOGGER.info("Loading Rhombus: x={}, y={}, width={}, height={}",
                        data.getX(), data.getY(), data.getWidth(), data.getHeight());
                coords = new double[]{
                        data.getX(), data.getY(),
                        data.getWidth(), data.getHeight()
                };
                break;
            default: // RECTANGLE, ELLIPSE
                coords = new double[]{
                        data.getX(), data.getY(),
                        data.getWidth(), data.getHeight()
                };
                break;
        }

        ShapeBase shape = factory.createShape(data.getType(), coords);

        if (shape != null) {
            // Устанавливаем цвета
            if (data.getStrokeColor() != null) {
                shape.setStrokeColor(ShapeData.stringToColor(data.getStrokeColor()));
            } else {
                shape.setStrokeColor(Color.BLACK);
            }

            if (data.getFillColor() != null) {
                shape.setFillColor(ShapeData.stringToColor(data.getFillColor()));
            } else {
                shape.setFillColor(Color.TRANSPARENT);
            }

            try {
                shape.setStrokeWidth(data.getStrokeWidth());
            } catch (Exception e) {
                // Игнорируем
            }
            shape.setRotation(data.getRotation());

            // Для текста - ВАЖНО: здесь должны быть данные о шрифте!
            if (shape instanceof TextShape textShape && data.getText() != null) {
                textShape.setText(data.getText());

                // Восстанавливаем шрифт, если есть данные
                if (data.getFontSize() > 0 && data.getFontStyle() != null) {
                    FontWeight weight = data.getFontStyle().contains("Bold") ?
                            FontWeight.BOLD : FontWeight.NORMAL;
                    FontPosture posture = data.getFontStyle().contains("Italic") ?
                            FontPosture.ITALIC : FontPosture.REGULAR;

                    String fontFamily = data.getFontFamily() != null ?
                            data.getFontFamily() : "Arial";

                    Font restoredFont = Font.font(
                            fontFamily,
                            weight,
                            posture,
                            data.getFontSize()
                    );

                    textShape.setFont(restoredFont);
                    textShape.calculateTextSize();

                    LOGGER.info("Restored Text font: size={}, style={}",
                            data.getFontSize(), data.getFontStyle());
                }
            }
        }

        return shape;
    }

    /**
     * Загрузка старого pipe-формата (для обратной совместимости)
     */
    private void tryLoadLegacyFormat(String data) {
        LOGGER.info("Пробуем загрузить в старом pipe-формате");
        if (data == null || data.trim().isEmpty() || data.equals("{}")) {
            return;
        }

        String[] parts = data.split(";");
        int loaded = 0;
        int failed = 0;

        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) {
                try {
                    ShapeBase shape = ShapeBase.deserialize(part.trim(),
                            factory.pane(), factory.statusSetter(),
                            factory.onSelectCallback(), factory.shapeManager());
                    if (shape != null) {
                        shapes.add(shape);
                        shape.addToPane();
                        loaded++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                    LOGGER.error("Ошибка загрузки pipe-формата: {}", e.getMessage());
                }
            }
        }

        LOGGER.info("Загружено из pipe: {}, ошибок: {}", loaded, failed);
    }

    // Сохраняем старый метод для обратной совместимости, но помечаем как deprecated
    @Deprecated
    public List<String> serializeAll() {
        LOGGER.warn("Используется устаревший метод serializeAll()");
        return new ArrayList<>();
    }

    @Deprecated
    public void deserializeAndAddAll(List<String> shapeData) {
        LOGGER.warn("Используется устаревший метод deserializeAndAddAll(List)");
        if (shapeData == null || shapeData.isEmpty()) return;
        deserializeAndAddAll(String.join(";", shapeData));
    }

    // Остальные методы остаются без изменений
    public void clearAllShapes() {
        if (shapes.isEmpty()) {
            LOGGER.info("No shapes to clear");
            return;
        }

        for (ShapeBase shape : shapes) {
            if (shape != null) {
                shape.removeFromPane();
                shape.removeResizeHandles();
            }
        }
        shapes.clear();
    }

    public void addShapeToList(ShapeBase shape) {
        if (shape != null && !shapes.contains(shape)) {
            shapes.add(shape);
        }
    }

    public void removeShapeFromList(ShapeBase shape) {
        boolean removed = shapes.remove(shape);
        if (removed) {
            LOGGER.info("Удалена фигура из списка, количество сейчас: {}", shapes.size());
        }
    }

    public void applyCanvasBoundsToAll(double width, double height) {
        shapes.forEach(s -> s.setCanvasBounds(width, height));
    }

    public int getShapeCount() {
        return shapes.size();
    }
}