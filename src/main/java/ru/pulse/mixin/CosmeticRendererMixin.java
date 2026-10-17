package ru.pulse.mixin;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.pulse.cosmetic.CosmeticFeatureRenderer;

/**
 * Порт CosmeticRendererMixin из бандля: добавляет CosmeticFeatureRenderer
 * в каждый PlayerEntityRenderer (рендереры в 1.21.11 создаются по одному
 * на сущность, поэтому хук по конструктору LivingEntityRenderer корректен).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CosmeticRendererMixin {

    @Shadow
    protected abstract boolean addFeature(FeatureRenderer<?, ?> featureRenderer);

    @Inject(require = 0, method = "<init>", at = @At("RETURN"))
    private void onInit(EntityRendererFactory.Context context, EntityModel<?> model, float shadowRadius, CallbackInfo callbackInfo) {
        if ((Object) this instanceof PlayerEntityRenderer) {
            this.addFeature(new CosmeticFeatureRenderer((FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) (Object) this));
        }
    }
}
