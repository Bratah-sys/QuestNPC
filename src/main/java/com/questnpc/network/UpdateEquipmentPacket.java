package com.questnpc.network;

import com.questnpc.QuestNPCLogger;
import com.questnpc.entity.QuestNPCEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S-пакет (v2.8.0, ID 16): применяет 4-слотовую экипировку NPC.
 * Слоты: 0=HEAD, 1=CHEST, 2=LEGS, 3=FEET. Хранится в кастомном NBT-ключе Equipment,
 * минуя vanilla armorItems — броня не рендерится на NPC, но даёт защиту через AttributeModifier.
 */
public class UpdateEquipmentPacket {

    private final int entityId;
    private final ItemStack[] equipment;

    public UpdateEquipmentPacket(int entityId, ItemStack[] equipment) {
        this.entityId = entityId;
        if (equipment != null && equipment.length == QuestNPCEntity.EQUIPMENT_SLOTS) {
            this.equipment = equipment;
        } else {
            this.equipment = new ItemStack[]{
                    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
            };
        }
    }

    public static void encode(UpdateEquipmentPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        for (int i = 0; i < QuestNPCEntity.EQUIPMENT_SLOTS; i++) {
            buf.writeItem(packet.equipment[i]);
        }
    }

    public static UpdateEquipmentPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        ItemStack[] eq = new ItemStack[QuestNPCEntity.EQUIPMENT_SLOTS];
        for (int i = 0; i < QuestNPCEntity.EQUIPMENT_SLOTS; i++) {
            eq[i] = buf.readItem();
        }
        return new UpdateEquipmentPacket(entityId, eq);
    }

    public static void handle(UpdateEquipmentPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (!NPCMenuSessionManager.getInstance().isSessionActive(player.getUUID(), packet.entityId)) {
                QuestNPCLogger.warn("Игрок {} без активной сессии — UpdateEquipment отклонён для NPC {}",
                        player.getName().getString(), packet.entityId);
                return;
            }

            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (!(entity instanceof QuestNPCEntity npc)) {
                QuestNPCLogger.warn("Игрок {} попытался изменить экипировку несуществующего NPC {}",
                        player.getName().getString(), packet.entityId);
                return;
            }

            for (int i = 0; i < QuestNPCEntity.EQUIPMENT_SLOTS; i++) {
                npc.setQuestNPCEquipment(i, packet.equipment[i]);
            }

            QuestNPCLogger.info("Игрок {} обновил экипировку NPC {}: head={}, chest={}, legs={}, feet={}",
                    player.getName().getString(), npc.getId(),
                    itemName(packet.equipment[0]), itemName(packet.equipment[1]),
                    itemName(packet.equipment[2]), itemName(packet.equipment[3]));
        });
        ctx.get().setPacketHandled(true);
    }

    private static String itemName(ItemStack s) {
        return s.isEmpty() ? "—" : s.getItem().toString();
    }
}
