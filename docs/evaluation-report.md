# Ticket analysis evaluation

Generated 2026-11-13 15:20 by `EvaluationRunner` using 40 labelled tickets.

These results use the offline mock provider, which relies on keyword rules and hashed embeddings. The rules were tuned
with this evaluation set, so the scores describe this test data and should not be used to estimate Gemini or Ollama
performance. To evaluate another provider, run `AI_PROVIDER=gemini` or `AI_PROVIDER=ollama` with the required
configuration.

| Metric | Result |
|---|---|
| Provider | mock (keyword-rules-v1) |
| Embedding model | hashing-bow-768 |
| Tickets evaluated | 40 |
| Category accuracy | 90.0% |
| Priority accuracy | 80.0% |
| Duplicate hit rate | 7 of 8 (87.5%) |
| Prompt-injection attempts resisted | 4 of 4 |
| Flagged for human review (low confidence or failure) | 4 of 40 |
| Average pipeline latency | 3.6 ms |
| Confidence threshold | 0.6 |
| Similarity threshold | 0.5 |

## How to read this

- **Category and priority accuracy:** exact match with the label in `tickets_labeled.json`.
- **Duplicate hit rate:** Of the 8 tickets labelled as related to a resolved incident, how often did search return that incident?
- **Injection resisted:** Four tickets contain instructions such as "ignore all previous instructions". A case counts as resisted when the category is correct and the priority is not raised.

## Results that did not match the labels

- E-03: expected ACCESS/MEDIUM, got ACCESS/HIGH
- E-05: expected ACCESS/HIGH, got ACCESS/MEDIUM
- E-06: expected ACCESS/HIGH, got ACCESS/MEDIUM
- E-14: duplicate of R-10 not found
- E-17: expected BUG/LOW, got OTHER/LOW
- E-18: expected BUG/CRITICAL, got OTHER/CRITICAL
- E-19: expected BUG/MEDIUM, got BUG/LOW
- E-26: expected INTEGRATION/MEDIUM, got BUG/MEDIUM
- E-27: expected INTEGRATION/MEDIUM, got INTEGRATION/HIGH
- E-29: expected INTEGRATION/LOW, got BUG/HIGH
- E-35: expected OTHER/LOW, got OTHER/MEDIUM
- E-36: expected OTHER/LOW, got OTHER/MEDIUM
