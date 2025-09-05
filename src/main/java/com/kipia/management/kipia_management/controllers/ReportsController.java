package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.DeviceReportService;
import com.kipia.management.kipia_management.services.ExcelExportService;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.effect.Glow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.scene.control.RadioButton;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private PieChart chart;
    @FXML
    private Button exportReportButton;

    private DeviceReportService reportService;
    private ExcelExportService excelService;
    private Stage primaryStage;
    private List<Device> allDevices;

    // Создай новый стильный метод для RadioButton
    private void applyStyleToRadioButton(RadioButton button) {
        // Начальный стиль
        button.setStyle(
                "-fx-background-color: linear-gradient(to right, #6b5ce7, #a29bfe); " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-radius: 10; " +
                        "-fx-border-radius: 10; " +
                        "-fx-padding: 5 10 5 10; " +
                        "-fx-effect: null; " +
                        "-fx-cursor: hand;");

        Glow glow = new Glow(0.0);
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(300), button);
        scaleIn.setFromX(1.0);
        scaleIn.setFromY(1.0);
        scaleIn.setToX(1.2);
        scaleIn.setToY(1.2);
        scaleIn.setInterpolator(Interpolator.EASE_OUT);

        // Timeline для hover-in эффектов — используй add для KeyValues
        Timeline hoverIn = new Timeline();
        hoverIn.getKeyFrames().add(new KeyFrame(Duration.millis(300),
                new KeyValue(glow.levelProperty(), 0.8, Interpolator.EASE_IN),
                new KeyValue(button.styleProperty(),
                        "-fx-background-color: linear-gradient(to right, #ff6b6b, #4ecdc4); " +
                                "-fx-text-fill: white; " +
                                "-fx-font-size: 14px; " +
                                "-fx-font-weight: bold; " +
                                "-fx-background-radius: 10; " +
                                "-fx-border-radius: 10; " +
                                "-fx-padding: 5 10 5 10; " +
                                "-fx-effect: dropshadow(gaussian, rgba(255,107,107,0.5), 10, 0, 0, 0); " +
                                "-fx-cursor: hand;", Interpolator.EASE_IN)));

        button.setOnMouseEntered(e -> {
            button.setEffect(glow);
            hoverIn.playFromStart();
            scaleIn.playFromStart();
        });

        button.setOnMouseExited(e -> {
            button.setEffect(null);
            hoverIn.stop();
            button.setStyle(
                    "-fx-background-color: linear-gradient(to right, #6b5ce7, #a29bfe); " +
                            "-fx-text-fill: white; " +
                            "-fx-font-size: 14px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-background-radius: 10; " +
                            "-fx-border-radius: 10; " +
                            "-fx-padding: 5 10 5 10; " +
                            "-fx-effect: null; " +
                            "-fx-cursor: hand;");
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(300), button);
            scaleOut.setFromX(1.2);
            scaleOut.setFromY(1.2);
            scaleOut.setToX(1.0);
            scaleOut.setToY(1.0);
            scaleOut.setInterpolator(Interpolator.EASE_OUT);
            scaleOut.play();
        });
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
        if (statusReportBtn != null) applyStyleToRadioButton(statusReportBtn);
        if (typeReportBtn != null) applyStyleToRadioButton(typeReportBtn);
        if (manufacturerReportBtn != null) applyStyleToRadioButton(manufacturerReportBtn);
        if (locationReportBtn != null) applyStyleToRadioButton(locationReportBtn);
        if (yearReportBtn != null) applyStyleToRadioButton(yearReportBtn);

        // Обработчик смены радио-кнопки
        reportTypeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                updateReport();
            }
        });

        exportReportButton.setOnAction(event -> exportReportToExcel());

        // Инициализация с первым отчётом
        updateReport();
    }

    // Метод обновления отчёта на основе выбранного типа
    private void updateReport() {
        RadioButton selected = (RadioButton) reportTypeGroup.getSelectedToggle();
        if (selected == null) return;
        String selectedType = selected.getText();
        titleLabel.setText("Отчёт по устройствам — " + selectedType);

        switch (selectedType) {
            case "По статусу":
                reportService.buildReport(allDevices, Device::getStatus, "Распределение по статусам", chart);
                exportReportButton.setOnAction(e -> excelService.exportReportToExcel(allDevices, "Status", primaryStage));
                break;
            case "По типам приборов":
                reportService.buildReport(allDevices, Device::getType, "Распределение по типам", chart);
                exportReportButton.setOnAction(e -> excelService.exportReportToExcel(allDevices, "Type", primaryStage));
                break;
            case "По производителям":
                reportService.buildReport(allDevices, Device::getManufacturer, "Распределение по производителям", chart);
                exportReportButton.setOnAction(e -> excelService.exportReportToExcel(allDevices, "Manufacturer", primaryStage));
                break;
            case "По местоположению":
                reportService.buildReport(allDevices, Device::getLocation, "Распределение по местоположениям", chart);
                exportReportButton.setOnAction(e -> excelService.exportReportToExcel(allDevices, "Location", primaryStage));
                break;
            case "По годам выпуска":
                reportService.buildReportByYear(allDevices, chart);
                exportReportButton.setOnAction(e -> excelService.exportReportToExcel(allDevices, "Year", primaryStage));
                break;
        }
    }
}
