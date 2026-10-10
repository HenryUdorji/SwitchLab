# SwitchLab Architecture

Status: **approved 2026-10-10**.

## 1. Decisions this plan is built on

| # | Decision | Choice | Notes |
|---|---|---|---|
| 1 | jPOS licensing | **jPOS under AGPL-3.0 now; buy a commercial licence before paid launch (M8)** | The SwitchLab repo is public under AGPL-3.0 from first deploy. The dashboard footer links to the source, which covers the AGPL network-use clause. If we want closed source later, the commercial licence must land first. |
| 2 | Tenancy | **Shared listener per dialect, routed by TID (field 41)** | A dedicated port per tenant is a later paid option. See section 4. |
| 3 | Dialects | **Generic ISO 8583:1987 plus a NIBSS POS profile** | The NIBSS profile follows the NIBSS POS ↔ CTMS spec you shared (kept in project files only, never committed). See section 7. |
| 4 | Hosting | **Oracle Cloud Always Free ARM VM running Docker Compose** (chosen 2026-10-10) | Render can't be used. Its public services accept HTTP, HTTPS and WebSocket only, so a POS can't open a raw TCP socket to it. Its free tier also sleeps after 15 minutes idle, and its free Postgres is deleted after 30 days. |
| 5 | Stack | **Spring Boot 4 on Java 25 (jPOS 3 requires Java 24+, and 25 is the current LTS) with jPOS Q2 embedded in the same JVM; React + TypeScript dashboard; SSE live feed; bundled Java CLI POS client** | One deployable keeps v1 simple. The module boundaries allow the switch and API to split later. |
| 6 | Brand | "SwitchLab" as working name | This decision doesn't block anything. |

## 2. System overview

```
                 POS terminals / test clients (raw TCP, optional TLS)
                         |                         |
                   :5000 generic-87          :5001 nibss-pos
                         |                         |
+------------------------v-------------------------v---------------------------+
|  switchlab-app (one JVM, Spring Boot + embedded jPOS Q2)                      |
|                                                                              |
|  [ISOServer: generic87]   [ISOServer: nibss]       <- channel + packager per  |
|          \                    /                       dialect profile         |
|           ISORequestListener (capture raw bytes, timestamps, connection id)   |
|                         |                                                    |
|                 TransactionManager "txnmgr" (per-MTI participant groups)     |
|   TerminalResolver > TestDataGuard > Validator > DuplicateDetector >         |
|   MacVerifier > PinVerifier > RuleEvaluator > Authorizer > ResponseBuilder > |
|   ResponseDispatcher (delay/drop/malformed/dup/close) > TxnRecorder           |
|                         |                                                    |
|  Domain services: Rules, Terminals, Keys (SoftSM), Cards, Txn store          |
|                         |                     \                              |
|  REST API (/api/v1) + SSE (/api/v1/feed)      in-process EventBus -> SSE     |
|  Static dashboard (React build)                                              |
+------------------------|-----------------------------------------------------+
                         |
                   PostgreSQL 16
```

Edge: **Caddy** terminates HTTPS for the dashboard and API and handles automatic certificates. The TCP ports go straight to the JVM. TLS on the ISO ports, when enabled, is handled by jPOS itself using a JSSE server socket factory.

### Components

| Component | Responsibility |
|---|---|
| **Listener layer** (jPOS `ISOServer` deployed through Q2 XML generated from DB config) | One listener per *dialect profile*. Each profile pairs a channel (header and length format) with a packager. Sets per-connection limits and IP filtering. |
| **Capture listener** (`ISORequestListener`) | Records the raw request bytes, a monotonic receive time and the connection id. Puts a `Context` on the TM queue. Does no business logic. |
| **TransactionManager** | The processing pipeline (section 5). It is configured per MTI class with `GroupSelector`: `auth`, `financial`, `advice`, `reversal`, `recon`, `netmgmt`. |
| **Rules engine** (plain Java module, no jPOS dependency) | Evaluates ordered, versioned rule sets against a `TxnFacts` view of the request and returns an `Outcome`. It is pure and unit-testable. |
| **Security module** | Wraps jPOS `JCESecurityModule`, the jPOS software SM, using a **test-only LMK** that is generated per environment and never imported. Handles key generation and encryption under CTMK/TMK, PIN block translation and verification (ISO 0/1/3), MAC generation and verification. |
| **Domain services** | Tenants, users, terminals, merchants, test cards, keys, rule sets, transactions. Shared by the TM participants and the REST API. |
| **REST API** | Spring MVC under `/api/v1`. Uses session cookies for the dashboard and API keys (`Authorization: Bearer sl_...`) for the CLI and CI. |
| **Live feed** | An in-process `EventBus` publishes `TxnRecorded` events. An SSE endpoint fans them out filtered by tenant, and can be narrowed further by terminal. If we ever run more than one JVM, this becomes Postgres `LISTEN/NOTIFY`. |
| **Dashboard** | A React + TypeScript (Vite) app built to static files and served by Spring. Its pages are terminals, rules, the live inspector, history and keys. |
| **Sample POS client** (`switchlab-client`) | A Java library and CLI that build and send dialect-correct messages (0800 key download, 0200, 0400/0420, ...). It powers the docs, the onboarding check and our own integration tests. |

## 3. Data model (PostgreSQL)

All tenant-owned tables carry `tenant_id`. Every repository query is scoped by it, and the scoping is enforced in one data-access helper rather than in each caller. Postgres row-level security is a later hardening step.

```
tenants(id, name, plan, status, created_at)
users(id, email, password_hash, created_at)
memberships(user_id, tenant_id, role[owner|admin|developer|viewer])
api_keys(id, tenant_id, name, prefix, hash, scopes, last_used_at, revoked_at)

dialect_profiles(id, code['generic87'|'nibss'], packager_xml, channel_type,
                 header_spec jsonb, mandatory_fields jsonb, version, builtin bool)
listeners(id, dialect_profile_id, port, tls bool, max_connections, enabled)

merchants(id, tenant_id, mid char(15), name, mcc, currency, country)
terminals(id, tenant_id, merchant_id, tid char(8) UNIQUE,   -- globally unique: routing key
          dialect_profile_id, status, mac_mode[none|verify|verify_and_generate],
          pin_mode[none|verify], rule_set_id NULL, ip_allowlist cidr[] NULL,
          last_seen_at, created_at)
test_cards(id, tenant_id, pan_masked, pan_hmac, bin, expiry, test_pin_ref,
           balance_minor, currency, status)          -- PAN never stored in clear
keys(id, tenant_id, terminal_id NULL, type[CTMK|TMK|TSK|TPK|ZMK|ZPK],
     scheme, value_under_lmk, kcv, version, status[active|retired], created_at)

rule_sets(id, tenant_id, name, scope[tenant|terminal], version, active bool,
          published_at, created_by)
rules(id, rule_set_id, priority int, name, enabled,
      conditions jsonb, actions jsonb, stop bool default true)
rule_counters(tenant_id, rule_id, key text, count bigint, window_start)  -- for "nth txn" rules

transactions(id uuid, tenant_id, terminal_id, listener_id, connection_id,
             mti, proc_code, stan, rrn, txn_datetime_f7, local_dt,
             pan_masked, pan_hmac, amount_minor, currency, rc, auth_code,
             status[approved|declined|no_response|late_response|reversed|failed_parse],
             rule_id NULL, rule_set_version, outcome jsonb,
             original_txn_id NULL,        -- 0400/0420 -> original 0200
             received_at, responded_at, latency_ms)
        PARTITION BY RANGE (received_at)  -- monthly partitions, dropped per plan retention
transaction_messages(txn_id, direction[in|out], seq, raw_masked bytea,
                     masked_ranges int4range[], parsed jsonb, sent_at)
unrouted_messages(id, listener_id, remote_ip, raw_masked bytea, reason, received_at)
connection_events(id, listener_id, connection_id, remote_ip, event, at)
audit_log(id, tenant_id, actor, action, target, detail jsonb, at)
usage_daily(tenant_id, day, txn_count)    -- free-tier caps
```

**PAN and sensitive data handling.** Before anything is persisted, a `Masker` overwrites the bytes of F2, F35 and F45, and the PIN block in F52, inside a copy of the raw message. Length is preserved, so offsets in the hex view still line up, and the masked byte ranges are stored so the UI can shade them. Search by PAN goes through `pan_hmac`, an HMAC-SHA256 under a per-environment secret. The clear PAN exists only in memory, inside the TM context, for the life of one transaction.

## 4. Tenancy approach

- Each dialect has **one shared listener**: `:5000` for generic 1987 and `:5001` for NIBSS. TLS twins come later (`:5400`/`:5401`).
- **Routing key: TID (field 41).** TIDs are globally unique across SwitchLab. When a tenant registers a terminal, we either allocate a TID or accept theirs if it is free. Field 42 (MID) is checked against the terminal's merchant, and a mismatch returns RC `03`.
- **Unknown TID:** the host replies with RC `58` (configurable per dialect) and writes the message to `unrouted_messages` with the remote IP. A tenant can see unrouted attempts from IPs on its allowlist. This helps with "my POS is pointing at the wrong TID".
- **Isolation:** each POS connection is its own socket, so connection-level actions (delay, drop, close, malformed) affect only that terminal. Optional per-terminal IP allowlists and per-tenant connection and TPS caps stop one tenant from starving the others.
- **Per-tenant port (later, paid):** the same Q2 template generates a dedicated listener bound to that tenant. Routing then skips the TID lookup but keeps the MID check.

## 5. 0200 purchase through the TransactionManager

Context keys are constants in `CtxKeys`. Each participant is small, implements `TransactionParticipant` (or `AbortParticipant` where it must also run on abort), and is unit-tested with a hand-built `Context`.

```
POS --0200--> ISOServer(nibss) --> CaptureListener
   ctx: REQUEST(ISOMsg), RAW_IN(bytes), RECEIVED_AT, CONNECTION(ISOSource), LISTENER
   --> TM queue "txnmgr"; GroupSelector picks group by MTI: 0200 -> "financial"
```

| # | Participant | Does | On failure |
|---|---|---|---|
| 1 | `TerminalResolver` | Loads the terminal by F41 from a cache. Checks F42, terminal status and the IP allowlist, and puts `TERMINAL` and `TENANT` in the context. | RC `58`/`03`. Jumps to `ResponseBuilder` (RC set, "short-circuit" flag). |
| 2 | `QuotaGuard` | Checks the daily transaction cap and TPS cap for the tenant's plan. | RC `91`, with a message in the inspector. |
| 3 | `TestDataGuard` | Extracts the PAN from F2 or F35. Checks it against the test BIN ranges allowed for the tenant. A Luhn-valid PAN outside those ranges is **rejected** with RC `14` and flagged `LIVE_CARD_SUSPECTED`. Builds the masked PAN and its HMAC. | RC `14` |
| 4 | `MessageValidator` | Checks mandatory fields for the MTI under the dialect profile, field formats, and that the processing code and currency are supported. | RC `30` (format error) |
| 5 | `DuplicateDetector` | Key: `TID + STAN (F11) + F7 + F37 + amount`, held in a 24h window (DB unique index plus an in-memory LRU). If the original already got a response, it **replays the stored response**. Otherwise RC `94`, depending on the dialect setting. | RC `94` or replay |
| 6 | `MacVerifier` | When `mac_mode != none`: verifies the MAC in F64/F128 under the terminal's TSK. NIBSS uses a SHA-256 hash MAC; the generic profile uses ANSI X9.19. | RC `63` security violation (NIBSS) / `96` (generic), dialect-configured |
| 7 | `PinVerifier` | When F52 is present and `pin_mode=verify`: translates the PIN block from TPK to LMK and compares it with the test card's PIN. | RC `55` |
| 8 | `RuleEvaluator` | Builds `TxnFacts` (MTI, PAN/BIN, amount, proc code, TID, MID, currency, any field, the nth-transaction count, time). Evaluates the terminal's rule set, then the tenant's, then the built-in default. The first match wins unless the rule has `stop=false`. Writes `OUTCOME` and `RULE_ID` to the context. | n/a |
| 9 | `Authorizer` | Runs if no rule set an RC. Uses the test card if one is registered: balance check (RC `51`), expiry (RC `54`), status. Otherwise approves. Generates an auth code for F38 and an RRN for F37 if it's missing. Debits the test balance on approval. | Sets RC |
| 10 | `ResponseBuilder` | `ISOMsg resp = req.clone(echoFields)`, sets the MTI to `+10` and F39. Applies `OUTCOME.fieldOverrides` such as a balance in F54 or EMV data in F55. Generates the MAC if the dialect requires it. Packs with the listener's packager. | RC `96` if packing fails |
| 11 | `ResponseDispatcher` | Applies the connection-level parts of `OUTCOME`. `delay(ms)` schedules the send on a `ScheduledExecutorService`, so **TM threads never sleep**. `drop` sends nothing. `malformed` corrupts the bitmap or length per the chosen variant. `duplicate` sends twice. `close` closes the socket before or after the send. Records `SENT_AT` or `NOT_SENT`. | n/a |
| 12 | `TxnRecorder` (`AbortParticipant`, also runs on abort) | Persists `transactions` and `transaction_messages` with the masked raw bytes and parsed JSON, increments usage, and publishes `TxnRecorded` on the EventBus. The live feed shows it within about 100 ms. | Logged, never blocks the response |

Notes:
- If a delayed send turns out "late" (the terminal has already given up), the `ResponseDispatcher` callback updates the transaction status to `late_response` and publishes a second event. A later reversal (0400, or 0420 in NIBSS) can then be shown as "reversal after late response".
- Rule evaluation is pure, so the dashboard can offer a **"test this rule"** dry run that runs participants 1 to 8 against a pasted hex message without touching a socket.

### 0400 / 0420 reversal group

`TerminalResolver > MessageValidator > OriginalMatcher > ReversalApplier > RuleEvaluator > ResponseBuilder > ResponseDispatcher > TxnRecorder`

- **Which MTI:** generic uses 0400/0401 → 0410. **NIBSS has no 0400.** Reversals are 0420/0421 reversal advices answered with 0430, and they must carry F56 (reason code), F90 and F95.
- `OriginalMatcher` matches on F90, which in NIBSS is n42: original MTI (4), STAN (6), F7 (10), acquirer ID (11) and forwarding ID (11). F90 is mandatory in NIBSS. For generic only, a missing F90 falls back to `tid + F11 + amount` within 24h. It records `original_txn_id`, or `ORIGINAL_NOT_FOUND`.
- `ReversalApplier` is idempotent. A repeat (0401/0421) for an already-reversed original returns the same response. It restores the test card balance and marks the original `reversed`.
- RC when the original isn't found: `25` in generic, `00` in NIBSS (configurable, because real hosts differ here).
- Rules apply to reversals too, so "reversal gets no response" can be tested.

### 0800 network management (NIBSS key download)

The `netmgmt` group dispatches on the processing code (F3). These codes are confirmed by the NIBSS spec:
- `9A0000`: TMK download. The TMK is encrypted under the test CTMK and returned in F53 as the encrypted key (8, 16 or 24 bytes) followed by a 3-byte KCV.
- `9B0000`: TSK (session key) under TMK
- `9G0000`: TPK (PIN key) under TMK
- `9C0000`: parameter download. The F62 response tags (tag + LLL + data) are 02 CTMS date-time, 03 MID, 04 timeout in seconds, 05 currency, 06 country, 07 call-home hours, 08 MCC and 52 merchant name and location.
- Later: 9E CA public keys and 9F EMV AIDs (F63, `~`-separated tagged blocks), 9H daily report, 9I/9J DUKPT IPEK, 9K DCC, and 4B to 4E biller/VAS downloads
- `9D0000`: call home / echo
- generic `0800` with F70 = `301`: echo; `101`/`161`: key change

Every key download writes a new `keys` version and retires the previous one, so the dashboard shows the key history and KCVs.

## 6. Rules engine

```jsonc
// rules.conditions: all must hold (AND); "any" nesting allowed
{ "all": [
    { "field": "mti",       "op": "in",  "value": ["0200", "0100"] },
    { "field": "amount",    "op": "gt",  "value": 5000000 },          // minor units
    { "field": "bin",       "op": "eq",  "value": "539983" },
    { "field": "tid",       "op": "eq",  "value": "2033ABCD" },
    { "field": "f:4",       "op": "matches", "value": "^0000.*" },   // raw field access
    { "field": "seq",       "op": "every", "value": 3 },             // every 3rd matching txn
    { "field": "time",      "op": "between", "value": ["09:00","17:00"], "tz": "Africa/Lagos" }
]}
// rules.actions: applied in this order
{ "rc": "51",
  "delayMs": 30000,
  "dispatch": "send | drop | malformed:bad_bitmap | malformed:bad_length | duplicate | close_before | close_after",
  "partialAmount": 2500000,
  "set": { "54": "1001566C000000500000", "55": "910A..." },
  "unset": ["38"] }
```

- **Versioning:** editing a rule set creates a draft. Publishing bumps `version`, and every transaction stores the `rule_set_version` it ran under, so history stays explainable.
- **Caching:** the published rule set is compiled to predicates and cached per terminal. Publishing invalidates the cache in-process (one JVM).
- **Preset packs** are JSON files in `rules/src/main/resources/packs/`. "Install pack" copies one into a tenant rule set, after which it is ordinary editable rules. The packs are happy path, insufficient funds, host timeout, late response then reversal, duplicate, invalid PIN, MAC failure and network flap.

## 7. Dialect profiles

| | generic87 | nibss |
|---|---|---|
| Packager | jPOS `GenericPackager` with `iso87ascii.xml` | `nibss-pos.xml`: ASCII fields, ASCII-hex bitmap (16 or 32 chars), F62 LLLVAR ..999, F63 LLLLVAR ..9999, F90 n42, F123 POS data code |
| Channel | `NACChannel`-style 2-byte binary length (configurable: ASCII 4-length `ASCIIChannel`, optional TPDU) | 2-byte binary length header, no TPDU |
| MAC | ANSI X9.19 under TSK (optional) | SHA-256 hash seeded with the clear TSK, 64 hex chars. Goes in F128 when any secondary field is present (always true for 0200, which needs F123), otherwise F64 |
| Key download | F70-based 0800 | 0800 with F3 `9A/9B/9G/9C/9D` |
| Messages | 0100, 0200, 0220, 0400, 0420, 0500, 0800 | 0100, 0200, 0220, 0420/0421, 0800 (no 0400, no 0500) |
| Field 62/63 | free | F62 tag+LLL TLVs (parameters, call-home, VAS); F63 tag+LLL blocks separated by `~` (EMV AIDs, CA keys) |

**Source:** the NIBSS POS ↔ CTMS interface specification. It is confidential and is **not** in this repository; only derived artefacts (the packager XML, our own fixtures) are. The same document's NCS host-to-host interface (Postilion-style F127) is relevant only to a later upstream connector.

**Still open (needed by M5, not before):** the spec says the hash is SHA-256 "seeded" with the TSK but doesn't define the exact input (for example, clear session key bytes followed by the message bytes up to the hash field, uppercase hex). One real 0200 hex capture with its test TSK from your POS code settles it. Captures also become the golden contract fixtures. DUKPT (BDK/IPEK) is in the spec but deferred until after the MVP.

Built-in dialects are defined in code (`io.switchlab.iso.Dialect`) with their packager XML in the jar. The `dialect_profiles` table arrives with custom-dialect import; until then `listeners.dialect` holds the dialect code. A tenant can later import a custom one, which is a paid feature. The built-in ones ship in the jar and are seeded on startup.

## 8. Security posture (test-only by construction)

- **No real keys:** the LMK is generated per environment, never imported. Tenants can only generate keys or enter **test** components through the UI. A component matching published real-world test or default keys is fine; anything else is accepted only with a "test key" attestation and is logged.
- **No real cards:** test BIN allowlist per tenant (defaults: common test BINs plus a `SwitchLab` BIN range). A Luhn-valid PAN outside the allowlist is rejected and flagged. Masked in storage, UI and logs.
- **Public TCP:** per-IP connection caps, idle timeout, max message size, plus optional TLS and IP allowlists. Unknown TIDs never reveal tenant data.
- **Web:** Argon2 password hashes, HTTP-only session cookie with CSRF protection, API keys stored hashed and shown once, role checks per endpoint, and an audit log for config changes.

## 9. Deployment

**Oracle Cloud Always Free** layout:
- An Ampere A1 VM (the free allowance is up to 4 OCPU and 24 GB RAM), Ubuntu 24.04, Docker Compose. Region: Johannesburg (`af-johannesburg-1`) if it has free ARM capacity at sign-up, otherwise the nearest region that does. The home region is fixed at sign-up.
- `docker-compose.yml` services: `app` (multi-arch image, `eclipse-temurin:25-jre`), `postgres:16`, `caddy:2`.
- Ports: `443` (dashboard and API via Caddy), `5000`, `5001` (ISO TCP), and later `5400`, `5401` (ISO over TLS). Open them in the OCI security list as well as the host firewall.
- Backups: nightly `pg_dump` to OCI Object Storage (part of the free allowance) with 7 dailies kept.
- Deploys: GitHub Actions builds the image and pushes it to GHCR, then SSHes in and runs `docker compose pull && up -d`.
- Risk: free ARM capacity is sometimes unavailable at sign-up, and Oracle can reclaim idle Always Free instances. Mitigation: the same compose file runs unchanged on a $5 Hetzner or DigitalOcean VPS.

## 10. Observability

- Logback JSON logs with `tenant`, `tid`, `stan`, `rrn` and `txn_id` in the MDC. Masked PANs only.
- Micrometer and Prometheus at `/actuator/prometheus`: TPS per listener, latency histogram, RC distribution, active connections, TM queue depth. Grafana Cloud free tier or a local Grafana when needed.
- `/actuator/health` includes the listener status.

## 11. Explicitly deferred

Billing, upstream proxying, white-label, load-test mode, settlement files, multi-region, and splitting the switch and API into separate services. The module layout keeps each of these possible without rewrites.
