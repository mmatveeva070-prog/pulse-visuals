package ru.pulse.cosmetic.geo;

/** Вершина гео-модели: позиция + UV (порт из бандля). */
public class GeoVertex {
    public Vec3F position;
    public float textureU;
    public float textureV;

    public GeoVertex(float x, float y, float z, float u, float v) {
        this.position = new Vec3F(x, y, z);
        this.textureU = u;
        this.textureV = v;
    }

    public GeoVertex(Vec3F position, float u, float v) {
        this.position = position;
        this.textureU = u;
        this.textureV = v;
    }
}
