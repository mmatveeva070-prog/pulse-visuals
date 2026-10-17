package ru.pulse.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.pulse.cosmetic.PlayerRenderTracker;
import ru.pulse.emote.EmoteManager;

/**
 * Запоминает соответствие PlayerEntityRenderState ↔ игрок для
 * фича-рендера косметики и эмоутов (state не хранит ссылку на сущность).
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(require = 0, method = "updateRenderState", at = @At("TAIL"))
    private void pulse$trackPlayer(PlayerEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo callbackInfo) {
        if (player instanceof AbstractClientPlayerEntity clientPlayer) {
            PlayerRenderTracker.track(state, clientPlayer);
            EmoteManager.trackLocalState(clientPlayer, state);
        }
    }
}
