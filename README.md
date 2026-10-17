# Pulse Visuals

Клиентский визуальный мод для **Minecraft 1.21.11 (Fabric)**: косметика игроков
(шляпы, плащи, крылья, питомцы, боди) и атмосферный пост-процессинг экрана.
Полностью косметический — не даёт никакого игрового преимущества.

## Сборка

Требуется JDK 25 (или любой JDK ≥ 21; таргет байткода — Java 21).

```bat
cd C:\Visual\pulse-visuals
.\gradlew.bat build
```

Первый запуск скачивает Gradle 9.8.1, Minecraft 1.21.11, yarn-маппинги и Fabric API —
это долго. Готовый jar: `build\libs\pulse-1.0.0.jar`.

## Установка

1. Установите [Fabric Loader](https://fabricmc.net/use/) ≥ 0.20.0 для Minecraft 1.21.11.
2. Положите `pulse-1.0.0.jar` и [Fabric API](https://modrinth.com/mod/fabric-api)
   (0.141.6+1.21.11) в папку `mods/` лаунчера.
3. Опционально: [ModMenu](https://modrinth.com/mod/modmenu) — экран настроек из списка модов.

## Управление

| Клавиша | Действие |
|---|---|
| **V** | Экран косметики (категории All/Capes/Wings/Body/Pets/Hats, поиск, лимит 5 экипированных) |
| **B** | Эмоут: нет → wave (2 с) → dance (3 с) → нет |
| ModMenu | Настройки атмосферы и показа косметики |

## Конфиг — `config/pulse.json`

- `atmosphere`: `enabled`, `mode` (`dawn|dust|snow|embers`), `density`, `scatterHeight`,
  `godRays`, `softness`, `dawnColor` (ARGB), `amount`, `speed`, `haze`, `grade`, `vignette`
- `cosmetics`: `selectedIds` (экипировка), `maxSelected` (5), `showOnSelf`, `showOnOthers`, `showCapes`
- `keys`: документация клавиш (сами бинды — в Controls, категория «Pulse»)

## Структура кода

```
ru.pulse
├── Pulse / PulseClient        — entrypoints (main/client), клавиши V/B, тик-события
├── config.PulseConfig         — потокобезопасный GSON-конфиг (load/save)
├── atmosphere
│   ├── AtmosphereSettings     — настройки + parameters(worldTime) → VisualState (порт Atmosphere)
│   └── AtmospherePostPipeline — GL33 пост-процессинг (порт), шейдеры assets/pulse/shaders/
├── cosmetic
│   ├── CosmeticCatalog        — каталог 63 косметик из assets/pulse/cosmetics/models
│   ├── CosmeticFeatureRenderer— фича-рендерер на PlayerEntityRenderState
│   ├── PlayerRenderTracker    — связка render-state ↔ игрок
│   ├── loader.CosmeticLoader  — парсинг JSON, base64-текстуры через TextureManager
│   ├── model.*                — CosmeticModel/ModelPosition
│   ├── geo.*                  — GeoModel/GeoBone/GeoCube/... (Bedrock-формат)
│   ├── geckolib.*             — рендер geo-моделей, keyframe-анимации + idle sway/bob
│   └── render.*               — CosmeticRenderer/RenderStack (позиционирование по частям тела)
├── emote.EmoteManager         — wave/dance (правая рука/тело), только визуально
├── gui
│   ├── CosmeticsScreen        — экран косметики (ванильные widget API)
│   ├── AtmosphereSettingsScreen— слайдеры/кнопки настроек
│   └── ModMenuScreens         — ModMenu entrypoint
└── mixin
    ├── CosmeticRendererMixin  — addFeature(CosmeticFeatureRenderer) в PlayerEntityRenderer
    ├── PlayerEntityRendererMixin / PlayerEntityModelMixin — трекинг state + позы эмоутов
    ├── AtmosphereRenderMixin  — пост-процессинг в TAIL WorldRenderer#render
    │   (фрейм-граф выполнен, глубина мира цела; матрицы и солнце — ванильные,
    │   из аргументов вызова и EnvironmentAttributes)
    └── AtmosphericFogModifierMixin — атмосферный туман из настроек
```

## Ресурсы

- `assets/pulse/cosmetics/models/cosmetic_0..62.json` — 63 модели косметики
  (geo-модель + base64-текстура + позиция + анимация)
- `assets/pulse/textures/cosmetics/` — текстуры (cosmetic_N, item_N — иконки, cape_N)
- `assets/pulse/shaders/atmosphere.{vsh,fsh}` — шейдеры атмосферы
