# TicketIQ: Manual Project Creation Walkthrough

This guide explains how a developer can build the current TicketIQ project in deliberate steps, why each step is
useful, and how the parts connect. It is based on the checked-in source, configuration, tests, and existing project
documents. The order below is a practical implementation sequence, not a claim about the historical order of commits.

TicketIQ is a Java 21 help-desk application. Employees create and follow support tickets; agents investigate and resolve
them; administrators manage users, knowledge articles, and AI settings. AI features suggest ticket triage, similar
resolved incidents, and grounded reply drafts. People remain responsible for triage decisions and communication.

## Step 1: Define the product behavior before choosing frameworks

Start by writing down the real users and the work each needs to do:

1. A requester signs in, submits an issue, follows their own active tickets, and replies to support.
2. An agent sees active and completed incidents, assigns and triages tickets, communicates with requesters, and records
   resolutions.
3. An administrator can do the support work and also manage accounts, knowledge articles, and AI operation.
4. AI assistance is optional. Ticket creation and support work must continue if a model is unavailable or disabled.

Record the role boundaries and ticket states before implementing endpoints. The application uses `USER`, `AGENT`, and
`ADMIN` roles; ticket statuses include `NEW`, `TRIAGED`, `NEEDS_HUMAN_TRIAGE`, `IN_PROGRESS`, `RESOLVED`, and `CLOSED`.
Categories and priorities are shared types in the core module.

**Why this step matters:** These rules shape the data model, API authorization, browser navigation, and tests. Defining
them first prevents a UI-only security model or AI behavior that silently takes decisions away from staff.

**Where it appears:** `README.md`, `docs/API.md`, `ticketiq-core/src/main/java/com/ticketiq/core/model/`, and
`ticketiq-app/src/main/java/com/ticketiq/app/ticket/`.

## Step 2: Choose a small architecture that can run locally

Use a modular monolith: one Spring Boot application process, separated into two Maven modules:

- `ticketiq-app` contains the REST API, persistence, authentication, in-process events, configuration, and static browser
  interface.
- `ticketiq-core` contains plain Java AI workflow code, provider interfaces and clients, privacy redaction, JSON/JWT
  utilities, seed data, and evaluation code. It does not depend on Spring at runtime.

Keep ticket storage in the application and let the application invoke AI through a small integration layer. Use
after-commit events for work that should not delay or undo ticket creation.

**Why this step matters:** One process is easy to run and test without Kafka, a vector database, or an external AI key.
Keeping the core independent of Spring also makes the AI behavior testable without starting the web application.

**Where it appears:** `pom.xml`, `ticketiq-app/pom.xml`, `ticketiq-core/pom.xml`, `docs/ARCHITECTURE.md`, and
`docs/DECISIONS.md`.

## Step 3: Create the Maven build and establish Java requirements

At the repository root, define the Maven parent project with Spring Boot's dependency management, project version
`2.0.0`, Java release `21`, UTF-8 encoding, and the two child modules. Add the Spring Boot web, JPA, validation,
security, actuator, and cache dependencies to the application module. Keep the core module's runtime dependency-free;
JUnit is used there for tests.

Install JDK 21 and Maven 3.9 or later. The repository does not include a Maven Wrapper, so `mvn` must be available on
the machine.

**Why this step matters:** A parent build gives both modules consistent versions and lets one command build and verify
the complete application. Java 21 is required by the configured source level and runtime code.

**How to verify:** From the repository root run `mvn -B verify`.

## Step 4: Add Spring Boot startup and shared application configuration

Create `TicketIqApplication` as the application entry point. Enable configuration-properties scanning, asynchronous
execution, scheduled tasks, and caching. Put environment-overridable settings in `application.yml`: port, database
connection, JWT lifetimes and secret, demo seeding, AI provider, model settings, and retry/rate/cache thresholds.
Provide `application-postgres.yml` for the optional PostgreSQL profile.

Keep the normal local configuration self-contained: the default database is an H2 file under `data/`, and the default
AI provider is the offline mock. PostgreSQL is optional and is started by the repository's `docker-compose.yml`.

**Why this step matters:** Environment-specific values should not be hard-coded into business logic. A local default
that needs no service or API key lets developers run and test the application immediately.

**Where it appears:** `ticketiq-app/src/main/java/com/ticketiq/app/TicketIqApplication.java`,
`ticketiq-app/src/main/java/com/ticketiq/app/config/`, `ticketiq-app/src/main/resources/application.yml`,
`ticketiq-app/src/main/resources/application-postgres.yml`, `.env.example`, and `docker-compose.yml`.

## Step 5: Model the help-desk records and persistence behavior

Create JPA entities and repositories for users, tickets, comments, knowledge-base articles, AI feedback, and AI call audit
records. The ticket stores requester and assignee references, status, category, priority, triage source, resolution,
timestamps, and AI suggestion fields. Add a JPA version field for optimistic locking, and indexes for common ticket
filters.

Persist normal business data in H2 or PostgreSQL. Do not persist vectors in the relational schema: the current vector
store is in memory and is rebuilt from the database at application startup. Database schema updates currently use
Hibernate `ddl-auto: update`; this is suitable for the demonstration setup, not a substitute for production migrations.

**Why this step matters:** The database remains the source of truth for tickets and knowledge. Optimistic locking helps
prevent one support user's edit from silently overwriting another's.

**Where it appears:** `ticketiq-app/src/main/java/com/ticketiq/app/ticket/`, `user/`, `kb/`, and `ai/`, plus the datasource
configuration in `application.yml`.

## Step 6: Implement identity, authentication, and API authorization

Implement account creation, sign-in, token refresh, and profile retrieval. Hash stored passwords with BCrypt. Signup
validates identity fields and requires an 8–72 character printable password containing a lowercase letter, uppercase
letter, number, and symbol; a self-registered account always receives the `USER` role. Issue stateless HS256 JWT access
tokens (30-minute default) and refresh tokens (7-day default).

Add a per-user-ID failed-login limiter, a generic wrong-credentials response, and JSON responses for authentication and
authorization errors. Require a bearer token for `/api/**` other than the explicitly public sign-in, signup, refresh,
metadata, and health endpoints. Enforce role permissions on the API using method security; hiding a button in the
browser is not authorization.

Apply ticket ownership rules in the service as well: requesters can see only their own unfinished tickets and receive a
not-found response for other users' or finished tickets. Staff can inspect ticket history.

**Why this step matters:** Authentication identifies callers, while authorization limits what each role can do. Enforcing
both at the server protects data even when a caller bypasses the browser interface.

**Where it appears:** `ticketiq-core/src/main/java/com/ticketiq/core/security/JwtService.java`,
`ticketiq-app/src/main/java/com/ticketiq/app/security/`, and `ticketiq-app/src/main/java/com/ticketiq/app/user/`.

## Step 7: Build the core ticket lifecycle without any model dependency

Implement ticket creation, filtering, pagination, detail lookup, manual triage, assignment, comments, and status changes
in `TicketService`; expose them through `TicketController`. Validate request DTOs and cap page sizes. Only staff can set
manual category and priority, assign tickets, and change status. Resolving a ticket requires resolution text.

The API also exposes related-ticket results, suggested replies, AI feedback, and a retriage request to staff. Keep
request/response DTOs separate from JPA entities so the API does not expose persistence internals.

**Why this step matters:** The conventional ticket workflow must be useful on its own. Making the base operations work
before adding AI ensures a model outage cannot block support work.

**Where it appears:** `ticketiq-app/src/main/java/com/ticketiq/app/ticket/TicketController.java`,
`TicketService.java`, `TicketDtos.java`, `TicketSpecs.java`, and `docs/API.md`.

## Step 8: Publish ticket and knowledge changes after database commit

Define the in-process events `TicketCreated`, `TicketResolved`, and `KbChanged`. Each carries IDs rather than detached
entity objects; handlers reload current data. Publish creation and knowledge-change events in their service
transactions, and handle them after commit. When a ticket is resolved, publish the resolution event so the indexer can
learn from its saved resolution.

The implemented event contract does **not** define a `TicketTriaged` event. AI triage is applied by the asynchronous
handler calling `TicketService.applyAiTriage` directly.

**Why this step matters:** After-commit handling avoids analyzing a record that later rolls back. ID-only events reduce
stale data and keep ticket operations separate from AI implementation details.

**Where it appears:** `ticketiq-app/src/main/java/com/ticketiq/app/events/TicketEvents.java`,
`ticketiq-app/src/main/java/com/ticketiq/app/ai/AiTriageHandler.java`, `AiIndexer.java`, and
`ticketiq-app/src/main/java/com/ticketiq/app/ticket/TicketService.java`.

## Step 9: Create the framework-independent AI core

Define `ChatModel` and `EmbeddingModel` interfaces, then implement the offline mock, Gemini, and Ollama clients behind
those contracts. Build provider selection in `AiRuntime`, so the rest of the application uses the same pipeline no
matter which provider is configured. Use the JDK HTTP client for the external provider adapters.

The mock provider uses local keyword rules and hashed bag-of-words embeddings. It is deterministic and needs no network
access. Gemini needs an API key; Ollama needs its local server and the configured chat and embedding models.

**Why this step matters:** Provider-specific HTTP details should not leak into ticket logic. A deterministic offline
provider makes normal development and automated tests possible without credentials or network calls.

**Where it appears:** `ticketiq-core/src/main/java/com/ticketiq/core/llm/`,
`ticketiq-core/src/main/java/com/ticketiq/core/ai/AiRuntime.java`, and `.env.example`.

## Step 10: Implement privacy-first triage and defensive model output handling

In `AiPipeline.run`, assemble the title and description, redact personal information, and then check the per-minute
pipeline limit before making model calls. The redactor replaces supported email, phone, PAN, Aadhaar-like ID, and
Luhn-valid payment-card patterns with tokens. Cache triage by a SHA-256 hash of the redacted input.

Ask the selected model for a structured category, priority, summary, confidence, and rationale. Parse and validate its
JSON; if the response is invalid, make one repair attempt. If the result is still unusable or the model fails, return a
fallback that requires human review. Ticket text is marked as untrusted in the prompts, and delimiter-like markers are
neutralized before interpolation.

**Why this step matters:** Redaction limits personal data sent to providers and stored in vector content. Strict parsing,
one bounded repair attempt, and a human-review fallback make malformed or unsafe model output non-authoritative.

**Where it appears:** `ticketiq-core/src/main/java/com/ticketiq/core/ai/AiPipeline.java`,
`TriageService.java`, `PromptTemplates.java`, `ticketiq-core/src/main/java/com/ticketiq/core/redaction/PiiRedactor.java`,
and `ticketiq-core/src/main/resources/prompts/`.

## Step 11: Add similar-incident search and grounded reply drafting

Embed the redacted ticket text. Search the in-memory vector store for sufficiently relevant knowledge articles and
resolved incidents using cosine similarity. For a reply draft, retrieve up to three knowledge articles and three
resolved tickets, then provide that context to the reply prompt. If no qualifying context is found, return no draft
rather than asking the model to invent an answer. Keep source references with the result.

The vector store is populated at startup from knowledge articles and resolved/closed tickets that have a resolution.
After a new ticket is resolved, or an article is changed, the indexer updates the in-memory store. Text is redacted
before it is embedded; ticket resolution content used for indexing is redacted as well.

**Why this step matters:** Similarity search can surface prior work, and grounded context gives staff a traceable starting
point for replies. Rebuilding the index avoids maintaining a second persistent data source in this local-first design.

**Where it appears:** `ticketiq-core/src/main/java/com/ticketiq/core/ai/VectorStore.java`,
`InMemoryVectorStore.java`, `SimilarityService.java`, `ReplyDraftService.java`, and
`ticketiq-app/src/main/java/com/ticketiq/app/ai/AiIndexer.java`.

## Step 12: Put resilience, guardrails, and audit around every model call

Route chat and embedding operations through `AiGateway`. The gateway records call metadata and metrics, and runs calls
through `ResilientAiExecutor`, which applies timeouts, configurable retries with exponential backoff, and a circuit
breaker. The default breaker opens after five consecutive failures for 30 seconds before allowing a recovery trial.
Non-retryable errors such as authentication failures are not retried.

Evaluate confidence and pipeline failures with `GuardrailPolicy`. Low-confidence or failed triage is marked for human
review. Store only model-call audit metadata such as provider, call kind, latency, success, and error—not ticket text.
An agent's manual triage is authoritative: a later AI response may retain suggestion fields but cannot replace manually
chosen category, priority, or triage source.

**Why this step matters:** A slow or failing provider should not freeze the application or make a model suggestion look
like a confirmed human decision. Centralizing calls also keeps resilience and measurement consistent.

**Where it appears:** `ticketiq-core/src/main/java/com/ticketiq/core/ai/AiGateway.java`,
`ResilientAiExecutor.java`, `GuardrailPolicy.java`, and `AiMetrics.java`; application audit persistence is in
`ticketiq-app/src/main/java/com/ticketiq/app/ai/`.

## Step 13: Connect ticket creation to asynchronous AI and make failure recoverable

Create a Spring `AiTriageHandler` that receives `TicketCreated` after commit, runs on the `aiExecutor`, reloads the
ticket, runs the core pipeline, and applies the result. Creating a ticket itself returns immediately with status `NEW`;
it never waits for a model.

If the runtime AI switch is off, the handler leaves the ticket as-is. Runtime failures are logged and leave the ticket
in its current state (normally `NEW`) for manual handling or a later retry. `AiTriageSweeper` periodically retries
sufficiently old `NEW` tickets when AI is enabled
(30-second default sweep interval, 20-second grace period). A successful pipeline result that cannot be triaged moves
the ticket to `NEEDS_HUMAN_TRIAGE`.

**Why this step matters:** Asynchronous work preserves responsiveness. A periodic retry covers tickets missed while AI
was disabled or the application was restarting, without making model availability a requirement for ticket creation.

**Where it appears:** `ticketiq-app/src/main/java/com/ticketiq/app/ai/AiTriageHandler.java`,
`AiTriageSweeper.java`, `ticketiq-app/src/main/java/com/ticketiq/app/config/AiConfig.java`, and
`TicketService.applyAiTriage`.

## Step 14: Complete knowledge, feedback, and operational views

Implement knowledge-base CRUD with administrator-only writes, list caching, cache eviction on changes, and index updates
after commit. Record whether agents accepted or edited AI drafts. Provide support Insights for ticket status/category/
priority mix and AI triage/feedback summaries. Provide administrator AI statistics for provider/model names, circuit
state, index size, metrics, and recent call metadata.

Add a dry-run endpoint for staff to try analysis without saving a ticket, and an administrator-only switch to enable or
disable AI at runtime. Public metadata exposes the active provider and category-to-team mapping without exposing
secrets.

**Why this step matters:** Operational views make it possible to review the system and assess usefulness. Feedback is
measured instead of assuming that generated suggestions are correct or helpful.

**Where it appears:** `ticketiq-app/src/main/java/com/ticketiq/app/kb/`, `stats/`, `ai/AiController.java`,
`ai/AiAuditService.java`, and `config/MetaController.java`.

## Step 15: Build a browser interface without a separate frontend build

Serve `index.html`, `styles.css`, and `app.js` from Spring Boot's static resources. Implement sign-in and signup,
ticket list/search/filter/paging, ticket creation and detail, comments, staff controls, KB, Insights, and the AI Lab.
The browser sends bearer tokens to the REST API, refreshes expired sessions where possible, and polls ticket details
while asynchronous triage is pending. The UI can display the AI suggestions and their sources, but the server still
enforces all permissions.

**Why this step matters:** A static JavaScript interface keeps the project runnable with Maven alone—no Node.js,
bundler, or separate web server is required—while exercising the same public API used by other clients.

**Where it appears:** `ticketiq-app/src/main/resources/static/` and screenshots in `docs/screenshots/`.

## Step 16: Seed a useful local demonstration dataset

When seeding is enabled and the user table is empty, create demo admin, agent, and requester accounts, knowledge
articles, and resolved tickets. Hash demo passwords before storing them. Reuse shared seed data from the core module
so the runtime vector index and evaluation set use consistent examples.

**Why this step matters:** Similarity search and grounded replies need prior knowledge to demonstrate their purpose on a
fresh database. A conditional seed also avoids inserting the demo data repeatedly into an existing installation.

**Where it appears:** `ticketiq-app/src/main/java/com/ticketiq/app/config/DataSeeder.java`,
`ticketiq-core/src/main/java/com/ticketiq/core/seed/SeedData.java`, and
`ticketiq-core/src/main/resources/seed/`.

## Step 17: Verify behavior with focused unit and integration tests

Add core tests for redaction, JSON parsing, JWT validation, mock/provider clients, triage parsing, guardrails, retries,
timeouts, circuit breaking, vector search, prompt handling, and the end-to-end AI pipeline. Add application tests for
signup, validation, authorization, ticket ownership, and the complete ticket flow. Use an in-memory database for Spring
tests and a local fake HTTP server for provider-client tests; normal tests need no AI API key.

The end-to-end ticket test exercises the important sequence: create a ticket, wait for asynchronous mock triage, check
similar incidents and a suggested reply, override with manual triage, reject a resolution without resolution text, and
resolve with a valid explanation.

**Why this step matters:** Unit tests isolate core rules, while application tests prove the API, database, security, and
asynchronous pieces work together. Testing both the happy path and failure boundaries protects the requirement that
human support continues when AI does not.

**How to verify:** Run `mvn -B verify` or `scripts/test.ps1` on Windows / `bash scripts/test.sh` on macOS and Linux.
`docs/BUILD_REPORT.md` records a previous local verification; its figures are a report snapshot rather than a guarantee
about a different machine or later checkout.

## Step 18: Add repeatable local setup, run, and evaluation commands

Provide matching PowerShell and Bash scripts. Setup checks Java and Maven and copies `.env.example` to `.env` if needed.
The run script loads configuration, builds the modules, starts the Spring Boot application, waits for the health
endpoint, and opens the local URL when possible. The test script runs the full Maven verification. The evaluation
script runs the core evaluation tool against 40 labeled tickets and writes `docs/evaluation-report.md`.

Configure VS Code tasks, launch settings, recommended extensions, and editor settings so contributors can run the
application and tests from the workspace. The `.http` files provide ready-made API requests.

**Why this step matters:** Documented, repeatable commands reduce setup mistakes and make the project's normal workflow
available on Windows, macOS, Linux, and from VS Code.

**Where it appears:** `scripts/`, `.vscode/`, `docs/http/`, `.env.example`, `README.md`, and
`docs/evaluation-report.md`.

## Step 19: Run the application and manually verify the user journey

On Windows, install JDK 21 and Maven 3.9+, then run:

```powershell
.\scripts\setup.ps1
.\scripts\run.ps1
```

Open `http://localhost:8080` (or the configured port). Sign in with a local demo account from `README.md`, create a
ticket, and verify that it first appears as `NEW` and is later updated by background triage. As an agent, inspect its
suggestions, similar tickets, and draft sources; try manual triage and resolve the incident with a resolution. As an
administrator, inspect AI Lab and switch AI off and on to see that ticket creation remains available.

Check `GET /actuator/health` for service health. Use `docs/http/tickets.http` and `docs/http/ai-and-admin.http` for
direct endpoint checks.

**Why this step matters:** An end-to-end manual check validates that the browser, API, database, authentication,
background event handling, and AI index work together outside isolated unit tests.

## Step 20: Understand limitations before using the project beyond a demo

The default mock provider is useful for offline testing but its keyword rules and hashed embeddings are not real model
quality. The checked-in evaluation report explicitly says its scores are not a measure of Gemini or Ollama performance;
run a provider-specific evaluation with the required configuration before interpreting those results.

For any shared or production deployment, replace demo credentials, set a strong `JWT_SECRET`, use versioned database
migrations instead of `ddl-auto: update`, and consider a persistent vector store. Review provider data handling and
credentials before enabling an external model. The current vector store, cache, and login-failure limiter are
in-memory; they do not provide cross-instance persistence or coordination.

**Why this step matters:** A successful local demonstration is not the same as a production-ready deployment. Stating
these limits prevents insecure defaults and misleading evaluation claims from being carried into a real service.

## Project map

| Area | Main contents |
|---|---|
| Root build | Parent `pom.xml`, `.env.example`, `.gitignore`, optional PostgreSQL `docker-compose.yml` |
| Application | REST controllers, services, repositories, JPA entities, auth/security, async event handlers, static UI |
| Core | AI pipeline, model clients, redaction, JWT/JSON utilities, seed/evaluation data, prompts |
| Tests | Core unit tests and Spring/MockMvc application tests under both modules' `src/test/java` |
| Documentation | `README.md`, `docs/ARCHITECTURE.md`, `docs/API.md`, `docs/AI-INTEGRATION.md`, `docs/DECISIONS.md`, build/evaluation reports, HTTP examples, screenshots |
| Developer workflow | Bash/PowerShell setup, run, test, and evaluation scripts; VS Code tasks and launch configuration |

The repository also contains a nested `ticketIq/` directory with duplicate project-level scaffolding files. It is not
listed as a module in the root `pom.xml`; build and run the actual application from the repository root, which includes
`ticketiq-app` and `ticketiq-core`.

## Main request-to-resolution workflow

1. A signed-in caller submits a valid ticket to `POST /api/tickets`.
2. `TicketService` persists the ticket in a transaction with status `NEW` and publishes `TicketCreated`.
3. After commit, `AiTriageHandler` runs asynchronously, redacts the ticket text, checks limits/cache, and calls the core
   pipeline if necessary.
4. The pipeline returns triage suggestions, similar-ticket matches, and—only when relevant context exists—a sourced
   reply draft. Guardrails mark uncertain or failed triage for human review.
5. `TicketService.applyAiTriage` stores suggestion fields. It does not overwrite a manual triage decision.
6. An agent reviews, edits, assigns, responds, and resolves the ticket; resolution text is required.
7. After resolution commits, `AiIndexer` embeds the resolved ticket for future similarity search. Knowledge article
   changes follow the same update pattern.
8. Requesters can continue to view and reply only on their own active tickets; staff can access history according to
   their role.
