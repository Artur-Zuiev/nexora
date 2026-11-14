# Nexora — Business Operations Platform

A full-stack **customer lead management MVP** using **Java 21, Spring Boot 3, PostgreSQL, React 19, TypeScript and Vite**.

> **Development prototype only.** A single local admin is protected with HTTP Basic, but account isolation, audit logs, TLS termination and deployment hardening are not implemented. **Never deploy this version publicly or store real customer data.**

## Implemented

- Dashboard with total, in-progress, and won lead counts
- Create customer leads (name and email)
- List leads in a responsive table
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

The starter uses demo-only local database credentials in `docker-compose.yml` and `application.properties`. Replace these and add secrets management before any deployment.

## Roadmap

- [x] Create-lead request DTO, field limits, status validation and structured validation errors
- [x] Single-admin HTTP Basic for local development (not production auth)
- [ ] Production authentication, password rotation, session security and granular roles
- [ ] Tenant isolation for multiple organizations
- [ ] Flyway migrations
- [ ] Unit/integration tests, Testcontainers and frontend tests
- [ ] CI pipeline, deployment, monitoring
- [ ] Customer activities, workflow automations and analytics

## Status

Version 0.1 is a portfolio starter. CI build and end-to-end functionality have **not yet been verified**.

**Developer:** [Artur Zuiev](https://github.com/Artur-Zuiev)

## Security limitations

- **Local development only.** HTTP Basic credentials are transmitted on every API request; use TLS before any remote use. Do not put this prototype on a public network.
- The server intentionally refuses to start without `NEXORA_ADMIN_PASSWORD` (12+ characters). No default admin password is shipped.
- The frontend keeps the encoded Basic auth header only in React state, not persistent browser storage. Base64 is **not encryption**.
- The app has one shared local admin account, no self-registration or tenant isolation; do not use real customer data.
- The database is exposed on localhost for local development and uses a demo credential: replace all credentials and harden infrastructure before deployment.
- `POST /api/leads` accepts only `name` (1–120 chars), `email` (valid, max 254 chars), and optional `note` (max 2000 chars). New leads always begin in status `NEW`.
