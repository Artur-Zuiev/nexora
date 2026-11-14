# Nexora tenant isolation — implementation plan

**Status:** architecture and security-test groundwork; multi-user access is NOT enabled.

## Threat model

The current application has exactly one local administrator and a shared lead database.
Adding accounts without tenant-aware queries would expose other organizations' leads,
notes, history and follow-up tasks. All tenancy checks must execute on the server.
Client-supplied organization IDs must never be trusted.

## Proposed domain

- `organizations`: internal UUID, name, created timestamp.
- `users`: internal UUID, organization UUID, normalized unique username/email,
  hashed password, active flag, role (ADMIN or MEMBER), created timestamp.
- `leads`: internal organization UUID; unique organization + lead ID lookup.
- `lead_tasks` and `lead_activities`: organization UUID plus parent lead ID;
  validate parent organization on writes and enforce it on reads.
- The authenticated principal supplies an immutable server-resolved organization
  UUID. Never use an organization identifier from the request body to choose scope.

## Access-control invariants

1. Every list/query is explicitly scoped to the current user's organization.
2. Every read, update and delete filters by **both** record ID and organization ID,
   returning a generic 404 on cross-tenant records.
3. Task creation first verifies that the target lead belongs to the same tenant.
4. Activity history uses the tenant-scoped lead lookup before reading activities.
5. Tenant IDs are immutable once the row is created; a user cannot reassign ownership.
6. Roles apply inside the tenant, rather than granting access to all tenants.
7. Security tests create two organizations and prove A cannot list, view, change,
   or attach tasks to B's leads, and vice versa.
8. Rate limits, authentication failure logging, strong password hashing,
   password reset, CSRF strategy, cookie/session security and TLS must be addressed
   before enabling public registration.

## Migration plan

1. Introduce repeatable baseline migrations (Flyway) for new databases; verify
   against a copy of the existing local development database, with backup.
2. Create one **legacy local organization** and backfill every existing lead,
   task and activity to that organization in a transaction.
3. Add and validate foreign keys / non-null constraints, then switch Hibernate
   to schema validation instead of runtime schema mutation.
4. Implement scoped repositories/services, authenticated organization principal,
   and two-tenant integration tests. Keep public signup disabled.
5. Implement invite-only users and role checks. Only then consider registration
   and external deployment after security review.

## Not shipped in this milestone

Tenant IDs, user accounts, roles, migration scripts and data isolation are
**not yet implemented**. This document is an engineering design, not a security
certification. Do not publish the app or use real customer data yet.
