package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import javafx.application.Platform;
import javafx.scene.layout.BorderPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.title.LegendTitle;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Класс-сервис для работы с отчетами
 *
 * @author vladimir_shi
 * @since 04.09.2025
 */

public class DeviceReportService {
    // логгер для сообщений
    private static final Logger LOGGER = LogManager.getLogger(DeviceReportService.class);

    // Возвращает map подсчёта по выбранному критерию
    public Map<String, Long> getReportData(List<Device> devices, String reportKey) {
        Map<String, Long> result = switch (reportKey) {
            case "Status" -> groupBy(devices, Device::getStatus);
            case "Type" -> groupBy(devices, Device::getType);
            case "Manufacturer" -> groupBy(devices, Device::getManufacturer);
            case "Location" -> groupBy(devices, Device::getLocation);
            case "Year" -> devices.stream()
                    .filter(d -> d.getYear() != null)
                    .collect(Collectors.groupingBy(d -> d.getYear().toString(), Collectors.counting()));
            default -> Collections.emptyMap();
        };
        LOGGER.info("Сгенерированы данные отчета для '{}': {} записей", reportKey, result.size());  // Logger для success
        return result;
    }

    private Map<String, Long> groupBy(List<Device> devices, Function<Device, String> classifier) {
        return devices.stream()
                .filter(d -> classifier.apply(d) != null && !classifier.apply(d).isEmpty())
                .collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }

    // Метод построения JFreeChart диаграммы
    public ChartViewer buildPieChart(Map<String, Long> dataMap, String chartTitle, BorderPane chartPane, boolean isDarkTheme) {
        if (dataMap.isEmpty()) {
            LOGGER.warn("Пустые данные для графика '{}' — график не создан", chartTitle);
            return null;
        }

        org.jfree.data.general.DefaultPieDataset dataset = new org.jfree.data.general.DefaultPieDataset();
        dataMap.forEach(dataset::setValue);

        JFreeChart chart = ChartFactory.createPieChart(
                chartTitle,
                dataset,
                true,
                true,
                false
        );

        PiePlot plot = (PiePlot) chart.getPlot();

        // Прозрачный фон
        chart.setBackgroundPaint(new java.awt.Color(0, 0, 0, 0));
        chart.getTitle().setVisible(false);
        plot.setBackgroundPaint(new java.awt.Color(0, 0, 0, 0));

        // Применяем стили
        applyChartTheme(chart, plot, isDarkTheme); // Выносим в отдельный метод


        plot.setOutlineVisible(false);
        plot.setLabelFont(new Font("Dialog", Font.BOLD, 14));


        ChartViewer chartViewer = new ChartViewer(chart);


        // Привязка к размерам
        chartPane.widthProperty().addListener((_, _, newVal) ->
                chartViewer.setPrefWidth(newVal.doubleValue() - 20)
        );
        chartPane.heightProperty().addListener((_, _, newVal) ->
                chartViewer.setPrefHeight(newVal.doubleValue() - 20)
        );

        chartPane.setCenter(chartViewer);
        LOGGER.info("График '{}' построен успешно", chartTitle);
        return chartViewer;
    }

    /**
     * Применяет тему к графику (используется при создании и обновлении)
     */
    private void applyChartTheme(JFreeChart chart, PiePlot plot, boolean isDarkTheme) {
        // 1. Цвет фона диаграммы
        chart.setBackgroundPaint(null);

        // 2. Настройка легенды (LegendTitle)
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setItemPaint(isDarkTheme ? Color.WHITE : Color.BLACK);  // Цвет текста легенды
            legend.setBackgroundPaint(new Color(0, 0, 0, 0));  // Прозрачный фон
            legend.setFrame(BlockBorder.NONE);  // Убираем рамку
        }

        // 3. Настройка меток сегментов (PiePlot)
        plot.setLabelPaint(isDarkTheme ? Color.WHITE : Color.BLACK);  // Цвет текста меток
        plot.setLabelBackgroundPaint(isDarkTheme ? Color.decode("#404040") : Color.LIGHT_GRAY);  // Фон меток
        plot.setLabelOutlinePaint(isDarkTheme ? Color.decode("#606060") : Color.DARK_GRAY);  // Контур меток
        plot.setLabelLinkPaint(isDarkTheme ? Color.GRAY : Color.DARK_GRAY);  // Цвет линий-выносок

        // Шрифт меток (опционально)
        Font labelFont = new Font("Arial", Font.PLAIN, 11);
        plot.setLabelFont(labelFont);
    }



    /**
     * Принудительное обновление графика и темы
     * @param chartViewer
     * @param isDarkTheme -
     */
    public void updateChartTheme(ChartViewer chartViewer, boolean isDarkTheme) {
        if (chartViewer == null) {
            return;
        }

        Platform.runLater(() -> {
            JFreeChart chart = chartViewer.getChart();
            PiePlot plot = (PiePlot) chart.getPlot();

            applyChartTheme(chart, plot, isDarkTheme);

            chartViewer.requestLayout();
            chartViewer.applyCss();
        });
    }
}