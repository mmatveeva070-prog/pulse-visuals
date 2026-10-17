package ru.pulse;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.pulse.config.PulseConfig;

/**
 * Pulse Visuals — main mod entrypoint.
 * Клиентский визуальный мод: косметика игроков + атмосферный пост-процессинг.
 */
public class Pulse implements ModInitializer {
    public static final String MOD_ID = "pulse";
    public static final Logger LOGGER = LoggerFactory.getLogger("Pulse Visuals");

    @Override
    public void onInitialize() {
        // Загружаем конфиг при старте мода (config/pulse.json)
        PulseConfig.get().load();
        LOGGER.info("Pulse Visuals initialized");
    }
}
