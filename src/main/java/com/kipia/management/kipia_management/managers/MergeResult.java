package com.kipia.management.kipia_management.managers;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Результат трёхстороннего merge баз данных.
 * Содержит статистику и список конфликтов для разрешения пользователем.
 * <p>
 * Используется для передачи результата merge из фонового потока в UI-поток
 * при синхронизации баз данных.
 *
 * @param stats               Статистика merge:
 *                            [0] - добавлено устройств
 *                            [1] - обновлено устройств
 *                            [2] - добавлено схем
 *                            [3] - обновлено схем
 * @param importedPhotosCount Количество импортированных фотографий.
 * @param conflicts           Список конфликтов, требующих разрешения пользователем.
 *                            Пустой список если конфликтов нет.
 * @param tempDirectory       Путь к временной директории с распакованными данными.
 *                            Нужен для удаления после применения решений.
 * @param changedDevices      Список изменённых устройств для точечного обновления timestamps.
 * @param changedSchemes      Список изменённых схем для точечного обновления timestamps.
 * @param changedLocations    Список изменённых локаций для точечного обновления timestamps.
 * @author vladimir_shi
 * @since 02.05.2026
 */
public record MergeResult(int[] stats, int importedPhotosCount, List<SyncManager.ConflictInfo> conflicts,
                          Path tempDirectory, List<com.kipia.management.kipia_management.models.Device> changedDevices,
                          List<com.kipia.management.kipia_management.models.Scheme> changedSchemes,
                          List<com.kipia.management.kipia_management.models.DeviceLocation> changedLocations) {

    public MergeResult(int[] stats, int importedPhotosCount,
                       List<SyncManager.ConflictInfo> conflicts, Path tempDirectory) {
        this(stats, importedPhotosCount, conflicts, tempDirectory,
             Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    public int getAddedDevices() {
        return stats[0];
    }

    public int getUpdatedDevices() {
        return stats[1];
    }

    public int getAddedSchemes() {
        return stats[2];
    }

    public int getUpdatedSchemes() {
        return stats[3];
    }

    public int getUpdatedLocations() {
        return stats.length > 4 ? stats[4] : 0;
    }

    @Override
    public String toString() {
        return String.format("MergeResult[devices: +%d/~%d, schemes: +%d/~%d, locations: ~%d, photos: %d, conflicts: %d]",
                stats[0], stats[1], stats[2], stats[3], getUpdatedLocations(), importedPhotosCount, conflicts.size());
    }
}
