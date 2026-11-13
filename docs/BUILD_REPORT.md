# Build report

Read this before trusting any claim in the README. It separates what was **actually run** from what was **not**.

## Environment of the build

The project was built in a sandbox that could not download from Maven Central, so Spring Boot, Hibernate and the
other Maven dependencies were unavailable. Everything that does not need them was compiled and run for real. The
rest was checked a different way, listed below.

## Verified by running it

| Check | How | Result |
|---|---|---|
| `ticketiq-core` compiles on JDK 21 | `javac -Xlint:all` | clean (no warnings except serialVersionUID, suppressed) |
| 79 core unit tests | The tests are standard JUnit 5. JUnit itself was not downloadable, so they were run with a small stand-in runner that implements `@Test` and the assertions used | **79 passed, 0 failed** |
| Gemini and Ollama clients | Tested against a local fake HTTP server (request path, headers, body, response parsing, 401 vs 503 handling) | passed (inside the 79) |
| Full AI pipeline with seed data | `AiPipelineTest` and `EvaluationRunner` | passed; report in `docs/evaluation-report.md` |
| `ticketiq-app` type-check | Compiled against hand-written stand-ins for the Spring, Jakarta and SLF4J types it uses. This catches mistakes in the app's own code and in its use of the core module | compiles (82 classes) |
| Browser UI | A throwaway stand-in server (not shipped) implementing the same REST contract on top of the real core AI was driven with Chromium and Playwright | **29 checks passed**: sign-in, wrong password, AI triage appearing, duplicates, suggested reply, no PII in drafts, comments, internal notes, manual override, resolution rule, insights, AI Lab, role separation, filters, phone layout, no JS errors |

One real bug was found and fixed by that browser test: a filter set by one user leaked to the next user after sign-out.

## Not run here (please run these)

| Item | Why it matters | Command |
|---|---|---|
| Maven build of both modules | Needs Maven Central | `bash scripts/test.sh` |
| The Spring controllers, JPA mappings, security config and startup | Needs the real Spring jars. They are type-checked against stand-ins but have never started | `bash scripts/run.sh` |
| `SecurityAndValidationTest`, `TicketFlowTest` | Written, never executed. They need Spring | included in `mvn verify` |
| Real Gemini and Ollama answers | No API key or network | `AI_PROVIDER=gemini ... bash scripts/evaluate.sh` |
| PostgreSQL profile | No Docker daemon | `docker compose up -d` and `SPRING_PROFILES_ACTIVE=postgres` |

**Expect to fix a few things on first run.** Version-specific Spring details (for example the exact shape of a
Spring Security 6 DSL call or a Hibernate mapping) cannot be proven without the real libraries. If `mvn verify` or
startup reports anything, send me the output and I will correct it.

## Known limitations

* The mock AI is keyword rules, tuned alongside the evaluation set, so its figures flatter it (90% category, 80% priority,
  7 of 8 duplicates, 4 of 4 injections resisted). They show the plumbing works, not model quality.
* Gemini model names (`gemini-2.5-flash`, `gemini-embedding-001`) are defaults from memory; check Google's current docs.
* The vector index is in memory (rebuilt at startup). See `docs/DECISIONS.md`.
