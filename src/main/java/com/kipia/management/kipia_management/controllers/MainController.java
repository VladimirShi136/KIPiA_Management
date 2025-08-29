package com.kipia.management.kipia_management.controllers;

import com.kipia.management.kipia_management.models.Device;
import com.kipia.management.kipia_management.services.DeviceDAO;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;

/**
 * Контроллер — это компонент в архитектуре MVC (Model-View-Controller),
 * который служит связующим звеном между пользовательским интерфейсом (View) и данными (Model).
 * Контроллер связывает элементы интерфейса из FXML с кодом, реализуя поведение приложения.
 *
 * @author vladimir_shi
 * @since 29.08.2025
 */

public class MainController {

    /**
     * Метки и методы помечены @FXML для связывания с элементами интерфейса и обработчиками событий.
     */

    // Метка для отображения статуса или сообщений пользователю
    @FXML
    private Label statusLabel;

    // Контейнер для динамического содержимого (например, таблицы или форм)
    @FXML
    private VBox contentArea;

    // Метки для отображения статистики приборов (общее количество, в работе, на ремонте)
    @FXML
    private Label totalDevicesLabel;

    @FXML
    private Label workingDevicesLabel;

    @FXML
    private Label repairDevicesLabel;

    // DAO для доступа к данным приборов
    private DeviceDAO deviceDAO;

    // Метод для внедрения DAO из внешнего кода (например, из главного приложения)
    public void setDeviceDAO(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
    }

    // Метод инициализации контроллера, вызывается автоматически после загрузки FXML
    @FXML
    private void initialize() {
        statusLabel.setText("Система инициализирована");
        // Если DAO уже установлен, обновляем статистику приборов
        if (deviceDAO != null) {
            updateStatistics();
        }
    }

    /**
     * Обновляет метки статистики приборов:
     * - общее количество
     * - количество в работе
     * - количество на ремонте
     */
    private void updateStatistics() {
        List<Device> devices = deviceDAO.getAllDevices();

        int total = devices.size();

        // Подсчет приборов со статусом "В работе"
        int working = (int) devices.stream()
                .filter(d -> "В работе".equalsIgnoreCase(d.getStatus()))
                .count();

        // Подсчет приборов со статусом "На ремонте"
        int repair = (int) devices.stream()
                .filter(d -> "На ремонте".equalsIgnoreCase(d.getStatus()))
                .count();

        // Обновляем текст меток
        totalDevicesLabel.setText(String.valueOf(total));
        workingDevicesLabel.setText(String.valueOf(working));
        repairDevicesLabel.setText(String.valueOf(repair));
    }

    /**
     * Отображает таблицу приборов в contentArea
     */
    @FXML
    private void showDevices() {
        statusLabel.setText("Просмотр списка приборов");

        // Очищаем содержимое контейнера перед добавлением новой таблицы
        contentArea.getChildren().clear();

        // Создаем новую таблицу для отображения приборов
        TableView<Device> table = new TableView<>();

        // Создаем колонки таблицы, указывая заголовки
        TableColumn<Device, String> inventoryCol = new TableColumn<>("Инв. номер");
        TableColumn<Device, String> nameCol = new TableColumn<>("Наименование");
        TableColumn<Device, String> typeCol = new TableColumn<>("Тип");
        TableColumn<Device, String> statusCol = new TableColumn<>("Статус");

        /*
         * Устанавливаем cellValueFactory для каждой колонки.
         * PropertyValueFactory связывает имя свойства (строка) с геттером в классе Device.
         * Например, "inventoryNumber" связывается с методом getInventoryNumber().
         * Это позволяет TableView автоматически получать значения для отображения.
         */
        inventoryCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("inventoryNumber"));
        nameCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        typeCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("type"));
        statusCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("status"));

        // Добавляем колонки в таблицу
        table.getColumns().addAll(inventoryCol, nameCol, typeCol, statusCol);

        // Получаем список приборов из DAO
        List<Device> devices = deviceDAO.getAllDevices();

        // Устанавливаем данные в таблицу, оборачивая список в ObservableList
        table.setItems(FXCollections.observableArrayList(devices));

        // Позволяем таблице расширяться по вертикали в VBox
        VBox.setVgrow(table, Priority.ALWAYS);

        // Добавляем таблицу в контейнер contentArea
        contentArea.getChildren().add(table);

        // Обновляем статус с количеством загруженных приборов
        statusLabel.setText("Загружено приборов: " + devices.size());
    }

    /**
     * Отображает форму добавления нового прибора
     */
    @FXML
    private void showAddDeviceForm() {
        statusLabel.setText("Добавление нового прибора");
        contentArea.getChildren().clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/add-device-form.fxml"));
            Parent form = loader.load();

            // Передаем deviceDAO в контроллер формы
            AddDeviceController controller = loader.getController();
            controller.setDeviceDAO(this.deviceDAO);

            contentArea.getChildren().add(form);
        } catch (IOException e) {
            statusLabel.setText("Ошибка загрузки формы: " + e.getMessage());
            contentArea.getChildren().add(new Label("Форма добавления прибора будет здесь"));
        }
    }

    /**
     * Отображает отчеты
     */
    @FXML
    private void showReports() {
        statusLabel.setText("Просмотр отчётов");
        contentArea.getChildren().clear();
        // Аналогично, сюда можно загрузить или построить отчеты
        contentArea.getChildren().add(new Label("Отчёты будут здесь"));
    }

    /**
     * Завершает работу приложения
     */
    @FXML
    private void exitApp() {
        System.exit(0);
    }
}