package com.questnpc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S→C: открывает {@link com.questnpc.client.gui.PlayerQuestScreen} на клиенте
 * со списком квестов NPC (offerable / active / turnInReady).
 *
 * <p>Stage 5 (v2.9.4), пакет ID 20.
 */
public class OpenPlayerQuestListPacket {

    private final int npcEntityId;
    private final String npcDisplayName;
    private final List<QuestSnapshots.QuestSnapshot> offerable;
    private final List<QuestSnapshots.QuestProgressSnapshot> active;
    private final List<QuestSnapshots.QuestSnapshot> turnInReady;

    public OpenPlayerQuestListPacket(int npcEntityId,
                                     String npcDisplayName,
                                     List<QuestSnapshots.QuestSnapshot> offerable,
                                     List<QuestSnapshots.QuestProgressSnapshot> active,
                                     List<QuestSnapshots.QuestSnapshot> turnInReady) {
        this.npcEntityId = npcEntityId;
        this.npcDisplayName = npcDisplayName;
        this.offerable = offerable;
        this.active = active;
        this.turnInReady = turnInReady;
    }

    public int getNpcEntityId() { return npcEntityId; }
    public String getNpcDisplayName() { return npcDisplayName; }
    public List<QuestSnapshots.QuestSnapshot> getOfferable() { return offerable; }
    public List<QuestSnapshots.QuestProgressSnapshot> getActive() { return active; }
    public List<QuestSnapshots.QuestSnapshot> getTurnInReady() { return turnInReady; }

    public static void encode(OpenPlayerQuestListPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.npcEntityId);
        buf.writeUtf(msg.npcDisplayName);
        buf.writeVarInt(msg.offerable.size());
        for (QuestSnapshots.QuestSnapshot q : msg.offerable) QuestSnapshots.QuestSnapshot.encode(q, buf);
        buf.writeVarInt(msg.active.size());
        for (QuestSnapshots.QuestProgressSnapshot q : msg.active) QuestSnapshots.QuestProgressSnapshot.encode(q, buf);
        buf.writeVarInt(msg.turnInReady.size());
        for (QuestSnapshots.QuestSnapshot q : msg.turnInReady) QuestSnapshots.QuestSnapshot.encode(q, buf);
    }

    public static OpenPlayerQuestListPacket decode(FriendlyByteBuf buf) {
        int npcEntityId = buf.readInt();
        String npcDisplayName = buf.readUtf();
        int oc = buf.readVarInt();
        List<QuestSnapshots.QuestSnapshot> offerable = new ArrayList<>(oc);
        for (int i = 0; i < oc; i++) offerable.add(QuestSnapshots.QuestSnapshot.decode(buf));
        int ac = buf.readVarInt();
        List<QuestSnapshots.QuestProgressSnapshot> active = new ArrayList<>(ac);
        for (int i = 0; i < ac; i++) active.add(QuestSnapshots.QuestProgressSnapshot.decode(buf));
        int tc = buf.readVarInt();
        List<QuestSnapshots.QuestSnapshot> turnInReady = new ArrayList<>(tc);
        for (int i = 0; i < tc; i++) turnInReady.add(QuestSnapshots.QuestSnapshot.decode(buf));
        return new OpenPlayerQuestListPacket(npcEntityId, npcDisplayName, offerable, active, turnInReady);
    }

    public static void handle(OpenPlayerQuestListPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.open(msg))
        );
        ctx.setPacketHandled(true);
    }

    /** Отдельный inner-класс для client-side кода — изолируем от dedicated server'а. */
    private static class ClientHandler {
        static void open(OpenPlayerQuestListPacket msg) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new com.questnpc.client.gui.PlayerQuestScreen(msg)
            );
        }
    }
}
