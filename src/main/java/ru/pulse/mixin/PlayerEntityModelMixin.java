package ru.pulse.mixin;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.pulse.emote.EmoteManager;

/**
 * Применяет позу эмоута локального игрока поверх ванильных углов модели
 * (только визуально, на клиенте).
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {

    @Inject(require = 0, method = "setAngles", at = @At("TAIL"))
    private void pulse$applyEmote(PlayerEntityRenderState state, CallbackInfo callbackInfo) {
        EmoteManager.applyEmote((PlayerEntityModel) (Object) this, state);
    }
}
