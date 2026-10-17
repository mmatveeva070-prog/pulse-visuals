package ru.pulse.cosmetic.geo;

import java.util.ArrayList;
import java.util.List;

/** Кость гео-модели с иерархией, позициями и вращением (порт из бандля). */
public class GeoBone {
    public GeoBone parent;
    public List<GeoBone> childBones = new ArrayList<>();
    public List<GeoCube> childCubes = new ArrayList<>();
    public String name;
    public boolean isHidden = false;
    public float rotationPointX;
    public float rotationPointY;
    public float rotationPointZ;
    private float rotateX;
    private float rotateY;
    private float rotateZ;
    private float positionX;
    private float positionY;
    private float positionZ;
    private float scaleX = 1.0F;
    private float scaleY = 1.0F;
    private float scaleZ = 1.0F;

    public GeoBone(String name) {
        this.name = name;
    }

    public float getRotationX() {
        return this.rotateX;
    }

    public float getRotationY() {
        return this.rotateY;
    }

    public float getRotationZ() {
        return this.rotateZ;
    }

    public void setRotationX(float rotationX) {
        this.rotateX = rotationX;
    }

    public void setRotationY(float rotationY) {
        this.rotateY = rotationY;
    }

    public void setRotationZ(float rotationZ) {
        this.rotateZ = rotationZ;
    }

    public float getPositionX() {
        return this.positionX;
    }

    public float getPositionY() {
        return this.positionY;
    }

    public float getPositionZ() {
        return this.positionZ;
    }

    public void setPositionX(float positionX) {
        this.positionX = positionX;
    }

    public void setPositionY(float positionY) {
        this.positionY = positionY;
    }

    public void setPositionZ(float positionZ) {
        this.positionZ = positionZ;
    }

    public float getScaleX() {
        return this.scaleX;
    }

    public float getScaleY() {
        return this.scaleY;
    }

    public float getScaleZ() {
        return this.scaleZ;
    }

    public void setScaleX(float scaleX) {
        this.scaleX = scaleX;
    }

    public void setScaleY(float scaleY) {
        this.scaleY = scaleY;
    }

    public void setScaleZ(float scaleZ) {
        this.scaleZ = scaleZ;
    }

    public float getPivotX() {
        return this.rotationPointX;
    }

    public float getPivotY() {
        return this.rotationPointY;
    }

    public float getPivotZ() {
        return this.rotationPointZ;
    }

    public void setPivotX(float pivotX) {
        this.rotationPointX = pivotX;
    }

    public void setPivotY(float pivotY) {
        this.rotationPointY = pivotY;
    }

    public void setPivotZ(float pivotZ) {
        this.rotationPointZ = pivotZ;
    }

    public String getName() {
        return this.name;
    }

    public boolean isHidden() {
        return this.isHidden;
    }

    public void setHidden(boolean hidden) {
        this.isHidden = hidden;
    }
}
