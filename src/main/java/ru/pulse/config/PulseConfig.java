package ru.pulse.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import ru.pulse.Pulse;
import ru.pulse.atmosphere.AtmosphereSettings;
import ru.pulse.wetworld.WetWorldSettings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Потокобезопасный конфиг мода: config/pulse.json (GSON).
 * Секции: atmosphere (пост-процессинг), cosmetics (экипировка/лимиты), keys.
 */
public final class PulseConfig {
    private static final PulseConfig INSTANCE = new PulseConfig();

    public static PulseConfig get() {
        return INSTANCE;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Настройки атмосферы (поля сериализуются GSON'ом напрямую). */
    public final AtmosphereSettings atmosphere = new AtmosphereSettings();

    /** Настройки мокрого мира (SSR-отражения, рябь). */
    public final WetWorldSettings wetWorld = new WetWorldSettings();

    /** Настройки косметики. */
    public final CosmeticsSettings cosmetics = new CosmeticsSettings();

    /** Настройки клавиш (только документация; сами бинды в Controls). */
    public final KeySettings keys = new KeySettings();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Path file;

    private PulseConfig() {
        this.file = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("pulse.json");
    }

    public static class CosmeticsSettings {
        /** Идентификаторы (resource index) экипированной косметики. COW: GUI пишет, рендер-поток читает. */
        public List<Integer> selectedIds = new CopyOnWriteArrayList<>();
        /** Максимум одновременно экипированных предметов. */
        public int maxSelected = 5;
        /** Показывать косметику на себе. */
        public boolean showOnSelf = true;
        /** Показывать косметику на других игроках. */
        public boolean showOnOthers = true;
        /** Показывать плащи. */
        public boolean showCapes = true;
    }

    public static class KeySettings {
        public String cosmeticsScreen = "V";
        public String emote = "B";
    }

    /** Читает конфиг с диска; при ошибке оставляет значения по умолчанию. */
    public void load() {
        lock.writeLock().lock();
        try {
            if (!Files.exists(file)) {
                save();
                Pulse.LOGGER.info("Created default config {}", file);
                return;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                PulseConfig loaded = GSON.fromJson(reader, PulseConfig.class);
                if (loaded != null) {
                    this.atmosphere.copyFrom(loaded.atmosphere);
                    this.wetWorld.copyFrom(loaded.wetWorld);
                    copyCosmetics(loaded.cosmetics);
                    this.keys.cosmeticsScreen = loaded.keys.cosmeticsScreen;
                    this.keys.emote = loaded.keys.emote;
                }
                Pulse.LOGGER.info("Loaded config {}", file);
            }
        } catch (IOException | JsonSyntaxException error) {
            Pulse.LOGGER.error("Failed to read config, using defaults", error);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Записывает конфиг на диск. */
    public void save() {
        lock.readLock().lock();
        try {
            Files.createDirectories(file.getParent());
            Snapshot snapshot = snapshot();
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot, writer);
            }
        } catch (IOException error) {
            Pulse.LOGGER.error("Failed to save config", error);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Атомарный снимок текущего состояния (для сериализации вне блокировки). */
    public Snapshot snapshot() {
        lock.readLock().lock();
        try {
            return new Snapshot(this);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Serializable copy of the config state. */
    public static final class Snapshot {
        public final AtmosphereSettings atmosphere;
        public final WetWorldSettings wetWorld;
        public final CosmeticsSettings cosmetics;
        public final KeySettings keys;

        private Snapshot(PulseConfig source) {
            this.atmosphere = source.atmosphere.cloneSettings();
            this.wetWorld = source.wetWorld.cloneSettings();
            this.cosmetics = new CosmeticsSettings();
            this.cosmetics.selectedIds = List.copyOf(source.cosmetics.selectedIds);
            this.cosmetics.maxSelected = source.cosmetics.maxSelected;
            this.cosmetics.showOnSelf = source.cosmetics.showOnSelf;
            this.cosmetics.showOnOthers = source.cosmetics.showOnOthers;
            this.cosmetics.showCapes = source.cosmetics.showCapes;
            this.keys = new KeySettings();
            this.keys.cosmeticsScreen = source.keys.cosmeticsScreen;
            this.keys.emote = source.keys.emote;
        }
    }

    private void copyCosmetics(CosmeticsSettings other) {
        if (other == null) {
            return;
        }
        this.cosmetics.selectedIds = new CopyOnWriteArrayList<>(other.selectedIds);
        this.cosmetics.maxSelected = other.maxSelected;
        this.cosmetics.showOnSelf = other.showOnSelf;
        this.cosmetics.showOnOthers = other.showOnOthers;
        this.cosmetics.showCapes = other.showCapes;
    }
}
