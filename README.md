---
title: TicketIQ
emoji: 🎫
colorFrom: blue
colorTo: indigo
sdk: docker
app_port: 7860
pinned: false
---

# TicketIQ

TicketIQ is an IT help desk built with **Java 21 and Spring Boot**. Employees can report issues and track their active
tickets. Support agents can review and manage incidents, including resolved cases.

TicketIQ can also analyze a ticket and suggest a category, priority, summary, support team, similar incidents, and a
reply based on the knowledge base. These suggestions help staff investigate; support staff remain responsible for
decisions and communication. If the analysis service is unavailable, users can still submit tickets.

![AI panel on a ticket](docs/screenshots/ticket-ai-panel.png)

## Free hosted demo (Hugging Face Spaces)

This repository is configured as a Docker Space. To publish a free public demo:

1. On [Hugging Face Spaces](https://huggingface.co/spaces), create a new **Public** Space and select **Docker**.
2. Push this repository to the Space's Git remote, or connect the repository when creating the Space.
3. Wait for the Space to build. Its public URL will be `https://<your-username>-<space-name>.hf.space`.

The Space uses the mock AI provider, needs no API key or external database, and seeds demo users, articles, and tickets
on startup. The hosted database is in memory, so changes disappear when the Space restarts. Free Spaces may sleep while
idle and take a little time to wake up. This is a shared demo: do not enter real personal or confidential information.
The seeded demo accounts below can be used to explore the different roles.

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

### Demo accounts (for local and hosted demos only)

| Role | User ID | Password | Can do |
|---|---|---|---|
| Admin | admin@ticketiq.local | Admin@123 | everything, including users, knowledge base, AI health |
| Agent | agent@ticketiq.local | Agent@123 | triage, reply, resolve, see AI suggestions |
| User | user@ticketiq.local | User@123 | raise and follow their own tickets |

New users can select **Create an account** on the sign-in page. Signing up saves an employee ID, name, user ID, and a
BCrypt-protected password. Passwords must be at least 8 characters and contain a lowercase letter, uppercase letter,
number, and symbol. Self-registered accounts receive the USER role.
Do not use the seeded accounts or demo configuration for a production deployment.

### Roles and access

| Role | Main purpose | Access |
|---|---|---|
| **USER** | Report an incident | Create tickets, follow their own active tickets, reply on those tickets, and view their profile. Cannot browse other users' tickets or any resolved/closed incident history. |
| **AGENT** | Investigate and assign incidents | See current and historical incidents, review AI matches and drafts, assign and triage tickets, communicate, and resolve them. Can read the knowledge base and Insights, but cannot create users, edit knowledge articles, or change AI settings. |
| **ADMIN** | Configure and oversee the service | All agent work, plus create users, manage knowledge articles, switch AI on/off, and inspect AI health and usage. |

Roles are enforced by the API, not just by hiding navigation options. ADMIN is the highest role; AGENT has support-work access, and USER has requester access only.

### Quick walkthrough

1. Sign in as **Agent**, choose **New ticket**, click the sample **Reset email missing**, submit.
2. Read the ticket page's guide, then watch the ticket fill in: summary, confidence, a **similar past incident**, and a **suggested reply with sources**.
3. Click **Use as reply**, review and edit the draft, then send it. Agents can also change the suggested category or priority.
4. See the suggested support team on the ticket, resolve it (a resolution is required), and open **Insights** to compare AI vs human triage.
5. Sign in as **Admin**, open **AI Lab**, switch the AI **off**, create a ticket (it stays NEW), switch the AI **on**, and
   watch the ticket get triaged. Tickets never depend on the AI.

## Features

| Ticket management | AI-assisted support |
|---|---|
| Sign-in and USER, AGENT, and ADMIN roles | Suggested category, priority, and summary |
| Create, filter, assign, comment on, and resolve tickets | Similarity search across resolved incidents |
| Manual triage and status updates | Reply drafts based on knowledge articles and past resolutions (RAG) |
| Knowledge articles and ticket statistics | Personal information is redacted before model processing |
| Role-based access to ticket history | Low-confidence results are flagged for staff review |
| | Resolved tickets and knowledge articles are added to the search index |
| | Model-call audit records, metrics, retries, circuit breaker, and caching |

## Project layout

```
ticketiq/
├── pom.xml                      parent POM (Spring Boot 3.5, Java 21)
├── ticketiq-core/               plain Java module for triage, search, and AI integrations
│   └── src/main/java/com/ticketiq/core/
│       ├── ai/                  pipeline, triage, similarity, RAG reply, guardrails, resilient executor
│       ├── llm/                 mock, Gemini and Ollama model clients
│       ├── redaction/           PiiRedactor
│       ├── security/            HS256 JWT support
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
| **mock** (default) | `AI_PROVIDER=mock` | Local keyword rules and hashed embeddings. Deterministic and requires no API key. |
| **Gemini** | `AI_PROVIDER=gemini` and `GEMINI_API_KEY=...` | Configure the chat and embedding models with `GEMINI_CHAT_MODEL` and `GEMINI_EMBEDDING_MODEL`. |
| **Ollama** | `AI_PROVIDER=ollama` | First run `ollama pull llama3.2` and `ollama pull nomic-embed-text`. |

You do not need to clear anything when you change provider: the vector index lives in memory and is rebuilt
from the database at every start.

To evaluate a provider, run `AI_PROVIDER=gemini GEMINI_API_KEY=... bash scripts/evaluate.sh`. This updates
`docs/evaluation-report.md`. The checked-in report uses the **mock** provider; its results describe the local rules
and are not a measure of Gemini or Ollama performance.

## Tests

```bash
bash scripts/test.sh        # mvn verify: core unit tests + Spring integration tests, no API key needed
```

The suite covers PII redaction, JSON handling, JWT tampering and expiry, retries and circuit breaking, triage parsing,
prompt-injection handling, guardrails, vector search, the full AI pipeline, Gemini and Ollama clients against a local test
server, security rules, validation, and the ticket flow through MockMvc. See `docs/BUILD_REPORT.md` for the recorded
validation details.

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

## How ticket analysis works

After a ticket is saved, TicketIQ analyzes its title and description. Retrieval-augmented generation (RAG) searches
knowledge articles and resolved tickets for relevant information, then prepares suggestions for the support team.
The suggested category maps to a team such as Identity and Access, Billing and Payments, Application Engineering,
Product Support, Integrations, Product, or General Support. Personal information is redacted before text is sent for
model processing. Staff review the suggestions before taking action.

## Production notes

Before using this beyond a local or demonstration environment, set a strong `JWT_SECRET`, replace the demo passwords,
use database migrations instead of `ddl-auto: update`, and consider a persistent vector store such as pgvector.
