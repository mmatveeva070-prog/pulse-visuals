package ru.pulse.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import ru.pulse.atmosphere.AtmospherePostPipeline;
import ru.pulse.atmosphere.AtmosphereSettings;
import ru.pulse.config.PulseConfig;
import ru.pulse.wetworld.WetWorldPipeline;
import ru.pulse.wetworld.WetWorldSettings;

/**
 * Экран настроек (ModMenu + встроенный): атмосфера (вкл/выкл, режим,
 * плотность, солнечные лучи, мягкость, частицы, скорость, дымка,
 * тонировка, виньетка) и флаги показа косметики.
 */
public class AtmosphereSettingsScreen extends Screen {
    private static final int SLIDER_W = 240;
    private static final int SLIDER_H = 20;
    private static final int ROW = 24;

    /** Пресеты цвета рассвета (ARGB) для кнопки dawnColor. */
    private static final int[] DAWN_COLOR_PRESETS = {0xFFFFAD7A, 0xFF7A9EFF, 0xFFAD7AFF, 0xFF7AFFB0, 0xFFFF7A9E};

    private final Screen parent;

    public AtmosphereSettingsScreen(Screen parent) {
        super(Text.literal("Pulse Visuals Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AtmosphereSettings s = PulseConfig.get().atmosphere;
        int x = 24;
        int y = 40;

        // Вкл/выкл атмосферы
        this.addDrawableChild(ButtonWidget.builder(toggleLabel(s.enabled), b -> {
            s.enabled = !s.enabled;
            if (s.enabled) {
                AtmospherePostPipeline.getInstance().reset();
            } else {
                AtmospherePostPipeline.getInstance().close();
            }
            b.setMessage(toggleLabel(s.enabled));
            PulseConfig.get().save();
        }).dimensions(x, y, SLIDER_W, SLIDER_H).build());
        y += ROW;

        // Режим: dawn / dust / snow / embers
        this.addDrawableChild(ButtonWidget.builder(modeLabel(s), b -> {
            s.setMode(s.modeIndex() + 1);
            b.setMessage(modeLabel(s));
            PulseConfig.get().save();
        }).dimensions(x, y, SLIDER_W, SLIDER_H).build());
        y += ROW;

        // Цвет рассвета (только для dawn)
        this.addDrawableChild(ButtonWidget.builder(dawnColorLabel(s), b -> {
            int next = 0;
            for (int i = 0; i < DAWN_COLOR_PRESETS.length; i++) {
                if (DAWN_COLOR_PRESETS[i] == s.dawnColor) {
                    next = (i + 1) % DAWN_COLOR_PRESETS.length;
                    break;
                }
            }
            s.dawnColor = DAWN_COLOR_PRESETS[next];
            b.setMessage(dawnColorLabel(s));
            PulseConfig.get().save();
        }).dimensions(x, y, SLIDER_W, SLIDER_H).build());
        y += ROW;

        // Слайдеры
        y = addSlider(x, y, "Density", 0.05F, 0.8F, s.density, value -> s.density = value);
        y = addSlider(x, y, "Scatter height", 60F, 120F, s.scatterHeight, value -> s.scatterHeight = value);
        y = addSlider(x, y, "God rays", 0F, 100F, s.godRays, value -> s.godRays = value);
        y = addSlider(x, y, "Softness", 0F, 100F, s.softness, value -> s.softness = value);
        y = addSlider(x, y, "Particles", 0F, 100F, s.amount, value -> s.amount = value);
        y = addSlider(x, y, "Speed", 10F, 300F, s.speed, value -> s.speed = value);
        y = addSlider(x, y, "Haze", 0F, 100F, s.haze, value -> s.haze = value);
        y = addSlider(x, y, "Grade", 0F, 100F, s.grade, value -> s.grade = value);
        y = addSlider(x, y, "Vignette", 0F, 100F, s.vignette, value -> s.vignette = value);

        // Мокрый мир (SSR-отражения, рябь)
        y += 8;
        WetWorldSettings w = PulseConfig.get().wetWorld;
        int wy = y;
        this.addDrawableChild(ButtonWidget.builder(wetWorldLabel(w), b -> {
            w.enabled = !w.enabled;
            if (!w.enabled) WetWorldPipeline.getInstance().close();
            else WetWorldPipeline.getInstance().reset();
            b.setMessage(wetWorldLabel(w));
            PulseConfig.get().save();
        }).dimensions(x, wy, SLIDER_W, SLIDER_H).build());
        wy += ROW;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Quality: " + w.qualityName()), b -> {
            w.cycleQuality();
            b.setMessage(Text.literal("Quality: " + w.qualityName()));
            PulseConfig.get().save();
        }).dimensions(x, wy, SLIDER_W, SLIDER_H).build());
        wy += ROW;
        wy = addSlider(x, wy, "Reflection", 0.1F, 1.5F, w.reflectionStrength, value -> w.reflectionStrength = value);
        wy = addSlider(x, wy, "Wet darkening", 0F, 0.8F, w.darkening, value -> w.darkening = value);
        wy = addSlider(x, wy, "Ripple speed", 0.1F, 3F, w.rippleSpeed, value -> w.rippleSpeed = value);
        this.addDrawableChild(ButtonWidget.builder(showLabel("Ripples", w.ripples), b -> {
            w.ripples = !w.ripples;
            b.setMessage(showLabel("Ripples", w.ripples));
            PulseConfig.get().save();
        }).dimensions(x, wy, SLIDER_W, SLIDER_H).build());
        wy += ROW;
        this.addDrawableChild(ButtonWidget.builder(showLabel("Rain only", w.rainOnly), b -> {
            w.rainOnly = !w.rainOnly;
            b.setMessage(showLabel("Rain only", w.rainOnly));
            PulseConfig.get().save();
        }).dimensions(x, wy, SLIDER_W, SLIDER_H).build());
        y = wy + ROW;

        // Косметика: флаги показа
        PulseConfig.CosmeticsSettings cosmetics = PulseConfig.get().cosmetics;
        y += 8;
        this.addDrawableChild(ButtonWidget.builder(showLabel("Show on self", cosmetics.showOnSelf), b -> {
            cosmetics.showOnSelf = !cosmetics.showOnSelf;
            b.setMessage(showLabel("Show on self", cosmetics.showOnSelf));
            PulseConfig.get().save();
        }).dimensions(x, y, SLIDER_W, SLIDER_H).build());
        y += ROW;

        this.addDrawableChild(ButtonWidget.builder(showLabel("Show on others", cosmetics.showOnOthers), b -> {
            cosmetics.showOnOthers = !cosmetics.showOnOthers;
            b.setMessage(showLabel("Show on others", cosmetics.showOnOthers));
            PulseConfig.get().save();
        }).dimensions(x, y, SLIDER_W, SLIDER_H).build());
        y += ROW;

        this.addDrawableChild(ButtonWidget.builder(showLabel("Show capes", cosmetics.showCapes), b -> {
            cosmetics.showCapes = !cosmetics.showCapes;
            b.setMessage(showLabel("Show capes", cosmetics.showCapes));
            PulseConfig.get().save();
        }).dimensions(x, y, SLIDER_W, SLIDER_H).build());

        // Готово
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
            .dimensions(this.width - 100, this.height - 28, 80, 20).build());
    }

    private int addSlider(int x, int y, String label, float min, float max, float current, java.util.function.Consumer<Float> setter) {
        this.addDrawableChild(new SettingSlider(x, y, SLIDER_W, SLIDER_H, label, min, max, current, setter));
        return y + ROW;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFF6EC8FF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        PulseConfig.get().save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static Text toggleLabel(boolean enabled) {
        return Text.literal("Atmosphere: " + (enabled ? "ON" : "OFF"));
    }

    private static Text modeLabel(AtmosphereSettings s) {
        return Text.literal("Mode: " + s.modeName(s.modeIndex()) + (s.isMood() ? " (mood)" : " (dawn)"));
    }

    private static Text dawnColorLabel(AtmosphereSettings s) {
        return Text.literal(String.format("Dawn color: #%08X", s.dawnColor));
    }

    private static Text showLabel(String label, boolean enabled) {
        return Text.literal(label + ": " + (enabled ? "ON" : "OFF"));
    }

    private static Text wetWorldLabel(WetWorldSettings w) {
        return Text.literal("Wet World: " + (w.enabled ? "ON" : "OFF"));
    }

    /** Слайдер одной float-настройки с сохранением в конфиг при отпускании. */
    private static class SettingSlider extends SliderWidget {
        private final String label;
        private final float min;
        private final float max;
        private final java.util.function.Consumer<Float> setter;

        SettingSlider(int x, int y, int width, int height, String label, float min, float max, float current, java.util.function.Consumer<Float> setter) {
            super(x, y, width, height, Text.literal(label), max <= min ? 0.0 : (current - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.updateMessage();
        }

        private float value01() {
            return (float) (this.value * (this.max - this.min) + this.min);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal(this.label + ": " + formatValue(this.value01())));
        }

        @Override
        protected void applyValue() {
            this.setter.accept(this.value01());
        }

        private static String formatValue(float value) {
            if (Math.abs(value - Math.round(value)) < 0.01F) {
                return String.valueOf(Math.round(value));
            }
            return String.format("%.2f", value);
        }
    }
}
