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

            context.fill(startX, startY, startX + colWidth, startY + panelHeight, 0xD014171D);
            drawFrame(context, startX, startY, colWidth, panelHeight, 0xFFE6E9EF);
            context.centeredText(this.font, header, startX + colWidth / 2, startY + 6, 0xFFFFFF);

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

                context.text(this.font, row.title, startX + 8, y + 5, 0xF3F4F6);

                int toggleW = 18;
                int toggleH = 10;
                int toggleX = mainRight - toggleW - 4;
                int toggleY = y + 4;
                boolean active = row.getter.get();

                context.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, active ? 0xFF22C55E : 0xFF3F4652);
                int knobX = active ? (toggleX + toggleW - toggleH) : toggleX;
                context.fill(knobX, toggleY, knobX + toggleH, toggleY + toggleH, 0xFFFFFFFF);

                clickAreas.add(new ClickArea(startX + 4, y, mainRight, y + rowHeight - 1,
                        () -> row.setter.accept(!row.getter.get())));

                if (hasSub) {
                    int arrowX1 = mainRight + 1;
                    int arrowX2 = startX + colWidth - 4;
                    boolean hoveredArrow = mouseX >= arrowX1 && mouseX <= arrowX2
                            && mouseY >= y && mouseY < y + rowHeight;

                    if (hoveredArrow) {
                        context.fill(arrowX1, y, arrowX2, y + rowHeight - 1, 0x45FFFFFF);
                    }

                    String arrow = row.expanded ? "▾" : "▸";
                    context.centeredText(this.font, arrow, (arrowX1 + arrowX2) / 2, y + 5, 0xCBD5E1);

                    clickAreas.add(new ClickArea(arrowX1, y, arrowX2, y + rowHeight - 1,
                            () -> row.expanded = !row.expanded));
                }

                y += rowHeight;

                if (row.expanded && hasSub) {
                    int subTop = y;
                    int subHeight = row.options.size() * subRowHeight;
                    context.fill(startX + 8, subTop, startX + colWidth - 8, subTop + subHeight, 0x990D1015);

                    for (SubOption opt : row.options) {
                        boolean subHover = mouseX >= startX + 8 && mouseX <= startX + colWidth - 8
                                && mouseY >= y && mouseY < y + subRowHeight;
                        if (subHover) {
                            context.fill(startX + 8, y, startX + colWidth - 8, y + subRowHeight - 1, 0x28FFFFFF);
                        }

                        if (opt instanceof BoolOption bOpt) {
                            boolean bVal = bOpt.getter().get();
                            context.text(this.font, bOpt.label(), startX + 12, y + 4, 0xCBD5E1);

                            String badge = bVal ? "§aON" : "§cOFF";
                            int badgeW = this.font.width(badge);
                            context.text(this.font, badge, startX + colWidth - 12 - badgeW, y + 4, 0xFFFFFF);

                            clickAreas.add(new ClickArea(startX + 8, y, startX + colWidth - 8, y + subRowHeight - 1,
                                    () -> bOpt.setter().accept(!bOpt.getter().get())));
                        } else if (opt instanceof CycleOption cOpt) {
                            context.text(this.font, cOpt.label(), startX + 12, y + 4, 0xCBD5E1);

                            String valText = "§e" + cOpt.valueGetter().get();
                            int valW = this.font.width(valText);
                            context.text(this.font, valText, startX + colWidth - 12 - valW, y + 4, 0xFFFFFF);

                            clickAreas.add(new ClickArea(startX + 8, y, startX + colWidth - 8, y + subRowHeight - 1,
                                    cOpt.cycler()));
                        }

                        y += subRowHeight;
                    }
                    y += 2;
                }
            }

            startX += colWidth + gap;
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawFrame(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            for (ClickArea area : clickAreas) {
                if (mx >= area.x1 && mx <= area.x2 && my >= area.y1 && my <= area.y2) {
                    area.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
