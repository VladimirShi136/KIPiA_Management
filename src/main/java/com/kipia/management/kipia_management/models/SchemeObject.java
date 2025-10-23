package com.kipia.management.kipia_management.models;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

/**
 * Класс для представления объектов схемы (линии, фигуры) и их сериализации.
 * Может быть расширен для добавления новых типов фигур.
 *
 * @author vladimir_shi
 * @since 21.10.2025
 */

public class SchemeObject {
    private enum Type {LINE, RECTANGLE, ELLIPSE, RHOMBUS}
    private final Type type;
    private double x1, y1, x2, y2, width, height, radiusX, radiusY, topX, topY, bottomX, bottomY, leftX, leftY, rightX, rightY;

    // Конструктор для геометрических фигур
    SchemeObject(Type t, double... coords) {
        this.type = t;
        switch (t) {
            case LINE:
                x1 = coords[0];
                y1 = coords[1];
                x2 = coords[2];
                y2 = coords[3];
                break;
            case RECTANGLE:
                x1 = coords[0];
                y1 = coords[1];
                width = coords[2];
                height = coords[3];
                break;
            case ELLIPSE:
                x1 = coords[0];
                y1 = coords[1];
                radiusX = coords[2];
                radiusY = coords[3];
                break;
            case RHOMBUS:
                throw new IllegalArgumentException("Use the other constructor for RHOMBUS");
            default:
                throw new IllegalArgumentException("Unknown type: " + t);
        }
    }

    // Конструктор для RHOMBUS
    SchemeObject(Type t, double topX, double topY, double rightX, double rightY, double bottomX, double bottomY, double leftX, double leftY) {
        if (t != Type.RHOMBUS) throw new IllegalArgumentException("This constructor is only for RHOMBUS");
        this.type = t;
        this.topX = topX;
        this.topY = topY;
        this.rightX = rightX;
        this.rightY = rightY;
        this.bottomX = bottomX;
        this.bottomY = bottomY;
        this.leftX = leftX;
        this.leftY = leftY;
    }

    // Метод создания JavaFX Node
    public Node toNode() {
        switch (type) {
            case LINE:
                Line line = new Line(x1, y1, x2, y2);
                line.setStroke(Color.BLACK);
                return line;
            case RECTANGLE:
                Rectangle rect = new Rectangle(x1, y1, width, height);
                rect.setFill(Color.TRANSPARENT);
                rect.setStroke(Color.BLACK);
                return rect;
            case ELLIPSE:
                Ellipse ellipse = new Ellipse(x1, y1, radiusX, radiusY);
                ellipse.setFill(Color.TRANSPARENT);
                ellipse.setStroke(Color.BLACK);
                return ellipse;
            case RHOMBUS:
                Path rhombus = new Path();
                rhombus.setFill(Color.TRANSPARENT);
                rhombus.setStroke(Color.BLACK);
                rhombus.getElements().addAll(
                        new MoveTo(topX, topY),
                        new LineTo(rightX, rightY),
                        new LineTo(bottomX, bottomY),
                        new LineTo(leftX, leftY),
                        new ClosePath()
                );
                return rhombus;
            default:
                return null;
        }
    }

    // Метод сериализации в строку
    public String toStringSegment() {
        return switch (type) {
            case LINE -> type.name() + "|" + x1 + "|" + y1 + "|" + x2 + "|" + y2;
            case RECTANGLE -> type.name() + "|" + x1 + "|" + y1 + width + "|" + height;
            case ELLIPSE -> type.name() + "|" + x1 + "|" + y1 + "|" + radiusX + "|" + radiusY;
            case RHOMBUS ->
                    type.name() + "|" + topX + "|" + topY + "|" + rightX + "|" + rightY + "|" + bottomX + "|" + bottomY + "|" + leftX + "|" + leftY;
        };
    }

    // Статический метод десериализации
    public static SchemeObject fromString(String str) {
        String[] parts = str.split("\\|");  // Разделитель "|"
        if (parts.length < 3) return null;
        Type t = Type.valueOf(parts[0]);
        try {
            double x1 = Double.parseDouble(parts[1]);
            double y1 = Double.parseDouble(parts[2]);
            switch (t) {
                case LINE, RECTANGLE, ELLIPSE:
                    double x2 = Double.parseDouble(parts[3]);
                    double y2 = Double.parseDouble(parts[4]);
                    return new SchemeObject(t, x1, y1, x2, y2);
                case RHOMBUS:
                    double topX = Double.parseDouble(parts[1]);
                    double topY = Double.parseDouble(parts[2]);
                    double rightX = Double.parseDouble(parts[3]);
                    double rightY = Double.parseDouble(parts[4]);
                    double bottomX = Double.parseDouble(parts[5]);
                    double bottomY = Double.parseDouble(parts[6]);
                    double leftX = Double.parseDouble(parts[7]);
                    double leftY = Double.parseDouble(parts[8]);
                    return new SchemeObject(Type.RHOMBUS, topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY);
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Статический метод создания из Node
    public static SchemeObject fromNode(Node node) {
        if (node instanceof Line line) {
            return new SchemeObject(Type.LINE, line.getStartX(), line.getStartY(), line.getEndX(), line.getEndY());
        } else if (node instanceof Rectangle rect) {
            return new SchemeObject(Type.RECTANGLE, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
        } else if (node instanceof Ellipse ellipse) {
            return new SchemeObject(Type.ELLIPSE, ellipse.getCenterX(), ellipse.getCenterY(), ellipse.getRadiusX(), ellipse.getRadiusY());
        } else if (node instanceof Path path && path.getElements().size() == 5) {  // Проверка на ромб
            MoveTo move = (MoveTo) path.getElements().get(0);
            LineTo line1 = (LineTo) path.getElements().get(1);
            LineTo line2 = (LineTo) path.getElements().get(2);
            LineTo line3 = (LineTo) path.getElements().get(3);
            return new SchemeObject(Type.RHOMBUS, move.getX(), move.getY(), line1.getX(), line1.getY(),
                    line2.getX(), line2.getY(), line3.getX(), line3.getY());
        }
        return null;
    }
}

