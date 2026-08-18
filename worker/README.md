# GhostTab status Worker

Receives the outbound JSON push from GhostTab and serves it to the website.

- Plugin → `POST /status` (Bearer token)
- Website → `GET /status` (public, CORS enabled)
- Your home IP is never exposed. This is outbound-only from the proxy.

## One-time setup (copy-paste)

From this `worker` folder, on any machine with Node.js:

```bash
cd worker
npx wrangler login
npx wrangler kv namespace create STATUS
```

Copy the printed `id` into `wrangler.toml` replacing `REPLACE_WITH_KV_NAMESPACE_ID`.

```bash
npx wrangler secret put PUSH_TOKEN
npx wrangler deploy
```

Use a long random token when prompted (same value goes in GhostTab `push-token`).

Wrangler will print a URL like:

`https://ghosttab-status.<your-account>.workers.dev`

Put that in Velocity `plugins/ghosttab/config.yml`:

```yaml
export-json: true
push-url: "https://ghosttab-status.<your-account>.workers.dev/status"
push-token: "the-same-token"
```

Restart Velocity. The website will later read:

`https://ghosttab-status.<your-account>.workers.dev/status`

## Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/status` | Bearer `PUSH_TOKEN` | Plugin writes snapshot |
| GET | `/status` | none | Website reads snapshot |
| GET | `/health` | none | Uptime check |
