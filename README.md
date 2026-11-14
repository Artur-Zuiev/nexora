# Nexora — Business Operations Platform

**Local-only CRM portfolio MVP candidate** built with Java 21, Spring Boot, PostgreSQL 17, React 19, TypeScript and Vite. The project is not publicly deployed and is **not approved for real customer data**. A tested local demonstration is the immediate milestone; production SaaS security and operations are separate work.

## What works

- Leads: create, search/filter, change status and notes, inspect recent activity
- Follow-ups: create, complete/reopen, filter overdue/today/later, search by task/customer
- Analytics: pipeline status counts, conversion and task-completion snapshot
- Users: local admin, administrator-managed MEMBER accounts, read-only member permissions
- Authentication: server-side HttpOnly cookie sessions with CSRF, session revocation after account/password changes; **HTTP Basic has been removed**
- Admin-only tenant-scoped CSV exports for leads and tasks
- PostgreSQL Flyway migrations V1–V5, local backup/verify/restore utilities
- Java integration tests, Chromium browser E2E tests, GitHub Actions checks
- Responsive React layout and mobile navigation

## Quick local start

Requirements: **Java 21, Maven, Node.js 22, npm, Docker Desktop/Compose**.

1. Start PostgreSQL: `docker compose up -d`.
2. Set an **original local-only administrator password** of at least 12 characters in the terminal where Java starts:
   - PowerShell: `$env:NEXORA_ADMIN_PASSWORD='replace-with-a-private-test-password'`
   - macOS/Linux: `export NEXORA_ADMIN_PASSWORD='replace-with-a-private-test-password'`
3. Start backend: `cd backend && mvn spring-boot:run`.
4. In a second terminal start frontend: `cd frontend && npm install && npm run dev`.
5. From the repository root run a read-only readiness check: `node scripts/mvp-preflight.mjs`.
6. Visit **http://127.0.0.1:5173** and sign in as `admin` using the initial password. **On existing databases, the admin password is not reset by the environment variable.**

For a reproducible walkthrough and safety checklist, see **[Local MVP demo guide](docs/mvp-demo.md)**.

## Repository

```text
backend/          Spring Boot REST API and Flyway/PostgreSQL persistence
frontend/         React + TypeScript dashboard and Playwright browser tests
scripts/          Optional local demo preflight and Python backup utility
docs/             MVP walkthrough, local recovery and security design
docker-compose.yml  Loopback-only PostgreSQL for local development
```

## Checks

| Check | Command | Notes |
| --- | --- | --- |
| Readiness script unit tests | `node --test scripts/mvp-preflight.test.mjs` | No running services required |
| Local services ready | `node scripts/mvp-preflight.mjs` | Read-only localhost HTTP checks; not a security review |
| Java backend tests | `cd backend && mvn verify` | Requires configured disposable PostgreSQL |
| React build | `cd frontend && npm run build` | TypeScript + production bundle |
| Chromium E2E | `cd frontend && npm run test:e2e` | Requires backend, browser and `E2E_ADMIN_PASSWORD`; **creates demo data** |
| Local database backup | `python scripts/db_backup.py backup --output backups/nexora-demo.dump` | Archive contains sensitive records and password hashes |

GitHub Actions also runs Chromium E2E and a synthetic-data PostgreSQL backup/restore smoke test. These jobs use available GitHub Actions quota; no paid plan or external service is connected.

## Local API

| Endpoint | Role/purpose |
| --- | --- |
| `GET /api/session/csrf`, `POST /api/session/login`, `POST /api/session/logout` | CSRF + cookie sessions |
| `GET /api/me`, `POST /api/me/password` | Current account and own password change |
| `GET/POST /api/leads`, `PATCH /api/leads/{id}/status`, `PATCH /api/leads/{id}/note` | Tenant-scoped CRM (writes ADMIN) |
| `GET /api/leads/{id}/activities` | Lead activity history |
| `GET/POST /api/tasks`, `PATCH /api/tasks/{id}/status` | Tenant-scoped follow-ups (writes ADMIN) |
| `GET/POST /api/team`, `PATCH /api/team/{id}/enabled` | ADMIN team management |
| `POST /api/team/{id}/reset-password`, `GET /api/security/events` | ADMIN member password recovery and audit |
| `GET /api/exports/leads.csv`, `GET /api/exports/tasks.csv` | ADMIN tenant-scoped CSV exports |

## Security and release boundaries

- **Local development only:** backend and PostgreSQL are bound to loopback, with demo database credentials. No TLS, production secure cookies, public deployment or real customer accounts.
- Passwords are hashed, but the local bootstrap environment variable **does not rotate an existing admin password**. Do not commit secrets or copy real records into demo data.
- Sessions use in-process memory and are lost on restart. Login throttling is in-memory rather than a distributed, durable protection; no MFA or production identity provider.
- Server-side tenant scoping and test coverage exist, but there is no production multi-tenant security approval, privacy impact assessment, monitoring or external penetration test.
- Browser-side lists are not paginated. CSV exports contain entire tenant datasets and must be protected as confidential files. Backups are not encrypted automatically.
- The [migration and tenant design](docs/tenant-isolation-design.md) and [backup guide](docs/local-backups.md) describe limits and local procedures. Do not deploy publicly or store real customer data.

## Next milestones

- [x] Functional leads, tasks, analytics, audit and role management
- [x] Cookie sessions and CSRF; removal of HTTP Basic
- [x] Browser E2E for key administrator/member flows
- [x] Local backup/restore verification and CSV exports
- [x] Read-only localhost readiness helper and repeatable demo instructions
- [ ] Manual acceptance check on a local machine with disposable data
- [ ] Review accessibility, security hardening, scaling and production migration needs
- [ ] Only after explicit approval: evaluate public hosting, costs and operational safeguards

**Project:** [Artur-Zuiev/nexora](https://github.com/Artur-Zuiev/nexora) · **Developer:** [Artur Zuiev](https://github.com/Artur-Zuiev)

Note: the Git history preserves earlier milestone documentation, which contained outdated statements about HTTP Basic and other now-completed features. This README reflects the current codebase.
