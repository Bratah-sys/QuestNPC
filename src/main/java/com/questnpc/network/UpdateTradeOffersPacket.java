package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C2S-пакет: обновляет наборы сделок NPC на сервере.
 *
 * <p>В v2.5.0 формат — список именованных наборов ({@code List<TradeSet>}).
 * В v2.4.1 и раньше это был плоский {@code ListTag} сделок.
 */
public class UpdateTradeOffersPacket {

    private static final int MAX_NAME_LEN = 32;

    private final int entityId;
    private final List<QuestNPCEntity.TradeSet> sets;

    public UpdateTradeOffersPacket(int entityId, List<QuestNPCEntity.TradeSet> sets) {
        this.entityId = entityId;
        this.sets = sets != null ? sets : new ArrayList<>();
    }

    public static void encode(UpdateTradeOffersPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        writeTradeSets(buf, packet.sets);
    }

    public static UpdateTradeOffersPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        List<QuestNPCEntity.TradeSet> sets = readTradeSets(buf);
        return new UpdateTradeOffersPacket(entityId, sets);
    }

    /**
     * Общий helper: сериализует список наборов сделок в пакет.
     * Используется из этого пакета и из {@link OpenNPCMenuPacket}.
     */
    public static void writeTradeSets(FriendlyByteBuf buf, List<QuestNPCEntity.TradeSet> sets) {
        int n = Math.min(sets != null ? sets.size() : 0, QuestNPCEntity.MAX_TRADE_SETS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            QuestNPCEntity.TradeSet s = sets.get(i);
            buf.writeUtf(s.name == null ? "" : s.name, MAX_NAME_LEN);
            CompoundTag wrap = new CompoundTag();
            wrap.put("Offers", s.offers == null ? new ListTag() : s.offers);
            buf.writeNbt(wrap);
        }
    }

    /** Симметричное чтение: возвращает список наборов сделок, клампится до {@link QuestNPCEntity#MAX_TRADE_SETS}. */
    public static List<QuestNPCEntity.TradeSet> readTradeSets(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), QuestNPCEntity.MAX_TRADE_SETS);
        List<QuestNPCEntity.TradeSet> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = buf.readUtf(MAX_NAME_LEN);
            CompoundTag wrap = buf.readNbt();
            ListTag offers = wrap != null
                    ? wrap.getList("Offers", Tag.TAG_COMPOUND)
                    : new ListTag();
            out.add(new QuestNPCEntity.TradeSet(name, offers));
        }
        return out;
    }

    public static void handle(UpdateTradeOffersPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (!(entity instanceof QuestNPCEntity npc)) {
                QuestNPCLogger.warn("UpdateTradeOffers: NPC {} не найден", packet.entityId);
                return;
            }

            if (!NPCMenuSessionManager.getInstance().isSessionActive(player.getUUID(), packet.entityId)) {
                QuestNPCLogger.warn("UpdateTradeOffers: нет активной сессии для игрока {} NPC {}",
                        player.getName().getString(), packet.entityId);
                return;
            }

            List<QuestNPCEntity.TradeSet> validated = new ArrayList<>();
            int setLimit = Math.min(packet.sets.size(), QuestNPCEntity.MAX_TRADE_SETS);
            int totalOffers = 0;
            for (int i = 0; i < setLimit; i++) {
                QuestNPCEntity.TradeSet raw = packet.sets.get(i);
                if (raw == null) continue;

                String name = raw.name != null ? raw.name.trim() : "";
                if (name.isEmpty()) name = QuestNPCEntity.DEFAULT_TRADE_SET_NAME;
                if (name.length() > MAX_NAME_LEN) name = name.substring(0, MAX_NAME_LEN);

                ListTag validOffers = new ListTag();
                ListTag rawOffers = raw.offers != null ? raw.offers : new ListTag();
                for (int j = 0; j < rawOffers.size() && validOffers.size() < QuestNPCEntity.MAX_OFFERS_PER_SET; j++) {
                    CompoundTag tag = rawOffers.getCompound(j);
                    if (!tag.contains("output") || !tag.contains("input1")) continue;
                    ItemStack output = ItemStack.of(tag.getCompound("output"));
                    ItemStack input1 = ItemStack.of(tag.getCompound("input1"));
                    if (!output.isEmpty() && !input1.isEmpty()) {
                        validOffers.add(tag);
                    }
                }
                validated.add(new QuestNPCEntity.TradeSet(name, validOffers));
                totalOffers += validOffers.size();
            }

            npc.setTradeSets(validated);
            QuestNPCLogger.info("Игрок {} обновил {} наборов ({} сделок) для NPC {}",
                    player.getName().getString(), validated.size(), totalOffers, packet.entityId);
        });
        ctx.get().setPacketHandled(true);
    }
}
