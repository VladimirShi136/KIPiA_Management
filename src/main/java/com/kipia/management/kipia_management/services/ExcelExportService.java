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

public class ExcelExportService {

    public void exportReport(List<Device> devices, String reportKey, Stage primaryStage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт отчёта " + reportKey + " в Excel");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        File file = chooser.showSaveDialog(primaryStage);
        if (file == null) return;

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Report");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue(getHeaderForType(reportKey));
            headerRow.createCell(1).setCellValue("Количество");

            Map<String, Long> countMap = getCountMap(devices, reportKey);

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

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Отчёт " + reportKey + " экспортирован: " + file.getAbsolutePath());
            alert.show();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Ошибка: " + e.getMessage());
            alert.show();
        }
    }

    private String getHeaderForType(String reportKey) {
        return switch (reportKey) {
            case "Status" -> "Статус";
            case "Type" -> "Тип прибора";
            case "Manufacturer" -> "Производитель";
            case "Location" -> "Местоположение";
            case "Year" -> "Год выпуска";
            default -> "Категория";
        };
    }

    private Map<String, Long> getCountMap(List<Device> devices, String reportKey) {
        return switch (reportKey) {
            case "Status" -> devices.stream().collect(Collectors.groupingBy(Device::getStatus, Collectors.counting()));
            case "Type" -> devices.stream().collect(Collectors.groupingBy(Device::getType, Collectors.counting()));
            case "Manufacturer" -> devices.stream().collect(Collectors.groupingBy(Device::getManufacturer, Collectors.counting()));
            case "Location" -> devices.stream().collect(Collectors.groupingBy(Device::getLocation, Collectors.counting()));
            case "Year" -> devices.stream()
                    .filter(d -> d.getYear() != null)
                    .collect(Collectors.groupingBy(d -> d.getYear().toString(), Collectors.counting()));
            default -> throw new IllegalArgumentException("Не поддерживаемый тип отчёта");
        };
    }
}
