package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
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
 */

public class ExcelExportService {
    // Метод для экспорта отчётов (per type, используя groupBy из DAO)
    public void exportReportToExcel(List<Device> devices, String reportType, Stage primaryStage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт отчёта " + reportType + " в Excel");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        File file = chooser.showSaveDialog(primaryStage);
        if (file == null) return;

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Report");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue(getHeaderForType(reportType));
            headerRow.createCell(1).setCellValue("Количество");

            Map<String, Long> countMap = getCountMap(devices, reportType);
            int rowNum = 1;
            for (Map.Entry<String, Long> entry : countMap.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty()) continue;
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }

            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Отчёт " + reportType + " экспортирован: " + file.getAbsolutePath());
            alert.show();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Ошибка: " + e.getMessage());
        }
    }

    private String getHeaderForType(String reportType) {
        return switch (reportType) {
            case "Status" -> "Статус";
            case "Type" -> "Тип прибора";
            case "Manufacturer" -> "Производитель";
            case "Location" -> "Местоположение";
            case "Year" -> "Год выпуска";
            default -> "Категория";
        };
    }

    private Map<String, Long> getCountMap(List<Device> devices, String reportType) {
        return switch (reportType) {
            case "Status" -> devices.stream().collect(Collectors.groupingBy(Device::getStatus, Collectors.counting()));
            case "Type" -> devices.stream().collect(Collectors.groupingBy(Device::getType, Collectors.counting()));
            case "Manufacturer" ->
                    devices.stream().collect(Collectors.groupingBy(Device::getManufacturer, Collectors.counting()));
            case "Location" ->
                    devices.stream().collect(Collectors.groupingBy(Device::getLocation, Collectors.counting()));
            case "Year" -> devices.stream().filter(d -> d.getYear() != null)
                    .collect(Collectors.groupingBy(d -> d.getYear().toString(), Collectors.counting()));
            default -> throw new IllegalArgumentException("Не поддерживаемый тип отчёта");
        };
    }
}
