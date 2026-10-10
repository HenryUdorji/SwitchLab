# SwitchLab

A hosted, configurable ISO 8583 test host for POS and payment-terminal developers. Point a terminal at it, control how it responds (approvals, declines, delays, dropped responses, reversals, key exchange), and inspect every message field by field.

Test environment only: no real cards, no real keys, no money movement.

Built on [jPOS](https://jpos.org). Licensed under the GNU Affero General Public License v3.0, see [LICENSE](LICENSE).

## Quick start

Requires Java 25 and Docker.

```sh
./gradlew build                       # unit + integration tests (Testcontainers)
docker compose -f docker-compose.dev.yml up -d
./gradlew :app:bootRun                # listeners: generic87 on :5000, nibss on :5001
./gradlew :client:installDist
client/build/install/switchlab-client/bin/switchlab-client echo --port 5001 --dialect nibss
```

Planning documents: [architecture](docs/architecture.md), [milestones](docs/milestones.md), [repo structure](docs/repo-structure.md), [testing strategy](docs/testing-strategy.md).
