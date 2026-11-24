# Architecture

TicketIQ is a **modular monolith**: one Spring Boot process, with the AI kept in a separate framework-free module.
Ticket lifecycle work reaches the AI through events; staff AI tools can call the same runtime directly. That keeps the
ticket workflow decoupled, makes the AI easy to test, and lets it fail without hurting ticket creation.

```
                    ┌──────────────────────── ticketiq-app (Spring Boot) ───────────────────────┐
 Browser ── REST ──▶│ Controllers ─▶ TicketService ─▶ JPA (H2 / PostgreSQL)                      │
 (static UI)        │      │              │  publishes events (after commit)                     │
                    │      │              ▼                                                     │
                    │      │   TicketCreated ──▶ AiTriageHandler ──▶ ┌── ticketiq-core ───────┐ │
                    │      │                      (async thread)     │ AiPipeline             │ │
                    │      │                          │              │  redact → triage       │ │
                    │      │   applyAiTriage ◀───────────────────────│  embed → similar       │ │
                    │      │        │                                │  RAG reply → guardrail │ │
                    │      │        ▼                                │ AiGateway (retry,      │ │
                    │      │                                         │  audit, metrics)       │ │
                    │      │   TicketResolved ──▶ AiIndexer ────────▶│ VectorStore (memory)   │ │
                    │      │   KbChanged      ──▶ (learning loop)    └──────────┬─────────────┘ │
                    └──────┴──────────────────────────────────────────────────  ▼ ───────────────┘
                                                                 mock | Gemini | Ollama
```

The optional Streamlit frontend is a separate Python client of the same authenticated REST API. Its server-side
requests do not require browser CORS configuration. The Spring Boot app and its original browser UI remain the system
of record.

## The three events

| Event | Published by | Handled by |
|---|---|---|
| `TicketCreated` | `TicketService.create` (after commit) | `AiTriageHandler` runs the pipeline in the background |
| `TicketResolved` | `TicketService.updateStatus` | `AiIndexer` embeds it (learning) |
| `KbChanged` | `KbService` | `AiIndexer` adds, updates or removes the article in the index |

Events carry ids only and handlers reload data, so running one twice is harmless.

## Why tickets survive an AI outage

* Creating a ticket never calls a model. It saves, commits, then publishes an event.
* `AiTriageHandler` catches everything. A failed or switched-off AI leaves the ticket NEW or NEEDS_HUMAN_TRIAGE.
* `AiTriageSweeper` retries NEW tickets every 30 seconds.
* A human's manual triage always wins (`TriageSource.MANUAL`), even if the AI answers later.

## Data model

`users` (employee ID, user ID, password hash, name, and role), `tickets` (v1 columns plus `ai_*` suggestion columns and an optimistic-lock `version`), `comments`,
`kb_articles`, `ai_feedback`, and `ai_calls` (audit, never ticket text). The vector index is in memory and
rebuilt at startup from `kb_articles` and resolved tickets.

## Security

Stateless JWT (HS256, 30-minute access and 7-day refresh tokens). BCrypt passwords. Self-signup creates USER accounts
only; passwords require uppercase, lowercase, a number, a symbol, and at least 8 characters. Roles enforced with
`@PreAuthorize`. Failed logins are rate-limited (5 per 5 minutes per user ID) and give one generic message. A user asking
for someone else's ticket or a completed incident gets 404, not 403. The USER role can create incidents and view only
their own active incidents. AGENT can browse incident history, use AI-assisted triage, assign and resolve tickets, and
read the knowledge base and Insights. ADMIN inherits the support access and additionally manages users and knowledge
articles and controls/monitors the AI. These restrictions are enforced by the API, not only the UI. Users never receive
the AI block. All errors share one JSON shape.
