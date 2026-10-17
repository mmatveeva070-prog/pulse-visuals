#version 330 core
// NEW implementation. Native settings/palettes are supplied by Atmosphere.java.
// This is not an extracted original shader.
in vec2 uv;
out vec4 fragColor;
uniform sampler2D uScene, uDepth;
uniform mat4 uInverseViewProjection;
uniform vec2 uResolution;
uniform vec3 uCamera, uSkyTop, uSkyHorizon, uFogCool, uFogWarm, uSunColor, uSunDirection, uSunScreen;
uniform int uMode;
uniform float uTime, uDistance, uOutside, uDensity, uScatterHeight, uRays, uSoftness;
uniform float uAmount, uSpeed, uHaze, uGrade, uVignette;

vec3 hash3(vec3 p) {
    p = vec3(dot(p, vec3(127.1,311.7,74.7)), dot(p,vec3(269.5,183.3,246.1)), dot(p,vec3(113.5,271.9,124.6)));
    return fract(sin(p) * 43758.5453);
}
vec3 unproject(float depth) {
    vec4 p = uInverseViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

// World-space particles: traverse grid cells and intersect camera rays with flakes.
// Sampling stops at scene depth, so particles cannot appear through opaque terrain.
vec3 particles(vec3 ray, float sceneDistance) {
    if (uAmount <= 0.0 || uOutside <= 0.01) return vec3(0.0);
    float size = uMode == 1 ? 3.6 : 2.8;
    vec3 velocity = uMode == 2 ? vec3(.22,-1.15,.12) :
                    uMode == 3 ? vec3(.18,.65,.08) : vec3(.35,.06,.12);
    vec3 origin = (uCamera - velocity * (uTime * uSpeed)) / size;
    vec3 cell = floor(origin);
    vec3 stepDirection = sign(ray);
    vec3 safeRay = mix(vec3(1e-6), ray, greaterThan(abs(ray), vec3(1e-6)));
    vec3 delta = abs(1.0 / safeRay);
    vec3 next = (cell - origin + step(vec3(0.0), ray)) / safeRay;
    vec3 result = vec3(0.0);
    float entry = 0.0;
    float limit = min(sceneDistance, 46.0) / size;
    for (int i = 0; i < 26; i++) {
        if (entry > limit) break;
        vec3 random = hash3(cell);
        if (random.z < uAmount * .9) {
            vec3 center = cell + .18 + random * .64;
            float t = dot(center - origin, ray);
            float radius = (uMode == 2 ? .035 : uMode == 3 ? .020 : .026) * (.5 + random.x);
            float distanceToRay = length((origin + ray * t) - center);
            float aa = max(radius * .28, t * 1.8 / uResolution.y);
            float flake = 1.0 - smoothstep(radius - aa, radius + aa, distanceToRay);
            float visibility = smoothstep(0.1, .6, t) * (1.0 - smoothstep(limit - 1.5, limit, t));
            visibility *= step(0.0, t) * step(t, sceneDistance / size);
            vec3 tint = uMode == 2 ? vec3(.85,.92,1.0) : uMode == 3 ? vec3(1.0,.35,.06) : vec3(.7,.55,.35);
            result += tint * flake * visibility * (uMode == 3 ? 1.3 : .55);
        }
        if (next.x < next.y && next.x < next.z) { entry = next.x; next.x += delta.x; cell.x += stepDirection.x; }
        else if (next.y < next.z) { entry = next.y; next.y += delta.y; cell.y += stepDirection.y; }
        else { entry = next.z; next.z += delta.z; cell.z += stepDirection.z; }
    }
    return result * uOutside;
}

void main() {
    vec4 original = texture(uScene, uv);
    float depth = texture(uDepth, uv).r;
    bool sky = depth >= .999999;
    vec3 position = unproject(min(depth, .999999));
    float distanceToSurface = length(position);
    vec3 ray = normalize(unproject(.99999));
    float sunFacing = max(dot(ray, uSunDirection), 0.0);
    float horizon = pow(1.0 - clamp(ray.y, 0.0, 1.0), 3.0);
    vec3 skyColor = mix(uSkyTop, uSkyHorizon, horizon);
    float sunDisk = smoothstep(.9996, .99988, sunFacing);
    skyColor += uSunColor * (sunDisk * .65 + pow(sunFacing, 22.0) * .2) * step(.01, uRays);
    vec3 color = original.rgb;
    if (sky) color = mix(original.rgb, skyColor, uMode == 0 ? .88 : .83);

    float haze = uMode == 0 ? uDensity : uHaze * .5;
    float worldHeight = uCamera.y + position.y;
    float heightFalloff = exp(-max((worldHeight + uCamera.y) * .5 - uScatterHeight + 18.0, 0.0) / 32.0);
    float fog = 1.0 - exp(-distanceToSurface / max(uDistance,32.0) * haze * 2.8 * heightFalloff);
    fog *= sky ? .10 : .85;
    vec3 fogColor = mix(uFogCool, uFogWarm, pow(sunFacing, 3.0));
    // Depth-aware softness prevents blurred silhouettes bleeding into nearby geometry.
    if (!sky && uSoftness > 0.0 && fog > .015) {
        vec3 blurred = color;
        float weights = 1.0;
        for (int i = 0; i < 4; i++) {
            vec2 offset = vec2(i == 0 ? 1 : i == 1 ? -1 : 0, i == 2 ? 1 : i == 3 ? -1 : 0);
            vec2 sampleUv = uv + offset * (1.0 + uSoftness * 2.0) / uResolution;
            float weight = exp(-abs(texture(uDepth, sampleUv).r - depth) * 4000.0);
            blurred += texture(uScene, sampleUv).rgb * weight;
            weights += weight;
        }
        color = mix(color, blurred / weights, fog * uSoftness * .65);
    }
    color = mix(color, fogColor, clamp(fog, 0.0, .9));

    // Screen-space light shafts, occluded by the world depth texture.
    if (uRays > .001 && uSunScreen.z > 0.0 && uOutside > .01) {
        vec2 sampleUv = uv;
        vec2 stepUv = (uSunScreen.xy - uv) / 20.0;
        float illumination = 0.0, attenuation = 1.0;
        for (int i = 0; i < 20; i++) {
            sampleUv += stepUv;
            if (all(greaterThanEqual(sampleUv,vec2(0.0))) && all(lessThanEqual(sampleUv,vec2(1.0))))
                illumination += step(.999999, texture(uDepth, sampleUv).r) * attenuation;
            attenuation *= .94;
        }
        float glow = pow(sunFacing, 12.0);
        color += uSunColor * illumination / 20.0 * glow * uRays * uSunScreen.z * .32 * uOutside;
    }
    if (uMode != 0) {
        vec3 tint = uMode == 1 ? vec3(1.08,1.01,.88) : uMode == 2 ? vec3(.88,.96,1.08) : vec3(1.12,.83,.68);
        color = mix(color, color * tint + uSkyHorizon * .025, uGrade);
        color += particles(ray, sky ? 1000.0 : distanceToSurface);
        vec2 q = uv * 2.0 - 1.0;
        float edge = smoothstep(.35,1.45,dot(q,q));
        color *= 1.0 - edge * uVignette * .35;
        if (uMode == 2) {
            float frost = pow(edge, 3.0) * uVignette * .12;
            color = mix(color, vec3(.79,.9,1.0), frost);
        }
        if (uMode == 1) color += vec3(.14,.09,.035) * edge * uVignette * pow(sunFacing,3.0);
    }
    fragColor = vec4(clamp(color, 0.0, 1.0), original.a);
}
