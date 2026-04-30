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

    // Время обновления
    private long updatedAt;

    // Время удаления (soft delete) - 0 если активна, >0 если удалена
    private long deletedAt;

    // Время последней синхронизации (для трёхстороннего merge)
    private long lastSyncedAt;

    /**
     * Конструктор по умолчанию.
     * Создает пустой объект Scheme без инициализации полей.
     */
    public Scheme() {
        this.updatedAt = System.currentTimeMillis();
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
        this.updatedAt = System.currentTimeMillis();
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
     * Получить время обновления
     *
     * @return - время обновления
     */
    public long getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Установить время обновления
     *
     * @param updatedAt - время обновления
     */
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Метод для обновления времени
    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Получить время удаления
     *
     * @return время удаления (0 если активна)
     */
    public long getDeletedAt() {
        return deletedAt;
    }

    /**
     * Установить время удаления
     *
     * @param deletedAt время удаления
     */
    public void setDeletedAt(long deletedAt) {
        this.deletedAt = deletedAt;
    }

    /**
     * Проверить, удалена ли схема
     *
     * @return true если удалена, false если активна
     */
    public boolean isDeleted() {
        return deletedAt > 0;
    }

    /**
     * Получить время последней синхронизации
     *
     * @return время последней синхронизации
     */
    public long getLastSyncedAt() {
        return lastSyncedAt;
    }

    /**
     * Установить время последней синхронизации
     *
     * @param lastSyncedAt время последней синхронизации
     */
    public void setLastSyncedAt(long lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
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
                ", updatedAt=" + updatedAt +
                ", deletedAt=" + deletedAt +
                ", lastSyncedAt=" + lastSyncedAt +
                '}';
    }
}
