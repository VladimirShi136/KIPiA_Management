package com.kipia.management.kipia_management.services;

import com.kipia.management.kipia_management.models.Device;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kipia.management.kipia_management.shapes.DonutChart;

import java.util.Collections;
import java.util.LinkedHashMap;
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
            case "Status" -> groupByStatusOrdered(devices);
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

    // Группировка по статусу с фиксированным порядком ключей (В работе, Хранение, Утерян, Испорчен),
    // чтобы порядок сегментов в DonutChart был стабильным между обновлениями
    private Map<String, Long> groupByStatusOrdered(List<Device> devices) {
        Map<String, Long> unordered = groupBy(devices, Device::getStatus);
        Map<String, Long> ordered = new LinkedHashMap<>();

        for (String status : DonutChart.STATUS_ORDER) {
            Long count = unordered.get(status);
            if (count != null) {
                ordered.put(status, count);
            }
        }
        // Добавляем статусы, не входящие в стандартный список, в конец
        for (Map.Entry<String, Long> entry : unordered.entrySet()) {
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }

        return ordered;
    }

    private Map<String, Long> groupBy(List<Device> devices, Function<Device, String> classifier) {
        return devices.stream()
                .filter(d -> classifier.apply(d) != null && !classifier.apply(d).isEmpty())
                .collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }
}