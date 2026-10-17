package ru.pulse.cosmetic.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** Обёртка над MatrixStack с удобными операциями (порт из бандля). */
public class RenderStack {
    private MatrixStack stack;

    public void update(MatrixStack stack) {
        this.stack = stack;
    }

    public void push() {
        this.stack.push();
    }

    public void pop() {
        this.stack.pop();
    }

    public MatrixStack get() {
        return this.stack;
    }

    public void translate(float x, float y, float z) {
        this.stack.translate(x, y, z);
    }

    public void scale(float x, float y, float z) {
        this.stack.scale(x, y, z);
    }

    public void rotateX(float angle) {
        this.stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
    }

    public void rotateY(float angle) {
        this.stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
    }

    public void rotateZ(float angle) {
        this.stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
    }

    public void rotate(float x, float y, float z) {
        if (z != 0.0F) {
            this.rotateZ(z);
        }

        if (y != 0.0F) {
            this.rotateY(y);
        }

        if (x != 0.0F) {
            this.rotateX(x);
        }
    }

    public void rotateDegrees(float x, float y, float z) {
        this.rotate(x, y, z);
    }

    public void rotateXDegrees(float angle) {
        this.rotateX(angle);
    }

    public void rotateYDegrees(float angle) {
        this.rotateY(angle);
    }

    public void rotateZDegrees(float angle) {
        this.rotateZ(angle);
    }
}
