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

    public SchemeData() {
        this.shapes = new ArrayList<>();
        this.version = 1;
    }

    public List<ShapeData> getShapes() { return shapes; }
    public void setShapes(List<ShapeData> shapes) { this.shapes = shapes; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}