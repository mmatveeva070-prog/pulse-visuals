package ru.pulse.wetworld;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.GlTexture;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import ru.pulse.Pulse;
import ru.pulse.config.PulseConfig;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Проход Wet World: screen-space отражения (SSR) и рябь на мокрых
 * поверхностях. Порт ez.redux WetWorld/WetWorldPipeline на собственные
 * настройки; UBO оригинального шейдера развёрнут в обычные юниформы.
 *
 * Запускается до прохода атмосферы, чтобы туман и тонировка ложились
 * поверх отражений. Шейдеры: /assets/pulse/shaders/wet_world.{vsh,fsh}.
 */
public final class WetWorldPipeline implements AutoCloseable {
    private static final WetWorldPipeline INSTANCE = new WetWorldPipeline();

    public static WetWorldPipeline getInstance() {
        return INSTANCE;
    }

    private WetWorldPipeline() {
    }

    public void reset() {
        failed = false;
    }

    private int program, vao, copyTexture, copyFbo, targetFbo, sampler;
    private int width, height;
    private boolean failed;
    private final Map<String, Integer> uniforms = new HashMap<>();
    private final long startTime = System.nanoTime();

    /**
     * Полноэкранный проход поверх готового кадра мира.
     *
     * @param viewRot    view-матрица только с вращением камеры (без трансляции)
     * @param projection проекция, которой растеризовался мир
     * @param tickDelta  частичный тик кадра
     */
    public void render(Camera camera, Matrix4f viewRot, Matrix4f projection, float tickDelta) {
        var client = MinecraftClient.getInstance();
        WetWorldSettings settings = PulseConfig.get().wetWorld;
        if (!settings.enabled || failed || client.world == null || !camera.isReady()) return;
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        float rainGradient = client.world.getRainGradient(tickDelta);
        if (settings.rainOnly && rainGradient <= 0.01f) return;
        var framebuffer = client.getFramebuffer();
        if (!(framebuffer.getColorAttachment() instanceof GlTexture color)
            || !(framebuffer.getDepthAttachment() instanceof GlTexture depth)) return;
        if (framebuffer.textureWidth < 2 || framebuffer.textureHeight < 2) return;

        try (GlState ignored = new GlState(); MemoryStack stack = MemoryStack.stackPush()) {
            if (program == 0) initialize();
            resize(framebuffer.textureWidth, framebuffer.textureHeight);

            glBindFramebuffer(GL_FRAMEBUFFER, targetFbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color.getGlId(), 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("WetWorld target framebuffer is incomplete");
            glBindFramebuffer(GL_READ_FRAMEBUFFER, targetFbo);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, copyFbo);
            glDisable(GL_SCISSOR_TEST);
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, targetFbo);
            glViewport(0, 0, width, height);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_BLEND);
            glDisable(GL_CULL_FACE);
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_FRAMEBUFFER_SRGB);
            glDisable(GL_RASTERIZER_DISCARD);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glColorMask(true, true, true, true);
            glDepthMask(false);
            glUseProgram(program);
            glBindVertexArray(vao);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, copyTexture);
            glBindSampler(0, sampler);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, depth.getGlId());
            glBindSampler(1, sampler);
            glUniform1i(location("Sampler0"), 0);
            glUniform1i(location("Sampler1"), 1);

            FloatBuffer matrix = stack.mallocFloat(16);
            new Matrix4f(projection).invert().get(matrix);
            glUniformMatrix4fv(location("uInvProjection"), false, matrix);
            new Matrix4f(projection).get(matrix);
            glUniformMatrix4fv(location("uProjection"), false, matrix);
            new Matrix4f(viewRot).get(matrix);
            glUniformMatrix4fv(location("uViewRot"), false, matrix);

            var position = camera.getCameraPos();
            glUniform4f(location("uCameraPos"), (float) position.x, (float) position.y, (float) position.z, 0f);
            glUniform4f(location("uScreen"), width, height, 0f, 0f);

            float effectiveStrength = settings.reflectionStrength;
            if (settings.rainOnly) effectiveStrength *= Math.clamp(rainGradient, 0f, 1f);
            float time = (float) ((System.nanoTime() - startTime) * 1e-9);
            glUniform4f(location("uParams"), effectiveStrength, settings.darkening, time, 0f);
            glUniform4f(location("uQualityParams"), settings.quality,
                settings.ripples ? 1f : 0f, settings.rippleSpeed, rainGradient);

            float fogEnd = Math.max(32, client.options.getClampedViewDistance() * 16);
            glUniform4f(location("uFogParams"), fogEnd * 0.75f, fogEnd, 0f, 0f);
            glUniform4f(location("uFogColor"), 0f, 0f, 0f, 0f);

            glDrawArrays(GL_TRIANGLES, 0, 3);
        } catch (Exception error) {
            failed = true;
            Pulse.LOGGER.error("WetWorld renderer disabled after an error", error);
            close();
        }
    }

    private void initialize() throws IOException {
        int vertex = shader(GL_VERTEX_SHADER, "wet_world.vsh");
        int fragment = 0;
        try {
            fragment = shader(GL_FRAGMENT_SHADER, "wet_world.fsh");
            program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
                throw new IllegalStateException(glGetProgramInfoLog(program));
        } finally {
            glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
        }
        vao = glGenVertexArrays();
        copyFbo = glGenFramebuffers();
        targetFbo = glGenFramebuffers();
        copyTexture = glGenTextures();
        sampler = glGenSamplers();
        glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glSamplerParameteri(sampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(sampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(sampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(sampler, GL_TEXTURE_COMPARE_MODE, GL_NONE);
    }

    private static int shader(int type, String name) throws IOException {
        String source;
        try (var stream = WetWorldPipeline.class.getResourceAsStream("/assets/pulse/shaders/" + name)) {
            if (stream == null) throw new IOException("Missing shader " + name);
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(id);
            glDeleteShader(id);
            throw new IllegalStateException(name + ": " + log);
        }
        return id;
    }

    private void resize(int w, int h) {
        if (width == w && height == h) return;
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, copyTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindFramebuffer(GL_FRAMEBUFFER, copyFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, copyTexture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("WetWorld copy framebuffer is incomplete");
        width = w;
        height = h;
    }

    private int location(String name) {
        return uniforms.computeIfAbsent(name, n -> glGetUniformLocation(program, n));
    }

    @Override
    public void close() {
        if (program != 0) glDeleteProgram(program);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (copyFbo != 0) glDeleteFramebuffers(copyFbo);
        if (targetFbo != 0) glDeleteFramebuffers(targetFbo);
        if (copyTexture != 0) glDeleteTextures(copyTexture);
        if (sampler != 0) glDeleteSamplers(sampler);
        program = vao = copyFbo = targetFbo = copyTexture = sampler = width = height = 0;
        uniforms.clear();
    }

    /** Restore actual GL state so Minecraft's cached state remains valid. */
    private static final class GlState implements AutoCloseable {
        private final int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        private final int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        private final int active = glGetInteger(GL_ACTIVE_TEXTURE);
        private final int[] texture = new int[2], sampler = new int[2], viewport = new int[4];
        private final int polygon;
        private final int[] capabilities = {GL_DEPTH_TEST, GL_BLEND, GL_CULL_FACE, GL_SCISSOR_TEST,
            GL_STENCIL_TEST, GL_FRAMEBUFFER_SRGB, GL_RASTERIZER_DISCARD};
        private final boolean[] enabled = new boolean[capabilities.length];
        private final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        private final boolean[] colorMask = new boolean[4];

        private GlState() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer v = stack.mallocInt(4);
                glGetIntegerv(GL_VIEWPORT, v);
                v.get(viewport);
                IntBuffer p = stack.mallocInt(2);
                glGetIntegerv(GL_POLYGON_MODE, p);
                polygon = p.get(0);
                ByteBuffer c = stack.malloc(4);
                glGetBooleanv(GL_COLOR_WRITEMASK, c);
                for (int i = 0; i < 4; i++) colorMask[i] = c.get(i) != 0;
            }
            for (int i = 0; i < capabilities.length; i++) enabled[i] = glIsEnabled(capabilities[i]);
            for (int i = 0; i < 2; i++) {
                glActiveTexture(GL_TEXTURE0 + i);
                texture[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
                sampler[i] = glGetInteger(GL_SAMPLER_BINDING);
            }
            glActiveTexture(active);
        }

        @Override
        public void close() {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
            glUseProgram(program);
            glBindVertexArray(vao);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glPolygonMode(GL_FRONT_AND_BACK, polygon);
            glDepthMask(depthMask);
            glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            for (int i = 0; i < capabilities.length; i++) {
                if (enabled[i]) glEnable(capabilities[i]);
                else glDisable(capabilities[i]);
            }
            for (int i = 0; i < 2; i++) {
                glActiveTexture(GL_TEXTURE0 + i);
                glBindTexture(GL_TEXTURE_2D, texture[i]);
                glBindSampler(i, sampler[i]);
            }
            glActiveTexture(active);
        }
    }
}
