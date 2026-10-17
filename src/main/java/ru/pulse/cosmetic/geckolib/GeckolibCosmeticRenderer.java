package ru.pulse.cosmetic.geckolib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.pulse.Pulse;
import ru.pulse.cosmetic.geo.GeoBone;
import ru.pulse.cosmetic.geo.GeoCube;
import ru.pulse.cosmetic.geo.GeoModel;
import ru.pulse.cosmetic.geo.GeoQuad;
import ru.pulse.cosmetic.geo.GeoVertex;
import ru.pulse.cosmetic.model.CosmeticModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Рендер косметики: geo-модель + текстура, кэш моделей/анимаций,
 * поддержка keyframe-анимаций из JSON и idle-анимаций (sway/bob).
 * Порт GeckolibCosmeticRenderer из бандля; RenderSystemHelper заменён
 * на штатный RenderLayer#getEntityCutoutNoCull (он сам выключает culling).
 */
public class GeckolibCosmeticRenderer {
    /** Idle-анимация для косметики без явной анимации в JSON. */
    public enum IdleAnimation {
        NONE, SWAY, BOB
    }

    private static GeckolibCosmeticRenderer instance;
    private final Map<Integer, GeoModel> modelCache = new ConcurrentHashMap<>();
    private final Map<Integer, CosmeticAnimationData> animationCache = new ConcurrentHashMap<>();
    private final Set<Integer> noAnimationSet = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Long> animationStartTime = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, float[]>> initialBoneTransforms = new ConcurrentHashMap<>();
    private final GeckolibModelParser modelParser = new GeckolibModelParser();

    public static GeckolibCosmeticRenderer getInstance() {
        if (instance == null) {
            instance = new GeckolibCosmeticRenderer();
        }

        return instance;
    }

    public void renderCosmetic(CosmeticModel cosmetic, MatrixStack matrices, VertexConsumer buffer, int light) {
        renderCosmetic(cosmetic, matrices, buffer, light, IdleAnimation.NONE);
    }

    /**
     * Рендер косметики с опциональной idle-анимацией (sway для крыльев/плащей, bob для питомцев).
     * 1.21.11: буфер приходит из submitCustom-очереди команд рендера (слой задаётся снаружи).
     */
    public void renderCosmetic(CosmeticModel cosmetic, MatrixStack matrices, VertexConsumer buffer, int light, IdleAnimation idle) {
        if (cosmetic != null && cosmetic.getTextureId() != null) {
            GeoModel geoModel = this.getOrParseModel(cosmetic);
            if (geoModel != null) {
                CosmeticAnimationData animationData = this.getOrParseAnimation(cosmetic);
                if (animationData != null) {
                    this.applyAnimations(geoModel, animationData, cosmetic.getId());
                } else {
                    this.applyIdleAnimation(geoModel, cosmetic.getId(), idle);
                }

                for (GeoBone bone : geoModel.topLevelBones) {
                    this.renderBone(bone, matrices, buffer, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
                }
            }
        }
    }

    /** Лёгкое покачивание (sway) или bob по времени для косметики без своей анимации. */
    private void applyIdleAnimation(GeoModel model, int cosmeticId, IdleAnimation idle) {
        if (idle == IdleAnimation.NONE) {
            return;
        }
        Map<String, float[]> initial = this.initialBoneTransforms.get(cosmeticId);
        if (initial == null) {
            return;
        }
        long start = this.animationStartTime.computeIfAbsent(cosmeticId, key -> System.currentTimeMillis());
        float time = (float) ((System.currentTimeMillis() - start) % 100000L) / 1000.0F;

        for (GeoBone bone : model.topLevelBones) {
            this.resetBoneRecursive(bone, initial);
        }
        for (GeoBone bone : model.topLevelBones) {
            float[] base = initial.get(bone.name);
            if (base == null) {
                continue;
            }
            if (idle == IdleAnimation.SWAY) {
                // Небольшое дыхание крыльев/плаща: вращение вокруг Z и подъём по Y
                float sway = (float) Math.sin(time * 1.6F) * 0.045F;
                bone.setRotationZ(base[2] + sway);
                bone.setRotationY(base[1] + (float) Math.sin(time * 0.8F) * 0.03F);
                bone.setPositionY(base[4] + (float) Math.sin(time * 1.6F) * 0.12F);
            } else {
                // Bob питомца: плавное вертикальное покачивание
                float bob = (float) Math.sin(time * 2.2F);
                bone.setPositionY(base[4] + bob * 0.35F);
                bone.setRotationZ(base[2] + bob * 0.05F);
            }
        }
    }

    private GeoModel getOrParseModel(CosmeticModel cosmetic) {
        int id = cosmetic.getId();
        if (this.modelCache.containsKey(id)) {
            return this.modelCache.get(id);
        }

        GeoModel model = this.modelParser.parseModel(cosmetic);
        if (model != null) {
            this.modelCache.put(id, model);
            this.saveInitialBoneTransforms(id, model);
            Pulse.LOGGER.info("Cached model for cosmetic: " + cosmetic.getName());
        }

        return model;
    }

    private void saveInitialBoneTransforms(int cosmeticId, GeoModel model) {
        HashMap<String, float[]> transforms = new HashMap<>();

        for (GeoBone bone : model.topLevelBones) {
            this.saveBonesRecursive(bone, transforms);
        }

        this.initialBoneTransforms.put(cosmeticId, transforms);
    }

    private void saveBonesRecursive(GeoBone bone, Map<String, float[]> transforms) {
        transforms.put(
            bone.name,
            new float[]{
                bone.getRotationX(),
                bone.getRotationY(),
                bone.getRotationZ(),
                bone.getPositionX(),
                bone.getPositionY(),
                bone.getPositionZ(),
                bone.getScaleX(),
                bone.getScaleY(),
                bone.getScaleZ()
            }
        );

        for (GeoBone child : bone.childBones) {
            this.saveBonesRecursive(child, transforms);
        }
    }

    private void renderBone(GeoBone bone, MatrixStack matrices, VertexConsumer buffer, int light, int overlay, float red, float green, float blue, float alpha) {
        if (!bone.isHidden) {
            matrices.push();
            GeckoRenderHelper.translate(bone, matrices);
            GeckoRenderHelper.moveToPivot(bone, matrices);
            GeckoRenderHelper.rotate(bone, matrices);
            GeckoRenderHelper.scale(bone, matrices);
            GeckoRenderHelper.moveBackFromPivot(bone, matrices);

            for (GeoCube cube : bone.childCubes) {
                this.renderCube(cube, matrices, buffer, light, overlay, red, green, blue, alpha);
            }

            for (GeoBone child : bone.childBones) {
                this.renderBone(child, matrices, buffer, light, overlay, red, green, blue, alpha);
            }

            matrices.pop();
        }
    }

    private void renderCube(GeoCube cube, MatrixStack matrices, VertexConsumer buffer, int light, int overlay, float red, float green, float blue, float alpha) {
        matrices.push();
        GeckoRenderHelper.moveToPivot(cube, matrices);
        GeckoRenderHelper.rotate(cube, matrices);
        GeckoRenderHelper.moveBackFromPivot(cube, matrices);
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

        for (GeoQuad quad : cube.quads) {
            if (quad != null) {
                Vector3f transformedNormal = new Vector3f(quad.normal.getX(), quad.normal.getY(), quad.normal.getZ());
                normalMatrix.transform(transformedNormal);
                float nx = transformedNormal.x();
                float ny = transformedNormal.y();
                float nz = transformedNormal.z();
                // Плоские кубы: нормаль всегда «лицом» к камере
                if ((cube.size.getY() == 0.0F || cube.size.getZ() == 0.0F) && nx < 0.0F) {
                    nx = -nx;
                }

                if ((cube.size.getX() == 0.0F || cube.size.getZ() == 0.0F) && ny < 0.0F) {
                    ny = -ny;
                }

                if ((cube.size.getX() == 0.0F || cube.size.getY() == 0.0F) && nz < 0.0F) {
                    nz = -nz;
                }

                for (GeoVertex vertex : quad.vertices) {
                    buffer.vertex(positionMatrix, vertex.position.getX(), vertex.position.getY(), vertex.position.getZ())
                        .color(red, green, blue, alpha)
                        .texture(vertex.textureU, vertex.textureV)
                        .overlay(overlay)
                        .light(light)
                        .normal(nx, ny, nz);
                }
            }
        }

        matrices.pop();
    }

    private CosmeticAnimationData getOrParseAnimation(CosmeticModel cosmetic) {
        int id = cosmetic.getId();
        if (this.animationCache.containsKey(id)) {
            return this.animationCache.get(id);
        }

        if (this.noAnimationSet.contains(id)) {
            return null;
        }

        JsonObject animationJson = cosmetic.getAnimationJson();
        if (animationJson == null) {
            this.noAnimationSet.add(id);
            return null;
        }

        try {
            CosmeticAnimationData animationData = this.parseAnimationData(animationJson);
            if (animationData != null) {
                this.animationCache.put(id, animationData);
                Pulse.LOGGER.info("Cached animation for cosmetic: " + cosmetic.getName());
            } else {
                this.noAnimationSet.add(id);
            }

            return animationData;
        } catch (Exception error) {
            Pulse.LOGGER.error("Failed to parse animation for: " + cosmetic.getName(), error);
            this.noAnimationSet.add(id);
            return null;
        }
    }

    private CosmeticAnimationData parseAnimationData(JsonObject animationJson) {
        CosmeticAnimationData animationData = new CosmeticAnimationData();
        if (!animationJson.has("animations")) {
            return null;
        }

        JsonObject animations = animationJson.getAsJsonObject("animations");
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            String animationName = entry.getKey();
            JsonObject animationJsonEntry = entry.getValue().getAsJsonObject();
            animationData.animationName = animationName;
            animationData.loop = animationJsonEntry.has("loop") && animationJsonEntry.get("loop").getAsBoolean();
            animationData.length = animationJsonEntry.has("animation_length") ? animationJsonEntry.get("animation_length").getAsFloat() : 1.0F;
            if (animationJsonEntry.has("bones")) {
                JsonObject bones = animationJsonEntry.getAsJsonObject("bones");

                for (Map.Entry<String, JsonElement> boneEntry : bones.entrySet()) {
                    String boneName = boneEntry.getKey();
                    JsonObject boneJson = boneEntry.getValue().getAsJsonObject();
                    BoneAnimationData boneAnimation = new BoneAnimationData();
                    if (boneJson.has("rotation")) {
                        boneAnimation.rotationKeyframes = this.parseKeyframes(boneJson.get("rotation"));
                    }

                    if (boneJson.has("position")) {
                        boneAnimation.positionKeyframes = this.parseKeyframes(boneJson.get("position"));
                    }

                    if (boneJson.has("scale")) {
                        boneAnimation.scaleKeyframes = this.parseKeyframes(boneJson.get("scale"));
                    }

                    animationData.boneAnimations.put(boneName, boneAnimation);
                }
            }
            // Используем первую объявленную анимацию
            break;
        }

        return animationData;
    }

    private Map<Float, float[]> parseKeyframes(JsonElement jsonElement) {
        HashMap<Float, float[]> keyframes = new HashMap<>();
        if (jsonElement.isJsonObject()) {
            JsonObject keyframeObject = jsonElement.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : keyframeObject.entrySet()) {
                try {
                    float time = Float.parseFloat(entry.getKey());
                    JsonElement keyframeJson = entry.getValue();
                    float[] values = new float[3];
                    if (keyframeJson.isJsonObject()) {
                        JsonObject keyframe = keyframeJson.getAsJsonObject();
                        if (keyframe.has("vector")) {
                            JsonArray vector = keyframe.getAsJsonArray("vector");
                            values[0] = vector.get(0).getAsFloat();
                            values[1] = vector.get(1).getAsFloat();
                            values[2] = vector.get(2).getAsFloat();
                        }
                    } else if (keyframeJson.isJsonArray()) {
                        JsonArray vector = keyframeJson.getAsJsonArray();
                        values[0] = vector.get(0).getAsFloat();
                        values[1] = vector.get(1).getAsFloat();
                        values[2] = vector.get(2).getAsFloat();
                    }

                    keyframes.put(time, values);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return keyframes;
    }

    private void applyAnimations(GeoModel model, CosmeticAnimationData animationData, int cosmeticId) {
        Map<String, float[]> initialTransforms = this.initialBoneTransforms.get(cosmeticId);
        if (initialTransforms != null) {
            long start = this.animationStartTime.computeIfAbsent(cosmeticId, key -> System.currentTimeMillis());
            float elapsed = (float) (System.currentTimeMillis() - start) / 1000.0F;
            float animationTime;
            if (animationData.loop && animationData.length > 0.0F) {
                animationTime = elapsed % animationData.length;
            } else {
                animationTime = Math.min(elapsed, animationData.length);
            }

            for (GeoBone bone : model.topLevelBones) {
                this.resetBoneRecursive(bone, initialTransforms);
            }

            for (Map.Entry<String, BoneAnimationData> entry : animationData.boneAnimations.entrySet()) {
                String boneName = entry.getKey();
                BoneAnimationData boneAnimation = entry.getValue();
                GeoBone bone = this.findBone(model, boneName);
                if (bone != null) {
                    float[] base = initialTransforms.get(boneName);
                    if (base == null) {
                        base = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
                    }

                    if (!boneAnimation.rotationKeyframes.isEmpty()) {
                        float[] rotation = this.interpolateKeyframes(boneAnimation.rotationKeyframes, animationTime);
                        bone.setRotationX(base[0] + (float) Math.toRadians(-rotation[0]));
                        bone.setRotationY(base[1] + (float) Math.toRadians(-rotation[1]));
                        bone.setRotationZ(base[2] + (float) Math.toRadians(rotation[2]));
                    }

                    if (!boneAnimation.positionKeyframes.isEmpty()) {
                        float[] position = this.interpolateKeyframes(boneAnimation.positionKeyframes, animationTime);
                        bone.setPositionX(base[3] + position[0]);
                        bone.setPositionY(base[4] + position[1]);
                        bone.setPositionZ(base[5] + position[2]);
                    }

                    if (!boneAnimation.scaleKeyframes.isEmpty()) {
                        float[] scale = this.interpolateKeyframes(boneAnimation.scaleKeyframes, animationTime);
                        bone.setScaleX(base[6] * scale[0]);
                        bone.setScaleY(base[7] * scale[1]);
                        bone.setScaleZ(base[8] * scale[2]);
                    }
                }
            }
        }
    }

    private void resetBoneRecursive(GeoBone bone, Map<String, float[]> initialTransforms) {
        float[] base = initialTransforms.get(bone.name);
        if (base != null) {
            bone.setRotationX(base[0]);
            bone.setRotationY(base[1]);
            bone.setRotationZ(base[2]);
            bone.setPositionX(base[3]);
            bone.setPositionY(base[4]);
            bone.setPositionZ(base[5]);
            bone.setScaleX(base[6]);
            bone.setScaleY(base[7]);
            bone.setScaleZ(base[8]);
        }

        for (GeoBone child : bone.childBones) {
            this.resetBoneRecursive(child, initialTransforms);
        }
    }

    private GeoBone findBone(GeoModel model, String boneName) {
        for (GeoBone bone : model.topLevelBones) {
            GeoBone found = this.findBoneRecursive(bone, boneName);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private GeoBone findBoneRecursive(GeoBone bone, String boneName) {
        if (bone.name.equals(boneName)) {
            return bone;
        }

        for (GeoBone child : bone.childBones) {
            GeoBone found = this.findBoneRecursive(child, boneName);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private float[] interpolateKeyframes(Map<Float, float[]> keyframes, float animationTime) {
        if (keyframes.isEmpty()) {
            return new float[]{0.0F, 0.0F, 0.0F};
        }

        Float beforeTime = null;
        Float afterTime = null;
        float[] before = null;
        float[] after = null;

        for (Map.Entry<Float, float[]> entry : keyframes.entrySet()) {
            float time = entry.getKey();
            if (time <= animationTime && (beforeTime == null || time > beforeTime)) {
                beforeTime = time;
                before = entry.getValue();
            }

            if (time >= animationTime && (afterTime == null || time < afterTime)) {
                afterTime = time;
                after = entry.getValue();
            }
        }

        if (before == null && after == null) {
            return new float[]{0.0F, 0.0F, 0.0F};
        }

        if (before == null) {
            return after;
        }

        if (after == null) {
            return before;
        }

        if (beforeTime.equals(afterTime)) {
            return before;
        }

        float progress = (animationTime - beforeTime) / (afterTime - beforeTime);
        return new float[]{before[0] + progress * (after[0] - before[0]), before[1] + progress * (after[1] - before[1]), before[2] + progress * (after[2] - before[2])};
    }

    private static class BoneAnimationData {
        Map<Float, float[]> rotationKeyframes = new HashMap<>();
        Map<Float, float[]> positionKeyframes = new HashMap<>();
        Map<Float, float[]> scaleKeyframes = new HashMap<>();
    }

    private static class CosmeticAnimationData {
        String animationName;
        boolean loop;
        float length;
        Map<String, BoneAnimationData> boneAnimations = new HashMap<>();
    }
}
