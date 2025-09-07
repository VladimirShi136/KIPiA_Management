module com.kipia.management.kipia_management {
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.apache.poi.ooxml;
    requires org.jfree.jfreechart;
    requires jfreechart.fx;
    requires java.desktop;

    // Открываем пакет с контроллерами для JavaFX FXML (рефлексия)
    opens com.kipia.management.kipia_management.controllers to javafx.fxml;

    // Если FXML использует классы из основного пакета, откройте его тоже
    opens com.kipia.management.kipia_management to javafx.fxml;

    // Если FXML или другие компоненты обращаются к моделям через рефлексию (например, биндинги), откройте модели
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
