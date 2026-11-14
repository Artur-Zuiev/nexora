-- Backfill legacy LOCAL demo rows. No existing user accounts/organizations.
-- Do not reuse this baseline path for a production system.
ALTER TABLE leads ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE lead_tasks ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE lead_activities ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);

UPDATE leads SET tenant_id = 'local-admin' WHERE tenant_id IS NULL;
UPDATE lead_tasks SET tenant_id = 'local-admin' WHERE tenant_id IS NULL;
UPDATE lead_activities SET tenant_id = 'local-admin' WHERE tenant_id IS NULL;

ALTER TABLE leads ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE lead_tasks ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE lead_activities ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lead_tenant ON leads (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_lead_task_tenant ON lead_tasks (tenant_id, lead_id);
CREATE INDEX IF NOT EXISTS idx_lead_activity_tenant ON lead_activities (tenant_id, lead_id);
