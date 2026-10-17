package ru.pulse.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.pulse.atmosphere.AtmospherePostPipeline;
import ru.pulse.config.PulseConfig;
import ru.pulse.wetworld.WetWorldPipeline;

/**
 * Хук атмосферы: в конце WorldRenderer#render, когда фрейм-граф уже выполнился,
 * а глубина мира ещё не стёрта. Точка выбрана осознанно:
 * GameRenderer.renderWorld сразу после рендера мира переключает проекцию на HUD
 * и стирает глубину (clearDepthTexture перед рукой), поэтому хук на его TAIL
 * видел бы пустую глубину и чужую проекцию.
 *
 * Матрицы берутся из аргументов вызова — это ровно те матрицы, которыми
 * растеризовался мир: проекция с FOV-эффектами (спринт, скорость, подзорная труба),
 * view bobbing и hurt tilt уже запечены в неё, а view — только вращение камеры
 * (вершины сабмитятся относительно камеры). Собирать их вручную нельзя:
 * получится рассинхрон с реальным кадром — лучи не от солнца, туман «плывёт».
 *
 * Направление солнца — ванильное, та же формула, что в SkyRendering:
 * (-sin(sunAngle), cos(sunAngle), 0) в мировых координатах.
 *
 * require=0 — точка хука может сместиться в будущих версиях без падения игры.
 */
@Mixin(WorldRenderer.class)
public abstract class AtmosphereRenderMixin {

    @Inject(require = 0,
        method = "render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
        at = @At("TAIL"))
    private void pulse$renderAtmosphere(ObjectAllocator allocator, RenderTickCounter tickCounter,
                                        boolean renderBlockOutline, Camera camera,
                                        Matrix4f viewMatrix, Matrix4f projectionWithEffects, Matrix4f projectionMatrix,
                                        GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
                                        CallbackInfo ci) {
        PulseConfig config = PulseConfig.get();
        if (!config.atmosphere.enabled && !config.wetWorld.enabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.getFramebuffer() == null) {
            return;
        }
        if (camera == null || !camera.isReady()) {
            return;
        }

        float tickDelta = tickCounter.getTickProgress(false);

        // Wet world идёт первым: туман и тонировка атмосферы ложатся поверх отражений.
        if (config.wetWorld.enabled) {
            WetWorldPipeline.getInstance().render(camera, viewMatrix, projectionWithEffects, tickDelta);
        }
        if (!config.atmosphere.enabled) {
            return;
        }

        float sunAngle = camera.getEnvironmentAttributeInterpolator()
            .get(EnvironmentAttributes.SUN_ANGLE_VISUAL, tickDelta) * (float) (Math.PI / 180.0);
        float[] sunDirection = new float[]{
            -(float) Math.sin(sunAngle), (float) Math.cos(sunAngle), 0.0f};

        AtmospherePostPipeline.getInstance().render(camera, viewMatrix, projectionWithEffects, sunDirection);
    }
}
