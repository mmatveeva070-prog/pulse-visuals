package ru.pulse.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.pulse.atmosphere.AtmosphereSettings;
import ru.pulse.config.PulseConfig;

/**
 * Порт AtmosphericFogModifierMixin из «Асмасферы»: вместо отсутствующего
 * CustomWorld управляется AtmosphereSettings из нашего конфига —
 * подкрашивает цвет тумана и поджимает дальность по «дымке» режима.
 */
@Mixin(AtmosphericFogModifier.class)
public abstract class AtmosphericFogModifierMixin {

    @Inject(require = 0, method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void pulse$tintFogColor(ClientWorld world, Camera camera, int viewDistance, float tickProgress, CallbackInfoReturnable<Integer> cir) {
        AtmosphereSettings settings = PulseConfig.get().atmosphere;
        if (!settings.enabled || world == null) {
            return;
        }

        AtmosphereSettings.VisualState state = settings.parameters(world.getTimeOfDay());
        int original = cir.getReturnValue();
        // Смешиваем ванильный цвет тумана с холодным/тёплым цветами атмосферы
        float mix = settings.isMood() ? 0.35F * state.haze() : 0.25F * state.density();
        int r = blend((original >> 16) & 0xFF, (int) (((state.fogCool()[0] + state.fogWarm()[0]) * 0.5F) * 255.0F), mix);
        int g = blend((original >> 8) & 0xFF, (int) (((state.fogCool()[1] + state.fogWarm()[1]) * 0.5F) * 255.0F), mix);
        int b = blend(original & 0xFF, (int) (((state.fogCool()[2] + state.fogWarm()[2]) * 0.5F) * 255.0F), mix);
        cir.setReturnValue((original & 0xFF000000) | (r << 16) | (g << 8) | b);
    }

    @Inject(require = 0, method = "applyStartEndModifier", at = @At("RETURN"))
    private void pulse$applyHaze(FogData fogData, Camera camera, ClientWorld world, float viewDistance, RenderTickCounter tickCounter, CallbackInfo callbackInfo) {
        AtmosphereSettings settings = PulseConfig.get().atmosphere;
        if (!settings.enabled || !settings.isMood()) {
            return;
        }

        // Дымка «настроения» (dust/snow/embers): уменьшаем дальность тумана
        float haze = AtmosphereSettings.clamp(settings.haze / 100.0F, 0.0F, 1.0F);
        float end = Math.max(24.0F, viewDistance * 16.0F * (1.0F - 0.6F * haze));
        float start = Math.min(2.0F, end * 0.25F);
        fogData.environmentalStart = Math.min(fogData.environmentalStart, start);
        fogData.environmentalEnd = Math.min(fogData.environmentalEnd, end);
        fogData.skyEnd = Math.min(fogData.skyEnd, end);
        fogData.cloudEnd = Math.min(fogData.cloudEnd, end);
    }

    private static int blend(int from, int to, float weight) {
        return Math.round(from + (to - from) * weight) & 0xFF;
    }
}
