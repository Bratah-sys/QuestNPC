package com.questnpc.client;

import net.minecraft.nbt.CompoundTag;

/**
 * Локальный кэш квестов игрока на стороне клиента.
 * Обновляется пакетом SyncPlayerQuestDataPacket.
 */
public class ClientQuestCache {

    public static CompoundTag playerData = new CompoundTag();

    public static void updateData(CompoundTag tag) {
        if (tag != null) {
            playerData = tag;
        }
    }
}