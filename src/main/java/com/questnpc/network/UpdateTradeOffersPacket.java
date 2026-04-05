package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S-пакет: обновляет список сделок NPC на сервере.
 */
public class UpdateTradeOffersPacket {

    private static final int MAX_OFFERS = 10;

    private final int entityId;
    private final ListTag offers;

    public UpdateTradeOffersPacket(int entityId, ListTag offers) {
        this.entityId = entityId;
        this.offers = offers != null ? offers : new ListTag();
    }

    public static void encode(UpdateTradeOffersPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        int size = Math.min(packet.offers.size(), MAX_OFFERS);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeNbt(packet.offers.getCompound(i));
        }
    }

    public static UpdateTradeOffersPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        ListTag offers = new ListTag();
        int size = Math.min(buf.readVarInt(), MAX_OFFERS);
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) offers.add(tag);
        }
        return new UpdateTradeOffersPacket(entityId, offers);
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

            // Валидация: у каждой сделки должны быть непустые input1 и output
            ListTag validated = new ListTag();
            for (int i = 0; i < packet.offers.size(); i++) {
                CompoundTag tag = packet.offers.getCompound(i);
                if (!tag.contains("output") || !tag.contains("input1")) continue;
                ItemStack output = ItemStack.of(tag.getCompound("output"));
                ItemStack input1 = ItemStack.of(tag.getCompound("input1"));
                if (!output.isEmpty() && !input1.isEmpty()) {
                    validated.add(tag);
                }
            }

            npc.setTradeOffers(validated);
            QuestNPCLogger.info("Игрок {} обновил {} сделок для NPC {}",
                    player.getName().getString(), validated.size(), packet.entityId);
        });
        ctx.get().setPacketHandled(true);
    }
}
