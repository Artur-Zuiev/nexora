# Nexora — Business Operations Platform

A full-stack **customer lead management MVP** using **Java 21, Spring Boot 3, PostgreSQL, React 19, TypeScript and Vite**.

> **Development prototype only.** A single local admin is protected with HTTP Basic, but account isolation, audit logs, TLS termination and deployment hardening are not implemented. **Never deploy this version publicly or store real customer data.**

## Implemented

- Dashboard with total, in-progress, and won lead counts
- Create customer leads (name and email)
- List leads in a responsive table
- Search leads by name, email or note; filter by status
- Open lead details and update notes (up to 2000 characters)
- Display the 50 most recent recorded lead events (creation, status and note changes)
- Create follow-up tasks per lead, assign due dates, mark tasks complete, and view overdue work
- View current CRM analytics: pipeline status counts, conversion rate, closed-lead win rate, task completion and overdue totals
- Update lead status (New, Contacted, Qualified, Won, Lost)
- REST API and persistent PostgreSQL storage
- Docker Compose database for local development

## Repository

```text
backend/               Spring Boot API and JPA persistence
frontend/              React + TypeScript dashboard
docker-compose.yml     Local PostgreSQL development database
```

## Run locally

**Requirements:** Java 21, Maven, Node.js, npm and Docker.

1. Start local PostgreSQL: `docker compose up -d`
2. Set a local admin password of **at least 12 characters** (do not commit it): on PowerShell, `$env:NEXORA_ADMIN_PASSWORD='choose-a-strong-local-password'`; on macOS/Linux, `export NEXORA_ADMIN_PASSWORD='choose-a-strong-local-password'`.
3. Start API in the same terminal: `cd backend && mvn spring-boot:run`
4. In a second terminal, start frontend: `cd frontend && npm install && npm run dev`
5. Open `http://localhost:5173` and sign in with username `admin` and your configured password.

The API is available at `http://localhost:8080`; CORS allows the local Vite development origin.

### API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/leads` | List all leads |
| POST | `/api/leads` | Create lead |
| PATCH | `/api/leads/{id}/status` | Update status |
| PATCH | `/api/leads/{id}/note` | Update note |
| GET | `/api/leads/{id}/activities` | 50 most recent recorded events |
| GET | `/api/tasks` | List follow-up tasks by due date |
| GET | `/api/tasks/lead/{leadId}` | Tasks for a specific lead |
| POST | `/api/tasks` | Create lead follow-up |
| PATCH | `/api/tasks/{id}/status` | Set OPEN or DONE |

**Before upgrading a populated local database**, back up the PostgreSQL volume. Flyway is configured to baseline a pre-existing schema at version 1 and apply version 2; this is intended only for the known local demo schema. Do not point this configuration at an unknown or production database.

The starter uses demo-only local database credentials in `docker-compose.yml` and `application.properties`. Replace these and add secrets management before any deployment.

## Roadmap

- [x] Create-lead request DTO, field limits, status validation and structured validation errors
- [x] Single-admin HTTP Basic for local development (not production auth)
- [ ] Production authentication, password rotation, session security and granular roles
- [x] Preliminary tenant-scoped repository and controller reads/writes for one local organization
- [ ] Real multi-organization principal resolution, enforced schema constraints and production isolation (required before user accounts)
- [x] Tenant threat model and migration plan (`docs/tenant-isolation-design.md`)
- [x] Flyway V1/V2 baseline migrations for local CRM database
- [ ] Production-grade, reviewed migration and backup/restore process
- [ ] Unit/integration tests, Testcontainers and frontend tests
- [x] GitHub Actions build/test checks
- [ ] Deployment and monitoring
- [x] Basic searchable and filterable CRM workspace with editable lead notes
- [x] Basic lead activity timeline for new events
- [ ] Workflow automations and historical analytics

## Status

Version 0.1 is a portfolio starter. GitHub Actions compile and test the backend and frontend on pull requests. A full end-to-end browser test has **not yet been verified**.

**Developer:** [Artur Zuiev](https://github.com/Artur-Zuiev)

## Security limitations

- **Local development only.** HTTP Basic credentials are transmitted on every API request; use TLS before any remote use. Do not put this prototype on a public network.
- The server intentionally refuses to start without `NEXORA_ADMIN_PASSWORD` (12+ characters). No default admin password is shipped.
- The frontend keeps the encoded Basic auth header only in React state, not persistent browser storage. Base64 is **not encryption**.
- The app has one shared local admin account, no self-registration or tenant isolation; do not use real customer data.
- The database port is bound to the loopback interface for local development and uses a demo credential: replace all credentials and harden infrastructure before deployment.
- `POST /api/leads` accepts only `name` (1–120 chars), `email` (valid, max 254 chars), and optional `note` (max 2000 chars). New leads always begin in status `NEW`.

## CRM workspace (local prototype)

After signing in, search the lead pipeline by name, email or note, filter by status and click **View** to see a lead's details. The note editor saves its contents using `PATCH /api/leads/{id}/note`, accepting a JSON body such as `{"note":"Follow up next week"}`. The backend validates a maximum of 2000 characters. Notes are stored on each lead; this is **not** an activity/audit history. Search and filtering currently run in the browser over the loaded leads; add server-side pagination and search for larger datasets.

The development-only credentials and single-admin security limitations described above still apply. Do not deploy publicly or use real customer data.

## Lead activity history

The activity timeline records newly created leads and later status/note changes, with UTC timestamps. It shows the 50 most recent events per lead, ordered newest first. It intentionally records only a short event summary and never copies customer names, email addresses, or note contents into the activity log. Existing leads won't have retrospective history; only actions taken after this feature is installed are recorded. This is a basic application event history, **not** a tamper-proof compliance audit log.

## Follow-up tasks

Open a lead and create a task with a title and due date. Tasks appear in a global list with filters for open, completed or all. Unfinished tasks with due dates earlier than the browser's current local date are visually highlighted as overdue. These are **in-app reminders only**: no push notifications, emails, scheduled jobs or external services run. Tasks are not automatically deleted. This local single-admin prototype has no tenant isolation and is not suitable for production customer information.

## Analytics dashboard

The dashboard computes a **current snapshot** from the leads and tasks already loaded into the browser. Lead conversion = won leads / all leads; closed-lead win rate = won / (won + lost); task completion = completed / all tasks. Each percentage is 0% when its denominator is zero. It also shows lead counts by status and the number of open tasks whose due date is before the browser's current local date. This feature does not claim trends over time, financial revenue, or forecasting accuracy. For large datasets, add paginated backend reporting rather than browser-side totals.

## Security hardening milestone

The API now returns explicit `LeadResponse` and `LeadTaskResponse` objects instead of exposing mutable JPA entities directly. Both the Spring Boot server and the Docker Compose PostgreSQL host port bind to `127.0.0.1` by default. These changes reduce accidental network exposure, **but are not production authentication, account registration, or tenant isolation**. The local-only warning remains in effect. Before multiple users are created, the data model must be migrated to organization-scoped records, and every read/write must enforce that scope on the server.

## Preparing for multiple organizations

See [the tenant-isolation design](docs/tenant-isolation-design.md) for the required organization-scoped data model, migration plan and two-tenant authorization tests. This milestone also adds integration tests confirming that anonymous and invalid-credential requests are rejected and that the configured local admin can access the API. **Organization scoping and multiple user accounts are still not implemented.**

## Tenant scoping milestone (local-only)

All lead, task and activity lookups now use a server-resolved tenant identifier instead of accepting one supplied by the browser. The current admin maps to the **single fixed** `local-admin` organization; account registration and multi-organization membership are **not implemented**. New records receive that tenant ID. On startup, the temporary local database backfill assigns existing rows with missing tenant IDs to `local-admin` after Hibernate has updated the schema. This preserves existing demo data but is **not** a migration suitable for production: it lacks managed versioning, hard foreign keys, and verified rollback procedures. Back up your local PostgreSQL volume before updating a populated database.

Repository-level unit tests verify that lead, task and activity operations reject records outside the resolved tenant and that new records receive their tenant ID. These do **not** certify isolation for multiple real authenticated organizations. Do not expose the service or use real customer data.

## Flyway schema migrations (local-only)

The backend now uses Flyway instead of Hibernate schema mutation. On an empty database, Flyway runs `V1__initial_crm_schema.sql` and `V2__tenant_scoping.sql`, after which Hibernate validates the resulting schema. Existing local Hibernate-generated demo databases without Flyway history are automatically baselined at V1 and then receive V2, including the `local-admin` tenant backfill. The previous startup-time backfill component has been removed. The integration test checks Flyway history and tenant columns against PostgreSQL in GitHub Actions.

**Important:** automatic baselining assumes the existing database already has `leads`, `lead_tasks`, and `lead_activities` tables matching the earlier local prototype. There are no production guarantees. Take a PostgreSQL backup, rehearse restoration, and review the schema before upgrading existing data; never enable automatic baseline against an unknown database. For production, implement an explicit reviewed migration path, foreign keys, organization membership, and backup/restore tests. Application login is still single-admin only.
