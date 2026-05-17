# KIPiA Management System

![Java](https://img.shields.io/badge/Java-23-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-25.0.1-blue)
![SQLite](https://img.shields.io/badge/SQLite-3.42-lightgrey)
![Maven](https://img.shields.io/badge/Maven-3.9.6-red)

**KIPiA Management System** — это настольное приложение для управления приборами учета и контроля, с возможностью визуализации схем расположения оборудования, ведения фотоархива и генерации отчетов.

---

## 📋 Содержание
1. [Основные возможности](#-основные-возможности)
2. [Скриншоты интерфейса](#-скриншоты-интерфейса)
3. [Установка и запуск](#-установка-и-запуск)
4. [Технологии](#-технологии)
5. [Структура проекта](#-структура-проекта)
6. [Архитектура приложения](#-архитектура-приложения)
7. [Разработка](#-разработка)
8. [Структура базы данных](#-структура-базы-данных)
9. [Лицензия](#-лицензия)
10. [Контакты и поддержка](#-контакты-и-поддержка)

---

## 🚀 Основные возможности

### 📊 Управление приборами
- **CRUD операции** с приборами (добавление, редактирование, удаление)
- **Группированный просмотр** по месту установки
- **Импорт/экспорт** данных в Excel
- **Валидация данных** при вводе

### 🗺️ Редактор схем
- **Визуальное расположение** приборов на схемах
- **Рисование фигур** (прямоугольники, эллипсы, линии, текст)
- **Привязка приборов** к позициям на схеме
- **Undo/Redo операций**

### 📸 Фотоархив
- **Привязка фотографий** к приборам
- **Галерея изображений** с просмотром
- **Управление фото-коллекцией**

### 📈 Отчетность
- **Генерация отчетов** по приборам
- **Графики и диаграммы** (используется JFreeChart)
- **Экспорт отчетов** в Excel

### ⚙️ Дополнительные функции
- **Темная/светлая тема**
- **Логирование операций** (Log4j2)
- **Работа с буфером обмена**
- **Валидация ячеек таблиц**

---

## 🖼️ Скриншоты интерфейса

### Главный экран
![Главный экран приложения](docs/screenshots/2025-12-14_213837.jpg)
*Навигационное меню и статистика приборов*

### Редактор схем
![Редактор схем с приборами](docs/screenshots/2025-12-14_213840.jpg)
*Визуальное расположение оборудования на плане*

---

## ⚡ Установка и запуск

### Предварительные требования
- **Java JDK 23** или выше
- **Maven 3.9+** (для сборки из исходников)

### Сборка из исходников

```bash
# Клонирование репозитория
git clone https://github.com/VladimirShi136/KIPiA_Management
cd KIPiA_Management

# Сборка проекта
mvn clean package

# Запуск приложения (впишите текущую версию)
java -jar target/KIPiA_Management-*.*.*.jar
```

### Прямой запуск JAR

- Скачайте последний релиз KIPiA_Management-*.*.*.jar
- Убедитесь, что установлена Java 23+
- Запустите:

```bash
java -jar KIPiA_Management-*.*.*.jar
```

---

## 🛠️ Технологии

- **Язык:** Java 23
- **GUI Framework:** JavaFX 25.0.1
- **База данных:** SQLite 3.42
- **Сборка:** Maven
- **Библиотеки:**
    - Apache POI 5.4.0 (работа с Excel)
    - JFreeChart 1.5.4 (графики и диаграммы)
    - Gson 2.10.1 (работа с JSON)
    - Log4j2 2.21.1 (логирование)
    - Apache Commons Lang3 & IO

---

## 🗂️ Структура проекта

<pre>
<code>
<b>KIPiA_Management/</b>
│
├── <b>📁 src/</b>                                 <i># Исходный код</i>
│   ├── <b>📁 main/</b>
│   │   ├── <b>📁 java/com/kipia/management/kipia_management/</b>
│   │   │   ├── Main.java
│   │   │   ├── <b>🎮 controllers/</b>            <i># Контроллеры JavaFX</i>
│   │   │   ├── <b>🗃️ models/</b>                <i># Модели данных</i>
│   │   │   ├── <b>⚙️ services/</b>              <i># Сервисы и DAO</i>
│   │   │   ├── <b>🛠️ utils/</b>                <i># Утилиты</i>
│   │   │   ├── <b>🎨 shapes/</b>                <i># Фигуры редактора</i>
│   │   │   └── <b>🧠 managers/</b>              <i># Менеджеры</i>
│   │   └── <b>📁 resources/</b>
│   │       ├── <b>🖼️ views/</b>                <i># FXML файлы</i>
│   │       ├── <b>🎨 styles/</b>                <i># CSS темы</i>
│   │       ├── <b>🖼️ images/</b>               <i># Иконки</i>
│   │       ├── <b>🗃️ data/</b>                 <i># База данных</i>
│   │       └── <b>📝 logs/</b>                  <i># Логи</i>
│   │
├── <b>📖 docs/</b>                               <i># Документация</i>
│   ├── USER_GUIDE.md
│   ├── SCHEME_EDITOR_GUIDE.md
│   ├── DEPLOYMENT.md
│   └── <b>🖼️ screenshots/</b>                    <i># Скриншоты к документации</i>
│
├── <b>📦 installer_resources/</b>                <i># Ресурсы установщика</i>
│   ├── setup.iss
│   └── build-exe-installer.bat
│
├── <b>⚙️ pom.xml</b>                            <i># Конфигурация Maven</i>
└── <b>📄 README.md</b>                          <i># Вы читаете этот файл</i>
</code>
</pre>

---

## 📊 Архитектура приложения

### Многослойная архитектура:
```
┌─────────────────────────────────────┐
│ Presentation Layer                  │
│ (JavaFX Controllers + FXML Views)   │
├─────────────────────────────────────┤
│ Business Logic                      │
│ (Services, Managers, Utils)         │
├─────────────────────────────────────┤
│ Data Access                         │
│ (DAOs, Database)                    │
├─────────────────────────────────────┤
│ Database                            │
│ (SQLite файл)                       │
└─────────────────────────────────────┘
```

### Ключевые особенности реализации:

1. **Автосохранение схем** - при переходе между разделами и выходе из приложения
2. **Валидация данных** - кастомные ячейки таблиц с проверкой ввода
3. **Undo/Redo система** - в редакторе схем через CommandManager
4. **Менеджер фотографий** - синглтон для управления фотоархивом
5. **Темы оформления** - светлая/темная тема с переключением на лету
6. **Логирование** - Log4j2 с конфигурируемым уровнем детализации

### База данных:
- **Файл:** `data/kipia_management.db`
- **Таблицы:** devices, schemes, device_locations
- **Автоматическое создание** при первом запуске
- **Миграции:** выполняются автоматически через DatabaseService

### Обработка ошибок:
- **Пользовательские алерты** (CustomAlert)
- **Повторная попытка запуска** при критических ошибках
- **Логирование всех исключений** в файл логов

---

## 🔧 Разработка

### Настройка среды разработки

- Установите IntelliJ IDEA с поддержкой JavaFX
- Импортируйте проект как Maven проект
- Убедитесь, что установлен JavaFX SDK 25.0.1

### Запуск в IDE

1. Укажите главный класс: `com.kipia.management.kipia_management.Main`
2. VM Options:

   - Для developer (путь к sdk выбрать свой):

`--module-path
"C:\javafx-sdk-25.0.1\lib"
--add-modules
javafx.controls,javafx.fxml,javafx.graphics,javafx.base
-Dlog4j.configurationFile=src/main/resources/logs/log4j2.xml
--enable-native-access=javafx.graphics,ALL-UNNAMED`

  - Для production (путь к sdk и конфигуратору выбрать свой):

`--module-path
"C:\javafx-sdk-25.0.1\lib"
--add-modules
javafx.controls,javafx.fxml,javafx.graphics,javafx.base
-Dproduction=true
-Dlog4j.configurationFile=file:///C:/Users/kalba/AppData/Roaming/KIPiA_Management/log4j2.xml
--enable-native-access=javafx.graphics,ALL-UNNAMED`

+ При необходимости, добавить `-Dlog4j2.debug=true` для отладки работы логгера

### База данных

- Используется SQLite с файлом `data/kipia_management.db`
- Миграции выполняются автоматически при запуске
- Резервное копирование: скопируйте файл `kipia_management.db`

---

## 🗃️ Структура базы данных

- ### `devices` (Приборы)
```sql
CREATE TABLE devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,              -- Тип прибора
    name TEXT,                       -- Наименование/модель
    manufacturer TEXT,               -- Производитель
    inventory_number TEXT UNIQUE NOT NULL, -- Инвентарный номер
    year INTEGER,                    -- Год выпуска
    measurement_limit TEXT,          -- Предел измерений
    accuracy_class REAL,             -- Класс точности
    location TEXT NOT NULL,          -- Место установки
    valve_number TEXT,               -- Номер крана/узла
    status TEXT DEFAULT 'В работе',  -- Статус
    additional_info TEXT,            -- Дополнительная информация
    photos TEXT                      -- Список имён файлов фото, разделённых точкой с запятой (;)
);
```

- ### `schemes` (Схемы)
```sql
CREATE TABLE schemes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,              -- Название схемы
    description TEXT,                -- Описание схемы
    data TEXT                       -- JSON данные схемы
);
```

- ### `device_locations` (Приборы по местоположению)
```sql
CREATE TABLE device_locations (
    device_id INTEGER NOT NULL,      -- ID прибора
    scheme_id INTEGER NOT NULL,      -- ID схемы
    x REAL NOT NULL,                 -- Координата X
    y REAL NOT NULL,                 -- Координата Y
    rotation REAL DEFAULT 0.0,       -- Поворот в градусах
    PRIMARY KEY (device_id, scheme_id),
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    FOREIGN KEY (scheme_id) REFERENCES schemes(id) ON DELETE CASCADE
);
```

---

## 📄 Лицензия

Этот проект распространяется под лицензией **MIT License**.

Полный текст лицензии доступен в файле [LICENSE](LICENSE).

**Коротко о сути лицензии MIT:**
*   ✅ **Можно:** свободно использовать, копировать, изменять, публиковать, распространять и продавать ПО.
*   ✅ **Можно:** использовать в коммерческих закрытых проектах.
*   ⚠️ **Условие:** во всех копиях или существенных частях должна быть сохранена данная информация об авторском праве и текст лицензии.
*   ❌ **Без гарантий:** автор не несёт ответственности за возможные проблемы при использовании ПО.

---

## 📞 Контакты и поддержка

* Автор: [VladimirShi136](https://github.com/VladimirShi136)
* Issues: [Перейти к вопросам и задачам](https://github.com/VladimirShi136/KIPiA_Management/issues)
* Документация: [Перейти к Wiki](https://github.com/VladimirShi136/KIPiA_Management/wiki)

**_Примечание:_** Для полной документации с пошаговыми инструкциями и скриншотами обратитесь к Wiki проекта.

---