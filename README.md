# GhostTab-Website

Working copy of [wilderop/GhostTab](https://github.com/wilderop/GhostTab) for website live-tablist, total-playtime, and JSON export.

**The original repo is not modified.** All website-related plugin changes go here.

Current plugin version in this repo: **1.9-website**

---

# GhostTab

A Velocity proxy plugin that enhances the tab list with:

- **Online players** showing how long they have been online (e.g. `Steve 1h 23m`)
- **Recently offline players ("ghosts")** showing how long they have been offline (e.g. `Alex offline 2h 15m`)

Offline players remain visible for a configurable window (default **24 hours**).

## Features

- Live updating times (default every 30 seconds)
- Configurable header / footer with placeholders `{online}`, `{ghosts}`, `{total_hours}`, `{playtime_window}`
- Footer can show total hours played by all players in a rolling window (default 12 hours)
- Configurable display formats using MiniMessage
- Persists recent player data and playtime sessions across proxy restarts
- Online players sorted by session length (longest online at the top)
- Offline/ghost players sorted by offline time (longest offline at the bottom)
- Caches player skins so ghosts keep their real skin after disconnect/reboot
- No dependency on backend plugins or VelocityTab
- **Lifetime playtime per player** (survives the 24h ghost window)
- **Import past playtime** from Paper/vanilla `world/stats/<uuid>.json` files

## Requirements

- Velocity 3.3+
- Java 17+

## Installation

1. Build this repo (`mvn clean package`) or wait for a release jar
2. Place `GhostTab.jar` in the Velocity `plugins/` folder (replace the current one when you are ready)
3. Restart the proxy
4. Edit `plugins/ghosttab/config.yml` and add your backend stats folders (see below)
5. Restart again so the importer can seed totals

**Important:** Disable or remove VelocityTab / Velocitab if you are using this plugin, otherwise they will conflict over the tab list.

## Lifetime playtime

Totals are stored in `plugins/ghosttab/totals.yml` and are never trimmed by the 24h ghost window.

On login/logout/save the plugin adds completed session time. On startup it can also read vanilla playtime from player stat files and keep the **higher** of stored vs file (never decreases).

Add your survival world stats path:

```yaml
stats-directories:
  - /path/to/your/paper/world/stats
import-stats-on-startup: true
```

You can point at the world folder instead of `stats/`; the plugin looks for a `stats` subdirectory.

It reads:
- `stats.minecraft:custom.minecraft:play_time`
- `stats.minecraft:custom.minecraft:play_one_minute`
- older `stat.playOneMinute`

Those values are ticks; the plugin stores seconds (`ticks / 20`).

## Building

```bash
mvn clean package
```

The shaded jar will be in `target/GhostTab.jar`.

## License

MIT
