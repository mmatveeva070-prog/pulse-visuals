package ru.pulse.cosmetic.geo;

/** Куб гео-модели (порт из бандля). */
public class GeoCube {
    public GeoQuad[] quads = new GeoQuad[6];
    public Vec3F size;
    public Vec3F pivot;
    public Vec3F rotation;
    public float inflate;
    public boolean mirror;

    public GeoCube(float width, float height, float depth) {
        this.size = new Vec3F(width, height, depth);
        this.pivot = new Vec3F(0.0F, 0.0F, 0.0F);
        this.rotation = new Vec3F(0.0F, 0.0F, 0.0F);
        this.inflate = 0.0F;
        this.mirror = false;
    }
}
