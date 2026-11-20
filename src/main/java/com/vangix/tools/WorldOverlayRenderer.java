package com.vangix.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;

public class WorldOverlayRenderer {

    public record TargetBox(BlockPos pos, float r, float g, float b, float fillAlpha, float lineAlpha) {}

    private static final List<TargetBox> BLOCK_TARGETS = new ArrayList<>();
    private static final List<TargetBox> TRACER_BLOCK_TARGETS = new ArrayList<>();

    public static List<TargetBox> getBlockTargets() {
        return BLOCK_TARGETS;
    }

    public static List<TargetBox> getTracerBlockTargets() {
        return TRACER_BLOCK_TARGETS;
    }

    public static void collectTargets(Minecraft mc) {
        BLOCK_TARGETS.clear();
        TRACER_BLOCK_TARGETS.clear();
        if (mc.level == null || mc.player == null) return;

        boolean needEsp = FeatureState.containerSight;
        boolean needBlockTracers = FeatureState.lineGuides && FeatureState.tracerBlocks;

        if (needEsp || needBlockTracers) {
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
                        float[] rgba = matchBlockColor(be);
                        if (rgba != null) {
                            TargetBox box = new TargetBox(
                                    be.getBlockPos().immutable(),
                                    rgba[0], rgba[1], rgba[2],
                                    0.24f,
                                    0.95f
                            );
                            if (needEsp) {
                                BLOCK_TARGETS.add(box);
                            }
                            if (needBlockTracers) {
                                TRACER_BLOCK_TARGETS.add(box);
                            }
                        }
                    }
                }
            }
        }

        if (FeatureState.entitySight || FeatureState.lineGuides) {
            for (Player player : mc.level.players()) {
                if (player == mc.player || !player.isAlive()) continue;
                if (FeatureState.entitySight && (FeatureState.showEntityTag || FeatureState.showEntityGear)) {
                    summarizeGear(player);
                }
            }
        }
    }

    private static float[] matchBlockColor(BlockEntity be) {
        if (FeatureState.filterContainers && (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity)) {
            return new float[]{1.0f, 0.65f, 0.05f};
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
