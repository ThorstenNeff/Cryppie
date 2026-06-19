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

- [ ] **Client authentication** — the proxy must not be open to the internet. Require a client token /
      attestation (e.g. app-check / signed nonce) so only the wallet app can spend the key.
- [ ] **TLS / trusted termination** — serve over HTTPS (or behind a TLS-terminating, trusted reverse
      proxy); never ship the proxy URL as plain `http` to clients off-device.
- [ ] **Upstream quota / budget caps** — global + per-client spend caps and alerting; the rate limit is
      per-process and in-memory only (resets on restart, not shared across instances).
- [ ] **Distributed rate limiting** — move the limiter to a shared store if running >1 instance.
- [ ] **Request-size / body limits + timeouts** — cap request bodies and enforce upstream timeouts.
- [ ] **Method allow-list for JSON-RPC** — restrict `/alchemy/rpc` to the methods the wallet uses
      (`eth_*` reads + `alchemy_getAssetTransfers`); reject the rest.
- [ ] **Observability** — structured access logs (no key, no PII), metrics, and abuse alerting.
- [ ] **Key rotation** — documented rotation procedure + short-lived keys where possible.
- [ ] **CORS** — if ever reached from a browser context, lock `Access-Control-Allow-Origin` down.

Owner: Dev-2 (`:server`). References: KAN-112, KAN-115, ADR-0021.
