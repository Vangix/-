package com.vangix.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AreaScanCache {

    public static class ScanResult {
        public final int score;
        public final int undergroundMatches;
        public final String summary;

        public ScanResult(int score, int undergroundMatches, String summary) {
            this.score = score;
            this.undergroundMatches = undergroundMatches;
            this.summary = summary;
        }
    }

    private static final Map<Long, ScanResult> ENTRIES = new HashMap<>();
    private static int scanTimer = 0;
    public static int scoreThreshold = 25;

    public static void tick(Minecraft client) {
        if (!FeatureState.areaScanner || client.level == null || client.player == null) {
            return;
        }

        if (scanTimer > 0) {
            scanTimer--;
            return;
        }
        scanTimer = 15;

        int radius = 5;
        ChunkPos center = client.player.chunkPosition();
        Set<Long> seen = new HashSet<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.x() + dx;
                int cz = center.z() + dz;
                if (!client.level.hasChunk(cx, cz)) continue;
                if (!(client.level.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;

                long key = ChunkPos.pack(cx, cz);
                seen.add(key);

                if (!chunk.isEmpty()) {
                    ENTRIES.put(key, evaluateChunk(chunk));
                }
            }
        }

        ENTRIES.keySet().removeIf(k -> !seen.contains(k));
    }

    public static Map<Long, ScanResult> getFlaggedChunks() {
        Map<Long, ScanResult> out = new HashMap<>();
        for (Map.Entry<Long, ScanResult> entry : ENTRIES.entrySet()) {
            if (entry.getValue().score >= scoreThreshold) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private static ScanResult evaluateChunk(LevelChunk chunk) {
        int[][] surfaceHeights = new int[16][16];
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int h = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                surfaceHeights[x][z] = h;
                if (h < minH) minH = h;
                if (h > maxH) maxH = h;
            }
        }

        Set<Block> distinctBlocks = new HashSet<>();
        int matchedCount = 0;
        int undergroundMatches = 0;
        int baseScore = 0;

        LevelChunkSection[] sections = chunk.getSections();
        for (LevelChunkSection section : sections) {
            if (section == null || section.hasOnlyAir()) continue;
            int sectionBottomY = section.bottomBlockY();

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int worldY = sectionBottomY + y;
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        Block block = state.getBlock();

                        if (isNotableBlock(block)) {
                            matchedCount++;
                            distinctBlocks.add(block);

                            boolean isUnderground = worldY < surfaceHeights[x][z] - 3 || worldY < 56;
                            if (isUnderground) {
                                undergroundMatches++;
                                baseScore += getBlockWeight(block) + 3;
                            } else {
                                baseScore += getBlockWeight(block);
                            }
                        }
                    }
                }
            }
        }

        baseScore += distinctBlocks.size() * 4;

        int abruptSteps = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int h = surfaceHeights[x][z];
                if (x + 1 < 16 && Math.abs(h - surfaceHeights[x + 1][z]) >= 8) {
                    abruptSteps++;
                }
                if (z + 1 < 16 && Math.abs(h - surfaceHeights[x][z + 1]) >= 8) {
                    abruptSteps++;
                }
            }
        }

        int terrainScore = 0;
        if (maxH - minH >= 24 && abruptSteps >= 12) {
            terrainScore += 15;
        } else if (abruptSteps >= 20) {
            terrainScore += 10;
        }

        int total = baseScore + terrainScore;
        String summary = "UG:" + undergroundMatches + "/" + matchedCount
                + " (types:" + distinctBlocks.size() + ")"
                + (terrainScore > 0 ? ", Terr+" + terrainScore : "");

        return new ScanResult(total, undergroundMatches, summary);
    }

    private static int getBlockWeight(Block block) {
        if (block instanceof ShulkerBoxBlock || block instanceof BeaconBlock) return 8;
        if (block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof EnderChestBlock) return 5;
        if (block instanceof SpawnerBlock || block instanceof CommandBlock || block instanceof StructureBlock) return 6;
        if (block instanceof HopperBlock || block instanceof ObserverBlock || block instanceof ComparatorBlock || block instanceof RepeaterBlock) return 3;
        return 2;
    }

    private static boolean isNotableBlock(Block block) {
        return block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof EnderChestBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof CraftingTableBlock
                || block instanceof AnvilBlock
                || block instanceof EnchantingTableBlock
                || block instanceof BeaconBlock
                || block instanceof BrewingStandBlock
                || block instanceof BedBlock
                || block instanceof WoolCarpetBlock
                || block instanceof TransparentBlock
                || block instanceof TorchBlock
                || block instanceof WallTorchBlock
                || block instanceof LanternBlock
                || block instanceof CommandBlock
                || block instanceof StructureBlock
                || block instanceof HopperBlock
                || block instanceof DispenserBlock
                || block instanceof DropperBlock
                || block instanceof RedstoneWireBlock
                || block instanceof RepeaterBlock
                || block instanceof ComparatorBlock
                || block instanceof ObserverBlock;
    }
}
