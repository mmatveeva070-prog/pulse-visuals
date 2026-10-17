package ru.pulse.atmosphere;

/**
 * Порт настроек модуля Atmosphere (ru.sankwii.module.impl.render.Atmosphere)
 * на собственный конфиг-класс без зависимостей от sankwii API.
 *
 * Поля сериализуются в config/pulse.json, метод {@link #parameters(long)}
 * переносит исходную логику расчёта VisualState без изменений.
 */
public class AtmosphereSettings {
    /** Включён ли пост-процессинг атмосферы. */
    public boolean enabled = false;

    /** Режим: dawn (рассвет), dust (пыль), snow (снег), embers (угли). */
    public String mode = "dawn";

    // --- Dawn mode settings (видимы только в режиме dawn) ---
    /** Плотность дымки рассвета, 0.05..0.8. */
    public float density = 0.35f;
    /** Высота рассеивания, блоки. */
    public float scatterHeight = 76f;
    /** Солнечные лучи, 0..100. */
    public float godRays = 75f;
    /** Мягкость, 0..100. */
    public float softness = 60f;
    /** Цвет рассвета, ARGB. */
    public int dawnColor = 0xffffad7a;

    // --- Mood mode settings (dust/snow/embers) ---
    /** Количество частиц, 0..100. */
    public float amount = 50f;
    /** Скорость частиц, 10..300. */
    public float speed = 100f;
    /** Дымка, 0..100. */
    public float haze = 50f;
    /** Тонировка, 0..100. */
    public float grade = 70f;
    /** Виньетка, 0..100. */
    public float vignette = 40f;

    /** Доступные режимы атмосферы. */
    public static final String[] MODES = {"dawn", "dust", "snow", "embers"};

    public int modeIndex() {
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].equalsIgnoreCase(mode)) {
                return i;
            }
        }
        return 0;
    }

    public String modeName(int index) {
        return MODES[Math.floorMod(index, MODES.length)];
    }

    public void setMode(int index) {
        this.mode = modeName(index);
    }

    public boolean isMood() {
        return modeIndex() != 0;
    }

    /** Копирует значения из другого экземпляра (из загруженного JSON). */
    public void copyFrom(AtmosphereSettings other) {
        if (other == null) {
            return;
        }
        this.enabled = other.enabled;
        this.mode = other.mode;
        this.density = other.density;
        this.scatterHeight = other.scatterHeight;
        this.godRays = other.godRays;
        this.softness = other.softness;
        this.dawnColor = other.dawnColor;
        this.amount = other.amount;
        this.speed = other.speed;
        this.haze = other.haze;
        this.grade = other.grade;
        this.vignette = other.vignette;
    }

    /** Независимая копия настроек (для потоко-безопасного снимка). */
    public AtmosphereSettings cloneSettings() {
        AtmosphereSettings copy = new AtmosphereSettings();
        copy.copyFrom(this);
        return copy;
    }

    /**
     * Native values; semantic color names are inferred for the independent renderer.
     * Логика перенесена из Atmosphere#parameters без изменений.
     */
    public VisualState parameters(long worldTime) {
        int index = modeIndex();
        if (index == 0) {
            int argb = dawnColor;
            float[] warm = {(argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f};
            return new VisualState(0,
                mixLms(new float[]{.16f, .19f, .38f}, warm, .14f),
                scaled(warm, 1.12f, .88f, .62f), new float[]{.56f, .62f, .8f},
                mixLms(warm, new float[]{.95f, .55f, .63f}, .42f),
                new float[]{.6f, .67f, .82f}, scaled(warm, 1.08f, .94f, .72f),
                sunDirection(worldTime, .11f),
                clamp(density, .05f, .8f), scatterHeight,
                percent(godRays), percent(softness), 0, 1, 0, 0, 0);
        }
        int p = index - 1;
        return new VisualState(index, NativePalettes.rgb(p, 4), NativePalettes.rgb(p, 0x10),
            NativePalettes.rgb(p, 0x28), NativePalettes.rgb(p, 0x34),
            NativePalettes.rgb(p, 0x40), NativePalettes.rgb(p, 0x4c),
            sunDirection(worldTime, NativePalettes.value(p, 0x88)),
            0, 76, NativePalettes.sunEnabled(p) ? NativePalettes.value(p, 0x68) : 0, .35f,
            percent(amount), speed / 100f,
            percent(haze) * NativePalettes.value(p, 0x8c) * 2, percent(grade), percent(vignette));
    }

    /** Снимок визуальных параметров, потребляемый пост-пайплайном. */
    public record VisualState(int mode, float[] skyTop, float[] skyHorizon,
        float[] fogCool, float[] fogWarm, float[] shadow, float[] sunColor, float[] sunDirection,
        float density, float scatterHeight, float godRays, float softness,
        float amount, float speed, float haze, float grade, float vignette) {
    }

    /** Day-time sign switches a fixed-height sun east/west (перенесено без изменений). */
    public static float[] sunDirection(long worldTime, float elevation) {
        double cycle = Math.floorMod(worldTime, 24000L) / 24000.0 - .25;
        cycle -= Math.floor(cycle);
        double angle = (2 * cycle + .5 - Math.cos(cycle * Math.PI) * .5) / 3 * 2 * Math.PI;
        float sign = -(float) Math.sin((float) angle) < 0 ? -1 : 1;
        return new float[]{sign * (float) Math.cos(elevation), (float) Math.sin(elevation), 0};
    }

    /** Interpolate cube-root LMS, then return sRGB (перенесено без изменений). */
    public static float[] mixLms(float[] a, float[] b, float weight) {
        float[] x = toLms(a), y = toLms(b);
        float t = clamp(weight, 0, 1);
        for (int i = 0; i < 3; i++) {
            float value = x[i] + (y[i] - x[i]) * t;
            x[i] = value * value * value;
        }
        return new float[]{
            srgb(4.0767417f * x[0] - 3.3077116f * x[1] + .23096994f * x[2]),
            srgb(-1.268438f * x[0] + 2.6097574f * x[1] - .34131938f * x[2]),
            srgb(-.0041960864f * x[0] - .70341861f * x[1] + 1.7076147f * x[2])};
    }

    private static float[] toLms(float[] color) {
        float r = linear(color[0]), g = linear(color[1]), b = linear(color[2]);
        return new float[]{
            (float) Math.cbrt(.41222147f * r + .53633255f * g + .051445995f * b),
            (float) Math.cbrt(.2119035f * r + .6806995f * g + .10739696f * b),
            (float) Math.cbrt(.08830246f * r + .28171885f * g + .6299787f * b)};
    }

    private static float linear(float v) {
        return v <= .04045f ? v / 12.92f : (float) Math.pow((v + .055f) / 1.055f, 2.4);
    }

    private static float srgb(float v) {
        v = clamp(v, 0, 1);
        return v <= .0031308f ? v * 12.92f : (float) (1.055 * Math.pow(v, 1 / 2.4) - .055);
    }

    private static float[] scaled(float[] a, float r, float g, float b) {
        return new float[]{clamp(a[0] * r, 0, 1), clamp(a[1] * g, 0, 1), clamp(a[2] * b, 0, 1)};
    }

    private static float percent(float value) {
        return clamp(value / 100f, 0, 1);
    }

    public static float clamp(float v, float low, float high) {
        return Float.isNaN(v) ? low : Math.max(low, Math.min(high, v));
    }

    /**
     * Exact 0xac-byte palette records extracted from ClientGUI.dll, RVA 0x42f8e0.
     * Field meanings are partly inferred. Word 0 is an integer; word 36 is a flag.
     * This is original data, not the original shader implementation.
     * Перенесено без изменений.
     */
    private static final class NativePalettes {
        private NativePalettes() {
        }

        private static final int[][] WORDS = {
            {0x00000000, 0x3f000000, 0x3f23d70a, 0x3f6147ae, 0x3f570a3d, 0x3f4ccccd, 0x3f333333, 0x3f800000, 0x3f6e147b, 0x3f400000, 0x3f666666, 0x3f570a3d, 0x3f3851ec, 0x3f800000, 0x3f733333, 0x3f5c28f6, 0x3e99999a, 0x3e851eb8, 0x3e75c28f, 0x3f800000, 0x3f75c28f, 0x3f6147ae, 0x3f828f5c, 0x3f866666, 0x3f733333, 0x00000000, 0x3f666666, 0x3e99999a, 0x3eb33333, 0x3e19999a, 0x00000000, 0x3f800000, 0x3f800000, 0x3f800000, 0x3ed70a3d, 0x3eb33333, 0x00000001, 0x3e99999a, 0x3e6b851f, 0x3e051eb8, 0x3f59999a, 0x3f333333, 0x00000000},
            {0x00000001, 0x3f19999a, 0x3f2b851f, 0x3f47ae14, 0x3f5c28f6, 0x3f6147ae, 0x3f6e147b, 0x00000000, 0x00000000, 0x00000000, 0x3f570a3d, 0x3f6147ae, 0x3f70a3d7, 0x3f800000, 0x3f800000, 0x3f800000, 0x3e6147ae, 0x3e8f5c29, 0x3ecccccd, 0x3f733333, 0x3f7851ec, 0x3f800000, 0x3f800000, 0x3f733333, 0x3f400000, 0x00000000, 0x00000000, 0x00000000, 0x3f666666, 0x00000000, 0x00000000, 0x3f800000, 0x3f800000, 0x3f800000, 0x00000000, 0x3f0ccccd, 0x00000000, 0x3e23d70a, 0x3e3851ec, 0x3e75c28f, 0x3f800000, 0x3f266666, 0x3f800000},
            {0x00000002, 0x3d75c28f, 0x3d23d70a, 0x3d23d70a, 0x3f0ccccd, 0x3e6147ae, 0x3da3d70a, 0x3f800000, 0x3f000000, 0x3e4ccccd, 0x3e8f5c29, 0x3e23d70a, 0x3dcccccd, 0x3f800000, 0x3f0ccccd, 0x3e3851ec, 0x3e23d70a, 0x3d75c28f, 0x3d23d70a, 0x3f800000, 0x3f1eb852, 0x3e99999a, 0x3f4ccccd, 0x3f99999a, 0x3f800000, 0x3ca3d70a, 0x3eb33333, 0x3f19999a, 0x3f800000, 0x3ecccccd, 0x3f333333, 0x3f800000, 0x3f19999a, 0x3e99999a, 0x3df5c28f, 0x3f19999a, 0x00000001, 0x3e99999a, 0x3df5c28f, 0x3d23d70a, 0x3f333333, 0x3f333333, 0x00000000}
        };

        public static float value(int palette, int byteOffset) {
            if (palette < 0 || palette > 2 || byteOffset < 0 || byteOffset >= 0xac || byteOffset % 4 != 0)
                throw new IllegalArgumentException("Invalid native palette offset");
            return Float.intBitsToFloat(WORDS[palette][byteOffset / 4]);
        }

        public static float[] rgb(int palette, int byteOffset) {
            return new float[]{value(palette, byteOffset), value(palette, byteOffset + 4), value(palette, byteOffset + 8)};
        }

        public static boolean sunEnabled(int palette) {
            return WORDS[palette][0x90 / 4] != 0;
        }
    }
}
