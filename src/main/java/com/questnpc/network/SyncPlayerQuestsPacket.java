package com.questnpc.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncPlayerQuestsPacket {
    private final int entityId;
    private final List<CompoundTag> questsData;

    public SyncPlayerQuestsPacket(int entityId, List<CompoundTag> questsData) {
        this.entityId = entityId;
        this.questsData = questsData;
    }

    public static void encode(SyncPlayerQuestsPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.questsData.size());
        for (CompoundTag tag : msg.questsData) {
            buf.writeNbt(tag);
        }
    }

    public static SyncPlayerQuestsPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int size = buf.readInt();
        List<CompoundTag> data = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            data.add(buf.readNbt());
        }
        return new SyncPlayerQuestsPacket(entityId, data);
    }

    public static void handle(SyncPlayerQuestsPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // ВАЖНО: Выполняем только на КЛИЕНТЕ
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                // TODO Stage 5: Здесь мы откроем PlayerQuestScreen
                // Minecraft.getInstance().setScreen(new PlayerQuestScreen(msg.entityId, msg.questsData));
            });
        });
        ctx.setPacketHandled(true);
    }
}