package ru.pulse.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screen.Screen;

/**
 * ModMenu-интеграция: экран настроек Pulse Visuals из списка модов.
 * ModMenu необязателен — entrypoint подхватывается только при его наличии.
 */
public class ModMenuScreens implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (Screen parent) -> new AtmosphereSettingsScreen(parent);
    }
}
