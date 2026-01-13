package com.kipia.management.kipia_management.models;

import java.util.ArrayList;
import java.util.List;

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

    // Тип или категория прибора (например: манометр, датчик давления и т.д.)
    private String type;

    // Наименование прибора / модель / серия
    private String name;

    // Завод изготовитель
    private String manufacturer;

    // Инвентарный номер прибора (уникальный идентификатор в организации)
    private String inventoryNumber;

    // Год выпуска
    private Integer year;

    // Предел измерений
    private String measurementLimit;

    // Класс точности
    private Double accuracyClass;

    // Местонахождение или расположение прибора
    private String location;

    // Номер крана или узла
    private String valveNumber;

    // Статус прибора (например: исправен, в ремонте, списан и т.д.)
    private String status;

    // Доп информация
    private String additionalInfo;

    // Коллекция для путей фото (только имена файлов)
    private List<String> photos;

    /**
     * Конструктор по умолчанию.
     * Создает пустой объект Device без инициализации полей.
     */
    public Device() {
        photos = new ArrayList<>();
    }

    /**
     * Параметризованный конструктор.
     * Создает объект Device с указанными параметрами (кроме id).
     *
     * @param type             тип прибора
     * @param name             наименование прибора
     * @param manufacturer     изготовитель прибора
     * @param inventoryNumber  инвентарный номер
     * @param year             год выпуска
     * @param measurementLimit предел измерений
     * @param accuracyClass    класс точности
     * @param location         местонахождение
     * @param status           статус прибора
     * @param additionalInfo   дополнительная информация
     */
    public Device(int id, String type, String name, String manufacturer, String inventoryNumber, Integer year, String measurementLimit, Double accuracyClass, String location, String valveNumber, String status, String additionalInfo) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.manufacturer = manufacturer;
        this.inventoryNumber = inventoryNumber;
        this.year = year;
        this.measurementLimit = measurementLimit;
        this.accuracyClass = accuracyClass;
        this.location = location;
        this.valveNumber = valveNumber;
        this.status = status;
        this.additionalInfo = additionalInfo;
        this.photos = new ArrayList<>();
    }

    // Геттеры и сеттеры для доступа к приватным полям класса

    /**
     * Получить идентификатор прибора.
     *
     * @return уникальный идентификатор прибора
     */
    public int getId() {
        return id;
    }

    /**
     * Установить идентификатор прибора.
     *
     * @param id новый идентификатор прибора
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Получить тип прибора.
     *
     * @return тип прибора
     */
    public String getType() {
        return type;
    }

    /**
     * Установить тип прибора.
     *
     * @param type новый тип прибора
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Получить наименование прибора.
     *
     * @return наименование прибора
     */
    public String getName() {
        return name;
    }

    /**
     * Установить наименование прибора.
     *
     * @param name новое наименование прибора
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Получить изготовителя
     *
     * @return изготовитель
     */
    public String getManufacturer() {
        return manufacturer;
    }

    /**
     * Установить изготовителя
     *
     * @param manufacturer новый изготовитель
     */
    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    /**
     * Получить инвентарный номер прибора.
     *
     * @return инвентарный номер
     */
    public String getInventoryNumber() {
        return inventoryNumber;
    }

    /**
     * Установить инвентарный номер прибора.
     *
     * @param inventoryNumber новый инвентарный номер
     */
    public void setInventoryNumber(String inventoryNumber) {
        this.inventoryNumber = inventoryNumber;
    }

    /**
     * Получить год выпуска
     *
     * @return год выпуска
     */
    public Integer getYear() {
        return year;
    }

    /**
     * Установить год выпуска
     *
     * @param year новый год выпуска
     */
    public void setYear(Integer year) {
        this.year = year;
    }

    /**
     * Получить предел измерений
     *
     * @return предел измерений
     */
    public String getMeasurementLimit() {
        return measurementLimit;
    }

    /**
     * Установить предел измерений для прибора
     *
     * @param measurementLimit предел измерений
     */
    public void setMeasurementLimit(String measurementLimit) {
        this.measurementLimit = measurementLimit;
    }

    /**
     * Получить класс точности прибора
     *
     * @return класс точности
     */
    public Double getAccuracyClass() {
        return accuracyClass;
    }

    /**
     * Установить класс точности прибора
     *
     * @param accuracyClass класс точности
     */
    public void setAccuracyClass(Double accuracyClass) {
        this.accuracyClass = accuracyClass;
    }

    /**
     * Получить местонахождение прибора.
     *
     * @return местонахождение прибора
     */
    public String getLocation() {
        return location;
    }

    /**
     * Установить местонахождение прибора.
     *
     * @param location новое местонахождение
     */
    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * Получить номер крана.
     *
     * @return номер крана
     */
    public String getValveNumber() {
        return valveNumber;
    }

    /**
     * Установить номер крана.
     *
     * @param valveNumber новый номер крана
     */
    public void setValveNumber(String valveNumber) {
        this.valveNumber = valveNumber;
    }

    /**
     * Получить статус прибора.
     *
     * @return текущий статус прибора
     */
    public String getStatus() {
        return status;
    }

    /**
     * Установить статус прибора.
     *
     * @param status новый статус прибора
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Получить доп инфо
     *
     * @return доп инфо
     */
    public String getAdditionalInfo() {
        return additionalInfo;
    }

    /**
     * Установить доп инфо
     *
     * @param additionalInfo новое доп инфо
     */
    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    /**
     * Получить список фотографий прибора.
     *
     * @return список путей к фотографиям
     */
    public List<String> getPhotos() {
        return photos;
    }

    /**
     * Установить список фотографий прибора.
     *
     * @param photos новый список фотографий
     */
    public void setPhotos(List<String> photos) {
        this.photos = photos;
    }

    /**
     * Добавить фотографию в список.
     *
     * @param photoName имя файла фотографии
     */
    public void addPhoto(String photoName) {
        if (photoName != null && !photoName.trim().isEmpty()) {
            if (photos == null) {
                photos = new ArrayList<>();
            }
            photos.add(photoName);
        }
    }

    /**
     * Удалить фотографию из списка.
     *
     * @param photoName имя файла фотографии для удаления
     * @return true если фотография была удалена, false если не найдена
     */
    public boolean removePhoto(String photoName) {
        if (photos != null) {
            return photos.remove(photoName);
        }
        return false;
    }

    /**
     * Получить количество фотографий.
     *
     * @return количество фотографий
     */
    public int getPhotoCount() {
        return photos != null ? photos.size() : 0;
    }

    /**
     * Проверить, есть ли фотографии у прибора.
     *
     * @return true если есть хотя бы одна фотография
     */
    public boolean hasPhotos() {
        return photos != null && !photos.isEmpty();
    }

    /**
     * Получить основную фотографию (первую в списке).
     *
     * @return путь к основной фотографии или null если нет фотографий
     */
    public String getMainPhoto() {
        if (hasPhotos()) {
            return photos.get(0);
        }
        return null;
    }

    /**
     * Переопределенный метод toString() для удобного представления объекта в виде строки.
     * Используется для отладки и вывода информации о приборе.
     *
     * @return строковое представление объекта Device
     */
    @Override
    public String toString() {
        return "Device{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", manufacturer='" + manufacturer + '\'' +
                ", inventoryNumber='" + inventoryNumber + '\'' +
                ", year=" + year +
                ", measurementLimit='" + measurementLimit + '\'' +
                ", accuracyClass=" + accuracyClass +
                ", location='" + location + '\'' +
                ", valveNumber='" + valveNumber + '\'' +
                ", status='" + status + '\'' +
                ", additionalInfo='" + additionalInfo + '\'' +
                ", photos=" + photos +
                '}';
    }
}
