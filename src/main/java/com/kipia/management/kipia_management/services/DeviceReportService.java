package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.PieChart;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author vladimir_shi
 * @since 05.09.2025
 */

public class DeviceReportService {
    // Метод для сборки отчёта (для категориальных полей — статус, тип, производитель, местоположение)
    public void buildReport(List<Device> devices, Function<Device, String> fieldGetter, String chartTitle, PieChart chart) {
        Map<String, Long> countMap = devices.stream()
                .filter(d -> fieldGetter.apply(d) != null && !fieldGetter.apply(d).isEmpty())
                .collect(Collectors.groupingBy(fieldGetter, Collectors.counting()));

        ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList();
        countMap.forEach((key, count) -> chartData.add(new PieChart.Data(key + " (" + count + ")", count)));
        chart.setData(chartData);
        chart.setTitle(chartTitle);
    }

    // Специальный метод для отчёта по годам (года — Integer, но группируем как String)
    public void buildReportByYear(List<Device> devices, PieChart chart) {
        Map<String, Long> countMap = devices.stream()
                .filter(d -> d.getYear() != null)
                .collect(Collectors.groupingBy(d -> d.getYear().toString(), Collectors.counting()));

        ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList();
        countMap.forEach((key, count) -> chartData.add(new PieChart.Data("Год " + key + " (" + count + ")", count)));
        chart.setData(chartData);
        chart.setTitle("Распределение по годам");
        updatePieChartLabelsColor(chart);
    }

    private void updatePieChartLabelsColor(PieChart chart) {
        // Ищем все Text элементы с подписями в PieChart
        chart.lookupAll(".chart-pie-label").forEach(node -> {
            if (node instanceof Text text) {
                text.setFill(Color.WHITE);  // Задаём белый цвет текста
                // При необходимости можно добавить тень или увеличенный шрифт:
                // text.setStyle("-fx-effect: dropshadow(gaussian, black, 2, 0.5, 0, 0);");
            }
        });
    }
}
