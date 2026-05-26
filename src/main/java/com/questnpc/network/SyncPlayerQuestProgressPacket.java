package com.questnpc.network;

import com.questnpc.capability.PlayerQuestProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S→C: сервер шлёт игроку полный snapshot его {@link com.questnpc.capability.PlayerQuestProgress}.
 * Используется после каждой мутации (accept/turn-in/abandon, progress update).
 *
 * <p>Stage 5 (v2.9.4): минимальная реализация — client-side просто десериализует
 * в свою local capability.
 *
 * <p>Stage 8 (v2.9.8): расширен списком {@link QuestJournalEntry} — необходим для
 * {@link com.questnpc.client.gui.PlayerQuestJournalScreen}, т.к. на клиенте нет
 * доступа к NPC во всех мирах, поэтому descriptions формируются server-side и
 * шлются вместе с прогрессом. Entries кэшируются в
 * {@link com.questnpc.client.ClientJournalCache}.
 *
 * <p>Пакет ID 24.
 */
public class SyncPlayerQuestProgressPacket {

    private final CompoundTag progressNbt;
    private final List<QuestJournalEntry> journalEntries;

    /** Stage 5 backward-compat constructor (без journal entries). */
    public SyncPlayerQuestProgressPacket(CompoundTag progressNbt) {
        this(progressNbt, new ArrayList<>());
    }

    /** Stage 8 (v2.9.8): основной конструктор с journal entries. */
    public SyncPlayerQuestProgressPacket(CompoundTag progressNbt, List<QuestJournalEntry> journalEntries) {
        this.progressNbt = progressNbt;
        this.journalEntries = journalEntries != null ? journalEntries : new ArrayList<>();
    }

    public static void encode(SyncPlayerQuestProgressPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.progressNbt);
        buf.writeVarInt(msg.journalEntries.size());
        for (QuestJournalEntry e : msg.journalEntries) QuestJournalEntry.encode(e, buf);
    }

    public static SyncPlayerQuestProgressPacket decode(FriendlyByteBuf buf) {
        CompoundTag nbt = buf.readNbt();
        int n = buf.readVarInt();
        List<QuestJournalEntry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) entries.add(QuestJournalEntry.decode(buf));
        return new SyncPlayerQuestProgressPacket(nbt != null ? nbt : new CompoundTag(), entries);
    }

    public static void handle(SyncPlayerQuestProgressPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(msg))
        );
        ctx.setPacketHandled(true);
    }

    private static class ClientHandler {
        static void apply(SyncPlayerQuestProgressPacket msg) {
            if (Minecraft.getInstance().player == null) return;
            Minecraft.getInstance().player.getCapability(PlayerQuestProvider.CAP)
                    .ifPresent(prog -> prog.deserializeNBT(msg.progressNbt));
            // Stage 8 (v2.9.8): кэшируем journal entries для PlayerQuestJournalScreen.
            com.questnpc.client.ClientJournalCache.get().update(msg.journalEntries);
        }
    }
}
