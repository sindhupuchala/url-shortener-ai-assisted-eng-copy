# Scenarios: Greenfield, Brownfield, Ambiguous

This assignment requires three scenario types, each showing decomposition → execution →
validation. They were built **sequentially against one real codebase** — brownfield extends the
code greenfield produced, ambiguous extends both — rather than as three disconnected demos, so
each pass had to keep the previous passes' tests green.

Throughout, Claude Code (Sonnet 5) was used as an in-editor AI assistant under human direction: the
engineer set the stack, approved the plan before any code was written, and reviewed/ran every
change. Below, "generated" means Claude wrote the first draft; "edited" means the engineer's
review changed the approach; "rejected" means a planned approach was deliberately not taken, with
the reasoning kept here rather than silently dropped.

---

## Scenario 1 — Greenfield: core create/redirect

### Requirement understanding

The assignment states: "build a URL shortener with core APIs." Normalized into: given a long URL,
return a short one; given a short code, redirect to the original. Ambiguities resolved before
coding:

- **Short code strategy** (random vs. deterministic) → base62(auto-increment id): collision-free
  by construction, no retry-on-collision loop.
- **Custom aliases** — not explicitly requested, but a standard feature of every real short-link
  service and needed to exercise the "conflict" error path meaningfully → included, with a DB
  unique constraint + 409 on collision.
- **What "core" excludes** — no auth, no analytics yet (that's Scenario 2), no abuse protection yet
  (Scenario 3). Scoped explicitly so the greenfield pass stayed reviewable.

### Task decomposition

1. Repo/Maven/Angular scaffolding
2. `V1` schema (`urls` table) → entity → repository
3. `ShortCodeEncoder` (pure function, unit-testable in isolation)
4. `UrlService.create` / `resolve` / `getMetadata`
5. `UrlController` (create, get metadata) + `RedirectController` (redirect)
6. `GlobalExceptionHandler` for consistent error JSON
7. Angular: owner-id service, API service, create-url form
8. Tests (unit + `@SpringBootTest` integration) + manual curl smoke test

Steps 2→3→4→5 are a dependency chain; 7 could only start once 5's response shape was fixed.

### AI-assisted execution & traceability

- **Generated and kept as-is:** the entity/repository/DTO boilerplate, `ShortCodeEncoder`,
  `GlobalExceptionHandler`, the Angular form component and its CSS.
- **Generated, then edited on review:** the id→code assignment. The first pass would have needed
  `code` to be `NOT NULL` at insert time, which is impossible when the code is derived from the
  row's own id. Resolved by making `code` nullable at the DB/entity level with a comment
  explaining *why*, and doing `saveAndFlush()` → `encode(id)` → `save()` inside one
  `@Transactional` method, so no caller ever observes the null intermediate state.
- **Caught by validation, not by inspection:** the first integration test file was named
  `UrlShortenerFlowIT.java`. Maven Surefire's default include pattern only picks up
  `*Test.java`/`Test*.java` (the `*IT.java` convention belongs to the Failsafe plugin, which
  wasn't configured) — so `mvn test` silently reported success while skipping all 5 of those
  tests. This surfaced by cross-checking the surefire report file list against the test files on
  disk, not by reading the code. Fixed by renaming to `UrlShortenerFlowIntegrationTest.java` and
  re-running to confirm all 5 executed.
- **Human-directed stack decision:** the assignment's suggested stacks (Node/Python) were offered
  as recommendations; the engineer chose Java/Spring Boot + Angular instead, which then drove every
  subsequent structural decision (Maven layout, Spring idioms, standalone Angular components).

### Validation

- `mvn test`: 3 test classes, all green (`ShortCodeEncoderTest`, `UrlServiceTest`,
  `UrlShortenerFlowIntegrationTest`, `ValidUrlValidatorTest`).
- `npm test`: `App` + `CreateUrlComponent` specs green.
- Manual curl sequence: create → 302 redirect with correct `Location` → metadata fetch → 404 for
  unknown code → 400 with field-level detail for a malformed URL. All confirmed against a live
  `mvn spring-boot:run` instance, not just unit-tested in isolation.

---

## Scenario 2 — Brownfield: analytics on the working shortener

### Requirement understanding

"Add analytics" to the already-built service. Read as: this is a change *to existing running
code*, so it must not regress Scenario 1 and must not slow down the redirect path (the one
endpoint every click hits). Normalized to two concrete features: total-click and per-day-click
counts per link, and a "my links" dashboard.

### Codebase reasoning (impacted modules)

- **`RedirectController`** — the only place a click can be observed; must call into recording
  without adding latency to the 302 response.
- **New:** `ClickEvent` entity/table, `ClickEventRepository`, `ClickRecorder`, `StatsService`,
  two new endpoints on the existing `UrlController` (`GET /api/urls`, `GET /api/urls/{code}/stats`).
- **`UrlRepository`** — needed an owner-scoped list query it didn't have before.
- **Angular** — two new routed components (`url-list`, `url-stats`) consuming the existing
  `UrlApiService`, extended with two new methods.

### Task decomposition

1. `V2` schema (`click_events`, FK to `urls`)
2. `ClickEvent` entity/repository
3. `ClickRecorder` (async) + dedicated bounded thread pool (`AsyncConfig`) — not Spring's default
   unbounded-thread-per-task executor
4. Wire `RedirectController` to fire-and-forget the recorder
5. `StatsService` (per-day aggregation) + stats/list endpoints on `UrlController`
6. Angular `UrlListComponent`, `UrlStatsComponent`, routes
7. Regression run of Scenario 1's tests + new tests for click recording and aggregation

### AI-assisted execution & traceability

- **Generated and kept:** the `ClickEvent`/`ClickEventRepository` pair, the per-day aggregation
  in `StatsService` (Java-side `groupingBy` rather than a SQL `GROUP BY`, chosen deliberately for
  portability across H2/Postgres at prototype data volumes — documented as a trade-off that would
  need to move into SQL or a rollup table at real scale).
- **Generated, then corrected for a real bug:** the async click test initially asserted the click
  count immediately after the redirect call. Because recording is genuinely async, that's a race —
  the assertion would pass or fail depending on scheduler timing. Replaced with an
  `Awaitility.await().untilAsserted(...)` poll (bounded at 3s), which is what "async and eventually
  consistent" actually requires from a test, and added `awaitility` as a test-scoped dependency to
  support it.
- **Rejected:** loading all URLs owned by a user and joining click counts in one query. Chosen
  instead: fetch the owner's URLs, then call `StatsService.countClicks` per URL (N+1 queries).
  Explicitly a prototype-scale trade-off, not an oversight — documented in
  `docs/ENGINEERING_SUMMARY.md` rather than silently accepted.

### Validation

- Full backend suite re-run after this pass: 6 test classes, all green, including the 3 Scenario 1
  classes (regression check) plus 2 new ones (`StatsServiceTest`, `AnalyticsIntegrationTest`).
- `npm test`: 5 spec files, 9 tests green (Scenario 1's 4 + 5 new).
- Manual smoke test against a live server: create → hit redirect 3× → `GET .../stats` returns
  `totalClicks: 3` with one `dailyClicks` bucket for today; `GET /api/urls` with an owner header
  returns only that owner's link with the matching click count.

---

## Scenario 3 — Ambiguous: "make sure this can survive real traffic and doesn't get abused"

### Requirement understanding — interpreting before implementing

This requirement was deliberately posed with no numbers and no named mechanism, mirroring how a
real ambiguous ask arrives. Rather than guessing at implementation, it was first normalized into
five testable acceptance criteria, agreed before any code was written:

1. Cap link creation per IP (defined: 20/min, 429 + `Retry-After` past that).
2. Avoid hammering the DB for popular links (defined: in-memory cache, 5 min TTL, in front of the
   redirect lookup only — not the create path).
3. Stop serving a link past its expiry (defined: 410 Gone at resolve time, plus a retention-window
   cleanup job — not immediate deletion, in case an expiry was set by mistake).
4. Don't let the service redirect to schemes it shouldn't (defined: allow-list `http`/`https` only,
   reject `javascript:`, `data:`, `file:`, etc.).
5. Make requests to the two hottest endpoints (create, redirect) traceable after the fact (defined:
   one structured log line per request with method/path/status/duration).

### Task decomposition

1. `RateLimiter` (pure token-bucket logic, unit-testable without Spring) + `RateLimitInterceptor`
   registered on `POST /api/urls` only
2. `CacheConfig` (Caffeine) + `UrlLookupCache` as a *separate bean* from `UrlService` (see below)
3. Wire `UrlService.resolve` to the cache, keeping the expiry check outside the cached call
4. `V3` migration (`ON DELETE CASCADE`) + `ExpiredUrlCleanupJob` (`@Scheduled`, retention window
   from config)
5. Tighten `ValidUrlValidator` to an http(s) allow-list
6. `RequestLoggingFilter`
7. Tests for each criterion, isolated where shared test state would otherwise interfere (below)

### AI-assisted execution & traceability

- **Rejected the plan's own original design, on review:** the approved plan called for a
  standalone `SafeUrlValidator` alongside the existing `ValidUrlValidator`. Building it, that would
  have meant two validators doing overlapping URL-shape checks — not how an engineer would actually
  evolve a codebase. Instead, `ValidUrlValidator` itself was tightened in place (scheme allow-list
  added to the existing check), with a comment explaining the change is part of the Scenario 3
  hardening pass. The plan deviation is called out here rather than silently diverging from what
  was approved.
- **A caching design decision made explicitly to avoid a correctness bug:** the obvious
  implementation is `@Cacheable` directly on `UrlService.resolve()`. That would cache the *whole*
  method, including the expiry check inside it — meaning an expired link could keep 302-ing for up
  to the cache's 5-minute TTL after its `expiresAt` had passed. Instead, only the raw DB lookup is
  cached (`UrlLookupCache.findByKey`, a separate Spring bean so the `@Cacheable` proxy actually
  applies — self-invocation from within `UrlService` would have silently skipped caching
  entirely), and `resolve()` re-checks `isExpired()` fresh against the cached entity on every call.
  This is the one piece of Scenario 3 where the "obvious" first draft was rejected before it was
  even written, because tracing through the caching semantics surfaced the bug on paper.
- **A test-isolation bug caught before it caused a flaky suite:** `@SpringBootTest` caches the
  Spring context (and therefore the singleton `RateLimiter`) across test classes that share
  configuration. Every MockMvc request resolves to the same fake remote address, so every
  `POST /api/urls` across *every* integration test class draws from the *same* token bucket if they
  share a context. A rate-limit test that intentionally exhausts a bucket would either fail
  (starved by other tests' earlier POSTs) or starve later tests, depending on run order — classic
  shared-mutable-state test pollution. Fixed by giving `RateLimitIntegrationTest` its own
  `@SpringBootTest(properties = {...})` override, which Spring's test context cache keys on
  separately, isolating its tiny 3-request capacity from the ~9 POSTs the rest of the suite makes
  against the default 20/min configuration.
- **Generated and kept:** `RateLimiter`'s token-bucket math, `ExpiredUrlCleanupJob`,
  `RequestLoggingFilter`.

### Validation

- `mvn test`: 9 test classes, 34 test methods, all green — including a new
  `RateLimitIntegrationTest` (isolated context, asserts 429 + `Retry-After` on the 4th request
  against a capacity-3 bucket) and `ReliabilityIntegrationTest` (asserts 400 for a `javascript:`
  URL, and 410 for a link whose `expiresAt` was pushed into the past directly via the repository —
  deterministic, no `Thread.sleep`).
- `RateLimiterTest` and the extended `ValidUrlValidatorTest` cover the logic as pure unit tests,
  independent of Spring.
- Manual smoke test against a live server: `javascript:` payload → 400 with the field-level
  message (this POST also consumed one rate-limit token, since the interceptor runs before
  request-body validation); 21 further rapid `POST /api/urls` calls → 19× `201` then `429` for the
  rest, consistent with the configured 20/min capacity minus the token already spent on the
  rejected request above.
