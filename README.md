# TicketIQ

An AI-enhanced helpdesk built with **Java 21 and Spring Boot**. Users raise tickets, an AI does the first-line
triage (category, priority, summary), spots duplicates and drafts a reply from your knowledge base, and **a human
always decides**.

It tells one story: a plain, legacy-style ticketing app (version 1) upgraded with AI (version 2) without rewriting it.
Create, Triage, Resolve: three events, three AI features.

![AI panel on a ticket](docs/screenshots/ticket-ai-panel.png)

## Quick start

You need **JDK 21** and **Maven 3.9+**. Nothing else: no Docker, no database, no API key.

```bash
bash scripts/setup.sh     # checks Java and Maven, creates .env       (Windows: scripts\setup.ps1)
bash scripts/run.sh       # builds, starts the app, opens the browser  (Windows: scripts\run.ps1)
```

The run script opens **http://localhost:8080** in a new browser window when a supported browser is installed.
Otherwise it opens the URL using the operating system's default browser handler.

**In VS Code:** open this folder, accept the recommended extensions, press **F5** (`TicketIQ (Spring Boot)`),
then browse to http://localhost:8080. Tasks are under *Terminal > Run Task* (Setup, Build and Test, Run, Evaluate AI).

### Demo sign-ins (local use only)

| Role | User ID | Password | Can do |
|---|---|---|---|
| Admin | admin@ticketiq.local | Admin@123 | everything, including users, knowledge base, AI health |
| Agent | agent@ticketiq.local | Agent@123 | triage, reply, resolve, see AI suggestions |
| User | user@ticketiq.local | User@123 | raise and follow their own tickets |

New users can select **Create an account** on the sign-in page. Signup saves an employee ID, name, user ID, and a
BCrypt-protected password. Passwords must be at least 8 characters and contain a lowercase letter, uppercase letter,
number, and symbol. Self-registered accounts receive the USER role.

### Roles and access

| Role | Main purpose | Access |
|---|---|---|
| **USER** | Report an incident | Create tickets, follow their own active tickets, reply on those tickets, and view their profile. Cannot browse other users' tickets or any resolved/closed incident history. |
| **AGENT** | Investigate and assign incidents | See current and historical incidents, review AI matches and drafts, assign and triage tickets, communicate, and resolve them. Can read the knowledge base and Insights, but cannot create users, edit knowledge articles, or change AI settings. |
| **ADMIN** | Configure and oversee the service | All agent work, plus create users, manage knowledge articles, switch AI on/off, and inspect AI health and usage. |

Roles are enforced by the API, not just by hiding navigation options. ADMIN is the highest role; AGENT has support-work access, and USER has requester access only.

### Try this in two minutes

1. Sign in as **Agent**, choose **New ticket**, click the sample **Reset email missing**, submit.
2. Read the ticket page's guide, then watch the ticket fill in: summary, confidence, a **similar past incident**, and a **suggested reply with sources**.
3. Click **Use as reply**, edit, send. Or override the triage by hand: your choice always beats the AI.
4. See the suggested support team on the ticket, resolve it (a resolution is required), and open **Insights** to compare AI vs human triage.
5. Sign in as **Admin**, open **AI Lab**, switch the AI **off**, create a ticket (it stays NEW), switch the AI **on**, and
   watch the ticket get triaged. Tickets never depend on the AI.

## What is in the box

| Version 1 (the "legacy" app) | Version 2 (added AI) |
|---|---|
| Users and roles (USER, AGENT, ADMIN), JWT sign-in | Auto-triage: category, priority, summary, confidence |
| Create, list, filter, page, comment, assign, resolve | Duplicate detection with embeddings |
| Manual triage by agents | Suggested reply drafted from the knowledge base and past fixes (RAG) |
| Knowledge-base articles | Personal data removed before any model sees it |
| Ticket updates and stats | Guardrails: low confidence or failure sends the ticket to a human |
| | Learning loop: every resolved ticket makes the next match better |
| | Audit log, metrics, retry, circuit breaker, caching, rate limit |

## Project layout

```
ticketiq/
├── pom.xml                      parent POM (Spring Boot 3.5, Java 21)
├── ticketiq-core/               plain Java, zero runtime dependencies: the AI brain
│   └── src/main/java/com/ticketiq/core/
│       ├── ai/                  pipeline, triage, similarity, RAG reply, guardrails, resilient executor
│       ├── llm/                 mock, Gemini and Ollama model clients
│       ├── redaction/           PiiRedactor
│       ├── security/            hand-written HS256 JWT
│       ├── json/  model/  seed/  eval/
│       └── resources/           prompts, seed data, 40-ticket evaluation set
├── ticketiq-app/                the Spring Boot application
│   └── src/main/java/com/ticketiq/app/
│       ├── ticket/ user/ kb/    entities, repositories, services, controllers
│       ├── ai/                  the integration points (event handlers, sweeper, indexer, audit, AI endpoints)
│       ├── stats/ config/ security/ events/ common/
│       └── resources/           application.yml, static/ (the browser UI)
├── docs/                        ARCHITECTURE, AI-INTEGRATION, API, DECISIONS, BUILD_REPORT, evaluation report, .http files
├── scripts/                     setup, run, test, evaluate (bash and PowerShell)
└── .vscode/                     extensions, settings, tasks, launch configs
```

## Switching the AI provider

The same pipeline runs on every provider. Edit `.env` and restart.

| Provider | Set | Notes |
|---|---|---|
| **mock** (default) | `AI_PROVIDER=mock` | Offline keyword rules and hashed embeddings. Free, deterministic, used by all tests. |
| **Gemini** | `AI_PROVIDER=gemini` and `GEMINI_API_KEY=...` | Models via `GEMINI_CHAT_MODEL` / `GEMINI_EMBEDDING_MODEL`. Check Google's docs for current names. |
| **Ollama** | `AI_PROVIDER=ollama` | Run `ollama pull llama3.2` and `ollama pull nomic-embed-text` first. |

You do not need to clear anything when you change provider: the vector index lives in memory and is rebuilt
from the database at every start.

To score a provider honestly: `AI_PROVIDER=gemini GEMINI_API_KEY=... bash scripts/evaluate.sh` rewrites
`docs/evaluation-report.md`. The shipped report was generated with the **mock**, and says so. Do not quote mock
numbers as AI accuracy.

## Tests

```bash
bash scripts/test.sh        # mvn verify: core unit tests + Spring integration tests, no API key needed
```

What is covered: PII redaction (every kind), JSON, JWT (tampering, expiry), the resilient executor (retry, backoff,
timeout, circuit breaker), triage parsing and the repair attempt, prompt-injection delimiting, guardrails, vector search,
the full AI pipeline, the Gemini and Ollama clients against a local fake server, security rules, validation, and the
whole ticket flow through MockMvc. See `docs/BUILD_REPORT.md` for exactly which tests were run where.

## Optional: PostgreSQL

`docker compose up -d`, then set `SPRING_PROFILES_ACTIVE=postgres` in `.env` and start the app.

## Troubleshooting

| Problem | Fix |
|---|---|
| `release version 21 not supported` | Your JDK is older than 21. Install JDK 21 and make `JAVA_HOME` point at it. |
| Port 8080 busy | Set `PORT=8090` in `.env`. |
| `JWT secret must be at least 32 characters` | Your `JWT_SECRET` is too short. |
| `GEMINI_API_KEY is required` | You chose `AI_PROVIDER=gemini` without a key. Use `mock` or add the key. |
| Tickets stay NEW | The AI is switched off (AI Lab) or still starting. The sweeper triages them within about 30 seconds of it returning. |
| Fresh start | Stop the app and delete the `data/` folder. Demo data is re-created. |
| Slow first build | Maven is downloading dependencies once. |

## AI ticket workflow

When a user submits a ticket, TicketIQ analyzes its title and description. The retrieval-augmented generation (RAG)
pipeline compares the incident with knowledge-base articles and resolved-ticket history, then suggests a category,
priority, summary, similar incidents, and a reply. The category maps to a suggested team: Identity and Access,
Billing and Payments, Application Engineering, Product Support, Integrations, Product, or General Support. PII is
redacted before model processing, and staff remain responsible for reviewing and acting on AI suggestions.

## Production notes

This is a portfolio-grade project. Before real use: set a strong `JWT_SECRET`, replace the demo passwords and
`ddl-auto: update` (use Flyway migrations), and swap the in-memory vector store for pgvector.
