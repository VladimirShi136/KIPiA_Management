package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import com.kipia.management.kipia_management.services.DeviceReportService;
import com.kipia.management.kipia_management.shapes.DonutChart;
import com.kipia.management.kipia_management.utils.LoadingIndicator;
import com.kipia.management.kipia_management.utils.StyleUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportsController implements SearchableController {

    private static final Logger LOGGER = LogManager.getLogger(ReportsController.class);

    @FXML private StackPane rootPane;
    @FXML private VBox contentBox;
    @FXML private Label titleLabel;
    @FXML private BorderPane chartPane;
    @FXML private HBox workingBar;
    @FXML private HBox storageBar;
    @FXML private HBox lostBar;
    @FXML private HBox brokenBar;
    @FXML private Label workingLabel;
    @FXML private Label storageLabel;
    @FXML private Label lostLabel;
    @FXML private Label brokenLabel;

    private DeviceReportService reportService;
    private Stage primaryStage;
    private List<Device> allDevices;
    private FilteredList<Device> filteredDevices;
    private DonutChart donutChart;
    private LoadingIndicator loadingIndicator;
    private boolean isProgrammaticChange = false; // Флаг для программных изменений
    private ComboBox<String> reportFilterTypeCombo; // Ссылка на комбобокс типа фильтра
    private ComboBox<String> reportFilterValueCombo; // Ссылка на комбобокс значения

    // Фильтры
    private ComboBox<String> statusFilter;
    private ComboBox<String> typeFilter;
    private ComboBox<String> manufacturerFilter;
    private ComboBox<String> locationFilter;
    private ComboBox<String> yearFilter;

    public void init(DeviceDAO deviceDAO, Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.reportService = new DeviceReportService();
        this.allDevices = deviceDAO.getAllDevices();
        this.filteredDevices = new FilteredList<>(FXCollections.observableArrayList(allDevices));

        loadingIndicator = new LoadingIndicator("Загрузка отчётов...");
        if (rootPane != null) {
            rootPane.getChildren().add(loadingIndicator.getOverlay());
        }

        hideContentBeforeLoad();
        loadDataAsync();
    }

    private void hideContentBeforeLoad() {
        if (contentBox != null) {
            contentBox.setVisible(false);
            contentBox.setManaged(false);
        }
    }

    private void showContentAfterLoad() {
        if (contentBox != null) {
            contentBox.setVisible(true);
            contentBox.setManaged(true);
        }
    }

    private void loadDataAsync() {
        Platform.runLater(() -> loadingIndicator.show());

        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long startTime = System.currentTimeMillis();

                Platform.runLater(() -> {
                    updateReport();
                    updateStatusBars();
                });

                long elapsedTime = System.currentTimeMillis() - startTime;
                long minDisplayTime = 300;

                if (elapsedTime < minDisplayTime) {
                    Thread.sleep(minDisplayTime - elapsedTime);
                }

                return null;
            }
        };

        loadTask.setOnSucceeded(_ -> {
            showContentAfterLoad();
            loadingIndicator.hide();
            updateStatusBarsColors();
            updateHeaderIconColor();
        });

        loadTask.setOnFailed(_ -> {
            LOGGER.error("Ошибка генерации отчёта: {}", loadTask.getException().getMessage());
            showContentAfterLoad();
            loadingIndicator.hide();
        });

        new Thread(loadTask).start();
    }

    @Override
    public void bindSearchField(javafx.scene.control.TextField searchField) {
        // Не используется для отчетов
    }

    @Override
    public void bindLocationFilter(ComboBox<String> locationFilter) {
        this.locationFilter = locationFilter;

        // Раньше items тут вообще не заполнялись: комбобокс либо оставался
        // пустым, либо показывал места "в наследство" от прошлого раза, когда
        // этот же (общий) ComboBox использовался в галерее фото.
        List<String> locations = allDevices.stream()
                .map(Device::getLocation)
                .filter(v -> v != null && !v.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        List<String> items = new java.util.ArrayList<>();
        items.add("Все места");
        items.addAll(locations);
        locationFilter.setItems(FXCollections.observableArrayList(items));
        locationFilter.setValue("Все места");

        // ✅ ИСПРАВЛЕНИЕ: откладываем вызов applyFilters() через Platform.runLater()
        // Это предотвращает IndexOutOfBoundsException при выборе элемента в ComboBox
        locationFilter.valueProperty().addListener((_, _, _) -> {
            if (!isProgrammaticChange) {
                Platform.runLater(this::applyFilters);
            }
        });
    }

    @Override
    public void bindStatusFilter(ComboBox<String> statusFilter) {
        this.statusFilter = statusFilter;
        populateFilter(statusFilter, allDevices, Device::getStatus);
        // ✅ ИСПРАВЛЕНИЕ: аналогично для других фильтров
        statusFilter.valueProperty().addListener((_, _, _) -> {
            if (!isProgrammaticChange) {
                Platform.runLater(this::applyFilters);
            }
        });
    }

    @Override
    public void bindTypeFilter(ComboBox<String> typeFilter) {
        this.typeFilter = typeFilter;
        populateFilter(typeFilter, allDevices, Device::getType);
        // ✅ ИСПРАВЛЕНИЕ: аналогично для других фильтров
        typeFilter.valueProperty().addListener((_, _, _) -> {
            if (!isProgrammaticChange) {
                Platform.runLater(this::applyFilters);
            }
        });
    }

    @Override
    public void bindManufacturerFilter(ComboBox<String> manufacturerFilter) {
        this.manufacturerFilter = manufacturerFilter;
        populateFilter(manufacturerFilter, allDevices, Device::getManufacturer);
        // ✅ ИСПРАВЛЕНИЕ: аналогично для других фильтров
        manufacturerFilter.valueProperty().addListener((_, _, _) -> {
            if (!isProgrammaticChange) {
                Platform.runLater(this::applyFilters);
            }
        });
    }

    @Override
    public void bindYearFilter(ComboBox<String> yearFilter) {
        this.yearFilter = yearFilter;
        populateFilter(yearFilter, allDevices, d -> d.getYear() != null ? d.getYear().toString() : null);
        // ✅ ИСПРАВЛЕНИЕ: аналогично для других фильтров
        yearFilter.valueProperty().addListener((_, _, _) -> {
            if (!isProgrammaticChange) {
                Platform.runLater(this::applyFilters);
            }
        });
    }

    @Override
    public void clearFilters() {
        isProgrammaticChange = true;
        try {
            if (statusFilter != null) statusFilter.setValue(null);
            if (typeFilter != null) typeFilter.setValue(null);
            if (manufacturerFilter != null) manufacturerFilter.setValue(null);
            if (locationFilter != null) locationFilter.setValue("Все места");
            if (yearFilter != null) yearFilter.setValue(null);
        } finally {
            isProgrammaticChange = false;
        }
    }

    @Override
    public java.util.List<String> getReportFilterTypes() {
        return java.util.List.of(
                "Статус",
                "Тип",
                "Место установки",
                "Производитель",
                "Год"
        );
    }

    @Override
    public java.util.List<String> getReportFilterValues(String filterType) {
        if (filterType == null || allDevices == null) {
            return java.util.Collections.emptyList();
        }
        return switch (filterType) {
            case "Статус" -> allDevices.stream()
                    .map(Device::getStatus)
                    .filter(v -> v != null && !v.isEmpty())
                    .distinct()
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            case "Тип" -> allDevices.stream()
                    .map(Device::getType)
                    .filter(v -> v != null && !v.isEmpty())
                    .distinct()
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            case "Место установки" -> allDevices.stream()
                    .map(Device::getLocation)
                    .filter(v -> v != null && !v.isEmpty())
                    .distinct()
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            case "Производитель" -> allDevices.stream()
                    .map(Device::getManufacturer)
                    .filter(v -> v != null && !v.isEmpty())
                    .distinct()
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            case "Год" -> allDevices.stream()
                    .map(d -> d.getYear() != null ? d.getYear().toString() : null)
                    .filter(v -> v != null && !v.isEmpty())
                    .distinct()
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            default -> java.util.Collections.emptyList();
        };
    }

    @Override
    public void bindReportFilterCombos(ComboBox<String> filterTypeCombo, ComboBox<String> filterValueCombo) {
        if (filterTypeCombo == null || filterValueCombo == null) return;

        // Заполняем комбобокс типов фильтров с добавлением значения по умолчанию
        java.util.List<String> filterTypes = new java.util.ArrayList<>(getReportFilterTypes());
        filterTypes.add(0, "Без фильтров");
        filterTypeCombo.setItems(FXCollections.observableArrayList(filterTypes));
        filterTypeCombo.setValue("Без фильтров");

        // Сохраняем ссылки для восстановления
        this.reportFilterTypeCombo = filterTypeCombo;
        this.reportFilterValueCombo = filterValueCombo;

        // При выборе типа фильтра обновляем комбобокс значений
        filterTypeCombo.valueProperty().addListener((_, _, newValue) -> {
            if ("Без фильтров".equals(newValue)) {
                // Сбрасываем фильтры
                clearFilters();
                applyFilters();
                filterValueCombo.setItems(FXCollections.observableArrayList());
                filterValueCombo.setValue(null);
            } else if (newValue != null) {
                java.util.List<String> values = new java.util.ArrayList<>(getReportFilterValues(newValue));
                values.add(0, "Все значения");
                filterValueCombo.setItems(FXCollections.observableArrayList(values));
                filterValueCombo.setValue("Все значения");
            } else {
                filterValueCombo.setItems(FXCollections.observableArrayList());
                filterValueCombo.setValue(null);
            }
        });

        // При выборе значения применяем фильтр
        filterValueCombo.valueProperty().addListener((_, _, newValue) -> {
            String selectedType = filterTypeCombo.getValue();
            if ("Все значения".equals(newValue)) {
                // Сбрасываем фильтр для выбранного типа
                clearFilters();
                applyFilters();
            } else if (selectedType != null && newValue != null && !"Без фильтров".equals(selectedType)) {
                applyReportFilter(selectedType, newValue);
            }
        });
    }

    @Override
    public void applyReportFilter(String filterType, String filterValue) {
        isProgrammaticChange = true;
        try {
            // Сначала сбрасываем все фильтры
            clearFilters();

            if (filterType == null || filterValue == null) {
                applyFilters();
                return;
            }

            switch (filterType) {
                case "Статус" -> {
                    if (statusFilter != null) statusFilter.setValue(filterValue);
                }
                case "Тип" -> {
                    if (typeFilter != null) typeFilter.setValue(filterValue);
                }
                case "Место установки" -> {
                    if (locationFilter != null) locationFilter.setValue(filterValue);
                }
                case "Производитель" -> {
                    if (manufacturerFilter != null) manufacturerFilter.setValue(filterValue);
                }
                case "Год" -> {
                    if (yearFilter != null) yearFilter.setValue(filterValue);
                }
            }
            applyFilters();
        } finally {
            isProgrammaticChange = false;
        }
    }

    @Override
    public void clearReportFilter() {
        clearFilters();
        applyFilters();
    }

    @Override
    public void refreshReportFilterCombos() {
        if (reportFilterTypeCombo != null) {
            java.util.List<String> filterTypes = new java.util.ArrayList<>(getReportFilterTypes());
            filterTypes.add(0, "Без фильтров");
            reportFilterTypeCombo.setItems(FXCollections.observableArrayList(filterTypes));
            reportFilterTypeCombo.setValue("Без фильтров");
        }
        if (reportFilterValueCombo != null) {
            java.util.List<String> values = new java.util.ArrayList<>();
            values.add(0, "Все значения");
            reportFilterValueCombo.setItems(FXCollections.observableArrayList(values));
            reportFilterValueCombo.setValue("Все значения");
        }
    }

    private <T> void populateFilter(ComboBox<String> comboBox, List<Device> devices, java.util.function.Function<Device, String> extractor) {
        List<String> values = devices.stream()
                .map(extractor)
                .filter(v -> v != null && !v.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        comboBox.setItems(FXCollections.observableArrayList(values));
    }

    private void applyFilters() {
        filteredDevices.setPredicate(device -> {
            boolean statusMatch = statusFilter == null || statusFilter.getValue() == null
                    || statusFilter.getValue().equals(device.getStatus());
            boolean typeMatch = typeFilter == null || typeFilter.getValue() == null
                    || typeFilter.getValue().equals(device.getType());
            boolean manufacturerMatch = manufacturerFilter == null || manufacturerFilter.getValue() == null
                    || manufacturerFilter.getValue().equals(device.getManufacturer());
            boolean locationMatch = locationFilter == null || locationFilter.getValue() == null
                    || "Все места".equals(locationFilter.getValue())
                    || locationFilter.getValue().equals(device.getLocation());
            boolean yearMatch = yearFilter == null || yearFilter.getValue() == null
                    || (device.getYear() != null && yearFilter.getValue().equals(device.getYear().toString()));

            return statusMatch && typeMatch && manufacturerMatch && locationMatch && yearMatch;
        });

        updateReport();
        updateStatusBars();
    }

    private void updateReport() {
        Map<String, Long> statusData = reportService.getReportData(filteredDevices, "Status");
        boolean isDarkTheme = StyleUtils.isDarkTheme();

        chartPane.setCenter(null);

        if (donutChart == null) {
            donutChart = new DonutChart(300);
        }

        donutChart.updateChart(statusData, isDarkTheme);
        chartPane.setCenter(donutChart);

        titleLabel.setText("Сводка по приборам");
    }

    private void updateStatusBars() {
        long working = filteredDevices.stream().filter(d -> "В работе".equals(d.getStatus())).count();
        long storage = filteredDevices.stream().filter(d -> "Хранение".equals(d.getStatus())).count();
        long lost = filteredDevices.stream().filter(d -> "Утерян".equals(d.getStatus())).count();
        long broken = filteredDevices.stream().filter(d -> "Испорчен".equals(d.getStatus())).count();

        workingLabel.setText(String.valueOf(working));
        storageLabel.setText(String.valueOf(storage));
        lostLabel.setText(String.valueOf(lost));
        brokenLabel.setText(String.valueOf(broken));
    }

    public void refreshTheme() {
        boolean isDarkTheme = StyleUtils.isDarkTheme();
        if (donutChart != null) {
            donutChart.updateTheme(isDarkTheme);
        }
        updateReport();
        updateStatusBarsColors();
        updateHeaderIconColor();
    }

    private void updateHeaderIconColor() {
        // Иконка заголовка - синий цвет (акцентный)
        String iconColor = StyleUtils.isDarkTheme() ? "#1d5980" : "#277aaf";
        if (titleLabel != null) {
            // Применяем стиль к иконке через lookup
            javafx.scene.Node icon = titleLabel.getParent().lookup(".report-icon");
            if (icon instanceof Label) {
                ((Label) icon).setStyle(String.format("-fx-font-size: 28px; -fx-text-fill: %s;", iconColor));
            }
        }
    }

    private void updateStatusBarsColors() {
        boolean isDarkTheme = StyleUtils.isDarkTheme();
        String bgColor = isDarkTheme ? "#2d2d2d" : "#ffffff";
        String borderColor = isDarkTheme ? "#3d3d3d" : "#bdc3c7";

        // Применяем стили к карточкам программно
        String cardStyle = String.format(
                "-fx-background-color: %s; -fx-background-radius: 12px; -fx-padding: 15px 20px; -fx-border-color: %s; -fx-border-width: 1px; -fx-border-radius: 12px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 6, 0, 0, 2);",
                bgColor, borderColor
        );

        if (workingBar != null) workingBar.setStyle(cardStyle);
        if (storageBar != null) storageBar.setStyle(cardStyle);
        if (lostBar != null) lostBar.setStyle(cardStyle);
        if (brokenBar != null) brokenBar.setStyle(cardStyle);

        // Обновляем цвета Rectangle для точек статусов
        String workingColor = "#4CAF50";
        String storageColor = "#F58352";
        String lostColor = "#848C9B";
        String brokenColor = "#F44336";

        // Получаем Rectangle напрямую по индексу (первый элемент в HBox)
        if (workingBar != null && !workingBar.getChildren().isEmpty()) {
            javafx.scene.Node node = workingBar.getChildren().get(0);
            if (node instanceof javafx.scene.shape.Rectangle) {
                ((javafx.scene.shape.Rectangle) node).setFill(javafx.scene.paint.Color.web(workingColor));
            }
        }
        if (storageBar != null && !storageBar.getChildren().isEmpty()) {
            javafx.scene.Node node = storageBar.getChildren().get(0);
            if (node instanceof javafx.scene.shape.Rectangle) {
                ((javafx.scene.shape.Rectangle) node).setFill(javafx.scene.paint.Color.web(storageColor));
            }
        }
        if (lostBar != null && !lostBar.getChildren().isEmpty()) {
            javafx.scene.Node node = lostBar.getChildren().get(0);
            if (node instanceof javafx.scene.shape.Rectangle) {
                ((javafx.scene.shape.Rectangle) node).setFill(javafx.scene.paint.Color.web(lostColor));
            }
        }
        if (brokenBar != null && !brokenBar.getChildren().isEmpty()) {
            javafx.scene.Node node = brokenBar.getChildren().get(0);
            if (node instanceof javafx.scene.shape.Rectangle) {
                ((javafx.scene.shape.Rectangle) node).setFill(javafx.scene.paint.Color.web(brokenColor));
            }
        }

        // Обновляем цвета текста
        String textColor = isDarkTheme ? "#ecf0f1" : "#2c3e50";
        String labelStyle = String.format(
                "-fx-font-size: 14px; -fx-font-weight: 500; -fx-text-fill: %s; -fx-padding: 0 0 0 10px;",
                textColor
        );

        for (javafx.scene.Node node : workingBar.getChildren()) {
            if (node instanceof Label && !"workingLabel".equals(node.getId())) {
                node.setStyle(labelStyle);
            }
        }
        for (javafx.scene.Node node : storageBar.getChildren()) {
            if (node instanceof Label && !"storageLabel".equals(node.getId())) {
                node.setStyle(labelStyle);
            }
        }
        for (javafx.scene.Node node : lostBar.getChildren()) {
            if (node instanceof Label && !"lostLabel".equals(node.getId())) {
                node.setStyle(labelStyle);
            }
        }
        for (javafx.scene.Node node : brokenBar.getChildren()) {
            if (node instanceof Label && !"brokenLabel".equals(node.getId())) {
                node.setStyle(labelStyle);
            }
        }

        // Обновляем цвета счетчиков
        String countStyleFormat = "-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: %s;";
        if (workingLabel != null) workingLabel.setStyle(String.format(countStyleFormat, workingColor));
        if (storageLabel != null) storageLabel.setStyle(String.format(countStyleFormat, storageColor));
        if (lostLabel != null) lostLabel.setStyle(String.format(countStyleFormat, lostColor));
        if (brokenLabel != null) brokenLabel.setStyle(String.format(countStyleFormat, brokenColor));
    }
}