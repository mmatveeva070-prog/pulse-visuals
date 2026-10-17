package ru.pulse.cosmetic.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import ru.pulse.cosmetic.geckolib.GeckolibCosmeticRenderer;
import ru.pulse.cosmetic.model.CosmeticModel;
import ru.pulse.cosmetic.model.ModelPosition;

/**
 * Рендер одной косметики: позиционирование относительно части модели игрока
 * (порт CosmeticRenderer из бандля; 1.21.11 — рендер через VertexConsumer
 * из submitCustom-очереди команд).
 */
public class CosmeticRenderer {
    private static CosmeticRenderer instance;
    private final GeckolibCosmeticRenderer geckolibRenderer = GeckolibCosmeticRenderer.getInstance();
    private final RenderStack stack = new RenderStack();
    private static final float RAD_TO_DEG = 180.0F / (float) Math.PI;

    public static CosmeticRenderer getInstance() {
        if (instance == null) {
            instance = new CosmeticRenderer();
        }

        return instance;
    }

    public void renderCosmetic(CosmeticModel cosmetic, AbstractClientPlayerEntity player, MatrixStack matrices,
                               VertexConsumer buffer, int light, PlayerEntityModel playerModel,
                               GeckolibCosmeticRenderer.IdleAnimation idle) {
        if (cosmetic != null && cosmetic.getTextureId() != null) {
            this.stack.update(matrices);
            this.stack.push();
            float yOffset = this.transformToPosition(cosmetic, playerModel);
            this.stack.rotateZDegrees(180.0F);
            this.stack.translate(cosmetic.getX(), cosmetic.getY() + yOffset, cosmetic.getZ());
            this.stack.rotateYDegrees(cosmetic.getYaw());
            this.stack.rotateXDegrees(cosmetic.getPitch());
            this.stack.rotateZDegrees(cosmetic.getRoll());
            this.stack.scale(cosmetic.getScale(), cosmetic.getScale(), cosmetic.getScale());
            this.geckolibRenderer.renderCosmetic(cosmetic, matrices, buffer, light, idle);
            this.stack.pop();
        }
    }

    private float transformToPosition(CosmeticModel cosmetic, PlayerEntityModel playerModel) {
        float yOffset = 0.0F;
        ModelPosition position = cosmetic.getPosition();
        if (playerModel == null) {
            return yOffset;
        }

        switch (position) {
            case HEAD:
                this.transformToModelPart(playerModel.head);
                yOffset = 0.5F;
                break;
            case ABOVE_HEAD:
                yOffset = 0.75F;
                break;
            case BODY:
                this.transformToModelPart(playerModel.body);
                yOffset = -0.3F;
                break;
            case RIGHT_ARM:
                this.transformToModelPart(playerModel.rightArm);
                yOffset = -0.25F;
                break;
            case LEFT_ARM:
                this.transformToModelPart(playerModel.leftArm);
                yOffset = -0.25F;
                break;
            case RIGHT_LEG:
                this.transformToModelPart(playerModel.rightLeg);
                yOffset = -0.35F;
                break;
            case LEFT_LEG:
                this.transformToModelPart(playerModel.leftLeg);
                yOffset = -0.35F;
                break;
            case FREE:
            default:
                break;
        }

        return yOffset;
    }

    private void transformToModelPart(ModelPart part) {
        this.stack.translate(part.originX * 0.0625F, part.originY * 0.0625F, part.originZ * 0.0625F);
        this.stack.rotateDegrees(
            part.pitch * RAD_TO_DEG,
            part.yaw * RAD_TO_DEG,
            part.roll * RAD_TO_DEG);
    }
}
