package ru.pulse.cosmetic.model;

/**
 * Точка крепления косметики к модели игрока (порт из бандля).
 * Позиции: FREE, BODY, HEAD, ABOVE_HEAD, руки/ноги.
 */
public enum ModelPosition {
    FREE(-1),
    BODY(1),
    HEAD(3),
    ABOVE_HEAD(-1),
    RIGHT_ARM(-1),
    LEFT_ARM(-1),
    RIGHT_LEG(2),
    LEFT_LEG(0);

    private final int armorSlot;

    ModelPosition(int armorSlot) {
        this.armorSlot = armorSlot;
    }

    public int getId() {
        return this.ordinal();
    }

    public int getArmorSlot() {
        return this.armorSlot;
    }

    public static ModelPosition getById(int id) {
        return id >= 0 && id < values().length ? values()[id] : BODY;
    }
}
