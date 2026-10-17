package ru.pulse.cosmetic.geo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Гео-модель (Bedrock-формат), набор корневых костей (порт из бандля). */
public class GeoModel {
    public List<GeoBone> topLevelBones = new ArrayList<>();
    public int textureWidth = 64;
    public int textureHeight = 64;

    public Optional<GeoBone> getBone(String name) {
        for (GeoBone bone : this.topLevelBones) {
            GeoBone found = this.getBoneRecursively(name, bone);
            if (found != null) {
                return Optional.of(found);
            }
        }

        return Optional.empty();
    }

    private GeoBone getBoneRecursively(String name, GeoBone bone) {
        if (bone.name.equals(name)) {
            return bone;
        }

        for (GeoBone child : bone.childBones) {
            GeoBone found = this.getBoneRecursively(name, child);
            if (found != null) {
                return found;
            }
        }

        return null;
    }
}
