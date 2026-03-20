package com.questnpc.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.questnpc.QuestNPCLogger;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Менеджер кастомных моделей.
 * Сканирует папку config/questnpc/custom_models/ на .geo.json файлы,
 * хранит список доступных моделей и проверяет наличие текстур.
 */
public final class CustomModelManager {

    /** Относительный путь к папке кастомных моделей внутри config/. */
    public static final String MODELS_DIR = "questnpc/custom_models";

    /** Префикс для идентификаторов кастомных моделей в NBT / DATA_MODEL_TYPE. */
    public static final String CUSTOM_PREFIX = "custom:";

    private static final CustomModelManager INSTANCE = new CustomModelManager();

    /** Кэш обнаруженных моделей: имя -> информация. */
    private final Map<String, CustomModelInfo> models = new LinkedHashMap<>();

    private CustomModelManager() {}

    public static CustomModelManager getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Публичный API
    // -------------------------------------------------------------------------

    /**
     * Возвращает путь к папке кастомных моделей.
     */
    public Path getModelsDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(MODELS_DIR);
    }

    /**
     * Создаёт папку кастомных моделей если не существует.
     */
    public void ensureDirectoryExists() {
        Path dir = getModelsDirectory();
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                QuestNPCLogger.info("Создана папка кастомных моделей: {}", dir);
            } catch (IOException e) {
                QuestNPCLogger.error("Не удалось создать папку кастомных моделей: {} — {}",
                        dir, e.getMessage());
            }
        }
    }

    /**
     * Сканирует папку моделей и обновляет кэш.
     * Вызывается при открытии браузера моделей.
     */
    public void scanModels() {
        models.clear();
        Path dir = getModelsDirectory();

        if (!Files.isDirectory(dir)) {
            QuestNPCLogger.warn("Папка кастомных моделей не найдена: {}", dir);
            return;
        }

        QuestNPCLogger.debug("Сканирование папки моделей: {}", dir);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.geo.json")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                // Извлекаем имя: "cool_kid.geo.json" -> "cool_kid"
                String modelName = fileName.substring(0, fileName.length() - ".geo.json".length());

                // Проверяем наличие текстуры
                Path texturePath = dir.resolve(modelName + ".png");
                boolean hasTexture = Files.exists(texturePath);

                // Парсим .geo.json для получения количества костей
                int boneCount = countBones(file);

                models.put(modelName, new CustomModelInfo(modelName, file, hasTexture, boneCount));

                if (hasTexture) {
                    QuestNPCLogger.info("Кастомная модель '{}' успешно загружена (bones: {}, текстура: есть)",
                            modelName, boneCount);
                } else {
                    QuestNPCLogger.warn("Текстура для модели '{}' не найдена, используется fallback", modelName);
                }
            }
        } catch (IOException e) {
            QuestNPCLogger.error("Ошибка сканирования папки моделей: {}", e.getMessage());
        }

        QuestNPCLogger.info("Открыт файловый браузер кастомных моделей, найдено {} моделей в {}",
                models.size(), dir);
    }

    /**
     * Возвращает неизменяемую коллекцию обнаруженных моделей.
     */
    public Collection<CustomModelInfo> getModels() {
        return Collections.unmodifiableCollection(models.values());
    }

    /**
     * Проверяет существует ли кастомная модель с данным именем.
     */
    public boolean hasModel(String name) {
        // Ленивая проверка: если кэш пуст — проверяем файл напрямую
        if (models.containsKey(name)) return true;
        Path file = getModelsDirectory().resolve(name + ".geo.json");
        return Files.exists(file);
    }

    /**
     * Проверяет есть ли текстура для данной модели.
     */
    public boolean hasTexture(String name) {
        CustomModelInfo info = models.get(name);
        if (info != null) return info.hasTexture();
        Path file = getModelsDirectory().resolve(name + ".png");
        return Files.exists(file);
    }

    /**
     * Проверяет является ли строка modelType кастомной моделью.
     */
    public static boolean isCustomModel(String modelType) {
        return modelType != null && modelType.startsWith(CUSTOM_PREFIX);
    }

    /**
     * Извлекает имя модели из полного идентификатора.
     * "custom:cool_kid" -> "cool_kid"
     */
    public static String extractModelName(String modelType) {
        if (isCustomModel(modelType)) {
            return modelType.substring(CUSTOM_PREFIX.length());
        }
        return modelType;
    }

    // -------------------------------------------------------------------------
    // Внутренние методы
    // -------------------------------------------------------------------------

    /**
     * Парсит .geo.json и считает количество костей (bones).
     */
    private int countBones(Path geoJsonPath) {
        try (Reader reader = Files.newBufferedReader(geoJsonPath)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) return 0;

            var geometryArray = root.getAsJsonObject().getAsJsonArray("minecraft:geometry");
            if (geometryArray == null || geometryArray.isEmpty()) return 0;

            var firstGeometry = geometryArray.get(0).getAsJsonObject();
            var bones = firstGeometry.getAsJsonArray("bones");
            return bones != null ? bones.size() : 0;
        } catch (Exception e) {
            QuestNPCLogger.error("Ошибка парсинга .geo.json: {} — {}", geoJsonPath, e.getMessage());
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Информация о кастомной модели
    // -------------------------------------------------------------------------

    /**
     * Информация об обнаруженной кастомной модели.
     */
    public record CustomModelInfo(String name, Path filePath, boolean hasTexture, int boneCount) {
        /**
         * Возвращает полный идентификатор для использования в DATA_MODEL_TYPE.
         */
        public String getModelTypeId() {
            return CUSTOM_PREFIX + name;
        }
    }
}
