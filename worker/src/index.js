/**
 * GhostTab status Worker
 * POST /status  — plugin push (Bearer token)
 * GET  /status  — public JSON for the website
 * OPTIONS       — CORS preflight
 */

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
  "Access-Control-Max-Age": "86400",
};

const KV_KEY = "ghosttab:status";

function json(body, status = 200, extra = {}) {
  return new Response(typeof body === "string" ? body : JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": status === 200 ? "public, max-age=5" : "no-store",
      ...CORS,
      ...extra,
    },
  });
}

function unauthorized() {
  return json({ error: "unauthorized" }, 401, { "Cache-Control": "no-store" });
}

function tokenFrom(request) {
  const header = request.headers.get("Authorization") || "";
  if (header.toLowerCase().startsWith("bearer ")) {
    return header.slice(7).trim();
  }
  return (request.headers.get("X-GhostTab-Token") || "").trim();
}

function looksLikeSnapshot(obj) {
  return obj && typeof obj === "object" && Array.isArray(obj.online) && Array.isArray(obj.ghosts);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS });
    }

    if (path === "/health") {
      return json({ ok: true });
    }

    if (path === "/status" && request.method === "POST") {
      const expected = env.PUSH_TOKEN;
      if (!expected) {
        return json({ error: "server missing PUSH_TOKEN" }, 500);
      }
      if (tokenFrom(request) !== expected) {
        return unauthorized();
      }

      let payload;
      try {
        payload = await request.json();
      } catch {
        return json({ error: "invalid json" }, 400);
      }
      if (!looksLikeSnapshot(payload)) {
        return json({ error: "not a ghosttab snapshot" }, 400);
      }

      const stored = {
        ...payload,
        receivedAt: Math.floor(Date.now() / 1000),
      };
      await env.STATUS.put(KV_KEY, JSON.stringify(stored));
      return json({ ok: true });
    }

    if (path === "/status" && request.method === "GET") {
      const raw = await env.STATUS.get(KV_KEY);
      if (!raw) {
        return json({
          updatedAt: 0,
          onlineCount: 0,
          ghostCount: 0,
          totalHours: "0",
          online: [],
          ghosts: [],
          stale: true,
        });
      }
      return json(raw);
    }

    return json({ error: "not found" }, 404);
  },
};
