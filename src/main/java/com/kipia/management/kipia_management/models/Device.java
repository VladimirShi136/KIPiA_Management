package com.kipia.management.kipia_management.models;

/**
 * Класс Device представляет модель данных прибора/оборудования в системе.
 * Содержит информацию о приборе и методы для работы с этими данными.
 * Это POJO (Plain Old Java Object) класс для хранения данных прибора.
 *
 * @author vladimir_shi
 * @since 29.08.2025
 */

public class Device {
    // Уникальный идентификатор прибора в базе данных
    private int id;

    // Наименование прибора
    private String name;

    // Тип или категория прибора (например: измерительный, контрольный и т.д.)
    private String type;

    // Инвентарный номер прибора (уникальный идентификатор в организации)
    private String inventoryNumber;

    // Местонахождение или расположение прибора
    private String location;

    // Статус прибора (например: исправен, в ремонте, списан и т.д.)
    private String status;

    /**
     * Конструктор по умолчанию.
     * Создает пустой объект Device без инициализации полей.
     */
    public Device() {}

    /**
     * Параметризованный конструктор.
     * Создает объект Device с указанными параметрами (кроме id).
     * @param name наименование прибора
     * @param type тип прибора
     * @param inventoryNumber инвентарный номер
     * @param location местонахождение
     * @param status статус прибора
     */
    public Device(String name, String type, String inventoryNumber, String location, String status) {
        this.name = name;
        this.type = type;
        this.inventoryNumber = inventoryNumber;
        this.location = location;
        this.status = status;
    }

    // Геттеры и сеттеры для доступа к приватным полям класса

    /**
     * Получить идентификатор прибора.
     * @return уникальный идентификатор прибора
     */
    public int getId() { return id; }

    /**
     * Установить идентификатор прибора.
     * @param id новый идентификатор прибора
     */
    public void setId(int id) { this.id = id; }

    /**
     * Получить наименование прибора.
     * @return наименование прибора
     */
    public String getName() { return name; }

    /**
     * Установить наименование прибора.
     * @param name новое наименование прибора
     */
    public void setName(String name) { this.name = name; }

    /**
     * Получить тип прибора.
     * @return тип прибора
     */
    public String getType() { return type; }

    /**
     * Установить тип прибора.
     * @param type новый тип прибора
     */
    public void setType(String type) { this.type = type; }

    /**
     * Получить инвентарный номер прибора.
     * @return инвентарный номер
     */
    public String getInventoryNumber() { return inventoryNumber; }

    /**
     * Установить инвентарный номер прибора.
     * @param inventoryNumber новый инвентарный номер
     */
    public void setInventoryNumber(String inventoryNumber) { this.inventoryNumber = inventoryNumber; }

    /**
     * Получить местонахождение прибора.
     * @return местонахождение прибора
     */
    public String getLocation() { return location; }

    /**
     * Установить местонахождение прибора.
     * @param location новое местонахождение
     */
    public void setLocation(String location) { this.location = location; }

    /**
     * Получить статус прибора.
     * @return текущий статус прибора
     */
    public String getStatus() { return status; }

    /**
     * Установить статус прибора.
     * @param status новый статус прибора
     */
    public void setStatus(String status) { this.status = status; }

    /**
     * Переопределенный метод toString() для удобного представления объекта в виде строки.
     * Используется для отладки и вывода информации о приборе.
     * @return строковое представление объекта Device в формате: "Наименование (Инвентарный номер) - Местонахождение"
     */
    @Override
    public String toString() {
        return name + " (" + inventoryNumber + ") - " + location;
    }
}
