package com.wilderop.ghost;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifetime playtime per player. Survives the 24h ghost window.
 * Can seed totals from vanilla/Paper stats JSON (world/stats/uuid.json).
 */
public final class LifetimePlaytime {

    private final Path dataDirectory;
    private final Logger logger;
    private final Map<UUID, Entry> totals = new ConcurrentHashMap<>();

    public LifetimePlaytime(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public long getSeconds(UUID uuid) {
        Entry e = totals.get(uuid);
        return e == null ? 0L : e.seconds;
    }

    public void rememberName(UUID uuid, String name) {
        if (uuid == null || name == null) {
            return;
        }
        totals.compute(uuid, (id, existing) -> {
            if (existing == null) {
                return new Entry(name, 0L);
            }
            existing.name = name;
            return existing;
        });
    }

    public long addSeconds(UUID uuid, String name, long extraSeconds) {
        if (uuid == null || extraSeconds <= 0) {
            return getSeconds(uuid);
        }
        Entry e = totals.computeIfAbsent(uuid, id -> new Entry(name != null ? name : "Unknown", 0L));
        if (name != null) {
            e.name = name;
        }
        e.seconds += extraSeconds;
        return e.seconds;
    }

    public long raiseTo(UUID uuid, String name, long seconds) {
        if (uuid == null || seconds <= 0) {
            return getSeconds(uuid);
        }
        Entry e = totals.computeIfAbsent(uuid, id -> new Entry(name != null ? name : "Unknown", 0L));
        if (name != null && (e.name == null || e.name.equals("Unknown"))) {
            e.name = name;
        }
        if (seconds > e.seconds) {
            e.seconds = seconds;
        }
        return e.seconds;
    }

    public void load() {
        Path file = dataDirectory.resolve("totals.yml");
        if (!Files.exists(file)) {
            logger.info("No totals.yml yet — lifetime playtime starts empty until sessions or stats import");
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Yaml yaml = new Yaml();
            Object raw = yaml.load(in);
            if (!(raw instanceof Map<?, ?> map)) {
                return;
            }
            int loaded = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(String.valueOf(entry.getKey()));
                    if (!(entry.getValue() instanceof Map<?, ?> values)) {
                        continue;
                    }
                    String name = String.valueOf(values.getOrDefault("name", "Unknown"));
                    Object secObj = values.get("seconds");
                    long seconds = secObj instanceof Number n ? n.longValue() : 0L;
                    if (seconds < 0) {
                        seconds = 0;
                    }
                    totals.put(uuid, new Entry(name, seconds));
                    loaded++;
                } catch (Exception ex) {
                    logger.warn("Skipping bad totals.yml entry {}: {}", entry.getKey(), ex.getMessage());
                }
            }
            logger.info("Loaded lifetime playtime for {} players", loaded);
        } catch (Exception e) {
            logger.warn("Could not load totals.yml", e);
        }
    }

    public void save() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            List<Map.Entry<UUID, Entry>> sorted = new ArrayList<>(totals.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue().seconds, a.getValue().seconds));
            for (Map.Entry<UUID, Entry> entry : sorted) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", entry.getValue().name);
                row.put("seconds", entry.getValue().seconds);
                out.put(entry.getKey().toString(), row);
            }
            Path file = dataDirectory.resolve("totals.yml");
            Files.writeString(file, new Yaml().dump(out));
        } catch (IOException e) {
            logger.error("Failed to save totals.yml", e);
        }
    }

    public int importFromStatsDirectories(List<Path> directories) {
        if (directories == null || directories.isEmpty()) {
            logger.info("No stats-directories configured; skip player-file import");
            return 0;
        }
        int imported = 0;
        int raised = 0;
        for (Path dir : directories) {
            if (dir == null) {
                continue;
            }
            Path resolved = dir;
            if (!Files.isDirectory(resolved)) {
                Path statsSub = dir.resolve("stats");
                if (Files.isDirectory(statsSub)) {
                    resolved = statsSub;
                } else {
                    logger.warn("Stats directory does not exist: {}", dir.toAbsolutePath());
                    continue;
                }
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(resolved, "*.json")) {
                for (Path file : stream) {
                    UUID uuid = uuidFromFileName(file);
                    if (uuid == null) {
                        continue;
                    }
                    long seconds = readPlaytimeSeconds(file);
                    imported++;
                    long before = getSeconds(uuid);
                    raiseTo(uuid, null, seconds);
                    if (getSeconds(uuid) > before) {
                        raised++;
                    }
                }
            } catch (IOException e) {
                logger.warn("Failed to scan stats directory {}", resolved, e);
            }
        }
        logger.info("Scanned {} player stat files; raised lifetime totals for {} players", imported, raised);
        return imported;
    }

    private static UUID uuidFromFileName(Path file) {
        String name = file.getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return null;
        }
        String id = name.substring(0, name.length() - 5);
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private long readPlaytimeSeconds(Path file) {
        try {
            String text = Files.readString(file);
            JsonElement rootEl = JsonParser.parseString(text);
            if (!rootEl.isJsonObject()) {
                return 0;
            }
            JsonObject root = rootEl.getAsJsonObject();
            long ticks = 0;

            if (root.has("stats") && root.get("stats").isJsonObject()) {
                JsonObject stats = root.getAsJsonObject("stats");
                if (stats.has("minecraft:custom") && stats.get("minecraft:custom").isJsonObject()) {
                    JsonObject custom = stats.getAsJsonObject("minecraft:custom");
                    ticks = Math.max(ticks, readLong(custom, "minecraft:play_time"));
                    ticks = Math.max(ticks, readLong(custom, "minecraft:play_one_minute"));
                }
            }

            ticks = Math.max(ticks, readLong(root, "stat.playOneMinute"));
            ticks = Math.max(ticks, readLong(root, "minecraft:play_one_minute"));
            ticks = Math.max(ticks, readLong(root, "minecraft:play_time"));

            if (ticks <= 0) {
                return 0;
            }
            return ticks / 20L;
        } catch (Exception e) {
            logger.debug("Could not parse stats file {}", file, e);
            return 0;
        }
    }

    private static long readLong(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) {
            return 0;
        }
        try {
            JsonElement el = obj.get(key);
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                return el.getAsLong();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static final class Entry {
        String name;
        long seconds;

        Entry(String name, long seconds) {
            this.name = name;
            this.seconds = seconds;
        }
    }
}
