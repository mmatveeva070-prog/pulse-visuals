package ru.pulse.cosmetic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.pulse.Pulse;
import ru.pulse.config.PulseConfig;
import ru.pulse.cosmetic.loader.CosmeticLoader;
import ru.pulse.cosmetic.model.CosmeticModel;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Каталог локальной косметики (аналог pulse.cosmetic.LocalCosmetics):
 * сканирует assets/pulse/cosmetics/models/cosmetic_*.json из jar,
 * даёт доступ к типам/именам/иконкам и управляет экипировкой (до maxSelected,
 * по умолчанию 5) с сохранением выбора в конфиг.
 */
public final class CosmeticCatalog {
    private static final int MAX_SCAN_INDEX = 256;
    private static final List<Entry> ENTRIES = loadEntries();
    private static final Map<Integer, CosmeticModel> MODELS = new HashMap<>();
    private static final Map<Integer, Identifier> ICON_TEXTURES = new LinkedHashMap<>();

    private CosmeticCatalog() {
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static Entry entry(int index) {
        if (index < 0 || index >= ENTRIES.size()) {
            throw new IndexOutOfBoundsException("Cosmetic index " + index + " out of " + ENTRIES.size());
        }
        return ENTRIES.get(index);
    }

    /** Поиск записи по resource index (номеру файла cosmetic_N.json). */
    public static Entry byResourceIndex(int resourceIndex) {
        for (Entry entry : ENTRIES) {
            if (entry.index == resourceIndex) {
                return entry;
            }
        }
        return null;
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static String name(int index) {
        return entry(index).name;
    }

    public static String type(int index) {
        return entry(index).type;
    }

    public static int resourceIndex(int index) {
        return entry(index).index;
    }

    public static boolean isSelected(int index) {
        return PulseConfig.get().cosmetics.selectedIds.contains(entry(index).index);
    }

    public static boolean isSelectedResourceIndex(int resourceIndex) {
        return PulseConfig.get().cosmetics.selectedIds.contains(resourceIndex);
    }

    public static List<Integer> selectedResourceIndices() {
        return List.copyOf(PulseConfig.get().cosmetics.selectedIds);
    }

    public static int selectedCount() {
        return PulseConfig.get().cosmetics.selectedIds.size();
    }

    public static int maxSelected() {
        return PulseConfig.get().cosmetics.maxSelected;
    }

    /**
     * Экипирует/снимает косметику; снимает без лимита, надевает — только
     * если есть свободный слот (лимит maxSelected из конфига).
     *
     * @return true, если состояние изменилось.
     */
    public static boolean toggle(int index) {
        Entry entry = entry(index);
        List<Integer> selected = PulseConfig.get().cosmetics.selectedIds;
        if (selected.contains(entry.index)) {
            selected.remove(Integer.valueOf(entry.index));
        } else {
            if (selected.size() >= PulseConfig.get().cosmetics.maxSelected) {
                Pulse.LOGGER.warn("Cosmetic limit reached ({}), cannot equip {}", PulseConfig.get().cosmetics.maxSelected, entry.name);
                return false;
            }
            selected.add(entry.index);
            if (!"cape".equals(entry.type)) {
                model(index);
            }
        }
        PulseConfig.get().save();
        return true;
    }

    /** Модель для рендера по индексу каталога (лениво разбирается и кэшируется). */
    public static CosmeticModel modelFor(int index) {
        return model(index);
    }

    /** Все экипированные модели для рендера. */
    public static List<CosmeticModel> selectedModels() {
        List<CosmeticModel> result = new ArrayList<>();
        for (int resourceIndex : PulseConfig.get().cosmetics.selectedIds) {
            CosmeticModel model = MODELS.get(resourceIndex);
            if (model == null) {
                for (int i = 0; i < ENTRIES.size(); i++) {
                    if (ENTRIES.get(i).index == resourceIndex) {
                        model = model(i);
                        break;
                    }
                }
            }
            if (model != null) {
                result.add(model);
            }
        }
        return result;
    }

    private static CosmeticModel model(int index) {
        int resourceIndex = entry(index).index;
        return MODELS.computeIfAbsent(resourceIndex, key -> {
            try (InputStream in = CosmeticCatalog.class.getResourceAsStream("/assets/pulse/cosmetics/models/cosmetic_" + key + ".json")) {
                return in == null ? null : CosmeticLoader.getInstance().loadFromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Exception error) {
                Pulse.LOGGER.error("Failed to load cosmetic model " + key, error);
                return null;
            }
        });
    }

    /**
     * Иконка карточки: item_N.png из jar, динамическая регистрация текстуры
     * через TextureManager (Identifier pulse:cosmetic/icon_N). Вызывать на
     * клиентском/рендер-потоке (из GUI).
     */
    public static Identifier iconTexture(int index, int textureWidth[], int textureHeight[]) {
        int resourceIndex = entry(index).index;
        Identifier cached = ICON_TEXTURES.get(resourceIndex);
        if (cached != null) {
            return cached;
        }
        Identifier textureId = Identifier.of("pulse", "cosmetic/icon_" + resourceIndex);
        try (InputStream in = CosmeticCatalog.class.getResourceAsStream("/assets/pulse/textures/cosmetics/item_" + resourceIndex + ".png")) {
            if (in == null) {
                return null;
            }
            NativeImage image = NativeImage.read(in);
            if (textureWidth != null && textureWidth.length > 0) {
                textureWidth[0] = image.getWidth();
            }
            if (textureHeight != null && textureHeight.length > 0) {
                textureHeight[0] = image.getHeight();
            }
            MinecraftClient client = MinecraftClient.getInstance();
            Runnable register = () -> client.getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(() -> "pulse icon", image));
            if (client.isOnThread()) {
                register.run();
            } else {
                client.execute(register);
            }
            ICON_TEXTURES.put(resourceIndex, textureId);
            return textureId;
        } catch (Exception error) {
            Pulse.LOGGER.error("Failed to load icon for cosmetic " + resourceIndex, error);
            return null;
        }
    }

    private static List<Entry> loadEntries() {
        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < MAX_SCAN_INDEX; i++) {
            String path = "/assets/pulse/cosmetics/models/cosmetic_" + i + ".json";
            try (InputStream in = CosmeticCatalog.class.getResourceAsStream(path)) {
                if (in != null) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject object = JsonParser.parseString(json).getAsJsonObject();
                    String rawName = object.has("name") ? object.get("name").getAsString() : "Cosmetic " + (i + 1);
                    String rawType = object.has("type") ? object.get("type").getAsString() : "";
                    int pos = object.has("pos") ? object.get("pos").getAsInt() : -1;
                    entries.add(new Entry(i, displayName(rawName, i), classifyType(i, rawName, rawType, pos)));
                }
            } catch (Exception ignored) {
            }
        }

        entries.sort(Comparator.comparingInt(entry -> entry.index));
        return List.copyOf(entries);
    }

    private static String displayName(String rawName, int index) {
        String name = rawName == null || rawName.isBlank() ? "Cosmetic " + (index + 1) : rawName;
        if (name.startsWith("pulse_")) {
            name = name.substring("pulse_".length());
        }

        return name.replace('_', ' ').trim();
    }

    private static String classifyType(int index, String name, String rawType, int pos) {
        if (rawType != null && !rawType.isBlank()) {
            return rawType.trim().toLowerCase();
        }

        String lower = name == null ? "" : name.toLowerCase();
        if (lower.contains("cape")) {
            return "cape";
        } else if (lower.contains("wing")) {
            return "wings";
        } else if (lower.contains("pet") || lower.contains("bee") || lower.contains("radish")) {
            return "pet";
        } else if (lower.contains("hat") || lower.contains("nimb") || pos == 2) {
            return "hat";
        } else if (index <= 13) {
            return "cape";
        } else if (index <= 26) {
            return "wings";
        } else if (index <= 37) {
            return "bodywear";
        } else if (index <= 49) {
            return "pet";
        } else {
            return "bodywear";
        }
    }

    /** Запись каталога: resource index, отображаемое имя, тип. */
    public static final class Entry {
        public final int index;
        public final String name;
        public final String type;

        private Entry(int index, String name, String type) {
            this.index = index;
            this.name = name;
            this.type = type;
        }
    }
}
