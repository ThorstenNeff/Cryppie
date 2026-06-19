# Key-Proxy hardening (KAN-112 / KAN-115, ADR-0021)

The `:server` Alchemy/RPC key-proxy keeps the API key off the client. It is currently scoped to
**local/dev use** (the wallet talks to `http://localhost:8080`). Before exposing it on any non-local
network, the items below are **gating** — a public proxy holds a spendable key and is an abuse target.

## Done (in repo)

- [x] **Key server-side only** — read from system property `alchemyApiKey` (build-bridged from
      `~/.gradle/gradle.properties`, never committed) or `$ALCHEMY_API_KEY`; never echoed (`/healthz`
      returns only a boolean). No key in the client binary.
- [x] **No foreign-host relay** — host + scheme are fixed to `*.g.alchemy.com`; only the path is built.
- [x] **Network allow-list** — `eth-mainnet` / `base-mainnet` only.
- [x] **Tail allow-list (least-privilege, KAN-115)** — only the exact upstream sub-paths the wallet
      calls are forwarded (`assets/tokens/by-address`, `tokens/by-address`, `tokens/historical`,
      `getNFTsForOwner`); any other tail → `404`, the key never reaches it.
- [x] **Per-client rate limit** — in-memory fixed window (abuse guard).
- [x] **Fail-closed when keyless** — `503`, so the client degrades cleanly (FR-4) instead of leaking.

## Required before non-local exposure (pre-public gate)

- [x] **TLS / trusted termination** (KAN-125) — HTTPS via the **Caddy reverse proxy** (`deploy/Caddyfile`):
      Let's Encrypt cert + auto-renew, HTTP→HTTPS, HSTS. The client only ever gets the `https://` prod URL.
- [x] **Rate-limit on the real client IP** (KAN-125, ADR-0022 B) — behind the reverse proxy the limiter
      reads `X-Forwarded-For` (`PROXY_TRUSTED_HOPS=1`); Caddy overwrites XFF with the socket peer (anti-spoof).
      Window-map is now **evicted** (bounded memory).
- [x] **Request-size / body limits** (KAN-125) — server caps at `PROXY_MAX_BODY_BYTES` (default 256 KiB) → `413`;
      Caddy enforces a hard edge cap too. Upstream timeouts already set on the forwarding client.
- [x] **Method allow-list for JSON-RPC** (KAN-125) — `/alchemy/rpc` forwards only the wallet's read +
      broadcast methods (`ProxyConfig.allowedRpcMethods`); anything else → `403`, fail-closed.
- [x] **Port isolation** (KAN-125, ADR-0022 D) — Ktor binds `127.0.0.1:8080` (`PROXY_BIND_HOST`); only Caddy
      (443) is public.
- [ ] **Client authentication** — *deferred* (not GA-gating per KAN-125): require a client token / app-attest
      so only the wallet app can spend the key. Until then, the tail/method/network allow-lists + rate-limit
      bound abuse.
- [ ] **Upstream quota / budget caps** — *follow-up*: global spend cap + alerting at the Alchemy dashboard.
- [ ] **Distributed rate limiting** — N/A for the single-host GA deployment (ADR-0022); needed only if >1 instance.
- [ ] **Observability** — `/healthz` for uptime monitoring + Caddy/launchd access logs (no key, no upstream URL);
      structured metrics + abuse alerting = follow-up.
- [ ] **CORS** — N/A (native wallet clients, no browser origin); lock down if a web client is ever added.

## Production deployment runbook (ADR-0022, Mac Mini @ Oakhost)

Artifacts in `server/deploy/`: `Caddyfile` (TLS reverse proxy), `com.tneff.cyppie.keyproxy.plist` (launchd).

1. **Build:** `./gradlew :server:shadowJar` → `server/build/libs/server-all.jar` → copy to `/opt/cyppie/key-proxy.jar`.
2. **Key (never in repo):** put the real `ALCHEMY_API_KEY` into the **host** copy of the plist's
   `EnvironmentVariables` (replace `__SET_REAL_KEY_ON_HOST__`). The committed template never holds it.
3. **Service:** install the plist to `/Library/LaunchDaemons/`, then
   `sudo launchctl bootstrap system /Library/LaunchDaemons/com.tneff.cyppie.keyproxy.plist` (RunAtLoad + KeepAlive
   = boot-persist + auto-restart). It binds `127.0.0.1:8080`.
4. **TLS / public edge:** point a DNS A-record at the host; open **80 + 443** only (Ktor's 8080 stays local);
   `caddy run --config /opt/cyppie/Caddyfile` (after setting the real domain) → cert auto-issued + renewed.
5. **Client:** ship `AlchemyProxyConfig.proxyBaseUrl = https://<that-domain>` (prod build-config) instead of localhost.
6. **Verify:** `curl https://<domain>/healthz` → `{"status":"ok","keyConfigured":true}`; a non-allow-listed RPC
   method → `403`; an oversized body → `413`.

- **Cert renewal:** automatic (Caddy); monitor `/healthz` + cert expiry; alert on down (FR-4 degrade keeps clients
  from crashing while the proxy is unreachable — they show `Approximate`/stale).
- **Key rotation:** mint a new Alchemy key → update the host plist env → `sudo launchctl kickstart -k system/com.tneff.cyppie.keyproxy` → revoke the old key. The key never touches the repo or client.
- **Restart / incident:** `launchctl kickstart -k …` to restart; check `/var/log/cyppie/*.log` (never logs the key
  or upstream URL); a key-less/misconfigured proxy answers `503` (clients degrade, not crash).
- **Network hardening:** SSH key-only + non-default port; firewall to 22/80/443; keep macOS + Caddy + JRE patched.
- **Scaling exit:** single host = single point of failure (accepted for GA volume, ADR-0022). Cloud migration is a
  pure `proxyBaseUrl` change when HA/geo demands it.

Owner: Dev-2 (`:server`). References: KAN-112, KAN-115, **KAN-125**, ADR-0021, **ADR-0022**.
