package com.questnpc;

import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Проверяет доступность каждой зависимости мода QuestNPC в runtime.
 * Возвращает результат проверки для каждой библиотеки.
 */
public final class ModIntegrationTester {

    private ModIntegrationTester() {}

    /**
     * Результат проверки одной зависимости.
     */
    public record TestResult(String displayName, String modId, boolean success, String version, String errorMessage) {

        public static TestResult ok(String displayName, String modId, String version) {
            return new TestResult(displayName, modId, true, version, null);
        }

        public static TestResult fail(String displayName, String modId, String errorMessage) {
            return new TestResult(displayName, modId, false, null, errorMessage);
        }
    }

    /**
     * Запускает проверку всех 5 зависимостей и возвращает список результатов.
     */
    public static List<TestResult> runAllTests() {
        List<TestResult> results = new ArrayList<>();

        results.add(testDependency("Bookshelf", "bookshelf", "net.darkhax.bookshelf.api.Services"));
        results.add(testDependency("Curios API", "curios", "top.theillusivec4.curios.api.CuriosApi"));
        results.add(testDependency("GeckoLib", "geckolib", "software.bernie.geckolib.GeckoLib"));
        results.add(testDependency("Patchouli", "patchouli", "vazkii.patchouli.api.PatchouliAPI"));
        results.add(testDependency("Structure Gel API", "structure_gel", "com.legacy.structure_gel.api.structure.ExtendedJigsawStructure"));

        return results;
    }

    /**
     * Проверяет одну зависимость: загружен ли мод + доступен ли ключевой класс.
     */
    private static TestResult testDependency(String displayName, String modId, String keyClassName) {
        QuestNPCLogger.debug("Тест интеграции: {} ({})", displayName, modId);

        try {
            // Проверка 1: мод загружен?
            boolean loaded = ModList.get().isLoaded(modId);
            if (!loaded) {
                String msg = "Мод не найден в ModList";
                QuestNPCLogger.error("ТЕСТ ПРОВАЛЕН [{}]: {}", displayName, msg);
                return TestResult.fail(displayName, modId, msg);
            }

            // Проверка 2: ключевой класс доступен?
            Class.forName(keyClassName);

            // Получаем версию мода
            String version = ModList.get().getModContainerById(modId)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");

            QuestNPCLogger.info("ТЕСТ ПРОЙДЕН [{}]: v{}", displayName, version);
            return TestResult.ok(displayName, modId, version);

        } catch (ClassNotFoundException e) {
            String msg = "Ключевой класс не найден: " + keyClassName;
            QuestNPCLogger.error("ТЕСТ ПРОВАЛЕН [{}]: {}", displayName, msg);
            return TestResult.fail(displayName, modId, msg);

        } catch (Exception e) {
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            QuestNPCLogger.error("ТЕСТ ПРОВАЛЕН [{}]: {}", displayName, msg);
            QuestNPCLogger.error("Стектрейс для " + displayName, e);
            return TestResult.fail(displayName, modId, msg);
        }
    }
}
