package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S-пакет: клиент отправляет обновлённые настройки NPC на сервер.
 */
public class UpdateNPCSettingsPacket {

    private final int entityId;
    private final double speed;
    private final int delayMin;
    private final int delayMax;
    private final double maxHealth;
    private final boolean heal;

    public UpdateNPCSettingsPacket(int entityId, double speed, int delayMin, int delayMax,
                                   double maxHealth, boolean heal) {
        this.entityId = entityId;
        this.speed = speed;
        this.delayMin = delayMin;
        this.delayMax = delayMax;
        this.maxHealth = maxHealth;
        this.heal = heal;
    }

    public static void encode(UpdateNPCSettingsPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeDouble(packet.speed);
        buf.writeVarInt(packet.delayMin);
        buf.writeVarInt(packet.delayMax);
        buf.writeDouble(packet.maxHealth);
        buf.writeBoolean(packet.heal);
    }

    public static UpdateNPCSettingsPacket decode(FriendlyByteBuf buf) {
        return new UpdateNPCSettingsPacket(
                buf.readVarInt(),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readBoolean()
        );
    }

    public static void handle(UpdateNPCSettingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (!(entity instanceof QuestNPCEntity npc)) {
                QuestNPCLogger.warn("Игрок {} попытался изменить настройки несуществующего NPC {}",
                        player.getName().getString(), packet.entityId);
                return;
            }

            // Проверка серверной сессии (заменяет проверку расстояния)
            if (!NPCMenuSessionManager.getInstance().isSessionActive(player.getUUID(), packet.entityId)) {
                QuestNPCLogger.warn("Игрок {} не имеет активной сессии для NPC {} — пакет UpdateSettings отклонён",
                        player.getName().getString(), packet.entityId);
                return;
            }

            // Валидация скорости
            if (packet.speed < 0.05 || packet.speed > 1.0) {
                QuestNPCLogger.warn("Невалидные настройки от игрока {} для NPC {}: speed={} (вне диапазона 0.05-1.0)",
                        player.getName().getString(), npc.getId(), packet.speed);
                player.sendSystemMessage(Component.translatable("message.questnpc.settings_invalid"));
                return;
            }

            // Валидация задержки
            if (packet.delayMin < 1 || packet.delayMax > 120 || packet.delayMin > packet.delayMax) {
                QuestNPCLogger.warn("Невалидные настройки от игрока {} для NPC {}: delay={}-{} (вне диапазона)",
                        player.getName().getString(), npc.getId(), packet.delayMin, packet.delayMax);
                player.sendSystemMessage(Component.translatable("message.questnpc.settings_invalid"));
                return;
            }

            // Применяем настройки
            double oldSpeed = npc.getPatrolSpeed();
            int oldDelayMin = npc.getPatrolDelayMin();
            int oldDelayMax = npc.getPatrolDelayMax();

            npc.setPatrolSpeed(packet.speed);
            npc.setPatrolDelay(packet.delayMin, packet.delayMax);

            QuestNPCLogger.info("Игрок {} изменил скорость NPC {}: {} -> {}",
                    player.getName().getString(), npc.getId(), oldSpeed, packet.speed);
            QuestNPCLogger.info("Игрок {} изменил задержку NPC {}: {}-{}с -> {}-{}с",
                    player.getName().getString(), npc.getId(), oldDelayMin, oldDelayMax, packet.delayMin, packet.delayMax);

            // v2.8.0: применяем max HP (defense-in-depth: клиент валидирует, сервер тоже).
            if (packet.maxHealth >= 1.0 && packet.maxHealth <= 1024.0) {
                AttributeInstance hp = npc.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null && hp.getBaseValue() != packet.maxHealth) {
                    double oldMax = hp.getBaseValue();
                    hp.setBaseValue(packet.maxHealth);
                    QuestNPCLogger.info("Игрок {} изменил Max HP NPC {}: {} -> {}",
                            player.getName().getString(), npc.getId(), oldMax, packet.maxHealth);
                }
            } else if (packet.maxHealth != 0.0) {
                // 0.0 — sentinel «не менять»; иное вне диапазона — варн.
                QuestNPCLogger.warn("Невалидное Max HP от игрока {} для NPC {}: {} (вне 1.0-1024.0)",
                        player.getName().getString(), npc.getId(), packet.maxHealth);
            }

            if (packet.heal) {
                npc.setHealth(npc.getMaxHealth());
                QuestNPCLogger.info("Игрок {} исцелил NPC {} до {}",
                        player.getName().getString(), npc.getId(), npc.getMaxHealth());
            }

            player.sendSystemMessage(Component.translatable("message.questnpc.settings_applied"));
        });
        ctx.get().setPacketHandled(true);
    }
}
