package com.questnpc.network;

import com.questnpc.capability.PlayerQuestProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: сервер шлёт игроку полный snapshot его {@link com.questnpc.capability.PlayerQuestProgress}.
 * Используется после каждой мутации (accept/turn-in/abandon, progress update).
 *
 * <p>Stage 5 (v2.9.4): минимальная реализация — client-side просто десериализует
 * в свою local capability. Полноценное использование (HUD, journal) — Этап 8.
 *
 * <p>Пакет ID 24.
 */
public class SyncPlayerQuestProgressPacket {

    private final CompoundTag progressNbt;

    public SyncPlayerQuestProgressPacket(CompoundTag progressNbt) {
        this.progressNbt = progressNbt;
    }

    public static void encode(SyncPlayerQuestProgressPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.progressNbt);
    }

    public static SyncPlayerQuestProgressPacket decode(FriendlyByteBuf buf) {
        CompoundTag nbt = buf.readNbt();
        return new SyncPlayerQuestProgressPacket(nbt != null ? nbt : new CompoundTag());
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
        }
    }
}
