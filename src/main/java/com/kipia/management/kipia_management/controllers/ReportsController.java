package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.DeviceReportService;
import com.kipia.management.kipia_management.services.ExcelExportService;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.fxml.FXML;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.general.DefaultPieDataset;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.control.RadioButton;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jfree.chart.block.BlockBorder;
import java.awt.Font;
import java.awt.Color;

/**
 * @author vladimir_shi
 * @since 05.09.2025
 * Контроллер для отчётов.
 */
public class ReportsController {
    @FXML
    private ToggleGroup reportTypeGroup;
    @FXML
    private RadioButton statusReportBtn;
    @FXML
    private RadioButton typeReportBtn;
    @FXML
    private RadioButton manufacturerReportBtn;
    @FXML
    private RadioButton locationReportBtn;
    @FXML
    private RadioButton yearReportBtn;
    @FXML
    private Label titleLabel;
    @FXML
    private BorderPane chartPane;
    @FXML
    private Button exportReportButton;

    private DeviceReportService reportService;
    private ExcelExportService excelService;
    private Stage primaryStage;
    private List<Device> allDevices;

    // Поля для хранения текущего графика и вьювера
    private JFreeChart currentChart;
    private ChartViewer currentChartViewer;

    private void buildPieChartWithJFreeChart(Map<String, Long> dataMap) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataMap.forEach(dataset::setValue);

        JFreeChart chart = ChartFactory.createPieChart(
                "Распределение",
                dataset,
                true,
                true,
                false
        );

        PiePlot plot = (PiePlot) chart.getPlot();

        // Применяем стиль в зависимости от темы
        updateChartTheme(chart, plot);

        plot.setOutlineVisible(true);
        plot.setLabelFont(new Font("Dialog", Font.BOLD, 12)); // Сделать шрифт чётким

        ChartViewer chartViewer = new ChartViewer(chart);
        chartViewer.setPrefSize(600, 400);

        // Сохраняем текущий график и вьювер
        currentChart = chart;
        currentChartViewer = chartViewer;

        chartPane.setCenter(chartViewer);

        // ДОБАВЛЕНО: Обновляем тему после установки графика (на случай задержки сцены)
        chartPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                // Логируем текущую тему
                boolean isDark = isDarkThemeActive();
                System.out.println("ReportsController: Scene установлена. Тема тёмная? " + isDark);

                // Слушатель на изменения стилей
                newScene.getStylesheets().addListener((javafx.collections.ListChangeListener<String>) c -> {
                    while (c.next()) {
                        if (c.wasAdded() || c.wasRemoved()) {
                            System.out.println("ReportsController: Стили изменились, обновляем тему.");
                            updateChartTheme();
                        }
                    }
                });

                // Сразу обновляем тему при установке сцены (если стили уже загружены)
                updateChartTheme();
            }
        });
    }

    private void styleChartForDarkTheme(JFreeChart chart, PiePlot plot) {
        Color darkBg = new Color(43, 43, 43);
        Color whiteText = Color.WHITE;

        chart.setBackgroundPaint(darkBg);
        plot.setBackgroundPaint(darkBg);
        plot.setOutlinePaint(whiteText); // Обводка

        plot.setLabelPaint(whiteText);
        plot.setLabelBackgroundPaint(null);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);

        // Легенда
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setBackgroundPaint(darkBg);
            legend.setItemPaint(whiteText);
            legend.setItemFont(new Font("Dialog", Font.BOLD, 12));
            legend.setFrame(new BlockBorder(Color.DARK_GRAY));
        }
    }

    private void styleChartForLightTheme(JFreeChart chart, PiePlot plot) {
        Color whiteBg = Color.WHITE;
        Color darkText = Color.DARK_GRAY;

        chart.setBackgroundPaint(whiteBg);
        plot.setBackgroundPaint(whiteBg);
        plot.setOutlinePaint(darkText); // Обводка

        plot.setLabelPaint(darkText);
        plot.setLabelBackgroundPaint(null);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);

        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setBackgroundPaint(whiteBg);
            legend.setItemPaint(darkText);
            legend.setItemFont(new Font("Dialog", Font.BOLD, 12));
            legend.setFrame(new BlockBorder(Color.LIGHT_GRAY));
        }
    }

    private void updateChartTheme(JFreeChart chart, PiePlot plot) {
        if (chart == null || plot == null) {
            System.out.println("ReportsController: chart или plot == null, пропускаем обновление.");
            return;
        }
        boolean isDark = isDarkThemeActive();
        System.out.println("ReportsController: Обновляем тему. Тёмная? " + isDark);
        if (isDark) {
            styleChartForDarkTheme(chart, plot);
        } else {
            styleChartForLightTheme(chart, plot);
        }
        chart.fireChartChanged();
    }

    private void updateChartTheme() {
        if (currentChart == null) return;
        PiePlot plot = (PiePlot) currentChart.getPlot();
        updateChartTheme(currentChart, plot);
    }

    private boolean isDarkThemeActive() {
        if (chartPane == null || chartPane.getScene() == null) {
            System.out.println("ReportsController: chartPane или scene == null.");
            return false;
        }
        boolean hasDark = chartPane.getScene().getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css"));
        System.out.println("ReportsController: Проверка темы - dark-theme.css найден? " + hasDark);
        return hasDark;
    }

    private void exportReportToExcel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт отчёта");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        File file = chooser.showSaveDialog(primaryStage);
        if (file == null) return;

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Report");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Категория");
            headerRow.createCell(1).setCellValue("Количество");

            Map<String, Long> countMap = getReportDataForCurrentType();
            int rowNum = 1;
            for (Map.Entry<String, Long> entry : countMap.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty()) continue;
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }

            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                workbook.write(fileOutputStream);
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Экспорт завершен!");
            alert.show();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Ошибка: " + e.getMessage());
            alert.show();
        }
    }

    // Вспомогательный метод для данных отчёта
    private Map<String, Long> getReportDataForCurrentType() {
        RadioButton selected = (RadioButton) reportTypeGroup.getSelectedToggle();
        if (selected == null) return Map.of();

        return switch (selected.getText()) {
            case "По статусу" ->
                    allDevices.stream().collect(Collectors.groupingBy(Device::getStatus, Collectors.counting()));
            case "По типам приборов" ->
                    allDevices.stream().collect(Collectors.groupingBy(Device::getType, Collectors.counting()));
            case "По производителям" ->
                    allDevices.stream().collect(Collectors.groupingBy(Device::getManufacturer, Collectors.counting()));
            case "По местоположению" ->
                    allDevices.stream().collect(Collectors.groupingBy(Device::getLocation, Collectors.counting()));
            case "По годам выпуска" ->
                    allDevices.stream().filter(d -> d.getYear() != null).collect(Collectors.groupingBy(d -> d.getYear().toString(), Collectors.counting()));
            default -> Map.of();
        };
    }


    // Метод инициализации — вызывается из MainController
    public void init(DeviceDAO deviceDAO, Stage primaryStage) {
        // Сервисы для логики
        this.primaryStage = primaryStage;
        this.reportService = new DeviceReportService();
        this.excelService = new ExcelExportService();
        this.allDevices = deviceDAO.getAllDevices();  // Загружаем данные из DAO

        // Добавь style к RadioButton после инициализации
        if (statusReportBtn != null) StyleUtils.applyStyleToRadioButton(statusReportBtn);
        if (typeReportBtn != null) StyleUtils.applyStyleToRadioButton(typeReportBtn);
        if (manufacturerReportBtn != null) StyleUtils.applyStyleToRadioButton(manufacturerReportBtn);
        if (locationReportBtn != null) StyleUtils.applyStyleToRadioButton(locationReportBtn);
        if (yearReportBtn != null) StyleUtils.applyStyleToRadioButton(yearReportBtn);

        // Обработчик смены радио-кнопки
        reportTypeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                updateReport();
            }
        });

        exportReportButton.setOnAction(event -> exportReportToExcel());

        // Вызов метода из StyleUtils для применения CSS и hover
        if (exportReportButton != null) {
            exportReportButton.getStyleClass().add("report-export-button");
            StyleUtils.applyHoverAndAnimation(exportReportButton, "report-export-button", "report-export-button-hover");
        }

        // Слушатель для смены темы (отслеживаем изменения в стилях сцены)
        chartPane.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getStylesheets().addListener((javafx.collections.ListChangeListener<String>) c -> {
                    while (c.next()) {
                        if (c.wasAdded() || c.wasRemoved()) {
                            updateChartTheme();
                        }
                    }
                });
            }
        });

        // Инициализация с первым отчётом
        updateReport();
    }

    // Метод обновления отчёта на основе выбранного типа
    private void updateReport() {
        RadioButton selected = (RadioButton) reportTypeGroup.getSelectedToggle();
        if (selected == null) return;

        String selectedType = selected.getText();
        titleLabel.setText("Отчёт по устройствам — " + selectedType);

        Map<String, Long> countMap = getReportDataForCurrentType();

        buildPieChartWithJFreeChart(countMap);

        // Обновляем тему при каждом обновлении отчёта (включая возврат в вкладку)
        updateChartTheme();
    }
}
