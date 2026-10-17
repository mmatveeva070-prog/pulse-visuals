package ru.pulse.emote;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import ru.pulse.cosmetic.PlayerRenderTracker;

/**
 * Клиентские визуальные эмоуты локального игрока (wave / dance).
 * Никакого влияния на геймплей — только позы модели в рендере.
 */
public final class EmoteManager {
    public static final String EMOTE_WAVE = "wave";
    public static final String EMOTE_DANCE = "dance";

    private static final long WAVE_DURATION_MS = 2000L;
    private static final long DANCE_DURATION_MS = 3000L;

    private static String activeEmote;
    private static long startTime;
    private static PlayerEntityRenderState activeState;

    private EmoteManager() {
    }

    /** Клавиша B: none → wave → dance → none. */
    public static void nextEmote() {
        if (activeEmote == null) {
            start(EMOTE_WAVE);
        } else if (EMOTE_WAVE.equals(activeEmote)) {
            start(EMOTE_DANCE);
        } else {
            stop();
        }
    }

    public static void start(String emote) {
        activeEmote = emote;
        startTime = System.currentTimeMillis();
    }

    public static void stop() {
        activeEmote = null;
        activeState = null;
    }

    public static String activeEmote() {
        return activeEmote;
    }

    /** Вызывается из клиентского тика — завершает эмоут по таймеру. */
    public static void tick() {
        if (activeEmote == null) {
            return;
        }
        long duration = EMOTE_WAVE.equals(activeEmote) ? WAVE_DURATION_MS : DANCE_DURATION_MS;
        if (System.currentTimeMillis() - startTime > duration) {
            stop();
        }
    }

    /** Запоминает state локального игрока (из PlayerEntityRendererMixin). */
    public static void trackLocalState(AbstractClientPlayerEntity player, PlayerEntityRenderState state) {
        if (activeEmote != null && player != null
            && isLocalPlayer(player) && state != null) {
            activeState = state;
        }
    }

    private static boolean isLocalPlayer(AbstractClientPlayerEntity player) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        return client.player != null && client.player.getId() == player.getId();
    }

    /**
     * Применяет позу эмоута (вызывается миксином в PlayerEntityModel#setAngles
     * в конце — поверх ванильных углов).
     */
    public static void applyEmote(PlayerEntityModel model, PlayerEntityRenderState state) {
        if (activeEmote == null || state == null || state != activeState) {
            return;
        }
        float t = (System.currentTimeMillis() - startTime) / 1000.0F;

        if (EMOTE_WAVE.equals(activeEmote)) {
            // Правая рука поднята и машет
            float fade = fadeInOut(t, WAVE_DURATION_MS / 1000.0F);
            model.rightArm.pitch = (-2.6F + (float) Math.sin(t * 9.0F) * 0.35F) * fade + model.rightArm.pitch * (1.0F - fade);
            model.rightArm.yaw = -0.35F * fade + model.rightArm.yaw * (1.0F - fade);
            model.rightArm.roll = 0.25F * fade + model.rightArm.roll * (1.0F - fade);
            model.head.roll = (float) Math.sin(t * 4.5F) * 0.06F * fade;
        } else if (EMOTE_DANCE.equals(activeEmote)) {
            // Танец: руки вверх-вниз попеременно + лёгкий наклон
            float fade = fadeInOut(t, DANCE_DURATION_MS / 1000.0F);
            float phase = (float) Math.sin(t * 6.0F);
            float phase2 = (float) Math.cos(t * 6.0F);
            model.rightArm.pitch = (-2.2F + phase * 0.6F) * fade + model.rightArm.pitch * (1.0F - fade);
            model.leftArm.pitch = (-2.2F - phase * 0.6F) * fade + model.leftArm.pitch * (1.0F - fade);
            model.rightArm.yaw = -0.5F * fade;
            model.leftArm.yaw = 0.5F * fade;
            model.body.roll = phase2 * 0.10F * fade;
            model.head.roll = phase2 * 0.12F * fade;
            model.rightLeg.roll = phase * 0.12F * fade + model.rightLeg.roll * (1.0F - fade);
            model.leftLeg.roll = -phase * 0.12F * fade + model.leftLeg.roll * (1.0F - fade);
        }
    }

    /** Плавное появление/затухание позы на краях эмоута. */
    private static float fadeInOut(float t, float duration) {
        float edge = Math.min(Math.min(t, duration - t) / 0.25F, 1.0F);
        return Math.max(0.0F, Math.min(edge, 1.0F));
    }
}
