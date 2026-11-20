package com.vangix.tools;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class VangixClientEntry implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(HelperTasks::tick);

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("vangixs_tools", "status_hud"),
                (GuiGraphicsExtractor context, DeltaTracker deltaTracker) ->
                        HelperTasks.renderOverlay(context, deltaTracker.getGameTimeDeltaPartialTick(true))
        );
    }
}
