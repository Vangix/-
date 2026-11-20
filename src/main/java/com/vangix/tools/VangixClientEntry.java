package com.vangix.tools;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class VangixClientEntry implements ClientModInitializer {

    private static KeyMapping menuBind;
    private static boolean wasDown = false;

    @Override
    public void onInitializeClient() {
        InputConstants.Key rightShift = InputConstants.Type.KEYSYM.getOrCreate(344);
        menuBind = new KeyMapping(
                "key.vangixs_tools.menu",
                InputConstants.Type.KEYSYM,
                rightShift.getValue(),
                KeyMapping.Category.MISC
        );

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("vangixs_tools", "status_hud"),
                (GuiGraphicsExtractor context, DeltaTracker deltaTracker) ->
                        HelperTasks.renderOverlay(context, deltaTracker.getGameTimeDeltaPartialTick(true))
        );
    }

    private void onClientTick(Minecraft client) {
        HelperTasks.tick(client);

        if (client.player == null) {
            wasDown = false;
            return;
        }

        boolean isDown = menuBind != null && menuBind.isDown();
        if (isDown && !wasDown) {
            if (client.screen == null) {
                client.setScreen(new ControlPanelScreen());
            } else if (client.screen instanceof ControlPanelScreen) {
                client.setScreen(null);
            }
        }
        wasDown = isDown;
    }
}
