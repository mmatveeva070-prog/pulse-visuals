package ru.pulse.cosmetic;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Связь PlayerEntityRenderState ↔ игрок для фича-рендера косметики и эмоутов.
 * Рендереры в 1.21.11 — по одному на сущность, поэтому state однозначно
 * соответствует игроку; связка заполняется миксином в PlayerEntityRenderer.
 */
public final class PlayerRenderTracker {
    private static final Map<PlayerEntityRenderState, AbstractClientPlayerEntity> OWNERS = new WeakHashMap<>();

    private PlayerRenderTracker() {
    }

    /** Вызывается из PlayerEntityRendererMixin (рендер-поток). */
    public static void track(PlayerEntityRenderState state, AbstractClientPlayerEntity player) {
        if (state != null && player != null) {
            OWNERS.put(state, player);
        }
    }

    /** Игрок, которому принадлежит state (или null). */
    public static AbstractClientPlayerEntity playerOf(PlayerEntityRenderState state) {
        return state == null ? null : OWNERS.get(state);
    }
}
