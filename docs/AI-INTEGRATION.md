# AI integration

All AI code lives in `ticketiq-core` and is reached from the Spring app only through `AiRuntime`
(built once in `config/AiConfig`). Switching provider changes nothing else.

## The pipeline (`AiPipeline.run`)

```
ticket text
  S1 rate limit ─▶ S2 redact PII ─▶ S3 triage (cached) ─▶ S4 embed + find similar ─▶ S5 RAG reply ─▶ S6 guardrails ─▶ result
                                          │                      │                        │
                                          └──────────── every model call goes through AiGateway ──────────┘
                                                      timeout · retry · circuit breaker · audit · metrics
```

Each step fails on its own. A broken model never throws out of the pipeline; it produces a result that says
"needs a human".

## Integration points

| # | Integration point | What it does | Classes |
|---|---|---|---|
| IP-1 | Event contract | `TicketCreated`/`TicketTriaged`/`TicketResolved`/`KbChanged` decouple tickets from AI | `events/TicketEvents`, `ai/AiTriageHandler`, `ticket/TicketService` |
| IP-2 | Privacy gate | Emails, phones (incl. +91 and 10-digit), PAN, Aadhaar-like, Luhn-valid cards become `[EMAIL]` etc. before any model, embedding or stored text | `redaction/PiiRedactor`, used first in `AiPipeline` and `AiRuntime.index*` |
| IP-3 | Structured output | Model must return one JSON object; parsed and validated into a `TriageResult` record; one repair attempt, then fall back to a human | `ai/TriageService`, `ai/TriageResult`, `resources/prompts/triage-*.txt` |
| IP-4 | Embeddings | Redacted text becomes a unit vector; cosine search finds earlier tickets above the similarity threshold | `llm/*EmbeddingModel`, `ai/SimilarityService`, `ai/InMemoryVectorStore` |
| IP-5 | Retrieval | Top 3 knowledge-base articles and top 3 resolved tickets that clear the relevance threshold | `ai/ReplyDraftService` |
| IP-6 | Grounded generation | The model may use only the supplied context and must cite `[kb:id]` / `[ticket:id]`; no context means no draft | `ai/ReplyDraftService`, `prompts/reply-*.txt` |
| IP-7 | Guardrails | Low confidence or any failure sets `needsHuman`; the AI never closes a ticket or messages a user; a manual triage overrides the AI | `ai/GuardrailPolicy`, `TicketService.applyAiTriage` |
| IP-8 | Feedback and learning loop | Resolved tickets and KB edits are embedded; agents mark drafts helpful or not; acceptance rate appears in Insights | `ai/AiIndexer`, `ticket/AiFeedback`, `stats/StatsController` |

## Prompt-injection defence

* Ticket text sits between `<<<TICKET` and `TICKET>>>` and the system prompt says it is untrusted data.
* Our own delimiter markers are stripped from ticket text so it cannot close the block (`PromptTemplates.neutralise`).
* Output is validated, so an obeyed injection that produces garbage is caught and repaired or escalated.
* Four labelled injection tickets are part of the evaluation set.

## Resilience (`ResilientAiExecutor`, hand-written, no library)

* Timeout per call (`AI_TIMEOUT_SECONDS`).
* Retries with exponential backoff (`AI_MAX_RETRIES`), skipped for non-retryable errors such as HTTP 401.
* Circuit breaker: 5 failed calls in a row open it for 30 seconds, then one trial call decides.
* Triage results are cached for an hour by hash of the redacted text; the pipeline is rate-limited per minute.

## Providers

`mock` (offline), `gemini` (REST `generateContent` and `embedContent`, key sent in a header), `ollama` (`/api/chat`,
`/api/embed`). The clients use only `java.net.http`, so there is no SDK version to keep in step. A Spring AI adapter could
implement the two small interfaces `ChatModel` and `EmbeddingModel` if you want to showcase it.
