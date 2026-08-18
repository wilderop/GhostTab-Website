# GhostTab-Website

Working copy of [wilderop/GhostTab](https://github.com/wilderop/GhostTab) for website live-tablist, total-playtime, and JSON export.

**The original repo is not modified.** All website-related plugin changes go here.

Current plugin version in this repo: **1.10-website**

## Cloudflare Worker (step 4)

The Worker lives in [`worker/`](worker/). It stores the latest tab snapshot and serves it to the public site.

Plugin → `POST /status` (secret token) → KV → website `GET /status`

Your home IP is never published. Setup commands are in [`worker/README.md`](worker/README.md).

After deploy, set this on the proxy:

```yaml
export-json: true
push-url: "https://ghosttab-status.<your-account>.workers.dev/status"
push-token: "the-same-token"
```

## Lifetime playtime

Totals are stored in `plugins/ghosttab/totals.yml`.

```yaml
stats-directories:
  - /path/to/your/paper/world/stats
import-stats-on-startup: true
```

## Building

```bash
mvn clean package
```

## License

MIT
