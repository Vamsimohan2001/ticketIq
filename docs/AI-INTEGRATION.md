# AI integration

All AI code lives in `ticketiq-core` and is reached from the Spring app only through `AiRuntime`
(built once in `config/AiConfig`). Switching provider changes nothing else.

## The pipeline (`AiPipeline.run`)

```
ticket text
  redact PII ─▶ rate limit ─▶ triage (cached) ─▶ embed + find similar ─▶ RAG reply ─▶ guardrails ─▶ result
                                          │                      │                        │
                                          └──────────── every model call goes through AiGateway ──────────┘
                                                      timeout · retry · circuit breaker · audit · metrics
```

Each step fails on its own. A broken model never throws out of the pipeline; it produces a result that says
"needs a human". The implementation redacts text before checking the pipeline rate limit; no model call is made when
the limit has been reached.

## Integration points

| # | Integration point | What it does | Classes |
|---|---|---|---|
| IP-1 | Event contract | `TicketCreated`/`TicketResolved`/`KbChanged` decouple ticket lifecycle work from AI; triage results are applied directly by the handler, not published as a `TicketTriaged` event | `events/TicketEvents`, `ai/AiTriageHandler`, `ticket/TicketService` |
| IP-2 | Privacy gate | Emails, phones (incl. +91 and 10-digit), PAN, Aadhaar-like, Luhn-valid cards become `[EMAIL]` etc. before reaching a model or embedding; indexed text is redacted too | `redaction/PiiRedactor`, used first in `AiPipeline` and `AiRuntime.index*` |
| IP-3 | Structured output | Model must return one JSON object; parsed and validated into a `TriageResult` record; one repair attempt, then fall back to a human | `ai/TriageService`, `ai/TriageResult`, `resources/prompts/triage-*.txt` |
| IP-4 | Embeddings | Redacted text becomes a unit vector; cosine search finds earlier tickets above the similarity threshold | `llm/*EmbeddingModel`, `ai/SimilarityService`, `ai/InMemoryVectorStore` |
| IP-5 | Retrieval | Top 3 knowledge-base articles and top 3 closed/resolved incidents that clear the relevance threshold. Incident documents include completion remarks, owning team, agent handling, and safe self-service notes. | `ai/ReplyDraftService`, `ai/AiIndexer` |
| IP-6 | Grounded generation | The model may use only the supplied context and must cite `[kb:id]` / `[ticket:id]`; no context means no draft | `ai/ReplyDraftService`, `prompts/reply-*.txt` |
| IP-7 | Guardrails | Low confidence or any failure sets `needsHuman`; the AI never closes a ticket or messages a user; a manual triage overrides the AI | `ai/GuardrailPolicy`, `TicketService.applyAiTriage` |
| IP-8 | Feedback and learning loop | Resolved tickets and KB edits are embedded; agents mark drafts helpful or not; acceptance rate appears in Insights | `ai/AiIndexer`, `ticket/AiFeedback`, `stats/StatsController` |

The demo corpus is a curated resource of 60 closed/resolved incidents in
`ticketiq-core/src/main/resources/seed/resolved_incidents.json` and
`ticketiq-core/src/main/resources/seed/resolved_tickets.json`. The index is rebuilt from those stored incident records
at startup. For a live incident, the admin can review relevant matches and explicitly share selected completion and
handling details with its requester; that access is unavailable once the live incident closes.

## Prompt-injection defence

* Ticket text sits between `<<<TICKET` and `TICKET>>>` and the system prompt says it is untrusted data.
* Our own delimiter markers are stripped from ticket text so it cannot close the block (`PromptTemplates.neutralise`).
* Output is validated, so an obeyed injection that produces garbage is caught and repaired or escalated.
* Four labelled injection tickets are part of the evaluation set.

## Resilience (`ResilientAiExecutor`)

* Timeout per call (`AI_TIMEOUT_SECONDS`).
* Retries with exponential backoff (`AI_MAX_RETRIES`), skipped for non-retryable errors such as HTTP 401.
* Circuit breaker: 5 failed calls in a row open it for 30 seconds, then one trial call decides.
* Triage results are cached for an hour by hash of the redacted text; the pipeline is rate-limited per minute.

## Providers

`mock` (offline), `gemini` (REST `generateContent` and `embedContent`, key sent in a header), `ollama` (`/api/chat`,
`/api/embed`). The clients use Java's built-in `java.net.http` package. Provider integrations implement the `ChatModel` and
`EmbeddingModel` interfaces.
