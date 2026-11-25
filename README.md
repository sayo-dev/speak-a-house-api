# Speak-a-House API

Speech/text → validated HouseSpec → deterministic floor plan → itemised naira estimate, with version history.
The AI only interprets language; layout and cost are deterministic code. Contract: `contract/API-CONTRACT.md`.

## Run
```bash
./mvnw spring-boot:run          # http://localhost:8080/api/v1/health  (H2 file DB in ./data)
./mvnw test
ANTHROPIC_API_KEY=... ./mvnw spring-boot:run   # enables Anthropic AI interpreter (rules are the fallback)
docker compose up -d && SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run   # PostgreSQL
```

Swagger UI: `http://localhost:8080/swagger-ui.html` (OpenAPI spec at `/v3/api-docs`).

## Admin price import
```bash
curl -X POST http://localhost:8080/api/v1/admin/prices/import \
  -H "X-Admin-Key: $ADMIN_KEY" -H "Content-Type: text/csv" --data-binary @prices.csv
```

## Structure (package by feature)
`config` properties, CORS, security headers · `common` errors (RFC 7807 + code), rate limiter ·
`spec` HouseSpec, validation, patches · `interpret` lexicon, rule/Anthropic interpreters ·
`layout` deterministic plan engine · `cost` quantity takeoff × price table ·
`session` sessions, versions, diff, head · `web` controllers.

## Honest limits
Single-storey bungalows only. Prices are sample values (`verified=false`) until supplier quotes are imported.
Setbacks are sample rules, not verified regulations. This is a planning aid, not an architect's drawing.
