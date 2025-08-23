module com.kipia.management.kipia_management {
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;


    opens com.kipia.management.kipia_management.controllers to javafx.fxml; // Открываем пакет для рефлексии
    opens com.kipia.management.kipia_management; // Если у вас есть другие пакеты, которые нужно открыть
    exports com.kipia.management.kipia_management;
    exports com.kipia.management.kipia_management.controllers;
}