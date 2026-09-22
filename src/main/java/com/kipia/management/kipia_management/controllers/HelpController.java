package com.kipia.management.kipia_management.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
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
                        "Приложение предназначено для учёта и управления приборами измерения.",
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
                        "• Поиск по всем колонкам в таблице",
                        "",
                        "Для добавления прибора нажмите кнопку «+» в правом нижнем углу экрана.",
                        "Для редактирования дважды кликните на строку в таблице.",
                        "Для удаления: правая кнопка мыши на прибор, нажмите кнопку удаления."
                }
        ));

        // Справка для раздела "Галерея"
        HELP_CONTENTS.put("photoGallery", new HelpContent(
                "Справка",
                "Фотогалерея приборов",
                "Галерея",
                new String[]{
                        "В этом разделе вы можете просматривать фотографии приборов.",
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
                        "В этом разделе вы можете редактировать технологические схемы.",
                        "",
                        "Функции:",
                        "• Автоматическое создание схем по названию местоположения прибора",
                        "• Редактирование существующих схем",
                        "• Добавление элементов (линии, круги, прямоугольники, текст)",
                        "• Расположение приборов на схеме",
                        "• Отмена и повтор действий (Undo/Redo)",
                        "• Сохранение схем",
                        "",
                        "Панель инструментов сверху содержит инструменты для рисования.",
                        "Используйте Undo/Redo для отмены и повтора действий.",
                        "Схемы автоматически сохраняются при навигации.",
                        "Схему можно удалить полностью только если нет приборов в БД,",
                        "которые ссылаются на эту схему через \"Местоположение\""
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
                        "• Генерация отчётов по заданным фильтрам",
                        "• Фильтрация данных для отчёта",
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
                        "В этом разделе вы можете провести операции по синхронизации данных.",
                        "",
                        "Функции:",
                        "• Импорт/экспорт базы данных",
                        "• Импорт/экспорт данных в excel",
                        "",
                        "Существует возможность синхронизации БД с [мобильным приложением на Android|https://github.com/VladimirShi136/KIPiA_Management_Mobile].",
                        "Для быстрого переноса таблицы приборов из excel необходимо:",
                        "1. Экспортировать пустую excel таблицу.",
                        "2. Заполнить пустой шаблон таблицы нужными данными.",
                        "3. Выполнить импорт заполненной таблицы."

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
            javafx.scene.Node node = createTextForLine(line);
            helpContent.getChildren().add(node);
            helpContentNoScroll.getChildren().add(createTextForLine(line));
        }
        
        // После загрузки контента определяем, нужен ли скролл
        javafx.application.Platform.runLater(this::adjustWindowSize);
    }
    
    private javafx.scene.Node createTextForLine(String line) {
        if (line.isEmpty()) {
            Text emptyText = new Text(" ");
            emptyText.setStyle("-fx-font-size: 8px;");
            emptyText.getStyleClass().add("help-text-empty");
            return emptyText;
        } else if (line.startsWith("•")) {
            Text bulletText = new Text(line);
            bulletText.setStyle("-fx-font-size: 14px;");
            bulletText.getStyleClass().add("help-text-bullet");
            return bulletText;
        } else if (line.endsWith(":")) {
            Text headerText = new Text(line);
            headerText.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
            headerText.getStyleClass().add("help-text-header");
            return headerText;
        } else {
            // Проверяем наличие ссылки в формате [текст|url]
            int linkStart = line.indexOf('[');
            int linkEnd = line.indexOf(']');
            int pipeIndex = line.indexOf('|', linkStart);

            if (linkStart != -1 && linkEnd != -1 && pipeIndex != -1 && pipeIndex > linkStart && pipeIndex < linkEnd) {
                // Создаем HBox с текстом и ссылкой
                HBox hbox = new HBox(5);
                hbox.setStyle("-fx-alignment: center-left;");

                // Текст до ссылки
                if (linkStart > 0) {
                    Text beforeText = new Text(line.substring(0, linkStart));
                    beforeText.setStyle("-fx-font-size: 14px;");
                    beforeText.getStyleClass().add("help-text-normal");
                    hbox.getChildren().add(beforeText);
                }

                // Ссылка
                String linkText = line.substring(linkStart + 1, pipeIndex);
                String linkUrl = line.substring(pipeIndex + 1, linkEnd);
                Hyperlink hyperlink = new Hyperlink(linkText);
                hyperlink.setStyle("-fx-font-size: 14px; -fx-border-color: transparent;");
                hyperlink.setOnAction(_ -> {
                    try {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(linkUrl));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                hbox.getChildren().add(hyperlink);

                // Текст после ссылки
                if (linkEnd < line.length() - 1) {
                    Text afterText = new Text(line.substring(linkEnd + 1));
                    afterText.setStyle("-fx-font-size: 14px;");
                    afterText.getStyleClass().add("help-text-normal");
                    hbox.getChildren().add(afterText);
                }

                return hbox;
            } else {
                Text normalText = new Text(line);
                normalText.setStyle("-fx-font-size: 14px;");
                normalText.getStyleClass().add("help-text-normal");
                return normalText;
            }
        }
    }

    // Метод для
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
