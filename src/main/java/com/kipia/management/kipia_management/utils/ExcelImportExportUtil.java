package com.kipia.management.kipia_management.utils;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Класс с утилитами для работы с импортом и экспортом таблицы БД
 *
 * @author vladimir_shi
 * @since 12.09.2025
 */

public class ExcelImportExportUtil {

    /**
     * Экспорт данных из списка устройств в Excel файл.
     */
    public static void exportDevicesToExcel(Window ownerWindow, List<Device> devices) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт в Excel");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        File file = chooser.showSaveDialog(ownerWindow);
        if (file == null) return;

        try (var wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Devices");
            String[] headers = {"Тип прибора", "Модель", "Производитель",
                    "Инвентарный №", "Год выпуска", "Предел измерений",
                    "Класс точности", "Место установки", "Кран №",
                    "Статус", "Доп. информация"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int rowNum = 1;
            for (Device d : devices) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(nullToEmpty(d.getType()));
                row.createCell(1).setCellValue(nullToEmpty(d.getName()));
                row.createCell(2).setCellValue(nullToEmpty(d.getManufacturer()));
                row.createCell(3).setCellValue(nullToEmpty(d.getInventoryNumber()));
                row.createCell(4).setCellValue(d.getYear() == null ? "" : d.getYear().toString());
                row.createCell(5).setCellValue(nullToEmpty(d.getMeasurementLimit()));
                row.createCell(6).setCellValue(d.getAccuracyClass() == null ? 0.0 : d.getAccuracyClass());
                row.createCell(7).setCellValue(nullToEmpty(d.getLocation()));
                row.createCell(8).setCellValue(nullToEmpty(d.getValveNumber()));
                row.createCell(9).setCellValue(nullToEmpty(d.getStatus()));
                row.createCell(10).setCellValue(nullToEmpty(d.getAdditionalInfo()));
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            try (var out = new FileOutputStream(file)) {
                wb.write(out);
            }
            showAlert(Alert.AlertType.INFORMATION, "Экспорт завершён: " + file.getAbsolutePath());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка экспорта: " + e.getMessage());
        }
    }

    /**
     * Импорт данных из Excel файла с обновлением/добавлением устройств.
     */
    public static void importDevicesFromExcel(Window ownerWindow,
                                              DeviceDAO deviceDAO,
                                              Runnable onSuccessUpdate,
                                              Runnable onError) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Импорт из Excel");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        File file = chooser.showOpenDialog(ownerWindow);
        if (file == null) return;

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheet("Devices");
            if (sheet == null) {
                showAlert(Alert.AlertType.ERROR, "Лист 'Devices' не найден в файле");
                if (onError != null) onError.run();
                return;
            }
            int imported = 0, updated = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Device d = new Device();
                d.setType(getStringCell(row, 0));
                d.setName(getStringCell(row, 1));
                d.setManufacturer(getStringCell(row, 2));
                d.setInventoryNumber(getStringCell(row, 3));
                String yearStr = getStringCell(row, 4);
                if (!yearStr.isBlank()) {
                    try {
                        d.setYear(Integer.valueOf(yearStr));
                    } catch (NumberFormatException ignored) {
                    }
                }
                d.setMeasurementLimit(getStringCell(row, 5));
                String accStr = getStringCell(row, 6);
                if (!accStr.isBlank()) {
                    try {
                        d.setAccuracyClass(Double.valueOf(accStr));
                    } catch (NumberFormatException ignored) {
                    }
                }
                d.setLocation(getStringCell(row, 7));
                d.setValveNumber(getStringCell(row, 8));
                d.setStatus(getStringCell(row, 9));
                d.setAdditionalInfo(getStringCell(row, 10));
                d.setPhotos(new ArrayList<>());

                if (d.getInventoryNumber() == null || d.getInventoryNumber().isBlank())
                    continue;

                Device existing = deviceDAO.findDeviceByInventoryNumber(d.getInventoryNumber());
                if (existing != null) {
                    // Обновляем существующее
                    existing.setType(d.getType());
                    existing.setName(d.getName());
                    existing.setManufacturer(d.getManufacturer());
                    existing.setYear(d.getYear());
                    existing.setMeasurementLimit(d.getMeasurementLimit());
                    existing.setAccuracyClass(d.getAccuracyClass());
                    existing.setLocation(d.getLocation());
                    existing.setValveNumber(d.getValveNumber());
                    existing.setStatus(d.getStatus());
                    existing.setAdditionalInfo(d.getAdditionalInfo());
                    deviceDAO.updateDevice(existing);
                    updated++;
                } else {
                    // Добавляем новое
                    deviceDAO.addDevice(d);
                    imported++;
                }
            }
            if (onSuccessUpdate != null) onSuccessUpdate.run();
            showAlert(Alert.AlertType.INFORMATION,
                    "Импорт завершён!\nДобавлено: " + imported +
                            "\nОбновлено: " + updated);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка импорта: " + e.getMessage());
            if (onError != null) onError.run();
        }
    }

    // Вспомогательные методы

    private static String getStringCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static void showAlert(Alert.AlertType type, String text) {
        Alert alert = new Alert(type, text);
        alert.show();
    }
}
