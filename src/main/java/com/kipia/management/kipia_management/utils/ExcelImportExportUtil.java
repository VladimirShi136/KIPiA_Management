package com.kipia.management.kipia_management.utils;

import com.kipia.management.kipia_management.controllers.DevicesGroupedController;
import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Класс с утилитами для работы с импортом и экспортом таблицы БД
 *
 * @author vladimir_shi
 * @since 12.09.2025
 */
public class ExcelImportExportUtil {
    // --- Логгер ---
    private static final Logger LOGGER = Logger.getLogger(ExcelImportExportUtil.class.getName());
    // --- Список заголовков столбцов ---
    private static final String[] HEADERS = {
            "Тип прибора", "Модель", "Завод изготовитель", "Инв. №", "Год выпуска",
            "Предел измерений", "Класс точности", "Место установки", "Кран №",
            "Статус", "Доп. информация"
    };

    // --- Основные методы ---

    /**
     * Экспорт списка приборов в Excel
     *
     * @param ownerWindow окно, которое вызвало экспорт
     * @param devices     список приборов
     * @return true если экспорт успешен, false если ошибка (с логгированием)
     */
    public static boolean exportDevicesToExcel(Window ownerWindow, List<Device> devices) {
        File file = showSaveDialog(ownerWindow, "Экспорт в Excel");
        if (file == null) return false;  // Пользователь отменил
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Devices");
            setupSheetForPrinting(sheet);
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle cellStyle = createCellStyle(wb);
            createHeaderRow(sheet, headerStyle);
            for (int i = 0; i < devices.size(); i++) {
                Row row = sheet.createRow(i + 1);
                fillDeviceRow(devices.get(i), row, cellStyle);
            }
            setColumnWidths(sheet);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                wb.write(fos);
            }
            LOGGER.info("Экспорт завершён: " + file.getAbsolutePath());  // Logger для уведомлений
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ошибка экспорта: " + e.getMessage());  // Logger для ошибок
            return false;
        }
    }

    /**
     * Импорт списка приборов из Excel
     *
     * @param ownerWindow     окно, которое вызвало импорт
     * @param deviceDAO       сервис работы с БД
     * @param onSuccessUpdate действие после успешного импорта
     * @param onError         действие при ошибке импорта
     * @return результат импорта
     */
    public static String importDevicesFromExcel(Window ownerWindow,
                                                DeviceDAO deviceDAO,
                                                Runnable onSuccessUpdate,
                                                Runnable onError) {
        File file = showOpenDialog(ownerWindow, "Импорт из Excel");
        if (file == null) return null;  // Пользователь отменил
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheet("Devices");
            if (sheet == null) {
                LOGGER.severe("Лист 'Devices' не найден в файле");  // Логгирование ошибки
                runSafe(onError);
                return null;
            }
            List<Device> devices = parseDevicesFromSheet(sheet, 1);
            int[] counts = processDevices(deviceDAO, devices);
            runSafe(onSuccessUpdate);
            String result = "Импорт завершён!\nДобавлено: " + counts[0] + "\nОбновлено: " + counts[1];
            LOGGER.info(result);  // Логгирование успеха
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ошибка импорта: " + e.getMessage());  // Логгирование ошибки
            runSafe(onError);
            return null;
        }
    }

    /**
     * Экспорт группированной таблицы в Excel
     *
     * @param ownerWindow окно, которое вызвало экспорт
     * @param treeTable   группированная таблица
     * @return true если экспорт успешен, false если ошибка (с логгированием)
     */
    public static boolean exportGroupedTreeTableToExcel(Window ownerWindow,
                                                        TreeTableView<DevicesGroupedController.TreeRowItem> treeTable) {
        File file = showSaveDialog(ownerWindow, "Экспорт группированной таблицы в Excel");
        if (file == null) return false;  // Пользователь отменил
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("GroupedDevices");
            setupSheetForPrinting(sheet);
            CellStyle groupStyle = createGroupStyle(wb);
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle cellStyle = createCellStyle(wb);
            int rowNum = 0;
            for (TreeItem<DevicesGroupedController.TreeRowItem> groupNode : treeTable.getRoot().getChildren()) {
                if (groupNode.getValue() instanceof DevicesGroupedController.GroupItem(String location)) {
                    Row groupRow = sheet.createRow(rowNum++);
                    Cell groupCell = groupRow.createCell(0);
                    groupCell.setCellValue(location);
                    groupCell.setCellStyle(groupStyle);
                    Row headerRow = sheet.createRow(rowNum++);
                    headerRow.setHeightInPoints(40);
                    for (int i = 0; i < HEADERS.length; i++) {
                        Cell cell = headerRow.createCell(i);
                        cell.setCellValue(HEADERS[i]);
                        cell.setCellStyle(headerStyle);
                    }
                    for (TreeItem<DevicesGroupedController.TreeRowItem> deviceNode : groupNode.getChildren()) {
                        if (deviceNode.getValue() instanceof DevicesGroupedController.DeviceItem(Device device)) {
                            Row row = sheet.createRow(rowNum++);
                            fillDeviceRow(device, row, cellStyle);
                        }
                    }
                }
            }
            setColumnWidths(sheet);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                wb.write(fos);
            }
            LOGGER.info("Экспорт завершён: " + file.getAbsolutePath());  // Логгирование успеха
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ошибка экспорта: " + e.getMessage());  // Логгирование ошибки
            return false;
        }
    }

    /**
     * Импорт группированной таблицы из Excel
     *
     * @param ownerWindow     окно, которое вызвало импорт
     * @param deviceDAO       сервис работы с БД
     * @param treeTable       группированная таблица
     * @param onSuccessUpdate действие после успешного импорта
     * @param onError         действие при ошибке импорта
     */
    public static void importGroupedTreeTableFromExcel(Window ownerWindow,
                                                       DeviceDAO deviceDAO,
                                                       TreeTableView<DevicesGroupedController.TreeRowItem> treeTable,
                                                       Runnable onSuccessUpdate,
                                                       Runnable onError) {
        File file = showOpenDialog(ownerWindow, "Импорт группированной таблицы из Excel");
        if (file == null) return;  // Пользователь отменил
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheet("GroupedDevices");
            if (sheet == null) {
                LOGGER.severe("Лист 'GroupedDevices' не найден в файле");  // Логгирование ошибки
                runSafe(onError);
                return;
            }
            TreeItem<DevicesGroupedController.TreeRowItem> root = new TreeItem<>();
            root.setExpanded(true);
            TreeItem<DevicesGroupedController.TreeRowItem> currentGroupNode = null;
            int imported = 0, updated = 0;
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String firstCellValue = getStringCell(row, 0);
                boolean otherCellsEmpty = true;
                for (int col = 1; col <= 10; col++) {
                    if (row.getCell(col) != null && !getStringCell(row, col).isBlank()) {
                        otherCellsEmpty = false;
                        break;
                    }
                }
                if (otherCellsEmpty && !isBlank(firstCellValue)) {
                    DevicesGroupedController.GroupItem groupItem = new DevicesGroupedController.GroupItem(firstCellValue);
                    currentGroupNode = new TreeItem<>(groupItem);
                    currentGroupNode.setExpanded(true);
                    root.getChildren().add(currentGroupNode);
                } else if (currentGroupNode != null) {
                    Device d = parseDeviceFromRow(row);
                    if (isBlank(d.getInventoryNumber())) continue;
                    int[] result = processDevice(deviceDAO, d);
                    imported += result[0];
                    updated += result[1];
                    DevicesGroupedController.DeviceItem deviceItem = new DevicesGroupedController.DeviceItem(d);
                    TreeItem<DevicesGroupedController.TreeRowItem> deviceNode = new TreeItem<>(deviceItem);
                    currentGroupNode.getChildren().add(deviceNode);
                }
            }
            treeTable.setRoot(root);
            runSafe(onSuccessUpdate);
            String result = "Импорт завершён!\nДобавлено: " + imported + "\nОбновлено: " + updated;
            LOGGER.info(result);  // Логгирование успеха
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ошибка импорта: " + e.getMessage());  // Логгирование ошибки
            runSafe(onError);
        }
    }

    // --- Вспомогательные методы ---

    /**
     * Настраивает лист для печати
     *
     * @param sheet лист
     */
    private static void setupSheetForPrinting(Sheet sheet) {
        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setLandscape(true);
        sheet.setFitToPage(true);
        sheet.setAutobreaks(true);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 0);
    }

    /**
     * Создает стиль заголовка
     *
     * @param wb workbook
     * @return стиль
     */
    private static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(style, BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    /**
     * Создает стиль группы
     *
     * @param wb workbook
     * @return стиль
     */
    private static CellStyle createGroupStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(style, BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    /**
     * Создает стиль ячейки
     *
     * @param wb workbook
     * @return стиль
     */
    private static CellStyle createCellStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        setBorders(style, BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /**
     * Создает строку заголовков
     *
     * @param sheet лист
     * @param style стиль
     */
    private static void createHeaderRow(Sheet sheet, CellStyle style) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(40);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    /**
     * Заполняет строку прибора
     *
     * @param d     прибор
     * @param row   строка
     * @param style стиль
     */
    private static void fillDeviceRow(Device d, Row row, CellStyle style) {
        createCell(row, 0, nullToEmpty(d.getType()), style);
        createCell(row, 1, nullToEmpty(d.getName()), style);
        createCell(row, 2, nullToEmpty(d.getManufacturer()), style);
        createCell(row, 3, nullToEmpty(d.getInventoryNumber()), style);
        createCell(row, 4, d.getYear() == null ? "" : d.getYear().toString(), style);
        createCell(row, 5, nullToEmpty(d.getMeasurementLimit()), style);
        createCell(row, 6, String.valueOf(d.getAccuracyClass() == null ? 0.0 : d.getAccuracyClass()), style);
        createCell(row, 7, nullToEmpty(d.getLocation()), style);
        createCell(row, 8, nullToEmpty(d.getValveNumber()), style);
        createCell(row, 9, nullToEmpty(d.getStatus()), style);
        createCell(row, 10, nullToEmpty(d.getAdditionalInfo()), style);
    }

    /**
     * Считывает приборы из листа
     *
     * @param sheet    лист
     * @param startRow начало чтения
     * @return список приборов
     */
    private static List<Device> parseDevicesFromSheet(Sheet sheet, int startRow) {
        List<Device> devices = new ArrayList<>();
        for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                devices.add(parseDeviceFromRow(row));
            }
        }
        return devices;
    }

    /**
     * Считывает прибор из строки
     *
     * @param row строка
     * @return прибор
     */
    private static Device parseDeviceFromRow(Row row) {
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
        return d;
    }

    /**
     * Обрабатывает список приборов
     *
     * @param deviceDAO DAO приборов
     * @param devices   список приборов
     * @return массив с количеством импортированных и обновленных приборов
     */
    private static int[] processDevices(DeviceDAO deviceDAO, List<Device> devices) {
        int imported = 0, updated = 0;
        for (Device d : devices) {
            if (isBlank(d.getInventoryNumber())) continue;
            int[] result = processDevice(deviceDAO, d);
            imported += result[0];
            updated += result[1];
        }
        return new int[]{imported, updated};
    }

    /**
     * Обрабатывает прибор
     *
     * @param deviceDAO DAO приборов
     * @param d         прибор
     * @return массив с количеством импортированных и обновленных приборов
     */
    private static int[] processDevice(DeviceDAO deviceDAO, Device d) {
        Device existing = deviceDAO.findDeviceByInventoryNumber(d.getInventoryNumber());
        if (existing != null) {
            updateDevice(existing, d);
            deviceDAO.updateDevice(existing);
            return new int[]{0, 1};
        } else {
            deviceDAO.addDevice(d);
            return new int[]{1, 0};
        }
    }

    /**
     * Обновляет прибор в базе данных
     *
     * @param existing существующий прибор
     * @param d        новый прибор
     */
    private static void updateDevice(Device existing, Device d) {
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
    }

    /**
     * Устанавливает ширину столбцов
     *
     * @param sheet лист
     */
    private static void setColumnWidths(Sheet sheet) {
        int[] colWidths = {4000, 3000, 3500, 3000, 2000, 3500, 2500, 4000, 2500, 2500, 5000};
        for (int i = 0; i < HEADERS.length; i++) {
            sheet.setColumnWidth(i, colWidths[i]);
        }
    }

    /**
     * Показывает диалог сохранения
     *
     * @param window окно
     * @param title  заголовок
     * @return выбранный файл
     */
    private static File showSaveDialog(Window window, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        return chooser.showSaveDialog(window);
    }

    /**
     * Показывает диалог открытия
     *
     * @param window окно
     * @param title  заголовок
     * @return выбранный файл
     */
    private static File showOpenDialog(Window window, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        return chooser.showOpenDialog(window);
    }

    /**
     * Защищает от null
     *
     * @param runnable runnable
     */
    private static void runSafe(Runnable runnable) {
        if (runnable != null) runnable.run();
    }

    /**
     * Проверяет, пустая ли строка
     *
     * @param s строка
     * @return true, если строка пустая
     */
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Устанавливает границы ячейки
     *
     * @param style  стиль
     * @param border стиль границы
     */
    private static void setBorders(CellStyle style, BorderStyle border) {
        style.setBorderTop(border);
        style.setBorderBottom(border);
        style.setBorderLeft(border);
        style.setBorderRight(border);
    }

    /**
     * Заполняет ячейку
     *
     * @param row      строка
     * @param colIndex индекс ячейки
     * @param value    значение
     * @param style    стиль
     */
    private static void createCell(Row row, int colIndex, String value, CellStyle style) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    /**
     * Возвращает значение ячейки, если она не null, иначе пустую строку
     *
     * @param row    строка
     * @param colIdx индекс ячейки
     * @return строка
     */
    private static String getStringCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    /**
     * Возвращает строку, если она не null, иначе пустую строку
     *
     * @param s строка
     * @return строка
     */
    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
