# Nexora — Business Operations Platform

A full-stack **customer lead management MVP** using **Java 21, Spring Boot 3, PostgreSQL, React 19, TypeScript and Vite**.

> **Development prototype only.** Authentication, account isolation, audit logs and deployment hardening are not implemented. **Never deploy this version publicly or store real customer data.**

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
2. Start API: `cd backend && mvn spring-boot:run`
3. In a second terminal, start frontend: `cd frontend && npm install && npm run dev`
4. Open `http://localhost:5173`

The API is available at `http://localhost:8080`; CORS allows the local Vite development origin.

### API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/leads` | List all leads |
| POST | `/api/leads` | Create lead |
| PATCH | `/api/leads/{id}/status` | Update status |

The starter currently uses demo-only local database credentials in `docker-compose.yml` and `application.properties`. Replace these and add secrets management before any deployment.

## Automated checks

A GitHub Actions workflow runs on pull requests and pushes to `main`. It starts a temporary PostgreSQL 17 service, runs `mvn verify` on Java 21, and checks the TypeScript frontend with `npm run build` on Node.js 22. These checks do not deploy the application and do not require a paid hosting service. Since the frontend currently has no committed lockfile, CI uses `npm install`; commit a lockfile and switch to `npm ci` for reproducible installs.

## Roadmap

- [ ] DTOs, strict request validation and structured errors
- [ ] Authentication (Spring Security) and roles
- [ ] Tenant isolation for multiple organizations
- [ ] Flyway migrations
- [ ] Unit/integration tests, Testcontainers and frontend tests
- [x] Initial CI workflow (pending first successful run)
- [ ] Deployment and monitoring
- [ ] Customer activities, workflow automations and analytics

## Status

Version 0.1 is a portfolio starter. The first CI run and end-to-end functionality have **not yet been verified**.

**Developer:** [Artur Zuiev](https://github.com/Artur-Zuiev)
