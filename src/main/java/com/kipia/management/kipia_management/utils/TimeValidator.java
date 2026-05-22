package com.kipia.management.kipia_management.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;

/**
 * Валидатор системного времени для защиты от изменения данных при сбое времени.
 * <p>
 * Поведение:
 * <ul>
 *   <li>При каждом запуске проверяет текущее время относительно сохранённого эталона</li>
 *   <li>Если время сбито (ушло назад более чем на 5 минут) — блокирует запись в текущей сессии</li>
 *   <li>Если время нормальное — работает штатно</li>
 *   <li>Блокировка НЕ сохраняется между запусками — каждый запуск начинается с чистой проверки</li>
 *   <li>Файл состояния хранит только lastValidTime и HMAC-подпись (защита от ручного редактирования)</li>
 * </ul>
 *
 * @author vladimir_shi
 * @since 19.05.2026
 */
public class TimeValidator {
    private static final Logger LOGGER = LogManager.getLogger(TimeValidator.class);

    // ── Синглтон ──────────────────────────────────────────────────────────────
    private static volatile TimeValidator instance;

    // ── Файл состояния ────────────────────────────────────────────────────────
    private static final String TIME_STATE_FILE = "time_state.properties";

    // ── Ключи в файле ─────────────────────────────────────────────────────────
    private static final String KEY_LAST_VALID_TIME = "lastValidTime";
    private static final String KEY_SIGNATURE       = "signature";

    // ── Причины блокировки ────────────────────────────────────────────────────
    public static final String REASON_NONE              = "NONE";
    public static final String REASON_TIME_WENT_BACK    = "TIME_WENT_BACK";
    public static final String REASON_BEFORE_BUILD      = "TIME_BEFORE_BUILD";

    // ── Пороги отклонения ─────────────────────────────────────────────────────
    /** Максимальное допустимое отклонение времени назад: 5 минут */
    private static final long MAX_TIME_DRIFT_BACKWARDS = 5 * 60 * 1000L;
    /** Максимальное допустимое отклонение времени вперёд: 1 час (только логирование) */
    private static final long MAX_TIME_DRIFT_FORWARDS  = 60 * 60 * 1000L;

    // ── Дата сборки ───────────────────────────────────────────────────────────
    /**
     * Unix-время (мс) сборки приложения.
     * Обновляй вручную при каждом релизе.
     * Текущее значение: 2026-05-19 00:00:00 UTC
     */
    private static final long BUILD_TIMESTAMP = 1747612800000L;

    // ── HMAC ──────────────────────────────────────────────────────────────────
    private static final String HMAC_SECRET = "KIPiA_TimeGuard_S3cr3t_2026!";
    private static final String HMAC_ALGO   = "HmacSHA256";

    // ── Состояние (живёт только в памяти, не сохраняется между запусками) ─────
    private final Path stateFilePath;

    private long    lastValidTime;      // Загружается из файла
    private boolean timeValidationEnabled;
    private boolean writeBlocked;       // Только для текущей сессии
    private String  blockReason;        // Только для текущей сессии

    // ─────────────────────────────────────────────────────────────────────────
    // Конструктор и синглтон
    // ─────────────────────────────────────────────────────────────────────────

    private TimeValidator() {
        this.stateFilePath        = resolveStateFilePath();
        this.timeValidationEnabled = true;
        this.writeBlocked         = false;
        this.blockReason          = REASON_NONE;
        loadState();
    }

    public static TimeValidator getInstance() {
        if (instance == null) {
            synchronized (TimeValidator.class) {
                if (instance == null) {
                    instance = new TimeValidator();
                }
            }
        }
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Определение пути к файлу состояния
    // ─────────────────────────────────────────────────────────────────────────

    private Path resolveStateFilePath() {
        String dataDir;

        if (isDevelopmentMode()) {
            dataDir = System.getProperty("user.dir") + "/src/main/resources/data";
            LOGGER.info("Режим разработки: файл состояния в {}", dataDir);
        } else {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isBlank()) {
                appData = System.getProperty("user.home");
                LOGGER.warn("APPDATA не задан, используем user.home: {}", appData);
            }
            dataDir = appData + "/KIPiA_Management/data";
            LOGGER.info("Режим production: файл состояния в {}", dataDir);
        }

        File dataFolder = new File(dataDir);
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            LOGGER.error("Не удалось создать директорию для файла состояния: {}", dataDir);
        }

        return Paths.get(dataDir, TIME_STATE_FILE);
    }

    private boolean isDevelopmentMode() {
        if ("true".equals(System.getProperty("production"))) {
            return false;
        }
        if ("true".equals(System.getProperty("development"))) {
            return true;
        }

        String classPath = System.getProperty("java.class.path");
        String javaHome  = System.getProperty("java.home");

        boolean isDev = classPath.contains("target/classes") ||
                classPath.contains("idea_rt.jar")    ||
                javaHome.contains("IntelliJ")        ||
                classPath.contains(".idea")          ||
                !isRunningFromJAR();

        LOGGER.info("TimeValidator режим: {}", isDev ? "РАЗРАБОТКА" : "ПРОДАКШЕН");
        return isDev;
    }

    private boolean isRunningFromJAR() {
        String className = this.getClass().getName().replace('.', '/');
        String classJar  = Objects.requireNonNull(
                this.getClass().getResource("/" + className + ".class")).toString();
        return classJar.startsWith("jar:");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Загрузка состояния из файла (только lastValidTime)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Загружает lastValidTime из файла.
     * writeBlocked ВСЕГДА сбрасывается в false при запуске.
     */
    private void loadState() {
        if (!Files.exists(stateFilePath)) {
            handleFirstLaunch();
            return;
        }

        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(stateFilePath)) {
            props.load(input);
        } catch (IOException e) {
            LOGGER.error("Ошибка чтения файла состояния: {}", e.getMessage(), e);
            handleFirstLaunch();
            return;
        }

        long savedTime = parseLong(props.getProperty(KEY_LAST_VALID_TIME, "0"));
        String savedSig = props.getProperty(KEY_SIGNATURE, "");

        // Проверяем подпись (без учёта флага блокировки, т.к. его больше нет в файле)
        String expectedSig = computeHmac(savedTime);
        if (!expectedSig.equals(savedSig)) {
            LOGGER.error("HMAC-подпись файла состояния не совпадает — файл был изменён вручную, создаём новый");
            handleFirstLaunch();
            return;
        }

        lastValidTime = savedTime;

        // ВАЖНО: при запуске всегда стартуем с разблокированным состоянием
        writeBlocked = false;
        blockReason = REASON_NONE;

        LOGGER.info("Состояние времени загружено: lastValidTime={}", lastValidTime);
    }

    /**
     * Сохраняет только lastValidTime в файл (без флага блокировки).
     */
    private void saveState() {
        Properties props = new Properties();
        props.setProperty(KEY_LAST_VALID_TIME, String.valueOf(lastValidTime));
        props.setProperty(KEY_SIGNATURE, computeHmac(lastValidTime));

        try (OutputStream output = Files.newOutputStream(stateFilePath)) {
            props.store(output, "KIPiA Time Validation State — do not edit manually");
            LOGGER.debug("Состояние времени сохранено: lastValidTime={}", lastValidTime);
        } catch (IOException e) {
            LOGGER.error("Ошибка сохранения состояния времени: {}", e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Первый запуск / проверка даты сборки
    // ─────────────────────────────────────────────────────────────────────────

    private void handleFirstLaunch() {
        long currentTime = System.currentTimeMillis();

        if (currentTime < BUILD_TIMESTAMP) {
            LOGGER.error("Первый запуск: системное время ({}) раньше даты сборки приложения ({}) — время сбито!",
                    currentTime, BUILD_TIMESTAMP);
            lastValidTime = BUILD_TIMESTAMP;
            // Блокируем текущую сессию, но не сохраняем в файл
            writeBlocked = true;
            blockReason = REASON_BEFORE_BUILD;
        } else {
            LOGGER.info("Первый запуск: системное время корректно, сохраняем как эталон");
            lastValidTime = currentTime;
            writeBlocked = false;
            blockReason = REASON_NONE;
        }

        saveState();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Основная логика валидации (вызывается при каждой записи)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Проверяет системное время перед операцией записи.
     * Вызывать перед каждым INSERT / UPDATE / DELETE.
     *
     * @return true — операция разрешена; false — заблокирована
     */
    public boolean validateTimeForWrite() {
        if (!timeValidationEnabled) {
            return true;
        }

        // Если в текущей сессии уже заблокировано
        if (writeBlocked) {
            LOGGER.warn("Операция записи заблокирована (причина: {})", blockReason);
            return false;
        }

        long currentTime = System.currentTimeMillis();

        // Защита от нулевого/неинициализированного lastValidTime
        if (lastValidTime <= 0) {
            lastValidTime = currentTime;
            saveState();
            return true;
        }

        long timeDiff = currentTime - lastValidTime;

        // Проверка на аномальное отклонение времени НАЗАД
        if (timeDiff < -MAX_TIME_DRIFT_BACKWARDS) {
            LOGGER.error("Обнаружено отклонение времени назад: {} ms (порог: {} ms) — блокируем текущую сессию",
                    Math.abs(timeDiff), MAX_TIME_DRIFT_BACKWARDS);
            blockCurrentSession(REASON_TIME_WENT_BACK);
            return false;
        }

        // Проверка на аномальное отклонение времени ВПЕРЁД (только логируем, не блокируем)
        if (timeDiff > MAX_TIME_DRIFT_FORWARDS) {
            LOGGER.warn("Обнаружено значительное отклонение времени вперёд: {} ms — возможно ручная коррекция", timeDiff);
        }

        // Всё в порядке — обновляем эталон (только если время движется вперёд)
        if (currentTime > lastValidTime) {
            lastValidTime = currentTime;
            saveState();
        }

        return true;
    }

    /**
     * Явная проверка при старте приложения.
     * Вызывается из Main.checkSystemTime().
     *
     * @return true — время в порядке; false — обнаружена аномалия (сессия заблокирована)
     */
    public boolean checkOnStartup() {
        // Всегда начинаем с разблокированного состояния при запуске
        this.writeBlocked = false;
        this.blockReason = REASON_NONE;

        long currentTime = System.currentTimeMillis();

        // Проверка на "запуск в прошлом" (если lastValidTime есть и он в будущем относительно текущего)
        if (lastValidTime > 0 && currentTime < lastValidTime - MAX_TIME_DRIFT_BACKWARDS) {
            LOGGER.error("При запуске обнаружено, что системное время переведено назад на {} мс относительно сохранённого эталона",
                    lastValidTime - currentTime);
            blockCurrentSession(REASON_TIME_WENT_BACK);
            return false;
        }

        // Дополнительная проверка: не раньше ли даты сборки?
        if (currentTime < BUILD_TIMESTAMP) {
            LOGGER.error("При запуске обнаружено, что системное время ({}) раньше даты сборки приложения ({})",
                    currentTime, BUILD_TIMESTAMP);
            blockCurrentSession(REASON_BEFORE_BUILD);
            return false;
        }

        // Всё хорошо, обновляем lastValidTime до текущего времени
        LOGGER.info("Проверка при запуске: время корректно, lastValidTime обновлён с {} на {}", lastValidTime, currentTime);
        lastValidTime = currentTime;
        saveState();

        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Управление блокировкой (только для текущей сессии)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Блокирует запись в ТЕКУЩЕЙ сессии.
     * Блокировка НЕ сохраняется в файл.
     */
    private void blockCurrentSession(String reason) {
        this.writeBlocked = true;
        this.blockReason = reason;
        // НЕ вызываем saveState() — блокировка только в памяти
        LOGGER.error("Текущая сессия заблокирована. Причина: {}. После перезапуска приложения блокировка снимется.", reason);
    }

    /**
     * Ручная разблокировка в текущей сессии (например, по кнопке от администратора).
     */
    public void unblockWriteOperations() {
        if (writeBlocked) {
            long currentTime = System.currentTimeMillis();
            lastValidTime = currentTime;
            writeBlocked = false;
            blockReason = REASON_NONE;
            saveState();
            LOGGER.info("Операции записи разблокированы в текущей сессии");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HMAC (только для lastValidTime)
    // ─────────────────────────────────────────────────────────────────────────

    private String computeHmac(long time) {
        try {
            String payload = String.valueOf(time);
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            LOGGER.error("Ошибка вычисления HMAC: {}", e.getMessage(), e);
            return "";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Геттеры
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isWriteBlocked() {
        return writeBlocked;
    }

    public void setTimeValidationEnabled(boolean enabled) {
        this.timeValidationEnabled = enabled;
        LOGGER.info("Валидация времени: {}", enabled ? "включена" : "выключена");
    }

    public long getLastValidTime() {
        return lastValidTime;
    }

    /**
     * Возвращает текущее время с проверкой валидности.
     *
     * @throws IllegalStateException если операции записи заблокированы
     */
    public long getValidCurrentTime() {
        if (!validateTimeForWrite()) {
            throw new IllegalStateException("Операции записи заблокированы из-за проблемы со временем");
        }
        return System.currentTimeMillis();
    }

    /**
     * Возвращает описание проблемы для отображения пользователю.
     */
    public String getTimeIssueDescription() {
        if (!writeBlocked) {
            return null;
        }

        return switch (blockReason) {
            case REASON_TIME_WENT_BACK ->
                    "Системное время было переведено назад. " +
                            "Операции записи заблокированы до перезапуска приложения. " +
                            "Пожалуйста, проверьте настройки даты и времени и перезапустите приложение.";
            case REASON_BEFORE_BUILD ->
                    "Системное время раньше даты выпуска приложения. " +
                            "Проверьте настройки даты и времени на компьютере и перезапустите приложение.";
            default ->
                    "Обнаружена проблема с системным временем. " +
                            "Операции записи заблокированы до перезапуска приложения.";
        };
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Некорректное значение времени в файле состояния: '{}'", value);
            return 0L;
        }
    }
}