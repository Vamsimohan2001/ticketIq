# TicketIQ

I built TicketIQ as an IT help desk where employees can report problems and support teams can manage them through
resolution. I wanted AI to help with the repetitive first pass, while leaving decisions and communication to the people
handling each ticket.

![TicketIQ ticket analysis panel](docs/screenshots/ticket-ai-panel.png)

## What you can do

I organized the portal around three roles. Employees can create tickets, follow their status, and reply to support.
Agents can review the queue, assign and triage tickets, use suggested replies, and resolve incidents. Admins can also
manage users and knowledge articles, and control the AI settings.

I added AI to handle some of the first-pass work. For a ticket, it can suggest a category, priority, summary, and
support team, then look for related incidents and knowledge articles to help draft a reply. Staff review the suggestions
before using them. If the AI service is unavailable, the ticket can still be submitted. Ticket text is checked for
personal information before it is sent to an external model.

## Run locally

You need JDK 21 and Maven 3.9 or later. The default setup uses H2 and the built-in mock AI provider, so no database
server or API key is required.

In Windows PowerShell:

```powershell
.\scripts\setup.ps1
.\scripts\run.ps1
```

On macOS or Linux:

```bash
bash scripts/setup.sh
bash scripts/run.sh
```

Open [http://localhost:8080](http://localhost:8080). The setup script creates a local `.env` file. For PostgreSQL,
start the database with `docker compose up -d` and set `SPRING_PROFILES_ACTIVE=postgres` in `.env`.

### Demo accounts

On a fresh demo database, these accounts are seeded for local use. All seeded accounts use the same initial password,
`12345678`; change it from **My profile** after signing in.

- Admin: `admin@ticketiq.local`
- Service desk agent: `agent@ticketiq.local`
- HR systems agent: `agent.hr@ticketiq.local`
- Finance systems agent: `agent.finance@ticketiq.local`
- Application engineering agent: `agent.engineering@ticketiq.local`
- Identity and access agent: `agent.access@ticketiq.local`
- User: `user@ticketiq.local`

Signup supports USER, AGENT, and ADMIN requests. Every new account remains disabled until an administrator approves it
from **Approvals**. Login checks the selected role against the account.

The demo data includes 60 closed/resolved incidents spanning HR, finance, access, endpoint, application, network,
integration, how-to, and product-request cases. Each record includes the owning team, how the case was handled,
completion remarks, resolution time, and safe self-service guidance where appropriate. These records are indexed with
the help articles for RAG. No active incidents are pre-seeded; new tickets are created through the portal.

## AI providers

TicketIQ uses the `mock` provider by default. It runs locally and needs no key. To try another provider, set
`AI_PROVIDER` in `.env`:

- `gemini` uses `GEMINI_API_KEY` and the configured Gemini chat and embedding models.
- `ollama` uses a local Ollama server; pull `llama3.2` and `nomic-embed-text` before starting the app.

The vector index is rebuilt from the database when the app starts. The evaluation report in
[docs/evaluation-report.md](docs/evaluation-report.md) uses the mock provider, so it is not a benchmark of Gemini or
Ollama.

## Streamlit deployment

The Streamlit frontend is an authenticated Python client for the existing Spring Boot API. It supports account
requests, sign-in, ticket creation and follow-up, staff triage and status changes, and staff insights. The full Java
portal remains available at the backend URL for the knowledge base, approvals, AI administration, and other advanced
workflows. Streamlit sends API requests server-to-server, so the backend does not need permissive browser CORS rules.

Run the frontend locally after starting Spring Boot (Python 3.12 or newer):

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
$env:TICKETIQ_API_URL = "http://localhost:8080"
.\.venv\Scripts\python.exe -m streamlit run streamlit_app.py
```

For Streamlit Community Cloud, deploy this repository with `streamlit_app.py` as the app entrypoint and select Python
3.12 or newer. First deploy the Spring Boot backend using the Render Blueprint below, then add its public base URL in
the Streamlit app settings:

```toml
TICKETIQ_API_URL = "https://your-ticketiq-api.onrender.com"
```

### Temporary Render backend

The root `render.yaml` Blueprint provisions the Spring Boot Docker service and PostgreSQL in Singapore. To deploy it,
open Render, select **New > Blueprint**, connect this GitHub repository on the `main` branch, and review the resources
before deploying. Render assigns the backend's public `onrender.com` URL after the first successful deployment.

The Blueprint generates a private JWT signing key and a random initial password for the seeded administrator. Find
`TICKETIQ_SEED_PASSWORD` in the backend service's Render environment settings and use it to sign in as
`admin@ticketiq.local`; change that password immediately after signing in.

This configuration deliberately uses Render's free plans for a temporary demo only: the web service may sleep after
inactivity, and the free PostgreSQL database expires after 30 days (with eventual deletion). Do not use real or
sensitive ticket data. Upgrade the database to a paid plan before relying on it for persistent data. Streamlit hosts
only the Python frontend; the backend URL must be publicly reachable over HTTPS.

## Tests and project code

Run the test suite with `.\scripts\test.ps1` in PowerShell, or `bash scripts/test.sh` on macOS and Linux. It covers the
AI pipeline, security, validation, and ticket workflows without needing an API key.
Run the Streamlit API-client tests with
`.\.venv\Scripts\python.exe -m unittest discover -s tests -v`.

The project has two Java modules. `ticketiq-app` contains the Spring Boot API, security, persistence, and browser UI;
`ticketiq-core` contains the ticket analysis, model clients, retrieval, and guardrails. The optional browser-only
sample is in `static-demo/`; unlike the Streamlit frontend, it does not call the API. More detail is in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md),
[docs/API.md](docs/API.md), and [docs/AI-INTEGRATION.md](docs/AI-INTEGRATION.md).

## Before using it beyond a demo

Replace the seeded passwords and default JWT secret, use database migrations instead of `ddl-auto: update`, and choose
a persistent database and vector store. The demo setup is not intended for production.
