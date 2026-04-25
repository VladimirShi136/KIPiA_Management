package com.kipia.management.kipia_management.utils;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Класс с утилитами для работы с импортом и экспортом таблицы БД.
 * <p>
 * Поддерживает {@link LoadingIndicator} — передайте экземпляр в методы,
 * чтобы индикатор отображался во время длительных операций.
 * Передайте {@code null}, если индикатор не нужен.
 *
 * @author vladimir_shi
 * @since 12.09.2025
 */
public class ExcelImportExportUtil {

    // --- Логгер ---
    private static final Logger LOGGER = LogManager.getLogger(ExcelImportExportUtil.class);

    // --- Текст информационной строки (строка 0 перед заголовком) ---
    private static final String INFO_TEXT =
            "* — обязательные поля для заполнения. " +
                    "Если статус «Хранение» — место установки: СКЛАД. " +
                    "Если статус «Утерян» — место установки: УТЕРЯННЫЕ.";

    // --- Список заголовков столбцов (звёздочки у обязательных полей) ---
    private static final String[] HEADERS = {
            "Тип прибора *", "Модель *", "Завод изготовитель", "Инв. № *",
            "Год выпуска", "Предел измерений", "Класс точности",
            "Место установки *", "Кран №", "Статус *", "Доп. информация"
    };  // Потом добавить "Дата обновления"

    // Индексы обязательных столбцов (0-based): Тип прибора, Модель, Инв.№, Место установки, Статус
    private static final int[] REQUIRED_COL_INDICES = {0, 1, 3, 7, 9};

    // --- Основные методы ---

    /**
     * Экспорт списка приборов в Excel.
     * <p>
     * Показывает {@code loadingIndicator} на время записи файла,
     * если индикатор не {@code null}.
     *
     * @param ownerWindow      окно, которое вызвало экспорт
     * @param devices          список приборов
     * @param loadingIndicator индикатор загрузки (может быть {@code null})
     * @return true если экспорт успешен, false если ошибка (с логгированием)
     */
    public static boolean exportDevicesToExcel(Window ownerWindow,
                                               List<Device> devices,
                                               LoadingIndicator loadingIndicator) {
        File file = showSaveDialog(ownerWindow);
        if (file == null) return false;  // Пользователь отменил

        showLoading(loadingIndicator, "Экспорт в Excel...");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Devices");
            setupSheetForPrinting(sheet);

            CellStyle infoStyle     = createInfoStyle(wb);
            CellStyle headerStyle   = createHeaderStyle(wb);
            CellStyle requiredStyle = createRequiredHeaderStyle(wb);
            CellStyle cellStyle     = createCellStyle(wb);

            createInfoRow(sheet, infoStyle);   // строка 0 — подсказка
            createHeaderRow(sheet, headerStyle, requiredStyle);  // строка 1 — заголовки

            for (int i = 0; i < devices.size(); i++) {
                Row row = sheet.createRow(i + 2);  // данные с строки 2
                fillDeviceRow(devices.get(i), row, cellStyle);
            }

            setColumnWidths(sheet);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                wb.write(fos);
            }
            LOGGER.info("Экспорт завершён: {}", file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            LOGGER.error("Ошибка экспорта: {}", e.getMessage(), e);
            return false;
        } finally {
            hideLoading(loadingIndicator);
        }
    }

    /**
     * Экспорт без индикатора загрузки (обратная совместимость).
     */
    public static boolean exportDevicesToExcel(Window ownerWindow, List<Device> devices) {
        return exportDevicesToExcel(ownerWindow, devices, null);
    }

    /**
     * Импорт списка приборов из Excel.
     * <p>
     * Показывает {@code loadingIndicator} на время чтения и записи в БД,
     * если индикатор не {@code null}.
     *
     * @param ownerWindow      окно, которое вызвало импорт
     * @param deviceDAO        сервис работы с БД
     * @param onSuccessUpdate  действие после успешного импорта
     * @param onError          действие при ошибке импорта
     * @param loadingIndicator индикатор загрузки (может быть {@code null})
     * @return результат импорта или {@code null} при ошибке / отмене
     */
    public static String importDevicesFromExcel(Window ownerWindow,
                                                DeviceDAO deviceDAO,
                                                Runnable onSuccessUpdate,
                                                Runnable onError,
                                                LoadingIndicator loadingIndicator) {
        File file = showOpenDialog(ownerWindow);
        if (file == null) return null;  // Пользователь отменил

        showLoading(loadingIndicator, "Импорт из Excel...");
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheet("Devices");
            if (sheet == null) {
                LOGGER.error("Лист 'Devices' не найден в файле");
                runSafe(onError);
                return null;
            }
            List<Device> devices = parseDevicesFromSheet(sheet);
            int[] counts = processDevices(deviceDAO, devices);
            runSafe(onSuccessUpdate);
            String result = "Импорт завершён!\nДобавлено: " + counts[0] + "\nОбновлено: " + counts[1];
            LOGGER.info(result);
            return result;
        } catch (Exception e) {
            LOGGER.error("Ошибка импорта: {}", e.getMessage(), e);
            runSafe(onError);
            return null;
        } finally {
            hideLoading(loadingIndicator);
        }
    }

    /**
     * Импорт без индикатора загрузки (обратная совместимость).
     */
    public static String importDevicesFromExcel(Window ownerWindow,
                                                DeviceDAO deviceDAO,
                                                Runnable onSuccessUpdate,
                                                Runnable onError) {
        return importDevicesFromExcel(ownerWindow, deviceDAO, onSuccessUpdate, onError, null);
    }

    // --- Вспомогательные методы ---

    /**
     * Настраивает лист для печати.
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
     * Создаёт стиль информационной строки (серый фон, курсив, перенос текста).
     */
    private static CellStyle createInfoStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style);
        return style;
    }

    /**
     * Создаёт стиль заголовка для обычных столбцов.
     */
    private static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(style);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    /**
     * Создаёт стиль заголовка для обязательных столбцов (более тёмный фон + красная звёздочка
     * выделена шрифтом — цвет шрифта красный, так как POI не поддерживает rich-text в
     * обычных CellStyle; для полного эффекта используйте XSSFRichTextString в createHeaderRow).
     */
    private static CellStyle createRequiredHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.DARK_RED.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.TAN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(style);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    /**
     * Создаёт стиль ячейки данных.
     */
    private static CellStyle createCellStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        setBorders(style);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /**
     * Создаёт информационную строку (строка 0) с подсказкой по обязательным полям.
     * Ячейки объединяются по всей ширине таблицы.
     */
    private static void createInfoRow(Sheet sheet, CellStyle infoStyle) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(36);
        Cell cell = row.createCell(0);
        cell.setCellValue(INFO_TEXT);
        cell.setCellStyle(infoStyle);
        // Объединяем все столбцы в одну строку
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));
        // Пустые ячейки в объединённом диапазоне должны иметь тот же стиль
        for (int i = 1; i < HEADERS.length; i++) {
            Cell c = row.createCell(i);
            c.setCellStyle(infoStyle);
        }
    }

    /**
     * Создаёт строку заголовков (строка 1).
     * Обязательные столбцы выделяются отдельным стилем.
     */
    private static void createHeaderRow(Sheet sheet,
                                        CellStyle headerStyle,
                                        CellStyle requiredStyle) {
        Row row = sheet.createRow(1);
        row.setHeightInPoints(40);

        // Формируем набор индексов обязательных столбцов для быстрой проверки
        java.util.Set<Integer> required = new java.util.HashSet<>();
        for (int idx : REQUIRED_COL_INDICES) required.add(idx);

        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(required.contains(i) ? requiredStyle : headerStyle);
        }
    }

    /**
     * Заполняет строку прибора.
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
     * Считывает приборы из листа.
     * Пропускает строку 0 (инфо) и строку 1 (заголовки), данные — с строки 2.
     */
    private static List<Device> parseDevicesFromSheet(Sheet sheet) {
        List<Device> devices = new ArrayList<>();
        // Начинаем с 2 (0 — инфо, 1 — заголовки)
        int dataStart = 2;
        // Поддержка старых файлов: если строка 1 похожа на заголовок, начинаем с 1
        Row firstRow = sheet.getRow(0);
        if (firstRow != null) {
            Cell firstCell = firstRow.getCell(0);
            if (firstCell != null) {
                String val = firstCell.getStringCellValue();
                // Старый формат: строка 0 — это сразу заголовок (нет инфо-строки)
                if (val != null && val.startsWith("Тип")) {
                    dataStart = 1;
                }
            }
        }
        for (int i = dataStart; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                devices.add(parseDeviceFromRow(row));
            }
        }
        return devices;
    }

    /**
     * Считывает прибор из строки.
     */
    private static Device parseDeviceFromRow(Row row) {
        Device d = new Device();
        d.setType(getStringCell(row, 0));
        d.setName(getStringCell(row, 1));
        d.setManufacturer(getStringCell(row, 2));
        d.setInventoryNumber(getStringCell(row, 3));
        String yearStr = getStringCell(row, 4);
        if (!yearStr.isBlank()) {
            try { d.setYear(Integer.valueOf(yearStr)); } catch (NumberFormatException ignored) {}
        }
        d.setMeasurementLimit(getStringCell(row, 5));
        String accStr = getStringCell(row, 6);
        if (!accStr.isBlank()) {
            try { d.setAccuracyClass(Double.valueOf(accStr)); } catch (NumberFormatException ignored) {}
        }
        d.setLocation(getStringCell(row, 7));
        d.setValveNumber(getStringCell(row, 8));
        d.setStatus(getStringCell(row, 9));
        d.setAdditionalInfo(getStringCell(row, 10));
        String updatedAtStr = getStringCell(row, 11);
        if (!updatedAtStr.trim().isEmpty()) {
            try {
                java.util.Date date = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
                        .parse(updatedAtStr.trim());
                d.setUpdatedAt(date.getTime());
            } catch (java.text.ParseException e) {
                d.setUpdatedAt(System.currentTimeMillis());
            }
        } else {
            d.setUpdatedAt(System.currentTimeMillis());
        }
        d.setPhotos(new ArrayList<>());
        return d;
    }

    /**
     * Обрабатывает список приборов.
     * Сначала валидирует все записи на наличие обязательных полей.
     * Если хотя бы одна запись невалидна - выбрасывает исключение и импорт отменяется.
     *
     * @param deviceDAO сервис работы с БД
     * @param devices список приборов для импорта
     * @return массив [количество добавленных, количество обновленных]
     * @throws RuntimeException если хотя бы один прибор имеет пустые обязательные поля
     */
    private static int[] processDevices(DeviceDAO deviceDAO, List<Device> devices) {
        // Сначала валидация всех записей
        for (Device d : devices) {
            if (isBlank(d.getInventoryNumber()) ||
                    isBlank(d.getType()) ||
                    isBlank(d.getName()) ||
                    isBlank(d.getLocation()) ||
                    isBlank(d.getStatus())) {
                throw new RuntimeException(
                        "Импорт отменён: прибор с инв.№ '" + d.getInventoryNumber() +
                                "' имеет пустые обязательные поля. Проверьте столбцы: Тип, Модель, Инв.№, Место установки, Статус."
                );
            }
        }

        // Если все записи валидны - выполняем импорт
        int imported = 0, updated = 0;
        for (Device d : devices) {
            int[] result = processDevice(deviceDAO, d);
            imported += result[0];
            updated  += result[1];
        }
        return new int[]{imported, updated};
    }

    /**
     * Обрабатывает один прибор (добавляет или обновляет).
     * Поиск существующего прибора выполняется по инвентарному номеру.
     *
     * @param deviceDAO сервис работы с БД
     * @param d прибор для обработки
     * @return массив [1, 0] если добавлен новый, [0, 1] если обновлен существующий
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
     * Обновляет поля существующего прибора.
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
     * Устанавливает ширину столбцов.
     */
    private static void setColumnWidths(Sheet sheet) {
        int[] colWidths = {4000, 3000, 3500, 3000, 2000, 3500, 2500, 4000, 2500, 2500, 5000};
        for (int i = 0; i < HEADERS.length; i++) {
            sheet.setColumnWidth(i, colWidths[i]);
        }
    }

    /**
     * Показывает диалог сохранения — вызывать только из JavaFX-потока.
     */
    public static File showSaveDialogPublic(Window window) {
        return showSaveDialog(window);
    }

    /**
     * Показывает диалог открытия — вызывать только из JavaFX-потока.
     */
    public static File showOpenDialogPublic(Window window) {
        return showOpenDialog(window);
    }

    /**
     * Экспорт в уже выбранный файл (без FileChooser) — безопасно вызывать из фонового потока.
     */
    public static boolean exportDevicesToFile(File file, List<Device> devices) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Devices");
            setupSheetForPrinting(sheet);

            CellStyle infoStyle     = createInfoStyle(wb);
            CellStyle headerStyle   = createHeaderStyle(wb);
            CellStyle requiredStyle = createRequiredHeaderStyle(wb);
            CellStyle cellStyle     = createCellStyle(wb);

            createInfoRow(sheet, infoStyle);
            createHeaderRow(sheet, headerStyle, requiredStyle);

            for (int i = 0; i < devices.size(); i++) {
                Row row = sheet.createRow(i + 2);
                fillDeviceRow(devices.get(i), row, cellStyle);
            }

            setColumnWidths(sheet);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                wb.write(fos);
            }
            LOGGER.info("Экспорт завершён: {}", file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            LOGGER.error("Ошибка экспорта: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Импорт из уже выбранного файла (без FileChooser) — безопасно вызывать из фонового потока.
     */
    public static String importDevicesFromFile(File file,
                                               DeviceDAO deviceDAO,
                                               Runnable onSuccessUpdate,
                                               Runnable onError) {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheet("Devices");
            if (sheet == null) {
                LOGGER.error("Лист 'Devices' не найден в файле");
                runSafe(onError);
                return null;
            }
            List<Device> devices = parseDevicesFromSheet(sheet);
            int[] counts = processDevices(deviceDAO, devices);
            runSafe(onSuccessUpdate);
            String result = "Импорт завершён!\nДобавлено: " + counts[0] + "\nОбновлено: " + counts[1];
            LOGGER.info(result);
            return result;
        } catch (Exception e) {
            LOGGER.error("Ошибка импорта: {}", e.getMessage(), e);
            runSafe(onError);
            return null;
        }
    }

    /**
     * Показывает диалог сохранения.
     */
    private static File showSaveDialog(Window window) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт в Excel");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        return chooser.showSaveDialog(window);
    }

    /**
     * Показывает диалог открытия.
     */
    private static File showOpenDialog(Window window) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Импорт из Excel");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel файлы", "*.xlsx"));
        return chooser.showOpenDialog(window);
    }

    // --- Методы работы с LoadingIndicator ---

    private static void showLoading(LoadingIndicator indicator, String message) {
        if (indicator != null) {
            indicator.setMessage(message);
            indicator.show();
        }
    }

    private static void hideLoading(LoadingIndicator indicator) {
        if (indicator != null) {
            indicator.hide();
        }
    }

    // --- Прочие вспомогательные методы ---

    private static void runSafe(Runnable runnable) {
        if (runnable != null) runnable.run();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static void createCell(Row row, int colIndex, String value, CellStyle style) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    private static String getStringCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}