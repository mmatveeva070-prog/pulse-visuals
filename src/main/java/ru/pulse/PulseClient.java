package ru.pulse;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.pulse.cosmetic.CosmeticCatalog;
import ru.pulse.emote.EmoteManager;
import ru.pulse.gui.CosmeticsScreen;

/**
 * Клиентский entrypoint: клавиши (V — экран косметики, B — эмоут),
 * тик-события, инициализация каталога косметики и атмосферы.
 */
public class PulseClient implements ClientModInitializer {
    private static KeyBinding cosmeticsKey;
    private static KeyBinding emoteKey;

    public static KeyBinding getCosmeticsKey() {
        return cosmeticsKey;
    }

    public static KeyBinding getEmoteKey() {
        return emoteKey;
    }

    @Override
    public void onInitializeClient() {
        // Клавиши: «Косметика» — V, «Эмоут» — B; категория «Pulse»
        cosmeticsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pulse.cosmetics", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, KeyBinding.Category.MISC));
        emoteKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pulse.emote", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, KeyBinding.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (cosmeticsKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new CosmeticsScreen(null));
                }
            }
            while (emoteKey.wasPressed()) {
                EmoteManager.nextEmote();
            }
            EmoteManager.tick();
        });

        // Прогреваем каталог косметики (сканирует assets/pulse/cosmetics/models)
        Pulse.LOGGER.info("Pulse Visuals client initialized, {} cosmetics in catalog", CosmeticCatalog.size());
    }
}
