# Design decisions

These choices keep the local setup straightforward while leaving room to expand the system.

| Decision | Reason |
|---|---|
| Modular monolith (two Maven modules) instead of separate services and Kafka | The application runs with one command and does not require Docker. Events run in-process and carry ids, which keeps the event handlers decoupled from ticket creation. |
| Framework-free `ticketiq-core` module with model clients built on `java.net.http` | Core analysis can be tested independently of Spring. The `ChatModel` and `EmbeddingModel` interfaces isolate provider-specific code. |
| In-memory vector store instead of pgvector | No separate vector database is needed for local use. The index is rebuilt from the database at startup, and the `VectorStore` interface allows a persistent implementation later. |
| H2 file database by default, PostgreSQL by profile | H2 needs no setup; `docker-compose.yml` provides an optional PostgreSQL service. |
| `ddl-auto: update` instead of Flyway | Keeps the demonstration setup simple. Use versioned migrations for production deployments. |
| Spring `@Cacheable` and an in-memory login rate limit instead of Redis | Caching and rate limiting work without another service to install. |
| Numeric (`Long`) ids instead of UUIDs | Ticket numbers and source references are easy to read. |
| HS256 JWT implementation in the application | Avoids an additional dependency and has tests for tampering and expiry. |
| Maven Wrapper not included | Maven 3.9 or later must be installed to build the project. |
| Browser UI is plain JavaScript served from `static/` | The UI does not need Node.js or a separate build step. |
