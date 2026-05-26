package com.questnpc.network;

import com.questnpc.capability.PlayerQuestProvider;
import com.questnpc.capability.QuestKey;
import com.questnpc.capability.QuestRegistry;
import com.questnpc.entity.QuestNPCEntity;
import com.questnpc.entity.quest.QuestDefinition;
import com.questnpc.events.QuestChatHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Stage 8 (v2.9.8): C→S отказ от квеста из
 * {@link com.questnpc.client.gui.PlayerQuestJournalScreen}.
 *
 * <p>В отличие от {@link RequestQuestAbandonPacket} (ID 23, использует {@code npcEntityId}),
 * этот пакет идентифицирует NPC по UUID через {@link QuestRegistry#lookupNpc} —
 * необходимо для случая, когда NPC находится в другом измерении и недоступен через
 * {@code player.level().getEntity(int)}.
 *
 * <p>Пакет ID 26.
 */
public class RequestJournalAbandonPacket {

    private final UUID npcUuid;
    private final String questId;

    public RequestJournalAbandonPacket(UUID npcUuid, String questId) {
        this.npcUuid = npcUuid;
        this.questId = questId;
    }

    public static void encode(RequestJournalAbandonPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.npcUuid);
        buf.writeUtf(msg.questId);
    }

    public static RequestJournalAbandonPacket decode(FriendlyByteBuf buf) {
        return new RequestJournalAbandonPacket(buf.readUUID(), buf.readUtf());
    }

    public static void handle(RequestJournalAbandonPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sp = ctx.getSender();
            if (sp == null) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;

            QuestNPCEntity npc = QuestRegistry.lookupNpc(msg.npcUuid, server);
            // Если NPC не загружен (chunks unloaded) — мы всё равно можем сделать abandon
            // т.к. ключ известен. Используем lookup как best-effort для получения QuestDefinition
            // (для chat-сообщения).
            QuestKey key = new QuestKey(msg.npcUuid, msg.questId);
            QuestDefinition quest = null;
            if (npc != null) {
                for (QuestDefinition q : npc.getQuests()) {
                    if (q.getId().equals(msg.questId)) { quest = q; break; }
                }
            }
            final QuestDefinition finalQuest = quest;
            sp.getCapability(PlayerQuestProvider.CAP).ifPresent(prog -> {
                if (!prog.isActive(key)) return;
                prog.abandonQuest(key);
                if (finalQuest != null) {
                    QuestChatHelper.sendQuestAbandoned(sp, finalQuest);
                }
                QuestChatHelper.syncProgressToClient(sp);
            });
        });
        ctx.setPacketHandled(true);
    }
}
