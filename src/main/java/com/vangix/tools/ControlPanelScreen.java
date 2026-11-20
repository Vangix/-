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
    private record CycleOption(
            String label,
            Supplier<String> valueGetter,
            Supplier<Integer> colorGetter,
            Runnable cycler
    ) implements SubOption {}

    private static class ToolRow {
        final String title;
        final Supplier<Boolean> getter;
        final Consumer<Boolean> setter;
        final List<SubOption> options;

        boolean expanded = false;
        float expandAnim = 0.0f;
        float knobAnim = 0.0f;
        float activeAnim = 0.0f;
        float mainHoverAnim = 0.0f;
        float arrowHoverAnim = 0.0f;
        final List<Float> subHoverAnim;

        ToolRow(String title, Supplier<Boolean> getter, Consumer<Boolean> setter, List<SubOption> options) {
            this.title = title;
            this.getter = getter;
            this.setter = setter;
            this.options = options;
            boolean initActive = Boolean.TRUE.equals(getter.get());
            this.knobAnim = initActive ? 1.0f : 0.0f;
            this.activeAnim = initActive ? 1.0f : 0.0f;
            this.subHoverAnim = new ArrayList<>(options.size());
            for (int i = 0; i < options.size(); i++) {
                this.subHoverAnim.add(0.0f);
            }
        }
    }

    private record ClickArea(int x1, int y1, int x2, int y2, int button, Runnable action) {}

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
                        new CycleOption("Color", FeatureState::getGuideColorName, FeatureState::getGuideColor, FeatureState::cycleGuideColor)
                )),
                new ToolRow("HUD", () -> FeatureState.statusHud, v -> FeatureState.statusHud = v, List.of(
                        new CycleOption("Color", FeatureState::getHudColorName, FeatureState::getHudColor, FeatureState::cycleHudColor)
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
    private float openAnim = 0.0f;

    public ControlPanelScreen() {
        super(Component.literal("Vangix tools"));
    }

    @Override
    protected void init() {
        super.init();
        openAnim = 0.0f;
        for (List<ToolRow> rows : PANELS.values()) {
            for (ToolRow row : rows) {
                boolean active = Boolean.TRUE.equals(row.getter.get());
                row.knobAnim = active ? 1.0f : 0.0f;
                row.activeAnim = active ? 1.0f : 0.0f;
                row.expandAnim = row.expanded ? 1.0f : 0.0f;
            }
        }
    }

    private static float approach(float current, float target, float speed) {
        if (current < target) {
            return Math.min(target, current + speed);
        } else if (current > target) {
            return Math.max(target, current - speed);
        }
        return current;
    }

    private static float easeOutCubic(float t) {
        float inv = 1.0f - Math.max(0.0f, Math.min(1.0f, t));
        return 1.0f - inv * inv * inv;
    }

    private static int withAlpha(int argb, float factor) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int newAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * Math.max(0.0f, Math.min(1.0f, factor)))));
        return (newAlpha << 24) | (argb & 0x00FFFFFF);
    }

    private void toggleRow(ToolRow row) {
        boolean next = !Boolean.TRUE.equals(row.getter.get());
        row.setter.accept(next);
        System.out.println("[Vangix] " + row.title + ": " + (next ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        clickAreas.clear();

        float step = Math.min(0.45f, Math.max(0.08f, delta * 0.24f));
        openAnim = approach(openAnim, 1.0f, step * 0.95f);
        float easedOpen = easeOutCubic(openAnim);

        context.fill(0, 0, this.width, this.height, withAlpha(0x6605080F, easedOpen));

        int activeCount = FeatureState.getEnabledTools().size();
        String titleText = "Vangix tools";
        String badgeText = activeCount + " ON";
        int bannerW = Math.max(196, this.font.width(titleText) + this.font.width(badgeText) + 46);
        int bannerH = 22;
        int bannerX = (this.width - bannerW) / 2;
        int bannerY = 8 - Math.round((1.0f - easedOpen) * 16.0f);

        context.fill(bannerX + 2, bannerY + 2, bannerX + bannerW + 2, bannerY + bannerH + 2, withAlpha(0x88000000, easedOpen));
        context.fill(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH, withAlpha(0xF2101520, easedOpen));
        drawFrame(context, bannerX, bannerY, bannerW, bannerH, withAlpha(0xFF334155, easedOpen));

        int midX = bannerX + bannerW / 2;
        context.fill(bannerX + 1, bannerY + bannerH - 2, midX, bannerY + bannerH - 1, withAlpha(0xFF38BDF8, easedOpen));
        context.fill(midX, bannerY + bannerH - 2, bannerX + bannerW - 1, bannerY + bannerH - 1, withAlpha(0xFFA855F7, easedOpen));

        context.text(
                this.font,
                Component.literal(titleText),
                bannerX + 10,
                bannerY + 7,
                withAlpha(0xFFFFFFFF, easedOpen)
        );

        int badgeW = this.font.width(badgeText) + 8;
        int badgeX = bannerX + bannerW - badgeW - 6;
        int badgeY = bannerY + 5;
        context.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 12,
                withAlpha(activeCount > 0 ? 0x3322C55E : 0x2264748B, easedOpen));
        drawFrame(context, badgeX, badgeY, badgeW, 12,
                withAlpha(activeCount > 0 ? 0xFF4ADE80 : 0xFF475569, easedOpen));
        context.centeredText(
                this.font,
                Component.literal(badgeText),
                badgeX + badgeW / 2,
                badgeY + 2,
                withAlpha(activeCount > 0 ? 0xFF86EFAC : 0xFF94A3B8, easedOpen)
        );

        int colWidth = 192;
        int gap = 20;
        int totalWidth = PANELS.size() * colWidth + (PANELS.size() - 1) * gap;
        int startX = Math.max(8, (this.width - totalWidth) / 2);
        int baseStartY = 38 + Math.round((1.0f - easedOpen) * 12.0f);
        int rowHeight = 21;
        int subRowHeight = 16;

        int panelIndex = 0;
        for (Map.Entry<String, List<ToolRow>> entry : PANELS.entrySet()) {
            String header = entry.getKey();
            List<ToolRow> rows = entry.getValue();

            float dynamicContentHeight = 0.0f;
            for (ToolRow row : rows) {
                boolean active = Boolean.TRUE.equals(row.getter.get());
                row.knobAnim = approach(row.knobAnim, active ? 1.0f : 0.0f, step * 1.3f);
                row.activeAnim = approach(row.activeAnim, active ? 1.0f : 0.0f, step * 1.1f);
                row.expandAnim = approach(row.expandAnim, row.expanded ? 1.0f : 0.0f, step * 1.15f);

                float easedExpand = easeOutCubic(row.expandAnim);
                dynamicContentHeight += rowHeight + 2;
                if (!row.options.isEmpty() && easedExpand > 0.001f) {
                    dynamicContentHeight += (row.options.size() * subRowHeight + 4) * easedExpand;
                }
            }

            int panelHeight = 26 + Math.round(dynamicContentHeight) + 6;
            int colX = startX + panelIndex * (colWidth + gap);
            int colY = baseStartY;

            int accentColor = panelIndex == 0 ? 0xFF38BDF8 : 0xFFA855F7;

            context.fill(colX + 3, colY + 3, colX + colWidth + 3, colY + panelHeight + 3, withAlpha(0x88000000, easedOpen));
            context.fill(colX, colY, colX + colWidth, colY + panelHeight, withAlpha(0xEE111520, easedOpen));
            drawFrame(context, colX, colY, colWidth, panelHeight, withAlpha(0xFF2B3548, easedOpen));

            context.fill(colX + 1, colY + 1, colX + colWidth - 1, colY + 21, withAlpha(0xFF171D2B, easedOpen));
            context.fill(colX + 1, colY + 1, colX + colWidth - 1, colY + 3, withAlpha(accentColor, easedOpen));
            context.centeredText(
                    this.font,
                    Component.literal(header),
                    colX + colWidth / 2,
                    colY + 8,
                    withAlpha(0xFFF8FAFC, easedOpen)
            );
            context.fill(colX + 1, colY + 21, colX + colWidth - 1, colY + 22, withAlpha(0xFF283246, easedOpen));

            float yCursor = colY + 26.0f;

            for (ToolRow row : rows) {
                int y = Math.round(yCursor);
                boolean hasSub = !row.options.isEmpty();
                int arrowW = hasSub ? 17 : 0;
                int mainRight = colX + colWidth - 5 - arrowW;

                boolean hoveredMain = mouseX >= colX + 5 && mouseX <= mainRight
                        && mouseY >= y && mouseY < y + rowHeight;
                row.mainHoverAnim = approach(row.mainHoverAnim, hoveredMain ? 1.0f : 0.0f, step * 1.5f);

                boolean active = Boolean.TRUE.equals(row.getter.get());
                float easedActive = easeOutCubic(row.activeAnim);

                context.fill(colX + 5, y, mainRight, y + rowHeight, withAlpha(0xFF181E2C, easedOpen));
                if (easedActive > 0.01f) {
                    context.fill(colX + 5, y, mainRight, y + rowHeight, withAlpha(0x2822C55E, easedActive * easedOpen));
                    context.fill(colX + 5, y, colX + 7, y + rowHeight, withAlpha(0xFF22C55E, easedActive * easedOpen));
                }
                if (row.mainHoverAnim > 0.01f) {
                    context.fill(colX + 5, y, mainRight, y + rowHeight, withAlpha(0x26FFFFFF, row.mainHoverAnim * easedOpen));
                }
                drawFrame(context, colX + 5, y, mainRight - (colX + 5), rowHeight,
                        withAlpha(active ? 0x4422C55E : 0xFF252F42, easedOpen));

                int titleX = colX + (active ? 11 : 10);
                int titleColor = active ? 0xFFFFFFFF : 0xFFCBD5E1;
                context.text(
                        this.font,
                        Component.literal(row.title),
                        titleX,
                        y + 6,
                        withAlpha(titleColor, easedOpen)
                );

                int toggleW = 22;
                int toggleH = 11;
                int toggleX = mainRight - toggleW - 5;
                int toggleY = y + 5;
                float easedKnob = easeOutCubic(row.knobAnim);

                context.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH,
                        withAlpha(active ? 0xFF15803D : 0xFF283244, easedOpen));
                drawFrame(context, toggleX, toggleY, toggleW, toggleH,
                        withAlpha(active ? 0xFF4ADE80 : 0xFF475569, easedOpen));

                int travel = toggleW - toggleH - 2;
                int knobX = toggleX + 1 + Math.round(travel * easedKnob);
                context.fill(knobX, toggleY + 1, knobX + toggleH - 2, toggleY + toggleH - 1, withAlpha(0xFFFFFFFF, easedOpen));

                // ЛКМ по основной части строки или тумблеру включает/выключает функцию
                clickAreas.add(new ClickArea(colX + 5, y, mainRight, y + rowHeight, 0, () -> toggleRow(row)));
                // ПКМ по строке тоже раскрывает доп. настройки, если они есть
                if (hasSub) {
                    clickAreas.add(new ClickArea(colX + 5, y, mainRight, y + rowHeight, 1, () -> {
                        row.expanded = !row.expanded;
                        System.out.println("[Vangix] " + row.title + " options: " + (row.expanded ? "EXPANDED" : "COLLAPSED"));
                    }));
                }

                // Стрелочка справа раскрывает/скрывает доп. настройки по ЛКМ и ПКМ
                if (hasSub) {
                    int arrowX1 = mainRight + 2;
                    int arrowX2 = colX + colWidth - 5;
                    boolean hoveredArrow = mouseX >= arrowX1 && mouseX <= arrowX2
                            && mouseY >= y && mouseY < y + rowHeight;
                    row.arrowHoverAnim = approach(row.arrowHoverAnim, hoveredArrow ? 1.0f : 0.0f, step * 1.5f);

                    context.fill(arrowX1, y, arrowX2, y + rowHeight,
                            withAlpha(row.expanded ? 0x3338BDF8 : 0xFF181E2C, easedOpen));
                    if (row.arrowHoverAnim > 0.01f) {
                        context.fill(arrowX1, y, arrowX2, y + rowHeight, withAlpha(0x3838BDF8, row.arrowHoverAnim * easedOpen));
                    }
                    drawFrame(context, arrowX1, y, arrowX2 - arrowX1, rowHeight,
                            withAlpha(row.expanded ? 0xFF38BDF8 : 0xFF252F42, easedOpen));

                    String arrow = row.expanded ? "v" : ">";
                    context.centeredText(
                            this.font,
                            Component.literal(arrow),
                            (arrowX1 + arrowX2) / 2,
                            y + 6,
                            withAlpha(row.expanded ? 0xFF38BDF8 : 0xFFCBD5E1, easedOpen)
                    );

                    Runnable toggleExpand = () -> {
                        row.expanded = !row.expanded;
                        System.out.println("[Vangix] " + row.title + " options: " + (row.expanded ? "EXPANDED" : "COLLAPSED"));
                    };
                    clickAreas.add(new ClickArea(arrowX1, y, arrowX2, y + rowHeight, 0, toggleExpand));
                    clickAreas.add(new ClickArea(arrowX1, y, arrowX2, y + rowHeight, 1, toggleExpand));
                }

                yCursor += rowHeight + 2;

                float easedExpand = easeOutCubic(row.expandAnim);
                if (hasSub && easedExpand > 0.01f) {
                    int fullSubHeight = row.options.size() * subRowHeight;
                    int visibleSubHeight = Math.max(1, Math.round(fullSubHeight * easedExpand));
                    int subTop = Math.round(yCursor);

                    context.fill(colX + 9, subTop, colX + colWidth - 9, subTop + visibleSubHeight,
                            withAlpha(0xF00B0E17, easedExpand * easedOpen));
                    drawFrame(context, colX + 9, subTop, colWidth - 18, visibleSubHeight,
                            withAlpha(0xFF232D3F, easedExpand * easedOpen));
                    context.fill(colX + 9, subTop, colX + 11, subTop + visibleSubHeight,
                            withAlpha(accentColor, easedExpand * easedOpen));

                    float subY = subTop;
                    for (int i = 0; i < row.options.size(); i++) {
                        SubOption opt = row.options.get(i);
                        int sy = Math.round(subY);

                        if (sy + 4 > subTop + visibleSubHeight) {
                            break;
                        }

                        boolean subHover = mouseX >= colX + 11 && mouseX <= colX + colWidth - 11
                                && mouseY >= sy && mouseY < sy + subRowHeight
                                && easedExpand > 0.7f;
                        float shAnim = approach(row.subHoverAnim.get(i), subHover ? 1.0f : 0.0f, step * 1.5f);
                        row.subHoverAnim.set(i, shAnim);

                        if (shAnim > 0.01f) {
                            context.fill(colX + 11, sy, colX + colWidth - 11, sy + subRowHeight,
                                    withAlpha(0x28FFFFFF, shAnim * easedExpand * easedOpen));
                        }

                        if (opt instanceof BoolOption bOpt) {
                            boolean bVal = Boolean.TRUE.equals(bOpt.getter().get());
                            context.text(
                                    this.font,
                                    Component.literal(bOpt.label()),
                                    colX + 15,
                                    sy + 4,
                                    withAlpha(0xFFE2E8F0, easedExpand * easedOpen)
                            );

                            String badge = bVal ? "ON" : "OFF";
                            int subBadgeW = this.font.width(badge) + 6;
                            int bx = colX + colWidth - 13 - subBadgeW;
                            context.fill(bx, sy + 2, bx + subBadgeW, sy + 13,
                                    withAlpha(bVal ? 0x3322C55E : 0x33EF4444, easedExpand * easedOpen));
                            context.centeredText(
                                    this.font,
                                    Component.literal(badge),
                                    bx + subBadgeW / 2,
                                    sy + 4,
                                    withAlpha(bVal ? 0xFF4ADE80 : 0xFFF87171, easedExpand * easedOpen)
                            );

                            if (easedExpand > 0.65f) {
                                clickAreas.add(new ClickArea(colX + 9, sy, colX + colWidth - 9, sy + subRowHeight, 0, () -> {
                                    boolean next = !Boolean.TRUE.equals(bOpt.getter().get());
                                    bOpt.setter().accept(next);
                                    System.out.println("[Vangix] " + row.title + " -> " + bOpt.label() + ": " + (next ? "ON" : "OFF"));
                                }));
                            }
                        } else if (opt instanceof CycleOption cOpt) {
                            context.text(
                                    this.font,
                                    Component.literal(cOpt.label()),
                                    colX + 15,
                                    sy + 4,
                                    withAlpha(0xFFE2E8F0, easedExpand * easedOpen)
                            );

                            String valText = cOpt.valueGetter().get();
                            int swatchColor = cOpt.colorGetter().get();
                            int valW = this.font.width(valText);
                            int textX = colX + colWidth - 13 - valW;

                            context.fill(textX - 11, sy + 4, textX - 4, sy + 12, withAlpha(swatchColor, easedExpand * easedOpen));
                            drawFrame(context, textX - 11, sy + 4, 7, 8, withAlpha(0xAAFFFFFF, easedExpand * easedOpen));
                            context.text(
                                    this.font,
                                    Component.literal(valText),
                                    textX,
                                    sy + 4,
                                    withAlpha(0xFFFACC15, easedExpand * easedOpen)
                            );

                            if (easedExpand > 0.65f) {
                                clickAreas.add(new ClickArea(colX + 9, sy, colX + colWidth - 9, sy + subRowHeight, 0, () -> {
                                    cOpt.cycler().run();
                                    System.out.println("[Vangix] " + row.title + " -> " + cOpt.label() + ": " + cOpt.valueGetter().get());
                                }));
                            }
                        }

                        subY += subRowHeight;
                    }

                    yCursor += (fullSubHeight + 4) * easedExpand;
                }
            }

            panelIndex++;
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawFrame(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int btn = event.button();

        for (ClickArea area : clickAreas) {
            if (area.button == btn && mx >= area.x1 && mx <= area.x2 && my >= area.y1 && my <= area.y2) {
                area.action.run();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
