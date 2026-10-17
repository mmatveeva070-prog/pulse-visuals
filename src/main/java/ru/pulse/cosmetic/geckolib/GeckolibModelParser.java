package ru.pulse.cosmetic.geckolib;

import ru.pulse.Pulse;
import ru.pulse.cosmetic.geo.GeoModel;
import ru.pulse.cosmetic.geo.GeoModelParser;
import ru.pulse.cosmetic.model.CosmeticModel;

/** Разбор geo-модели косметики через GeoModelParser (порт из бандля). */
public class GeckolibModelParser {
    public GeoModel parseModel(CosmeticModel cosmetic) {
        try {
            String rawModelJson = cosmetic.getRawModelJson();
            if (rawModelJson == null) {
                Pulse.LOGGER.error("No raw model JSON available for cosmetic: " + cosmetic.getName());
                return null;
            } else {
                GeoModel model = GeoModelParser.parse(rawModelJson);
                if (model == null) {
                    Pulse.LOGGER.error("Failed to parse GeoModel for: " + cosmetic.getName());
                    return null;
                } else {
                    Pulse.LOGGER.info("Successfully parsed model for: " + cosmetic.getName() + " (bones: " + model.topLevelBones.size() + ")");
                    return model;
                }
            }
        } catch (Exception error) {
            Pulse.LOGGER.error("Error parsing model for: " + cosmetic.getName(), error);
            return null;
        }
    }
}
