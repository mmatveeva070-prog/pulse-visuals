package ru.pulse.wetworld;

/**
 * Настройки модуля Wet World (порт ez.redux WetWorld):
 * отражения в реальном времени (SSR) и рябь на мокрых поверхностях.
 *
 * Поля сериализуются в config/pulse.json.
 */
public class WetWorldSettings {
    /** Включён ли проход мокрого мира. */
    public boolean enabled = true;

    /** Качество SSR: 0 Low, 1 Medium, 2 High, 3 Ultra. */
    public int quality = 2;

    /** Сила отражений, 0.1..1.5. */
    public float reflectionStrength = 0.75f;

    /** Затемнение мокрых поверхностей, 0..0.8. */
    public float darkening = 0.30f;

    /** Рябь (капли дождя) на горизонтальных поверхностях. */
    public boolean ripples = true;

    /** Скорость ряби, 0.1..3. */
    public float rippleSpeed = 1.0f;

    /** Применять эффект только под дождём. */
    public boolean rainOnly = false;

    public static final String[] QUALITIES = {"Low", "Medium", "High", "Ultra"};

    public String qualityName() {
        return QUALITIES[Math.max(0, Math.min(quality, QUALITIES.length - 1))];
    }

    public void cycleQuality() {
        quality = (quality + 1) % QUALITIES.length;
    }

    /** Копирует значения из другого экземпляра (из загруженного JSON). */
    public void copyFrom(WetWorldSettings other) {
        if (other == null) {
            return;
        }
        this.enabled = other.enabled;
        this.quality = other.quality;
        this.reflectionStrength = other.reflectionStrength;
        this.darkening = other.darkening;
        this.ripples = other.ripples;
        this.rippleSpeed = other.rippleSpeed;
        this.rainOnly = other.rainOnly;
    }

    /** Независимая копия настроек (для потоко-безопасного снимка). */
    public WetWorldSettings cloneSettings() {
        WetWorldSettings copy = new WetWorldSettings();
        copy.copyFrom(this);
        return copy;
    }
}
