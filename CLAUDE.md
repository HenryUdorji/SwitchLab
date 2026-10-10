# SwitchLab

Hosted ISO 8583 test host built on jPOS 3 (Q2 + TransactionManager) embedded in Spring Boot. Planning docs live in `docs/`.

## Build

- Java 25 (jPOS 3 requires 24+). `./gradlew build` runs formatting checks, unit tests and the Testcontainers integration tests (needs Docker).
- `./gradlew spotlessApply` before committing.

## Rules

- **Tests:** write only tests that protect real behaviour or catch an edge case that would otherwise be missed (protocol layout, money/state transitions, safety guarantees, socket behaviour). No tests for getters, wiring, framework behaviour or trivial mapping, no mocks of our own classes, no tests written to raise coverage.
- **Comments:** none that restate the code. A comment is only for a non-obvious *why*, such as a spec quirk.
- **NIBSS spec:** the specification document is confidential and must never be committed. Only derived artefacts (packager XML, our own fixtures) belong in the repo.
- Test environment only: never real PANs or keys. Mask PANs in storage, logs and UI.
