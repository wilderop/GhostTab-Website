# GhostTab-Website

Working copy of [wilderop/GhostTab](https://github.com/wilderop/GhostTab) for website live-tablist, total-playtime, and JSON export.

**The original repo is not modified.** All website-related plugin changes go here.

Current plugin version in this repo: **1.10-website**

---

# GhostTab

A Velocity proxy plugin that enhances the tab list with online session times and recently offline ghosts.

## Lifetime playtime

Totals are stored in `plugins/ghosttab/totals.yml` and are never trimmed by the 24h ghost window.

On startup the plugin can seed totals from Paper/vanilla stats files:

```yaml
stats-directories:
  - /path/to/your/paper/world/stats
import-stats-on-startup: true
```

## JSON export (website)

Every tab refresh writes `plugins/ghosttab/status.json` in the same order as the in-game tab list.

```json
{
  "updatedAt": 1787050000,
  "onlineCount": 1,
  "ghostCount": 1,
  "totalHours": "4.2",
  "playtimeWindowHours": 12,
  "offlineWindowHours": 24,
  "online": [
    {
      "uuid": "11111111-1111-1111-1111-111111111111",
      "name": "Steve",
      "online": true,
      "lifetimeSeconds": 360000,
      "sessionSeconds": 4980,
      "time": "1h 23m"
    }
  ],
  "ghosts": [
    {
      "uuid": "22222222-2222-2222-2222-222222222222",
      "name": "Alex",
      "online": false,
      "lifetimeSeconds": 12000,
      "offlineSeconds": 8100,
      "time": "2h 15m"
    }
  ]
}
```

Optional outbound POST (no inbound port):

```yaml
export-json: true
push-url: ""
push-token: ""
```

Leave `push-url` empty until the Cloudflare Worker exists.

## Building

```bash
mvn clean package
```

## License

MIT
