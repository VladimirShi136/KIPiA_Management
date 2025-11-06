package com.kipia.management.kipia_management.models;

/**
 * Класс DeviceLocation представляет модель данных привязки прибора к схеме (местоположение).
 * Содержит информацию о положении прибора на схеме и методы для работы с этими данными.
 *
 * @author vladimir_shi
 * @since 30.09.2025
 */

public class DeviceLocation {
    // Идентификатор прибора
    private int deviceId;
    // Идентификатор схемы
    private int schemeId;
    // Координата X на схеме
    private double x;
    // Координата Y на схеме
    private double y;
    private double rotation; // Новое поле для угла поворота

    /**
     * Конструктор по умолчанию.
     * Создает пустой объект DeviceLocation без инициализации полей.
     */
    public DeviceLocation() {
        // Пустой конструктор
    }

    /**
     * Параметризованный конструктор.
     * Создает объект DeviceLocation с указанными параметрами.
     *
     * @param deviceId  ID прибора
     * @param schemeId  ID схемы
     * @param x         координата X
     * @param y         координата Y
     */
    public DeviceLocation(int deviceId, int schemeId, double x, double y) {
        this.deviceId = deviceId;
        this.schemeId = schemeId;
        this.x = x;
        this.y = y;
        this.rotation = 0.0; // По умолчанию 0 градусов
    }

    public DeviceLocation(int deviceId, int schemeId, double x, double y, double rotation) {
        this.deviceId = deviceId;
        this.schemeId = schemeId;
        this.x = x;
        this.y = y;
        this.rotation = rotation;
    }

    // Геттеры и сеттеры

    /**
     * Получить ID прибора.
     *
     * @return ID прибора
     */
    public int getDeviceId() {
        return deviceId;
    }

    /**
     * Установить ID прибора.
     *
     * @param deviceId новый ID прибора
     */
    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * Получить ID схемы.
     *
     * @return ID схемы
     */
    public int getSchemeId() {
        return schemeId;
    }

    /**
     * Установить ID схемы.
     *
     * @param schemeId новый ID схемы
     */
    public void setSchemeId(int schemeId) {
        this.schemeId = schemeId;
    }

    /**
     * Получить координату X.
     *
     * @return координата X
     */
    public double getX() {
        return x;
    }

    /**
     * Установить координату X.
     *
     * @param x новая координата X
     */
    public void setX(double x) {
        this.x = x;
    }

    /**
     * Получить координату Y.
     *
     * @return координата Y
     */
    public double getY() {
        return y;
    }

    /**
     * Установить координату Y.
     *
     * @param y новая координата Y
     */
    public void setY(double y) {
        this.y = y;
    }

    // Геттеры и сеттеры
    public double getRotation() {
        return rotation;
    }

    public void setRotation(double rotation) {
        this.rotation = rotation;
    }

    /**
     * Переопределенный метод toString() для удобного представления объекта в виде строки.
     * Используется для отладки и вывода информации о привязке.
     *
     * @return строковое представление объекта DeviceLocation
     */
    @Override
    public String toString() {
        return "DeviceLocation{" +
                "deviceId=" + deviceId +
                ", schemeId=" + schemeId +
                ", x=" + x +
                ", y=" + y +
                ", rotation=" + rotation +
                '}';
    }
}

