package com.questnpc.client.model;

import com.questnpc.QuestNPC;
import com.questnpc.QuestNPCLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Виртуальный ресурс-пак, загружающий кастомные .geo.json модели и .png текстуры
 * из папки config/questnpc/custom_models/ как Minecraft ресурсы.
 *
 * <p>Маппинг:
 * <ul>
 *   <li>{@code config/questnpc/custom_models/cool_kid.geo.json}
 *       → {@code assets/questnpc/geo/custom/cool_kid.geo.json}</li>
 *   <li>{@code config/questnpc/custom_models/cool_kid.png}
 *       → {@code assets/questnpc/textures/custom/cool_kid.png}</li>
 * </ul>
 */
public class CustomModelPackResources implements PackResources {

    private static final String PACK_ID = "questnpc_custom_models";

    private final Path modelsDir;

    public CustomModelPackResources(Path modelsDir) {
        this.modelsDir = modelsDir;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... pathElements) {
        // Мы не храним корневых ресурсов (pack.mcmeta — не нужен для injected pack)
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        if (packType != PackType.CLIENT_RESOURCES) return null;
        if (!QuestNPC.MOD_ID.equals(location.getNamespace())) return null;

        Path resolved = resolveToFilesystem(location.getPath());
        if (resolved != null && Files.isRegularFile(resolved)) {
            return () -> Files.newInputStream(resolved);
        }
        return null;
    }

    @Override
    public void listResources(PackType packType, String namespace, String pathPrefix,
                              ResourceOutput output) {
        if (packType != PackType.CLIENT_RESOURCES) return;
        if (!QuestNPC.MOD_ID.equals(namespace)) return;

        if (!Files.isDirectory(modelsDir)) return;

        // Обслуживаем geo/custom/ и textures/custom/
        // GeckoLib вызывает listResources с prefix "geo" для обнаружения всех моделей,
        // поэтому нужно отвечать на все префиксы, которые покрывают "geo/custom/"
        if ("geo/custom".startsWith(pathPrefix) || pathPrefix.startsWith("geo/custom/")) {
            listFilesWithExtension(".geo.json", "geo/custom/", output);
        }
        if ("textures/custom".startsWith(pathPrefix) || pathPrefix.startsWith("textures/custom/")) {
            listFilesWithExtension(".png", "textures/custom/", output);
        }
    }

    @Override
    public Set<String> getNamespaces(PackType packType) {
        if (packType == PackType.CLIENT_RESOURCES) {
            return Set.of(QuestNPC.MOD_ID);
        }
        return Set.of();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        // Предоставляем pack.mcmeta виртуально — без этого Pack.readMetaAndCreate вернёт null
        if (serializer == PackMetadataSection.TYPE) {
            return (T) new PackMetadataSection(
                    Component.literal("QuestNPC Custom Models"),
                    15 // pack_format для 1.20.1 client resources
            );
        }
        return null;
    }

    @Override
    public String packId() {
        return PACK_ID;
    }

    @Override
    public void close() {
        // Нет ресурсов для закрытия
    }

    // -------------------------------------------------------------------------
    // Внутренние методы
    // -------------------------------------------------------------------------

    /**
     * Маппит виртуальный ResourceLocation path на реальный путь в файловой системе.
     *
     * <p>Примеры:
     * <ul>
     *   <li>{@code "geo/custom/cool_kid.geo.json"} → {@code config/.../cool_kid.geo.json}</li>
     *   <li>{@code "textures/custom/cool_kid.png"} → {@code config/.../cool_kid.png}</li>
     * </ul>
     */
    @Nullable
    private Path resolveToFilesystem(String resourcePath) {
        if (resourcePath.startsWith("geo/custom/") && resourcePath.endsWith(".geo.json")) {
            String fileName = resourcePath.substring("geo/custom/".length());
            return modelsDir.resolve(fileName);
        }
        if (resourcePath.startsWith("textures/custom/") && resourcePath.endsWith(".png")) {
            String fileName = resourcePath.substring("textures/custom/".length());
            return modelsDir.resolve(fileName);
        }
        return null;
    }

    /**
     * Перечисляет файлы с заданным расширением и отдаёт их через ResourceOutput.
     */
    private void listFilesWithExtension(String extension, String virtualPrefix,
                                        ResourceOutput output) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modelsDir,
                "*" + extension)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                ResourceLocation rl = new ResourceLocation(QuestNPC.MOD_ID,
                        virtualPrefix + fileName);
                output.accept(rl, () -> Files.newInputStream(file));
            }
        } catch (IOException e) {
            QuestNPCLogger.warn("Ошибка перечисления файлов {} в {}: {}",
                    extension, modelsDir, e.getMessage());
        }
    }
}
