package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.DeviceReportService;
import com.kipia.management.kipia_management.services.ExcelExportReportsService;
import com.kipia.management.kipia_management.utils.CustomAlert;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.jfree.chart.fx.ChartViewer;

import java.io.File;
import java.util.List;
import java.util.Map;

public class ReportsController {

    @FXML private ToggleGroup reportTypeGroup;
    @FXML private RadioButton statusReportBtn;
    @FXML private RadioButton typeReportBtn;
    @FXML private RadioButton manufacturerReportBtn;
    @FXML private RadioButton locationReportBtn;
    @FXML private RadioButton yearReportBtn;
    @FXML private Label titleLabel;
    @FXML private BorderPane chartPane;
    @FXML private Button exportReportButton;

    private DeviceReportService reportService;
    private ExcelExportReportsService excelService;
    private Stage primaryStage;
    private List<Device> allDevices;
    private ChartViewer currentChartViewer;

    public void init(DeviceDAO deviceDAO, Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.reportService = new DeviceReportService();
        this.excelService = new ExcelExportReportsService();
        this.allDevices = deviceDAO.getAllDevices();

        if (statusReportBtn != null) StyleUtils.applyStyleToRadioButton(statusReportBtn);
        if (typeReportBtn != null) StyleUtils.applyStyleToRadioButton(typeReportBtn);
        if (manufacturerReportBtn != null) StyleUtils.applyStyleToRadioButton(manufacturerReportBtn);
        if (locationReportBtn != null) StyleUtils.applyStyleToRadioButton(locationReportBtn);
        if (yearReportBtn != null) StyleUtils.applyStyleToRadioButton(yearReportBtn);

        reportTypeGroup.selectedToggleProperty().addListener((_, _, _) -> updateReport());
        exportReportButton.setOnAction(_ -> {
            String reportKey = getCurrentReportKey();
            if (reportKey.isEmpty()) return;

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Экспорт отчёта " + reportKey + " в Excel");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
            File file = chooser.showSaveDialog(primaryStage);
            if (file == null) return;

            boolean success = excelService.exportReport(allDevices, reportKey, file);
            if (success) {
                CustomAlert.showSuccess("Экспорт", "Отчёт " + reportKey + " экспортирован: " + file.getAbsolutePath());
            } else {
                CustomAlert.showError("Экспорт", "Ошибка при экспорте отчёта " + reportKey);
            }
        });

        if (exportReportButton != null) {
            exportReportButton.getStyleClass().add("report-export-button");
            StyleUtils.applyHoverAndAnimation(exportReportButton, "report-export-button", "report-export-button-hover");
        }

        updateReport();
    }

    // Метод для обновления темы существующего графика
    public void refreshTheme() {
        boolean isDarkTheme = isDarkThemeActive();
        if (currentChartViewer != null) {
            reportService.updateChartTheme(currentChartViewer, isDarkTheme);
        }
    }

    private void updateReport() {
        String reportKey = getCurrentReportKey();
        if (reportKey.isEmpty()) return;

        titleLabel.setText("Отчёт по устройствам — " + getCurrentReportLabel());
        Map<String, Long> dataMap = reportService.getReportData(allDevices, reportKey);

        boolean isDarkTheme = isDarkThemeActive();

        chartPane.setCenter(null);

        currentChartViewer = reportService.buildPieChart(
                dataMap,
                getCurrentReportLabel(),
                chartPane,
                isDarkTheme
        );
    }

    private String getCurrentReportKey() {
        Toggle selectedToggle = reportTypeGroup.getSelectedToggle();
        if (selectedToggle == null) return "";
        RadioButton selected = (RadioButton) selectedToggle;
        return switch (selected.getText()) {
            case "По статусу" -> "Status";
            case "По типам приборов" -> "Type";
            case "По производителям" -> "Manufacturer";
            case "По местоположению" -> "Location";
            case "По годам выпуска" -> "Year";
            default -> "";
        };
    }

    private String getCurrentReportLabel() {
        Toggle selectedToggle = reportTypeGroup.getSelectedToggle();
        if (selectedToggle == null) return "";
        RadioButton selected = (RadioButton) selectedToggle;
        return selected.getText();
    }

    private boolean isDarkThemeActive() {
        if (chartPane == null || chartPane.getScene() == null) return false;
        return chartPane.getScene().getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css"));
    }

    public ChartViewer getCurrentChartViewer() {
        return currentChartViewer;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }
}