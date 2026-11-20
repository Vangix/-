package com.vangix.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorldOverlayRenderer {

    public static void render() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (FeatureState.entitySight || FeatureState.lineGuides) {
            for (Player player : mc.level.players()) {
                if (player == mc.player || !player.isAlive()) continue;
                if (FeatureState.entitySight && (FeatureState.showEntityTag || FeatureState.showEntityGear)) {
                    summarizeGear(player);
                }
            }
        }

        if (FeatureState.containerSight) {
            int radius = 4;
            int centerX = mc.player.chunkPosition().x();
            int centerZ = mc.player.chunkPosition().z();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int cx = centerX + dx;
                    int cz = centerZ + dz;
                    if (!mc.level.hasChunk(cx, cz)) continue;
                    if (!(mc.level.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        matchBlockColor(be);
                    }
                }
            }
        }

        if (FeatureState.areaScanner) {
            Map<Long, AreaScanCache.ScanResult> flagged = AreaScanCache.getFlaggedChunks();
            for (Map.Entry<Long, AreaScanCache.ScanResult> entry : flagged.entrySet()) {
                long packed = entry.getKey();
                int cx = ChunkPos.getX(packed);
                int cz = ChunkPos.getZ(packed);
                int minBlockX = cx * 16;
                int minBlockZ = cz * 16;
                if (minBlockX == Integer.MIN_VALUE && minBlockZ == Integer.MIN_VALUE) continue;
            }
        }
    }

    private static float[] matchBlockColor(BlockEntity be) {
        if (FeatureState.filterContainers && (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity)) {
            return new float[]{1.0f, 0.65f, 0.0f};
        }
        if (FeatureState.filterShulkers && be instanceof ShulkerBoxBlockEntity) {
            return new float[]{0.85f, 0.25f, 1.0f};
        }
        if (FeatureState.filterLogicBlocks && be instanceof CommandBlockEntity) {
            return new float[]{0.15f, 0.85f, 1.0f};
        }
        if (FeatureState.filterSpawners && be instanceof SpawnerBlockEntity) {
            return new float[]{1.0f, 0.2f, 0.2f};
        }
        if (FeatureState.filterVaultBlocks && be instanceof EnderChestBlockEntity) {
            return new float[]{0.2f, 1.0f, 0.7f};
        }
        return null;
    }

    private static String summarizeGear(Player player) {
        List<String> parts = new ArrayList<>();
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty()) {
            parts.add("H:" + main.getHoverName().getString());
        }

        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        int pieces = 0;
        for (EquipmentSlot slot : armorSlots) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                pieces++;
            }
        }
        if (pieces > 0) {
            parts.add("Armor:" + pieces + "/4");
        }
        return String.join(" | ", parts);
    }
}
