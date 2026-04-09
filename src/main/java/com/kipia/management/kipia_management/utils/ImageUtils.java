package com.kipia.management.kipia_management.utils;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Утилита для работы с изображениями с учетом EXIF-ориентации
 *
 * @author vladimir_shi
 * @since 07.04.2026
 */
public class ImageUtils {
    private static final Logger LOGGER = LogManager.getLogger(ImageUtils.class);

    /**
     * Загружает изображение с автоматической коррекцией ориентации на основе EXIF
     *
     * @param file файл изображения
     * @return изображение с правильной ориентацией
     */
    public static Image loadImageWithCorrectOrientation(File file) {
        try {
            // Загружаем изображение
            Image image = new Image(file.toURI().toString());
            
            if (image.isError()) {
                LOGGER.error("Ошибка загрузки изображения: {}", file.getName());
                return image;
            }

            // Читаем EXIF метаданные
            int orientation = getExifOrientation(file);
            
            // Если ориентация стандартная (1) или не определена, возвращаем как есть
            if (orientation == 1 || orientation == -1) {
                return image;
            }

            // Применяем трансформацию на основе EXIF
            return rotateImage(image, orientation);

        } catch (Exception e) {
            LOGGER.error("Ошибка обработки изображения {}: {}", file.getName(), e.getMessage());
            return new Image(file.toURI().toString());
        }
    }

    /**
     * Получает значение ориентации из EXIF метаданных
     *
     * @param file файл изображения
     * @return код ориентации (1-8) или -1 если не найдено
     */
    private static int getExifOrientation(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            LOGGER.debug("Не удалось прочитать EXIF для {}: {}", file.getName(), e.getMessage());
        }
        return -1;
    }

    /**
     * Поворачивает изображение согласно EXIF ориентации
     *
     * @param image исходное изображение
     * @param orientation код ориентации EXIF (1-8)
     * @return повернутое изображение
     */
    private static Image rotateImage(Image image, int orientation) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        
        PixelReader reader = image.getPixelReader();
        WritableImage rotated;
        PixelWriter writer;

        switch (orientation) {
            case 2: // Flip horizontal
                rotated = new WritableImage(width, height);
                writer = rotated.getPixelWriter();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        writer.setArgb(width - x - 1, y, reader.getArgb(x, y));
                    }
                }
                return rotated;

            case 3: // Rotate 180°
                rotated = new WritableImage(width, height);
                writer = rotated.getPixelWriter();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        writer.setArgb(width - x - 1, height - y - 1, reader.getArgb(x, y));
                    }
                }
                return rotated;

            case 4: // Flip vertical
                rotated = new WritableImage(width, height);
                writer = rotated.getPixelWriter();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        writer.setArgb(x, height - y - 1, reader.getArgb(x, y));
                    }
                }
                return rotated;

            case 5: // Rotate 90° CW + flip horizontal
                rotated = new WritableImage(height, width);
                writer = rotated.getPixelWriter();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        writer.setArgb(y, x, reader.getArgb(x, y));
                    }
                }
                return rotated;

            case 6: // Rotate 90° CW (самый частый случай для Android)
                rotated = new WritableImage(height, width);
                writer = rotated.getPixelWriter();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        writer.setArgb(height - y - 1, x, reader.getArgb(x, y));
                    }
                }
                return rotated;

            case 7: // Rotate 90° CCW + flip horizontal
                rotated = new WritableImage(height, width);
                writer = rotated.getPixelWriter();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        writer.setArgb(y, width - x - 1, reader.getArgb(x, y));
                    }
                }
                return rotated;

            case 8: // Rotate 90° CCW
                rotated = new WritableImage(height, width);
                writer = rotated.getPixelWriter();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        writer.setArgb(y, width - x - 1, reader.getArgb(x, y));
                    }
                }
                return rotated;

            default:
                return image;
        }
    }
}
