package ru.pulse.cosmetic.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.pulse.Pulse;

import java.util.HashMap;

/**
 * Парсер Bedrock-гео-моделей (minecraft:geometry / bones / cubes / uv).
 * Порт из бандля, логика разбора не изменена.
 */
public class GeoModelParser {
    public static GeoModel parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return parseModel(root);
        } catch (Exception error) {
            Pulse.LOGGER.error("Failed to parse Bedrock model", error);
            return null;
        }
    }

    private static GeoModel parseModel(JsonObject root) {
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries != null && !geometries.isEmpty()) {
            JsonObject geometry = geometries.get(0).getAsJsonObject();
            JsonObject description = geometry.getAsJsonObject("description");
            int textureWidth = description.has("texture_width") ? description.get("texture_width").getAsInt() : 64;
            int textureHeight = description.has("texture_height") ? description.get("texture_height").getAsInt() : 64;
            GeoModel model = new GeoModel();
            model.textureWidth = textureWidth;
            model.textureHeight = textureHeight;
            JsonArray bones = geometry.getAsJsonArray("bones");
            if (bones != null) {
                HashMap<String, GeoBone> boneMap = new HashMap<>();

                for (JsonElement element : bones) {
                    JsonObject boneJson = element.getAsJsonObject();
                    GeoBone bone = parseBone(boneJson, textureWidth, textureHeight);
                    boneMap.put(bone.name, bone);
                }

                for (JsonElement element : bones) {
                    JsonObject boneJson = element.getAsJsonObject();
                    String name = boneJson.get("name").getAsString();
                    GeoBone bone = boneMap.get(name);
                    if (boneJson.has("parent")) {
                        String parentName = boneJson.get("parent").getAsString();
                        GeoBone parent = boneMap.get(parentName);
                        if (parent != null) {
                            parent.childBones.add(bone);
                            bone.parent = parent;
                        }
                    } else {
                        model.topLevelBones.add(bone);
                    }
                }
            }

            Pulse.LOGGER.info("Parsed model with {} top-level bones", model.topLevelBones.size());
            return model;
        } else {
            Pulse.LOGGER.error("No minecraft:geometry found in model");
            return null;
        }
    }

    private static GeoBone parseBone(JsonObject boneJson, int textureWidth, int textureHeight) {
        String name = boneJson.get("name").getAsString();
        GeoBone bone = new GeoBone(name);
        if (boneJson.has("pivot")) {
            JsonArray pivot = boneJson.getAsJsonArray("pivot");
            bone.rotationPointX = -pivot.get(0).getAsFloat();
            bone.rotationPointY = pivot.get(1).getAsFloat();
            bone.rotationPointZ = pivot.get(2).getAsFloat();
        }

        if (boneJson.has("rotation")) {
            JsonArray rotation = boneJson.getAsJsonArray("rotation");
            bone.setRotationX((float) Math.toRadians(-rotation.get(0).getAsFloat()));
            bone.setRotationY((float) Math.toRadians(-rotation.get(1).getAsFloat()));
            bone.setRotationZ((float) Math.toRadians(rotation.get(2).getAsFloat()));
        }

        if (boneJson.has("cubes")) {
            for (JsonElement element : boneJson.getAsJsonArray("cubes")) {
                JsonObject cubeJson = element.getAsJsonObject();
                GeoCube cube = parseCube(cubeJson, textureWidth, textureHeight);
                bone.childCubes.add(cube);
            }
        }

        return bone;
    }

    private static GeoCube parseCube(JsonObject cubeJson, int textureWidth, int textureHeight) {
        float[] origin = parseFloatArray(cubeJson, "origin", new float[]{0.0F, 0.0F, 0.0F});
        float[] size = parseFloatArray(cubeJson, "size", new float[]{1.0F, 1.0F, 1.0F});
        float[] pivot = parseFloatArray(cubeJson, "pivot", origin.clone());
        float[] rotation = parseFloatArray(cubeJson, "rotation", new float[]{0.0F, 0.0F, 0.0F});
        float inflate = cubeJson.has("inflate") ? cubeJson.get("inflate").getAsFloat() : 0.0F;
        boolean mirror = cubeJson.has("mirror") && cubeJson.get("mirror").getAsBoolean();
        GeoCube cube = new GeoCube(size[0], size[1], size[2]);
        cube.pivot = new Vec3F(-pivot[0], pivot[1], pivot[2]);
        cube.rotation = new Vec3F((float) Math.toRadians(-rotation[0]), (float) Math.toRadians(-rotation[1]), (float) Math.toRadians(rotation[2]));
        cube.inflate = inflate;
        cube.mirror = mirror;
        buildCubeQuads(cube, origin, size, inflate, mirror, cubeJson, textureWidth, textureHeight);
        return cube;
    }

    private static void buildCubeQuads(GeoCube cube, float[] origin, float[] size, float inflate, boolean mirror, JsonObject cubeJson, int textureWidth, int textureHeight) {
        float ox = origin[0];
        float oy = origin[1];
        float oz = origin[2];
        float sx = size[0];
        float sy = size[1];
        float sz = size[2];
        ox -= inflate;
        oy -= inflate;
        oz -= inflate;
        sx += inflate * 2.0F;
        sy += inflate * 2.0F;
        sz += inflate * 2.0F;
        float uvX = 0.0F;
        float uvY = 0.0F;
        boolean perFace = false;
        JsonObject uvObject = null;
        if (cubeJson.has("uv")) {
            JsonElement uv = cubeJson.get("uv");
            if (uv.isJsonArray()) {
                JsonArray uvArray = uv.getAsJsonArray();
                uvX = uvArray.get(0).getAsFloat();
                uvY = uvArray.get(1).getAsFloat();
            } else if (uv.isJsonObject()) {
                perFace = true;
                uvObject = uv.getAsJsonObject();
            }
        }

        // Bedrock→Java преобразование координат (порт из бандля)
        float minX = -(ox + sx) / 16.0F;
        float minY = oy / 16.0F;
        float minZ = oz / 16.0F;
        float maxX = -ox / 16.0F;
        float maxY = (oy + sy) / 16.0F;
        float maxZ = (oz + sz) / 16.0F;
        if (perFace && uvObject != null) {
            cube.quads[0] = buildQuadPerFace(uvObject, "west", minX, minY, minZ, minX, maxY, maxZ, -1.0F, 0.0F, 0.0F, textureWidth, textureHeight);
            cube.quads[1] = buildQuadPerFace(uvObject, "east", maxX, minY, minZ, maxX, maxY, maxZ, 1.0F, 0.0F, 0.0F, textureWidth, textureHeight);
            cube.quads[2] = buildQuadPerFace(uvObject, "down", minX, minY, minZ, maxX, minY, maxZ, 0.0F, -1.0F, 0.0F, textureWidth, textureHeight);
            cube.quads[3] = buildQuadPerFace(uvObject, "up", minX, maxY, minZ, maxX, maxY, maxZ, 0.0F, 1.0F, 0.0F, textureWidth, textureHeight);
            cube.quads[4] = buildQuadPerFace(uvObject, "north", minX, minY, minZ, maxX, maxY, minZ, 0.0F, 0.0F, -1.0F, textureWidth, textureHeight);
            cube.quads[5] = buildQuadPerFace(uvObject, "south", minX, minY, maxZ, maxX, maxY, maxZ, 0.0F, 0.0F, 1.0F, textureWidth, textureHeight);
        } else {
            float tw = textureWidth;
            float th = textureHeight;
            cube.quads[0] = buildQuadBox(minX, minY, minZ, minX, maxY, maxZ, -1.0F, 0.0F, 0.0F, uvX, uvY, sz, sy, sx, tw, th, "west");
            cube.quads[1] = buildQuadBox(maxX, minY, minZ, maxX, maxY, maxZ, 1.0F, 0.0F, 0.0F, uvX, uvY, sz, sy, sx, tw, th, "east");
            cube.quads[2] = buildQuadBox(minX, minY, minZ, maxX, minY, maxZ, 0.0F, -1.0F, 0.0F, uvX, uvY, sz, sy, sx, tw, th, "down");
            cube.quads[3] = buildQuadBox(minX, maxY, minZ, maxX, maxY, maxZ, 0.0F, 1.0F, 0.0F, uvX, uvY, sz, sy, sx, tw, th, "up");
            cube.quads[4] = buildQuadBox(minX, minY, minZ, maxX, maxY, minZ, 0.0F, 0.0F, -1.0F, uvX, uvY, sz, sy, sx, tw, th, "north");
            cube.quads[5] = buildQuadBox(minX, minY, maxZ, maxX, maxY, maxZ, 0.0F, 0.0F, 1.0F, uvX, uvY, sz, sy, sx, tw, th, "south");
        }
    }

    private static GeoQuad buildQuadPerFace(
        JsonObject uvObject, String face, float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
        float nx, float ny, float nz, int textureWidth, int textureHeight
    ) {
        if (!uvObject.has(face)) {
            return null;
        }

        JsonObject faceJson = uvObject.getAsJsonObject(face);
        JsonArray uv = faceJson.getAsJsonArray("uv");
        JsonArray uvSize = faceJson.getAsJsonArray("uv_size");
        float u = uv.get(0).getAsFloat() / textureWidth;
        float v = uv.get(1).getAsFloat() / textureHeight;
        float uSize = uvSize.get(0).getAsFloat() / textureWidth;
        float vSize = uvSize.get(1).getAsFloat() / textureHeight;
        GeoVertex[] vertices = buildFaceVertices(face, minX, minY, minZ, maxX, maxY, maxZ, u, v, uSize, vSize);
        return new GeoQuad(vertices, nx, ny, nz);
    }

    private static GeoQuad buildQuadBox(
        float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
        float nx, float ny, float nz, float uvX, float uvY, float depth, float height, float width,
        float textureWidth, float textureHeight, String face
    ) {
        float u;
        float v;
        float uSize;
        float vSize;
        switch (face) {
            case "north":
                u = (uvX + width + depth) / textureWidth;
                v = (uvY + height) / textureHeight;
                uSize = depth / textureWidth;
                vSize = height / textureHeight;
                break;
            case "south":
                u = (uvX + width + depth + width) / textureWidth;
                v = (uvY + height) / textureHeight;
                uSize = depth / textureWidth;
                vSize = height / textureHeight;
                break;
            case "east":
                u = uvX / textureWidth;
                v = (uvY + height) / textureHeight;
                uSize = width / textureWidth;
                vSize = height / textureHeight;
                break;
            case "west":
                u = (uvX + width + depth) / textureWidth;
                v = (uvY + height) / textureHeight;
                uSize = width / textureWidth;
                vSize = height / textureHeight;
                break;
            case "up":
                u = (uvX + width) / textureWidth;
                v = uvY / textureHeight;
                uSize = depth / textureWidth;
                vSize = width / textureHeight;
                break;
            case "down":
                u = (uvX + width + depth) / textureWidth;
                v = uvY / textureHeight;
                uSize = depth / textureWidth;
                vSize = width / textureHeight;
                break;
            default:
                u = 0.0F;
                v = 0.0F;
                uSize = 0.0F;
                vSize = 0.0F;
        }

        GeoVertex[] vertices = buildFaceVertices(face, minX, minY, minZ, maxX, maxY, maxZ, u, v, uSize, vSize);
        return new GeoQuad(vertices, nx, ny, nz);
    }

    private static GeoVertex[] buildFaceVertices(
        String face, float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
        float u, float v, float uSize, float vSize
    ) {
        GeoVertex[] vertices = new GeoVertex[4];
        float uEnd = u + uSize;
        float vEnd = v + vSize;
        switch (face) {
            case "north":
                vertices[0] = new GeoVertex(maxX, maxY, minZ, u, v);
                vertices[1] = new GeoVertex(minX, maxY, minZ, uEnd, v);
                vertices[2] = new GeoVertex(minX, minY, minZ, uEnd, vEnd);
                vertices[3] = new GeoVertex(maxX, minY, minZ, u, vEnd);
                break;
            case "south":
                vertices[0] = new GeoVertex(minX, maxY, maxZ, u, v);
                vertices[1] = new GeoVertex(maxX, maxY, maxZ, uEnd, v);
                vertices[2] = new GeoVertex(maxX, minY, maxZ, uEnd, vEnd);
                vertices[3] = new GeoVertex(minX, minY, maxZ, u, vEnd);
                break;
            case "east":
                vertices[0] = new GeoVertex(maxX, maxY, maxZ, u, v);
                vertices[1] = new GeoVertex(maxX, maxY, minZ, uEnd, v);
                vertices[2] = new GeoVertex(maxX, minY, minZ, uEnd, vEnd);
                vertices[3] = new GeoVertex(maxX, minY, maxZ, u, vEnd);
                break;
            case "west":
                vertices[0] = new GeoVertex(minX, maxY, minZ, u, v);
                vertices[1] = new GeoVertex(minX, maxY, maxZ, uEnd, v);
                vertices[2] = new GeoVertex(minX, minY, maxZ, uEnd, vEnd);
                vertices[3] = new GeoVertex(minX, minY, minZ, u, vEnd);
                break;
            case "up":
                vertices[0] = new GeoVertex(minX, maxY, minZ, u, v);
                vertices[1] = new GeoVertex(minX, maxY, maxZ, u, vEnd);
                vertices[2] = new GeoVertex(maxX, maxY, maxZ, uEnd, vEnd);
                vertices[3] = new GeoVertex(maxX, maxY, minZ, uEnd, v);
                break;
            case "down":
                vertices[0] = new GeoVertex(maxX, minY, minZ, u, v);
                vertices[1] = new GeoVertex(maxX, minY, maxZ, u, vEnd);
                vertices[2] = new GeoVertex(minX, minY, maxZ, uEnd, vEnd);
                vertices[3] = new GeoVertex(minX, minY, minZ, uEnd, v);
        }

        return vertices;
    }

    private static float[] parseFloatArray(JsonObject object, String key, float[] fallback) {
        if (!object.has(key)) {
            return fallback;
        }

        JsonArray array = object.getAsJsonArray(key);
        float[] result = new float[array.size()];

        for (int i = 0; i < array.size(); i++) {
            result[i] = array.get(i).getAsFloat();
        }

        return result;
    }
}
