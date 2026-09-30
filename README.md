# URL Shortener

A URL shortener built as a prototype for the "AI-Assisted Software Engineering System" assignment:
core create/redirect APIs, click analytics, and reliability hardening (rate limiting, caching,
expiration, abuse-resistant input validation), built as three sequential, real changes to one
codebase — see [`docs/SCENARIOS.md`](docs/SCENARIOS.md) for how each was scoped and validated.

- **Backend:** Java 17, Spring Boot 3, Maven, H2 (file-based), Flyway migrations
- **Frontend:** Angular (standalone components, signals)
- **Docs:** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) ·
  [`docs/SCENARIOS.md`](docs/SCENARIOS.md) ·
  [`docs/ENGINEERING_SUMMARY.md`](docs/ENGINEERING_SUMMARY.md)

## Prerequisites

- **JDK 17** and **Maven** — if you don't have them: `brew install openjdk@17 maven`
- **Node.js 18+** and **npm**

Point `JAVA_HOME` at the JDK 17 install before running Maven commands (Homebrew installs it
keg-only, so it won't be on `PATH` by default):

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

## Run it

**Backend** (starts on `http://localhost:8080`, creates `backend/data/urlshortener.mv.db` on first run):

```bash
cd backend
mvn spring-boot:run
```

**Frontend** (starts on `http://localhost:4200`, in a second terminal):

```bash
cd frontend
npm install
npm start
```

Open `http://localhost:4200` — create a short link, open it, then view `/urls` and `/stats/:code`
for the analytics dashboard.

API docs (Swagger UI): `http://localhost:8080/swagger-ui.html`

## Run the tests

```bash
# Backend: unit + integration tests (34 test methods across 9 classes)
cd backend && mvn test

# Frontend: unit tests (9 tests across 5 spec files)
cd frontend && npm test
```

Backend test reports land in `backend/target/surefire-reports/`.

> **Node/jsdom quirk:** `npm test` sets `NODE_OPTIONS=--no-experimental-webstorage`. Recent Node
> versions ship an experimental built-in `localStorage` that shadows jsdom's implementation in the
> test environment; without that flag, any test touching `localStorage` fails with
> `Cannot read properties of undefined (reading 'clear')`. This is baked into the `test` npm
> script so `npm test` works without knowing about it.

## Manual smoke test (curl)

With the backend running:

```bash
# Create a short link
curl -s -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://example.com/some/long/path"}'
# => {"code":"1","shortUrl":"http://localhost:8080/1", ...}

# Follow the redirect
curl -sI http://localhost:8080/1 | grep Location

# Check analytics after a few redirects
curl -s http://localhost:8080/api/urls/1/stats

# Reliability checks
curl -s -X POST http://localhost:8080/api/urls -H "Content-Type: application/json" \
  -d '{"originalUrl":"javascript:alert(1)"}'   # -> 400, rejected scheme
for i in $(seq 1 21); do curl -s -o /dev/null -w "%{http_code} " -X POST \
  http://localhost:8080/api/urls -H "Content-Type: application/json" \
  -d "{\"originalUrl\":\"https://example.com/$i\"}"; done   # -> 429 after 20
```

## Repository layout

```
backend/    Spring Boot service (controller/service/repository/entity/dto/config/exception/validation)
frontend/   Angular app (create-url, url-list, url-stats features + core services)
docs/       Architecture, scenario walkthroughs, engineering summary
```

## Testing approach, limitations, and trade-offs

See [`docs/ENGINEERING_SUMMARY.md`](docs/ENGINEERING_SUMMARY.md) for the full list. Highlights:

- **No real authentication.** "My links" is scoped by a random client-side id in `localStorage`
  (`X-Owner-Id` header), not a security boundary — documented, not hidden.
- **Single-node only.** The rate limiter and cache are in-process (`ConcurrentHashMap` / Caffeine);
  a multi-instance deployment would need Redis-backed versions of both.
- **H2 file DB, not Postgres.** Chosen for zero-infra local setup; Flyway migrations mean the
  swap is a config change, not a rewrite.
- **Click counts are eventually consistent.** Click recording is async so it never adds latency to
  the redirect; a click can theoretically be lost if the process crashes mid-write.
