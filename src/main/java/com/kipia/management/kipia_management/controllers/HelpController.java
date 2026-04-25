package com.kipia.management.kipia_management.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер окна справки
 * Отображает контекстную справку для каждого раздела приложения
 *
 * @author vladimir_shi
 * @since 24.04.2026
 */
public class HelpController {

    @FXML private Label typeLabel;
    @FXML private Label titleLabel;
    @FXML private Label sectionLabel;
    @FXML private VBox helpContent;
    @FXML private VBox helpContentNoScroll;
    @FXML private ScrollPane scrollPane;
    @FXML private VBox contentWrapper;
    @FXML private Button understoodBtn;

    private Stage stage;

    // Контекстная справка для каждого раздела
    private static final Map<String, HelpContent> HELP_CONTENTS = new HashMap<>();

    static {
        // Общая справка
        HELP_CONTENTS.put("main", new HelpContent(
                "Справка",
                "Информация о приложении",
                "",
                new String[]{
                        "Добро пожаловать в систему учёта приборов КИПиА!",
                        "",
                        "Приложение предназначено для учёта и управления приборами измерения и автоматики.",
                        "",
                        "Основные функции:",
                        "• Учёт приборов с полной информацией",
                        "• Фотогалерея приборов по местам установки",
                        "• Редактор технологических схем",
                        "• Генерация отчётов",
                        "• Настройки и управление данными",
                        "",
                        "Для навигации используйте меню слева.",
                        "Для поиска используйте кнопку поиска в верхней панели."
                }
        ));

        // Справка для раздела "Приборы"
        HELP_CONTENTS.put("devices", new HelpContent(
                "Справка",
                "Раздел учёта приборов",
                "Приборы",
                new String[]{
                        "В этом разделе вы можете просматривать, добавлять, редактировать и удалять приборы.",
                        "",
                        "Функции:",
                        "• Таблица со списком всех приборов",
                        "• Добавление нового прибора через форму",
                        "• Редактирование существующих приборов",
                        "• Удаление приборов",
                        "• Поиск по названию, типу, серийному номеру",
                        "",
                        "Для добавления прибора нажмите кнопку «Добавить прибор».",
                        "Для редактирования дважды кликните на строку в таблице.",
                        "Для удаления выделите прибор и нажмите кнопку удаления."
                }
        ));

        // Справка для раздела "Галерея"
        HELP_CONTENTS.put("photoGallery", new HelpContent(
                "Справка",
                "Фотогалерея приборов",
                "Галерея",
                new String[]{
                        "В этом разделе вы можете просматривать фотографии приборов, сгруппированные по местам установки.",
                        "",
                        "Функции:",
                        "• Просмотр фото по местам установки",
                        "• Фильтрация по названию места",
                        "• Отображение только приборов с фотографиями",
                        "• Поиск по названию прибора",
                        "",
                        "Выберите место установки из списка для просмотра фотографий.",
                        "Используйте фильтры для уточнения поиска."
                }
        ));

        // Справка для раздела "Схемы"
        HELP_CONTENTS.put("schemes", new HelpContent(
                "Справка",
                "Редактор технологических схем",
                "Схемы",
                new String[]{
                        "В этом разделе вы можете создавать и редактировать технологические схемы.",
                        "",
                        "Функции:",
                        "• Создание новых схем",
                        "• Редактирование существующих схем",
                        "• Добавление элементов (линии, круги, прямоугольники, текст)",
                        "• Отмена и повтор действий (Undo/Redo)",
                        "• Сохранение схем",
                        "",
                        "Панель инструментов слева содержит инструменты рисования.",
                        "Используйте Undo/Redo для отмены и повтора действий.",
                        "Схемы автоматически сохраняются при навигации."
                }
        ));

        // Справка для раздела "Отчёты"
        HELP_CONTENTS.put("reports", new HelpContent(
                "Справка",
                "Генерация отчётов",
                "Отчёты",
                new String[]{
                        "В этом разделе вы можете генерировать отчёты по приборам.",
                        "",
                        "Функции:",
                        "• Генерация отчётов в различных форматах",
                        "• Фильтрация данных для отчёта",
                        "• Экспорт в Excel",
                        "",
                        "Выберите тип отчёта и параметры для генерации."
                }
        ));

        // Справка для раздела "Настройки"
        HELP_CONTENTS.put("settings", new HelpContent(
                "Справка",
                "Настройки приложения",
                "Настройки",
                new String[]{
                        "В этом разделе вы можете настроить параметры приложения.",
                        "",
                        "Функции:",
                        "• Управление базой данных",
                        "• Импорт/экспорт данных",
                        "• Настройка синхронизации",
                        "• Управление резервными копиями",
                        "",
                        "Будьте осторожны при операциях с базой данных - они необратимы."
                }
        ));
    }

    @FXML
    private void initialize() {
        understoodBtn.setOnAction(_ -> closeHelp());
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /**
     * Загружает контент справки для указанного раздела
     */
    public void loadHelpContent(String section) {
        HelpContent content = HELP_CONTENTS.getOrDefault(section, HELP_CONTENTS.get("main"));
        
        typeLabel.setText(content.type());
        titleLabel.setText(content.title());
        sectionLabel.setText(content.sectionLabel());
        
        // Очищаем оба контейнера
        helpContent.getChildren().clear();
        helpContentNoScroll.getChildren().clear();
        
        // Добавляем контент в оба контейнера
        for (String line : content.lines()) {
            Text textNode = createTextForLine(line);
            helpContent.getChildren().add(textNode);
            helpContentNoScroll.getChildren().add(createTextForLine(line));
        }
        
        // После загрузки контента определяем, нужен ли скролл
        javafx.application.Platform.runLater(() -> adjustWindowSize());
    }
    
    private Text createTextForLine(String line) {
        if (line.isEmpty()) {
            Text emptyText = new Text(" ");
            emptyText.setStyle("-fx-font-size: 8px;");
            return emptyText;
        } else if (line.startsWith("•")) {
            Text bulletText = new Text(line);
            bulletText.setStyle("-fx-font-size: 14px; -fx-fill: #666;");
            return bulletText;
        } else if (line.endsWith(":")) {
            Text headerText = new Text(line);
            headerText.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-fill: #333;");
            return headerText;
        } else {
            Text normalText = new Text(line);
            normalText.setStyle("-fx-font-size: 14px; -fx-fill: #333;");
            return normalText;
        }
    }
    
    private void adjustWindowSize() {
        // Получаем преферредный размер контента
        helpContentNoScroll.applyCss();
        helpContentNoScroll.layout();
        double contentHeight = helpContentNoScroll.getPrefHeight();
        double contentWidth = helpContentNoScroll.getPrefWidth();
        
        // Если преферредный размер не рассчитан, используем текущий размер
        if (contentHeight <= 0) {
            contentHeight = helpContentNoScroll.getHeight();
        }
        if (contentWidth <= 0) {
            contentWidth = helpContentNoScroll.getWidth();
        }
        
        // Максимальные разумные размеры
        final double MAX_HEIGHT = 600;
        final double MAX_WIDTH = 700;
        final double MIN_WIDTH = 550;
        final double HEADER_HEIGHT = 100; // Примерная высота шапки
        final double BUTTON_BAR_HEIGHT = 80; // Примерная высота панели кнопок
        
        // Определяем, нужен ли скролл
        boolean needsScroll = contentHeight > MAX_HEIGHT;
        
        if (needsScroll) {
            // Показываем скролл
            scrollPane.setVisible(true);
            scrollPane.setManaged(true);
            helpContentNoScroll.setVisible(false);
            helpContentNoScroll.setManaged(false);
            
            // Устанавливаем размер окна с максимальной высотой
            if (stage != null) {
                stage.setHeight(MAX_HEIGHT + HEADER_HEIGHT + BUTTON_BAR_HEIGHT);
                stage.setWidth(Math.max(MIN_WIDTH, Math.min(contentWidth + 80, MAX_WIDTH)));
            }
        } else {
            // Без скролла - окно подстраивается под контент
            scrollPane.setVisible(false);
            scrollPane.setManaged(false);
            helpContentNoScroll.setVisible(true);
            helpContentNoScroll.setManaged(true);
            
            if (stage != null) {
                double totalHeight = contentHeight + HEADER_HEIGHT + BUTTON_BAR_HEIGHT;
                stage.setHeight(totalHeight);
                stage.setWidth(Math.max(MIN_WIDTH, Math.min(contentWidth + 80, MAX_WIDTH)));
            }
        }
    }

    @FXML
    private void closeHelp() {
        if (stage != null) {
            stage.close();
        }
    }

    /**
     * Внутренний класс для хранения контента справки
     */
    private record HelpContent(String type, String title, String sectionLabel, String[] lines) {}
}
