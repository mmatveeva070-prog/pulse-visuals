package ru.pulse.cosmetic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import ru.pulse.config.PulseConfig;
import ru.pulse.cosmetic.geckolib.GeckolibCosmeticRenderer;
import ru.pulse.cosmetic.model.CosmeticModel;
import ru.pulse.cosmetic.render.CosmeticRenderer;

import java.util.List;

/**
 * Фича-рендерер косметики игрока (адаптация pulse.cosmetic.CosmeticFeatureRenderer
 * под 1.21.11): для каждого экипированного предмета вызывает
 * CosmeticRenderer#renderCosmetic с учётом showOnSelf/showOnOthers/showCapes.
 */
public class CosmeticFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public CosmeticFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float tickDelta) {
        AbstractClientPlayerEntity player = PlayerRenderTracker.playerOf(state);
        if (player == null || state == null || player.isSpectator() || player.isInvisible()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        boolean isSelf = client.player != null && client.player.getId() == player.getId();

        PulseConfig.CosmeticsSettings settings = PulseConfig.get().cosmetics;
        if (isSelf && !settings.showOnSelf) {
            return;
        }
        if (!isSelf && !settings.showOnOthers) {
            return;
        }

        List<CosmeticModel> selected = CosmeticCatalog.selectedModels();
        if (selected.isEmpty()) {
            return;
        }

        for (CosmeticModel model : selected) {
            if (model == null || model.getTextureId() == null) {
                continue;
            }

            CosmeticCatalog.Entry entry = CosmeticCatalog.byResourceIndex(model.getId());
            String type = entry == null ? "" : entry.type;
            if ("cape".equals(type) && !settings.showCapes) {
                continue;
            }

            GeckolibCosmeticRenderer.IdleAnimation idle = idleFor(type);
            // 1.21.11: геометрия рисуется через очередь команд — submitCustom
            var layer = RenderLayers.entityCutoutNoCull(model.getTextureId());
            queue.submitCustom(matrices, layer, (base, consumer) ->
                renderOne(model, player, base, consumer, light, idle));
        }
    }

    /**
     * Рендер одной косметики внутри submitCustom: локальный стек позиционирования
     * строится поверх базовой entry очереди команд.
     */
    private void renderOne(CosmeticModel model, AbstractClientPlayerEntity player, MatrixStack.Entry base,
                           VertexConsumer consumer, int light, GeckolibCosmeticRenderer.IdleAnimation idle) {
        MatrixStack stack = new MatrixStack();
        stack.peek().multiplyPositionMatrix(base.getPositionMatrix());
        stack.peek().getNormalMatrix().set(base.getNormalMatrix());
        CosmeticRenderer.getInstance().renderCosmetic(model, player, stack, consumer,
            light, this.getContextModel(), idle);
    }

    /** Idle-анимации: крылья/плащи мягко покачиваются, питомцы — bob. */
    private static GeckolibCosmeticRenderer.IdleAnimation idleFor(String type) {
        return switch (type) {
            case "wings", "cape" -> GeckolibCosmeticRenderer.IdleAnimation.SWAY;
            case "pet" -> GeckolibCosmeticRenderer.IdleAnimation.BOB;
            default -> GeckolibCosmeticRenderer.IdleAnimation.NONE;
        };
    }
}
