package com.vangix.tools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ControlPanelScreen extends Screen {

    private sealed interface SubOption permits BoolOption, CycleOption {}

    private record BoolOption(String label, Supplier<Boolean> getter, Consumer<Boolean> setter) implements SubOption {}
    private record CycleOption(String label, Supplier<String> valueGetter, Runnable cycler) implements SubOption {}

    private static class ToolRow {
        final String title;
        final Supplier<Boolean> getter;
        final Consumer<Boolean> setter;
        final List<SubOption> options;
        boolean expanded = false;

        ToolRow(String title, Supplier<Boolean> getter, Consumer<Boolean> setter, List<SubOption> options) {
            this.title = title;
            this.getter = getter;
            this.setter = setter;
            this.options = options;
        }
    }

    private record ClickArea(int x1, int y1, int x2, int y2, Runnable action) {}

    private static final Map<String, List<ToolRow>> PANELS = new LinkedHashMap<>();

    static {
        PANELS.put("VISUALS", List.of(
                new ToolRow("Player ESP", () -> FeatureState.entitySight, v -> FeatureState.entitySight = v, List.of(
                        new BoolOption("Show Name", () -> FeatureState.showEntityTag, v -> FeatureState.showEntityTag = v),
                        new BoolOption("Show Gear", () -> FeatureState.showEntityGear, v -> FeatureState.showEntityGear = v)
                )),
                new ToolRow("Block ESP", () -> FeatureState.containerSight, v -> FeatureState.containerSight = v, List.of(
                        new BoolOption("Chests / Barrels", () -> FeatureState.filterContainers, v -> FeatureState.filterContainers = v),
                        new BoolOption("Shulkers", () -> FeatureState.filterShulkers, v -> FeatureState.filterShulkers = v),
                        new BoolOption("Command Blocks", () -> FeatureState.filterLogicBlocks, v -> FeatureState.filterLogicBlocks = v),
                        new BoolOption("Spawners", () -> FeatureState.filterSpawners, v -> FeatureState.filterSpawners = v),
                        new BoolOption("Ender Chests", () -> FeatureState.filterVaultBlocks, v -> FeatureState.filterVaultBlocks = v)
                )),
                new ToolRow("Tracers", () -> FeatureState.lineGuides, v -> FeatureState.lineGuides = v, List.of(
                        new CycleOption("Color", FeatureState::getGuideColorName, FeatureState::cycleGuideColor)
                )),
                new ToolRow("HUD", () -> FeatureState.statusHud, v -> FeatureState.statusHud = v, List.of(
                        new CycleOption("Color", FeatureState::getHudColorName, FeatureState::cycleHudColor)
                )),
                new ToolRow("Fullbright", () -> FeatureState.fullbrightFlag, v -> FeatureState.fullbrightFlag = v, List.of()),
                new ToolRow("Chunk Finder", () -> FeatureState.areaScanner, v -> FeatureState.areaScanner = v, List.of())
        ));

        PANELS.put("UTILITY", List.of(
                new ToolRow("Auto Tool", () -> FeatureState.quickHarvest, v -> FeatureState.quickHarvest = v, List.of()),
                new ToolRow("Auto Totem", () -> FeatureState.offhandGuard, v -> FeatureState.offhandGuard = v, List.of())
        ));
    }

    private final List<ClickArea> clickAreas = new ArrayList<>();

    public ControlPanelScreen() {
        super(Component.literal("Vangix's Tools"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        clickAreas.clear();

        int colWidth = 175;
        int gap = 16;
        int totalWidth = PANELS.size() * colWidth + (PANELS.size() - 1) * gap;
        int startX = Math.max(8, (this.width - totalWidth) / 2);
        int startY = 28;
        int rowHeight = 19;
        int subRowHeight = 16;

        for (Map.Entry<String, List<ToolRow>> entry : PANELS.entrySet()) {
            String header = entry.getKey();
            List<ToolRow> rows = entry.getValue();

            int contentHeight = 0;
            for (ToolRow row : rows) {
                contentHeight += rowHeight;
                if (row.expanded && !row.options.isEmpty()) {
                    contentHeight += row.options.size() * subRowHeight + 2;
                }
            }

            int panelHeight = 22 + contentHeight + 6;

            context.fill(startX, startY, startX + colWidth, startY + panelHeight, 0xE614171D);
            drawFrame(context, startX, startY, colWidth, panelHeight, 0xFFE6E9EF);
            context.centeredText(this.font, Component.literal(header), startX + colWidth / 2, startY + 6, 0xFFFFFFFF);

            int y = startY + 22;
            for (ToolRow row : rows) {
                boolean hasSub = !row.options.isEmpty();
                int arrowW = hasSub ? 16 : 0;
                int mainRight = startX + colWidth - 4 - arrowW;

                boolean hoveredMain = mouseX >= startX + 4 && mouseX <= mainRight
                        && mouseY >= y && mouseY < y + rowHeight;

                if (hoveredMain) {
                    context.fill(startX + 4, y, mainRight, y + rowHeight - 1, 0x35FFFFFF);
                }

                context.text(this.font, Component.literal(row.title), startX + 8, y + 5, 0xFFF3F4F6);

                int toggleW = 18;
                int toggleH = 10;
                int toggleX = mainRight - toggleW - 4;
                int toggleY = y + 4;
                boolean active = Boolean.TRUE.equals(row.getter.get());

                context.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, active ? 0xFF22C55E : 0xFF3F4652);
                int knobX = active ? (toggleX + toggleW - toggleH) : toggleX;
                context.fill(knobX, toggleY, knobX + toggleH, toggleY + toggleH, 0xFFFFFFFF);

                clickAreas.add(new ClickArea(startX + 4, y, mainRight, y + rowHeight - 1, () -> {
                    boolean next = !Boolean.TRUE.equals(row.getter.get());
                    row.setter.accept(next);
                    notifyAction(row.title + ": " + (next ? "ON" : "OFF"));
                }));

                if (hasSub) {
                    int arrowX1 = mainRight + 1;
                    int arrowX2 = startX + colWidth - 4;
                    boolean hoveredArrow = mouseX >= arrowX1 && mouseX <= arrowX2
