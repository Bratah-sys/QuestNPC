package com.questnpc.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C-пакет: Отправляет актуальный PlayerQuestData на клиент для отображения в Журнале квестов.
 */
public class SyncPlayerQuestDataPacket {

    private final CompoundTag tag;

    public SyncPlayerQuestDataPacket(CompoundTag tag) {
        this.tag = tag;
    }

    public static void encode(SyncPlayerQuestDataPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.tag);
    }

    public static SyncPlayerQuestDataPacket decode(FriendlyByteBuf buf) {
        return new SyncPlayerQuestDataPacket(buf.readNbt());
    }

    public static void handle(SyncPlayerQuestDataPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // Выполняется на клиенте
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                // TODO: Сохраняем полученный CompoundTag в клиентский кэш данных игрока,
                // чтобы PlayerQuestScreen мог его прочитать при открытии.
                com.questnpc.client.ClientQuestCache.updateData(msg.tag);
            });
        });
        ctx.setPacketHandled(true);
    }
}