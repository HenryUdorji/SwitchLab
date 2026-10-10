# SwitchLab Testing Strategy

## Principle

A test is written only if it protects something that would really break or be missed: protocol correctness, money and state transitions, safety guarantees, or an edge case a real POS would hit. There are no tests for getters, wiring, framework behaviour or trivial mapping, no tests written to raise a coverage number, and no comments that restate the code. A code comment exists only to explain a non-obvious *why*, such as a spec quirk.

## What gets tested

| Area | Tests | Why it earns a test |
|---|---|---|
| **Packagers** (`iso`) | Golden hex round-trips for each dialect: one per MTI we support, plus the awkward fields (F62/F63 TLVs, F90, secondary bitmap) | A wrong field length silently breaks every terminal |
| **Hash/MAC, PIN blocks** (`hsm`) | Known-answer vectors, including one real NIBSS capture for F128 | Crypto mistakes are invisible until a terminal rejects us |
| **Rules engine** (`rules`) | Table-driven cases for operators, priority/first-match/`stop`, the `seq` counter and time windows across midnight in Africa/Lagos | This is the product's core promise and is full of off-by-one risk |
| **Dispatch** (`switch-core`, socket level) | Delay doesn't block other terminals; drop, close-before/after, duplicate, malformed | Concurrency and socket behaviour can't be trusted without running them |
| **End-to-end flows** (`integration-tests`, Testcontainers Postgres + real TCP) | The flagship flow below, plus duplicate replay and unknown TID | Proves the MVP definition itself |
| **Contract** (`fixtures/nibss`) | Real captures parse, and our responses unpack | Locks the dialect against regressions |
| **Dashboard** | One Playwright smoke test: log in, register a terminal, see a live transaction | Catches a broken build or feed. Components aren't unit-tested unless they hold real logic, such as the hex masking view |
| **Deployed smoke** | Echo plus one 0200 against the live host after deploy | Catches config and firewall mistakes |

Not written: per-participant unit tests where an end-to-end test already covers the path, mocks of our own classes, DTO/serialisation tests, and load tests (a manual run before beta is enough).

**Safety tests (must never regress):** a Luhn-valid PAN outside the test BINs is rejected with RC 14 and is never stored in clear. A grep over DB rows and logs after the integration suite finds no clear test PAN. Tenant A's API key can't read tenant B's terminals, transactions or feed.

## Flagship integration test: 0200 → timeout → reversal (0400 generic, 0420 NIBSS)

`integration-tests/src/test/java/io/switchlab/it/PurchaseTimeoutReversalIT.java`. It runs once per dialect, parameterised over `generic87` and `nibss`.

**Setup**
1. Start the app with Testcontainers Postgres. Wait for `/actuator/health` to report UP with the listeners up.
2. Through the REST API (test API key): create a tenant, a merchant and terminal `TID=20390001`, and a test card `5399 83xx xxxx xxx1` with a balance of 100,000.00.
3. Publish a rule: `tid == 20390001 && mti == 0200 && amount == 1500.00` → `{rc: "00", delayMs: 3000}`.
4. Subscribe to the SSE feed for the tenant. Collect events in a queue.

**Act**
5. Send a 0200 from the client (STAN `000123`, amount 1500.00) with a **1s read timeout**.
6. Assert the client times out without a response.
7. Send the reversal from the client: 0400 for generic, 0420 for NIBSS with F56 reason `4021` (timeout) and F95. It carries the same F37, a new STAN and F90 built from the original. Normal timeout.
8. Wait (Awaitility, at most 5s) for the delayed 0210 to be written by the host. The client keeps reading on the same connection and discards the late 0210, as a real terminal would. Collect it to assert on.

**Assert**
- The 0410/0430 has RC `00`, echoes the reversal's STAN, and `original_txn_id` pointing at the 0200 row (via `GET /api/v1/transactions?rrn=...`).
- The 0200 row ends with status `reversed`. It also records `late_response` in its outcome history, with `rule_id` set and `rule_set_version = 1`.
- The test card balance is back to 100,000.00 (debited by the late approval, restored by the reversal).
- The SSE feed shows `0200 received`, `0400/0420 received / 0410/0430 sent` and `0210 sent (late)`. They carry matching `txn_id` links, and their order follows the timestamps.
- Masked PAN only, in every persisted `raw_masked` and `parsed` column for these rows.
- **Idempotency:** resending it as a repeat (0401/0421) returns an identical response, and the balance is unchanged.

**Variants in the same class:**
- **Drop:** the rule is `dispatch: drop`, so no 0210 is ever sent and the original status is `no_response` → `reversed`.
- **Reversal for an unknown original:** RC `25` (generic) or `00` (nibss, configurable).
- **Reversal without F90:** generic matches by TID + STAN + amount; NIBSS rejects it with RC `30`, because F90 is mandatory there.
- **NIBSS hash:** with `mac_mode=verify`, a 0200 with a wrong F128 gets RC `63`, and every 0210 carries a correct F128 (from M5).

Timing: the host delay is 3s against a 1s client timeout, which keeps the test under 10s while still leaving a clear gap between timeout and late response. The test uses a short-delay variant of the "late response then reversal" scenario pack.

## Tooling

JUnit 5, AssertJ, Awaitility, Testcontainers (Postgres), Playwright. There is no coverage target. CI fails on any test failure, Spotless formatting or ErrorProne error.
