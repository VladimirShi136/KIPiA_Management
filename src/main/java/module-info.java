module com.kipia.management.kipia_management {
    // requires - Общие импорты для всего проекта
    requires javafx.base;  // Базовый модуль JavaFX для фундаментальных классов
    requires javafx.graphics;  // Для графики и рендеринга
    requires javafx.controls;  // Для UI-компонентов (Button, TableView).
    requires javafx.fxml;  // Для загрузки и работы с FXML-файлами.
    requires java.sql;  // Для работы с базами данных (JDBC).
    requires org.apache.poi.ooxml; // Для работы с Excel-файлами (через Apache POI).
    requires org.jfree.jfreechart; // Основная библиотека для создания графиков (JFreeChart).
    requires jfreechart.fx; // JavaFX-адаптер для интеграции JFreeChart с JavaFX UI.
    requires java.desktop; // Стандартный модуль для десктоп-функций (например, иконки или системные диалоги).


    // opens - позволяет другим модулям (или системе) получать доступ к вашим классам через рефлексию.
    // Открываем пакет с контроллерами для JavaFX FXML для связки fxml полей/методов (рефлексия)
    opens com.kipia.management.kipia_management.controllers to javafx.fxml;

    // Открываем для FXML, чтобы использовать классы из основного пакета
    opens com.kipia.management.kipia_management to javafx.fxml;

    // Открывает модели. Нужно, если FXML использует биндинги или DataFX для рефлексии моделей.
    opens com.kipia.management.kipia_management.models to javafx.fxml;

    // Если сервисы не нужны для рефлексии, их не обязательно открывать
    // Но если используете рефлексию или DI, можно открыть
    // opens com.kipia.management.kipia_management.services to javafx.fxml;

    // Экспортируем пакеты, которые должны быть видимы другим модулям (например, для тестов или внешних библиотек)
    exports com.kipia.management.kipia_management;
    exports com.kipia.management.kipia_management.controllers;
    exports com.kipia.management.kipia_management.models;
    exports com.kipia.management.kipia_management.services;
}
