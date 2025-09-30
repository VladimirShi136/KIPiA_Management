package com.kipia.management.kipia_management.models;

/**
 * Класс Scheme представляет модель данных схемы/карты в системе.
 * Содержит информацию о схеме и методы для работы с этими данными.
 *
 * @author vladimir_shi
 * @since 30.09.2025
 */

public class Scheme {
    // Уникальный идентификатор схемы в базе данных
    private int id;
    // Название схемы
    private String name;
    // Описание схемы
    private String description;
    // Данные схемы (JSON-строка с объектами: линии, фигуры и т.д.)
    private String data;

    /**
     * Конструктор по умолчанию.
     * Создает пустой объект Scheme без инициализации полей.
     */
    public Scheme() {
        // Пустой конструктор
    }

    /**
     * Параметризованный конструктор.
     * Создает объект Scheme с указанными параметрами (без id, если добавляется новая).
     *
     * @param id           идентификатор схемы
     * @param name         название схемы
     * @param description  описание схемы
     * @param data         данные схемы (JSON)
     */
    public Scheme(int id, String name, String description, String data) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.data = data;
    }

    // Геттеры и сеттеры

    /**
     * Получить идентификатор схемы.
     *
     * @return уникальный идентификатор схемы
     */
    public int getId() {
        return id;
    }

    /**
     * Установить идентификатор схемы.
     *
     * @param id новый идентификатор схемы
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Получить название схемы.
     *
     * @return название схемы
     */
    public String getName() {
        return name;
    }

    /**
     * Установить название схемы.
     *
     * @param name новое название схемы
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Получить описание схемы.
     *
     * @return описание схемы
     */
    public String getDescription() {
        return description;
    }

    /**
     * Установить описание схемы.
     *
     * @param description новое описание схемы
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Получить данные схемы (JSON).
     *
     * @return данные схемы
     */
    public String getData() {
        return data;
    }

    /**
     * Установить данные схемы (JSON).
     *
     * @param data новые данные схемы
     */
    public void setData(String data) {
        this.data = data;
    }

    /**
     * Переопределенный метод toString() для удобного представления объекта в виде строки.
     * Используется для отладки и вывода информации о схеме.
     *
     * @return строковое представление объекта Scheme
     */
    @Override
    public String toString() {
        return "Scheme{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", data='" + data + '\'' +
                '}';
    }
}