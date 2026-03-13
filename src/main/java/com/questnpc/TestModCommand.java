package com.questnpc;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Команда /test_mod — запускает проверку всех зависимостей и выводит результат в чат.
 * Доступна всем игрокам (permissionLevel = 0).
 */
public final class TestModCommand {

    private TestModCommand() {}

    /**
     * Регистрирует команду /test_mod в диспетчере команд.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("test_mod")
                        .requires(source -> source.hasPermission(0))
                        .executes(context -> execute(context.getSource()))
        );
        QuestNPCLogger.info("Команда /test_mod зарегистрирована");
    }

    private static int execute(CommandSourceStack source) {
        QuestNPCLogger.info("Выполнение команды /test_mod...");

        // Заголовок
        source.sendSuccess(() -> Component.literal("=== QuestNPC Integration Tests ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        // Запуск тестов
        List<ModIntegrationTester.TestResult> results = ModIntegrationTester.runAllTests();

        int passed = 0;
        for (ModIntegrationTester.TestResult result : results) {
            MutableComponent message;

            if (result.success()) {
                passed++;
                message = Component.literal("✅ [" + result.displayName() + "] ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal("Интеграция успешна (v." + result.version() + ")")
                                .withStyle(ChatFormatting.GREEN));
            } else {
                message = Component.literal("❌ [" + result.displayName() + "] ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal("Ошибка: " + result.errorMessage() + " | Лог: logs/latest.log")
                                .withStyle(ChatFormatting.RED));
            }

            source.sendSuccess(() -> message, false);
        }

        // Итог
        int total = results.size();
        int finalPassed = passed;
        ChatFormatting summaryColor = (passed == total) ? ChatFormatting.GREEN : ChatFormatting.YELLOW;

        source.sendSuccess(() -> Component.literal(
                "QuestNPC v0.0.1-alpha | Тесты: " + finalPassed + "/" + total + " пройдено"
        ).withStyle(summaryColor, ChatFormatting.BOLD), false);

        QuestNPCLogger.info("Команда /test_mod завершена: {}/{} тестов пройдено", passed, total);

        return Command.SINGLE_SUCCESS;
    }
}
