package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import javafx.scene.layout.BorderPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.block.BlockBorder;
import java.awt.Font;
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

    // Метод построения JFreeChart диаграммы с обновлением темы
    public ChartViewer buildPieChart(Map<String, Long> dataMap, String chartTitle, BorderPane chartPane, boolean isDarkTheme) {
        if (dataMap.isEmpty()) {
            LOGGER.warn("Пустые данные для графика '{}' — график не создан", chartTitle);  // Logger для предупреждения
            // Не возвращаем ничего или null; контроллер xử lý
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
        if (isDarkTheme) {
            styleChartForDarkTheme(chart, plot);
        } else {
            styleChartForLightTheme(chart, plot);
        }
        plot.setOutlineVisible(true);
        plot.setLabelFont(new Font("Dialog", Font.BOLD, 12));
        ChartViewer chartViewer = new ChartViewer(chart);
        chartViewer.setPrefSize(600, 400);
        chartPane.setCenter(chartViewer);
        LOGGER.info("График '{}' построен успешно", chartTitle);  // Logger для success
        return chartViewer;
    }

    private void styleChartForDarkTheme(JFreeChart chart, PiePlot plot) {
        // Используем полное имя для java.awt.Color, чтобы избежать конфликта с javafx.scene.paint.Color
        java.awt.Color darkBg = new java.awt.Color(43, 43, 43);
        java.awt.Color whiteText = java.awt.Color.WHITE;
        chart.setBackgroundPaint(darkBg);
        plot.setBackgroundPaint(darkBg);
        plot.setOutlinePaint(whiteText);
        plot.setLabelPaint(whiteText);
        plot.setLabelBackgroundPaint(null);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setBackgroundPaint(darkBg);
            legend.setItemPaint(whiteText);
            legend.setItemFont(new Font("Dialog", Font.BOLD, 12));
            legend.setFrame(new BlockBorder(java.awt.Color.DARK_GRAY));
        }
    }

    private void styleChartForLightTheme(JFreeChart chart, PiePlot plot) {
        // Используем полное имя для java.awt.Color
        java.awt.Color whiteBg = java.awt.Color.WHITE;
        java.awt.Color darkText = java.awt.Color.DARK_GRAY;
        chart.setBackgroundPaint(whiteBg);
        plot.setBackgroundPaint(whiteBg);
        plot.setOutlinePaint(darkText);
        plot.setLabelPaint(darkText);
        plot.setLabelBackgroundPaint(null);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setBackgroundPaint(whiteBg);
            legend.setItemPaint(darkText);
            legend.setItemFont(new Font("Dialog", Font.BOLD, 12));
            legend.setFrame(new BlockBorder(java.awt.Color.LIGHT_GRAY));
        }
    }
}