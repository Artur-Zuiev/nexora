# Nexora — local MVP demonstration checklist

**Purpose:** demonstrate the current CRM using synthetic data on your own computer.
No public hosting, client information, or paid services are required.

## Prerequisites

- Java 21 and Maven, Node.js 22 and npm, Docker Desktop + Docker Compose
- PostgreSQL 17 container from `docker-compose.yml`
- A local admin password **at least 12 characters**; store it privately
- Use a clean, disposable local database; do not use customer data

## Start

1. From the project root: `docker compose up -d`.
2. Set `NEXORA_ADMIN_PASSWORD` in the same terminal that will run Java.
   - PowerShell: `$env:NEXORA_ADMIN_PASSWORD='replace-this-with-a-unique-local-password'`
   - macOS/Linux: `export NEXORA_ADMIN_PASSWORD='replace-this-with-a-unique-local-password'`
3. Start backend in that terminal: `cd backend && mvn spring-boot:run`.
4. In another terminal: `cd frontend && npm install && npm run dev`.
5. In a third terminal from the repository root: `node scripts/mvp-preflight.mjs`.
6. Open **http://127.0.0.1:5173**. Sign in as `admin` using the
   same password as step 2, **unless this database already has an admin**.
   The bootstrap password is only used when an admin is created for the first time.

## Demo script (synthetic records only)

1. Sign in and check the pipeline summary metrics.
2. Add a fictional lead, e.g. `Demo Company`, `demo@example.test`.
3. Change the lead's status, open its details, add a note, inspect activity.
4. Create a follow-up due today, view **Due today**, search the task, complete it.
5. Filter the pipeline and show its analytics dashboard.
6. As admin, export leads/tasks CSV; keep files private and delete after demonstration.
7. Create a disposable MEMBER account with a unique test password.
8. Sign out and sign in as MEMBER. Show the read-only workspace and that
   team management, editing and CSV export controls are not available.
9. Sign out. Reload to confirm the previous session was revoked.

## Automated validation

- `node --test scripts/mvp-preflight.test.mjs`: checks readiness logic
  without starting backend or frontend.
- `node scripts/mvp-preflight.mjs`: read-only checks for the *running*
  local frontend and backend; no credentials required.
- Backend tests: from `backend/`, `mvn verify` (requires PostgreSQL and
  the test configuration).
- Browser tests: from `frontend/`, set `E2E_ADMIN_PASSWORD`, then
  `npx playwright install chromium` and `npm run test:e2e`. **These tests
  create records**. Run them against a disposable database only.
- GitHub Actions checks the Java backend, React build, Chromium browser flows,
  PostgreSQL backup/restore and Node preflight logic.

## Protect existing data

Never run tests on a database containing personal/customer data. Before updating
an existing local demo, make a backup using
`python scripts/db_backup.py backup --output backups/nexora-demo.dump`.
See [local backups](local-backups.md). A backup may contain account hashes and
contact information; store it privately and never commit it.

## Known limitations before public SaaS

This is a **localhost-only portfolio demo**, not a product approved for external
deployment. HTTPS and secure-cookie deployment, reviewed multi-tenant security,
MFA, durable rate limits/sessions, monitoring, incident response, data retention,
production migration/backup procedures, and privacy/security review are missing.
Local CSV export may be large and browser-side CRM search is not paginated.
Production hosting, email delivery, billing and invitation flows are not configured.
