# SwitchLab Repo Structure

A Gradle (Kotlin DSL) multi-module monorepo. Every module except `app` has no Spring dependency. This keeps the core testable without a container, and lets the switch and API split into separate services later.

```
SwitchLab/
├── LICENSE                         # AGPL-3.0 (jPOS route, decision 1)
├── README.md                       # quick start: docker compose up, client echo
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml       # jPOS, Spring Boot, Testcontainers, JUnit... pinned here
├── docker-compose.yml              # app + postgres + caddy (prod-like)
├── docker-compose.dev.yml          # postgres only, for running the app from the IDE
├── deploy/
│   ├── Caddyfile
│   ├── hetzner.md                  # provisioning steps, firewall ports 443/5000/5001
│   └── backup.sh                   # nightly pg_dump -> object storage
├── .github/workflows/
│   ├── ci.yml                      # build, unit, integration (Testcontainers), dashboard tests
│   └── deploy.yml                  # image -> GHCR -> ssh compose pull/up (manual trigger)
│
├── iso/                            # protocol layer: dialects, packagers, channels, masking
│   └── src/main/java/io/switchlab/iso/
│       ├── dialect/                # DialectProfile, DialectRegistry, HeaderSpec
│       ├── channel/                # channel factory from HeaderSpec (2-byte, ASCII len, TPDU)
│       ├── mask/                   # Masker (PAN/track/PIN byte-range masking), PanHasher
│       └── json/                   # ISOMsg <-> JSON for inspector + API
│   └── src/main/resources/dialects/
│       ├── generic87/iso87ascii.xml + profile.json
│       └── nibss/nibss-pos.xml + profile.json
│
├── rules/                          # pure rules engine (no jPOS, no Spring)
│   └── src/main/java/io/switchlab/rules/   # Condition, Action, Outcome, Compiler, TxnFacts
│   └── src/main/resources/packs/           # happy-path.json, insufficient-funds.json, ...
│
├── hsm/                            # software security module wrapper (test LMK, keys, PIN, MAC)
│   └── src/main/java/io/switchlab/hsm/
│
├── switch-core/                    # jPOS pipeline: listeners, TM participants, Q2 config gen
│   └── src/main/java/io/switchlab/sw/
│       ├── q2/                     # Q2Launcher, deploy XML generation from listener config
│       ├── listener/               # CaptureListener
│       ├── tm/                     # CtxKeys, MtiGroupSelector
│       ├── participant/            # TerminalResolver, TestDataGuard, ..., TxnRecorder
│       ├── dispatch/               # ResponseDispatcher, MalformedWriter
│       └── port/                   # interfaces the core needs: TerminalLookup, TxnStore,
│                                   # RuleSetLookup, KeyStore, EventPublisher
│
├── domain/                         # persistence + services implementing switch-core ports
│   └── src/main/java/io/switchlab/domain/  # tenants, terminals, cards, keys, rules, txns (jOOQ or JDBC)
│   └── src/main/resources/db/migration/    # Flyway V1__baseline.sql, ...
│
├── app/                            # Spring Boot: wiring, REST API, SSE, auth, static dashboard
│   └── src/main/java/io/switchlab/app/
│       ├── config/  api/  feed/  security/
│   └── src/main/resources/application.yml
│
├── client/                         # sample POS: library + CLI (picocli)  -> switchlab-client
│   └── src/main/java/io/switchlab/client/  # PosClient, MessageFactory per dialect, Cli
│
├── testkit/                        # JUnit 5 extension for customers' CI (M6) + our own ITs
│
├── integration-tests/              # end-to-end: real app + Postgres (Testcontainers) + client
│   └── src/test/java/io/switchlab/it/
│       ├── PurchaseTimeoutReversalIT.java
│       └── ...
│   └── src/test/resources/fixtures/        # golden hex messages per dialect
│
├── dashboard/                      # React + TypeScript (Vite), built into app's static resources
│   ├── src/{api,pages,components,inspector}/
│   ├── package.json
│   └── e2e/                        # Playwright smoke tests
│
└── docs/                           # architecture.md, milestones.md, dialects/, rules-reference.md
```

Modules are added in the milestone that first needs them: M0 has `iso`, `switch-core`, `app`, `client` and `integration-tests`. `rules` arrives in M2, `domain` with M1 persistence, `hsm` in M5, `testkit` in M6 and `dashboard` in M4.

**Dependency direction:** `iso` and `rules` are leaves. `hsm` depends on `iso`. `switch-core` depends on `iso`, `rules` and `hsm`, and defines ports. `domain` implements those ports. `app` wires everything together. `client` depends only on `iso`. Nothing depends on `app`.

**Conventions:**
- **Tests:** only tests that protect real behaviour or catch edge cases that might be missed. No tests written for their own sake (see testing-strategy.md).
- **Comments:** none that restate the code. A comment exists only to explain a non-obvious why, such as a spec quirk. A `CLAUDE.md` at the repo root records both rules for future sessions.
- Branches: `m1-4-response-builder`.
- Commits: conventional style (`feat(switch-core): ...`) carrying the milestone task ID.
- One PR per task where practical.
- The planning docs in `/mnt/project-files/switchlab/` move into `docs/` in the first commit.
