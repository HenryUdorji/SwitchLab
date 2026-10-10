# SwitchLab Milestone Plan

Tasks are sized at 1 to 3 days (d) for one developer. Each milestone ends with a demo you can run yourself. IDs (`M1-3`) are used in commit and PR titles.

Totals: about **M0 to M6 ≈ 66 dev-days** of engineering before the private beta. The MVP as defined in the brief (section 9) is reached at the **end of M4**, about 47 days.

A non-engineering track runs alongside: talk to 20 target users and ask for deposits (brief section 11). The useful checkpoints are the end of M2 (a demo of "force any response code") and the end of M4 (the MVP dashboard).

---

## M0: Foundation (≈ 10d)
Demo: `docker compose up` locally, then `switchlab-client echo` gets an 0810 from both listeners.

| ID | Task | Size | Done when |
|---|---|---|---|
| M0-1 | Gradle multi-module skeleton (Java 25, Gradle 9, version catalog, Spotless), AGPL-3.0 `LICENSE`, README, `CLAUDE.md` with the test and comment rules | 1d | `./gradlew build` is green |
| M0-2 | GitHub Actions CI: build, unit tests, integration tests (Testcontainers), multi-arch image to GHCR | 1d | CI passes on a PR |
| M0-3 | Spring Boot app with embedded Q2 lifecycle (start/stop with the Spring context), deploy dir generated at startup | 2d | App boots, Q2 logs that it's running, clean shutdown |
| M0-4 | Postgres + Flyway baseline: tenants, terminals, merchants, listeners | 1d | Migrations run in CI against Testcontainers |
| M0-5 | `generic87` dialect: packager XML, channel config, golden-hex pack/unpack tests | 1d | Round-trip tests pass for 0800, 0200, 0400 |
| M0-6 | `nibss` dialect (first pass): packager XML, 2-byte length channel, secondary bitmap | 2d | Round-trip on fixtures built from the spec's 0200/0420/0800 layouts. Real captures are added when you send them |
| M0-7 | Listeners from DB config, plus a minimal TM with a `netmgmt` group answering echo (generic F70=301, NIBSS 9D0000) | 1d | Echo works on :5000 and :5001 |
| M0-8 | `switchlab-client` CLI v0: `echo` command, both dialects | 1d (shared with M0-7) | CLI prints the parsed 0810 |
| M0-9 | `docker-compose.yml` (app, postgres, caddy), Hetzner VM provisioning notes, first deploy | 1d | Echo works against the public IP |

## M1: Financial flow (≈ 8d)
Demo: a 0200 purchase from the CLI is approved, and the transaction row is in the DB.

| ID | Task | Size |
|---|---|---|
| M1-1 | `CtxKeys`, `CaptureListener`, `GroupSelector` by MTI class | 1d |
| M1-2 | `TerminalResolver` with cache, MID check, unknown TID → RC 58 + `unrouted_messages` | 1d |
| M1-3 | `MessageValidator` with per-dialect mandatory-field specs for 0100/0200/0220 | 1d |
| M1-4 | `Authorizer` (fixed approve), auth code and RRN generation, `ResponseBuilder` with echo-field rules per dialect | 2d |
| M1-5 | `Masker` + `TestDataGuard` (BIN allowlist, Luhn live-card rejection, PAN HMAC) | 1d |
| M1-6 | `TxnRecorder`: transactions (monthly partitions) and transaction_messages with masked raw bytes and parsed JSON | 2d |
| M1-7 | Structured logging with MDC, Micrometer metrics, health indicator for the listeners | 1d (overlaps) |

## M2: Rules engine (≈ 10d)
Demo: a rule "amount > 50,000 returns 51" and a rule "TID X delays 30s", both created through the API, and the CLI shows the effect.

| ID | Task | Size |
|---|---|---|
| M2-1 | `rules` module: condition model, JSON (de)serialisation, compiler to predicates, table-driven edge-case tests | 3d |
| M2-2 | Actions model and `Outcome`; `RuleEvaluator` participant (terminal, then tenant, then default; first match / `stop`) | 2d |
| M2-3 | `ResponseDispatcher`: scheduled delay (non-blocking), drop, close before/after, duplicate, malformed variants, late-response status update | 2d |
| M2-4 | Field overrides (`set`/`unset`), partial approval, `seq` counters | 1d |
| M2-5 | Rule set versioning (draft → publish), cache invalidation, `rule_set_version` stored on each transaction | 1d |
| M2-6 | Minimal REST: terminals CRUD, rule sets CRUD/publish (dev-only API key auth for now) | 1d |

## M3: Reversals, duplicates, STAN/RRN (≈ 7d)
Demo: the CLI sends a 0200, the host is ruled to time out, the CLI sends the reversal (0400 generic, 0420 NIBSS), and the 0410/0430 comes back linked to the original.

| ID | Task | Size |
|---|---|---|
| M3-1 | `reversal` TM group; `OriginalMatcher` (F90 n42; TID+STAN+amount fallback for generic only) | 2d |
| M3-2 | `ReversalApplier`: idempotent repeats (0401), status transitions, test balance restore | 1d |
| M3-3 | `DuplicateDetector`: unique key and LRU, replay of the stored response versus RC 94 per dialect | 1d |
| M3-4 | 0220/0230 advice and 0420/0430 reversal advice | 1d |
| M3-5 | **Full flow integration test** (0200 → timeout → 0400/0420; see testing-strategy.md) plus the late-response variant | 2d |

## M4: Dashboard and live inspector (≈ 12d). **MVP complete**
Demo: brief section 9, steps 1 to 6, in the browser, in under 15 minutes from sign-up.

| ID | Task | Size |
|---|---|---|
| M4-1 | Auth: sign-up, login, Argon2, sessions with CSRF, tenant creation, memberships and roles | 2d |
| M4-2 | Full REST API: merchants, terminals, test cards, transactions search (STAN, RRN, masked PAN, TID, RC, date), OpenAPI spec | 2d |
| M4-3 | SSE feed (`/api/v1/feed`) on top of the EventBus, filtered by tenant and terminal | 1d |
| M4-4 | Dashboard shell (Vite, React, TS, router, API client generated from OpenAPI), login and onboarding wizard ("your host:port and TID") | 2d |
| M4-5 | Live inspector: feed list, field-by-field request/response diff, raw hex with masked ranges shaded, latency, rule that fired | 3d |
| M4-6 | Terminals and rules pages (form-based rule editor plus a JSON view, publish, history) | 2d |

## M5: Security simulation (≈ 10d)

| ID | Task | Size |
|---|---|---|
| M5-1 | `hsm` module: wrap `JCESecurityModule`, generate the per-environment test LMK, key storage and KCVs | 2d |
| M5-2 | NIBSS key download: 9A (TMK under CTMK), 9B (TSK), 9G (TPK) with key + KCV in F53, 9C parameters (F62 tags 02-08, 52) | 3d |
| M5-3 | `MacVerifier` and MAC generation: NIBSS SHA-256 (F64/F128, input confirmed from a real capture) and X9.19 | 2d |
| M5-4 | `PinVerifier`: ISO 0/1/3 PIN blocks, test card PINs, RC 55 | 2d |
| M5-5 | Keys page in the dashboard (versions, KCVs, rotate) and TLS listeners (:5400/:5401) | 1d |

## M6: API, CLI, CI integration, scenario packs (≈ 9d)

| ID | Task | Size |
|---|---|---|
| M6-1 | API keys (scoped, hashed, shown once), rate limits | 1d |
| M6-2 | CLI v1: `login`, `terminals`, `rules apply -f`, `txn tail`, `txn assert --rrn ... --rc 00` | 2d |
| M6-3 | `switchlab-testkit` JUnit 5 extension: create an ephemeral terminal, apply a scenario, assert host-side outcomes | 2d |
| M6-4 | 8 preset scenario packs and an "install pack" endpoint and UI | 2d |
| M6-5 | Export JSON/CSV and a downloadable test report (HTML) | 1d |
| M6-6 | Docs site (quick start, dialects, rules reference) | 1d |

## M7: Private beta (≈ 2 weeks of support, plus fixes)
- Free-tier caps (`QuotaGuard`, usage_daily), retention job that drops old partitions.
- Onboard 5 to 10 teams from your network; measure time to first transaction, rules created and weekly transactions.
- Backups verified with a restore drill; uptime check.

## M8: Billing and public launch
- **Licensing gate:** decide between buying the commercial jPOS licence and staying AGPL. Purchase must be complete before any closed-source or white-label offering.
- Paystack (NGN) and Stripe (USD) subscriptions, plan limits, terms of use (test-data only).
- Choose the brand and domain.
