package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * C2S-пакет: клиент отправляет запрос на смену модели NPC.
 */
public class ChangeModelPacket {

    private final int entityId;
    private final String modelType;

    public ChangeModelPacket(int entityId, String modelType) {
        this.entityId = entityId;
        this.modelType = modelType != null ? modelType : "";
    }

    public static void encode(ChangeModelPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeUtf(packet.modelType, 256);
    }

    public static ChangeModelPacket decode(FriendlyByteBuf buf) {
        return new ChangeModelPacket(buf.readVarInt(), buf.readUtf(256));
    }

    public static void handle(ChangeModelPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (!(entity instanceof QuestNPCEntity npc)) {
                QuestNPCLogger.warn("Игрок {} попытался сменить модель несуществующего NPC {}",
                        player.getName().getString(), packet.entityId);
                return;
            }

            // Проверка серверной сессии (заменяет проверку расстояния)
            if (!NPCMenuSessionManager.getInstance().isSessionActive(player.getUUID(), packet.entityId)) {
                QuestNPCLogger.warn("Игрок {} не имеет активной сессии для NPC {} — пакет ChangeModel отклонён",
                        player.getName().getString(), packet.entityId);
                return;
            }

            // Валидация: пустая строка = сброс к дефолту, иначе проверяем что EntityType существует
            if (!packet.modelType.isEmpty()) {
                ResourceLocation rl = ResourceLocation.tryParse(packet.modelType);
                if (rl == null || !ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                    QuestNPCLogger.warn("Невалидный тип модели '{}' от игрока {} для NPC {}",
                            packet.modelType, player.getName().getString(), packet.entityId);
                    return;
                }
            }

            String oldModel = npc.getModelEntityType();
            EntityDimensions oldDims = npc.getCurrentDimensions();
            npc.setModelEntityType(packet.modelType);
            EntityDimensions newDims = npc.getCurrentDimensions();

            QuestNPCLogger.info("Игрок {} сменил модель NPC {}: '{}' -> '{}', dimensions: {}x{} -> {}x{}",
                    player.getName().getString(), npc.getId(),
                    oldModel.isEmpty() ? "default" : oldModel,
                    packet.modelType.isEmpty() ? "default" : packet.modelType,
                    oldDims.width, oldDims.height, newDims.width, newDims.height);

            // Локализованное имя для сообщения
            String displayName = packet.modelType.isEmpty() ? "Default" : packet.modelType;
            player.sendSystemMessage(Component.translatable("message.questnpc.model_changed", displayName));
        });
        ctx.get().setPacketHandled(true);
    }
}
