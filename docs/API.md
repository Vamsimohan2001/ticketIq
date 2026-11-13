# REST API

All endpoints are under `/api`. Send `Authorization: Bearer <accessToken>` except where marked public.
Errors always look like `{ "timestamp", "status", "error", "message", "fieldErrors"? }`.
Ready-to-click examples are in `docs/http/*.http`.

| Method and path | Who | Purpose |
|---|---|---|
| `GET /api/meta` | public | Version and active AI provider |
| `POST /api/auth/signup` | public | `{employeeId,fullName,userId,password}` creates a USER profile |
| `POST /api/auth/login` | public | `{userId,password}` returns access and refresh tokens |
| `POST /api/auth/refresh` | public | `{refreshToken}` returns new tokens |
| `GET /api/auth/me` | any | Current profile (`userId,employeeId,fullName,role`) |
| `POST /api/users` | ADMIN | Create a profile `{userId,employeeId?,fullName,password,role}` |
| `GET /api/users` | AGENT, ADMIN | List users |
| `POST /api/tickets` | USER, AGENT, ADMIN | `{title,description}`; status NEW; AI triage starts in the background |
| `GET /api/tickets` | USER (own active only), AGENT, ADMIN | Filters `status, category, priority, q`, paging `page, size`; only staff see resolved/closed history |
| `GET /api/tickets/{id}` | owner (active tickets only) or staff | Detail and category-based `suggestedTeam`; staff also get the `ai` block. Resolved/closed incidents are hidden from USER accounts. |
| `PATCH /api/tickets/{id}/triage` | AGENT, ADMIN | Manual `{category,priority}`; overrides the AI |
| `PATCH /api/tickets/{id}/assign` | AGENT, ADMIN | `{assigneeId}` (null unassigns) |
| `PATCH /api/tickets/{id}/status` | AGENT, ADMIN | `{status,resolution?}`; RESOLVED needs a resolution |
| `POST /api/tickets/{id}/comments` | owner (active tickets only) or staff | `{body,internal}`; internal is staff-only |
| `GET /api/tickets/{id}/comments` | owner (active tickets only) or staff | Users never see internal notes or comments on completed tickets |
| `GET /api/tickets/{id}/similar` | AGENT, ADMIN | Similar past tickets with scores |
| `GET /api/tickets/{id}/suggested-reply` | AGENT, ADMIN | Draft and its sources |
| `POST /api/tickets/{id}/ai-feedback` | AGENT, ADMIN | `{accepted,edited}` |
| `POST /api/tickets/{id}/retriage` | AGENT, ADMIN | Ask the AI to look again (202) |
| `GET /api/kb` | AGENT, ADMIN | Articles (cached) |
| `POST/PUT/DELETE /api/kb[/{id}]` | ADMIN | Manage articles; changes reach the AI index |
| `GET /api/stats` | AGENT, ADMIN | Ticket mix and AI usefulness |
| `POST /api/ai/dry-run/triage` | AGENT, ADMIN | Run the pipeline on given text; saves nothing |
| `GET /api/ai/stats` | ADMIN | Provider, circuit state, metrics, recent model calls |
| `PUT /api/ai/switch` | ADMIN | `{enabled}` turn the AI off or on at runtime |
| `GET /actuator/health` | public | Liveness |

Statuses: `NEW, TRIAGED, NEEDS_HUMAN_TRIAGE, IN_PROGRESS, RESOLVED, CLOSED`.
Categories: `ACCESS, BILLING, BUG, HOW_TO, INTEGRATION, FEATURE_REQUEST, OTHER`. Suggested teams are Identity and Access,
Billing and Payments, Application Engineering, Product Support, Integrations, Product, and General Support.
Passwords on signup must be 8–72 printable characters and include lowercase, uppercase, a number, and a symbol.
Priorities: `LOW, MEDIUM, HIGH, CRITICAL`.

Role boundary: AGENT can review and handle tickets and read shared knowledge/Insights. Only ADMIN can manage users,
write knowledge articles, or change AI configuration. USER accounts can create and follow their own active incidents;
the API denies access to all other users' tickets and completed incident history.
