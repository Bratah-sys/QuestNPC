package com.questnpc.entity.ai;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * ИИ-цель: привязать NPC к ближайшему farmnpc_block в радиусе SEARCH_RADIUS.
 * Срабатывает однократно при отсутствии привязки; не блокирует другие цели.
 */
public class BindToBlockGoal extends Goal {

    private final QuestNPCEntity entity;

    // Флаг однократного логирования начала поиска
    private boolean hasLoggedSearching = false;

    public BindToBlockGoal(QuestNPCEntity entity) {
        this.entity = entity;
        // пустой набор флагов — не блокирует движение или взгляд
        this.setFlags(java.util.EnumSet.noneOf(Flag.class));
    }

    /** Запускается каждый тик, пока NPC не привязан. */
    @Override
    public boolean canUse() {
        return !entity.isBound();
    }

    /** Однократный поиск блока; в следующем тике canContinueToUse вернёт false. */
    @Override
    public void start() {
        if (!hasLoggedSearching) {
            QuestNPCLogger.info("NPC {} начал поиск farmnpc_block...", entity.getId());
            hasLoggedSearching = true;
        }
        entity.findAndBindToBlock();
        // Сбрасываем флаг при успешной привязке (лог "нашёл" пишется в findAndBindToBlock)
        if (entity.isBound()) {
            hasLoggedSearching = false;
        }
    }

    /** Цель одноразовая: сразу прекращаем после выполнения. */
    @Override
    public boolean canContinueToUse() {
        return false;
    }

    /**
     * Вызывается извне при потере привязки — разрешает повторный лог поиска.
     */
    public void notifyBlockLost() {
        hasLoggedSearching = false;
    }
}
