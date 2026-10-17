package ru.pulse.cosmetic.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.pulse.Pulse;
import ru.pulse.cosmetic.model.CosmeticModel;
import ru.pulse.cosmetic.model.ModelPosition;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Загрузчик косметики из JSON (порт из бандля): парсинг модели, позиции,
 * оффсетов, base64-текстур с регистрацией через TextureManager.
 */
public class CosmeticLoader {
    private static CosmeticLoader instance;
    private final Map<Integer, CosmeticModel> loadedCosmetics = new ConcurrentHashMap<>();
    private final Map<String, Identifier> textureCache = new ConcurrentHashMap<>();

    public static CosmeticLoader getInstance() {
        if (instance == null) {
            instance = new CosmeticLoader();
        }

        return instance;
    }

    public CosmeticModel loadFromJson(String json) {
        return this.loadFromJson(json, null, -1);
    }

    public CosmeticModel loadFromJson(String json, Identifier textureId) {
        return this.loadFromJson(json, textureId, -1);
    }

    public CosmeticModel loadFromJson(String json, Identifier textureId, int forcedId) {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            return this.loadFromJson(root, textureId, forcedId);
        } catch (Exception error) {
            Pulse.LOGGER.error("Error loading cosmetic from JSON string", error);
            return null;
        }
    }

    public CosmeticModel loadFromJson(JsonObject root) {
        return this.loadFromJson(root, null, -1);
    }

    public CosmeticModel loadFromJson(JsonObject root, Identifier textureId, int forcedId) {
        try {
            if (root.has("name") && root.has("model")) {
                String name = root.get("name").getAsString();
                int id = forcedId > 0 ? forcedId : (root.has("id") ? root.get("id").getAsInt() : name.hashCode());
                int category = root.has("category") ? root.get("category").getAsInt() : 1;
                CosmeticModel cosmetic = new CosmeticModel(name, id, category);
                JsonObject modelJson = root.getAsJsonObject("model");
                cosmetic.setRawModelJson(modelJson.toString());
                if (textureId != null) {
                    cosmetic.setTextureId(textureId);
                } else if (root.has("texture")) {
                    String textureBase64 = root.get("texture").getAsString();
                    Identifier loadedTextureId = this.loadTextureFromBase64(name, id, textureBase64);
                    cosmetic.setTextureId(loadedTextureId);
                }

                this.parseModelPosition(root, cosmetic);
                if (root.has("height")) {
                    cosmetic.setHeight(root.get("height").getAsFloat());
                }

                if (root.has("scale")) {
                    cosmetic.setScale(root.get("scale").getAsFloat());
                }

                if (root.has("previewScale")) {
                    cosmetic.setPreviewScale(root.get("previewScale").getAsFloat());
                }

                if (root.has("previewY")) {
                    cosmetic.setPreviewY(root.get("previewY").getAsFloat());
                }

                if (root.has("animation")) {
                    cosmetic.setAnimationJson(root.getAsJsonObject("animation"));
                }

                this.loadedCosmetics.put(id, cosmetic);
                Pulse.LOGGER.info("Loaded cosmetic: " + name + " (id=" + id + "), texture=" + cosmetic.getTextureId());
                return cosmetic;
            } else {
                Pulse.LOGGER.error("Invalid cosmetic JSON: missing name or model");
                return null;
            }
        } catch (Exception error) {
            Pulse.LOGGER.error("Error loading cosmetic", error);
            return null;
        }
    }

    private void parseModelPosition(JsonObject root, CosmeticModel cosmetic) {
        if (root.has("pos")) {
            cosmetic.setPosition(ModelPosition.getById(root.get("pos").getAsInt()));
        }

        if (root.has("scale")) {
            cosmetic.setScale(root.get("scale").getAsFloat());
        }

        if (root.has("x")) {
            cosmetic.setX(root.get("x").getAsFloat());
        }

        if (root.has("y")) {
            cosmetic.setY(root.get("y").getAsFloat());
        }

        if (root.has("z")) {
            cosmetic.setZ(root.get("z").getAsFloat());
        }

        if (root.has("yaw")) {
            cosmetic.setYaw(root.get("yaw").getAsFloat());
        }

        if (root.has("pitch")) {
            cosmetic.setPitch(root.get("pitch").getAsFloat());
        }

        if (root.has("roll")) {
            cosmetic.setRoll(root.get("roll").getAsFloat());
        }
    }

    private Identifier loadTextureFromBase64(String name, int id, String base64) {
        try {
            String cacheKey = name.replace(" ", "").toLowerCase() + "_" + id;
            if (this.textureCache.containsKey(cacheKey)) {
                return this.textureCache.get(cacheKey);
            }

            byte[] bytes = Base64.getDecoder().decode(base64);
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            NativeImage image = NativeImage.read(stream);
            Identifier textureId = Identifier.of("pulse", "cosmetic/" + cacheKey);
            Runnable register = () -> {
                NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "pulse cosmetic", image);
                MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
                this.textureCache.put(cacheKey, textureId);
                Pulse.LOGGER.info("Registered texture: " + textureId + " (" + image.getWidth() + "x" + image.getHeight() + ")");
            };
            if (MinecraftClient.getInstance().isOnThread()) {
                register.run();
            } else {
                MinecraftClient.getInstance().execute(register);
            }

            this.textureCache.put(cacheKey, textureId);
            return textureId;
        } catch (Exception error) {
            Pulse.LOGGER.error("Failed to load texture for cosmetic: " + name, error);
            return null;
        }
    }

    public CosmeticModel getCosmetic(int id) {
        return this.loadedCosmetics.get(id);
    }

    public Map<Integer, CosmeticModel> getAllCosmetics() {
        return this.loadedCosmetics;
    }

    public void loadFromInputStream(InputStream stream) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];

            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }

            String json = buffer.toString(StandardCharsets.UTF_8.name());
            this.loadFromJson(json);
        } catch (Exception error) {
            Pulse.LOGGER.error("Error loading cosmetic from input stream", error);
        }
    }
}
