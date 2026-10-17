#version 330 core
// Independent fullscreen triangle. No vertex buffers or native client required.
out vec2 uv;
void main() {
    vec2 position = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    uv = position;
    gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
}
