package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Класс для работы с экспортом данных
 *
 * @author vladimir_shi
 * @since 23.08.2025
 */

public class ExcelExportReportsService {
    // логгер для сообщений
    private static final Logger LOGGER = Logger.getLogger(ExcelExportReportsService.class.getName());

    // Обновлено: принимает File, возвращает boolean
    public boolean exportReport(List<Device> devices, String reportKey, File file) {
        if (file == null) {
            LOGGER.warning("Файл для экспорта не указан");
            return false;
        }

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

            LOGGER.info("Отчёт " + reportKey + " успешно экспортирован: " + file.getAbsolutePath());  // Logger вместо Alert
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ошибка экспорта отчёта " + reportKey + ": " + e.getMessage());  // Logger вместо Alert
            return false;
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