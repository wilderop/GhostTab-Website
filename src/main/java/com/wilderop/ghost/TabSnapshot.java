package com.wilderop.ghost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Writes a GhostTab-matching JSON snapshot and optionally POSTs it outbound.
 */
public final class TabSnapshot {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path dataDirectory;
    private final Logger logger;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private volatile boolean exportJson = true;
    private volatile String pushUrl = "";
    private volatile String pushToken = "";

    public TabSnapshot(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public void configure(boolean exportJson, String pushUrl, String pushToken) {
        this.exportJson = exportJson;
        this.pushUrl = pushUrl == null ? "" : pushUrl.trim();
        this.pushToken = pushToken == null ? "" : pushToken.trim();
    }

    public String build(
            Instant now,
            List<Row> online,
            List<Row> ghosts,
            String totalHours,
            long playtimeWindowHours,
            long offlineWindowHours
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("updatedAt", now.getEpochSecond());
        root.addProperty("onlineCount", online.size());
        root.addProperty("ghostCount", ghosts.size());
        root.addProperty("totalHours", totalHours);
        root.addProperty("playtimeWindowHours", playtimeWindowHours);
        root.addProperty("offlineWindowHours", offlineWindowHours);
        root.add("online", toArray(online, true, now));
        root.add("ghosts", toArray(ghosts, false, now));
        return GSON.toJson(root);
    }

    public void publish(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        if (exportJson) {
            writeFile(json);
        }
        if (!pushUrl.isEmpty()) {
            push(json);
        }
    }

    private void writeFile(String json) {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Path file = dataDirectory.resolve("status.json");
            Path tmp = dataDirectory.resolve("status.json.tmp");
            Files.writeString(tmp, json);
            try {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            logger.warn("Failed to write status.json: {}", e.getMessage());
        }
    }

    private void push(String json) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(pushUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (!pushToken.isEmpty()) {
                builder.header("Authorization", "Bearer " + pushToken);
            }
            http.sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        logger.debug("status.json push failed: {}", ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            logger.debug("status.json push not sent: {}", e.getMessage());
        }
    }

    private JsonArray toArray(List<Row> rows, boolean online, Instant now) {
        JsonArray arr = new JsonArray();
        for (Row row : rows) {
            if (row == null || row.uuid == null) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("uuid", row.uuid.toString());
            o.addProperty("name", row.name == null ? "Unknown" : row.name);
            o.addProperty("online", online);
            o.addProperty("lifetimeSeconds", Math.max(0, row.lifetimeSeconds));
            if (online) {
                Instant since = row.onlineSince != null ? row.onlineSince : now;
                long seconds = Math.max(0, Duration.between(since, now).getSeconds());
                o.addProperty("sessionSeconds", seconds);
                o.addProperty("time", formatDuration(seconds));
            } else {
                Instant last = row.lastSeen != null ? row.lastSeen : now;
                long seconds = Math.max(0, Duration.between(last, now).getSeconds());
                o.addProperty("offlineSeconds", seconds);
                o.addProperty("time", formatDuration(seconds));
            }
            arr.add(o);
        }
        return arr;
    }

    static String formatDuration(long totalSeconds) {
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return totalSeconds + "s";
    }

    public static final class Row {
        public final UUID uuid;
        public final String name;
        public final Instant onlineSince;
        public final Instant lastSeen;
        public final long lifetimeSeconds;

        public Row(UUID uuid, String name, Instant onlineSince, Instant lastSeen, long lifetimeSeconds) {
            this.uuid = uuid;
            this.name = name;
            this.onlineSince = onlineSince;
            this.lastSeen = lastSeen;
            this.lifetimeSeconds = lifetimeSeconds;
        }
    }
}
