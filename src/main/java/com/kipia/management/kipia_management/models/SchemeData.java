package com.kipia.management.kipia_management.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Корневая модель для JSON данных схемы
 *
 * @author vladimir_shi
 * @since 07.03.2026
 */

public class SchemeData {
    private List<ShapeData> shapes;
    private int version; // для будущих обновлений формата
    private double width = 2000;
    private double height = 1200;
    private boolean gridEnabled = true;
    private int gridSize = 50;

    public SchemeData() {
        this.shapes = new ArrayList<>();
        this.version = 1;
    }

    public List<ShapeData> getShapes() { return shapes; }
    public void setShapes(List<ShapeData> shapes) { this.shapes = shapes; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public void setWidth(int width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public void setHeight(int height) { this.height = height; }

    public boolean isGridEnabled() { return gridEnabled; }
    public void setGridEnabled(boolean gridEnabled) { this.gridEnabled = gridEnabled; }

    public int getGridSize() { return gridSize; }
    public void setGridSize(int gridSize) { this.gridSize = gridSize; }
}