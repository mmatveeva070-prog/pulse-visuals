package ru.pulse.cosmetic.geo;

/** Квад грани гео-модели (порт из бандля). */
public class GeoQuad {
    public GeoVertex[] vertices;
    public Vec3F normal;

    public GeoQuad(GeoVertex[] vertices, Vec3F normal) {
        this.vertices = vertices;
        this.normal = normal;
    }

    public GeoQuad(GeoVertex[] vertices, float nx, float ny, float nz) {
        this.vertices = vertices;
        this.normal = new Vec3F(nx, ny, nz);
    }
}
