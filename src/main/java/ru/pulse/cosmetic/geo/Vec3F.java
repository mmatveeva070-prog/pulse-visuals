package ru.pulse.cosmetic.geo;

/** 3D-вектор с float-компонентами (порт из бандля, без изменений). */
public class Vec3F {
    public static final Vec3F NULL_VECTOR = new Vec3F(0.0F, 0.0F, 0.0F);
    public float x;
    public float y;
    public float z;

    public Vec3F(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public float getZ() {
        return this.z;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setZ(float z) {
        this.z = z;
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3F crossProduct(Vec3F other) {
        return new Vec3F(this.y * other.z - this.z * other.y, this.z * other.x - this.x * other.z, this.x * other.y - this.y * other.x);
    }

    @Override
    public String toString() {
        return this.x + "," + this.y + "," + this.z;
    }
}
