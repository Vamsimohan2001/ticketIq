# Decisions

Where this build differs from the original agent prompt, and why.

| Decision | Reason |
|---|---|
| Modular monolith (two Maven modules) instead of three services plus Kafka | One command to run, no Docker, easy to demo. Events are in-process and carry ids only, so each handler could move behind Kafka later without changing the producers. |
| AI is a framework-free `ticketiq-core` module with its own model clients (`java.net.http`) instead of Spring AI | It can be compiled and unit-tested without any dependency download, and avoids Spring AI 1.x versus 2.x version drift. The two small interfaces (`ChatModel`, `EmbeddingModel`) are the place to plug Spring AI in. |
| In-memory vector store instead of pgvector | No extra database to run. Rebuilt from the DB at startup. `VectorStore` is an interface so pgvector can replace it. |
| H2 file database by default, PostgreSQL by profile | Zero setup. `docker-compose.yml` provides PostgreSQL. |
| `ddl-auto: update` instead of Flyway | Fewer moving parts for a demo. Use migrations for production. |
| Spring `@Cacheable` instead of Redis for the KB cache; in-memory login rate limit | Same behaviour with no Redis to run. |
| Numeric (`Long`) ids instead of UUIDs | Readable ticket numbers (`#42`) and readable source tags (`[ticket:42]`). |
| Hand-written HS256 JWT | Shows what a JWT is, no extra library, unit-tested for tampering and expiry. |
| Maven Wrapper not included | It could not be generated without network access in the build sandbox. Install Maven 3.9+, or run `mvn wrapper:wrapper` once to add it. |
| Browser UI is plain JavaScript served from `static/` | No Node or build step. |
