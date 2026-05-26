package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C2S-пакет: отправляет измененные диалоги NPC обратно с клиента на сервер.
 * Защищен проверкой сессии через NPCMenuSessionManager.
 */
public class UpdateNPCDialoguesPacket {

    private final int entityId;
    private final boolean dialoguesEnabled;
    private final String startNodeId;
    private final List<CompoundTag> dialogues;

    public UpdateNPCDialoguesPacket(int entityId, boolean dialoguesEnabled, String startNodeId, List<CompoundTag> dialogues) {
        this.entityId = entityId;
        this.dialoguesEnabled = dialoguesEnabled;
        this.startNodeId = startNodeId;
        this.dialogues = dialogues;
    }

    public static void encode(UpdateNPCDialoguesPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.entityId);
        buf.writeBoolean(packet.dialoguesEnabled);
        buf.writeUtf(packet.startNodeId != null ? packet.startNodeId : "start");

        buf.writeInt(packet.dialogues.size());
        for (CompoundTag tag : packet.dialogues) {
            buf.writeNbt(tag);
        }
    }

    public static UpdateNPCDialoguesPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        boolean dialoguesEnabled = buf.readBoolean();
        String startNodeId = buf.readUtf();

        int size = buf.readInt();
        List<CompoundTag> dialogues = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) dialogues.add(tag);
        }
        return new UpdateNPCDialoguesPacket(entityId, dialoguesEnabled, startNodeId, dialogues);
    }

    public static void handle(UpdateNPCDialoguesPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Защита: проверяем активную сессию административного меню у игрока
            if (!NPCMenuSessionManager.getInstance().isSessionActive(player.getUUID(), packet.entityId)) {
                QuestNPCLogger.warn("Игрок {} пытался обновить диалоги NPC {} без активной сессии меню!",
                        player.getName().getString(), packet.entityId);
                return;
            }

            Entity entity = player.level().getEntity(packet.entityId);
            if (entity instanceof QuestNPCEntity npc) {
                // Применяем настройки
                npc.setDialoguesEnabled(packet.dialoguesEnabled);
                npc.setStartNodeId(packet.startNodeId);

                // Пересобираем диалоги на стороне сервера
                QuestNPCLogger.info("Получен пакет. ID NPC: {}. Размер присланных диалогов: {}",
                        packet.entityId, packet.dialogues.size());
                npc.getDialogues().clear();
                for (CompoundTag nodeTag : packet.dialogues) {
                    String id = nodeTag.getString("Id");
                    String npcText = nodeTag.getString("NpcText");

                    com.questnpc.entity.dialogue.DialogueNode node =
                            new com.questnpc.entity.dialogue.DialogueNode(id, npcText);

                    if (nodeTag.contains("Options", Tag.TAG_LIST)) {
                        ListTag optionList = nodeTag.getList("Options", Tag.TAG_COMPOUND);
                        List<com.questnpc.entity.dialogue.DialogueOption> options = new ArrayList<>();
                        for (int j = 0; j < optionList.size(); j++) {
                            CompoundTag optTag = optionList.getCompound(j);
                            options.add(new com.questnpc.entity.dialogue.DialogueOption(
                                    optTag.getString("Text"),
                                    optTag.getString("NextNodeId"),
                                    optTag.getString("Action")
                            ));
                        }
                        node.setOptions(options);
                    }
                    npc.getDialogues().put(node.getId(), node);
                }
                QuestNPCLogger.info("NPC {} теперь имеет диалогов: {}",
                        npc.getId(), npc.getDialogues().size());
                QuestNPCLogger.info("Игрок {} успешно сохранил диалоги для NPC {}. Реплик: {}",
                        player.getName().getString(), npc.getId(), npc.getDialogues().size());
            }
        });
        ctx.setPacketHandled(true);
    }
}