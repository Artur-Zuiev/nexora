# Nexora — Business Operations Platform

A full-stack **customer lead management MVP** using **Java 21, Spring Boot 3, PostgreSQL, React 19, TypeScript and Vite**.

> **Development prototype only.** The local prototype uses HTTP Basic with database-backed credentials and tenant-scoped reads; public registration, production session management, access control review, TLS termination and deployment hardening are not implemented. **Never deploy this version publicly or store real customer data.**

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
| GET | `/api/me` | Current username, organization ID and role |
| GET | `/api/security/events` | Admin-only 100 recent security events for own organization |
| POST | `/api/me/password` | Authenticated self-service password change (old password required) |
| POST | `/api/team/{id}/reset-password` | ADMIN-only local MEMBER password reset, requires admin password |
| GET | `/api/team` | Admin-only team directory for the current organization |
| POST | `/api/team` | Admin-only creation of a MEMBER in the current organization |
| PATCH | `/api/team/{id}/enabled` | Admin-only enable or disable member account |
| GET | `/api/leads` | List leads scoped to the current user |
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
- [x] Database-backed users with hashed passwords and organization-aware principals (local-only)
- [x] First role permissions: MEMBER read-only CRM, ADMIN writes and views team roster
- [x] Admin-only local MEMBER creation and enable/disable controls
- [x] Authenticated password change with existing password verification and PostgreSQL integration tests
- [x] Local-only admin-assisted MEMBER recovery with administrator reauthentication
- [x] Initial in-memory local HTTP Basic login throttling with Retry-After responses
- [x] First-phase cookie-session API, CSRF enforcement, session invalidation on password/role/enable changes
- [ ] Frontend migration off HTTP Basic, production session storage and full granular roles
- [x] Preliminary tenant-scoped repository and controller reads/writes for one local organization
- [x] Database-level organization references and tenant-safe lead foreign keys
- [ ] Real multi-organization principal resolution and production isolation (required before user accounts)
- [x] Tenant threat model and migration plan (`docs/tenant-isolation-design.md`)
- [x] Flyway V1–V5 migrations, including tenant-scoped security audit history
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
- The frontend uses a server-managed HttpOnly session cookie and reads a CSRF token into React state. Credentials and tokens are not saved to localStorage. Old HTTP Basic is still available to legacy local API clients during this transition.
- The app bootstraps one local admin account into PostgreSQL. Although the database can store additional users, there is **no public registration or team management UI**; do not use real customer data.
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

## Database-level organization integrity (V3)

Flyway V3 creates the `organizations` table with a single local demo organization and adds foreign keys from leads, tasks and activity records. Tasks and activity records additionally reference the composite key `(tenant_id, lead_id)` on their parent lead: a child row cannot reference another organization's lead even if a faulty future API tries to insert one. The PostgreSQL integration test checks this invariant. This is useful defense in depth but **does not constitute user authentication, account membership or safe multi-user deployment**.

**Existing local data:** V3 refuses to run if there are orphaned tasks or activity records, unknown tenant identifiers or other inconsistent demo data. Back up the database first and inspect/correct it manually rather than silently deleting records. Neither this migration nor the repository tests have been validated against any individual user's existing PostgreSQL volume.

## Database-backed accounts (local-only)

Flyway V4 creates `app_users` containing username, hashed password, enabled flag, role and organization ID. Spring Security now authenticates against these records. On the **first startup** the existing `NEXORA_ADMIN_PASSWORD` environment variable seeds the `admin` account in the `local-admin` organization using Spring Security's delegating password encoder (bcrypt hash). The existing admin password is **not automatically changed** on subsequent restarts; changing the environment variable alone does not rotate the stored password. Do not lose the original local password. This is still a development-only bootstrap and not a password recovery mechanism.

The authenticated server-side identity provides organization scope to all CRM controllers. The new authenticated `GET /api/me` returns only username, organization ID and role. A PostgreSQL-backed integration test uses two organizations and accounts to verify separate lead listings and reject cross-organization activity access and task creation.

**Not implemented:** self-registration, user invitations, admin team management, fine-grained authorization of ADMIN vs MEMBER actions, password resets, secure sessions, TLS or an approval for public deployment. HTTP Basic remains local only; never expose it on the internet or enter real customer records. Back up any existing local database before the V4 upgrade.

## Team roles — initial access rules

- `ADMIN`: can read and modify leads and tasks, view the team roster for their own organization.
- `MEMBER`: can read only their own organization's leads, activities and tasks; cannot create or update leads/tasks or list team members.
- `GET /api/team` returns only an organization's usernames, roles, account enabled flags and creation timestamps. It never returns password hashes.

Role checks run in Spring Security on the server. This milestone **does not** implement invitations, creating or disabling users via API, self-registration, admin UI, password rotation, sessions or production hardening. Accounts can currently be inserted only through controlled development/testing database setup. The existing HTTP Basic login remains **local-only**, and no real customer data should be stored.

## Local team management (no paid services)

The admin dashboard now has a **Team members** section where an ADMIN can create a MEMBER account by entering a unique username and a password of 12–128 characters. The password is bcrypt-hashed through Spring Security before being stored in PostgreSQL. The API never returns passwords or password hashes. Username is unique across the whole local database, not only within an organization. Members have read-only CRM access. An ADMIN can disable/re-enable MEMBER accounts, with tenant-scoped server queries; disabling the current administrator or another ADMIN is rejected. Disabled users cannot log in on subsequent requests.

**Important limitations:** This is a **local developer-controlled mechanism**, not email invitation delivery and not production user management. The admin must communicate an initial password through an appropriate secure channel; there is no invitation email or self-service password reset. HTTP Basic remains local-only, password rotation/first-login reset are not yet implemented, and no real customer data should be entered. Do not forward passwords in unencrypted messages or enable public access. No paid services or hosting were added.

## Self-service password change (local only)

Any authenticated ADMIN or MEMBER can change their own password from **Account security** by supplying the current password and a different 12–128 character replacement. The backend verifies the existing encoded password and stores a newly hashed replacement. The browser clears its in-memory HTTP Basic credentials and signs out after success. Previous credentials stop authenticating subsequent requests; HTTP Basic does **not** revoke already-running requests or provide secure sessions. Passwords are not stored in the frontend or returned by the API.

This feature is not a forgotten-password recovery mechanism. No email sending, paid provider, password-reset token, TLS, production session management or public registration is implemented. Do not use the prototype with real account credentials or expose it beyond localhost.

## Password rotation integration verification

Automated Spring Security and PostgreSQL tests now verify that a successful password change stores a new encoded password, rejects the previous login credentials, and accepts the replacement credentials. Negative tests cover incorrect current passwords, reusing the currently stored password, and too-short replacements. This does **not** prevent reuse of passwords from older rotations, provide password recovery, invalidate already-running HTTP requests, or provide production sessions. No paid provider or external service is needed.

## Local administrator-assisted account recovery

When a MEMBER forgets their password, an ADMIN may use the Team members **Reset password** action. The admin must re-enter their own existing password and set a distinct new password (12–128 characters). The server verifies the admin again, restricts the account lookup to their organization, rejects ADMIN or disabled targets, and hashes the replacement. The API response does not contain the password. The old member password then stops working for new Basic-authenticated requests. There is no email delivery and no paid service.

**This is not production-grade password recovery.** Admins must communicate the replacement through an appropriate secure channel; the application has no first-login forced password change, MFA, audit event log, per-account rate limiting or secure browser sessions. It remains bound to localhost; do not expose it or use real customer accounts. Admin recovery of their own forgotten password is not implemented.

## Security activity log (local-only)

Flyway V5 adds a database-backed, per-organization timeline for successful member creation, enabling/disabling members, member password reset, and self-service password changes. Administrators can view the last 100 events in the **Security history** panel or through `GET /api/security/events`. Events store only the event type, time, actor username, target username and tenant ID — **never passwords, hashes or request bodies**. Member accounts cannot query the audit endpoint. The event insert uses the same database transaction as the state change so both changes commit or roll back together.

This is an application activity record, **not** a tamper-proof compliance audit, an intrusion detector, or failed-login monitoring. No brute-force throttling or IP-based monitoring is implemented in this step. Logs need retention, access review, and production controls before using real data. This milestone adds no external services or paid subscriptions.

## Local login-attempt throttling

A Spring Security filter blocks **a username + observed remote IP address** after 5 Basic-authentication failures during a 15-minute window. Blocked API calls return HTTP **429** with `Retry-After`; the sign-in form explains the temporary wait. A successful authenticated request clears that username/IP counter. Requests without Basic credentials do not count toward a user's failed-password attempts. The filter stores neither passwords nor Authorization headers and never trusts `X-Forwarded-For`. Unit tests cover the threshold, reset, scope, and expiry.

**Limitations:** This is a single-process **local development** defense only. Counters disappear on application restart and can be evaded using alternate usernames or addresses. Username/IP keyed throttling can also temporarily affect legitimate access if someone guesses a username. There is no shared/distributed limiter, durable lockout state, protected reverse proxy, MFA, or failed-attempt alerting. This is **not production-ready brute-force prevention**; do not open the prototype to external networks. No hosting, paid tools or subscriptions were added.

## Browser sessions — first phase, local-only

The backend provides an experimental cookie-backed session flow and the **React frontend now uses it**. This is deliberately incremental: the current React app still signs in using Basic until the session-based frontend migration and its own tests are complete. Session users have the same organization/role controls.

1. Call `GET /api/session/csrf` and retain the returned `headerName` and `token`, along with the server's HttpOnly session cookie.
2. Send `POST /api/session/login` with `{"username":"...","password":"..."}`, the CSRF header and the session cookie. A successful login rotates the session ID.
3. Send the cookie on later API requests. For every non-safe HTTP method, include a valid CSRF header; without it, the server returns 403.
4. Send `POST /api/session/logout` with the CSRF header to invalidate the session.

The server checks session-backed identities against the stored account on each request: disabling an account, changing its password or role, or changing its organization invalidates its old session. JSON login has a separate in-memory 5-attempt/15-minute limiter. The server session is local-process memory and is lost on restart. The cookie is HttpOnly and SameSite=Lax, and local development has **no HTTPS**; never expose this implementation publicly. HTTP Basic stays temporarily enabled for old local development clients and tests, with their existing CSRF exemption, until the frontend is fully migrated. This stage is **not** a production-grade authentication system, and no payment or hosting is involved.

## Local React session migration

The Vite dev server proxies `/api` requests to the locally bound Spring Boot server on `127.0.0.1:8080`. This puts cookies and API requests on one browser origin, avoiding cross-origin credential cookies. React fetches a CSRF token, signs in with `POST /api/session/login`, sends the cookie on API requests, and supplies CSRF on unsafe methods. After a page reload it checks `GET /api/me` to restore a valid server session. Logout calls `POST /api/session/logout` and clears application state; changing password also signs the user out.

**Still to do:** Retire legacy HTTP Basic completely and remove its temporary CSRF exemption, add frontend browser-level end-to-end tests, harden logout under network failure, and configure HTTPS plus secure cookies for any eventual public deployment. Never deploy this version or enter real client credentials. No new paid services are used.
