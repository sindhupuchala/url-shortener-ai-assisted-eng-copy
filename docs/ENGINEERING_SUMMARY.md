# Engineering Summary

## Plan and rationale

The brief asked for a URL shortener prototype built to demonstrate AI-assisted engineering
*process*, not just working code, structured as three required scenario types. The approach taken:
build one real codebase across three sequential passes — greenfield (core create/redirect),
brownfield (analytics layered onto the working core), ambiguous (a vague reliability/abuse
requirement, normalized into explicit acceptance criteria before implementation) — so that each
later pass had to keep the earlier passes' tests passing, rather than three disconnected demos.
Stack (Java/Spring Boot + Angular) and scenario mapping were confirmed with the engineer before
any code was written; see `docs/SCENARIOS.md` for the full decomposition, execution, and
validation of each pass.

Every pass followed the same gate before moving on: compile → unit tests → integration tests →
manual curl (or `ng test`) smoke test against a live instance. Nothing in Scenario 2 was built
against Scenario 1 code that hadn't already passed its own tests, and likewise for Scenario 3.

## Artifacts produced

- **Backend** (`backend/`, Spring Boot 3 / Java 17 / Maven): 3 Flyway migrations, 2 entities, 3
  repositories, 6 services (`UrlService`, `StatsService`, `ShortCodeEncoder`, `ClickRecorder`,
  `RateLimiter`, `UrlLookupCache`), 1 scheduled job, 3 controllers, a custom Bean Validation
  constraint, a global exception handler, CORS/cache/async/rate-limit config, a structured request
  log filter.
- **Frontend** (`frontend/`, Angular, standalone components + signals): 3 routed feature
  components (create, list, stats), 2 core services, an HTTP interceptor for the owner-id header.
- **Tests**: 9 backend test classes / 34 test methods (unit + `@SpringBootTest` integration); 5
  frontend spec files / 9 tests.
- **API docs**: springdoc-openapi → live Swagger UI at `/swagger-ui.html`, satisfying the
  API/schema definitions deliverable without a hand-maintained spec that could drift from the code.
- **Docs**: this file, `docs/ARCHITECTURE.md`, `docs/SCENARIOS.md`, root `README.md`.

## Risks, trade-offs, and how they were handled

| Risk / trade-off | Handling |
|---|---|
| No real authentication | `X-Owner-Id` is a client-generated, unauthenticated header. Anyone can claim any owner id and see that owner's link list (not their redirect targets — those are only reachable by knowing/guessing the short code either way). Explicitly documented as a prototype simplification; a real deployment needs actual auth before this header means anything security-wise. |
| Single-node rate limiter and cache | Both are in-process (`ConcurrentHashMap`, Caffeine). A multi-instance deployment would let an attacker get `N×` the intended rate limit (once per instance) and would have a cold cache per instance. Would move to Redis (`INCR`/`EXPIRE` or a Lua token bucket, and a Redis-backed cache) behind a load balancer. |
| Click counts are eventually consistent | Click recording is deliberately async so it can never add latency to the redirect response. A crash between sending the 302 and the async write landing loses that one click. Acceptable for analytics counts; would not be acceptable if click events were financial or billing-relevant. |
| Client IP for rate limiting is `request.getRemoteAddr()` | Behind a reverse proxy/load balancer, this sees the proxy's IP, not the real client — the limiter would effectively rate-limit *all* traffic together. A production deployment behind a proxy needs to trust and parse `X-Forwarded-For` (only from trusted proxy hops, to avoid trivial spoofing). |
| N+1 queries on the "my links" list endpoint | `GET /api/urls` fetches a user's URLs, then queries click counts per URL individually. Fine at prototype scale (a handful to low hundreds of links per owner); would move to a single joined/aggregated query if a user's link count grew large. |
| H2 file DB, not Postgres | Zero-infra local run was prioritized for a 2–3 day prototype. Flyway migrations mean the swap is a datasource config change, not a schema rewrite — but H2/Postgres SQL-dialect differences haven't been tested (this project only runs against H2, in-memory for tests and file-based for dev). |
| Redirect route (`/{code}`) is a catch-all on the backend root | Correct for how a real short-link service is deployed (redirect on the short domain itself). If the Angular build were ever served from the *same* origin as this API, `/urls`, `/stats/*`, etc. would need to be registered before the catch-all redirect route to avoid being swallowed by it. Not an issue in the current two-process dev setup (`:4200` / `:8080`). |
| Expired-link cleanup is a retention window, not immediate deletion | Deliberate: an expiry set by mistake is recoverable for `expired-retention-days` (30, by default) before the row is actually purged. |

## Assumptions

- A short-link redirect should be a standard HTTP 302 (not 301) — short links are often reused
  with a changed destination in real products, so caching the redirect permanently in the
  browser/CDN would be the wrong default.
- "My links" scoping via a client-side id is an acceptable prototype stand-in for auth, given the
  assignment's time-box; a login system was out of scope.
- Analytics needed are click **counts** over time (total + per-day), not full clickstream detail
  (geo, device breakdown, referrer analytics UI) — `referrer`/`userAgent` are captured and stored
  per click event for future use, but no endpoint currently surfaces them.
- "Survive real traffic and doesn't get abused" (the ambiguous requirement) was scoped to
  single-node reliability primitives appropriate to this prototype's deployment shape, not
  distributed-systems-scale infrastructure (no CDN, no distributed rate limiting, no WAF).

## Limitations

- No CI pipeline or containerization (Docker/Compose) — out of scope to keep "runnable end-to-end"
  reducible to `mvn spring-boot:run` + `npm start` with no additional infra. A natural next step.
- No load/performance testing was run; the cache and rate limiter are sized for demonstration, not
  benchmarked against a target QPS.
- Frontend test coverage is component-level and intentionally not exhaustive (happy path + one
  representative error path per component), consistent with the assignment's time-box; it is not a
  substitute for broader e2e coverage in a real product.
- No static analysis / linting gate is wired into the build (e.g., Checkstyle, SpotBugs, ESLint
  config beyond Angular's defaults) — tests are the quality gate currently enforced.
- Geo/device breakdown of clicks is not implemented, only counts — `referrer`/`user_agent` are
  captured but unused beyond storage, as noted above.
