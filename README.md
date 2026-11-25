# Speak-a-House API

Speech/text → validated HouseSpec → deterministic floor plan → itemised naira estimate, with version history.
The AI only interprets language; layout and cost are deterministic code. Contract: `contract/API-CONTRACT.md`.

## Run
```bash
./mvnw spring-boot:run          # http://localhost:8080/api/v1/health  (H2 file DB in ./data)
./mvnw test
ANTHROPIC_API_KEY=... ./mvnw spring-boot:run   # enables the AI interpreter (rules are the fallback)
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run   # PostgreSQL (DATABASE_URL/USER/PASSWORD)
```

## Structure (package by feature)
`config` properties, CORS · `common` errors (RFC 7807 + code) · `spec` HouseSpec, validation, patches ·
`interpret` injection guard, lexicon, rule/AI interpreters · `layout` deterministic plan engine ·
`cost` quantity takeoff × price table · `session` sessions, versions, diff, head · `web` controllers.

## Honest limits
Single-storey bungalows only. Prices are sample values (`verified=false`) until supplier quotes are imported.
Setbacks are sample rules, not verified regulations. This is a planning aid, not an architect's drawing.
