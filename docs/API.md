# REST API

All endpoints are under `/api`. Send `Authorization: Bearer <accessToken>` except where marked public.
Errors always look like `{ "timestamp", "status", "error", "message", "fieldErrors"? }`.
Ready-to-click examples are in `docs/http/*.http`.

| Method and path | Who | Purpose |
|---|---|---|
| `GET /api/meta` | public | Version and active AI provider |
| `POST /api/auth/signup` | public | `{employeeId?,fullName,userId,password,role,team?}` creates a disabled account request |
| `POST /api/auth/login` | public | `{userId,password,role}` returns access and refresh tokens after approval |
| `POST /api/auth/refresh` | public | `{refreshToken}` returns new tokens |
| `GET/PUT /api/auth/me` | any | View/update current name and employee ID |
| `POST /api/auth/password` | any | Change password after verifying the current password |
| `POST /api/users` | ADMIN | Create a profile `{userId,employeeId?,fullName,password,role,team?}` |
| `GET /api/users` | AGENT, ADMIN | List users |
| `PATCH /api/users/{id}/approval` | ADMIN | `{approved}` approve or decline a new account request |
| `POST /api/tickets` | USER, AGENT, ADMIN | `{title,description}`; status NEW; AI triage starts in the background |
| `PATCH /api/tickets/{id}` | owner or staff | `{title,description}` update an active incident; AI analysis refreshes |
| `GET /api/tickets` | USER (own incidents), AGENT, ADMIN | Filters `status, category, priority, q`, paging `page, size`; users can follow their own incidents through closure |
| `GET /api/tickets/{id}` | owner or staff | Incident details and team; staff get full AI analysis and users get a warning-labeled, non-actionable AI summary |
| `PATCH /api/tickets/{id}/triage` | AGENT, ADMIN | Manual `{category,priority}`; overrides the AI |
| `PATCH /api/tickets/{id}/assign` | AGENT, ADMIN | `{assigneeId}` (null unassigns) |
| `PATCH /api/tickets/{id}/status` | AGENT, ADMIN | `{status,resolution?}`; closing requires completion remarks; only admins can reopen |
| `PATCH /api/tickets/{id}/timeline` | ADMIN | `{severityBand,expectedResolutionDays}`; targets: A=2, B=2–4, C=5–7 days |
| `POST /api/tickets/{id}/requests` | owner or staff | Request severity change or deadline extension; agent severity proposals require requester approval |
| `GET /api/tickets/{id}/requests` | owner | Pending severity proposals from support |
| `POST /api/tickets/{id}/requests/{requestId}/user-decision` | owner | `{approved}` accept or decline an agent severity proposal |
| `GET/PATCH /api/approvals[/{id}]` | ADMIN | Review and approve/decline incident change requests |
| `POST /api/tickets/{id}/knowledge-access-request` | owner | Request selected resolved-case details |
| `GET /api/tickets/{id}/knowledge-recommendations` | ADMIN | Relevant closed cases with team, handling, completion, and self-service details |
| `GET /api/tickets/{id}/related-incidents` | AGENT, ADMIN | Relevant closed-case details for staff knowledge-base use |
| `PUT /api/tickets/{id}/knowledge-share` | ADMIN | `{incidentIds}` share selected recommendations until the current incident closes |
| `GET /api/tickets/{id}/knowledge-share` | owner or staff | Admin-approved details; automatically unavailable after closure |
| `GET /api/notifications` | AGENT, ADMIN | Incidents without an update for at least six hours |
| `POST /api/tickets/{id}/comments` | owner or staff | `{body,internal}`; internal is staff-only |
| `GET /api/tickets/{id}/comments` | owner or staff | Users never see internal notes |
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

Role boundary: AGENT can review active and closed incidents, access RAG history, and handle support requests. Only
ADMIN can approve accounts or incident changes, reopen incidents, choose past-case recommendations to share, write
knowledge articles, or change AI configuration. USER accounts can create and follow their own incidents; historical
resolution details from other incidents remain unavailable until an administrator approves them for an active ticket.
