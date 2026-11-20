package com.vangix.tools;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class VangixClientEntry implements ClientModInitializer {

    private static KeyMapping menuBind;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("vangixs_tools", "general")
        );

        menuBind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.vangixs_tools.panel",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuBind.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new ControlPanelScreen());
                } else if (client.screen instanceof ControlPanelScreen) {
                    client.setScreen(null);
                }
            }
            HelperTasks.tick(client);
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("vangixs_tools", "status_hud"),
                (GuiGraphicsExtractor context, DeltaTracker deltaTracker) ->
                        HelperTasks.renderOverlay(context, deltaTracker.getGameTimeDeltaPartialTick(true))
        );

        // WorldOverlayRenderer требует обновления под новый API рендера мира в 26.3
    }
}
