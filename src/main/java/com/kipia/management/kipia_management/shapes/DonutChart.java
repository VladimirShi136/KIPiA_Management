package com.kipia.management.kipia_management.shapes;

import javafx.geometry.Pos;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DonutChart extends StackPane {

    private static final double DEFAULT_SIZE = 300;
    private static final double STROKE_WIDTH = 25;

    // Фиксированный порядок отрисовки сегментов (чтобы кольцо не "прыгало" между обновлениями)
    public static final List<String> STATUS_ORDER = List.of(
            "В работе", "Хранение", "Утерян", "Испорчен"
    );

    private static final Map<String, Color> STATUS_COLORS = new LinkedHashMap<>();
    static {
        STATUS_COLORS.put("В работе", Color.web("#4CAF50"));   // зелёный
        STATUS_COLORS.put("Хранение", Color.web("#FF9800"));   // оранжевый
        STATUS_COLORS.put("Утерян", Color.web("#9E9E9E"));     // серый
        STATUS_COLORS.put("Испорчен", Color.web("#F44336"));   // красный
    }

    private final Pane chartPane;
    private final Text totalText;
    private final Text subtitleText;
    private final double size;

    public DonutChart() {
        this(DEFAULT_SIZE);
    }

    public DonutChart(double size) {
        this.size = size;
        this.chartPane = new Pane();
        this.totalText = new Text();
        this.subtitleText = new Text("всего приборов");

        initializeChart(size);
    }

    private void initializeChart(double size) {
        setPrefSize(size, size);
        setMaxSize(size, size);

        chartPane.setPrefSize(size, size);
        chartPane.setMaxSize(size, size);
        chartPane.setMouseTransparent(true);

        totalText.setFont(Font.font("System", FontWeight.BOLD, 36));
        totalText.setTextAlignment(TextAlignment.CENTER);

        subtitleText.setFont(Font.font("System", 13));
        subtitleText.setTextAlignment(TextAlignment.CENTER);
        subtitleText.setFill(Color.web("#9AA1AC"));

        VBox centerBox = new VBox(2, totalText, subtitleText);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setMouseTransparent(true);

        getChildren().addAll(chartPane, centerBox);
    }

    public void updateChart(Map<String, Long> data, boolean isDarkTheme) {
        chartPane.getChildren().clear();

        Color textColor = isDarkTheme ? Color.WHITE : Color.web("#2C3440");
        totalText.setFill(textColor);

        if (data == null || data.isEmpty()) {
            totalText.setText("0");
            return;
        }

        double total = data.values().stream().mapToLong(Long::longValue).sum();
        totalText.setText(String.valueOf((long) total));

        if (total == 0) {
            return;
        }

        double centerX = size / 2;
        double centerY = size / 2;
        double ringRadius = (size / 2) - STROKE_WIDTH / 2;

        // Начинаем с 12 часов (90°) и идём по часовой стрелке
        double startAngle = 90;

        for (String status : STATUS_ORDER) {
            long count = data.getOrDefault(status, 0L);
            if (count == 0) continue;

            double arcLength = (count / total) * 360.0;

            Arc arc = createRingSegment(centerX, centerY, ringRadius, startAngle, -arcLength,
                    STATUS_COLORS.getOrDefault(status, Color.GRAY));
            chartPane.getChildren().add(arc);

            startAngle -= arcLength;
        }

        // На случай, если в данных есть статус, не входящий в STATUS_ORDER
        for (Map.Entry<String, Long> entry : data.entrySet()) {
            if (!STATUS_ORDER.contains(entry.getKey()) && entry.getValue() > 0) {
                double arcLength = (entry.getValue() / total) * 360.0;
                Arc arc = createRingSegment(centerX, centerY, ringRadius, startAngle, -arcLength, Color.GRAY);
                chartPane.getChildren().add(arc);
                startAngle -= arcLength;
            }
        }
    }

    private Arc createRingSegment(double centerX, double centerY, double radius,
                                  double startAngle, double arcLength, Color color) {
        Arc arc = new Arc();
        arc.setCenterX(centerX);
        arc.setCenterY(centerY);
        arc.setRadiusX(radius);
        arc.setRadiusY(radius);
        arc.setStartAngle(startAngle);
        arc.setLength(arcLength);
        // ВАЖНО: OPEN, а не ROUND — иначе обводка идёт и по радиальным линиям к центру,
        // и вместо кольца получаются "спицы"
        arc.setType(ArcType.OPEN);
        arc.setFill(Color.TRANSPARENT);
        arc.setStroke(color);
        arc.setStrokeWidth(STROKE_WIDTH);
        arc.setStrokeLineCap(StrokeLineCap.BUTT);

        return arc;
    }

    public void setCenterText(String text) {
        totalText.setText(text);
    }

    public void setCenterTextSize(double size) {
        totalText.setFont(Font.font("System", FontWeight.BOLD, size));
    }

    public void updateTheme(boolean isDarkTheme) {
        totalText.setFill(isDarkTheme ? Color.WHITE : Color.web("#2C3440"));
    }

    public static Color getStatusColor(String status) {
        return STATUS_COLORS.getOrDefault(status, Color.GRAY);
    }
}