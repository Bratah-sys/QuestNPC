package com.questnpc.network;

import com.questnpc.events.QuestChatHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Stage 8 (v2.9.8): C→S manual refresh from
 * {@link com.questnpc.client.gui.PlayerQuestJournalScreen}.
 *
 * <p>Empty payload — игрок жмёт кнопку «Обновить» → сервер пересобирает journal
 * snapshot и шлёт обратно через {@link SyncPlayerQuestProgressPacket}. Без roundtrip'а
 * данные могут устареть если последний sync был давно (например игрок ушёл в другой мир,
 * админ удалил/добавил квест).
 *
 * <p>Кнопка disabled на 2 секунды после клика — защита от spam'а.
 *
 * <p>Пакет ID 25.
 */
public class RequestJournalRefreshPacket {

    public RequestJournalRefreshPacket() {}

    public static void encode(RequestJournalRefreshPacket msg, FriendlyByteBuf buf) {
        // empty
    }

    public static RequestJournalRefreshPacket decode(FriendlyByteBuf buf) {
        return new RequestJournalRefreshPacket();
    }

    public static void handle(RequestJournalRefreshPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sp = ctx.getSender();
            if (sp == null) return;
            QuestChatHelper.syncProgressToClient(sp);
        });
        ctx.setPacketHandled(true);
    }
}
